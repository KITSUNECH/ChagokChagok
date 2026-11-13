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

/**
 * 화면을 PNG로 찍는다. 에뮬레이터 없이 UI를 확인하려고 만든 테스트다.
 * ./gradlew testDebugUnitTest --tests '*ScreenshotTest*' 후 build/shots 에 이미지가 생긴다.
 * 차량 SDK는 여기서 연결되지 않으므로 차량 값은 비어 있다 (예시 주행 기록은 넣는다).
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTest {

    /** Pleos 화면 전체 (가로로 긴 태블릿) */
    @Test @Config(qualifiers = "w1280dp-h720dp-land-mdpi") fun wide() = shoot("wide")
    /** 밤 */
    @Test @Config(qualifiers = "w1280dp-h720dp-land-night-mdpi") fun night() = shoot("night", short = true)
    /** 내비 옆 앱 영역처럼 좁고 긴 창 */
    @Test @Config(qualifiers = "w420dp-h700dp-port-mdpi") fun panel() = shoot("panel")

    private fun shoot(tag: String, short: Boolean = false) {
        Car.offline = true
        val ctx = RuntimeEnvironment.getApplication()
        Store(ctx).apply { clearAll() }
        val ctrl = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = ctrl.get()
        val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
        fun idle() = shadowOf(Looper.getMainLooper()).idle()
        fun snap(name: String) {
            idle()
            val v: View = a.window.decorView
            val w = ctx.resources.displayMetrics.widthPixels; val h = ctx.resources.displayMetrics.heightPixels
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, w, h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(dir, "${tag}_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        snap("00_onboard")
        val store = a.store
        store.setDrivers(listOf("나", "아빠")); store.driver = "나"; store.setType("나", "초보"); store.setType("아빠", "익숙"); store.setRelation("아빠", "가족"); store.onboarded = true
        a.go(MainActivity.TODAY); snap("01_today_empty")
        store.addSampleGuide(); store.addSampleFriend()
        // 지난 주행 몇 번 (분석 차트용)
        repeat(4) { i ->
            val evs = listOf(Ev(.2f, Evt.SIGNAL, "RIGHT"), Ev(.3f, Evt.BRAKE, "on"), Ev(.5f, Evt.LIGHT, "on"), Ev(.55f, Evt.WIPER, "on"), Ev(.7f, Evt.WIPER, "off"), Ev(.8f, Evt.SIGNAL, "LEFT"), Ev(.85f, Evt.BRAKE, "on"))
            val dr = Drive("나", System.currentTimeMillis() - (6 - i) * 86400000L, 3f + i * 2.2f, 12 + i * 5, 900, evs, 2 + i, 9 - i, i == 2, i == 3, 5 - i, 0, 12 - i * 2, turns = 4, turnsSignaled = 1 + i, passenger = i == 1)
            store.addDiary(Writer.write(dr, store.diariesOf("나"), if (i % 2 == 0) store.guide("sample") else null, 1))
        }
        a.go(MainActivity.TODAY); snap("02_today")
        if (!short) { a.prepDone.clear(); a.go(MainActivity.PREP); a.onPrep(Evt.SIGNAL, "LEFT"); a.onPrep(Evt.SIGNAL, "RIGHT"); a.onPrep(Evt.WIPER, "on"); snap("03_prep") }
        a.depart(store.guide("sample")!!); snap("04_ready")
        a.trip.start(); a.onCarEvent(Evt.SIGNAL, "RIGHT"); a.car.setTurn("TURN_RIGHT", 80, ""); a.onCarEvent(Evt.NAVI, "turn")
        a.trip.onLocation(SampleRoute.points[16].first, SampleRoute.points[16].second, null)
        snap("05_drive")
        a.onCarEvent(Evt.WIPER, "on"); a.onCarEvent(Evt.BRAKE, "on"); a.onCarEvent(Evt.SEAT, "on")
        a.onCarEvent(Evt.GEAR, "P")
        snap("06_diary")
        if (!short) {
            val last = store.diariesOf("나").last()
            a.giveTitle = ""; a.giveKind = "길"; a.go(MainActivity.GIVE, last.id); snap("07_give")
            a.journalTab = 0; a.go(MainActivity.JOURNAL); snap("08_journal")
            a.journalTab = 1; a.go(MainActivity.JOURNAL); snap("09_analysis")
            a.go(MainActivity.TOGETHER); snap("10_together")
            store.demoOn = true; a.go(MainActivity.TOGETHER); snap("15_social_demo")
            a.convoyWith(Demo.friends[0], store.guide("sample")); a.trip.start(); snap("16_convoy_drive"); a.onCarEvent(Evt.GEAR, "P"); store.demoOn = false
            a.roadsTab = 2; a.go(MainActivity.ROADS); snap("11_roads")
            a.go(MainActivity.SETTINGS); snap("12_settings")
            a.go(MainActivity.LEARN, "law"); snap("13_learn")
            a.go(MainActivity.CHECK); snap("14_check")
            a.go(MainActivity.PROPOSE); snap("17_propose")
            a.go(MainActivity.TODAY); a.stackSheet(); snap("18_stack_sheet")
        } else { a.journalTab = 1; a.go(MainActivity.JOURNAL); snap("09_analysis") }
        ctrl.pause().stop().destroy()
    }
}
