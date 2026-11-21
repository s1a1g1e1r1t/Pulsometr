package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ppg.PpgPhase
import com.example.ppg.PpgSignalProcessor
import kotlin.math.PI
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Пульсометр", appName)
    }

    @Test
    fun `ppg processor detects 75 bpm synthetic pulse wave`() {
        val processor = PpgSignalProcessor()
        // 75 BPM = 1.25 Hz, sample at 30 fps (33ms step) for 17.5 seconds
        val freqHz = 75.0 / 60.0
        var lastSnapshot = processor.processFrame(210.0, 45.0, 35.0, 0L)
        for (frame in 1..540) {
            val tMs = frame * 33L
            val tSec = tMs / 1000.0
            // Absorption dips at systole (inverted inside processor)
            val red = 210.0 - 4.0 * sin(2.0 * PI * freqHz * tSec)
            lastSnapshot = processor.processFrame(red, 45.0, 35.0, tMs)
            if (lastSnapshot.phase == PpgPhase.COMPLETED) break
        }
        assertEquals(PpgPhase.COMPLETED, lastSnapshot.phase)
        assertNotNull(lastSnapshot.finalBpm)
        assertTrue("Expected BPM near 75, got ${lastSnapshot.finalBpm}", lastSnapshot.finalBpm!! in 70..80)
    }
}
