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

    data class Camera(val longitude: Double, val latitude: Double, val zoom: Double)

    /** Fill the MapView, cropping the longer map dimension without distorting geography. */
    fun cover(bounds: Bounds, width: Double, height: Double): Camera = Camera(
        (bounds.west + bounds.east) / 2.0,
        yLatitude((latitudeY(bounds.north) + latitudeY(bounds.south)) / 2.0),
        // Two logical pixels per edge keep native rounding and pan limits inside the export.
        coverageZoom(bounds, width + 4.0, height + 4.0)
    )

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

    internal fun longitudeX(longitude: Double) = (longitude + 180.0) / 360.0
    internal fun latitudeY(latitude: Double): Double {
        val sine = sin(Math.toRadians(latitude.coerceIn(-MAX_LATITUDE, MAX_LATITUDE)))
        return 0.5 - ln((1.0 + sine) / (1.0 - sine)) / (4.0 * Math.PI)
    }
    private fun xLongitude(x: Double) = x * 360.0 - 180.0
    private fun yLatitude(y: Double) = Math.toDegrees(2.0 * atan(exp((0.5 - y) * 2.0 * Math.PI)) - Math.PI / 2.0)
}
