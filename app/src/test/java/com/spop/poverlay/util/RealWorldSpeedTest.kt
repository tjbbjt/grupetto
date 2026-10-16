package com.spop.poverlay.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealWorldSpeedTest {

    @Test
    fun `no power or invalid power is zero speed`() {
        assertEquals(0f, calculateRealWorldSpeedMph(0f), 0f)
        assertEquals(0f, calculateRealWorldSpeedMph(0.05f), 0f)
        assertEquals(0f, calculateRealWorldSpeedMph(-20f), 0f)
        assertEquals(0f, calculateRealWorldSpeedMph(Float.NaN), 0f)
        assertEquals(0f, calculateRealWorldSpeedMph(Float.POSITIVE_INFINITY), 0f)
    }

    @Test
    fun `about 196 watts reads about 19 mph`() {
        assertEquals(19.2f, calculateRealWorldSpeedMph(196f), 0.3f)
    }

    @Test
    fun `reference points follow the flat-road curve`() {
        assertEquals(10.8f, calculateRealWorldSpeedMph(50f), 0.3f)
        assertEquals(14.7f, calculateRealWorldSpeedMph(100f), 0.3f)
        assertEquals(17.3f, calculateRealWorldSpeedMph(150f), 0.3f)
        assertEquals(21.1f, calculateRealWorldSpeedMph(250f), 0.3f)
        assertEquals(22.6f, calculateRealWorldSpeedMph(300f), 0.3f)
        assertEquals(25.1f, calculateRealWorldSpeedMph(400f), 0.3f)
    }

    @Test
    fun `speed always rises with power`() {
        var previous = calculateRealWorldSpeedMph(1f)
        for (watts in 2..1000) {
            val speed = calculateRealWorldSpeedMph(watts.toFloat())
            assertTrue("speed must increase at $watts W", speed > previous)
            previous = speed
        }
    }

    @Test
    fun `real-world estimate is below Peloton's scale for normal riding power`() {
        for (watts in 50..400) {
            assertTrue(
                "$watts W",
                calculateRealWorldSpeedMph(watts.toFloat()) < calculateSpeedFromPelotonV1Power(watts.toFloat())
            )
        }
    }

    @Test
    fun `very high power stays finite`() {
        val speed = calculateRealWorldSpeedMph(5000f)
        assertTrue(speed.isFinite())
        assertTrue(speed > 0f)
    }

    @Test
    fun `model selector picks the requested formula`() {
        assertEquals(calculateSpeedFromPelotonV1Power(196f), speedMphForModel(196f, realWorld = false), 0f)
        assertEquals(calculateRealWorldSpeedMph(196f), speedMphForModel(196f, realWorld = true), 0f)
    }

    @Test
    fun `setting defaults to Peloton scale`() {
        assertFalse(SpeedModel.realWorld.value)
    }
}
