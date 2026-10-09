package com.resq.app.map

import kotlin.math.atan
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** North-up, flat Mercator camera math. Dimensions use MapLibre's density-independent pixels. */
object MapCameraPolicy {
    private const val WORLD_SIZE = 512.0
    private const val MAX_LATITUDE = 85.0511287798066

    data class Bounds(val west: Double, val south: Double, val east: Double, val north: Double) {
        init {
            require(listOf(west, south, east, north).all { it.isFinite() })
            require(west >= -180.0 && east <= 180.0 && west < east)
            require(south >= -MAX_LATITUDE && north <= MAX_LATITUDE && south < north)
        }
    }

    data class Padding(val left: Double, val top: Double, val right: Double, val bottom: Double)
    data class Camera(val longitude: Double, val latitude: Double, val zoom: Double)

    /** Corner controls leave the center free in landscape, so short views need less vertical padding. */
    fun overviewPadding(focus: Bounds, coverage: Bounds, width: Double, height: Double): Padding {
        if (width <= height) {
            return Padding(20.0, min(72.0, height * 0.22), 20.0, min(32.0, height * 0.12))
        }
        var padding = Padding(20.0, min(24.0, height * 0.10), 20.0, min(16.0, height * 0.08))
        val coverageMinimum = coverageZoom(coverage, width + 2.0, height + 2.0)
        repeat(8) {
            if (fit(focus, width, height, padding).zoom > coverageMinimum) return padding
            padding = padding.copy(top = padding.top / 2.0, bottom = padding.bottom / 2.0)
        }
        return padding.copy(top = 0.0, bottom = 0.0)
    }

    fun fit(bounds: Bounds, width: Double, height: Double, padding: Padding): Camera {
        val usableWidth = width - padding.left - padding.right
        val usableHeight = height - padding.top - padding.bottom
        require(usableWidth > 0.0 && usableHeight > 0.0)
        val west = longitudeX(bounds.west)
        val east = longitudeX(bounds.east)
        val north = latitudeY(bounds.north)
        val south = latitudeY(bounds.south)
        val scale = min(usableWidth / (east - west), usableHeight / (south - north))
        val zoom = ln(scale / WORLD_SIZE) / ln(2.0)
        // Shift the camera so the province sits in the unobscured portion of the view.
        val centerX = (west + east) / 2.0 - (padding.left - padding.right) / (2.0 * scale)
        val centerY = (north + south) / 2.0 - (padding.top - padding.bottom) / (2.0 * scale)
        return Camera(xLongitude(centerX), yLatitude(centerY), zoom)
    }

    /** Minimum zoom needed to keep the complete screen inside the archive's data coverage. */
    fun coverageZoom(coverage: Bounds, width: Double, height: Double): Double {
        require(width > 0.0 && height > 0.0)
        val scale = max(
            width / (longitudeX(coverage.east) - longitudeX(coverage.west)),
            height / (latitudeY(coverage.south) - latitudeY(coverage.north))
        )
        return ln(scale / WORLD_SIZE) / ln(2.0)
    }

    /** Province-focused camera targets also keep the entire viewport inside the backdrop coverage. */
    fun targetBounds(coverage: Bounds, focus: Bounds, width: Double, height: Double, zoom: Double): Bounds {
        val scale = WORLD_SIZE * 2.0.pow(zoom)
        // One logical pixel of safety accommodates native rounding at the tile edge.
        val halfWidth = (width / 2.0 + 1.0) / scale
        val halfHeight = (height / 2.0 + 1.0) / scale
        val west = max(longitudeX(coverage.west) + halfWidth, longitudeX(focus.west))
        val east = min(longitudeX(coverage.east) - halfWidth, longitudeX(focus.east))
        val north = max(latitudeY(coverage.north) + halfHeight, latitudeY(focus.north))
        val south = min(latitudeY(coverage.south) - halfHeight, latitudeY(focus.south))
        require(west < east && north < south) { "Map coverage cannot contain this viewport" }
        return Bounds(xLongitude(west), yLatitude(south), xLongitude(east), yLatitude(north))
    }

    fun constrain(camera: Camera, targetBounds: Bounds, minimumZoom: Double, maximumZoom: Double): Camera =
        Camera(
            camera.longitude.coerceIn(targetBounds.west, targetBounds.east),
            camera.latitude.coerceIn(targetBounds.south, targetBounds.north),
            camera.zoom.coerceIn(minimumZoom, maximumZoom)
        )

    fun viewport(camera: Camera, width: Double, height: Double): Bounds {
        val scale = WORLD_SIZE * 2.0.pow(camera.zoom)
        val x = longitudeX(camera.longitude)
        val y = latitudeY(camera.latitude)
        return Bounds(
            xLongitude(x - width / (2.0 * scale)),
            yLatitude(y + height / (2.0 * scale)),
            xLongitude(x + width / (2.0 * scale)),
            yLatitude(y - height / (2.0 * scale))
        )
    }

    fun fitsWithinPadding(camera: Camera, bounds: Bounds, width: Double, height: Double, padding: Padding): Boolean {
        val scale = WORLD_SIZE * 2.0.pow(camera.zoom)
        val centerX = longitudeX(camera.longitude)
        val centerY = latitudeY(camera.latitude)
        val westPixel = width / 2.0 + (longitudeX(bounds.west) - centerX) * scale
        val eastPixel = width / 2.0 + (longitudeX(bounds.east) - centerX) * scale
        val northPixel = height / 2.0 + (latitudeY(bounds.north) - centerY) * scale
        val southPixel = height / 2.0 + (latitudeY(bounds.south) - centerY) * scale
        return westPixel >= padding.left - 0.000001 && eastPixel <= width - padding.right + 0.000001 &&
            northPixel >= padding.top - 0.000001 && southPixel <= height - padding.bottom + 0.000001
    }

    internal fun longitudeX(longitude: Double) = (longitude + 180.0) / 360.0
    internal fun latitudeY(latitude: Double): Double {
        val sine = sin(Math.toRadians(latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)))
        return 0.5 - ln((1.0 + sine) / (1.0 - sine)) / (4.0 * Math.PI)
    }
    private fun xLongitude(x: Double) = x * 360.0 - 180.0
    private fun yLatitude(y: Double) = Math.toDegrees(2.0 * atan(exp((0.5 - y) * 2.0 * Math.PI)) - Math.PI / 2.0)
}
