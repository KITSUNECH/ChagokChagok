// 차곡車곡 – 시작 모션
// 천천히, 끊기지 않게: 동작이 서로 겹치며 이어지고, 튕기지 않고 부드럽게 감속한다 (prompt-motion.com의 로고 등장 사례들을 참고).
// 세 줄이 위에서 미끄러져 내려와 차곡차곡 쌓인다 → 쌓인 세 줄이 로고의 왼쪽 아래 칸으로 옮겨 가고,
// 차·곡·곡이 떠오르는 것과 동시에 가운데 줄이 상자로 벌어지고 세로 획이 꿰뚫어 車이 완성된다. 마지막 글자와 車이 함께 완성된다.
// 그 끝에 흰 테두리가 가로 여덟 줄로 나뉘어 아랫줄부터 한 줄씩 빠르게 쌓이며 앱 아이콘과 같은 모습이 된다.
// 화면 전체는 내내 아주 천천히 다가온다. 눌러도 끝까지 보여 준다.
package com.example.chagokchagok

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.pow

class SplashView(ctx: Context, private val face: Typeface, private val onDone: () -> Unit) : View(ctx) {
    /** 0 → 1. 테스트에서는 직접 정해 그림을 찍는다. */
    var t = 0f
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var anim: ValueAnimator? = null
    private var finished = false

    companion object {
        const val DURATION = 4400L
        // 로고 G3: 검정 바탕, 현대 샌드 글자, 車은 Active Blue. 화면은 검정, 로고 정사각형은 살짝 떠 보이게
        val STROKE = Color.parseColor("#00AAD2")
        val INK = Color.parseColor("#0C0C0C")   // 아이콘처럼 검정에 가깝게, 흰 선이 타일을 드러낸다
        val BG = Color.parseColor("#000000")
        val SAND = Color.parseColor("#E4DCD3")
        val SKY = Color.parseColor("#9C958D")
    }

