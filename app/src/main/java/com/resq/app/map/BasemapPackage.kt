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
    val maxTileZoom: Int,
    val archives: List<Archive>,
    val styleAsset: String,
    val hazardArchives: List<Archive>
) {
    data class Archive(val archive: String, val sizeBytes: Long, val sha256: String)

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
            require(coverage.west <= boundary.west && coverage.east >= boundary.east &&
                coverage.south <= boundary.south && coverage.north >= boundary.north)
            val archives = json.getJSONArray("archives").let { entries ->
                (0 until entries.length()).map { index ->
                    val entry = entries.getJSONObject(index)
                    val name = entry.getString("archive")
                    val bytes = entry.getLong("sizeBytes")
                    val checksum = entry.getString("sha256").lowercase()
                    require(name.matches(Regex("[a-zA-Z0-9_-]+\\.pmtiles")))
                    require(bytes > 127 && checksum.matches(Regex("[0-9a-f]{64}")))
                    Archive(name, bytes, checksum)
                }
            }
            require(archives.map { it.archive }.distinct().size == archives.size)
            require(Archive(archive, size, hash) in archives)
            val styleAsset = json.getString("style")
            require(styleAsset.matches(Regex("[a-zA-Z0-9_-]+\\.json")))
            val hazardArchives = json.optJSONArray("hazards")?.let { entries ->
                (0 until entries.length()).map { index ->
                    val entry = entries.getJSONObject(index)
                    val name = entry.getString("archive")
                    val bytes = entry.getLong("sizeBytes")
                    val checksum = entry.getString("sha256").lowercase()
                    require(name.matches(Regex("[a-zA-Z0-9_-]+\\.pmtiles")))
                    require(bytes > 127 && checksum.matches(Regex("[0-9a-f]{64}")))
                    Archive(name, bytes, checksum)
                }
            } ?: emptyList()
            require(hazardArchives.map { it.archive }.distinct().size == hazardArchives.size)
            return BasemapPackage(version, archive, size, hash, boundary, coverage, minZoom, maxZoom,
                archives, styleAsset, hazardArchives)
        }

        private fun JSONArray.bounds(): MapCameraPolicy.Bounds {
            require(length() == 4)
            return MapCameraPolicy.Bounds(getDouble(0), getDouble(1), getDouble(2), getDouble(3))
        }
    }

    fun loadStyle(context: Context, archiveFile: File): String =
        context.assets.open("map/$styleAsset").bufferedReader().use { it.readText() }
            .replace("{ARCHIVE_DIRECTORY}", archiveFile.parentFile!!.absolutePath)
}
