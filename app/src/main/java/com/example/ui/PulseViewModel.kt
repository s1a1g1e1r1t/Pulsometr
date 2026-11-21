package com.example.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.MeasurementContext
import com.example.data.MeasurementSource
import com.example.data.PulseMeasurement
import com.example.data.PulseRepository
import com.example.data.UiState
import com.example.ppg.PpgPhase
import com.example.ppg.PpgSignalProcessor
import com.example.ppg.PpgSnapshot
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class TapTempoState(
    val tapCount: Int = 0,
    val liveBpm: Int? = null,
    val confidence: Int = 0,
    val isReadyToSave: Boolean = false,
    val beatTick: Long = 0L,
)

class PulseViewModel(
    private val repository: PulseRepository,
) : ViewModel() {

    private val ppgProcessor = PpgSignalProcessor()
    private val tapTimestamps = mutableListOf<Long>()

    val measurementsState: StateFlow<UiState<List<PulseMeasurement>>> =
        repository.observeMeasurements()
            .map<List<PulseMeasurement>, UiState<List<PulseMeasurement>>> { UiState.Success(it) }
            .catch { error ->
                if (error is CancellationException) throw error
                emit(UiState.Error(error.localizedMessage ?: "Не вдалося завантажити журнал"))
            }
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(5000L),
                initialValue = UiState.Loading,
            )

    private val _selectedMode = MutableStateFlow(MeasurementSource.CAMERA_PPG)
    val selectedMode: StateFlow<MeasurementSource> = _selectedMode.asStateFlow()

    private val _isCameraScanning = MutableStateFlow(false)
    val isCameraScanning: StateFlow<Boolean> = _isCameraScanning.asStateFlow()

    private val _torchEnabled = MutableStateFlow(true)
    val torchEnabled: StateFlow<Boolean> = _torchEnabled.asStateFlow()

    private val _ppgSnapshot = MutableStateFlow(PpgSnapshot())
    val ppgSnapshot: StateFlow<PpgSnapshot> = _ppgSnapshot.asStateFlow()

    private val _tapState = MutableStateFlow(TapTempoState())
    val tapState: StateFlow<TapTempoState> = _tapState.asStateFlow()

    private val _filterContext = MutableStateFlow<MeasurementContext?>(null)
    val filterContext: StateFlow<MeasurementContext?> = _filterContext.asStateFlow()

    private val _isSaving = MutableStateFlow(false)
    val isSaving: StateFlow<Boolean> = _isSaving.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val events: SharedFlow<String> = _events.asSharedFlow()

    fun selectMode(mode: MeasurementSource) {
        if (_selectedMode.value == mode) return
        stopCameraScan()
        _selectedMode.value = mode
    }

    fun startCameraScan() {
        ppgProcessor.reset()
        _ppgSnapshot.value = PpgSnapshot(phase = PpgPhase.CALIBRATING)
        _isCameraScanning.value = true
    }

    fun stopCameraScan() {
        _isCameraScanning.value = false
        ppgProcessor.reset()
        _ppgSnapshot.value = PpgSnapshot(phase = PpgPhase.IDLE)
    }

    fun toggleTorch() {
        _torchEnabled.update { !it }
    }

    fun onCameraFrameSample(red: Double, green: Double, blue: Double, timestampMs: Long) {
        if (!_isCameraScanning.value) return
        val next = ppgProcessor.processFrame(red, green, blue, timestampMs)
        _ppgSnapshot.value = next
        if (next.phase == PpgPhase.COMPLETED) {
            _isCameraScanning.value = false
        }
    }

    fun onCameraError(message: String) {
        _isCameraScanning.value = false
        _events.tryEmit(message)
    }

    fun recordTap(nowMs: Long = System.currentTimeMillis()) {
        val last = tapTimestamps.lastOrNull()
        if (last != null && nowMs - last > 3000L) {
            tapTimestamps.clear()
        }
        tapTimestamps.add(nowMs)
        if (tapTimestamps.size > 20) {
            tapTimestamps.removeAt(0)
        }

        val intervals = tapTimestamps.zipWithNext { a, b -> b - a }
            .filter { it in 250L..2000L }

        val bpm = if (intervals.size >= 2) {
            val sorted = intervals.sorted()
            val mid = sorted.size / 2
            val median = if (sorted.size % 2 == 0) {
                (sorted[mid - 1] + sorted[mid]) / 2.0
            } else {
                sorted[mid].toDouble()
            }
            (60_000.0 / median).roundToInt().coerceIn(30, 240)
        } else {
            null
        }

        val confidence = if (intervals.size >= 3) {
            val avg = intervals.average()
            val sd = sqrt(intervals.map { (it - avg) * (it - avg) }.average())
            val cv = if (avg > 0) sd / avg else 1.0
            (((1.0 - (cv / 0.35)) * 75.0) + (intervals.size * 3.0)).roundToInt().coerceIn(45, 98)
        } else {
            (tapTimestamps.size * 12).coerceIn(15, 60)
        }

        _tapState.value = TapTempoState(
            tapCount = tapTimestamps.size,
            liveBpm = bpm,
            confidence = confidence,
            isReadyToSave = bpm != null && tapTimestamps.size >= 4,
            beatTick = _tapState.value.beatTick + 1L,
        )
    }

    fun resetTapTempo() {
        tapTimestamps.clear()
        _tapState.value = TapTempoState()
    }

    fun setFilterContext(context: MeasurementContext?) {
        _filterContext.value = context
    }

    fun saveMeasurement(
        bpm: Int,
        contextTag: MeasurementContext,
        source: MeasurementSource,
        confidence: Int,
        note: String?,
        onSaved: () -> Unit,
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.addMeasurement(
                bpm = bpm,
                contextTag = contextTag,
                source = source,
                confidence = confidence,
                note = note,
            )
            _isSaving.value = false
            result.onSuccess {
                if (source == MeasurementSource.CAMERA_PPG) {
                    ppgProcessor.reset()
                    _ppgSnapshot.value = PpgSnapshot(phase = PpgPhase.IDLE)
                } else if (source == MeasurementSource.TAP_TEMPO) {
                    resetTapTempo()
                }
                onSaved()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Не вдалося зберегти вимірювання")
            }
        }
    }

    fun updateMeasurement(
        measurementId: Long,
        contextTag: MeasurementContext,
        note: String?,
        onUpdated: () -> Unit,
    ) {
        viewModelScope.launch {
            _isSaving.value = true
            val result = repository.updateMeasurement(
                measurementId = measurementId,
                contextTag = contextTag,
                note = note,
            )
            _isSaving.value = false
            result.onSuccess {
                onUpdated()
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Не вдалося оновити запис")
            }
        }
    }

    fun deleteMeasurement(measurementId: Long, deletedMessage: String) {
        viewModelScope.launch {
            val result = repository.deleteMeasurement(measurementId)
            result.onSuccess {
                _events.tryEmit(deletedMessage)
            }.onFailure { err ->
                _events.tryEmit(err.localizedMessage ?: "Не вдалося видалити запис")
            }
        }
    }
}
