// 차곡車곡 – 시작 모션
// 천천히, 끊기지 않게: 동작이 서로 겹치며 이어지고, 튕기지 않고 부드럽게 감속한다 (prompt-motion.com의 로고 등장 사례들을 참고).
// 세 줄이 위에서 미끄러져 내려와 차곡차곡 쌓이고 → 가운데 줄이 상자로 벌어지고 → 세로 획이 위에서 아래로 그려지며 가운데를 꿰뚫어 車이 된다.
// 이어서 車이 왼쪽 아래 칸으로 옮겨 가고 차·곡·곡이 차례로 떠올라 정사각형 로고가 된다. 화면 전체는 내내 아주 천천히 다가온다. 누르면 건너뛴다.
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
        const val DURATION = 4000L
        val ORANGE = Color.parseColor("#EB6834")
        val INK = Color.parseColor("#1D1D1F")
        val BG = Color.parseColor("#0E0E10")
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
        val sq = cubicOut(seg(.67f, .85f))
        if (sq > 0f) {
            p.shader = null; p.style = Paint.Style.FILL; p.color = INK; p.alpha = (255 * sq).toInt()
            val sc = .94f + .06f * sq
            c.save(); c.scale(sc, sc, ox + S / 2, oy + S / 2)
            c.drawRoundRect(ox, oy, ox + S, oy + S, S * .22f, S * .22f, p)
            c.restore()
        }

        // 車: 처음엔 가운데에 크게, 모일 때 왼쪽 아래 칸으로
        val move = quintInOut(seg(.64f, .86f))
        val bigBox = S * .92f; val cellBox = S * .34f
        val box = bigBox + (cellBox - bigBox) * move
        val cx = w / 2 + (ox + S * .30f - w / 2) * move
        val cy = (oy + S / 2) + (oy + S * .70f - (oy + S / 2)) * move
        drawCha(c, cx - box / 2, cy - box / 2, box)

        // 차 · 곡 · 곡 이 차례로 떠오른다 (겹치며)
        p.shader = null; p.style = Paint.Style.FILL; p.typeface = face; p.textAlign = Paint.Align.CENTER; p.textSize = S * .34f; p.color = Color.WHITE
        listOf(Triple("차", .30f, .42f), Triple("곡", .70f, .42f), Triple("곡", .70f, .82f)).forEachIndexed { i, (ch, u, v) ->
            val k = cubicOut(seg(.70f + i * .04f, .86f + i * .04f)); if (k <= 0f) return@forEachIndexed
            p.alpha = (255 * k).toInt()
            c.drawText(ch, ox + S * u, oy + S * v + (1 - k) * S * .07f, p)
        }
        // 한 줄 소개
        val tag = cubicOut(seg(.82f, .94f))
        if (tag > 0f) {
            p.textSize = S * .085f; p.color = Color.parseColor("#C7C7CC"); p.alpha = (255 * tag).toInt()
            c.drawText("차로 쓰는 성장일기", w / 2, oy + S + S * .22f + (1 - tag) * S * .03f, p)
        }
        c.restore()
    }

    /** 車을 획으로 그린다. (x, y)에서 한 변이 s 인 칸 안에 */
    private fun drawCha(c: Canvas, x: Float, y: Float, s: Float) {
        fun X(u: Float) = x + s * u
        fun Y(v: Float) = y + s * v
        val th = s * .085f; val r = th / 2
        p.shader = null; p.style = Paint.Style.FILL; p.color = ORANGE

        /** 줄 하나: 위에서 미끄러져 내려오며 길이도 함께 늘어난다. 튕기지 않고 자리에서 멈춘다 */
        fun bar(start: Float, x0: Float, x1: Float, yTarget: Float): Float? {
            val k = seg(start, start + .20f); if (k <= 0f) return null
            val e = expoOut(k)
            val yy = Y(yTarget) - (1 - e) * s * .55f
            val cxm = (X(x0) + X(x1)) / 2; val half = (X(x1) - X(x0)) / 2 * (.55f + .45f * e)
            p.alpha = (255 * cubicOut(minOf(1f, k * 2.2f))).toInt()
            c.drawRoundRect(RectF(cxm - half, yy - r, cxm + half, yy + r), r, r, p)
            return yy
        }

        bar(.00f, .06f, .94f, .80f)                         // 아래 긴 줄
        val midY = bar(.09f, .20f, .80f, .47f)              // 가운데 줄
        bar(.18f, .24f, .76f, .15f)                         // 위 짧은 줄

        // 가운데 줄이 위아래로 부드럽게 벌어져 상자(曰)가 된다
        val open = cubicInOut(seg(.32f, .50f))
        if (midY != null && open > 0f) {
            p.alpha = 255
            val half = s * .16f * open
            c.drawRoundRect(RectF(X(.20f), midY - half - r, X(.80f), midY - half + r), r, r, p)
            c.drawRoundRect(RectF(X(.20f), midY + half - r, X(.80f), midY + half + r), r, r, p)
            c.drawRoundRect(RectF(X(.20f) - r, midY - half - r, X(.20f) + r, midY + half + r), r, r, p)
            c.drawRoundRect(RectF(X(.80f) - r, midY - half - r, X(.80f) + r, midY + half + r), r, r, p)
        }

        // 세로 획: 위에서 아래로 그려지며 가운데를 꿰뚫는다. 끝에 은은한 빛이 따라간다
        val draw = cubicInOut(seg(.44f, .64f))
        if (draw > 0f) {
            val top = Y(.02f); val bottom = top + (Y(.98f) - top) * draw
            p.alpha = 255
            c.drawRoundRect(RectF(X(.5f) - r, top, X(.5f) + r, maxOf(bottom, top + th)), r, r, p)
            val glow = (if (draw < 1f) 1f else 1 - seg(.64f, .69f)) * minOf(1f, draw * 4f)
            if (glow > 0f) {
                val rad = th * 3.2f
                p.shader = RadialGradient(X(.5f), bottom, rad, Color.argb((150 * glow).toInt(), 255, 190, 140), Color.TRANSPARENT, Shader.TileMode.CLAMP)
                c.drawCircle(X(.5f), bottom, rad, p)
                p.shader = null
            }
        }
    }
}
