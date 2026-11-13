// 차곡車곡 – 시작 모션
// 세 줄이 위에서 떨어져 차곡차곡 쌓이고, 가운데 줄이 상자로 벌어진 뒤, 세로 획이 가운데를 꿰뚫어 車이 된다.
// 이어서 차·곡·곡이 나타나 정사각형 로고가 되고, 앱으로 넘어간다. 누르면 건너뛴다.
package com.example.chagokchagok

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import android.view.animation.LinearInterpolator

class SplashView(ctx: Context, private val face: Typeface, private val onDone: () -> Unit) : View(ctx) {
    /** 0 → 1. 테스트에서는 직접 정해 그림을 찍는다. */
    var t = 0f
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var anim: ValueAnimator? = null
    private var finished = false

    companion object {
        const val DURATION = 2600L
        val ORANGE = Color.parseColor("#EB6834")
        val INK = Color.parseColor("#1D1D1F")
        val BG = Color.parseColor("#0E0E10")
    }

    fun play() {
        anim = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = DURATION; interpolator = LinearInterpolator()
            addUpdateListener { t = it.animatedValue as Float; alpha = 1f - ((t - .86f) / .14f).coerceIn(0f, 1f) }
            addListener(object : android.animation.AnimatorListenerAdapter() { override fun onAnimationEnd(a: android.animation.Animator) { finish() } })
            start()
        }
    }

    private fun finish() { if (finished) return; finished = true; anim?.cancel(); onDone() }

    override fun onTouchEvent(e: MotionEvent): Boolean { if (e.action == MotionEvent.ACTION_UP) finish(); return true }

    // ── 시간 도우미 ──
    private fun seg(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    private fun easeOut(x: Float) = 1 - (1 - x) * (1 - x) * (1 - x)
    /** 살짝 튕기며 내려앉기 */
    private fun drop(x: Float): Float { val c = 1.70158f * 1.2f; val y = x - 1; return 1 + (c + 1) * y * y * y + c * y * y }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val out = seg(.86f, 1f)                                   // 끝에서 전체가 사라짐
        c.drawColor(BG)
        val S = minOf(w, h) * .46f                                  // 로고 정사각형 한 변
        val ox = (w - S) / 2; val oy = (h - S) / 2 - S * .08f
        val alphaAll = 1 - out

        // 정사각형 바탕 (로고가 모일 때 나타남)
        val sq = easeOut(seg(.58f, .74f))
        if (sq > 0) {
            p.style = Paint.Style.FILL; p.color = INK; p.alpha = (255 * sq * alphaAll).toInt()
            val g = S * (1 - (.94f + .06f * sq)) / 2
            c.drawRoundRect(ox + g, oy + g, ox + S - g, oy + S - g, S * .22f, S * .22f, p)
        }

        // 車은 처음엔 화면 가운데에 크게, 로고가 모일 때 왼쪽 아래 칸으로 옮겨 간다
        val move = easeOut(seg(.58f, .76f))
        val bigBox = S * .92f
        val cellBox = S * .34f
        val box = bigBox + (cellBox - bigBox) * move
        val cxBig = w / 2; val cyBig = oy + S / 2
        val cxCell = ox + S * .30f; val cyCell = oy + S * .70f
        val cx = cxBig + (cxCell - cxBig) * move
        val cy = cyBig + (cyCell - cyBig) * move
        drawCha(c, cx - box / 2, cy - box / 2, box, alphaAll)

        // 나머지 세 글자
        val txt = seg(.64f, .82f)
        if (txt > 0) {
            p.style = Paint.Style.FILL; p.typeface = face; p.textAlign = Paint.Align.CENTER; p.textSize = S * .34f
            p.color = Color.WHITE; p.alpha = (255 * txt * alphaAll).toInt()
            val lift = (1 - easeOut(txt)) * S * .05f
            c.drawText("차", ox + S * .30f, oy + S * .42f + lift, p)
            c.drawText("곡", ox + S * .70f, oy + S * .42f + lift, p)
            c.drawText("곡", ox + S * .70f, oy + S * .82f + lift, p)
        }
        // 한 줄 소개
        val tag = seg(.72f, .86f)
        if (tag > 0) {
            p.typeface = face; p.textSize = S * .085f; p.color = Color.parseColor("#C7C7CC"); p.alpha = (255 * tag * alphaAll).toInt()
            c.drawText("차로 쓰는 성장일기", w / 2, oy + S + S * .22f, p)
        }
    }

    /** 車을 획으로 그린다. (x, y)에서 한 변이 s 인 칸 안에 */
    private fun drawCha(c: Canvas, x: Float, y: Float, s: Float, alphaAll: Float) {
        fun X(u: Float) = x + s * u
        fun Y(v: Float) = y + s * v
        val th = s * .085f                     // 획 굵기
        p.style = Paint.Style.FILL; p.color = ORANGE

        // 1단계: 세 줄이 위에서 떨어져 쌓인다 (아래 → 가운데 → 위)
        fun bar(start: Float, x0: Float, x1: Float, yTarget: Float) {
            val k = seg(start, start + .14f); if (k <= 0f) return
            val yy = Y(yTarget) - (1 - drop(k)) * s * 1.2f
            p.alpha = (255 * minOf(1f, k * 3f) * alphaAll).toInt()
            c.drawRoundRect(RectF(X(x0), yy - th / 2, X(x1), yy + th / 2), th / 2, th / 2, p)
        }
        bar(.00f, .06f, .94f, .80f)            // 아래 긴 줄
        // 가운데 줄: 떨어진 뒤 상자(曰)로 벌어진다
        val mid = seg(.10f, .24f)
        val open = easeOut(seg(.34f, .46f))
        if (mid > 0f) {
            val yy = Y(.47f) - (1 - drop(mid)) * s * 1.2f
            p.alpha = (255 * minOf(1f, mid * 3f) * alphaAll).toInt()
            // 가운데 가로획 (상자 안의 가운데 줄로 남는다)
            c.drawRoundRect(RectF(X(.20f), yy - th / 2, X(.80f), yy + th / 2), th / 2, th / 2, p)
            if (open > 0f) {
                val half = s * .16f * open
                // 위아래 테두리
                c.drawRoundRect(RectF(X(.20f), yy - half - th / 2, X(.80f), yy - half + th / 2), th / 2, th / 2, p)
                c.drawRoundRect(RectF(X(.20f), yy + half - th / 2, X(.80f), yy + half + th / 2), th / 2, th / 2, p)
                // 양옆 테두리
                c.drawRoundRect(RectF(X(.20f) - th / 2, yy - half - th / 2, X(.20f) + th / 2, yy + half + th / 2), th / 2, th / 2, p)
                c.drawRoundRect(RectF(X(.80f) - th / 2, yy - half - th / 2, X(.80f) + th / 2, yy + half + th / 2), th / 2, th / 2, p)
            }
        }
        bar(.20f, .24f, .76f, .15f)            // 위 짧은 줄

        // 2단계: 세로 획이 위에서 아래로 가운데를 꿰뚫는다
        val pierce = easeOut(seg(.46f, .58f))
        if (pierce > 0f) {
            val top = Y(.02f); val bottom = top + (Y(.98f) - top) * pierce
            p.alpha = (255 * alphaAll).toInt()
            c.drawRoundRect(RectF(X(.5f) - th / 2, top, X(.5f) + th / 2, bottom), th / 2, th / 2, p)
            // 꿰뚫는 순간의 빛
            if (pierce < 1f) {
                p.alpha = (110 * (1 - pierce) * alphaAll).toInt()
                c.drawCircle(X(.5f), bottom, th * 1.6f, p)
            }
        }
    }
}
