package com.resq.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MapCameraPolicyTest {
    private val province = MapCameraPolicy.Bounds(120.562081789, 14.05654967, 121.077498833, 14.500089418)
    private val coverage = MapCameraPolicy.Bounds(120.5319102, 14.0392096, 121.1117325, 14.519209)
    private val screens = listOf(360.0 to 700.0, 800.0 to 280.0, 1280.0 to 800.0,
        320.0 to 1000.0, 876.0 to 180.0, 876.0 to 206.0)

    @Test fun openingViewFillsPortraitLandscapeAndTabletWithoutBlankBands() {
        screens.forEach { (width, height) ->
            val camera = MapCameraPolicy.cover(coverage, width, height)
            assertWithin(MapCameraPolicy.viewport(camera, width, height), coverage)
            // Zooming out even slightly would expose the rectangular export edge.
            val zoomedOut = MapCameraPolicy.viewport(camera.copy(zoom = camera.zoom - 0.1), width, height)
            assertFalse("Opening zoom should be only enough to fill the viewport", inside(zoomedOut, coverage))
            assertEquals((coverage.west + coverage.east) / 2.0, camera.longitude, 0.0000001)
        }
    }

    @Test fun tallPhoneCropsWidthAndWidePhoneCropsHeightWithoutStretching() {
        val portrait = MapCameraPolicy.viewport(MapCameraPolicy.cover(coverage, 360.0, 700.0), 360.0, 700.0)
        assertTrue(portrait.west > coverage.west && portrait.east < coverage.east)
        assertEquals(coverage.north, portrait.north, 0.003)
        assertEquals(coverage.south, portrait.south, 0.003)
        val landscape = MapCameraPolicy.viewport(MapCameraPolicy.cover(coverage, 800.0, 280.0), 800.0, 280.0)
        assertTrue(landscape.south > coverage.south && landscape.north < coverage.north)
        assertEquals(coverage.west, landscape.west, 0.003)
        assertEquals(coverage.east, landscape.east, 0.003)
    }

    @Test fun panLimitsKeepEveryScreenCornerInsideTheExportAtMinimumAndStreetZoom() {
        screens.forEach { (width, height) ->
            val minimum = MapCameraPolicy.cover(coverage, width, height).zoom
            listOf(minimum, 12.0, 15.0, 18.0).forEach { zoom ->
                val bounds = MapCameraPolicy.targetBounds(coverage, province, width, height, zoom)
                assertWithin(bounds, province)
                listOf(bounds.west to bounds.south, bounds.east to bounds.north,
                    bounds.west to bounds.north, bounds.east to bounds.south).forEach { (lon, lat) ->
                    assertWithin(MapCameraPolicy.viewport(MapCameraPolicy.Camera(lon, lat, zoom), width, height), coverage)
                }
            }
        }
    }

    @Test fun attemptedPanAndZoomOutClampToTheFilledMinimumView() {
        val overview = MapCameraPolicy.cover(coverage, 360.0, 700.0)
        val bounds = MapCameraPolicy.targetBounds(coverage, province, 360.0, 700.0, overview.zoom)
        val constrained = MapCameraPolicy.constrain(MapCameraPolicy.Camera(125.0, 10.0, 1.0), bounds, overview.zoom, 18.0)
        assertEquals(bounds.east, constrained.longitude, 0.0000001)
        assertEquals(bounds.south, constrained.latitude, 0.0000001)
        assertEquals(overview.zoom, constrained.zoom, 0.0000001)
        assertWithin(MapCameraPolicy.viewport(constrained, 360.0, 700.0), coverage)
    }

    @Test fun streetZoomStillAllowsExploringProvinceEdgesAndCorregidor() {
        screens.forEach { (width, height) ->
            val bounds = MapCameraPolicy.targetBounds(coverage, province, width, height, 17.0)
            val constrained = MapCameraPolicy.constrain(
                MapCameraPolicy.Camera(coverage.west, coverage.north, 17.0), bounds, 7.0, 18.0)
            assertEquals(province.west, constrained.longitude, 0.0000001)
            assertEquals(province.north, constrained.latitude, 0.0000001)
            assertWithin(MapCameraPolicy.viewport(constrained, width, height), coverage)
            val island = MapCameraPolicy.constrain(MapCameraPolicy.Camera(120.575, 14.383, 17.0), bounds, 7.0, 18.0)
            assertEquals(120.575, island.longitude, 0.0000001)
            assertEquals(14.383, island.latitude, 0.0000001)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsZoomThatWouldExposeTheExportEdges() {
        MapCameraPolicy.targetBounds(coverage, province, 360.0, 700.0, 7.0)
    }

    private fun inside(inner: MapCameraPolicy.Bounds, outer: MapCameraPolicy.Bounds): Boolean =
        inner.west >= outer.west - 0.0000001 && inner.east <= outer.east + 0.0000001 &&
            inner.south >= outer.south - 0.0000001 && inner.north <= outer.north + 0.0000001

    private fun assertWithin(inner: MapCameraPolicy.Bounds, outer: MapCameraPolicy.Bounds) {
        assertTrue("Viewport must stay inside the exported bounds: $inner", inside(inner, outer))
    }
}