    fun play() {
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION; interpolator = LinearInterpolator()
            addUpdateListener { t = it.animatedValue as Float; alpha = 1f - ((t - .95f) / .05f).coerceIn(0f, 1f) }
            addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(a: android.animation.Animator) { finish() } })
            start()
        }
    }

    private fun finish() { if (finished) return; finished = true; anim?.cancel(); onDone() }

    /** 눌러도 건너뛰지 않는다: 모션은 끝까지 보여 주고, 아래 화면으로 터치가 새지 않게만 막는다. */
    override fun onTouchEvent(e: MotionEvent): Boolean = true

    // ── 시간과 이징 (튕김 없이 부드럽게) ──
    private fun seg(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    private fun expoOut(x: Float) = if (x >= 1f) 1f else 1 - 2f.pow(-10 * x)
    private fun cubicOut(x: Float) = 1 - (1 - x).pow(3)
    private fun cubicInOut(x: Float) = if (x < .5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    private fun quintInOut(x: Float) = if (x < .5f) 16 * x.pow(5) else 1 - (-2 * x + 2).pow(5) / 2

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(BG)
        // 화면 전체가 내내 아주 천천히 다가온다 (정지된 순간이 없도록)
        val drift = .965f + .035f * cubicOut(t)
        c.save(); c.scale(drift, drift, w / 2, h / 2)

        val S = minOf(w, h) * .46f
        val ox = (w - S) / 2; val oy = (h - S) / 2 - S * .08f

        // 로고 바탕: 車이 옮겨 가기 시작할 때 함께 부드럽게
        val sq = cubicOut(seg(.34f, .56f))
        if (sq > 0f) {
            p.shader = null; p.style = Paint.Style.FILL; p.color = INK; p.alpha = (255 * sq).toInt()
            val sc = .94f + .06f * sq
            c.save(); c.scale(sc, sc, ox + S / 2, oy + S / 2)
            c.drawRoundRect(ox, oy, ox + S, oy + S, S * LogoLayout.TILE_R, S * LogoLayout.TILE_R, p)
            c.restore()
        }

        // 車: 처음엔 가운데에 크게, 모일 때 왼쪽 아래 칸의 "차"에 맞춘 상자로
        val move = quintInOut(seg(.32f, .56f))
        p.typeface = face; p.textSize = S * LogoLayout.TEXT
        val fin = ChaGlyph.box(p, ox + S * LogoLayout.COL_L, oy + S * LogoLayout.ROW_B)
        val bigW = S * .80f; val bigH = bigW * fin[3] / fin[2]
        val bx = w / 2 - bigW / 2; val by = oy + S / 2 - bigH / 2
        drawCha(c, bx + (fin[0] - bx) * move, by + (fin[1] - by) * move, bigW + (fin[2] - bigW) * move, bigH + (fin[3] - bigH) * move)

        // 차 · 곡 · 곡 이 차례로 떠오른다 (겹치며)
        p.shader = null; p.style = Paint.Style.FILL; p.typeface = face; p.textAlign = Paint.Align.CENTER; p.textSize = S * LogoLayout.TEXT; p.color = SAND
        val L = LogoLayout
        listOf(Triple("차", L.COL_L, L.ROW_T), Triple("곡", L.COL_R, L.ROW_T), Triple("곡", L.COL_R, L.ROW_B)).forEachIndexed { i, (ch, u, v) ->
            val k = cubicOut(seg(.50f + i * .06f, .66f + i * .06f)); if (k <= 0f) return@forEachIndexed
            p.alpha = (255 * k).toInt()
            c.drawText(ch, ox + S * u, oy + S * v + (1 - k) * S * .07f, p)
        }
        // 흰 선: 테두리를 가로로 여덟 줄로 잘라, 아랫줄부터 한 줄씩 빠르게 떨어져 쌓인다.
        // (앞에서 車의 세 줄이 천천히 쌓였으니, 여기는 훨씬 빠르게: 한 줄에 약 0.1초, 줄 사이 0.05초)
        val rows = 8
        if (t > .70f) {
            p.shader = null; p.style = Paint.Style.STROKE; p.color = Color.WHITE; p.strokeWidth = S * LogoLayout.RING_W
            val pad = S * .05f
            for (i in 0 until rows) {
                val k = seg(.70f + i * .012f, .724f + i * .012f); if (k <= 0f) continue
                val e = cubicOut(k)
                val top = oy + S * (1f - (i + 1f) / rows); val bottom = oy + S * (1f - i.toFloat() / rows)
                c.save()
                c.translate(0f, -(1f - e) * S * .09f)   // 위에서 떨어져 제자리에
                c.clipRect(ox - pad, if (i == rows - 1) top - pad else top, ox + S + pad, if (i == 0) bottom + pad else bottom)
                p.alpha = (255 * minOf(1f, k * 2f)).toInt()
                c.drawPath(LogoLayout.ring(ox, oy, S), p)
                c.restore()
            }
            p.style = Paint.Style.FILL; p.alpha = 255
        }
        // 한 줄 소개
        val tag = cubicOut(seg(.84f, .93f))
        if (tag > 0f) {
            p.textSize = S * .085f; p.color = SKY; p.alpha = (255 * tag).toInt()
            c.drawText("차로 쓰는 성장일기", w / 2, oy + S + S * .22f + (1 - tag) * S * .03f, p)
        }
        c.restore()
    }

    /** 車을 획으로 그린다. (x, y)에서 폭 w, 높이 h 인 상자 안에. 획은 ChaGlyph 와 같고 끝이 평평하다 */
    private fun drawCha(c: Canvas, x: Float, y: Float, w: Float, h: Float) {
        p.shader = null; p.style = Paint.Style.FILL; p.color = STROKE
        fun rect(q: FloatArray, dy: Float = 0f, grow: Float = 1f) {
            val cxm = x + w * (q[0] + q[2]) / 2; val half = w * (q[2] - q[0]) / 2 * grow
            c.drawRect(cxm - half, y + h * q[1] + dy, cxm + half, y + h * q[3] + dy, p)
        }
        /** 줄 하나: 위에서 미끄러져 내려오며 길이도 함께 늘어난다 */
        fun bar(start: Float, q: FloatArray): Boolean {
            val k = seg(start, start + .20f); if (k <= 0f) return false
            val e = expoOut(k)
            p.alpha = (255 * cubicOut(minOf(1f, k * 2.2f))).toInt()
            rect(q, -(1 - e) * h * .55f, .55f + .45f * e)
            return true
        }
        bar(.00f, ChaGlyph.BOTTOM)
        val midShown = bar(.08f, ChaGlyph.MID)
        bar(.15f, ChaGlyph.TOP)
        p.alpha = 255

        // 가운데 줄이 위아래로 벌어져 상자(曰)가 된다: 위·아래 테두리가 가운데에서 제자리로, 양옆 기둥이 자란다
        val open = cubicInOut(seg(.52f, .68f))
        if (midShown && open > 0f) {
            val midTop = ChaGlyph.MID[1]; val midBot = ChaGlyph.MID[3]
            fun lerp(a: Float, b: Float) = a + (b - a) * open
            val t1 = floatArrayOf(ChaGlyph.BOX_T[0], lerp(midTop, ChaGlyph.BOX_T[1]), ChaGlyph.BOX_T[2], lerp(midBot, ChaGlyph.BOX_T[3]))
            val b1 = floatArrayOf(ChaGlyph.BOX_B[0], lerp(midTop, ChaGlyph.BOX_B[1]), ChaGlyph.BOX_B[2], lerp(midBot, ChaGlyph.BOX_B[3]))
            rect(t1); rect(b1)
            val sideTop = lerp(midTop, ChaGlyph.SIDE_L[1]); val sideBot = lerp(midBot, ChaGlyph.SIDE_L[3])
            rect(floatArrayOf(ChaGlyph.SIDE_L[0], sideTop, ChaGlyph.SIDE_L[2], sideBot))
            rect(floatArrayOf(ChaGlyph.SIDE_R[0], sideTop, ChaGlyph.SIDE_R[2], sideBot))
        }

        // 세로 획: 위에서 아래로 그려지며 가운데를 꿰뚫는다. 끝에 은은한 빛
        val draw = cubicInOut(seg(.62f, .78f))
        if (draw > 0f) {
            val q = ChaGlyph.VERT
            val top = y + h * q[1]; val bottom = top + (y + h * q[3] - top) * draw
            c.drawRect(x + w * q[0], top, x + w * q[2], maxOf(bottom, top + w * .05f), p)
            val glow = (if (draw < 1f) 1f else 1 - seg(.78f, .86f)) * minOf(1f, draw * 4f)
            if (glow > 0f) {
                val rad = w * .28f
                p.shader = RadialGradient(x + w * (q[0] + q[2]) / 2, bottom, rad, Color.argb((150 * glow).toInt(), 170, 230, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                c.drawCircle(x + w * (q[0] + q[2]) / 2, bottom, rad, p)
                p.shader = null
            }
        }
    }
}
