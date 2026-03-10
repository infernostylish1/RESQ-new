package com.resq.app.data.repository

import com.resq.app.data.dao.DisasterTipDao
import com.resq.app.data.entity.DisasterTipEntity
import com.resq.app.data.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class DisasterTipRepository(
    private val disasterTipDao: DisasterTipDao
) {

    fun getAllTips(): Flow<List<DisasterTipEntity>> =
        disasterTipDao.getAllTips()

    suspend fun getAllTipsOnce(): List<DisasterTipEntity> =
        disasterTipDao.getAllTipsOnce()

    fun getTipsByDisasterId(disasterId: Long): Flow<List<DisasterTipEntity>> =
        disasterTipDao.getTipsByDisasterId(disasterId)

    fun getTipsByDisaster(disasterId: Long): Flow<List<DisasterTipEntity>> =
        disasterTipDao.getTipsByDisasterId(disasterId)

    suspend fun getTipsByDisasterIdOnce(disasterId: Long): List<DisasterTipEntity> =
        disasterTipDao.getTipsByDisasterIdOnce(disasterId)

    suspend fun getRandomTips(limit: Int = 5): List<DisasterTipEntity> =
        disasterTipDao.getRandomTips(limit)

    fun getRandomTip(): Flow<DisasterTipEntity?> = flow {
        val tips = disasterTipDao.getRandomTips(1)
        emit(tips.firstOrNull())
    }

    suspend fun getNextTip(currentTipId: Long): DisasterTipEntity? {
        val all = disasterTipDao.getAllTipsOnce()
        if (all.isEmpty()) return null
        val currentIndex = all.indexOfFirst { it.id == currentTipId }
        return if (currentIndex == -1 || currentIndex == all.lastIndex) {
            all.first()
        } else {
            all[currentIndex + 1]
        }
    }

    suspend fun getPreviousTip(currentTipId: Long): DisasterTipEntity? {
        val all = disasterTipDao.getAllTipsOnce()
        if (all.isEmpty()) return null
        val currentIndex = all.indexOfFirst { it.id == currentTipId }
        return if (currentIndex <= 0) {
            all.last()
        } else {
            all[currentIndex - 1]
        }
    }

    fun searchTips(query: String): Flow<List<DisasterTipEntity>> =
        disasterTipDao.searchTips(query)

    suspend fun markTipAsViewed(tipId: Long) {
        // no-op placeholder
    }

    suspend fun getRecommendedTips(
        profile: UserProfileEntity,
        limit: Int = 5
    ): List<DisasterTipEntity> =
        disasterTipDao.getRecommendedTips(
            nearWater   = profile.isNearWater,
            mountainous = profile.isInMountainousArea,
            landslide   = profile.isInLandslideProne,
            flood       = profile.isInFloodProne,
            volcano     = profile.isNearVolcano,
            urban       = profile.isInUrbanArea,
            coastal     = profile.isInCoastalArea,
            limit       = limit
        )

    suspend fun getTipCount(): Int =
        disasterTipDao.getTipCount()

    suspend fun updateTip(tip: DisasterTipEntity) = disasterTipDao.update(tip)
}