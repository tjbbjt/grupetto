package com.spop.poverlay

import org.junit.Assert.assertEquals
import org.junit.Test

class HeartRateZoneMathTest {
    private val none = listOf<Int?>(null, null, null, null)
    private val defaults = listOf<Int?>(108, 126, 144, 162)

    @Test
    fun `one boundary fills the blank rest at 60 70 80 90 percent of max`() {
        assertEquals(defaults, fillZoneBoundaries(none, listOf(null, 126, null, null), 1))
        assertEquals(defaults, fillZoneBoundaries(none, listOf(108, null, null, null), 0))
        assertEquals(defaults, fillZoneBoundaries(none, listOf(null, null, null, 162), 3))
    }

    @Test
    fun `changing one of an automatic set moves the whole set`() {
        // Max 190: 114, 133, 152, 171
        assertEquals(
            listOf<Int?>(114, 133, 152, 171),
            fillZoneBoundaries(defaults, listOf(108, 133, 144, 162), 1)
        )
    }

    @Test
    fun `boundaries typed by hand are left alone`() {
        // 100 is not the automatic value for 126, so it stays; the automatic ones follow.
        assertEquals(
            listOf<Int?>(100, 133, 152, 171),
            fillZoneBoundaries(listOf(100, 126, 144, 162), listOf(100, 133, 144, 162), 1)
        )
    }

    @Test
    fun `blank or zero anchor changes nothing`() {
        assertEquals(none, fillZoneBoundaries(none, none, 2))
        val zero = listOf<Int?>(0, null, null, null)
        assertEquals(zero, fillZoneBoundaries(none, zero, 0))
    }

    @Test
    fun `wrong sized lists are returned unchanged`() {
        val short = listOf<Int?>(126)
        assertEquals(short, fillZoneBoundaries(short, short, 0))
    }
}
