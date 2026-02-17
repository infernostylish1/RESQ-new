package com.resq.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.resq.app.data.model.MediaType

/**
 * Entity representing detailed first aid content.
 * Contains step-by-step instructions for emergency response.
 */
@Entity(
    tableName = "first_aid_content",
    foreignKeys = [
        ForeignKey(
            entity = FirstAidCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["category_id"])]
)
data class FirstAidContentEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "category_id")
    val categoryId: Long,

    @ColumnInfo(name = "section_title")
    val sectionTitle: String,

    @ColumnInfo(name = "section_title_tagalog")
    val sectionTitleTagalog: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "title_tagalog")
    val titleTagalog: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "content_tagalog")
    val contentTagalog: String,

    @ColumnInfo(name = "media_type")
    val mediaType: MediaType = MediaType.TEXT,

    @ColumnInfo(name = "media_res_name")
    val mediaResName: String? = null,

    @ColumnInfo(name = "display_order")
    val displayOrder: Int = 0
)