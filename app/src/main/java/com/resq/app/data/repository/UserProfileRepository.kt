package com.resq.app.data.repository

import com.resq.app.data.dao.UserProfileDao
import com.resq.app.data.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Repository for user profile operations.
 * Handles onboarding data and disaster risk profile.
 */
@Singleton
class UserProfileRepository @Inject constructor(
    private val userProfileDao: UserProfileDao
) {

    fun getUserProfile(): Flow<UserProfileEntity?> = userProfileDao.getUserProfile()

    suspend fun getUserProfileOnce(): UserProfileEntity? = userProfileDao.getUserProfileOnce()

    suspend fun isOnboardingCompleted(): Boolean = userProfileDao.isOnboardingCompleted() ?: false

    suspend fun insertProfile(profile: UserProfileEntity) = userProfileDao.insertProfile(profile)

    suspend fun updateProfile(profile: UserProfileEntity) = userProfileDao.updateProfile(profile)

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
            isNearWater = isNearWater,
            isMountainous = isMountainous,
            isLandslide = isLandslide,
            isFlood = isFlood,
            isVolcano = isVolcano,
            isUrban = isUrban,
            isCoastal = isCoastal
        )
    }

    suspend fun resetOnboarding() = userProfileDao.resetOnboarding()

    suspend fun deleteProfile() = userProfileDao.deleteProfile()
}