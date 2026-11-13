// 차곡車곡 – 시작 모션
// 천천히, 끊기지 않게: 동작이 서로 겹치며 이어지고, 튕기지 않고 부드럽게 감속한다 (prompt-motion.com의 로고 등장 사례들을 참고).
// 세 줄을 상자처럼 하나씩 내려놓아 차곡차곡 쌓는다 (기울여 들고 와 수평으로, 아주 느리게 닿고 살짝 눌림) → 쌓인 세 줄이 로고의 왼쪽 아래 칸으로 옮겨 가고,
// 차·곡·곡이 떠오르는 것과 동시에 가운데 줄이 상자로 벌어지고 세로 획이 꿰뚫어 車이 완성된다. 마지막 글자와 車이 함께 완성된다. 화면 전체는 내내 아주 천천히 다가온다. 누르면 건너뛴다.
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
import kotlin.math.sin

class SplashView(ctx: Context, private val face: Typeface, private val onDone: () -> Unit) : View(ctx) {
    /** 0 → 1. 테스트에서는 직접 정해 그림을 찍는다. */
    var t = 0f
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var anim: ValueAnimator? = null
    private var finished = false

    companion object {
        const val DURATION = 4000L
        // 로고 G3: 검정 바탕, 현대 샌드 글자, 車은 Active Blue. 화면은 검정, 로고 정사각형은 살짝 떠 보이게
        val STROKE = Color.parseColor("#00AAD2")
        val INK = Color.parseColor("#161616")
        val BG = Color.parseColor("#000000")
        val SAND = Color.parseColor("#E4DCD3")
        val SKY = Color.parseColor("#9C958D")
    }

    fun play() {
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION; interpolator = LinearInterpolator()
            addUpdateListener { t = it.animatedValue as Float; alpha = 1f - ((t - .93f) / .07f).coerceIn(0f, 1f) }
            addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(a: android.animation.Animator) { finish() } })
            start()
        }
    }

    private fun finish() { if (finished) return; finished = true; anim?.cancel(); onDone() }

    override fun onTouchEvent(e: MotionEvent): Boolean { if (e.action == MotionEvent.ACTION_UP) finish(); return true }

    // ── 시간과 이징 (튕김 없이 부드럽게) ──
    private fun seg(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    private fun expoOut(x: Float) = if (x >= 1f) 1f else 1 - 2f.pow(-10 * x)
    private fun cubicOut(x: Float) = 1 - (1 - x).pow(3)
    private fun cubicInOut(x: Float) = if (x < .5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
    private fun quintOut(x: Float) = 1 - (1 - x).pow(5)
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
            c.drawRoundRect(ox, oy, ox + S, oy + S, S * .22f, S * .22f, p)
            c.restore()
        }

        // 車: 처음엔 가운데에 크게, 모일 때 왼쪽 아래 칸의 "차"에 맞춘 상자로
        val move = quintInOut(seg(.32f, .56f))
        p.typeface = face; p.textSize = S * .34f
        val fin = ChaGlyph.box(p, ox + S * .30f, oy + S * .82f)
        val bigW = S * .80f; val bigH = bigW * fin[3] / fin[2]
        val bx = w / 2 - bigW / 2; val by = oy + S / 2 - bigH / 2
        drawCha(c, bx + (fin[0] - bx) * move, by + (fin[1] - by) * move, bigW + (fin[2] - bigW) * move, bigH + (fin[3] - bigH) * move)

        // 차 · 곡 · 곡 이 차례로 떠오른다 (겹치며)
        p.shader = null; p.style = Paint.Style.FILL; p.typeface = face; p.textAlign = Paint.Align.CENTER; p.textSize = S * .34f; p.color = SAND
        listOf(Triple("차", .30f, .42f), Triple("곡", .70f, .42f), Triple("곡", .70f, .82f)).forEachIndexed { i, (ch, u, v) ->
            val k = cubicOut(seg(.50f + i * .06f, .66f + i * .06f)); if (k <= 0f) return@forEachIndexed
            p.alpha = (255 * k).toInt()
            c.drawText(ch, ox + S * u, oy + S * v + (1 - k) * S * .07f, p)
        }
        // 한 줄 소개
        val tag = cubicOut(seg(.80f, .92f))
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
        /**
         * 줄 하나를 상자처럼 내려놓는다: 살짝 기울어진 채 내려오다 바닥 직전에 수평이 되고,
         * 아주 느리게 닿은 뒤 조금 눌렸다 자리를 잡는다. 놓일 자리에는 옅은 빛이 먼저 비친다.
         */
        val DUR = .22f; val TOUCH = .84f
        fun touch(start: Float): Float {           // 닿은 뒤 눌림 정도 (0 → 1 → 0)
            val k = seg(start, start + DUR); if (k < TOUCH) return 0f
            return sin(Math.PI.toFloat() * ((k - TOUCH) / (1 - TOUCH)))
        }
        fun place(start: Float, q: FloatArray, tilt: Float, press: Float): Boolean {
            val k = seg(start, start + DUR); if (k <= 0f) return false
            val cxm = x + w * (q[0] + q[2]) / 2; val half = w * (q[2] - q[0]) / 2
            val bottom = y + h * q[3]; val thick = h * (q[3] - q[1])
            // 놓일 자리의 옅은 빛 (닿기 전까지 점점 진해졌다가 닿으면 사라짐)
            val near = (k / TOUCH).coerceIn(0f, 1f); val after = ((k - TOUCH) / (1 - TOUCH)).coerceIn(0f, 1f)
            val glowA = near * near * (1 - after)
            if (glowA > 0f) {
                p.shader = RadialGradient(cxm, bottom + thick * .2f, half * 1.1f, Color.argb((70 * glowA).toInt(), 0, 170, 210), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                c.save(); c.scale(1f, .22f, cxm, bottom + thick * .2f); c.drawCircle(cxm, bottom + thick * .2f, half * 1.1f, p); c.restore()
                p.shader = null
            }
            // 내려오기: 바닥 가까이서 아주 느려진다
            val fall = cubicOut(quintOut(near))
            val dy = -(1 - fall) * h * .5f + press
            // 기울기: 닿기 직전 수평으로
            val ang = tilt * (1 - cubicOut(near))
            // 닿는 순간 살짝 눌림 (바닥에 붙은 채)
            val sq = if (k >= TOUCH) sin(Math.PI.toFloat() * after) else 0f
            val sy = 1 - .10f * sq; val sx = 1 + .025f * sq
            p.alpha = (255 * cubicOut(minOf(1f, k * 2.5f))).toInt()
            c.save()
            c.translate(cxm, bottom + dy); c.rotate(ang); c.scale(sx, sy)
            c.drawRect(-half, -thick, half, 0f, p)
            c.restore()
            return true
        }
        val tMid = .08f; val tTop = .16f
        place(.00f, ChaGlyph.BOTTOM, -4f, h * .012f * (touch(tMid) + touch(tTop)))
        val midShown = place(tMid, ChaGlyph.MID, 4f, h * .012f * touch(tTop))
        place(tTop, ChaGlyph.TOP, -3f, 0f)
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
