package com.resq.app.data.database

import androidx.room.TypeConverter
import com.resq.app.data.model.ContactCategory
import com.resq.app.data.model.FontSize
import com.resq.app.data.model.MediaType
import com.resq.app.data.model.SituationType
import com.resq.app.data.model.TipsPreference

/**
 * Type converters for Room database.
 * Converts enum types to/from strings for database storage.
 */
class Converters {

    // MediaType converters
    @TypeConverter
    fun fromMediaType(value: MediaType): String = value.name

    @TypeConverter
    fun toMediaType(value: String): MediaType =
        enumValueOf<MediaType>(value)

    // SituationType converters
    @TypeConverter
    fun fromSituationType(value: SituationType): String = value.name

    @TypeConverter
    fun toSituationType(value: String): SituationType =
        enumValueOf<SituationType>(value)

    // FontSize converters
    @TypeConverter
    fun fromFontSize(value: FontSize): String = value.name

    @TypeConverter
    fun toFontSize(value: String): FontSize =
        enumValueOf<FontSize>(value)

    // TipsPreference converters
    @TypeConverter
    fun fromTipsPreference(value: TipsPreference): String = value.name

    @TypeConverter
    fun toTipsPreference(value: String): TipsPreference =
        enumValueOf<TipsPreference>(value)

    // ContactCategory converters
    @TypeConverter
    fun fromContactCategory(value: ContactCategory): String = value.name

    @TypeConverter
    fun toContactCategory(value: String): ContactCategory =
        enumValueOf<ContactCategory>(value)
}