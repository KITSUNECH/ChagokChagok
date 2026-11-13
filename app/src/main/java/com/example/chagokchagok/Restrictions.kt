// 차곡車곡 – 주행 중 화면 제한
// 차(AAOS)가 알려 주는 CarUxRestrictions를 따른다. 제한이 켜지면 시트를 닫고 주행 화면만 남긴다.
package com.example.chagokchagok

import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.os.Handler
import android.os.Looper

class Restrictions(private val ctx: Context, private val onChange: (Boolean) -> Unit) {
    var restricted = false; private set
    var status = "준비 중"; private set
    private var car: android.car.Car? = null
    private var manager: CarUxRestrictionsManager? = null
    private val main = Handler(Looper.getMainLooper())

    fun start() {
        if (Car.offline) { status = "꺼짐 (화면 확인용)"; return }
        try {
            val c = android.car.Car.createCar(ctx.applicationContext) ?: run { status = "차 서비스 없음"; return }
            val m = c.getCarManager(android.car.Car.CAR_UX_RESTRICTION_SERVICE) as CarUxRestrictionsManager
            restricted = m.currentCarUxRestrictions?.isRequiresDistractionOptimization == true
            m.registerListener { r: CarUxRestrictions? ->
                main.post {
                    val now = r?.isRequiresDistractionOptimization == true
                    if (now != restricted) { restricted = now; onChange(now) }
                }
            }
            car = c; manager = m; status = if (restricted) "주행 중 제한 켜짐" else "정차 (제한 없음)"
        } catch (e: Throwable) { status = "연결 실패: ${e.javaClass.simpleName}" }
    }

    fun stop() { try { manager?.unregisterListener(); car?.disconnect() } catch (_: Throwable) {} }
}
