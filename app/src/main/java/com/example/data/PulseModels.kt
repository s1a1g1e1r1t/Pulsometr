package com.example.data

import androidx.annotation.StringRes
import androidx.compose.ui.graphics.Color
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.R
import com.example.ui.theme.ZoneElevatedAmber
import com.example.ui.theme.ZoneHighRed
import com.example.ui.theme.ZoneLowBlue
import com.example.ui.theme.ZoneNormalMint

enum class MeasurementContext(@StringRes val labelRes: Int) {
    RESTING(R.string.context_resting),
    MORNING(R.string.context_morning),
    WORKOUT(R.string.context_workout),
    STRESS(R.string.context_stress),
    BEDTIME(R.string.context_bedtime);

    companion object {
        fun fromRaw(raw: String): MeasurementContext =
            entries.firstOrNull { it.name == raw } ?: RESTING
    }
}

enum class MeasurementSource(@StringRes val labelRes: Int) {
    CAMERA_PPG(R.string.mode_camera_ppg),
    TAP_TEMPO(R.string.mode_tap_tempo),
    MANUAL(R.string.mode_manual);

    companion object {
        fun fromRaw(raw: String): MeasurementSource =
            entries.firstOrNull { it.name == raw } ?: CAMERA_PPG
    }
}

enum class HeartRateZone(@StringRes val labelRes: Int, val color: Color) {
    LOW(R.string.zone_low, ZoneLowBlue),
    NORMAL(R.string.zone_normal, ZoneNormalMint),
    ELEVATED(R.string.zone_elevated, ZoneElevatedAmber),
    HIGH(R.string.zone_high, ZoneHighRed);

    companion object {
        fun forBpm(bpm: Int): HeartRateZone = when {
            bpm < 60 -> LOW
            bpm < 100 -> NORMAL
            bpm < 140 -> ELEVATED
            else -> HIGH
        }
    }
}

@Entity(tableName = "pulse_measurements")
data class PulseMeasurement(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val bpm: Int,
    val contextTag: String = MeasurementContext.RESTING.name,
    val source: String = MeasurementSource.CAMERA_PPG.name,
    val confidence: Int = 100,
    val note: String? = null,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = System.currentTimeMillis(),
) {
    fun contextEnum(): MeasurementContext = MeasurementContext.fromRaw(contextTag)
    fun sourceEnum(): MeasurementSource = MeasurementSource.fromRaw(source)
    fun zone(): HeartRateZone = HeartRateZone.forBpm(bpm)
}

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}
