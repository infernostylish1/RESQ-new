package com.resq.app.data.repository

import com.resq.app.data.dao.DisasterTipDao
import com.resq.app.data.entity.DisasterTipEntity
import com.resq.app.data.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow

/**
 * Repository for disaster tip operations.
 * Handles quick tips for the Disaster Tips module.
 */
class DisasterTipRepository(
    private val disasterTipDao: DisasterTipDao
) {

    fun getAllTips(): Flow<List<DisasterTipEntity>> = disasterTipDao.getAllTips()

    suspend fun getAllTipsOnce(): List<DisasterTipEntity> = disasterTipDao.getAllTipsOnce()

    fun getTipsByDisasterId(disasterId: Long): Flow<List<DisasterTipEntity>> =
        disasterTipDao.getTipsByDisasterId(disasterId)

    suspend fun getTipsByDisasterIdOnce(disasterId: Long): List<DisasterTipEntity> =
        disasterTipDao.getTipsByDisasterIdOnce(disasterId)

    suspend fun getRandomTips(limit: Int = 5): List<DisasterTipEntity> =
        disasterTipDao.getRandomTips(limit)

    suspend fun getRecommendedTips(profile: UserProfileEntity, limit: Int = 5): List<DisasterTipEntity> {
        return disasterTipDao.getRecommendedTips(
            nearWater = profile.isNearWater,
            mountainous = profile.isInMountainousArea,
            landslide = profile.isInLandslideProne,
            flood = profile.isInFloodProne,
            volcano = profile.isNearVolcano,
            urban = profile.isInUrbanArea,
            coastal = profile.isInCoastalArea,
            limit = limit
        )
    }

    suspend fun getTipCount(): Int = disasterTipDao.getTipCount()
}