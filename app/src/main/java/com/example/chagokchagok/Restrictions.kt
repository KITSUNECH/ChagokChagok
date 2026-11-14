// 차곡車곡 – 주행 중 화면 제한
// 차(AAOS)가 알려 주는 CarUxRestrictions를 따른다. 제한이 켜지면 시트를 닫고 주행 화면만 남긴다.
// Pleos 문서: DO 앱은 isRequiresDistractionOptimization 과 함께 activeRestrictions 비트를 보고 기능별로 직접 제한한다.
//  NO_KEYBOARD → 글자 입력 막기 · NO_SETUP → 설정·처음 시작 막기(주차 전용 화면) · LIMIT_STRING_LENGTH → 긴 글 자르기
package com.example.chagokchagok

import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper

class Restrictions(private val ctx: Context, private val onChange: (Boolean) -> Unit) {
    var restricted = false; private set
    /** 지금 걸린 제한 비트 (CarUxRestrictions.UX_RESTRICTIONS_*) */
    var active = 0; private set
    /** 글 길이 제한이 걸렸을 때 한 줄의 최대 글자 수 */
    var maxString = 120; private set
    fun has(flag: Int) = restricted && (active and flag) != 0
    val noKeyboard get() = has(CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD)
    val noSetup get() = has(CarUxRestrictions.UX_RESTRICTIONS_NO_SETUP)
    /** 길이 제한이 걸려 있으면 잘라서 돌려준다 */
    fun fit(text: String) = if (has(CarUxRestrictions.UX_RESTRICTIONS_LIMIT_STRING_LENGTH) && text.length > maxString) text.take(maxString - 1) + "…" else text
    var status = "준비 중"; private set
    private var car: android.car.Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private val main = Handler(Looper.getMainLooper())

    fun start() {
        if (Car.offline) { status = "꺼짐 (화면 확인용)"; return }
        try {
            val c = android.car.Car.createCar(ctx.applicationContext) ?: run { status = "차 서비스 없음"; return }
            val m = c.getCarManager(android.car.Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            read(m.currentCarUxRestrictions)
            m.registerListener { r: CarUxRestrictions? ->
                main.post {
                    val before = restricted to active
                    read(r)
                    if ((restricted to active) != before) onChange(restricted)
                }
            }
            car = c; manager = m
        } catch (e: Throwable) { status = "연결 실패: ${e.javaClass.simpleName}" }
    }

    private fun read(r: CarUxRestrictions?) {
        restricted = r?.isRequiresDistractionOptimization == true
        active = r?.activeRestrictions ?: 0
        r?.maxRestrictedStringLength?.takeIf { it > 0 }?.let { maxString = it }
        status = if (restricted) "주행 중 제한 켜짐 (제한 비트 $active)" else "정차 (제한 없음)"
    }

    fun stop() { try { manager?.unregisterListener(); car?.disconnect() } catch (_: Throwable) {} }
}
