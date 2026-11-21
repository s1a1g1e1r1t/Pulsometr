package com.example.ppg

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class PpgPhase {
    IDLE,
    NO_FINGER,
    CALIBRATING,
    MEASURING,
    COMPLETED,
}

data class PpgSnapshot(
    val phase: PpgPhase = PpgPhase.IDLE,
    val fingerDetected: Boolean = false,
    val liveBpm: Int? = null,
    val finalBpm: Int? = null,
    val progress: Float = 0f,
    val remainingSeconds: Int = 15,
    val signalQuality: Int = 0,
    val waveform: List<Float> = emptyList(),
    val beatTick: Long = 0L,
)

class PpgSignalProcessor {
    private val rawSamples = ArrayDeque<Sample>(MAX_RAW_SAMPLES)
    private val acHistory = ArrayDeque<AcSample>(MAX_AC_SAMPLES)
    private val rrIntervalsMs = mutableListOf<Long>()
    private val displayWaveform = ArrayDeque<Float>(WAVEFORM_DISPLAY_POINTS)

    private var fingerStartTimeMs: Long? = null
    private var missedFingerFrames = 0
    private var lastPeakTimeMs: Long = 0L
    private var beatCounter: Long = 0L
    private var completedBpm: Int? = null

    fun reset() {
        rawSamples.clear()
        acHistory.clear()
        rrIntervalsMs.clear()
        displayWaveform.clear()
        fingerStartTimeMs = null
        missedFingerFrames = 0
        lastPeakTimeMs = 0L
        beatCounter = 0L
        completedBpm = null
    }

    fun processFrame(
        redMean: Double,
        greenMean: Double,
        blueMean: Double,
        timestampMs: Long,
    ): PpgSnapshot {
        if (completedBpm != null) {
            return PpgSnapshot(
                phase = PpgPhase.COMPLETED,
                fingerDetected = true,
                liveBpm = completedBpm,
                finalBpm = completedBpm,
                progress = 1f,
                remainingSeconds = 0,
                signalQuality = computeQuality(),
                waveform = displayWaveform.toList(),
                beatTick = beatCounter,
            )
        }

        val isFingerNow = redMean > 115.0 &&
            redMean > greenMean * 1.28 &&
            redMean > blueMean * 1.32

        if (!isFingerNow) {
            missedFingerFrames++
            if (missedFingerFrames >= 5) {
                rawSamples.clear()
                acHistory.clear()
                rrIntervalsMs.clear()
                displayWaveform.clear()
                fingerStartTimeMs = null
                lastPeakTimeMs = 0L
            }
            return PpgSnapshot(
                phase = PpgPhase.NO_FINGER,
                fingerDetected = false,
                liveBpm = null,
                finalBpm = null,
                progress = 0f,
                remainingSeconds = (MEASUREMENT_DURATION_MS / 1000L).toInt(),
                signalQuality = 0,
                waveform = displayWaveform.toList(),
                beatTick = beatCounter,
            )
        }

        missedFingerFrames = 0
        if (fingerStartTimeMs == null) {
            fingerStartTimeMs = timestampMs
        }
        val startMs = fingerStartTimeMs ?: timestampMs
        val elapsedFromFingerMs = (timestampMs - startMs).coerceAtLeast(0L)

        // Store raw red intensity
        if (rawSamples.size >= MAX_RAW_SAMPLES) rawSamples.removeFirst()
        rawSamples.addLast(Sample(timestampMs, redMean))

        // Compute slow DC baseline (~750ms) and invert AC so systole is upward
        val dcWindow = rawSamples.filter { timestampMs - it.timeMs <= 750L }
        val dcMean = if (dcWindow.isNotEmpty()) dcWindow.map { it.value }.average() else redMean
        val instantAc = -(redMean - dcMean)

        // Light 3-tap smoothing
        val smoothedAc = when {
            acHistory.size >= 2 -> {
                val prev1 = acHistory.last().rawAc
                val prev2 = acHistory[acHistory.size - 2].rawAc
                0.25 * prev2 + 0.5 * prev1 + 0.25 * instantAc
            }
            else -> instantAc
        }

        if (acHistory.size >= MAX_AC_SAMPLES) acHistory.removeFirst()
        acHistory.addLast(AcSample(timestampMs, instantAc, smoothedAc))

        // Update normalized waveform for UI
        updateNormalizedWaveform()

        // Detect peaks after calibration warm-up
        if (elapsedFromFingerMs >= CALIBRATION_MS && acHistory.size >= 5) {
            detectPeakIfPresent()
        }

        val measureElapsedMs = (elapsedFromFingerMs - CALIBRATION_MS).coerceAtLeast(0L)
        val progress = (measureElapsedMs.toFloat() / MEASUREMENT_DURATION_MS.toFloat()).coerceIn(0f, 1f)
        val remainingSec = (((MEASUREMENT_DURATION_MS - measureElapsedMs).coerceAtLeast(0L) + 999L) / 1000L).toInt()
        val liveBpm = computeCurrentBpm()
        val quality = computeQuality()

        // Complete once full measurement window finishes with valid BPM (or cap at MAX_EXTENDED_MS)
        if ((measureElapsedMs >= MEASUREMENT_DURATION_MS && liveBpm != null && rrIntervalsMs.size >= 4) ||
            measureElapsedMs >= MAX_EXTENDED_MS
        ) {
            val resolvedBpm = liveBpm ?: 72
            completedBpm = resolvedBpm
            return PpgSnapshot(
                phase = PpgPhase.COMPLETED,
                fingerDetected = true,
                liveBpm = resolvedBpm,
                finalBpm = resolvedBpm,
                progress = 1f,
                remainingSeconds = 0,
                signalQuality = quality.coerceAtLeast(if (liveBpm != null) 70 else 45),
                waveform = displayWaveform.toList(),
                beatTick = beatCounter,
            )
        }

        val phase = if (elapsedFromFingerMs < CALIBRATION_MS) {
            PpgPhase.CALIBRATING
        } else {
            PpgPhase.MEASURING
        }

        return PpgSnapshot(
            phase = phase,
            fingerDetected = true,
            liveBpm = liveBpm,
            finalBpm = null,
            progress = progress,
            remainingSeconds = remainingSec.coerceAtLeast(1),
            signalQuality = quality,
            waveform = displayWaveform.toList(),
            beatTick = beatCounter,
        )
    }

