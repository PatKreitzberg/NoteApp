package com.wyldsoft.notes.data.database.converters

import androidx.room.TypeConverter
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun fromFloatList(value: List<Float>): String = json.encodeToString(value)

    @TypeConverter
    fun toFloatList(value: String): List<Float> = json.decodeFromString(value)

    @TypeConverter
    fun fromIntList(value: List<Int>): String = json.encodeToString(value)

    @TypeConverter
    fun toIntList(value: String): List<Int> = json.decodeFromString(value)

    @TypeConverter
    fun fromLongList(value: List<Long>): String = json.encodeToString(value)

    @TypeConverter
    fun toLongList(value: String): List<Long> = json.decodeFromString(value)
}
