package com.resq.app.data.repository

import com.resq.app.data.dao.DisasterDao
import com.resq.app.data.dao.DisasterInfoDao
import com.resq.app.data.dao.DisasterPreparationDao
import com.resq.app.data.entity.DisasterEntity
import com.resq.app.data.entity.DisasterInfoEntity
import com.resq.app.data.entity.DisasterPreparationEntity
import com.resq.app.data.entity.UserProfileEntity
import com.resq.app.data.model.SituationType
import kotlinx.coroutines.flow.Flow

class DisasterRepository(
    private val disasterDao: DisasterDao,
    private val disasterInfoDao: DisasterInfoDao,
    private val disasterPreparationDao: DisasterPreparationDao
) {

    // ─── Disaster CRUD ────────────────────────────────────────────────────────

    fun getAllDisasters(): Flow<List<DisasterEntity>> =
        disasterDao.getAllDisasters()

    suspend fun getAllDisastersOnce(): List<DisasterEntity> =
        disasterDao.getAllDisastersOnce()

    fun getDisasterById(disasterId: Long): Flow<DisasterEntity?> =
        disasterDao.getDisasterById(disasterId)

    suspend fun getDisasterByIdOnce(disasterId: Long): DisasterEntity? =
        disasterDao.getDisasterByIdOnce(disasterId)

    /**
     * One-shot lookup by name — used by VoiceCommandViewModel to resolve
     * a spoken disaster name to its real DB id before navigating to detail.
     * Passes the name as-is; DAO uses LIKE for case-insensitive matching.
     */
    suspend fun getDisasterByName(name: String): DisasterEntity? =
        disasterDao.getDisasterByName(name)

    fun getRecommendedDisasters(profile: UserProfileEntity): Flow<List<DisasterEntity>> =
        disasterDao.getRecommendedDisasters(
            nearWater   = profile.isNearWater,
            mountainous = profile.isInMountainousArea,
            landslide   = profile.isInLandslideProne,
            flood       = profile.isInFloodProne,
            volcano     = profile.isNearVolcano,
            urban       = profile.isInUrbanArea,
            coastal     = profile.isInCoastalArea
        )

    fun getUniversalDisasters(): Flow<List<DisasterEntity>> =
        disasterDao.getUniversalDisasters()

    fun searchDisasters(query: String): Flow<List<DisasterEntity>> =
        disasterDao.searchDisasters(query)

    suspend fun getDisasterCount(): Int =
        disasterDao.getDisasterCount()

    // ─── Disaster Info ────────────────────────────────────────────────────────

    fun getDisasterInfo(disasterId: Long): Flow<List<DisasterInfoEntity>> =
        disasterInfoDao.getInfoByDisasterId(disasterId)

    fun getInfoByDisasterId(disasterId: Long): Flow<List<DisasterInfoEntity>> =
        disasterInfoDao.getInfoByDisasterId(disasterId)

    suspend fun getInfoByDisasterIdOnce(disasterId: Long): List<DisasterInfoEntity> =
        disasterInfoDao.getInfoByDisasterIdOnce(disasterId)

    suspend fun getInfoById(infoId: Long): DisasterInfoEntity? =
        disasterInfoDao.getInfoById(infoId)

    fun searchInfo(query: String): Flow<List<DisasterInfoEntity>> =
        disasterInfoDao.searchInfo(query)

    // ─── Disaster Preparation ─────────────────────────────────────────────────

    fun getDisasterPreparations(disasterId: Long): Flow<List<DisasterPreparationEntity>> =
        disasterPreparationDao.getPreparationByDisasterId(disasterId)

    fun getDisasterPreparationsBySituation(
        disasterId: Long,
        situationType: SituationType
    ): Flow<List<DisasterPreparationEntity>> =
        disasterPreparationDao.getPreparationBySituation(disasterId, situationType)

    fun getPreparationByDisasterId(disasterId: Long): Flow<List<DisasterPreparationEntity>> =
        disasterPreparationDao.getPreparationByDisasterId(disasterId)

    suspend fun getPreparationByDisasterIdOnce(disasterId: Long): List<DisasterPreparationEntity> =
        disasterPreparationDao.getPreparationByDisasterIdOnce(disasterId)

    fun getPreparationBySituation(
        disasterId: Long,
        situationType: SituationType
    ): Flow<List<DisasterPreparationEntity>> =
        disasterPreparationDao.getPreparationBySituation(disasterId, situationType)

    fun getGeneralPreparation(disasterId: Long): Flow<List<DisasterPreparationEntity>> =
        disasterPreparationDao.getGeneralPreparation(disasterId)

    suspend fun getPhasesByDisasterId(disasterId: Long): List<String> =
        disasterPreparationDao.getPhasesByDisasterId(disasterId)
}