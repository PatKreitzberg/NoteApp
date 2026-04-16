package com.wyldsoft.notes.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Stores a named set of 5 pen profiles.
 * Profiles are stored as flat columns (width, penType name, ARGB color, alpha) per slot.
 * Use PenProfileSetRepository to convert to/from PenProfile instances.
 */
@Entity(tableName = "pen_profile_sets")
data class PenProfileSetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val updatedAt: Long = System.currentTimeMillis(),
    // Slot 1
    val slot1Width: Float,
    val slot1PenType: String,
    val slot1ColorArgb: Int,
    val slot1Alpha: Float,
    // Slot 2
    val slot2Width: Float,
    val slot2PenType: String,
    val slot2ColorArgb: Int,
    val slot2Alpha: Float,
    // Slot 3
    val slot3Width: Float,
    val slot3PenType: String,
    val slot3ColorArgb: Int,
    val slot3Alpha: Float,
    // Slot 4
    val slot4Width: Float,
    val slot4PenType: String,
    val slot4ColorArgb: Int,
    val slot4Alpha: Float,
    // Slot 5
    val slot5Width: Float,
    val slot5PenType: String,
    val slot5ColorArgb: Int,
    val slot5Alpha: Float,
)
