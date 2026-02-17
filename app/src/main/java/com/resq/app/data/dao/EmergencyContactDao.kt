package com.resq.app.data.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.resq.app.data.entity.EmergencyContactEntity
import com.resq.app.data.model.ContactCategory
import kotlinx.coroutines.flow.Flow

/**
 * Data Access Object for EmergencyContact operations.
 * Handles both preloaded and user-added emergency contacts.
 */
@Dao
interface EmergencyContactDao {

    @Query("SELECT * FROM emergency_contacts ORDER BY category ASC, display_order ASC, name ASC")
    fun getAllContacts(): Flow<List<EmergencyContactEntity>>

    @Query("SELECT * FROM emergency_contacts ORDER BY category ASC, display_order ASC, name ASC")
    suspend fun getAllContactsOnce(): List<EmergencyContactEntity>

    @Query("SELECT * FROM emergency_contacts WHERE category = :category ORDER BY display_order ASC, name ASC")
    fun getContactsByCategory(category: ContactCategory): Flow<List<EmergencyContactEntity>>

    @Query("SELECT * FROM emergency_contacts WHERE is_custom = 1 ORDER BY created_at DESC")
    fun getCustomContacts(): Flow<List<EmergencyContactEntity>>

    @Query("SELECT * FROM emergency_contacts WHERE is_custom = 0 ORDER BY category ASC, display_order ASC")
    fun getPreloadedContacts(): Flow<List<EmergencyContactEntity>>

    @Query("SELECT * FROM emergency_contacts WHERE id = :contactId")
    suspend fun getContactById(contactId: Long): EmergencyContactEntity?

    @Query("""
        SELECT * FROM emergency_contacts 
        WHERE name LIKE '%' || :query || '%' 
           OR phone_number LIKE '%' || :query || '%'
           OR description LIKE '%' || :query || '%'
        ORDER BY category ASC, display_order ASC
    """)
    fun searchContacts(query: String): Flow<List<EmergencyContactEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertContact(contact: EmergencyContactEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllContacts(contacts: List<EmergencyContactEntity>)

    @Update
    suspend fun updateContact(contact: EmergencyContactEntity)

    @Delete
    suspend fun deleteContact(contact: EmergencyContactEntity)

    @Query("DELETE FROM emergency_contacts WHERE id = :contactId")
    suspend fun deleteContactById(contactId: Long)

    @Query("DELETE FROM emergency_contacts WHERE is_custom = 1")
    suspend fun deleteAllCustomContacts()

    @Query("SELECT COUNT(*) FROM emergency_contacts WHERE is_custom = 0")
    suspend fun getPreloadedContactCount(): Int

    @Query("DELETE FROM emergency_contacts")
    suspend fun deleteAllContacts()
}