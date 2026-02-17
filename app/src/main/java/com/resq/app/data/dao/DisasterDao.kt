package com.resq.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.resq.app.data.entity.DisasterEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for Disaster operations.
 * Handles disaster categories and recommendations based on user profile.
 */
@Dao
interface DisasterDao {

    @Query("SELECT * FROM disasters ORDER BY display_order ASC")
    fun getAllDisasters(): Flow<List<DisasterEntity>>

    @Query("SELECT * FROM disasters ORDER BY display_order ASC")
    suspend fun getAllDisastersOnce(): List<DisasterEntity>

    @Query("SELECT * FROM disasters WHERE id = :disasterId")
    fun getDisasterById(disasterId: Long): Flow<DisasterEntity?>

    @Query("SELECT * FROM disasters WHERE id = :disasterId")
    suspend fun getDisasterByIdOnce(disasterId: Long): DisasterEntity?

    @Query("""
        SELECT * FROM disasters 
        WHERE is_universal = 1
           OR (:nearWater = 1 AND is_recommended_near_water = 1)
           OR (:mountainous = 1 AND is_recommended_mountainous = 1)
           OR (:landslide = 1 AND is_recommended_landslide = 1)
           OR (:flood = 1 AND is_recommended_flood = 1)
           OR (:volcano = 1 AND is_recommended_volcano = 1)
           OR (:urban = 1 AND is_recommended_urban = 1)
           OR (:coastal = 1 AND is_recommended_coastal = 1)
        ORDER BY display_order ASC
    """)
    fun getRecommendedDisasters(
        nearWater: Boolean,
        mountainous: Boolean,
        landslide: Boolean,
        flood: Boolean,
        volcano: Boolean,
        urban: Boolean,
        coastal: Boolean
    ): Flow<List<DisasterEntity>>

    @Query("SELECT * FROM disasters WHERE is_universal = 1 ORDER BY display_order ASC")
    fun getUniversalDisasters(): Flow<List<DisasterEntity>>

    @Query("SELECT * FROM disasters WHERE name LIKE '%' || :query || '%' OR name_tagalog LIKE '%' || :query || '%' ORDER BY display_order ASC")
    fun searchDisasters(query: String): Flow<List<DisasterEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDisaster(disaster: DisasterEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllDisasters(disasters: List<DisasterEntity>)

    @Query("SELECT COUNT(*) FROM disasters")
    suspend fun getDisasterCount(): Int

    @Query("DELETE FROM disasters")
    suspend fun deleteAllDisasters()
}