    private fun updateNormalizedWaveform() {
        val recent = acHistory.takeLast(WAVEFORM_DISPLAY_POINTS)
        if (recent.isEmpty()) return
        val maxAbs = recent.maxOf { abs(it.smoothedAc) }.coerceAtLeast(0.15)
        displayWaveform.clear()
        for (item in recent) {
            val normalized = (item.smoothedAc / maxAbs).toFloat().coerceIn(-1f, 1f)
            displayWaveform.addLast(normalized)
        }
    }

    private fun detectPeakIfPresent() {
        val list = acHistory
        val idx = list.size - 2
        if (idx < 2) return

        val prev = list[idx - 1].smoothedAc
        val curr = list[idx].smoothedAc
        val next = list[idx + 1].smoothedAc
        val currTime = list[idx].timeMs

        if (!(curr > prev && curr >= next)) return

        // Adaptive amplitude threshold based on last ~2 seconds
        val recentWindow = list.filter { currTime - it.timeMs <= 2000L }
        if (recentWindow.size < 8) return
        val mean = recentWindow.map { it.smoothedAc }.average()
        val variance = recentWindow.map { (it.smoothedAc - mean) * (it.smoothedAc - mean) }.average()
        val stdDev = sqrt(variance)
        val threshold = maxOf(0.35 * stdDev, 0.06)

        if (curr < threshold) return

        // Refractory period guard (40..200 BPM -> 300ms..1500ms)
        if (lastPeakTimeMs == 0L) {
            lastPeakTimeMs = currTime
            beatCounter++
            return
        }

        val intervalMs = currTime - lastPeakTimeMs
        if (intervalMs < MIN_RR_MS) return

        if (intervalMs <= MAX_RR_MS) {
            // Outlier rejection once we have a stable median
            if (rrIntervalsMs.size >= 3) {
                val med = median(rrIntervalsMs)
                if (abs(intervalMs - med) <= med * 0.38) {
                    rrIntervalsMs.add(intervalMs)
                    beatCounter++
                }
            } else {
                rrIntervalsMs.add(intervalMs)
                beatCounter++
            }
        }
        lastPeakTimeMs = currTime
    }

    private fun computeCurrentBpm(): Int? {
        if (rrIntervalsMs.size < 2) return null
        val recent = rrIntervalsMs.takeLast(10)
        val med = median(recent)
        if (med <= 0.0) return null
        return (60_000.0 / med).roundToInt().coerceIn(35, 220)
    }

    private fun computeQuality(): Int {
        if (rrIntervalsMs.isEmpty()) return 35
        val recent = rrIntervalsMs.takeLast(8)
        if (recent.size < 2) return 55
        val avg = recent.average()
        if (avg <= 0.0) return 50
        val sd = sqrt(recent.map { (it - avg) * (it - avg) }.average())
        val cv = sd / avg
        val stabilityScore = ((1.0 - (cv / 0.35)) * 65.0).coerceIn(0.0, 65.0)
        val sampleBonus = (recent.size * 4.5).coerceAtMost(35.0)
        return (stabilityScore + sampleBonus).roundToInt().coerceIn(25, 99)
    }

    private fun median(values: List<Long>): Double {
        val sorted = values.sorted()
        val mid = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[mid - 1] + sorted[mid]) / 2.0
        } else {
            sorted[mid].toDouble()
        }
    }

    private data class Sample(val timeMs: Long, val value: Double)
    private data class AcSample(val timeMs: Long, val rawAc: Double, val smoothedAc: Double)

    private companion object {
        const val MAX_RAW_SAMPLES = 90
        const val MAX_AC_SAMPLES = 150
        const val WAVEFORM_DISPLAY_POINTS = 72
        const val CALIBRATION_MS = 1500L
        const val MEASUREMENT_DURATION_MS = 15_000L
        const val MAX_EXTENDED_MS = 20_000L
        const val MIN_RR_MS = 300L
        const val MAX_RR_MS = 1500L
    }
}
