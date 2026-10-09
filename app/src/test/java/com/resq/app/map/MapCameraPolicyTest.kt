package com.resq.app.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

class MapCameraPolicyTest {
    private val province = MapCameraPolicy.Bounds(120.562081789, 14.05654967, 121.077498833, 14.500089418)
    private val coverage = MapCameraPolicy.Bounds(119.4, 13.2, 122.0, 15.2)
    private val screens = listOf(360.0 to 700.0, 800.0 to 280.0, 1280.0 to 800.0,
        320.0 to 1000.0, 876.0 to 180.0, 876.0 to 206.0)

    @Test fun fullProvinceFitsInsideOverlayPaddingOnPhoneLandscapeAndTablet() {
        screens.forEach { (width, height) ->
            val padding = padding(width, height)
            val fitted = MapCameraPolicy.fit(province, width, height, padding)
            val camera = MapCameraPolicy.constrain(fitted,
                MapCameraPolicy.targetBounds(coverage, province, width, height, fitted.zoom), fitted.zoom, 18.0)
            val scale = 512.0 * 2.0.pow(camera.zoom)
            val centerX = MapCameraPolicy.longitudeX(camera.longitude)
            val centerY = MapCameraPolicy.latitudeY(camera.latitude)
            val westPixel = width / 2.0 + (MapCameraPolicy.longitudeX(province.west) - centerX) * scale
            val eastPixel = width / 2.0 + (MapCameraPolicy.longitudeX(province.east) - centerX) * scale
            val northPixel = height / 2.0 + (MapCameraPolicy.latitudeY(province.north) - centerY) * scale
            val southPixel = height / 2.0 + (MapCameraPolicy.latitudeY(province.south) - centerY) * scale
            assertTrue("West edge at $width x $height", westPixel >= padding.left - 0.000001)
            assertTrue("East edge at $width x $height", eastPixel <= width - padding.right + 0.000001)
            assertTrue("North edge at $width x $height", northPixel >= padding.top - 0.000001)
            assertTrue("South edge at $width x $height", southPixel <= height - padding.bottom + 0.000001)
            assertTrue(MapCameraPolicy.fitsWithinPadding(camera, province, width, height, padding))
            assertTrue(camera.zoom > MapCameraPolicy.coverageZoom(coverage, width + 2.0, height + 2.0))
            assertWithin(MapCameraPolicy.viewport(camera, width, height), coverage)
        }
    }

    @Test fun panLimitsKeepEveryScreenCornerInsideArchiveAtOverviewAndStreetZoom() {
        screens.forEach { (width, height) ->
            val overview = MapCameraPolicy.fit(province, width, height, padding(width, height))
            listOf(overview.zoom, 12.0, 15.0, 18.0).forEach { zoom ->
                val bounds = MapCameraPolicy.targetBounds(coverage, province, width, height, zoom)
                assertWithin(bounds, province)
                listOf(bounds.west to bounds.south, bounds.east to bounds.north,
                    bounds.west to bounds.north, bounds.east to bounds.south).forEach { (lon, lat) ->
                    assertWithin(MapCameraPolicy.viewport(MapCameraPolicy.Camera(lon, lat, zoom), width, height), coverage)
                }
            }
        }
    }

    @Test fun attemptedPanAndZoomOutClampToSafeTargetAndOverviewZoom() {
        val overview = MapCameraPolicy.fit(province, 360.0, 700.0, padding(360.0, 700.0))
        val bounds = MapCameraPolicy.targetBounds(coverage, province, 360.0, 700.0, overview.zoom)
        val constrained = MapCameraPolicy.constrain(MapCameraPolicy.Camera(125.0, 10.0, 1.0), bounds, overview.zoom, 18.0)
        assertEquals(bounds.east, constrained.longitude, 0.0000001)
        assertEquals(bounds.south, constrained.latitude, 0.0000001)
        assertEquals(overview.zoom, constrained.zoom, 0.0000001)
        assertWithin(MapCameraPolicy.viewport(constrained, 360.0, 700.0), coverage)
    }

    @Test fun streetZoomAllowsEveryProvinceEdgeWhileOverviewRemainsViewportSafe() {
        val overview = MapCameraPolicy.fit(province, 320.0, 1000.0, padding(320.0, 1000.0))
        val overviewBounds = MapCameraPolicy.targetBounds(coverage, province, 320.0, 1000.0, overview.zoom)
        val streetBounds = MapCameraPolicy.targetBounds(coverage, province, 320.0, 1000.0, 18.0)
        assertEquals(province.west, streetBounds.west, 0.0000001)
        assertEquals(province.east, streetBounds.east, 0.0000001)
        assertEquals(province.south, streetBounds.south, 0.0000001)
        assertEquals(province.north, streetBounds.north, 0.0000001)
        assertTrue(streetBounds.south < overviewBounds.south)
        assertTrue(streetBounds.north > overviewBounds.north)
    }

    @Test fun largePanToBackdropEdgeClampsToProvinceEdgeAtEveryScreenSize() {
        screens.forEach { (width, height) ->
            val bounds = MapCameraPolicy.targetBounds(coverage, province, width, height, 17.0)
            val constrained = MapCameraPolicy.constrain(
                MapCameraPolicy.Camera(coverage.west, coverage.north, 17.0), bounds, 7.0, 18.0)
            assertEquals(province.west, constrained.longitude, 0.0000001)
            assertEquals(province.north, constrained.latitude, 0.0000001)
            assertWithin(MapCameraPolicy.viewport(constrained, width, height), coverage)
            // Corregidor's western part of the province remains accessible at street zoom.
            val island = MapCameraPolicy.constrain(MapCameraPolicy.Camera(120.575, 14.383, 17.0), bounds, 7.0, 18.0)
            assertEquals(120.575, island.longitude, 0.0000001)
            assertEquals(14.383, island.latitude, 0.0000001)
        }
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsCoverageSmallerThanViewportInsteadOfAllowingBlankEdges() {
        MapCameraPolicy.targetBounds(province, province, 360.0, 700.0, 7.0)
    }

    @Test fun shortLandscapeReducesPaddingWhilePortraitKeepsRoomForControls() {
        val shortLandscape = padding(876.0, 180.0)
        assertEquals(9.0, shortLandscape.top, 0.000001)
        assertEquals(7.2, shortLandscape.bottom, 0.000001)
        val largerLandscape = padding(876.0, 206.0)
        assertEquals(20.6, largerLandscape.top, 0.000001)
        assertEquals(16.0, largerLandscape.bottom, 0.000001)
        val portrait = padding(360.0, 700.0)
        assertEquals(72.0, portrait.top, 0.000001)
        assertEquals(32.0, portrait.bottom, 0.000001)
    }

    private fun padding(width: Double, height: Double) =
        MapCameraPolicy.overviewPadding(province, coverage, width, height)

    private fun assertWithin(inner: MapCameraPolicy.Bounds, outer: MapCameraPolicy.Bounds) {
        assertTrue("West viewport edge", inner.west >= outer.west - 0.0000001)
        assertTrue("East viewport edge", inner.east <= outer.east + 0.0000001)
        assertTrue("South viewport edge", inner.south >= outer.south - 0.0000001)
        assertTrue("North viewport edge", inner.north <= outer.north + 0.0000001)
    }
}
