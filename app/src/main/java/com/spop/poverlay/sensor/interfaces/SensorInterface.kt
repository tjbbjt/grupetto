package com.spop.poverlay.sensor.interfaces

import com.spop.poverlay.util.SpeedModel
import com.spop.poverlay.util.speedMphForModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf

/** Distinguishes the kind of Peloton hardware backing a [SensorInterface]. */
enum class DeviceType { Bike, Tread }

interface SensorInterface {
    val bikeControl: com.spop.poverlay.control.BikeControl? get() = null
    val power: Flow<Float>
    /** Native watts remain available for comparison when an external meter supplies [power]. */
    val nativePower: Flow<Float> get() = power
    val usesExternalPower: Flow<Boolean> get() = flowOf(false)
    val cadence: Flow<Float>
    val resistance: Flow<Float>
    /**
     * Bike speed is derived from power (the Bike has no speed sensor). The formula follows the
     * "Real-world speed" setting: Peloton's own display scale (default) or a flat-road estimate.
     */
    val speed: Flow<Float>
        get() = combine(power, SpeedModel.realWorld) { watts, realWorld ->
            speedMphForModel(watts, realWorld)
        }

    /** Incline (percent grade). Meaningful only for treadmills; bikes report none. */
    val incline: Flow<Float>
        get() = flowOf(0f)

    /** The kind of device backing this interface. Defaults to [DeviceType.Bike]. */
    val deviceType: DeviceType
        get() = DeviceType.Bike

    /**
     * Reactive view of [deviceType]. Fixed interfaces emit their single type. Detection
     * is platform-based and synchronous, so the correct interface is chosen at construction
     * and the type never changes at runtime; this stays a Flow so the overlay can observe
     * it uniformly.
     */
    val deviceTypeFlow: Flow<DeviceType>
        get() = flowOf(deviceType)
}
