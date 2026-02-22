package com.resq.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.resq.app.data.entity.DisasterTipEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DisasterTipDao {

    @Query("SELECT * FROM disaster_tips ORDER BY id ASC")
    fun getAllTips(): Flow<List<DisasterTipEntity>>

    @Query("SELECT * FROM disaster_tips ORDER BY id ASC")
    suspend fun getAllTipsOnce(): List<DisasterTipEntity>

    @Query("SELECT * FROM disaster_tips WHERE disaster_id = :disasterId ORDER BY id ASC")
    fun getTipsByDisasterId(disasterId: Long): Flow<List<DisasterTipEntity>>

    @Query("SELECT * FROM disaster_tips WHERE disaster_id = :disasterId ORDER BY id ASC")
    suspend fun getTipsByDisasterIdOnce(disasterId: Long): List<DisasterTipEntity>

    @Query("SELECT * FROM disaster_tips ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomTips(limit: Int): List<DisasterTipEntity>

    @Query("""
        SELECT t.* FROM disaster_tips t
        INNER JOIN disasters d ON t.disaster_id = d.id
        WHERE (d.is_recommended_near_water = :nearWater AND :nearWater = 1)
           OR (d.is_recommended_mountainous = :mountainous AND :mountainous = 1)
           OR (d.is_recommended_landslide = :landslide AND :landslide = 1)
           OR (d.is_recommended_flood = :flood AND :flood = 1)
           OR (d.is_recommended_volcano = :volcano AND :volcano = 1)
           OR (d.is_recommended_urban = :urban AND :urban = 1)
           OR (d.is_recommended_coastal = :coastal AND :coastal = 1)
           OR d.is_universal = 1
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

    /** Single tip by ID — required for updateFavoriteStatus */
    @Query("SELECT * FROM disaster_tips WHERE id = :tipId LIMIT 1")
    suspend fun getTipById(tipId: Long): DisasterTipEntity?

    /** Full row update — required for updateFavoriteStatus */
    @Update
    suspend fun updateTip(tip: DisasterTipEntity)

    /** Favorite tips as Flow */
    @Query("SELECT * FROM disaster_tips WHERE is_favorite = 1 ORDER BY id ASC")
    fun getFavoriteTips(): Flow<List<DisasterTipEntity>>

    /** Search tips by text */
    @Query("""
        SELECT * FROM disaster_tips
        WHERE tip_text LIKE '%' || :query || '%'
           OR tip_text_tagalog LIKE '%' || :query || '%'
        ORDER BY id ASC
    """)
    fun searchTips(query: String): Flow<List<DisasterTipEntity>>

    @Query("SELECT COUNT(*) FROM disaster_tips")
    suspend fun getTipCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTip(tip: DisasterTipEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTips(tips: List<DisasterTipEntity>)

    @Delete
    suspend fun deleteTip(tip: DisasterTipEntity)

    @Query("DELETE FROM disaster_tips")
    suspend fun deleteAllTips()

    @Update
    suspend fun update(tip: DisasterTipEntity)
}