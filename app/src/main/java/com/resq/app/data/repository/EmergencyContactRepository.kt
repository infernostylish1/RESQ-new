package com.resq.app.data.repository

import com.resq.app.data.dao.EmergencyContactDao
import com.resq.app.data.entity.EmergencyContactEntity
import com.resq.app.data.model.ContactCategory
import kotlinx.coroutines.flow.Flow

/**
 * Repository for emergency contact operations.
 * Handles both preloaded official hotlines and user-added contacts.
 */
class EmergencyContactRepository(
    private val emergencyContactDao: EmergencyContactDao
) {

    fun getAllContacts(): Flow<List<EmergencyContactEntity>> =
        emergencyContactDao.getAllContacts()

    suspend fun getAllContactsOnce(): List<EmergencyContactEntity> =
        emergencyContactDao.getAllContactsOnce()

    fun getContactsByCategory(category: ContactCategory): Flow<List<EmergencyContactEntity>> =
        emergencyContactDao.getContactsByCategory(category)

    fun getCustomContacts(): Flow<List<EmergencyContactEntity>> =
        emergencyContactDao.getCustomContacts()

    fun getPreloadedContacts(): Flow<List<EmergencyContactEntity>> =
        emergencyContactDao.getPreloadedContacts()

    suspend fun getContactById(contactId: Long): EmergencyContactEntity? =
        emergencyContactDao.getContactById(contactId)

    fun searchContacts(query: String): Flow<List<EmergencyContactEntity>> =
        emergencyContactDao.searchContacts(query)

    suspend fun insertContact(contact: EmergencyContactEntity): Long =
        emergencyContactDao.insertContact(contact)

    suspend fun addCustomContact(
        name: String,
        phoneNumber: String,
        category: ContactCategory = ContactCategory.PERSONAL,
        description: String? = null
    ): Long {
        val contact = EmergencyContactEntity(
            name         = name,
            phoneNumber  = phoneNumber,
            category     = category,
            isCustom     = true,
            description  = description,
            displayOrder = 999
        )
        return emergencyContactDao.insertContact(contact)
    }

    suspend fun updateContact(contact: EmergencyContactEntity) =
        emergencyContactDao.updateContact(contact)

    suspend fun deleteContact(contact: EmergencyContactEntity) =
        emergencyContactDao.deleteContact(contact)

    suspend fun deleteContactById(contactId: Long) =
        emergencyContactDao.deleteContactById(contactId)

    suspend fun deleteAllCustomContacts() =
        emergencyContactDao.deleteAllCustomContacts()

    suspend fun getPreloadedContactCount(): Int =
        emergencyContactDao.getPreloadedContactCount()
}