package com.resq.app.data.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing quick disaster tips.
 * Used for the Disaster Tips module with random/recommended display.
 */
@Entity(
    tableName = "disaster_tips",
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
data class DisasterTipEntity(
    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    val id: Long = 0,

    @ColumnInfo(name = "disaster_id")
    val disasterId: Long,

    @ColumnInfo(name = "tip_text")
    val tipText: String,

    @ColumnInfo(name = "tip_text_tagalog")
    val tipTextTagalog: String,

    @ColumnInfo(name = "source")
    val source: String? = null,

    @ColumnInfo(name = "is_favorite", defaultValue = "0")
    val isFavorite: Boolean = false
)