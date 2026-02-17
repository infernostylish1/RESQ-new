package com.resq.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.resq.app.data.model.ContactCategory

/**
 * Entity representing emergency contacts.
 * Includes both preloaded official hotlines and user-added contacts.
 */
@Entity(tableName = "emergency_contacts")
data class EmergencyContactEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "phone_number")
    val phoneNumber: String,

    @ColumnInfo(name = "category")
    val category: ContactCategory = ContactCategory.PERSONAL,

    @ColumnInfo(name = "is_custom")
    val isCustom: Boolean = true,

    @ColumnInfo(name = "description")
    val description: String? = null,

    @ColumnInfo(name = "display_order")
    val displayOrder: Int = 0,

    @ColumnInfo(name = "created_at")
    val createdAt: Long = System.currentTimeMillis()
)