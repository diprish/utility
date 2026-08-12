package com.diprish.utilitymeter.data

import kotlinx.coroutines.flow.Flow

/**
 * Single entry point to the data layer. Wraps [MeterDao] so the UI never talks
 * to Room directly and the storage backend can be swapped in tests.
 */
class MeterRepository(private val dao: MeterDao) {

    fun metersWithStats(): Flow<List<MeterWithStats>> = dao.observeMetersWithStats()

    fun meter(meterId: Long): Flow<Meter?> = dao.observeMeter(meterId)

    fun readings(meterId: Long): Flow<List<MeterReading>> = dao.observeReadings(meterId)

    suspend fun latestReading(meterId: Long): MeterReading? = dao.latestReading(meterId)

    suspend fun reading(readingId: Long): MeterReading? = dao.readingById(readingId)

    suspend fun addMeter(meter: Meter): Long = dao.insertMeter(meter)

    suspend fun updateMeter(meter: Meter) = dao.updateMeter(meter)

    suspend fun updateMeters(meters: List<Meter>) = dao.updateMeters(meters)

    suspend fun deleteMeter(meter: Meter) = dao.deleteMeter(meter)

    suspend fun addReading(reading: MeterReading): Long = dao.insertReading(reading)

    suspend fun updateReading(reading: MeterReading) = dao.updateReading(reading)

    suspend fun deleteReading(reading: MeterReading) = dao.deleteReading(reading)

    // ---- Backup / restore ----

    suspend fun allMeters(): List<Meter> = dao.allMeters()

    suspend fun allReadings(): List<MeterReading> = dao.allReadings()

    suspend fun replaceAll(meters: List<Meter>, readings: List<MeterReading>) =
        dao.replaceAll(meters, readings)
}
