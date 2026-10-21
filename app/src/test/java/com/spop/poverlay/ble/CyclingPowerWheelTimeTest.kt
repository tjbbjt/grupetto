package com.spop.poverlay.ble

import org.junit.Assert.assertEquals
import org.junit.Test

class CyclingPowerWheelTimeTest {

    @Test
    fun `wheel event time is doubled for the 1 over 2048 second unit`() {
        assertEquals(0, cyclingPowerWheelEventTime(0))
        assertEquals(2048, cyclingPowerWheelEventTime(1024))
        assertEquals(1000, cyclingPowerWheelEventTime(500))
    }

    @Test
    fun `wheel event time wraps as a uint16`() {
        assertEquals(0, cyclingPowerWheelEventTime(32768))
        assertEquals(65534, cyclingPowerWheelEventTime(65535))
    }

    @Test
    fun `interval between events stays consistent across the wrap`() {
        val before = cyclingPowerWheelEventTime(65530)
        val after = cyclingPowerWheelEventTime((65530 + 20) and 0xFFFF)
        assertEquals(40, (after - before) and 0xFFFF)
    }
}
