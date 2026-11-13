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

/** 첫 설정 게이지 → 홈 성장 바 압축 모션을 프레임으로 그려 본다 (build/shots/squeeze_*.png) */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h720dp-land-mdpi")
class SqueezeTest {
    @Test fun frames() {
        val ctx = RuntimeEnvironment.getApplication()
        val ui = Ui(ctx)
        val dir = File("build/shots").apply { mkdirs() }
        for (level in 1..3) {
            val blocks = (0 until level).map { Block("c$it", 0, "", "", true) } + (0 until 12).map { Block("b$it", Gauge.stageOfSlot(it + 1), "", "", false) }
            val v = StackView(ctx, ui, blocks, 0, Gauge.cap(level))
            val f = StackView::class.java.getDeclaredField("sq").apply { isAccessible = true }
            for (i in 0..24) {
                f.setFloat(v, i / 24f)
                v.measure(View.MeasureSpec.makeMeasureSpec(56, View.MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(560, View.MeasureSpec.EXACTLY))
                v.layout(0, 0, 56, 560)
                val bmp = Bitmap.createBitmap(56, 560, Bitmap.Config.ARGB_8888)
                val c = Canvas(bmp); c.drawColor(Ink.surface); v.draw(c)
                File(dir, "squeeze_${level}_%02d.png".format(i)).outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
            }
        }
    }
}
