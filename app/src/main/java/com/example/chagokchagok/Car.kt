// 차곡車곡 – 차와 내비에 연결하는 곳
// Vehicle SDK 와 NaviHelper SDK 의 콜백을 받아 지금 상태를 보관하고,
// 값이 바뀔 때마다 화면 스레드에서 Listener 를 불러 준다.
@file:Suppress("DEPRECATION")

package com.example.chagokchagok

import android.content.Context
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DestinationInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.area.WindowArea
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.constant.area.WindowValue
import ai.pleos.playground.vehicle.constant.control.CruiseControl
import ai.pleos.playground.vehicle.constant.control.LightState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener

class Car(private val ctx: Context, private val listener: Listener) {

    interface Listener {
        /** 차량 신호가 바뀌었다. type 은 Evt 의 값, value 는 새 상태. */
        fun onCarEvent(type: String, value: String)
        /** 주행거리(누적 km)가 바뀌었다. */
        fun onDistance(km: Float) {}
        /** 내비가 목적지에 도착했다고 알렸다. */
        fun onArrived() {}
    }

    // 지금 차의 상태. 화면이 언제든 읽어 간다.
    var gear = "P"; private set
    var signal = "OFF"; private set
    var brake = false; private set
    var wiper = false; private set
    var lowBeam = false; private set
    var belt = false; private set
    var cruise = false; private set
    var steering = 0f; private set
    var odometerKm: Float? = null; private set
    var naviConnected = false; private set
    var lat: Double? = null; private set
    var lng: Double? = null; private set
    var destLat: Double? = null; private set
    var destLng: Double? = null; private set
    var destName: String? = null; private set
    /** 신호별로 마지막에 실패한 이유. 화면의 점검 칸에서 보여 준다. */
    val failures = mutableMapOf<String, String>()

    private val main = Handler(Looper.getMainLooper())
    private fun ui(block: () -> Unit) { main.post(block) }

    private lateinit var vehicle: Vehicle
    private lateinit var navi: NaviHelper
    private val frontWindow = WindowArea(WindowValue.FRONT_WINDSHIELD)
    private val driverSeat = SeatArea(SeatValue.ROW_1_LEFT)

