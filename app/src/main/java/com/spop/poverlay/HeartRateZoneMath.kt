package com.spop.poverlay

import kotlin.math.abs
import kotlin.math.roundToInt

/** Zone boundaries as a percentage of maximum heart rate: 1-2, 2-3, 3-4, 4-5. */
private val ZoneBoundaryPercents = listOf(60, 70, 80, 90)

private fun boundariesFrom(anchor: Int, anchorIndex: Int): List<Int> {
    val maxHeartRate = anchor * 100.0 / ZoneBoundaryPercents[anchorIndex]
    return ZoneBoundaryPercents.map { (maxHeartRate * it / 100.0).roundToInt() }
}

/**
 * Called after the boundary at [anchorIndex] was edited, with all four boundaries [before] and
 * [after] the edit. Other boundaries are calculated from it at 60/70/80/90% of max heart rate:
 *  - blank boundaries are always filled in;
 *  - boundaries that were still the automatic values for the old number (give or take 1 bpm)
 *    follow the new number;
 *  - boundaries someone typed in by hand are left alone.
 */
internal fun fillZoneBoundaries(before: List<Int?>, after: List<Int?>, anchorIndex: Int): List<Int?> {
    if (before.size != ZoneBoundaryPercents.size || after.size != before.size) return after
    val anchor = after.getOrNull(anchorIndex)?.takeIf { it > 0 } ?: return after
    val oldAnchor = before[anchorIndex]?.takeIf { it > 0 }
    val oldAuto = oldAnchor?.let { boundariesFrom(it, anchorIndex) }
    val newAuto = boundariesFrom(anchor, anchorIndex)
    return after.mapIndexed { index, value ->
        when {
            index == anchorIndex -> value
            value == null -> newAuto[index]
            oldAuto != null && abs(value - oldAuto[index]) <= 1 -> newAuto[index]
            else -> value
        }
    }
}
