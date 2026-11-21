package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PulseDao {
    @Query("SELECT * FROM pulse_measurements ORDER BY createdAtMillis DESC")
    fun observeAll(): Flow<List<PulseMeasurement>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(measurement: PulseMeasurement): Long

    @Query(
        "UPDATE pulse_measurements SET contextTag = :contextTag, note = :note, updatedAtMillis = :updatedAtMillis WHERE id = :id"
    )
    suspend fun update(
        id: Long,
        contextTag: String,
        note: String?,
        updatedAtMillis: Long,
    )

    @Query("DELETE FROM pulse_measurements WHERE id = :id")
    suspend fun deleteById(id: Long)
}
