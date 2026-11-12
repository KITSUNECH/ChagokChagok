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
 * 차량 SDK는 여기서 연결되지 않으므로 차량 값은 비어 있다.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class ScreenshotTest {

    @Test @Config(qualifiers = "w1280dp-h720dp-land-mdpi") fun wide() = shoot("wide")
    @Test @Config(qualifiers = "w640dp-h720dp-port-mdpi") fun half() = shoot("half")
    /** Pleos 화면의 앱 영역처럼 좁고 긴 창 */
    @Test @Config(qualifiers = "w420dp-h700dp-port-mdpi") fun panel() = shoot("panel")

    private fun shoot(tag: String) {
        Car.offline = true
        val ctx = RuntimeEnvironment.getApplication()
        Store(ctx).apply { clearAll(); setType("나", "초보"); setType("아빠", "익숙") }
        val ctrl = Robolectric.buildActivity(MainActivity::class.java).setup()
        val a = ctrl.get()
        val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
        val show = MainActivity::class.java.getDeclaredMethod("show", String::class.java).apply { isAccessible = true }
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
        fun go(screen: String, name: String = screen) { show.invoke(a, screen); snap(name) }

        go("home", "01_home_empty")
        val store = Store(ctx); store.addSampleGuide(); store.addSampleFriend()
        go("home", "02_home_guide")
        go("prep", "03_prep")
        // 건네받은 길로 출발
        val depart = MainActivity::class.java.getDeclaredMethod("depart", Guide::class.java).apply { isAccessible = true }
        depart.invoke(a, store.guide("sample")); snap("04_ready")
        a.onCarEvent(Evt.GEAR, "D"); a.onCarEvent(Evt.SIGNAL, "RIGHT"); a.onCarEvent(Evt.SIGNAL, "OFF"); a.onCarEvent(Evt.BRAKE, "on")
        a.onCarEvent(Evt.WIPER, "on"); a.onCarEvent(Evt.SEAT, "on")
        snap("05_driving")
        a.onCarEvent(Evt.GEAR, "P")
        snap("06_diary")
        go("give", "07_give")
        go("book", "08_book")
        go("feed", "09_feed")
        go("shelf", "10_shelf")
        ctrl.pause().stop().destroy()
    }
}
