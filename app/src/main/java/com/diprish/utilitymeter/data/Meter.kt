package com.diprish.utilitymeter.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A single utility meter the user tracks, e.g. "Home electricity" or
 * "Garden water tap".
 */
@Entity(tableName = "meters")
data class Meter(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val type: MeterType,
    /** Unit of measurement, e.g. kWh, m³. Defaults from [MeterType]. */
    val unit: String,
    /** Optional free-text location or note. */
    val location: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    /**
     * Manual sort position for the list (ascending). New meters get a large
     * negative value so they appear at the top; drag-to-reorder rewrites all
     * positions to 0..n-1.
     */
    val position: Long = 0,
)
