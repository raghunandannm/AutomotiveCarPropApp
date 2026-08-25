package com.example.capstone

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.math.roundToInt

private const val AREA_GLOBAL = 0

data class CarUiState(
    val speedMph: Int = 0,
    val gear: String = "--",
    val fuelLevel: String = "--",
    val manufacturer: String = "--",
    val carModel: String = "--",
    val modelYear: String = "--",
    val connected: Boolean = false
)

class CarPropertyRepository(private val context: Context) {

    private var car: Car? = null
    private var carPropertyManager: CarPropertyManager? = null

    private val _state = MutableStateFlow(CarUiState())
    val state: StateFlow<CarUiState> = _state

    private val callback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) = handleValue(value)

        override fun onErrorEvent(propertyId: Int, areaId: Int) {
            Log.e(TAG, "VHAL error event for property=$propertyId area=$areaId")
        }
    }

    fun connect() {
        car = Car.createCar(
            context,
            /* handler = */ null,
            Car.CAR_WAIT_TIMEOUT_WAIT_FOREVER
        ) { readyCar, ready ->
            if (ready) {
                carPropertyManager =
                    readyCar.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
                registerListeners()
                readStaticProperties()
                _state.value = _state.value.copy(connected = true)
            } else {
                _state.value = _state.value.copy(connected = false)
            }
        }
    }

    fun disconnect() {
        try {
            carPropertyManager?.unregisterCallback(callback)
        } catch (e: Exception) {
            Log.w(TAG, "unregisterCallback failed", e)
        }
        car?.disconnect()
        car = null
    }

    private fun registerListeners() {
        val mgr = carPropertyManager ?: return
        try {
            mgr.registerCallback(
                callback,
                VehiclePropertyIds.PERF_VEHICLE_SPEED,
                CarPropertyManager.SENSOR_RATE_ONCHANGE
            )
            mgr.registerCallback(
                callback,
                VehiclePropertyIds.GEAR_SELECTION,
                CarPropertyManager.SENSOR_RATE_ONCHANGE
            )
            mgr.registerCallback(
                callback,
                VehiclePropertyIds.FUEL_LEVEL,
                CarPropertyManager.SENSOR_RATE_ONCHANGE
            )
        } catch (e: SecurityException) {
            // Most common cause: the corresponding CAR_* permission wasn't
            // granted. See README step 7.
            Log.e(TAG, "Missing permission while registering VHAL callbacks", e)
        }
    }

    private fun readStaticProperties() {
        val mgr = carPropertyManager ?: return
        try {
            val make = mgr.getProperty(
                String::class.java, VehiclePropertyIds.INFO_MAKE, AREA_GLOBAL
            ).value
            val model = mgr.getProperty(
                String::class.java, VehiclePropertyIds.INFO_MODEL, AREA_GLOBAL
            ).value
            val year = mgr.getProperty(
                Integer::class.java, VehiclePropertyIds.INFO_MODEL_YEAR, AREA_GLOBAL
            ).value

            _state.value = _state.value.copy(
                manufacturer = make ?: "--",
                carModel = model ?: "--",
                modelYear = year?.toString() ?: "--"
            )
        } catch (e: SecurityException) {
            Log.e(TAG, "Missing CAR_INFO permission", e)
        } catch (e: Exception) {
            Log.e(TAG, "Failed reading static car info", e)
        }
    }

    private fun handleValue(value: CarPropertyValue<*>) {
        when (value.propertyId) {
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> {
                val metersPerSecond = value.value as? Float ?: return
                val mph = (metersPerSecond * 2.23694f).roundToInt().coerceAtLeast(0)
                _state.value = _state.value.copy(speedMph = mph)
            }

            VehiclePropertyIds.GEAR_SELECTION -> {
                val gear = value.value as? Int ?: return
                _state.value = _state.value.copy(gear = gearToLabel(gear))
            }

            VehiclePropertyIds.FUEL_LEVEL -> {
                val fuel = value.value as? Float ?: return
                _state.value = _state.value.copy(fuelLevel = fuel.roundToInt().toString())
            }
        }
    }

    private fun gearToLabel(gear: Int): String = when (gear) {
        1 -> "N (Neutral)"
        2 -> "R (Reverse)"
        4 -> "P (Park)"
        8 -> "D (Drive)"
        16 -> "1"
        32 -> "2"
        64 -> "3"
        128 -> "4"
        256 -> "5"
        else -> "Gear $gear"
    }

    companion object {
        private const val TAG = "CarPropertyRepo"
    }
}
