package com.resq.app.map

import com.resq.app.R

data class HazardLayer(
    val id: String,
    val sourceLayer: String,
    val attr: String,
    val displayNameRes: Int,
    val hazardType: HazardType
)

enum class HazardType { FLOOD, LANDSLIDE, DEBRIS_FLOW, STORM_SURGE }

object MapLayerController {
    const val SRC = "noah"

    val layers: List<HazardLayer> = listOf(
        HazardLayer("haz-flood-5yr",     "flood_5yr",         "Var", R.string.map_layer_flood_5yr,     HazardType.FLOOD),
        HazardLayer("haz-flood-25yr",    "flood_25yr",        "Var", R.string.map_layer_flood_25yr,    HazardType.FLOOD),
        HazardLayer("haz-flood-100yr",   "flood_100yr",       "Var", R.string.map_layer_flood_100yr,   HazardType.FLOOD),
        HazardLayer("haz-landslide",     "landslide",         "HAZ", R.string.map_layer_landslide,     HazardType.LANDSLIDE),
        HazardLayer("haz-debris-flow",   "debris_flow",       "HAZ", R.string.map_layer_debris_flow,   HazardType.DEBRIS_FLOW),
        HazardLayer("haz-ssa1",          "storm_surge_ssa1",  "HAZ", R.string.map_layer_ssa1,          HazardType.STORM_SURGE),
        HazardLayer("haz-ssa2",          "storm_surge_ssa2",  "HAZ", R.string.map_layer_ssa2,          HazardType.STORM_SURGE),
        HazardLayer("haz-ssa3",          "storm_surge_ssa3",  "HAZ", R.string.map_layer_ssa3,          HazardType.STORM_SURGE),
        HazardLayer("haz-ssa4",          "storm_surge_ssa4",  "HAZ", R.string.map_layer_ssa4,          HazardType.STORM_SURGE)
    )

    fun get(id: String): HazardLayer? = layers.firstOrNull { it.id == id }
}