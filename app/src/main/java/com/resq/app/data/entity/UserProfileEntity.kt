package com.resq.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing the user's profile based on onboarding answers.
 * Used to personalize disaster recommendations throughout the app.
 *
 * This table should only contain one row (the current user's profile).
 */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: Int = 1,

    @ColumnInfo(name = "is_near_water")
    val isNearWater: Boolean = false,

    @ColumnInfo(name = "is_in_mountainous_area")
    val isInMountainousArea: Boolean = false,

    @ColumnInfo(name = "is_in_landslide_prone_area")
    val isInLandslideProne: Boolean = false,

    @ColumnInfo(name = "is_in_flood_prone_area")
    val isInFloodProne: Boolean = false,

    @ColumnInfo(name = "is_near_volcano")
    val isNearVolcano: Boolean = false,

    @ColumnInfo(name = "is_in_urban_area")
    val isInUrbanArea: Boolean = false,

    @ColumnInfo(name = "is_in_coastal_area")
    val isInCoastalArea: Boolean = false,

    @ColumnInfo(name = "onboarding_completed")
    val onboardingCompleted: Boolean = false,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis(),

    @ColumnInfo(name = "updated_at")
    val updatedAt: Long = System.currentTimeMillis()
)