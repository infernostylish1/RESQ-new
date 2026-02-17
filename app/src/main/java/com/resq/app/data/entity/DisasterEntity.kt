package com.resq.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a disaster category.
 * Contains basic information about each disaster type.
 */
@Entity(tableName = "disasters")
data class DisasterEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "name_tagalog")
    val nameTagalog: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "description_tagalog")
    val descriptionTagalog: String,

    @ColumnInfo(name = "icon_res_name")
    val iconResName: String,

    @ColumnInfo(name = "image_res_name")
    val imageResName: String,

    @ColumnInfo(name = "is_recommended_near_water")
    val isRecommendedNearWater: Boolean = false,

    @ColumnInfo(name = "is_recommended_mountainous")
    val isRecommendedMountainous: Boolean = false,

    @ColumnInfo(name = "is_recommended_landslide")
    val isRecommendedLandslide: Boolean = false,

    @ColumnInfo(name = "is_recommended_flood")
    val isRecommendedFlood: Boolean = false,

    @ColumnInfo(name = "is_recommended_volcano")
    val isRecommendedVolcano: Boolean = false,

    @ColumnInfo(name = "is_recommended_urban")
    val isRecommendedUrban: Boolean = false,

    @ColumnInfo(name = "is_recommended_coastal")
    val isRecommendedCoastal: Boolean = false,

    @ColumnInfo(name = "is_universal")
    val isUniversal: Boolean = false,

    @ColumnInfo(name = "display_order")
    val displayOrder: Int = 0
)