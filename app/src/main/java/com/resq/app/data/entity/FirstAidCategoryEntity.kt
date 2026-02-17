package com.resq.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Entity representing a first aid category.
 * Groups related first aid and emergency response content.
 */
@Entity(tableName = "first_aid_categories")
data class FirstAidCategoryEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "name_tagalog")
    val nameTagalog: String,

    @ColumnInfo(name = "description")
    val description: String,

    @ColumnInfo(name = "description_tagalog")
    val descriptionTagalog: String,

    @ColumnInfo(name = "icon_res_name")
    val iconResName: String,

    @ColumnInfo(name = "image_res_name")
    val imageResName: String,

    @ColumnInfo(name = "display_order")
    val displayOrder: Int = 0
)