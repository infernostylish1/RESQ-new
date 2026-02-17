package com.resq.app.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.resq.app.data.entity.DisasterPreparationEntity
import com.resq.app.data.model.SituationType
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for DisasterPreparation operations.
 * Handles preparation content filtered by situation type.
 */
@Dao
interface DisasterPreparationDao {

    @Query("SELECT * FROM disaster_preparation WHERE disaster_id = :disasterId ORDER BY phase ASC, display_order ASC")
    fun getPreparationByDisasterId(disasterId: Long): Flow<List<DisasterPreparationEntity>>

    @Query("SELECT * FROM disaster_preparation WHERE disaster_id = :disasterId ORDER BY phase ASC, display_order ASC")
    suspend fun getPreparationByDisasterIdOnce(disasterId: Long): List<DisasterPreparationEntity>

    @Query("""
        SELECT * FROM disaster_preparation 
        WHERE disaster_id = :disasterId 
          AND (situation_type = :situationType OR situation_type = 'GENERAL')
        ORDER BY phase ASC, display_order ASC
    """)
    fun getPreparationBySituation(
        disasterId: Long,
        situationType: SituationType
    ): Flow<List<DisasterPreparationEntity>>

    @Query("""
        SELECT * FROM disaster_preparation 
        WHERE disaster_id = :disasterId 
          AND situation_type = 'GENERAL'
        ORDER BY phase ASC, display_order ASC
    """)
    fun getGeneralPreparation(disasterId: Long): Flow<List<DisasterPreparationEntity>>

    @Query("""
        SELECT * FROM disaster_preparation 
        WHERE disaster_id = :disasterId 
          AND phase = :phase
        ORDER BY display_order ASC
    """)
    fun getPreparationByPhase(disasterId: Long, phase: String): Flow<List<DisasterPreparationEntity>>

    @Query("SELECT DISTINCT phase FROM disaster_preparation WHERE disaster_id = :disasterId ORDER BY display_order ASC")
    suspend fun getPhasesByDisasterId(disasterId: Long): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPreparation(preparation: DisasterPreparationEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllPreparation(preparations: List<DisasterPreparationEntity>)

    @Query("DELETE FROM disaster_preparation WHERE disaster_id = :disasterId")
    suspend fun deletePreparationByDisasterId(disasterId: Long)

    @Query("DELETE FROM disaster_preparation")
    suspend fun deleteAllPreparation()
}