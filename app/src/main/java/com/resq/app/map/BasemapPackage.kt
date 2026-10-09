package com.resq.app.map

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The generated archive and camera metadata are released together. */
data class BasemapPackage(
    val version: Int,
    val archive: String,
    val sizeBytes: Long,
    val sha256: String,
    val boundary: MapCameraPolicy.Bounds,
    val coverage: MapCameraPolicy.Bounds,
    val minTileZoom: Int,
    val maxTileZoom: Int
) {
    companion object {
        fun load(context: Context): BasemapPackage {
            val json = context.assets.open("map/cavite_basemap.json").bufferedReader().use {
                JSONObject(it.readText())
            }
            val archive = json.getString("archive")
            require(archive.matches(Regex("[a-zA-Z0-9_-]+\\.pmtiles")))
            val version = json.getInt("version")
            val size = json.getLong("sizeBytes")
            val hash = json.getString("sha256").lowercase()
            require(version > 0 && size > 127 && hash.matches(Regex("[0-9a-f]{64}")))
            val minZoom = json.getInt("minTileZoom")
            val maxZoom = json.getInt("maxTileZoom")
            require(minZoom in 0..maxZoom && maxZoom in 14..22)
            val boundary = json.getJSONArray("boundary").bounds()
            val coverage = json.getJSONArray("coverage").bounds()
            require(coverage.west < boundary.west && coverage.east > boundary.east &&
                coverage.south < boundary.south && coverage.north > boundary.north)
            return BasemapPackage(version, archive, size, hash, boundary, coverage, minZoom, maxZoom)
        }

        private fun JSONArray.bounds(): MapCameraPolicy.Bounds {
            require(length() == 4)
            return MapCameraPolicy.Bounds(getDouble(0), getDouble(1), getDouble(2), getDouble(3))
        }
    }

    fun loadStyle(context: Context, archiveFile: File): String =
        context.assets.open("map/cavite_style.json").bufferedReader().use { it.readText() }
            .replace("{BASEMAP_PATH}", archiveFile.absolutePath)
}
