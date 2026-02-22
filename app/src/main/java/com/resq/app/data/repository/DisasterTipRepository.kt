package com.resq.app.data.repository

import com.resq.app.data.dao.DisasterTipDao
import com.resq.app.data.entity.DisasterTipEntity
import com.resq.app.data.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Repository for disaster tip operations.
 * All ViewModel-facing method names normalized here.
 */
class DisasterTipRepository(
    private val disasterTipDao: DisasterTipDao
) {

    fun getAllTips(): Flow<List<DisasterTipEntity>> =
        disasterTipDao.getAllTips()

    suspend fun getAllTipsOnce(): List<DisasterTipEntity> =
        disasterTipDao.getAllTipsOnce()

    fun getTipsByDisasterId(disasterId: Long): Flow<List<DisasterTipEntity>> =
        disasterTipDao.getTipsByDisasterId(disasterId)

    /** ViewModel-facing alias for getTipsByDisasterId */
    fun getTipsByDisaster(disasterId: Long): Flow<List<DisasterTipEntity>> =
        disasterTipDao.getTipsByDisasterId(disasterId)

    suspend fun getTipsByDisasterIdOnce(disasterId: Long): List<DisasterTipEntity> =
        disasterTipDao.getTipsByDisasterIdOnce(disasterId)

    suspend fun getRandomTips(limit: Int = 5): List<DisasterTipEntity> =
        disasterTipDao.getRandomTips(limit)

    /**
     * Returns a single random tip as a Flow.
     * Used by HomeViewModel and DisasterTipsViewModel.
     */
    fun getRandomTip(): Flow<DisasterTipEntity?> = flow {
        val tips = disasterTipDao.getRandomTips(1)
        emit(tips.firstOrNull())
    }

    /**
     * Returns the next tip after the given ID (wraps around).
     * Used by DisasterTipsViewModel.
     */
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

    /**
     * Returns the previous tip before the given ID (wraps around).
     * Used by DisasterTipsViewModel.
     */
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

    /**
     * Updates the isFavorite field for a tip.
     * Room does not support partial updates without a DAO query,
     * so we load the entity, copy with new value, and update.
     */
    suspend fun updateFavoriteStatus(tipId: Long, isFavorite: Boolean) {
        val tip = disasterTipDao.getTipById(tipId) ?: return
        disasterTipDao.updateTip(tip.copy(isFavorite = isFavorite))
    }

    /**
     * Returns all favorite tips as a Flow.
     */
    fun getFavoriteTips(): Flow<List<DisasterTipEntity>> =
        disasterTipDao.getFavoriteTips()

    /**
     * Search tips by keyword.
     */
    fun searchTips(query: String): Flow<List<DisasterTipEntity>> =
        disasterTipDao.searchTips(query)

    /**
     * Mark a tip as viewed (no-op if DAO does not support it —
     * kept here so ViewModel compiles; extend DAO later if needed).
     */
    suspend fun markTipAsViewed(tipId: Long) {
        // Extend DisasterTipDao with a viewedAt column if tracking is required.
        // Currently a no-op placeholder so ViewModel compiles cleanly.
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