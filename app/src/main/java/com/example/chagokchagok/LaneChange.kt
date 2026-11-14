// 차곡차곡 – 차선 변경 기록 (추정)
// 인터뷰: "제일 어려운 게 끼어들기(차선 변경)다. 10년, 20년이 지나도 비 오거나 어두우면 어렵다."
// 옆 차의 속도·거리는 공개 SDK에 없고 실시간 판단은 제조사의 안전 기능이다. 앱은 그 순간에 끼어들지 않는다.
// 대신 차가 본 것으로 "어디서, 깜빡이를 켜고 몇 초 기다렸는지, 비·밤이었는지"를 남겨
// 주차 후에 돌아보고, 다음에 그 구간에 들어서기 전에 미리 알려 준다. 같은 구간에서 기다린 시간이 줄면 그게 늘었다는 근거가 된다.
//
// 판정 (추정):
//  · 깜빡이(LEFT/RIGHT)가 켜질 때 내비가 300m 안의 회전을 안내 중이면 회전이지 차선 변경이 아니다.
//  · 조향각(Vehicle SDK SteeringWheelAngle)을 받으면: 기준에서 4° 넘게 움직인 때를 "바꾸기 시작"으로 본다.
//    30° 넘게 돌면 교차로 회전으로 보고 차선 변경에서 뺀다.
//  · 조향각을 못 받으면: 내비 경로 안내 중이고 회전 안내가 없을 때만, 깜빡이를 켜 둔 시간을 기다린 시간으로 본다.
package com.example.chagokchagok

import kotlin.math.abs

/** 차선 변경 한 번. wait: 깜빡이를 켜고 바꾸기 시작하기까지(초). moved=false 는 깜빡이를 켰다가 바꾸지 못하고 끈 것 */
internal data class LaneMove(val side: String, val wait: Float, val moved: Boolean, val steerKnown: Boolean, val rain: Boolean, val dark: Boolean) {
    fun encode() = "$side;w=${"%.1f".format(java.util.Locale.US, wait)};m=${if (moved) 1 else 0};s=${if (steerKnown) 1 else 0};r=${if (rain) 1 else 0};n=${if (dark) 1 else 0}"

    companion object {
        fun decode(v: String): LaneMove? = try {
            val parts = v.split(";"); val kv = parts.drop(1).associate { it.substringBefore("=") to it.substringAfter("=") }
            LaneMove(parts[0], kv["w"]!!.toFloat(), kv["m"] == "1", kv["s"] == "1", kv["r"] == "1", kv["n"] == "1")
        } catch (_: Throwable) { null }
    }
}

/** 이번 주행의 차선 변경들 (사건 위치와 진행 정도 포함) */
/** 급하게 선 횟수. 속도 신호를 받은 주행에서만 뜻이 있다 */
internal fun Drive.hardBrakes() = events.count { it.type == Evt.HARD }
internal fun Drive.speedSeen() = events.any { it.type == Evt.SPEED }

internal fun Drive.lanes(): List<Pair<Ev, LaneMove>> = events.filter { it.type == Evt.LANE }.mapNotNull { e -> LaneMove.decode(e.value)?.let { e to it } }

/** 바꾼 차선 변경의 기다린 시간 가운데값 (없으면 null) */
internal fun Drive.laneWaitMedian(): Float? {
    val w = lanes().filter { it.second.moved }.map { it.second.wait }.sorted()
    return if (w.isEmpty()) null else w[w.size / 2]
}

/** 주행 중에 깜빡이와 조향각을 보고 차선 변경을 찾는다 */
internal class LaneWatch(private val now: () -> Long = { android.os.SystemClock.elapsedRealtime() }) {
    private var side: String? = null
    private var onAt = 0L
    private var movedAt = 0L
    private var base = 0f
    private var peak = 0f
    private var steerSeenAtStart = false
    private var rain = false; private var dark = false
    private var at: Pair<Double, Double>? = null
    /** 이번 주행에서 조향각을 한 번이라도 받았는지 */
    var steerSeen = false; private set
    private var lastAngle = 0f
    var onLane: ((LaneMove, Pair<Double, Double>?) -> Unit)? = null

    fun reset() { side = null; steerSeen = false }

    /**
     * 깜빡이가 바뀌었다.
     * turnNear: 내비가 150m 안의 회전을 안내 중. routeActive: 내비 경로 안내 중.
     */
    fun signal(value: String, turnNear: Boolean, routeActive: Boolean, rain: Boolean, dark: Boolean, fix: Pair<Double, Double>?) {
        if (value == "LEFT" || value == "RIGHT") {
            if (side != null || turnNear) return
            if (!steerSeen && !routeActive) return   // 조향각도 내비도 없으면 회전과 구분할 수 없다
            side = value; onAt = now(); movedAt = 0L; base = lastAngle; peak = 0f
            steerSeenAtStart = steerSeen; this.rain = rain; this.dark = dark; at = fix
        } else if (side != null) finish()
    }

    fun steer(angle: Float) {
        steerSeen = true; lastAngle = angle
        val s = side ?: return
        val d = abs(angle - base)
        peak = maxOf(peak, d)
        if (movedAt == 0L && d >= 4f) movedAt = now()
        if (s.isNotEmpty() && peak > 30f) side = null   // 교차로 회전: 차선 변경이 아니다
    }

    private fun finish() {
        val s = side ?: return; side = null
        val dur = (now() - onAt) / 1000f
        if (dur < 1f) return   // 스치듯 켠 깜빡이
        val known = steerSeenAtStart || steerSeen
        val lane = if (known) LaneMove(s, if (movedAt > 0) (movedAt - onAt) / 1000f else dur, movedAt > 0, true, rain, dark)
                   else LaneMove(s, dur, true, false, rain, dark)
        onLane?.invoke(lane, at)
    }
}
