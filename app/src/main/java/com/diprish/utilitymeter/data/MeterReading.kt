package com.diprish.utilitymeter.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single reading captured for a [Meter]. Readings are deleted automatically
 * when their parent meter is removed (cascade).
 */
@Entity(
    tableName = "readings",
    foreignKeys = [
        ForeignKey(
            entity = Meter::class,
            parentColumns = ["id"],
            childColumns = ["meterId"],
            onDelete = ForeignKey.CASCADE,
        )
    ],
    indices = [Index("meterId")],
)
data class MeterReading(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val meterId: Long,
    /** The value shown on the meter (cumulative, as read off the display). */
    val value: Double,
    val timestamp: Long = System.currentTimeMillis(),
    /** Absolute path to the captured photo on device storage, if any. */
    val photoPath: String? = null,
    val note: String = "",
)
