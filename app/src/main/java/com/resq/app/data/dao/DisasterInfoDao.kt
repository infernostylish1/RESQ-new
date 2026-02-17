package com.resq.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.resq.app.data.entity.DisasterInfoEntity
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for DisasterInfo operations.
 * Handles educational content about disasters.
 */
@Dao
interface DisasterInfoDao {

    @Query("SELECT * FROM disaster_info WHERE disaster_id = :disasterId ORDER BY display_order ASC")
    fun getInfoByDisasterId(disasterId: Long): Flow<List<DisasterInfoEntity>>

    @Query("SELECT * FROM disaster_info WHERE disaster_id = :disasterId ORDER BY display_order ASC")
    suspend fun getInfoByDisasterIdOnce(disasterId: Long): List<DisasterInfoEntity>

    @Query("SELECT * FROM disaster_info WHERE id = :infoId")
    suspend fun getInfoById(infoId: Long): DisasterInfoEntity?

    @Query("""
        SELECT * FROM disaster_info 
        WHERE content LIKE '%' || :query || '%' 
           OR content_tagalog LIKE '%' || :query || '%'
           OR title LIKE '%' || :query || '%'
           OR title_tagalog LIKE '%' || :query || '%'
        ORDER BY display_order ASC
    """)
    fun searchInfo(query: String): Flow<List<DisasterInfoEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInfo(info: DisasterInfoEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllInfo(infoList: List<DisasterInfoEntity>)

    @Query("DELETE FROM disaster_info WHERE disaster_id = :disasterId")
    suspend fun deleteInfoByDisasterId(disasterId: Long)

    @Query("DELETE FROM disaster_info")
    suspend fun deleteAllInfo()
}