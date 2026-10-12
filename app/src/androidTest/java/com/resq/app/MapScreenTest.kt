package com.resq.app

import android.content.Context
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.database.sqlite.SQLiteDatabase
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.RectF
import android.os.Build
import android.os.SystemClock
import android.util.Log
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import androidx.navigation.fragment.NavHostFragment
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.json.JSONArray
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.gestures.StandardScaleGestureDetector
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import java.io.File
import java.io.FileOutputStream
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** Exercises the packaged offline map without changing settings, profiles, or saved contacts. */
@RunWith(AndroidJUnit4::class)
class MapScreenTest {
    private var scenario: ActivityScenario<MainActivity>? = null
    private var observedView: MapView? = null
    private var map: MapLibreMap? = null
    private val completeFrames = AtomicInteger()
    private var originalOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
    @Volatile private var renderingPhase = "initialization"
    private val frameTimings = Collections.synchronizedList(mutableListOf<FrameTiming>())
    private val fullRenderTimes = linkedMapOf<String, Long>()
    private val gestureDiagnostics = linkedMapOf<String, Any>()

    @After
    fun closeScreen() {
        writePerformanceReport()
        scenario?.onActivity { it.requestedOrientation = originalOrientation }
        scenario?.close()
    }

    @Test
    fun offlineProvinceMap_fitsBothOrientations_clampsZoom_andRendersStreetDetails() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val metadata = context.assets.open("map/cavite_basemap.json")
            .bufferedReader().use { JSONObject(it.readText()) }
        val archiveName = metadata.getString("archive")
        assertTrue("Packaged basemap must use PMTiles", archiveName.endsWith(".pmtiles"))
        context.assets.open("pmtiles/$archiveName").use { archive ->
            val header = ByteArray(8)
            assertEquals("PMTiles header should be available", header.size, archive.read(header))
            assertEquals("Archive must have a real PMTiles signature", "PMTiles", String(header, 0, 7, Charsets.US_ASCII))
            assertEquals("MapLibre's offline archive must be PMTiles v3", 3, header[7].toInt())
        }
        val hazardArchives = metadata.getJSONArray("hazards")
        assertEquals("Storm surge package must contain one compact archive", 1, hazardArchives.length())
        val hazardArchiveName = hazardArchives.getJSONObject(0).getString("archive")
        context.assets.open("pmtiles/$hazardArchiveName").use { archive ->
            val header = ByteArray(8)
            assertEquals("Storm surge PMTiles header should be available", header.size, archive.read(header))
            assertEquals("Storm surge archive must be PMTiles v3", 3, header[7].toInt())
        }
        val coverage = metadata.getJSONArray("coverage").let {
            doubleArrayOf(it.getDouble(0), it.getDouble(1), it.getDouble(2), it.getDouble(3))
        }
        val corners = listOf(
            LatLng(coverage[1], coverage[0]), LatLng(coverage[1], coverage[2]),
            LatLng(coverage[3], coverage[0]), LatLng(coverage[3], coverage[2])
        )
        val style = context.assets.open("map/${metadata.getString("style")}")
            .bufferedReader().use { JSONObject(it.readText()) }
        assertTrue("The basemap source should use the offline PMTiles archive",
            style.getJSONObject("sources").getJSONObject("mapsplat").getString("url").startsWith("pmtiles://"))
        assertEquals("The basemap plus offline hazard source should be present", 7, style.getJSONObject("sources").length())
        assertTrue("Storm surge source must be offline",
            style.getJSONObject("sources").getJSONObject("noah").getString("url").startsWith("pmtiles://"))
        assertEquals("Sprites must be available offline", "asset://map/poi_sprite", style.getString("sprite"))
        val layers = style.getJSONArray("layers")
        val definitions = (0 until layers.length()).map { layers.getJSONObject(it) }
        val roadIds = definitions.filter {
            it.optString("type") == "line" && it.optString("source-layer").contains("osm_roads")
        }.map { it.getString("id") }.toTypedArray()
        val buildingIds = definitions.filter { it.optString("source-layer").contains("osm_buildings") }
            .map { it.getString("id") }.toTypedArray()
        val outlineIds = definitions.filter {
            it.getString("id") == "cavite_admin_boundary_uniform"
        }.map { it.getString("id") }.toTypedArray()
        val placeIds = definitions.filter {
            it.getString("id") == "cavite_admin_label_anchors_uniform"
        }.map { it.getString("id") }.toTypedArray()
        val hazardIds = definitions.filter { it.getString("id").startsWith("haz-ssa") }
            .map { it.getString("id") }.toTypedArray()
        assertTrue("Basemap must provide detailed roads", roadIds.isNotEmpty())
        assertTrue("Basemap must provide street-level buildings", buildingIds.isNotEmpty())
        assertTrue("Cavite must have a visible outline", outlineIds.isNotEmpty())
        assertTrue("Administrative boundaries must be visible in the province overview",
            definitions.first { it.getString("id") == "cavite_admin_boundary_uniform" }
                .optDouble("minzoom", 0.0) <= 0.01)
        val buildingDefinitions = definitions.filter { it.optString("source-layer").contains("osm_buildings") }
        assertTrue("Buildings must be deferred until near maximum zoom",
            buildingDefinitions.isNotEmpty() && buildingDefinitions.all { it.optDouble("minzoom", 0.0) >= 16.0 })
        assertTrue("Map must provide readable place labels", placeIds.isNotEmpty())
        assertEquals("All four storm surge advisories must be styled", 4, hazardIds.size)
        assertTrue("Hazard layers must remain visible through the map maximum zoom",
            definitions.filter { it.getString("id").startsWith("haz-ssa") }
                .all { it.optDouble("maxzoom", 24.0) > 18.0 })
        val hazardMetadata = context.assets.open("map/cavite_storm_surge.json")
            .bufferedReader().use { JSONObject(it.readText()) }
        assertTrue("Storm surge archive must contain native maximum-zoom tiles",
            hazardMetadata.optInt("maxTileZoom", 0) >= 18)

