package com.resq.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.resq.app.data.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for UserProfile operations.
 * Handles user onboarding data and disaster risk profile.
 */
@Dao
interface UserProfileDao {

    @Query("SELECT * FROM user_profile WHERE id = 1")
    fun getUserProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profile WHERE id = 1")
    suspend fun getUserProfileOnce(): UserProfileEntity?

    @Query("SELECT onboarding_completed FROM user_profile WHERE id = 1")
    suspend fun isOnboardingCompleted(): Boolean?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity)

    @Update
    suspend fun updateProfile(profile: UserProfileEntity)

    @Query("""
        UPDATE user_profile SET 
            is_near_water = :isNearWater,
            is_in_mountainous_area = :isMountainous,
            is_in_landslide_prone_area = :isLandslide,
            is_in_flood_prone_area = :isFlood,
            is_near_volcano = :isVolcano,
            is_in_urban_area = :isUrban,
            is_in_coastal_area = :isCoastal,
            onboarding_completed = 1,
            updated_at = :updatedAt
        WHERE id = 1
    """)
    suspend fun updateOnboardingAnswers(
        isNearWater: Boolean,
        isMountainous: Boolean,
        isLandslide: Boolean,
        isFlood: Boolean,
        isVolcano: Boolean,
        isUrban: Boolean,
        isCoastal: Boolean,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE user_profile SET onboarding_completed = 0, updated_at = :updatedAt WHERE id = 1")
    suspend fun resetOnboarding(updatedAt: Long = System.currentTimeMillis())

    @Query("DELETE FROM user_profile")
    suspend fun deleteProfile()
}