package com.example.data

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow

class PulseRepository(
    private val pulseDao: PulseDao,
) {
    fun observeMeasurements(): Flow<List<PulseMeasurement>> = pulseDao.observeAll()

    suspend fun addMeasurement(
        bpm: Int,
        contextTag: MeasurementContext,
        source: MeasurementSource,
        confidence: Int,
        note: String?,
    ): Result<Unit> = try {
        require(bpm in 30..240) { "BPM must be between 30 and 240" }
        val now = System.currentTimeMillis()
        val trimmedNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
        pulseDao.insert(
            PulseMeasurement(
                bpm = bpm,
                contextTag = contextTag.name,
                source = source.name,
                confidence = confidence.coerceIn(0, 100),
                note = trimmedNote,
                createdAtMillis = now,
                updatedAtMillis = now,
            )
        )
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun updateMeasurement(
        measurementId: Long,
        contextTag: MeasurementContext,
        note: String?,
    ): Result<Unit> = try {
        val trimmedNote = note?.trim()?.takeIf { it.isNotEmpty() }?.take(200)
        pulseDao.update(
            id = measurementId,
            contextTag = contextTag.name,
            note = trimmedNote,
            updatedAtMillis = System.currentTimeMillis(),
        )
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun deleteMeasurement(measurementId: Long): Result<Unit> = try {
        pulseDao.deleteById(measurementId)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
