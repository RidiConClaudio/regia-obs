package com.regiaobs.app

import com.regiaobs.app.obs.AudioCurve
import org.junit.Assert.assertEquals
import org.junit.Test

class AudioCurveTest {

    @Test
    fun `deflection and multiplier roundtrip along cubic curve`() {
        val samples = listOf(0f, 0.25f, 0.5f, 0.75f, 1f)
        for (deflection in samples) {
            val mul = AudioCurve.deflectionToMul(deflection)
            val back = AudioCurve.mulToDeflection(mul)
            assertEquals(deflection, back, 0.0005f)
        }
        assertEquals(0.125f, AudioCurve.deflectionToMul(0.5f), 0.0001f)
    }

    @Test
    fun `bounds are clamped safely`() {
        assertEquals(0f, AudioCurve.deflectionToMul(-0.5f), 0.0001f)
        assertEquals(1f, AudioCurve.deflectionToMul(1.5f), 0.0001f)
        assertEquals(0f, AudioCurve.mulToDeflection(-1f), 0.0001f)
        assertEquals(1f, AudioCurve.mulToDeflection(2f), 0.0001f)
    }

    @Test
    fun `multiplier and decibel conversions and formatting work cleanly`() {
        assertEquals(0f, AudioCurve.mulToDb(1f), 0.01f)
        assertEquals(-6.02f, AudioCurve.mulToDb(0.5f), 0.05f)
        assertEquals(-100f, AudioCurve.mulToDb(0f), 0.01f)

        assertEquals(1f, AudioCurve.dbToMul(0f), 0.001f)
        assertEquals(0f, AudioCurve.dbToMul(-100f), 0.001f)
        assertEquals(0.501f, AudioCurve.dbToMul(-6f), 0.01f)

        assertEquals("-∞ dB", AudioCurve.formatDb(-100f))
        assertEquals("0.0 dB", AudioCurve.formatDb(-0.01f))
        assertEquals("-12.5 dB", AudioCurve.formatDb(-12.5f))
    }
}
