package com.resq.app.data.repository

import com.resq.app.data.dao.UserProfileDao
import com.resq.app.data.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for user profile operations.
 * Handles onboarding data and disaster risk profile.
 * Normalized method names to match ViewModel expectations.
 */
class UserProfileRepository(
    private val userProfileDao: UserProfileDao
) {

    fun getUserProfile(): Flow<UserProfileEntity?> =
        userProfileDao.getUserProfile()

    suspend fun getUserProfileOnce(): UserProfileEntity? =
        userProfileDao.getUserProfileOnce()

    suspend fun isOnboardingCompleted(): Boolean =
        userProfileDao.isOnboardingCompleted() ?: false

    /**
     * Insert if not exists, update if exists (upsert).
     * Used by OnboardingViewModel and SettingsViewModel.
     */
    suspend fun insertOrUpdateProfile(profile: UserProfileEntity) {
        val existing = userProfileDao.getUserProfileOnce()
        if (existing == null) {
            userProfileDao.insertProfile(profile)
        } else {
            userProfileDao.updateProfile(profile.copy(id = existing.id))
        }
    }

    /** Legacy insert — kept for direct DAO access if needed */
    suspend fun insertProfile(profile: UserProfileEntity) =
        userProfileDao.insertProfile(profile)

    /** Legacy update */
    suspend fun updateProfile(profile: UserProfileEntity) =
        userProfileDao.updateProfile(profile)

    suspend fun updateOnboardingAnswers(
        isNearWater: Boolean,
        isMountainous: Boolean,
        isLandslide: Boolean,
        isFlood: Boolean,
        isVolcano: Boolean,
        isUrban: Boolean,
        isCoastal: Boolean
    ) {
        userProfileDao.updateOnboardingAnswers(
            isNearWater  = isNearWater,
            isMountainous = isMountainous,
            isLandslide  = isLandslide,
            isFlood      = isFlood,
            isVolcano    = isVolcano,
            isUrban      = isUrban,
            isCoastal    = isCoastal
        )
    }

    suspend fun resetOnboarding() =
        userProfileDao.resetOnboarding()

    suspend fun deleteProfile() =
        userProfileDao.deleteProfile()
}