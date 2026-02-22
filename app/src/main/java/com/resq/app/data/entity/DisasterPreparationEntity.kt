package com.resq.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.resq.app.data.model.MediaType
import com.resq.app.data.model.SituationType

/**
 * Entity representing disaster preparation information.
 * Contains preparation steps that may vary based on user's situational context.
 */
@Entity(
    tableName = "disaster_preparation",
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
data class DisasterPreparationEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "disaster_id")
    val disasterId: Long,

    @ColumnInfo(name = "situation_type")
    val situationType: SituationType = SituationType.GENERAL,

    @ColumnInfo(name = "phase")
    val phase: String,

    @ColumnInfo(name = "phase_tagalog")
    val phaseTagalog: String,

    @ColumnInfo(name = "title")
    val title: String,

    @ColumnInfo(name = "title_tagalog")
    val titleTagalog: String,

    @ColumnInfo(name = "content")
    val content: String,

    @ColumnInfo(name = "content_tagalog")
    val contentTagalog: String,

    @ColumnInfo(name = "tip")
    val tip: String? = null,

    @ColumnInfo(name = "tip_tagalog")
    val tipTagalog: String? = null,

    @ColumnInfo(name = "media_type")
    val mediaType: MediaType = MediaType.TEXT,

    @ColumnInfo(name = "media_res_name")
    val mediaResName: String? = null,

    @ColumnInfo(name = "display_order")
    val displayOrder: Int = 0
)