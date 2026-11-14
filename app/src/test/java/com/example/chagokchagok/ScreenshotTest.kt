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
    /** 에뮬레이터에서 실제로 본 큰 창 (1682×1210dp) */
    @Test @Config(qualifiers = "w1682dp-h1210dp-land-mdpi") fun big() = shoot("big", short = true)
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
            idle(); a.sheets.clearToast()
            val v: View = a.window.decorView
            val w = ctx.resources.displayMetrics.widthPixels; val h = ctx.resources.displayMetrics.heightPixels
            v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY))
            v.layout(0, 0, w, h)
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(dir, "${tag}_$name.png").outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
        fun scrollDown(px: Int) {
            fun find(v: View): android.widget.ScrollView? = if (v is android.widget.ScrollView) v else (v as? android.view.ViewGroup)?.let { g -> (0 until g.childCount).firstNotNullOfOrNull { find(g.getChildAt(it)) } }
            idle(); find(a.window.decorView)?.scrollBy(0, px)
        }
        Onboard.step = 0; a.go(MainActivity.ONBOARD); snap("00a_onboard_welcome")
        Onboard.step = 1; a.go(MainActivity.ONBOARD); snap("00b_onboard_name")
        Onboard.step = 2; a.go(MainActivity.ONBOARD); snap("00c_onboard_kind")
        Onboard.step = 3; a.go(MainActivity.ONBOARD); snap("00d_onboard_settings")
        val store = a.store
        store.setDrivers(listOf("나", "아빠")); store.driver = "나"; store.setLocationConsent("나", true); store.setType("나", "초보"); store.setType("아빠", "익숙"); store.setRelation("아빠", "가족"); store.onboarded = true
        store.setType("나", "초보")
        a.go(MainActivity.TODAY); snap("01_today_empty")
        store.addSampleGuide(); store.addSampleFriend()
        val keep = store.guides(); keep.forEach { store.removeGuide(it.id) }
        // 지난 주행 몇 번 (분석 차트용)
        repeat(4) { i ->
            val evs = listOf(Ev(.2f, Evt.SIGNAL, "RIGHT"), Ev(.3f, Evt.BRAKE, "on"), Ev(.5f, Evt.LIGHT, "on"), Ev(.55f, Evt.WIPER, "on"), Ev(.7f, Evt.WIPER, "off"), Ev(.8f, Evt.SIGNAL, "LEFT"), Ev(.85f, Evt.BRAKE, "on"))
            val dr = Drive("나", System.currentTimeMillis() - (6 - i) * 86400000L, 3f + i * 2.2f, 12 + i * 5, 900, evs, 2 + i, 9 - i, i == 2, i == 3, 5 - i, 0, 12 - i * 2, turns = 4, turnsSignaled = 1 + i, passenger = i == 1,
                place = if (i % 2 == 1) listOf("동네 마트", "도서관")[i / 2] else null, placeLat = if (i % 2 == 1) 37.49 else null, placeLng = if (i % 2 == 1) 127.03 else null)
            store.addDiary(Writer.write(dr, store.diariesOf("나"), if (i % 2 == 0) store.guide("sample") else null, 1))
        }
        a.go(MainActivity.TODAY); snap("01b_today_solo")
        keep.forEach { store.addGuide(it) }
        a.go(MainActivity.TODAY); snap("02_today")
        if (!short) { a.prepDone.clear(); a.go(MainActivity.PREP); a.onPrep(Evt.SIGNAL, "LEFT"); a.onPrep(Evt.SIGNAL, "RIGHT"); a.onPrep(Evt.WIPER, "on"); snap("03_prep"); store.carModel = "더 뉴 그랜저 (2026)"; a.prepTab = 1; a.onPrep("view", "gear"); snap("03b_prep_gear"); a.prepTab = 2; a.render(); snap("03c_prep_safety"); a.prepTab = 0; store.carModel = null }
        a.depart(store.guide("sample")!!); snap("04_ready")
        a.trip.start(); a.onCarEvent(Evt.SIGNAL, "RIGHT"); a.car.setTurn("TURN_RIGHT", 80, ""); a.onCarEvent(Evt.NAVI, "turn")
        a.trip.onLocation(SampleRoute.points[16].first, SampleRoute.points[16].second, null)
        snap("05_drive")
        a.onCarEvent(Evt.WIPER, "on"); a.onCarEvent(Evt.BRAKE, "on"); a.onCarEvent(Evt.SEAT, "on")
        a.onCarEvent(Evt.GEAR, "P")
        snap("06_diary")
        if (!short) {
            // 같은 곳에 다시 간 날: 지난번과 비교 + 한마디 후보
            val again = Drive("나", System.currentTimeMillis(), 4.1f, 13, 820, listOf(Ev(.2f, Evt.SIGNAL, "RIGHT"), Ev(.4f, Evt.BRAKE, "on"), Ev(.42f, Evt.BRAKE, "on"), Ev(.45f, Evt.BRAKE, "on"), Ev(.6f, Evt.CALM, "on"), Ev(.66f, Evt.LANE, LaneMove("LEFT", 12f, true, true, false, false).encode()), Ev(.8f, Evt.SIGNAL, "LEFT")),
                2, 5, false, false, 1, 0, 6, turns = 4, turnsSignaled = 3, calm = 1, place = "동네 마트", placeLat = 37.49, placeLng = 127.03, economy = 14.8f,
                path = SampleRoute.points.mapIndexed { i, q -> Pt(i / (SampleRoute.points.size - 1f), q.first, q.second) })
            val dd = Writer.write(again, store.diariesOf("나"), null, 0); store.addDiary(dd)
            a.go(MainActivity.DIARY, dd.id); snap("06b_diary_compare")
            scrollDown(620); snap("06b2_diary_lower"); scrollDown(700); snap("06b3_diary_lowest")
            a.noteToSelf(dd); snap("06c_tip_ideas"); a.onBackPressed()
            a.go(MainActivity.ROAD, dd.id); snap("06e_road")
            scrollDown(600); snap("06e2_road_lower")
            a.go(MainActivity.TODAY); snap("02b_today_review")
        }
        if (!short) {
            val last = store.diariesOf("나").last()
            a.giveTitle = ""; a.giveKind = "길"; a.go(MainActivity.GIVE, last.id); snap("07_give")
            a.go(MainActivity.JOURNAL); snap("08_journal")
            a.go(MainActivity.ANALYSIS); snap("09_analysis")
            a.go(MainActivity.TOGETHER); snap("10_together")
            store.demoOn = true; a.go(MainActivity.TOGETHER); snap("15_social_demo")
            a.convoyWith(Demo.friends[0], store.guide("sample")); a.trip.start(); snap("16_convoy_drive"); a.onCarEvent(Evt.GEAR, "P"); store.demoOn = false
            a.roadsTab = 2; a.go(MainActivity.ROADS); snap("11_roads")
            a.go(MainActivity.SETTINGS); snap("12_settings")
            scrollDown(600); snap("12b_settings_lower"); a.go(MainActivity.DEMO); snap("12c_demo")
            a.go(MainActivity.LEARN, "law"); snap("13_learn"); a.go(MainActivity.LEARN, "tips"); snap("13b_tips")
            listOf(Evt.GEAR to "D", Evt.SIGNAL to "LEFT", Evt.BRAKE to "on", Evt.WIPER to "on", Evt.LIGHT to "on", "location" to "37.4900, 127.0300").forEach { (t, v) -> Telemetry.hit(t, v) }
            a.go(MainActivity.CHECK); snap("14_check")

            // 시연: 회사 가는 길, 네 번째 출근 (예시 3개 + 모의 주행)
            DemoCommute.start(a); a.sheets.closeAll(); snap("20_demo_ready")
            a.trip.startSim()
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(46)); snap("21_demo_drive")
            shadowOf(Looper.getMainLooper()).idleFor(java.time.Duration.ofSeconds(50))
            a.onCarEvent(Evt.GEAR, "P"); a.sheets.closeAll(); snap("22_demo_arrival")
            val demoDiary = store.diariesOf("나").last { it.drive.place == DemoCommute.PLACE && it.drive.sim }
            a.go(MainActivity.ROAD, demoDiary.id); snap("23_demo_road")
            val dj = Growth.judge(store.diariesOf("나"), demoDiary)
            org.junit.Assert.assertEquals(Verdict.UP, dj?.verdict); org.junit.Assert.assertEquals(1, demoDiary.drive.lanes().size); org.junit.Assert.assertTrue(store.heardTips(demoDiary.id).isNotEmpty()); org.junit.Assert.assertTrue(demoDiary.drive.sim)
            DemoCommute.clear(a)
        } else { a.go(MainActivity.JOURNAL); snap("08_journal") }
        ctrl.pause().stop().destroy()
    }
}
