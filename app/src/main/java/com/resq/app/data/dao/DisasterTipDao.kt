package com.resq.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.resq.app.data.entity.DisasterTipEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for DisasterTip operations.
 * Handles quick tips for the Disaster Tips module.
 */
@Dao
interface DisasterTipDao {

    @Query("SELECT * FROM disaster_tips")
    fun getAllTips(): Flow<List<DisasterTipEntity>>

    @Query("SELECT * FROM disaster_tips")
    suspend fun getAllTipsOnce(): List<DisasterTipEntity>

    @Query("SELECT * FROM disaster_tips WHERE disaster_id = :disasterId")
    fun getTipsByDisasterId(disasterId: Long): Flow<List<DisasterTipEntity>>

    @Query("SELECT * FROM disaster_tips WHERE disaster_id = :disasterId")
    suspend fun getTipsByDisasterIdOnce(disasterId: Long): List<DisasterTipEntity>

    @Query("SELECT * FROM disaster_tips ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomTips(limit: Int): List<DisasterTipEntity>

    @Query("""
        SELECT dt.* FROM disaster_tips dt
        INNER JOIN disasters d ON dt.disaster_id = d.id
        WHERE d.is_universal = 1
           OR (:nearWater = 1 AND d.is_recommended_near_water = 1)
           OR (:mountainous = 1 AND d.is_recommended_mountainous = 1)
           OR (:landslide = 1 AND d.is_recommended_landslide = 1)
           OR (:flood = 1 AND d.is_recommended_flood = 1)
           OR (:volcano = 1 AND d.is_recommended_volcano = 1)
           OR (:urban = 1 AND d.is_recommended_urban = 1)
           OR (:coastal = 1 AND d.is_recommended_coastal = 1)
        ORDER BY RANDOM()
        LIMIT :limit
    """)
    suspend fun getRecommendedTips(
        nearWater: Boolean,
        mountainous: Boolean,
        landslide: Boolean,
        flood: Boolean,
        volcano: Boolean,
        urban: Boolean,
        coastal: Boolean,
        limit: Int
    ): List<DisasterTipEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTip(tip: DisasterTipEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllTips(tips: List<DisasterTipEntity>)

    @Query("SELECT COUNT(*) FROM disaster_tips")
    suspend fun getTipCount(): Int

    @Query("DELETE FROM disaster_tips")
    suspend fun deleteAllTips()
}