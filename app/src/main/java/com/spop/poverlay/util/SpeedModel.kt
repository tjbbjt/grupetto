package com.spop.poverlay.util

import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.abs
import kotlin.math.max

/*
 * Real-world speed estimate for the Peloton Bike (V1).
 *
 * The Bike has no speed sensor. Grupetto's default speed is Peloton's own display scale
 * (see calculateSpeedFromPelotonV1Power), which reads generously: ~21 mph at 196 W.
 * This model instead solves the standard flat-road power balance
 *
 *     power * drivetrainEfficiency = 0.5 * airDensity * CdA * v^3 + Crr * mass * g * v
 *
 * for v, so ~196 W reads ~19 mph and the curve flattens the way real drag does.
 *
 * Assumptions: 90 kg rider + bike, road-tire rolling resistance, and a riding position
 * between hands-on-hoods (CdA ~0.35) and upright (CdA ~0.45).
 * Tuning: raise DRAG_AREA_M2 to read slower, lower it to read faster. At 196 W:
 * 0.35 -> ~20 mph, 0.45 -> ~18.5 mph, 0.55 -> ~17.5 mph, 0.69 -> ~16 mph.
 */
private const val RIDER_AND_BIKE_MASS_KG = 90.0
private const val ROLLING_RESISTANCE = 0.005
private const val DRAG_AREA_M2 = 0.40
private const val AIR_DENSITY_KG_M3 = 1.2
private const val DRIVETRAIN_EFFICIENCY = 0.97
private const val GRAVITY = 9.81
private const val METERS_PER_SECOND_TO_MPH = 2.23694
private const val NEWTON_MAX_ITERATIONS = 50
private const val NEWTON_TOLERANCE = 1e-9

/** Estimated flat-road speed in mph for a given power in watts. */
fun calculateRealWorldSpeedMph(power: Float): Float {
    if (!power.isFinite() || power < 0.1f) return 0f

    val wheelPower = power * DRIVETRAIN_EFFICIENCY
    val drag = 0.5 * AIR_DENSITY_KG_M3 * DRAG_AREA_M2
    val rolling = ROLLING_RESISTANCE * RIDER_AND_BIKE_MASS_KG * GRAVITY

    // f(v) = drag*v^3 + rolling*v - wheelPower is increasing and convex for v > 0,
    // so Newton's method converges from any positive start without leaving v > 0.
    var v = 5.0
    for (i in 0 until NEWTON_MAX_ITERATIONS) {
        val f = drag * v * v * v + rolling * v - wheelPower
        val slope = 3.0 * drag * v * v + rolling
        val next = v - f / slope
        val done = abs(next - v) < NEWTON_TOLERANCE
        v = next
        if (done) break
    }
    return (max(v, 0.0) * METERS_PER_SECOND_TO_MPH).toFloat()
}

/** Picks the speed formula for [power]: the real-world estimate, or Peloton's own scale. */
fun speedMphForModel(power: Float, realWorld: Boolean): Float =
    if (realWorld) calculateRealWorldSpeedMph(power) else calculateSpeedFromPelotonV1Power(power)

/**
 * Process-wide "Real-world speed" setting. Held here because the speed Flow is built inside
 * SensorInterface, which is created once by the Application and has no access to the
 * per-screen ConfigurationRepository. Off by default, which keeps Peloton's own scale.
 */
object SpeedModel {
    const val PreferenceKey = "realWorldSpeed"

    private val mutableRealWorld = MutableStateFlow(false)
    val realWorld: StateFlow<Boolean> = mutableRealWorld

    fun load(preferences: SharedPreferences) {
        mutableRealWorld.value = preferences.getBoolean(PreferenceKey, false)
    }

    fun set(preferences: SharedPreferences, enabled: Boolean) {
        mutableRealWorld.value = enabled
        preferences.edit().putBoolean(PreferenceKey, enabled).apply()
    }
}
