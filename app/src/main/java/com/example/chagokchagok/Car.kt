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
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SeatForeAftPositionListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.constant.area.WheelValue
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import ai.pleos.playground.vehicle.listener.WiperStateListener

class Car(private val ctx: Context, private val listener: Listener) {

    companion object {
        /** 화면 확인 테스트처럼 차 없이 앱을 띄울 때 true. 실제 앱에서는 쓰지 않는다. */
        @JvmStatic var offline = false
    }

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
    var passenger = false; private set
    /** 남은 연료(%) 또는 전기차의 배터리(%). 읽히지 않으면 null. */
    var energy: Int? = null; private set
    var washerLow = false; private set
    /** 워셔액 경고를 실제로 읽어 왔는지. */
    var washerKnown = false; private set
    /** 지금 켜져 있는 표시등 종류 */
    val lampsOn = mutableSetOf<String>()
    /** 바퀴별 공기압 (차가 주는 단위 그대로) */
    val tires = mutableMapOf<String, Float>()
    /** 운전석 앞뒤 위치 */
    var seatPos: Int? = null; private set
    // 내비가 알려 준 앞길. 없으면 null 이거나 0.
    /** 다음 회전 방향: LEFT, RIGHT, UTURN, 그 밖에는 OTHER. */
    var turn: String? = null; private set
    var turnMeters = 0; private set
    var turnText = ""; private set
    var speedLimit = 0; private set
    var cameraMeters = 0; private set
    var cameraLimit = 0; private set
    var schoolZone = false; private set
    var cautionMeters = 0; private set
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
    private val seatL = object : SeatOccupancyListener {
        override fun onSeatOccupancyUpdated(occupied: Boolean?) = ui {
            if (occupied == true && !passenger) { passenger = true; listener.onCarEvent(Evt.SEAT, "on") }
        }
        override fun onFailed(e: Exception) { failures[Evt.SEAT] = e.message ?: "실패" }
    }
    private val telltaleL = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(telltales: Telltales?) = ui {
            val now = (telltales?.telltales ?: emptyList()).filter { it.state != TelltaleState.OFF }.map { it.type.name }.toSet()
            setLamps(now)
        }
        override fun onFailed(e: Exception) { failures["telltale"] = e.message ?: "실패" }
    }
    private val seatPosL = object : SeatForeAftPositionListener {
        override fun onSeatForeAftPositionUpdated(position: Int?) = ui { if (position != null) seatPos = position }
        override fun onFailed(e: Exception) { failures["seatPos"] = e.message ?: "실패" }
    }
    private val passengerSeats = listOf(SeatValue.ROW_1_RIGHT, SeatValue.ROW_2_LEFT, SeatValue.ROW_2_RIGHT).map { SeatArea(it) }
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
        override fun onTBTInfo(tbtInfo: List<ai.pleos.playground.navi.data.TBTInfo>) = ui {
            val next = tbtInfo.firstOrNull()
            setTurn(next?.type?.name, next?.distance?.toInt() ?: 0, next?.description ?: "")
        }
        override fun onCurrentRoadSpeedLimit(speedLimit: Int) = ui { this@Car.speedLimit = speedLimit; listener.onCarEvent(Evt.NAVI, "limit") }
        override fun onCameraAlert(cameraAlerts: List<ai.pleos.playground.navi.data.CameraAlert>) = ui {
            val c = cameraAlerts.filter { it.remainDistance > 0 }.minByOrNull { it.remainDistance }
            cameraMeters = c?.remainDistance ?: 0; cameraLimit = c?.speedLimit ?: 0
            schoolZone = c?.type?.name?.contains("SCHOOL_ZONE") == true
            listener.onCarEvent(Evt.NAVI, "camera")
        }
        override fun onCautionPointInfo(cautionPoints: List<ai.pleos.playground.navi.data.CautionPoint>) = ui {
            cautionMeters = cautionPoints.filter { it.remainDistance > 0 }.minOfOrNull { it.remainDistance } ?: 0
            listener.onCarEvent(Evt.NAVI, "caution")
        }
        override fun onError(errorCode: NaviErrorCode) { failures["navi"] = errorCode.toString() }
    }

    fun start() {
        if (offline) return
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
        passengerSeats.forEach { seat -> attempt(Evt.SEAT) { vehicle.getSeat().registerSeatOccupancy(seat, seatL) } }
        attempt("telltale") { vehicle.getSafety().registerTelltales(telltaleL) }
        attempt("seatPos") { vehicle.getSeat().registerSeatForeAftPosition(driverSeat, seatPosL) }
        refreshCondition()

        navi = NaviHelper(ctx.applicationContext)
        navi.initialize(
            onConnected = { naviConnected = true; navi.addListener(naviL); refreshNavi() },
            onFailed = { t -> failures["navi"] = t.message ?: "연결 실패" }
        )
    }

    fun stop() {
        if (offline) return
        attempt("") { vehicle.getTurnSignal().unregisterTurnSignalState(signalL) }
        attempt("") { vehicle.getBrake().unregisterBrakePedalStatus(brakeL) }
        attempt("") { vehicle.getSteeringWheel().unregisterSteeringWheelAngle(steeringL) }
        attempt("") { vehicle.getGear().unregisterGearSelection(gearL) }
        attempt("") { vehicle.getWiper().unregisterWiperState(frontWindow, wiperL) }
        attempt("") { vehicle.getLight().unregisterLowBeamLightState(lightL) }
        attempt("") { vehicle.getSeat().unregisterSeatBeltBuckledState(driverSeat, beltL) }
        attempt("") { vehicle.getOdometer().unregisterDistanceDriven(distanceL) }
        attempt("") { vehicle.getDrivingMode().unregisterCruiseControlState(cruiseL) }
        passengerSeats.forEach { seat -> attempt("") { vehicle.getSeat().unregisterSeatOccupancy(seat, seatL) } }
        attempt("") { vehicle.getSafety().unregisterTelltales(telltaleL) }
        attempt("") { vehicle.getSeat().unregisterSeatForeAftPosition(driverSeat, seatPosL) }
        attempt("") { vehicle.release() }
        attempt("") { navi.removeListener(naviL); navi.release() }
    }

    /** 다음 사람에게 전할 차 상태를 다시 읽는다. 답은 조금 뒤에 따로 온다. */
    fun refreshCondition() {
        attempt("fuel") { vehicle.getFuel().getFuelPercent({ v -> if (v != null) energy = v }, { e -> failures["fuel"] = e.message ?: "실패" }) }
        attempt("battery") { vehicle.getEvBattery().getEvBatteryStateOfCharge({ v -> if (v != null && energy == null) energy = v }, { e -> failures["battery"] = e.message ?: "실패" }) }
        listOf(WheelValue.LEFT_FRONT, WheelValue.RIGHT_FRONT, WheelValue.LEFT_REAR, WheelValue.RIGHT_REAR).forEach { w ->
            attempt("tire") { vehicle.getTire().getTirePressure(WheelArea(w), { v -> if (v != null) tires[w.name] = v }, { e -> failures["tire"] = e.message ?: "실패" }) }
        }
        attempt("seatPos") { vehicle.getSeat().getCurrentSeatForeAftPosition(driverSeat, { v -> if (v != null) seatPos = v }, { e -> failures["seatPos"] = e.message ?: "실패" }) }
        attempt("washer") { vehicle.getSafety().hasLowWasherFluidWarning({ v -> washerLow = v == true; washerKnown = true }, { e -> failures["washer"] = e.message ?: "실패" }) }
    }
    fun resetPassenger() { passenger = false }

    /** 켜진 표시등이 바뀌면 바뀐 것만 알린다. 시험용 신호도 이 길로 들어온다. */
    fun setLamps(now: Set<String>) {
        (now - lampsOn).forEach { listener.onCarEvent(Evt.LAMP, "$it:on") }
        (lampsOn - now).forEach { listener.onCarEvent(Evt.LAMP, "$it:off") }
        lampsOn.clear(); lampsOn.addAll(now)
    }

    /** 놀란 운전자를 위해 내비 경로를 편안한 길로 바꾼다. */
    fun comfortable(): Boolean = naviConnected && attempt("navi") { navi.changeRouteOption(ai.pleos.playground.navi.constants.RouteOption.COMFORTABLE) }

    /** 모의 주행이 앞길 정보를 넣을 때 쓴다. 실제 내비 값이 아니다. */
    fun setAhead(limit: Int, cameraMeters: Int, cameraLimit: Int) {
        speedLimit = limit; this.cameraMeters = cameraMeters; this.cameraLimit = cameraLimit; schoolZone = false
        listener.onCarEvent(Evt.NAVI, "ahead")
    }

    /** 내비의 회전 안내를 우리 말로 바꿔 둔다. 시험용 신호도 이 길로 들어온다. */
    fun setTurn(type: String?, meters: Int, text: String) {
        turn = when {
            type == null || type == "TURN_UNKNOWN" || type == "TURN_STRAIGHT" || type == "TURN_ARRIVE" -> null
            type == "TURN_UTURN" -> "UTURN"
            type.endsWith("_R") || type == "TURN_RIGHT" -> "RIGHT"
            type.endsWith("_L") || type == "TURN_LEFT" -> "LEFT"
            else -> "OTHER"
        }
        turnMeters = meters; turnText = text
        listener.onCarEvent(Evt.NAVI, "turn")
    }

    /** 내비에 지금 위치와 목적지를 물어본다. 답은 위 리스너로 따로 온다. */
    fun refreshNavi() {
        if (!naviConnected) return
        attempt("navi") { navi.getCurrentLocationInfo(); navi.getDestinationInfo(); navi.getTBTInfo() }
    }

    /** 건네준 길의 목적지를 내비에 넣는다. 좌표가 없거나 내비가 연결되지 않았으면 false. */
    fun requestRoute(name: String, lat: Double?, lng: Double?, comfortable: Boolean = false): Boolean {
        if (!naviConnected || lat == null || lng == null) return false
        return attempt("navi") {
            navi.requestRoute(
                RouteInfo(
                    latitude = lat, longitude = lng, displayLongitude = null, displayLatitude = null,
                    poiName = name, poiId = "", poiSubId = "", address = "",
                    routeOption = if (comfortable) ai.pleos.playground.navi.constants.RouteOption.COMFORTABLE else ai.pleos.playground.navi.constants.RouteOption.RECOMMENDED
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