    // ── 차량 신호 리스너 ──
    private val signalL = object : TurnSignalStateListener {
        override fun onTurnSignalStateUpdated(state: TurnSignalValue?) = ui {
            signal = state?.name ?: "OFF"; listener.onCarEvent(Evt.SIGNAL, signal)
        }
        override fun onFailed(e: Exception) { failures[Evt.SIGNAL] = e.message ?: "실패" }
    }
    private val brakeL = object : BrakePedalStatusListener {
        override fun onBrakePedalStatusUpdated(status: Boolean?) = ui {
            brake = status == true; listener.onCarEvent(Evt.BRAKE, if (brake) "on" else "off")
        }
        override fun onFailed(e: Exception) { failures[Evt.BRAKE] = e.message ?: "실패" }
    }
    private val steeringL = object : SteeringWheelAngleListener {
        override fun onSteeringWheelAngleUpdated(angle: Float?) { steering = angle ?: 0f }
        override fun onFailed(e: Exception) { failures["steering"] = e.message ?: "실패" }
    }
    private val gearL = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = ui {
            this@Car.gear = when (gear) {
                VehicleGear.GEAR_DRIVE -> "D"; VehicleGear.GEAR_REVERSE -> "R"
                VehicleGear.GEAR_NEUTRAL -> "N"; VehicleGear.GEAR_PARK -> "P"; else -> this@Car.gear
            }
            listener.onCarEvent(Evt.GEAR, this@Car.gear)
        }
        override fun onFailed(e: Exception) { failures[Evt.GEAR] = e.message ?: "실패" }
    }
    private val wiperL = object : WiperStateListener {
        override fun onWiperStateUpdated(state: WiperState?) = ui {
            wiper = state == WiperState.ON; listener.onCarEvent(Evt.WIPER, if (wiper) "on" else "off")
        }
        override fun onFailed(e: Exception) { failures[Evt.WIPER] = e.message ?: "실패" }
    }
    private val lightL = object : LightStateListener {
        override fun onLightStateUpdated(state: LightState?) = ui {
            lowBeam = state == LightState.ON; listener.onCarEvent(Evt.LIGHT, if (lowBeam) "on" else "off")
        }
        override fun onFailed(e: Exception) { failures[Evt.LIGHT] = e.message ?: "실패" }
    }
    private val beltL = object : SeatBeltBuckledStateListener {
        override fun onSeatBeltBuckledStateUpdated(buckled: Boolean?) = ui {
            belt = buckled == true; listener.onCarEvent("belt", if (belt) "on" else "off")
        }
        override fun onFailed(e: Exception) { failures["belt"] = e.message ?: "실패" }
    }
    private val cruiseL = object : CruiseControlStateListener {
        override fun onCruiseControlStateUpdated(state: CruiseControl?) = ui {
            val on = state == CruiseControl.ACTIVATED
            if (on != cruise) { cruise = on; listener.onCarEvent(Evt.CRUISE, if (on) "on" else "off") }
        }
        override fun onFailed(e: Exception) { failures[Evt.CRUISE] = e.message ?: "실패" }
    }
    private val distanceL = object : DistanceDrivenListener {
        override fun onDistanceDrivenUpdated(distance: Float?) = ui {
            if (distance != null) { odometerKm = distance; listener.onDistance(distance) }
        }
        override fun onFailed(e: Exception) { failures["distance"] = e.message ?: "실패" }
    }

    // ── 내비 이벤트 리스너 ──
    private val naviL = object : NaviHelperEventListener {
        override fun onReRouteEnded() = ui { listener.onCarEvent(Evt.LOST, "reroute") }
        override fun onDestinationArrived(destinationArrivedInfo: ai.pleos.playground.navi.data.DestinationArrivedInfo) =
            ui { listener.onArrived() }
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            lat = currentLocationInfo.latitude; lng = currentLocationInfo.longitude
        }
        override fun onDestinationInfo(destinationInfo: DestinationInfo) {
            destLat = destinationInfo.latitude; destLng = destinationInfo.longitude; destName = destinationInfo.name
        }
        override fun onError(errorCode: NaviErrorCode) { failures["navi"] = errorCode.toString() }
    }

    fun start() {
        attempt("vehicle") { vehicle = Vehicle(ctx); vehicle.initialize() }
        // 하나가 지원되지 않아도 나머지는 계속 등록되도록 각각 감싼다.
        attempt(Evt.SIGNAL) { vehicle.getTurnSignal().registerTurnSignalState(signalL) }
        attempt(Evt.BRAKE) { vehicle.getBrake().registerBrakePedalStatus(brakeL) }
        attempt("steering") { vehicle.getSteeringWheel().registerSteeringWheelAngle(steeringL) }
        attempt(Evt.GEAR) { vehicle.getGear().registerGearSelection(gearL) }
        attempt(Evt.WIPER) { vehicle.getWiper().registerWiperState(frontWindow, wiperL) }
        attempt(Evt.LIGHT) { vehicle.getLight().registerLowBeamLightState(lightL) }
        attempt("belt") { vehicle.getSeat().registerSeatBeltBuckledState(driverSeat, beltL) }
        attempt("distance") { vehicle.getOdometer().registerDistanceDriven(distanceL) }
        attempt(Evt.CRUISE) { vehicle.getDrivingMode().registerCruiseControlState(cruiseL) }

        navi = NaviHelper(ctx.applicationContext)
        navi.initialize(
            onConnected = { naviConnected = true; navi.addListener(naviL); refreshNavi() },
            onFailed = { t -> failures["navi"] = t.message ?: "연결 실패" }
        )
    }

    fun stop() {
        attempt("") { vehicle.getTurnSignal().unregisterTurnSignalState(signalL) }
        attempt("") { vehicle.getBrake().unregisterBrakePedalStatus(brakeL) }
        attempt("") { vehicle.getSteeringWheel().unregisterSteeringWheelAngle(steeringL) }
        attempt("") { vehicle.getGear().unregisterGearSelection(gearL) }
        attempt("") { vehicle.getWiper().unregisterWiperState(frontWindow, wiperL) }
        attempt("") { vehicle.getLight().unregisterLowBeamLightState(lightL) }
        attempt("") { vehicle.getSeat().unregisterSeatBeltBuckledState(driverSeat, beltL) }
        attempt("") { vehicle.getOdometer().unregisterDistanceDriven(distanceL) }
        attempt("") { vehicle.getDrivingMode().unregisterCruiseControlState(cruiseL) }
        attempt("") { vehicle.release() }
        attempt("") { navi.removeListener(naviL); navi.release() }
    }

    /** 내비에 지금 위치와 목적지를 물어본다. 답은 위 리스너로 따로 온다. */
    fun refreshNavi() {
        if (!naviConnected) return
        attempt("navi") { navi.getCurrentLocationInfo(); navi.getDestinationInfo() }
    }

    /** 교본의 목적지를 내비에 넣는다. 좌표가 없거나 내비가 연결되지 않았으면 false. */
    fun requestRoute(name: String, lat: Double?, lng: Double?): Boolean {
        if (!naviConnected || lat == null || lng == null) return false
        return attempt("navi") {
            navi.requestRoute(
                RouteInfo(
                    latitude = lat, longitude = lng, displayLongitude = null, displayLatitude = null,
                    poiName = name, poiId = "", poiSubId = "", address = ""
                )
            )
        }
    }

    private fun attempt(key: String, block: () -> Unit): Boolean = try {
        block(); true
    } catch (e: Throwable) {
        if (key.isNotEmpty()) failures[key] = e.message ?: e.javaClass.simpleName
        false
    }
}