        val savedData = launchMap(context)
        requestOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
        awaitMapReady()
        awaitFullRender("portrait overview")
        scenario!!.onActivity {
            definitions.forEach { layer ->
                assertTrue("Export layer must load in native Android: ${layer.getString("id")}",
                    map!!.style!!.getLayer(layer.getString("id")) != null)
            }
            assertTrue("The original fuel sprite must load", map!!.style!!.getImage("fuel_station") != null)
        }
        assertOverview(corners)
        assertViewportInsideCoverage(coverage)
        assertRenderedFeatures("overview place labels", placeIds)
        assertRenderedFeatures("administrative boundaries at minimum zoom", outlineIds)
        assertNoRenderedFeatures("buildings at minimum zoom", buildingIds)
        assertNoRenderedFeatures("storm surge advisories while disabled", hazardIds)
        var collapsedHazardWidth = 0
        scenario!!.onActivity { collapsedHazardWidth = it.findViewById<View>(R.id.layer_scroll).width }
        scenario!!.onActivity { it.findViewById<View>(R.id.chip_hazard_storm_surge).performClick() }
        awaitUi("storm surge advisory controls") { it.findViewById<View>(R.id.layout_hazard_advisories).isShown }
        scenario!!.onActivity {
            assertEquals("Selecting advisories must not resize the hazard panel",
                collapsedHazardWidth, it.findViewById<View>(R.id.layer_scroll).width)
        }
        val advisoryViews = listOf(R.id.chip_ssa1, R.id.chip_ssa2, R.id.chip_ssa3, R.id.chip_ssa4)
        val advisorySizes = mutableMapOf<Int, Pair<Int, Int>>()
        scenario!!.onActivity { activity ->
            advisoryViews.forEach { id ->
                val chip = activity.findViewById<View>(id)
                advisorySizes[id] = chip.width to chip.height
            }
        }
        advisoryViews.forEachIndexed { index, viewId ->
            scenario!!.onActivity { it.findViewById<View>(viewId).performClick() }
            awaitFullRender("storm surge advisory ${index + 1}")
            assertRenderedFeatures("storm surge advisory ${index + 1}", arrayOf(hazardIds[index]))
            scenario!!.onActivity { activity ->
                val chip = activity.findViewById<View>(viewId)
                assertEquals("Selecting an advisory must not change its size",
                    advisorySizes[viewId], chip.width to chip.height)
            }
            scenario!!.onActivity { it.findViewById<View>(viewId).performClick() }
            awaitFullRender("storm surge advisory ${index + 1} disabled")
        }
        scenario!!.onActivity {
            it.findViewById<View>(R.id.chip_ssa1).performClick()
            map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(
                LatLng(14.487211169056957, 120.89850999305446), 18.0))
        }
        awaitUi("storm surge maximum zoom") { kotlin.math.abs(map!!.cameraPosition.zoom - 18.0) < 0.05 }
        scenario!!.onActivity {
            assertTrue("Storm surge layer must stay visible at the map maximum zoom",
                map!!.style!!.getLayer(hazardIds[0])!!.visibility.toString().contains("visible"))
        }
        scenario!!.onActivity {
            it.findViewById<View>(R.id.chip_ssa1).performClick()
            it.findViewById<View>(R.id.btn_map_overview).performClick()
        }
        awaitFullRender("storm surge controls collapsed")
        scenario!!.onActivity { it.findViewById<View>(R.id.chip_hazard_storm_surge).performClick() }
        assertAdminLabels()
        assertOriginalGeometry("portrait minimum", definitions)
        assertNoSeaLanes()
        captureScreenshot("overview-portrait")

        var openingLongitude = 0.0
        scenario!!.onActivity { openingLongitude = map!!.cameraPosition.target!!.longitude }
        panMapWithTouch()
        SystemClock.sleep(500)
        scenario!!.onActivity { map!!.cancelTransitions() }
        awaitFullRender("panned minimum view")
        assertOverview(corners)
        assertViewportInsideCoverage(coverage)
        scenario!!.onActivity {
            assertTrue("Users must be able to pan to cropped areas at the minimum zoom",
                kotlin.math.abs(map!!.cameraPosition.target!!.longitude - openingLongitude) > 0.00001)
            it.findViewById<View>(R.id.btn_map_overview).performClick()
        }

        var portraitMinimum = 0.0
        scenario!!.onActivity { portraitMinimum = map!!.minZoomLevel }
        scenario!!.onActivity { activity ->
            assertFalse("Zoom out must be disabled at the overview", activity.findViewById<View>(R.id.btn_map_zoom_out).isEnabled)
            assertTrue("Zoom in must be available", activity.findViewById<View>(R.id.btn_map_zoom_in).isEnabled)
            activity.findViewById<View>(R.id.btn_map_zoom_in).performClick()
        }
        awaitUi("zoom-in control") { map!!.cameraPosition.zoom > portraitMinimum + 0.1 }
        scenario!!.onActivity { activity ->
            assertTrue("Users should be able to return from a closer zoom", activity.findViewById<View>(R.id.btn_map_zoom_out).isEnabled)
            map!!.moveCamera(CameraUpdateFactory.zoomTo(portraitMinimum - 3.0))
        }
        awaitUi("minimum zoom clamp") { kotlin.math.abs(map!!.cameraPosition.zoom - map!!.minZoomLevel) < 0.03 }
        assertOverview(corners)
        pinchMapWithTouch(atMinimum = true)
        assertOverview(corners)

        for (zoom in listOf(10.0, 11.0, 13.0)) {
            scenario!!.onActivity {
                map!!.cancelTransitions()
                map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(14.3277, 120.9366), zoom))
            }
            awaitFullRender("geometry zoom $zoom")
            assertOriginalGeometry("intermediate zoom $zoom", definitions)
        }
        scenario!!.onActivity {
            map!!.cancelTransitions()
            map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(14.3277, 120.9366), 12.0))
        }
        awaitUi("municipal boundary zoom") { kotlin.math.abs(map!!.cameraPosition.zoom - 12.0) < 0.05 }
        awaitFullRender("export municipal boundaries")
        assertRenderedFeatures("Cavite boundaries at their exported zoom", outlineIds)
        assertOriginalGeometry("intermediate zoom 12", definitions)
        captureScreenshot("geometry-zoom12")

        scenario!!.onActivity {
            map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(14.3277, 120.9366), 17.0))
        }
        awaitUi("street zoom") { kotlin.math.abs(map!!.cameraPosition.zoom - 17.0) < 0.05 }
        awaitFullRender("street-level map")
        assertRenderedFeatures("Cavite street roads", roadIds)
        assertRenderedFeatures("Cavite street buildings", buildingIds)
        assertViewportInsideCoverage(coverage)
        assertNoSeaLanes()
        captureScreenshot("street")
        pinchMapWithTouch()
        awaitFullRender("pinch-zoomed street map")
        assertViewportInsideCoverage(coverage)
        panMapWithTouch()
        awaitFullRender("touch-panned street map")
        scenario!!.onActivity {
            assertEquals("Dragging the map must keep the user in Map Module", R.id.nav_map,
                navHost(it).navController.currentDestination?.id)
        }
        scenario!!.onActivity {
            assertTrue("Street zoom should remain available", map!!.maxZoomLevel >= 17.0)
            map!!.moveCamera(CameraUpdateFactory.zoomTo(map!!.maxZoomLevel + 2.0))
        }
        awaitUi("maximum zoom clamp") { kotlin.math.abs(map!!.cameraPosition.zoom - map!!.maxZoomLevel) < 0.03 }

        scenario!!.onActivity {
            map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(14.0576496, 120.8452137593759), 17.0))
        }
        awaitFullRender("export fuel marker")
        assertRenderedFeatures("named fuel marker", arrayOf("context_fuel_labels"))
        captureScreenshot("fuel-marker")

        scenario!!.onActivity {
            map!!.moveCamera(CameraUpdateFactory.newLatLngZoom(LatLng(coverage[3], coverage[0]), 12.0))
        }
        awaitUi("panning clamp keeps the whole viewport inside the archive") { activity ->
            viewportInsideCoverage(activity, coverage)
        }
        awaitFullRender("bounded archive edge")
        assertViewportInsideCoverage(coverage)

        scenario!!.onActivity { it.findViewById<View>(R.id.btn_map_overview).performClick() }
        awaitUi("return to overview") { kotlin.math.abs(map!!.cameraPosition.zoom - map!!.minZoomLevel) < 0.03 }
        assertOverview(corners)
        assertViewportInsideCoverage(coverage)

        requestOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE, Configuration.ORIENTATION_LANDSCAPE)
        awaitMapReady()
        awaitFullRender("landscape overview")
        assertOverview(corners)
        assertViewportInsideCoverage(coverage)
        var landscapeMinimum = 0.0
        scenario!!.onActivity { landscapeMinimum = map!!.minZoomLevel }
        assertTrue("The overview minimum should adapt to the MapView's new dimensions",
            kotlin.math.abs(landscapeMinimum - portraitMinimum) > 0.05)
        assertAdminLabels()
        assertRenderedFeatures("landscape administrative boundaries at minimum zoom", outlineIds)
        assertOriginalGeometry("landscape minimum", definitions)
        captureScreenshot("overview-landscape")

        requestOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, Configuration.ORIENTATION_PORTRAIT)
        awaitMapReady()
        awaitFullRender("restored portrait overview")
        assertOverview(corners)
        assertViewportInsideCoverage(coverage)
        captureScreenshot("overview-restored")
        scenario!!.onActivity { navHost(it).navController.navigate(R.id.nav_home) }
        awaitUi("Home after leaving map") { navHost(it).navController.currentDestination?.id == R.id.nav_home }
        scenario!!.onActivity { navHost(it).navController.navigate(R.id.nav_map) }
        awaitMapReady()
        awaitFullRender("re-entered map overview")
        assertOverview(corners)
        assertRenderedFeatures("re-entered administrative boundaries at minimum zoom", outlineIds)
        assertTrue("Map navigation must preserve user settings, profile, and saved contacts",
            savedData == snapshotSavedData(context))
    }

    private fun launchMap(context: Context): Map<String, Any?> {
        scenario = ActivityScenario.launch(MainActivity::class.java)
        awaitUi("navigation graph") { activity ->
            (activity.supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as? NavHostFragment)
                ?.navController?.currentDestination != null
        }
        scenario!!.onActivity { activity ->
            originalOrientation = activity.requestedOrientation
            val navigation = navHost(activity).navController
            if (navigation.currentDestination?.id != R.id.nav_home) navigation.navigate(R.id.nav_home)
        }
        awaitUi("Home before opening the map") { navHost(it).navController.currentDestination?.id == R.id.nav_home }
        awaitSeededDatabase(context)
        val savedData = snapshotSavedData(context)
        scenario!!.onActivity { navHost(it).navController.navigate(R.id.nav_map) }
        awaitMapReady()
        return savedData
    }

    private fun requestOrientation(requested: Int, configuration: Int) {
        scenario!!.onActivity { it.requestedOrientation = requested }
        awaitUi("orientation $configuration") { activity ->
            activity.resources.configuration.orientation == configuration &&
                activity.findViewById<MapView>(R.id.map_view)?.let {
                    it.width > 0 && it.height > 0 && !it.isLayoutRequested
                } == true
        }
    }

    private fun awaitMapReady() {
        awaitUi("offline map initialization", timeout = 120_000) { activity ->
            if (navHost(activity).navController.currentDestination?.id != R.id.nav_map) return@awaitUi false
            val view = activity.findViewById<MapView>(R.id.map_view) ?: return@awaitUi false
            if (observedView !== view) {
                observedView = view
                map = null
                completeFrames.set(0)
                view.addOnDidFinishRenderingFrameListener { fully, encodingTime, renderingTime ->
                    if (fully) completeFrames.incrementAndGet()
                    if (frameTimings.size < 10_000) {
                        frameTimings.add(FrameTiming(renderingPhase, fully, encodingTime, renderingTime))
                    }
                }
                view.getMapAsync { map = it }
            }
            view.isShown && view.width > 0 && view.height > 0 && !view.isLayoutRequested &&
                map?.style?.isFullyLoaded == true &&
                activity.findViewById<View>(R.id.progress_map).visibility == View.GONE &&
                activity.findViewById<View>(R.id.map_error_container).visibility == View.GONE &&
                map!!.minZoomLevel > 0.0
        }
    }

    private fun awaitFullRender(description: String) {
        renderingPhase = description
        val started = SystemClock.elapsedRealtime()
        val before = completeFrames.get()
        awaitUi("native full render: $description", timeout = 45_000) {
            map!!.triggerRepaint()
            completeFrames.get() > before
        }
        fullRenderTimes[description] = SystemClock.elapsedRealtime() - started
    }

    private fun assertOverview(corners: List<LatLng>) {
        scenario!!.onActivity { activity ->
            val view = activity.findViewById<MapView>(R.id.map_view)
            assertEquals("Entry zoom must equal the dynamically fitted minimum", map!!.minZoomLevel,
                map!!.cameraPosition.zoom, 0.03)
            val margin = 2f
            val projected = corners.map { map!!.projection.toScreenLocation(it) }
            assertTrue("Export must fill the left edge", projected.minOf { it.x } <= margin)
            assertTrue("Export must fill the right edge", projected.maxOf { it.x } >= view.width - margin)
            assertTrue("Export must fill the top edge", projected.minOf { it.y } <= margin)
            assertTrue("Export must fill the bottom edge", projected.maxOf { it.y } >= view.height - margin)
            val horizontalCrop = projected.minOf { it.x } < -margin && projected.maxOf { it.x } > view.width + margin
            val verticalCrop = projected.minOf { it.y } < -margin && projected.maxOf { it.y } > view.height + margin
            assertTrue("Opening view must crop the longer map dimension instead of adding blank bands",
                horizontalCrop || verticalCrop)
            assertEquals("Tilt should stay flat for a readable hazard map", 0.0, map!!.cameraPosition.tilt, 0.01)
            assertEquals("Province overview should keep north at the top", 0.0, map!!.cameraPosition.bearing, 0.01)
            assertFalse("Rotating the map should be disabled", map!!.uiSettings.isRotateGesturesEnabled)
            assertFalse("Tilting the map should be disabled", map!!.uiSettings.isTiltGesturesEnabled)
            assertTrue("Storm surge hazard control must remain accessible", activity.findViewById<View>(R.id.chip_hazard_storm_surge).isShown)
            assertFalse("Advisory controls should stay collapsed until requested",
                activity.findViewById<View>(R.id.layout_hazard_advisories).isShown)
            assertTrue("The map scale bar must be visible", activity.findViewById<View>(R.id.map_scale_container).isShown)
            assertTrue("The map scale must have a distance label",
                activity.findViewById<android.widget.TextView>(R.id.tv_map_scale).text.isNotBlank())
            assertTrue("The overview action must remain accessible", activity.findViewById<View>(R.id.btn_map_overview).isShown)
            val floatingButton = Rect().also { activity.findViewById<View>(R.id.fab_main).getGlobalVisibleRect(it) }
            for (id in listOf(R.id.btn_map_overview, R.id.btn_map_zoom_in, R.id.btn_map_zoom_out)) {
                val control = activity.findViewById<View>(id)
                val visible = Rect()
                assertTrue("Every map control must remain visible", control.getGlobalVisibleRect(visible))
                assertEquals("Control must not be clipped vertically", control.height, visible.height())
                assertFalse("Floating action must not cover a map control", Rect.intersects(visible, floatingButton))
            }
        }
    }

    private fun assertRenderedFeatures(description: String, layerIds: Array<String>) {
        awaitUi("rendered $description", timeout = 30_000) { activity ->
            val view = activity.findViewById<MapView>(R.id.map_view)
            map!!.triggerRepaint()
            map!!.queryRenderedFeatures(RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()), *layerIds).isNotEmpty()
        }
    }

    private fun assertNoRenderedFeatures(description: String, layerIds: Array<String>) {
        scenario!!.onActivity { activity ->
            val view = activity.findViewById<MapView>(R.id.map_view)
            val features = map!!.queryRenderedFeatures(
                RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()), *layerIds)
            assertTrue("Unexpected $description", features.isEmpty())
        }
    }

    private fun assertOriginalGeometry(description: String, definitions: List<JSONObject>) {
        val checkpoints = InstrumentationRegistry.getInstrumentation().context.assets
            .open("map/geometry_checkpoints.json").bufferedReader().use { JSONArray(it.readText()) }
        scenario!!.onActivity { activity ->
            val view = activity.findViewById<MapView>(R.id.map_view)
            val density = activity.resources.displayMetrics.density
            val margin = 8 * density
            val radius = 0.75f * density
            val misses = mutableListOf<String>()
            var checked = 0
            for (index in 0 until checkpoints.length()) {
                val checkpoint = checkpoints.getJSONObject(index)
                val point = map!!.projection.toScreenLocation(LatLng(
                    checkpoint.getDouble("latitude"), checkpoint.getDouble("longitude")))
                if (point.x !in margin..(view.width - margin) || point.y !in margin..(view.height - margin)) continue
                val layer = checkpoint.getString("layer")
                val ids = definitions.filter { it.optString("source-layer") == layer && it.optString("type") == "line" }
                    .map { it.getString("id") }.toTypedArray()
                val osmId = checkpoint.getString("osm_id")
                val features = map!!.queryRenderedFeatures(RectF(point.x - radius, point.y - radius,
                    point.x + radius, point.y + radius), *ids)
                checked++
                if (features.none { it.hasProperty("osm_id") && it.getStringProperty("osm_id") == osmId }) {
                    misses.add("$layer/$osmId at ${checkpoint.getDouble("longitude")},${checkpoint.getDouble("latitude")}")
                }
            }
            assertTrue("Original road/river geometry checkpoints must be on-screen at $description", checked >= 3)
            assertTrue("Roads and rivers must follow the original lines without gaps at $description ($checked checks): $misses",
                misses.isEmpty())
            Log.i("MapScreenTest", "Original geometry at $description: $checked checkpoints passed")
        }
    }

    private fun assertNoSeaLanes() {
        scenario!!.onActivity { activity ->
            val view = activity.findViewById<MapView>(R.id.map_view)
            val features = map!!.queryRenderedFeatures(RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()))
            assertTrue("A rendered map should have vector features", features.isNotEmpty())
            assertTrue("Ferry routes and sea lanes must not be rendered", features.none { feature ->
                listOf("kind", "class", "subclass", "route").any { key ->
                    feature.hasProperty(key) && feature.getStringProperty(key).equals("ferry", ignoreCase = true)
                }
            })
        }
    }

    private fun assertAdminLabels() {
        scenario!!.onActivity { activity ->
            val view = activity.findViewById<MapView>(R.id.map_view)
            val features = map!!.queryRenderedFeatures(
                RectF(0f, 0f, view.width.toFloat(), view.height.toFloat()), "cavite_admin_label_anchors_uniform")
            val names = features.map { it.getStringProperty("adm3_en") }.toSet()
            val anchors = InstrumentationRegistry.getInstrumentation().context.assets
                .open("map/admin_label_anchors.geojson").bufferedReader().use { JSONObject(it.readText()) }
                .getJSONArray("features")
            val margin = 20 * activity.resources.displayMetrics.density
            val expected = (0 until anchors.length()).mapNotNull { index ->
                val anchor = anchors.getJSONObject(index)
                val coordinates = anchor.getJSONObject("geometry").getJSONArray("coordinates")
                val point = map!!.projection.toScreenLocation(LatLng(coordinates.getDouble(1), coordinates.getDouble(0)))
                if (point.x in margin..(view.width - margin) && point.y in margin..(view.height - margin))
                    anchor.getJSONObject("properties").getString("adm3_en") else null
            }
            assertTrue("The filled view must show municipality names", expected.isNotEmpty())
            assertTrue("Every on-screen municipality must remain labeled: $expected", names.containsAll(expected))
        }
    }

    private fun assertViewportInsideCoverage(coverage: DoubleArray) {
        scenario!!.onActivity { activity ->
            assertTrue("The complete MapView must stay within packaged coverage", viewportInsideCoverage(activity, coverage))
        }
    }

    private fun viewportInsideCoverage(activity: MainActivity, coverage: DoubleArray): Boolean {
        val view = activity.findViewById<MapView>(R.id.map_view)
        val points = listOf(
            PointF(0f, 0f), PointF(view.width.toFloat(), 0f),
            PointF(0f, view.height.toFloat()), PointF(view.width.toFloat(), view.height.toFloat())
        )
        val epsilon = 0.00001
        return points.map { map!!.projection.fromScreenLocation(it) }.all {
            it.longitude >= coverage[0] - epsilon && it.longitude <= coverage[2] + epsilon &&
                it.latitude >= coverage[1] - epsilon && it.latitude <= coverage[3] + epsilon
        }
    }

    private fun panMapWithTouch() {
        renderingPhase = "native touch drag"
        awaitUi("RESQ window focus before injecting a map drag") { it.window.decorView.hasWindowFocus() }
        var startX = 0f
        var startY = 0f
        var distance = 0f
        var previousTarget: LatLng? = null
        scenario!!.onActivity { activity ->
            val view = activity.findViewById<MapView>(R.id.map_view)
            val location = IntArray(2)
            view.getLocationOnScreen(location)
            startX = location[0] + view.width * 0.35f
            startY = location[1] + view.height * 0.60f
            distance = view.width * 0.30f
            previousTarget = map!!.cameraPosition.target
        }
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        val downTime = SystemClock.uptimeMillis()
        fun inject(action: Int, x: Float) {
            val event = MotionEvent.obtain(downTime, SystemClock.uptimeMillis(), action, x, startY, 0).apply {
                source = InputDevice.SOURCE_TOUCHSCREEN
            }
            try {
                assertTrue("Map touch should be accepted", automation.injectInputEvent(event, true))
            } finally {
                event.recycle()
            }
        }
        inject(MotionEvent.ACTION_DOWN, startX)
        for (step in 1..12) {
            SystemClock.sleep(16)
            inject(MotionEvent.ACTION_MOVE, startX + distance * step / 12f)
        }
        inject(MotionEvent.ACTION_UP, startX + distance)
        awaitUi("native map drag moves the camera") { activity ->
            navHost(activity).navController.currentDestination?.id == R.id.nav_map &&
                map!!.cameraPosition.target?.let { target ->
                    kotlin.math.abs(target.longitude - previousTarget!!.longitude) > 0.000001
                } == true
        }
    }

    private fun pinchMapWithTouch(atMinimum: Boolean = false) {
        renderingPhase = "native two-finger pinch through MapView dispatch"
        var centerX = 0f
        var centerY = 0f
        var initialSpan = 0f
        var finalSpan = 0f
        var initialZoom = 0.0
        val scaleBegins = AtomicInteger()
        val scaleUpdates = AtomicInteger()
        val scaleEnds = AtomicInteger()
        val scaleListener = object : MapLibreMap.OnScaleListener {
            override fun onScaleBegin(detector: StandardScaleGestureDetector) { scaleBegins.incrementAndGet() }
            override fun onScale(detector: StandardScaleGestureDetector) { scaleUpdates.incrementAndGet() }
            override fun onScaleEnd(detector: StandardScaleGestureDetector) { scaleEnds.incrementAndGet() }
        }
        scenario!!.onActivity { activity ->
            val view = activity.findViewById<MapView>(R.id.map_view)
            val density = activity.resources.displayMetrics.density
            centerX = view.width * 0.50f
            centerY = view.height * 0.58f
            initialSpan = (200f * density).coerceAtMost(view.width * 0.60f)
            finalSpan = initialSpan * 0.45f
            initialZoom = map!!.cameraPosition.zoom
            assertTrue("Native pinch zoom gestures must be enabled", map!!.uiSettings.isZoomGesturesEnabled)
            map!!.addOnScaleListener(scaleListener)
        }
        val properties = Array(2) { index ->
            MotionEvent.PointerProperties().apply {
                id = index
                toolType = MotionEvent.TOOL_TYPE_FINGER
            }
        }
        var injectionDiagnostic = ""
        fun runSequence(): Boolean {
            val downTime = SystemClock.uptimeMillis()
            var eventTime = downTime
            var activePointers = 0
            var currentSpan = initialSpan
            var sequenceComplete = false
            fun dispatch(action: Int, span: Float, pointerCount: Int): Boolean {
                val coordinates = Array(pointerCount) { index ->
                    MotionEvent.PointerCoords().apply {
                        x = centerX + (if (index == 0) -span / 2f else span / 2f)
                        y = centerY
                        pressure = 1f
                        size = 1f
                    }
                }
                val event = MotionEvent.obtain(
                    downTime, eventTime, action, pointerCount,
                    properties.copyOfRange(0, pointerCount), coordinates,
                    0, 0, 1f, 1f, 0, 0, InputDevice.SOURCE_TOUCHSCREEN, 0
                )
                eventTime += 12
                try {
                    var accepted = false
                    // Normal widget input dispatch enters MapLibre's native gesture recognizer.
                    // Floating OS windows cannot steal this synthetic multi-pointer sequence.
                    scenario!!.onActivity { activity ->
                        accepted = activity.findViewById<MapView>(R.id.map_view).dispatchTouchEvent(event)
                    }
                    if (!accepted && action != MotionEvent.ACTION_CANCEL) {
                        injectionDiagnostic = "MapView rejected " +
                            "${MotionEvent.actionToString(action)} pointers=$pointerCount api=${Build.VERSION.SDK_INT}"
                        Log.w("MapScreenTest", injectionDiagnostic)
                    }
                    return accepted
                } finally {
                    event.recycle()
                }
            }
            try {
                if (!dispatch(MotionEvent.ACTION_DOWN, initialSpan, 1)) return false
                activePointers = 1
                SystemClock.sleep(12)
                if (!dispatch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), initialSpan, 2)) return false
                activePointers = 2
                // MapLibre requires >=0.6dp/ms to start scaling. This 200dp -> 90dp
                // span change over 120ms stays above that threshold despite main-thread dispatch overhead.
                for (step in 1..10) {
                    SystemClock.sleep(12)
                    currentSpan = initialSpan + (finalSpan - initialSpan) * step / 10f
                    if (!dispatch(MotionEvent.ACTION_MOVE, currentSpan, 2)) return false
                }
                SystemClock.sleep(12)
                if (!dispatch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT), finalSpan, 2)) return false
                activePointers = 1
                SystemClock.sleep(12)
                if (!dispatch(MotionEvent.ACTION_UP, finalSpan, 1)) return false
                activePointers = 0
                sequenceComplete = true
                return true
            } finally {
                if (!sequenceComplete && activePointers > 0) {
                    runCatching { dispatch(MotionEvent.ACTION_CANCEL, currentSpan, activePointers) }
                }
            }
        }
        try {
            val sequenceHandled = runSequence()
            assertTrue("Native MapView pinch dispatch should be handled ($injectionDiagnostic)", sequenceHandled)
            awaitUi("native two-finger pinch decreases zoom; $injectionDiagnostic, " +
                "scaleBegin=${scaleBegins.get()}, scaleUpdates=${scaleUpdates.get()}, scaleEnd=${scaleEnds.get()}") { activity ->
                navHost(activity).navController.currentDestination?.id == R.id.nav_map &&
                    (if (atMinimum) kotlin.math.abs(map!!.cameraPosition.zoom - map!!.minZoomLevel) < 0.03
                     else map!!.cameraPosition.zoom <= initialZoom - 0.2) &&
                    map!!.cameraPosition.zoom >= map!!.minZoomLevel - 0.03
            }
            assertTrue("Native scale listener should confirm pinch begin/update/end",
                scaleBegins.get() > 0 && scaleUpdates.get() > 0 && scaleEnds.get() > 0)
            // Scale inertia can outlive pointer-up. Let the gesture settle before
            // a subsequent programmatic camera move, then stop remaining transitions.
            SystemClock.sleep(500)
            scenario!!.onActivity { map!!.cancelTransitions() }
        } finally {
            var finalZoom = initialZoom
            scenario!!.onActivity {
                finalZoom = map!!.cameraPosition.zoom
                map!!.removeOnScaleListener(scaleListener)
            }
            gestureDiagnostics["pinchInputPath"] = "MapView.dispatchTouchEvent"
            gestureDiagnostics["pinchScaleBegins"] = scaleBegins.get()
            gestureDiagnostics["pinchScaleUpdates"] = scaleUpdates.get()
            gestureDiagnostics["pinchScaleEnds"] = scaleEnds.get()
            gestureDiagnostics["pinchInitialZoom"] = initialZoom
            gestureDiagnostics["pinchFinalZoom"] = finalZoom
            Log.i("MapScreenTest", "Native pinch $gestureDiagnostics")
        }
    }

    private fun awaitUi(description: String, timeout: Long = 20_000, condition: (MainActivity) -> Boolean) {
        val deadline = SystemClock.uptimeMillis() + timeout
        while (SystemClock.uptimeMillis() < deadline) {
            var ready = false
            scenario!!.onActivity { ready = condition(it) }
            if (ready) return
            SystemClock.sleep(50)
        }
        var state = ""
        scenario!!.onActivity { activity ->
            val view = activity.findViewById<MapView>(R.id.map_view)
            state = "destination=${navHost(activity).navController.currentDestination?.id}, " +
                "zoom=${map?.cameraPosition?.zoom}, min=${map?.minZoomLevel}, fullFrames=${completeFrames.get()}, " +
                "mapViewPx=${view?.width}x${view?.height}, density=${activity.resources.displayMetrics.density}, " +
                "errorShown=${activity.findViewById<View>(R.id.map_error_container)?.isShown}"
        }
        throw AssertionError("Timed out waiting for $description ($state)")
    }

    private fun captureScreenshot(name: String) {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.waitForIdleSync()
        val screenshot = instrumentation.uiAutomation.takeScreenshot()
            ?: throw AssertionError("Could not capture $name")
        try {
            val output = File(instrumentation.targetContext.getExternalFilesDir(null), "map-test-$name.png")
            FileOutputStream(output).use { assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, it)) }
        } finally {
            screenshot.recycle()
        }
    }

    private fun awaitSeededDatabase(context: Context) {
        val deadline = SystemClock.uptimeMillis() + 30_000
        while (SystemClock.uptimeMillis() < deadline) {
            val ready = runCatching {
                val database = context.getDatabasePath("resq_database")
                if (!database.exists()) return@runCatching false
                SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
                    db.rawQuery("SELECT (SELECT COUNT(*) FROM user_settings), " +
                        "(SELECT COUNT(*) FROM user_profile), (SELECT COUNT(*) FROM emergency_contacts)", null).use { cursor ->
                        cursor.moveToFirst() && (0..2).all { cursor.getInt(it) > 0 }
                    }
                }
            }.getOrDefault(false)
            if (ready) return
            SystemClock.sleep(50)
        }
        throw AssertionError("Timed out waiting for Room to initialize settings, profile, and bundled contacts")
    }

    private fun snapshotSavedData(context: Context): Map<String, Any?> {
        val result = linkedMapOf<String, Any?>()
        val preferenceDirectory = File(context.applicationInfo.dataDir, "shared_prefs")
        preferenceDirectory.listFiles()?.filter { it.extension == "xml" }?.sortedBy { it.name }?.forEach { file ->
            result["preferences:${file.name}"] = context.getSharedPreferences(file.nameWithoutExtension, Context.MODE_PRIVATE).all.toMap()
        }
        val database = context.getDatabasePath("resq_database")
        assertTrue("Room database should be initialized before map navigation", database.exists())
        SQLiteDatabase.openDatabase(database.absolutePath, null, SQLiteDatabase.OPEN_READONLY).use { db ->
            val settingsColumns = "id,is_dark_theme,font_size,is_tts_enabled,tts_speed,tts_pitch," +
                "is_voice_command_enabled,tips_preference,preferred_language,has_completed_onboarding," +
                "terms_accepted,privacy_accepted"
            for ((key, query) in listOf(
                "settings" to "SELECT $settingsColumns FROM user_settings ORDER BY id",
                "profile" to "SELECT * FROM user_profile ORDER BY id",
                "customContacts" to "SELECT * FROM emergency_contacts WHERE is_custom = 1 ORDER BY id"
            )) {
                db.rawQuery(query, null).use { cursor ->
                    result[key] = buildList {
                        while (cursor.moveToNext()) add((0 until cursor.columnCount).map { column ->
                            if (cursor.isNull(column)) null else cursor.getString(column)
                        })
                    }
                }
            }
        }
        return result
    }

    private fun writePerformanceReport() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val timings = synchronized(frameTimings) { frameTimings.toList() }
        val report = JSONObject().apply {
            put("device", "${Build.MANUFACTURER} ${Build.MODEL}")
            put("androidApi", Build.VERSION.SDK_INT)
            put("frameTimingUnits", "Raw SDK values; MapLibre Android 11.8.5 Java documentation does not specify units")
            put("wallClockMillisecondsToFullRender", JSONObject(fullRenderTimes as Map<*, *>))
            put("framesObserved", timings.size)
            put("fullyRenderedFramesObserved", timings.count { it.fully })
            put("gestureDiagnostics", JSONObject(gestureDiagnostics as Map<*, *>))
            put("frameSamples", JSONArray().apply {
                timings.forEach { timing ->
                    put(JSONObject().apply {
                        put("phase", timing.phase)
                        put("fully", timing.fully)
                        put("encodingTimeRaw", if (timing.encoding.isFinite()) timing.encoding else JSONObject.NULL)
                        put("renderingTimeRaw", if (timing.rendering.isFinite()) timing.rendering else JSONObject.NULL)
                    })
                }
            })
        }
        File(context.getExternalFilesDir(null), "map-test-performance.json").writeText(report.toString(2))
        Log.i("MapScreenTest", "Native frames=${timings.size}, fully=${timings.count { it.fully }}, " +
            "full-render wall-clock timings ms=$fullRenderTimes")
    }

    private data class FrameTiming(val phase: String, val fully: Boolean, val encoding: Double, val rendering: Double)

    private fun navHost(activity: MainActivity) = activity.supportFragmentManager
        .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
}
