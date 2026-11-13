package com.example.chagokchagok

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

/** 시작 모션을 프레임별 PNG로 찍는다 (build/shots/splash_000.png …). 움직임 확인용. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w960dp-h540dp-land-mdpi")
class SplashTest {
    @Test fun frames() {
        val ctx = RuntimeEnvironment.getApplication()
        val face = ctx.resources.getFont(R.font.pretendard_bold)
        val v = SplashView(ctx, face) {}
        val w = 960; val h = 540
        v.measure(View.MeasureSpec.makeMeasureSpec(w, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(h, View.MeasureSpec.EXACTLY)); v.layout(0, 0, w, h)
        val dir = File(System.getProperty("shots.dir") ?: "build/shots").apply { mkdirs() }
        val n = 66   // 2.6초를 25fps로
        for (i in 0..n) {
            v.t = i / n.toFloat()
            val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            v.draw(Canvas(bmp))
            File(dir, "splash_%03d.png".format(i)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
