package com.resq.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.resq.app.data.model.MediaType

/**
 * Entity representing detailed information about a specific disaster.
 * Contains educational content about what the disaster is and how it occurs.
 */
@Entity(
    tableName = "disaster_info",
    foreignKeys = [
        ForeignKey(
            entity = DisasterEntity::class,
            parentColumns = ["id"],
            childColumns = ["disaster_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["disaster_id"])]
)
data class DisasterInfoEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "disaster_id")
    val disasterId: Long,

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