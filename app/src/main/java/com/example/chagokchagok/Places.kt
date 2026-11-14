// 차곡車곡 – 위치
// Pleos FusedLocation SDK로 차의 위치를 받는다. SDK가 없거나 권한이 없으면 내비가 알려 주는 위치를 쓴다.
// 위치는 이 차 안에만 저장하고, 건네줄 때는 한마디를 남긴 자리와 목적지만 함께 간다.
package com.example.chagokchagok

import android.content.Context
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition

class Places(private val ctx: Context, private val onMove: (lat: Double, lng: Double, heading: Float?) -> Unit) {
    companion object {
        const val PERMISSION = "pleos.car.permission.FUSED_LOCATION"

        /** 두 좌표 사이 거리(m) */
        fun meters(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
            val r = 6371000.0
            val p1 = Math.toRadians(aLat); val p2 = Math.toRadians(bLat)
            val dp = p2 - p1; val dl = Math.toRadians(bLng - aLng)
            val h = Math.sin(dp / 2) * Math.sin(dp / 2) + Math.cos(p1) * Math.cos(p2) * Math.sin(dl / 2) * Math.sin(dl / 2)
            return 2 * r * Math.asin(Math.sqrt(h))
        }
        /** a에서 b를 향하는 방위(도, 북=0) */
        fun bearing(aLat: Double, aLng: Double, bLat: Double, bLng: Double): Double {
            val p1 = Math.toRadians(aLat); val p2 = Math.toRadians(bLat); val dl = Math.toRadians(bLng - aLng)
            val y = Math.sin(dl) * Math.cos(p2); val x = Math.cos(p1) * Math.sin(p2) - Math.sin(p1) * Math.cos(p2) * Math.cos(dl)
            return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
        }
    }

    var status = "준비 중"; private set
    var source = ""; private set
    var lat: Double? = null; private set
    var lng: Double? = null; private set
    var heading: Float? = null; private set
    private var fused: FusedLocation? = null
    private val main = Handler(Looper.getMainLooper())

    fun permitted() = ctx.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    private val listener = object : FusedLocationListener {
        override fun onChanged(location: LocationData?) {
            location ?: return
            main.post { update(location.latitude, location.longitude, location.heading, "위치 SDK") }
        }
        override fun onError(exception: Exception) { main.post { status = "위치 오류: ${exception.message}" } }
    }

    fun start() {
        if (Car.offline) { status = "꺼짐 (화면 확인용)"; return }
        try {
            val f = FusedLocation(ctx.applicationContext)
            if (!f.isSupported()) { status = "위치 SDK를 지원하지 않는 환경 (내비 위치 사용)"; return }
            f.initialize(); fused = f
            if (permitted()) listen() else status = "위치 권한이 필요해요 (지금은 내비 위치 사용)"
        } catch (e: Throwable) { status = "위치 SDK 연결 실패 (내비 위치 사용)" }
    }

    /** 권한을 받은 뒤 다시 부른다 */
    fun listen() {
        val f = fused ?: return
        try { f.registerFusedLocationCallback(listener); f.getLastKnownLocation(listener); status = "켜짐" } catch (e: Throwable) { status = "위치 받기 실패: ${e.message}" }
    }

    /** 위치 SDK가 조용하면 내비가 알려 준 위치를 쓴다 */
    fun fromNavi(lat: Double, lng: Double) { if (source != "위치 SDK" || stale()) update(lat, lng, null, "내비") }

    private var lastAt = 0L
    private fun stale() = System.currentTimeMillis() - lastAt > 5000

    private fun update(lat: Double, lng: Double, heading: Float?, from: String) {
        this.lat = lat; this.lng = lng; if (heading != null && heading > 0f) this.heading = heading
        source = from; lastAt = System.currentTimeMillis()
        if (status.startsWith("준비") || status.startsWith("위치 권한")) status = "켜짐"
        onMove(lat, lng, this.heading)
    }

    // ── 근접 알림 (FusedLocation SDK 공식 API) ──
    // 한마디를 남긴 자리마다 반경 150m ENTER 알림을 건다. 방향 조건은 SDK에 없어 들어온 뒤 앱이 확인한다.
    private val alertIds = mutableMapOf<String, Int>()
    var alerts = 0; private set

    /** 자리마다 근접 알림을 건다. SDK를 쓸 수 없으면 false (그때는 앱이 거리로 판단한다). */
    /** points: (한마디 번호, 위도, 경도) */
    fun watch(points: List<Triple<Int, Double, Double>>, onEnter: (Int) -> Unit): Boolean {
        val f = fused ?: return false
        if (!permitted() || points.isEmpty()) return false
        clearWatch()
        return try {
            points.forEach { (i, la, ln) ->
                f.addProximityAlert(ProximityAlertRequest(la, ln, 150f, setOf(ProximityTransition.ENTER)), object : ProximityAlertListener {
                    override fun onRegistered(alertId: String) { main.post { alertIds[alertId] = i; alerts = alertIds.size } }
                    override fun onTransition(event: ProximityAlertEvent) { if (event.transition == ProximityTransition.ENTER) main.post { alertIds[event.alertId]?.let(onEnter) } }
                    override fun onError(exception: Exception) { main.post { status = "근접 알림 오류: ${exception.message}" } }
                })
            }
            true
        } catch (e: Throwable) { status = "근접 알림을 걸지 못함: ${e.javaClass.simpleName}"; false }
    }

    fun clearWatch() {
        val f = fused
        alertIds.keys.forEach { id -> try { f?.removeProximityAlert(id) } catch (_: Throwable) {} }
        alertIds.clear(); alerts = 0
    }

    /** 위치 권한이 막혔을 때 Pleos가 주는 안내 팝업 (문서 권장) */
    fun showBlocked() { try { fused?.showLocationAccessBlockedPopup() } catch (_: Throwable) {} }

    fun stop() {
        clearWatch()
        try { fused?.unregisterFusedLocationCallback(listener); fused?.release() } catch (_: Throwable) {}
    }
}
