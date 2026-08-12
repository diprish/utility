package com.diprish.utilitymeter.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Embedded
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/** A meter joined with a small summary of its readings, for the list screen. */
data class MeterWithStats(
    @Embedded val meter: Meter,
    val readingCount: Int,
    val latestValue: Double?,
    val latestTimestamp: Long?,
    val latestPhotoPath: String?,
)

@Dao
interface MeterDao {

    @Insert
    suspend fun insertMeter(meter: Meter): Long

    @Update
    suspend fun updateMeter(meter: Meter)

    @Update
    suspend fun updateMeters(meters: List<Meter>)

    @Delete
    suspend fun deleteMeter(meter: Meter)

    @Query("SELECT * FROM meters WHERE id = :meterId")
    fun observeMeter(meterId: Long): Flow<Meter?>

    @Query(
        """
        SELECT m.*,
               COUNT(r.id) AS readingCount,
               (SELECT value FROM readings WHERE meterId = m.id ORDER BY timestamp DESC LIMIT 1) AS latestValue,
               (SELECT timestamp FROM readings WHERE meterId = m.id ORDER BY timestamp DESC LIMIT 1) AS latestTimestamp,
               (SELECT photoPath FROM readings WHERE meterId = m.id ORDER BY timestamp DESC LIMIT 1) AS latestPhotoPath
        FROM meters m
        LEFT JOIN readings r ON r.meterId = m.id
        GROUP BY m.id
        ORDER BY m.position ASC
        """
    )
    fun observeMetersWithStats(): Flow<List<MeterWithStats>>

    // ---- Readings ----

    @Insert
    suspend fun insertReading(reading: MeterReading): Long

    @Update
    suspend fun updateReading(reading: MeterReading)

    @Delete
    suspend fun deleteReading(reading: MeterReading)

    @Query("SELECT * FROM readings WHERE meterId = :meterId ORDER BY timestamp ASC")
    fun observeReadings(meterId: Long): Flow<List<MeterReading>>

    @Query("SELECT * FROM readings WHERE meterId = :meterId ORDER BY timestamp DESC LIMIT 1")
    suspend fun latestReading(meterId: Long): MeterReading?

    @Query("SELECT * FROM readings WHERE id = :readingId")
    suspend fun readingById(readingId: Long): MeterReading?

    // ---- Backup / restore ----

    @Query("SELECT * FROM meters")
    suspend fun allMeters(): List<Meter>

    @Query("SELECT * FROM readings")
    suspend fun allReadings(): List<MeterReading>

    @Insert
    suspend fun insertMeters(meters: List<Meter>)

    @Insert
    suspend fun insertReadings(readings: List<MeterReading>)

    /** Deletes every meter (readings cascade). Used before restoring a backup. */
    @Query("DELETE FROM meters")
    suspend fun clearMeters()

    /** Replaces all data with the supplied rows in a single transaction. */
    @Transaction
    suspend fun replaceAll(meters: List<Meter>, readings: List<MeterReading>) {
        clearMeters()
        insertMeters(meters)
        insertReadings(readings)
    }
}
