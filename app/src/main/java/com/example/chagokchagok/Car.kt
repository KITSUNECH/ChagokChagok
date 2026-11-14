// 차곡車곡 – 차와 내비에 연결하는 곳
// Vehicle SDK 와 NaviHelper SDK 의 콜백을 받아 지금 상태를 보관하고,
// 값이 바뀔 때마다 화면 스레드에서 Listener 를 불러 준다.

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
import ai.pleos.playground.vehicle.constant.control.WiperState
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.CruiseControlStateListener
import ai.pleos.playground.vehicle.listener.DistanceDrivenListener
import ai.pleos.playground.vehicle.listener.LightStateListener
import ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import ai.pleos.playground.vehicle.listener.SeatForeAftPositionListener
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
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
        /** 내비가 알려 준 지금 위치 */
        fun onNaviLocation(lat: Double, lng: Double) {}
    }

    // 지금 차의 상태. 화면이 언제든 읽어 간다.
    var gear = "P"; private set
    /** GEAR_SELECTION 값을 한 번이라도 받았는지 */
    var gearKnown = false; private set
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
    /** 내비가 경로 안내를 시작했다고 알려 왔는지 (요청만 보낸 것과 구분) */
    var routeStarted = false; private set
    /** 지금 켜져 있는 경고 (엔진오일·브레이크액 수준은 실제 API, 그 밖의 표시등은 설정의 시험용 신호로만) */
    val lampsOn = mutableSetOf<String>()
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
    /** 조향각이 바뀔 때 (차선 변경 판정에 쓴다. 기록에는 남기지 않는다) */
    var onSteer: ((Float) -> Unit)? = null
    var odometerKm: Float? = null; private set
    var naviConnected = false; private set
    var lat: Double? = null; private set
    var lng: Double? = null; private set
    var destLat: Double? = null; private set
    var destLng: Double? = null; private set
    var destName: String? = null; private set
    /** 차대번호 (CarInfo SDK). 차종과 변속 방식을 알아내는 데만 쓰고 밖으로 보내지 않는다. */
    var vin: String? = null; private set
    var isEv: Boolean? = null; private set
    /** 이번 주행 연비 (차가 계산한 "현재" 연비: 내연기관 km/L, 전기차 km/kWh). 읽지 못하면 null */
    var economy: Float? = null; private set
    val economyUnit get() = if (isEv == true) "km/kWh" else "km/L"
    private val econL = object : ai.pleos.playground.vehicle.listener.FuelEconomyListener {
        override fun onFuelEconomyUpdated(value: Float?) = ui { if (value != null && value > 0f && isEv != true) economy = value }
        override fun onFailed(e: Exception) { failures["economy"] = e.message ?: "실패" }
    }
    private val evEconL = object : ai.pleos.playground.vehicle.listener.EvBatteryEfficiencyListener {
        override fun onEvBatteryEfficiencyUpdated(value: Float?) = ui { if (value != null && value > 0f) { isEv = true; economy = value } }
        override fun onFailed(e: Exception) { failures["evEconomy"] = e.message ?: "실패" }
    }
    /** 시험용·시연용 연비 (실제 차량 값이 아니다) */
    fun simEconomy(v: Float?) { economy = v }
    /** 신호별로 마지막에 실패한 이유. 화면의 점검 칸에서 보여 준다. */
    val failures: MutableMap<String, String> = java.util.concurrent.ConcurrentHashMap()   // SDK 콜백 스레드에서도 쓴다

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
        override fun onSteeringWheelAngleUpdated(angle: Float?) = ui { steering = angle ?: 0f; onSteer?.invoke(steering) }
        override fun onFailed(e: Exception) { failures["steering"] = e.message ?: "실패" }
    }
    // 기어: Pleos 문서는 Vehicle SDK의 Gear(지원 중단)를 쓰지 말고 AAOS 표준 GEAR_SELECTION 속성을 쓰라고 한다.
    private var carApi: android.car.Car? = null
    private var props: android.car.hardware.property.CarPropertyManager? = null
    private val gearCb = object : android.car.hardware.property.CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: android.car.hardware.CarPropertyValue<*>) = ui {
            gearKnown = true
            val g = when (value.value as? Int) {
                android.car.VehicleGear.GEAR_DRIVE -> "D"; android.car.VehicleGear.GEAR_REVERSE -> "R"
                android.car.VehicleGear.GEAR_NEUTRAL -> "N"; android.car.VehicleGear.GEAR_PARK -> "P"
                else -> if ((value.value as? Int ?: 0) >= 16) "D" else this@Car.gear   // 16 이상은 수동 1~9단(GEAR_FIRST…) = 앞으로
            }
            if (g != gear) { gear = g; listener.onCarEvent(Evt.GEAR, g) }
        }
        override fun onErrorEvent(propId: Int, zone: Int) { failures[Evt.GEAR] = "GEAR_SELECTION 오류" }
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
        override fun onRouteStarted(routeStartInfo: ai.pleos.playground.navi.data.RouteStartInfo) = ui { routeStarted = true; listener.onCarEvent(Evt.NAVI, "route") }
        override fun onRoutingCompleted() = ui { routeStarted = true; listener.onCarEvent(Evt.NAVI, "route") }
        override fun onDestinationArrived(destinationArrivedInfo: ai.pleos.playground.navi.data.DestinationArrivedInfo) =
            ui { listener.onArrived() }
        override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) {
            lat = currentLocationInfo.latitude; lng = currentLocationInfo.longitude
            val la = currentLocationInfo.latitude; val ln = currentLocationInfo.longitude
            ui { listener.onNaviLocation(la, ln) }
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
        // Pleos 문서대로: 신호마다 먼저 checkXxxCapabilities 로 지원 여부를 보고, 읽을 수 있을 때만 등록한다.
        // 하나가 지원되지 않아도 나머지는 계속 등록되도록 각각 감싼다.
        cap(Evt.SIGNAL, { ok, no -> vehicle.getTurnSignal().checkTurnSignalStateCapabilities(ok, no) }) { vehicle.getTurnSignal().registerTurnSignalState(signalL) }
        cap(Evt.BRAKE, { ok, no -> vehicle.getBrake().checkBrakePedalStatusCapabilities(ok, no) }) { vehicle.getBrake().registerBrakePedalStatus(brakeL) }
        cap("steering", { ok, no -> vehicle.getSteeringWheel().checkSteeringWheelAngleCapabilities({ c, _ -> ok(c) }, no) }) { vehicle.getSteeringWheel().registerSteeringWheelAngle(steeringL) }
        cap(Evt.WIPER, { ok, no -> vehicle.getWiper().checkWiperStateCapabilities({ c, _ -> ok(c) }, no) }) { vehicle.getWiper().registerWiperState(frontWindow, wiperL) }
        cap(Evt.LIGHT, { ok, no -> vehicle.getLight().checkLowBeamLightSwitchCapabilities({ c, _ -> ok(c) }, no) }) { vehicle.getLight().registerLowBeamLightState(lightL) }
        cap("belt", { ok, no -> vehicle.getSeat().checkSeatBeltBuckledCapabilities({ c, _ -> ok(c) }, no) }) { vehicle.getSeat().registerSeatBeltBuckledState(driverSeat, beltL) }
        cap("distance", { ok, no -> vehicle.getOdometer().checkCurrentDistanceDrivenCapabilities(ok, no) }) { vehicle.getOdometer().registerDistanceDriven(distanceL) }
        cap(Evt.CRUISE, { ok, no -> vehicle.getDrivingMode().checkCruiseControlStateCapabilities(ok, no) }) { vehicle.getDrivingMode().registerCruiseControlState(cruiseL) }
        cap(Evt.SEAT, { ok, no -> vehicle.getSeat().checkSeatOccupancyCapabilities({ c, _ -> ok(c) }, no) }) { passengerSeats.forEach { seat -> vehicle.getSeat().registerSeatOccupancy(seat, seatL) } }
        attempt("seatPos") { vehicle.getSeat().registerSeatForeAftPosition(driverSeat, seatPosL) }
        // 표시등(Telltales)과 바퀴별 공기압은 PRIVATE 권한(써드파티 앱 불가)이라 쓰지 않는다. 설정의 시험용 신호로만 화면을 확인한다.
        // 기어: AAOS CarPropertyManager GEAR_SELECTION (권한 android.car.permission.CAR_POWERTRAIN)
        attempt(Evt.GEAR) {
            val c = android.car.Car.createCar(ctx); carApi = c
            val pm = c.getCarManager(android.car.Car.PROPERTY_SERVICE) as android.car.hardware.property.CarPropertyManager; props = pm
            if (!pm.registerCallback(gearCb, android.car.VehiclePropertyIds.GEAR_SELECTION, android.car.hardware.property.CarPropertyManager.SENSOR_RATE_ONCHANGE))
                failures[Evt.GEAR] = "GEAR_SELECTION 을 지원하지 않음"
        }
        // 연비: Vehicle SDK Fuel(현재 연비) / EvBattery(현재 전비). 드라이빙 뷰의 "현 주행 정보" 연비와 같은 종류의 값
        cap("economy", { ok, no -> vehicle.getFuel().checkCurrentFuelEconomyCapabilities(ok, no) }) { vehicle.getFuel().registerCurrentFuelEconomy(econL) }
        cap("evEconomy", { ok, no -> vehicle.getEvBattery().checkCurrentEvEfficiencyCapabilities(ok, no) }) { vehicle.getEvBattery().registerCurrentEvEfficiency(evEconL) }
        refreshCondition()
        cap("vin", { ok, no -> vehicle.getCarInfo().checkVINInfoCapabilities(ok, no) }) { vehicle.getCarInfo().getVINInfo({ v -> ui { vin = v?.trim()?.takeIf { it.length >= 10 }; listener.onCarEvent(Evt.NAVI, "vin") } }, { e -> failures["vin"] = e.message ?: "실패" }) }

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
        attempt("") { props?.unregisterCallback(gearCb); carApi?.disconnect() }
        attempt("") { vehicle.getWiper().unregisterWiperState(frontWindow, wiperL) }
        attempt("") { vehicle.getLight().unregisterLowBeamLightState(lightL) }
        attempt("") { vehicle.getSeat().unregisterSeatBeltBuckledState(driverSeat, beltL) }
        attempt("") { vehicle.getOdometer().unregisterDistanceDriven(distanceL) }
        attempt("") { vehicle.getDrivingMode().unregisterCruiseControlState(cruiseL) }
        passengerSeats.forEach { seat -> attempt("") { vehicle.getSeat().unregisterSeatOccupancy(seat, seatL) } }
        attempt("") { vehicle.getSeat().unregisterSeatForeAftPosition(driverSeat, seatPosL) }
        attempt("") { vehicle.release() }
        attempt("") { navi.removeListener(naviL); navi.release() }
    }

    /** 다음 사람에게 전할 차 상태를 다시 읽는다. 답은 조금 뒤에 따로 온다. */
    // SDK 응답은 다른 스레드에서 오므로 상태는 화면 스레드에서 바꾼다
    fun refreshCondition() {
        attempt("fuel") { vehicle.getFuel().getFuelPercent({ v -> ui { if (v != null) energy = v } }, { e -> failures["fuel"] = e.message ?: "실패" }) }
        attempt("battery") { vehicle.getEvBattery().getEvBatteryStateOfCharge({ v -> ui { if (v != null) { isEv = true; if (energy == null) energy = v } } }, { e -> failures["battery"] = e.message ?: "실패" }) }
        attempt("seatPos") { vehicle.getSeat().getCurrentSeatForeAftPosition(driverSeat, { v -> ui { if (v != null) seatPos = v } }, { e -> failures["seatPos"] = e.message ?: "실패" }) }
        attempt("washer") { vehicle.getSafety().hasLowWasherFluidWarning({ v -> ui { washerLow = v == true; washerKnown = true } }, { e -> failures["washer"] = e.message ?: "실패" }) }
        // 공개 API(CAR_SAFETY)로 읽는 실제 경고: 엔진오일·브레이크액 수준. 낮으면 표시등처럼 알린다.
        fun level(key: String, lamp: String, get: ((ai.pleos.playground.vehicle.constant.control.OilLevelState?) -> Unit, (Exception) -> Unit) -> Unit) =
            attempt(key) { get({ v -> ui { val low = v == ai.pleos.playground.vehicle.constant.control.OilLevelState.LOW || v == ai.pleos.playground.vehicle.constant.control.OilLevelState.CRITICALLY_LOW
                setLamps(if (low) lampsOn + lamp else lampsOn - lamp) } }, { e -> failures[key] = e.message ?: "실패" }) }
        level("oil", "ENGINE_OIL_LEVEL") { ok, no -> vehicle.getSafety().getEngineOilLevelState(ok, no) }
        level("brakeFluid", "BRAKE_FLUID_LEVEL") { ok, no -> vehicle.getSafety().getBrakeFluidLevelState(ok, no) }
    }
    fun resetPassenger() { passenger = false }

    /** 켜진 표시등이 바뀌면 바뀐 것만 알린다. 시험용 신호도 이 길로 들어온다. */
    fun setLamps(now: Set<String>) {
        (now - lampsOn).forEach { listener.onCarEvent(Evt.LAMP, "$it:on") }
        (lampsOn - now).forEach { listener.onCarEvent(Evt.LAMP, "$it:off") }
        lampsOn.clear(); lampsOn.addAll(now)
    }

    /** Pleos 내비 화면을 앞으로 불러온다. keyword가 있으면 장소 검색 화면으로. 연결 안 됐으면 false. */
    fun openNavi(keyword: String? = null): Boolean = naviConnected && attempt("navi") {
        navi.requestNavigationScreen(if (keyword != null) ai.pleos.playground.navi.data.NavigationScreenRequest.SearchPlace(keyword) else ai.pleos.playground.navi.data.NavigationScreenRequest.Foreground)
    }

    /** 놀란 운전자를 위해 내비 경로를 편안한 길로 바꾼다. */
    fun comfortable(): Boolean = naviConnected && attempt("navi") { navi.changeRouteOption(ai.pleos.playground.navi.constants.RouteOption.COMFORTABLE) }

    /** 시험용·시연용 신호 (실제 차량 신호가 아니다) */
    fun simEvent(type: String, value: String) {
        when (type) { Evt.SIGNAL -> signal = value; Evt.BRAKE -> brake = value == "on" }
        Telemetry.fake { listener.onCarEvent(type, value) }
    }

    /** 시험용·시연용 차선 변경: 깜빡이를 켜고 wait 초 뒤 조향, 조금 뒤 끈다. 실제 차량 신호가 아니다. */
    fun simLaneChange(side: String, waitSec: Float, main: android.os.Handler) {
        signal = side; Telemetry.fake { listener.onCarEvent(Evt.SIGNAL, side) }
        main.postDelayed({ steering = if (side == "LEFT") -6f else 6f; onSteer?.invoke(steering) }, (waitSec * 1000).toLong())
        main.postDelayed({ steering = 0f; onSteer?.invoke(0f); signal = "OFF"; Telemetry.fake { listener.onCarEvent(Evt.SIGNAL, "OFF") } }, (waitSec * 1000).toLong() + 2500)
    }

    /** 모의 주행이 앞길 정보를 넣을 때 쓴다. 실제 내비 값이 아니다. */
    fun setAhead(limit: Int, cameraMeters: Int, cameraLimit: Int) {
        speedLimit = limit; this.cameraMeters = cameraMeters; this.cameraLimit = cameraLimit; schoolZone = false
        Telemetry.fake { listener.onCarEvent(Evt.NAVI, "ahead") }
    }

    /** 모의 주행·시험용 회전 안내 (실제 내비 값이 아니다) */
    fun simTurn(type: String?, meters: Int) = Telemetry.fake { setTurn(type, meters, "") }

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
        routeStarted = false
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

    /** 문서대로 지원 여부(AccessType READ/READ_WRITE)를 먼저 확인하고 등록한다. 지원하지 않으면 이유를 남긴다. */
    private fun cap(key: String, check: (ok: (CapabilityConfig) -> Unit, no: (Exception) -> Unit) -> Unit, register: () -> Unit) {
        attempt(key) {
            check({ c -> if (c.accessType == AccessType.READ || c.accessType == AccessType.READ_WRITE) attempt(key) { register() } else failures[key] = "지원하지 않음 (${c.accessType})" },
                { e -> failures[key] = "지원 확인 실패: " + (e.message ?: e.javaClass.simpleName) })
        }
    }

    private fun attempt(key: String, block: () -> Unit): Boolean = try {
        block(); true
    } catch (e: Throwable) {
        if (key.isNotEmpty()) failures[key] = e.message ?: e.javaClass.simpleName
        false
    }
}
