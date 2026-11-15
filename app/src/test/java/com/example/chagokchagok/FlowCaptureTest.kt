package com.example.chagokchagok

import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Looper
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.time.Duration

/**
 * 앱을 처음부터 끝까지 한 번 써 보며 화면을 차례로 찍는다 (build/shots/flow_NNN_이름.png).
 * 첫 설정 → 홈 → 출근길 시연(모의 주행) → 주차 후 일기 → 이 길의 나 → 일기장.
 * media 의 화면 흐름 영상은 이 그림들을 이어 붙여 만든다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1682dp-h1210dp-land-mdpi")
class FlowCaptureTest {
    @Test fun flow() {
        Car.offline = true
        Car.previewConnected = true   // 영상에서는 차에 연결된 모습으로
        val ctx = RuntimeEnvironment.getApplication()
        Store(ctx).apply { clearAll() }
        val ctrl = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = ctrl.get()
        val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
        val looper = shadowOf(Looper.getMainLooper())
        var n = 0
        fun snap(name: String) {
            looper.idle(); a.sheets.clearToast()
            val v: View = a.window.decorView
            val w = ctx.resources.displayMetrics.widthPixels; val h = ctx.resources.displayMetrics.heightPixels
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, w, h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(dir, "flow_%03d_%s.png".format(n++, name)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun wait(ms: Long) = looper.idleFor(Duration.ofMillis(ms))

        // 첫 설정
        // 소개 상자: 게이지가 차오르며 네 장이 넘어가는 모습을 0.2초 간격으로
        Onboard.step = 0; a.go(MainActivity.ONBOARD)
        repeat(72) { wait(200); snap("onboard") }
        Onboard.step = 1; a.go(MainActivity.ONBOARD); snap("name")
        Onboard.step = 2; a.go(MainActivity.ONBOARD); snap("kind")
        Onboard.step = 3; a.go(MainActivity.ONBOARD); snap("consent")
        val store = a.store
        store.setDrivers(listOf("김현대")); store.driver = "김현대"; store.setType("김현대", "초보"); store.setLocationConsent("김현대", true); store.onboarded = true
        Onboard.step = 0
        a.go(MainActivity.TODAY); snap("home")

        // 출근길 시연: 지난 출근 세 번(예시) + 모의 주행
        a.go(MainActivity.SETTINGS); snap("settings")
        DemoCommute.start(a); a.sheets.closeAll(); snap("ready")
        a.trip.startSim()
        repeat(24) { wait(4000); snap("drive") }
        wait(6000)
        a.onCarEvent(Evt.GEAR, "P"); a.sheets.closeAll(); snap("diary")
        val d = store.diariesOf("김현대").last { it.drive.place == DemoCommute.PLACE && it.drive.sim }
        store.setAnswer(d.id, Q_VS_LAST, "더 편했어요"); a.render(); snap("diary_answer")
        a.go(MainActivity.ROAD, d.id); snap("road")
        a.go(MainActivity.JOURNAL); snap("journal")
        a.go(MainActivity.TODAY); snap("home_after")
        DemoCommute.clear(a)
        ctrl.pause().stop().destroy()
        Car.previewConnected = false
    }
}
