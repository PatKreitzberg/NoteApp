package com.wyldsoft.notes.data.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.TypeConverters
import com.wyldsoft.notes.data.database.converters.Converters
import com.wyldsoft.notes.domain.models.ShapeType
import com.wyldsoft.notes.pen.PenType
import android.graphics.PointF

@Entity(
    tableName = "shapes",
    foreignKeys = [
        ForeignKey(
            entity = NoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["noteId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("noteId")]
)
@TypeConverters(Converters::class)
data class ShapeEntity(
    @PrimaryKey
    val id: String,
    val noteId: String,
    val type: ShapeType,
    val points: List<PointF>,
    val strokeWidth: Float,
    val strokeColor: Int,
    val penType: PenType = PenType.BALLPEN,
    val pressure: List<Float> = emptyList(),
    val pointTimestamps: List<Long> = emptyList(),
    val timestamp: Long = System.currentTimeMillis(),
    val text: String? = null
)