// 차곡車곡 – 시작 모션 (천천히, 쫀득하게)
// 세 줄이 위에서 가속하며 떨어져 바닥에 닿는 순간 납작해졌다 튕기며 차곡차곡 쌓이고(위 줄이 떨어지면 아래 줄들도 눌렸다 올라온다),
// 가운데 줄이 탄성 있게 상자로 벌어진 뒤, 세로 획이 살짝 당겼다가 가운데를 꿰뚫는다. 꽂히는 순간 글자 전체가 눌렸다 튕긴다.
// 이어서 車이 왼쪽 아래 칸으로 옮겨 가고 차·곡·곡이 하나씩 통통 튀어나와 정사각형 로고가 된다. 누르면 건너뛴다.
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
import kotlin.math.cos
import kotlin.math.exp

class SplashView(ctx: Context, private val face: Typeface, private val onDone: () -> Unit) : View(ctx) {
    /** 0 → 1. 테스트에서는 직접 정해 그림을 찍는다. */
    var t = 0f
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var anim: ValueAnimator? = null
    private var finished = false

    companion object {
        const val DURATION = 4200L
        val ORANGE = Color.parseColor("#EB6834")
        val INK = Color.parseColor("#1D1D1F")
        val BG = Color.parseColor("#0E0E10")
        // 줄이 바닥에 닿는 때 (0~1)
        const val LAND_BOTTOM = .10f
        const val LAND_MID = .20f
        const val LAND_TOP = .30f
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

    override fun onTouchEvent(e: MotionEvent): Boolean { if (e.action == MotionEvent.ACTION_UP) finish(); return true }

    // ── 시간과 탄성 ──
    private fun seg(a: Float, b: Float) = ((t - a) / (b - a)).coerceIn(0f, 1f)
    private fun easeOut(x: Float) = 1 - (1 - x) * (1 - x) * (1 - x)
    private fun easeIn(x: Float) = x * x * x
    /** 목표를 살짝 지나쳤다가 돌아오는 스프링 (0 → 1) */
    private fun spring(x: Float) = if (x <= 0f) 0f else (1 - exp(-5.5f * x) * cos(11f * x))
    /** 부딪힌 뒤의 출렁임: 처음엔 크게, 점점 잦아든다 */
    private fun wobble(u: Float) = if (u <= 0f || u >= 1f) 0f else exp(-4.5f * u) * cos(15f * u)
    /** 땅에 닿은 때로부터 지난 정도 (출렁임 길이 .16) */
    private fun since(land: Float) = (t - land) / .16f

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        c.drawColor(BG)
        val S = minOf(w, h) * .46f
        val ox = (w - S) / 2; val oy = (h - S) / 2 - S * .08f

        // 로고로 모이기 (스프링)
        val gather = spring(seg(.74f, .90f))
        val sq = spring(seg(.74f, .88f))
        if (sq > 0f) {
            p.style = Paint.Style.FILL; p.color = INK; p.alpha = (255 * sq.coerceAtMost(1f)).toInt()
            val scale = .72f + .28f * sq
            c.save(); c.scale(scale, scale, ox + S / 2, oy + S / 2)
            c.drawRoundRect(ox, oy, ox + S, oy + S, S * .22f, S * .22f, p)
            c.restore()
        }

        // 車: 처음엔 가운데에 크게, 모일 때 왼쪽 아래 칸으로
        val bigBox = S * .92f; val cellBox = S * .34f
        val box = bigBox + (cellBox - bigBox) * gather
        val cx = w / 2 + (ox + S * .30f - w / 2) * gather
        val cy = (oy + S / 2) + (oy + S * .70f - (oy + S / 2)) * gather
        drawCha(c, cx - box / 2, cy - box / 2, box)

        // 차 · 곡 · 곡 이 하나씩 통통
        p.style = Paint.Style.FILL; p.typeface = face; p.textAlign = Paint.Align.CENTER; p.textSize = S * .34f; p.color = Color.WHITE
        listOf(Triple("차", .30f, .42f), Triple("곡", .70f, .42f), Triple("곡", .70f, .82f)).forEachIndexed { i, (ch, u, v) ->
            val k = seg(.80f + i * .03f, .93f + i * .03f); if (k <= 0f) return@forEachIndexed
            val pop = spring(k)
            p.alpha = (255 * minOf(1f, k * 4f)).toInt()
            val px = ox + S * u; val py = oy + S * v - S * .12f
            c.save(); c.scale(pop, pop, px, py); c.drawText(ch, px, oy + S * v, p); c.restore()
        }
        // 한 줄 소개
        val tag = easeOut(seg(.88f, .95f))
        if (tag > 0f) {
            p.textSize = S * .085f; p.color = Color.parseColor("#C7C7CC"); p.alpha = (255 * tag).toInt()
            c.drawText("차로 쓰는 성장일기", w / 2, oy + S + S * .22f + (1 - tag) * S * .04f, p)
        }
    }

    /** 車을 획으로 그린다. (x, y)에서 한 변이 s 인 칸 안에 */
    private fun drawCha(c: Canvas, x: Float, y: Float, s: Float) {
        fun X(u: Float) = x + s * u
        fun Y(v: Float) = y + s * v
        val th = s * .085f
        p.style = Paint.Style.FILL; p.color = ORANGE; p.alpha = 255

        // 꿰뚫린 순간 글자 전체가 아래로 눌렸다가 튕긴다
        val hit = wobble((t - .67f) / .18f)
        c.save()
        c.scale(1 + .07f * hit, 1 - .13f * hit, X(.5f), Y(.98f))

        // 위 줄이 떨어질 때 아래 줄들이 함께 눌리는 정도
        fun press(landedAfter: Float) = wobble(since(landedAfter)).coerceAtLeast(0f) * s * .035f

        /** 줄 하나: 떨어지기(가속) → 닿는 순간 납작 → 출렁. 그린 y를 돌려준다 */
        fun bar(land: Float, x0: Float, x1: Float, yTarget: Float, extraY: Float): Float? {
            val fallStart = land - .07f
            if (t < fallStart) return null
            val fall = ((t - fallStart) / .07f).coerceIn(0f, 1f)
            val yy = Y(yTarget) - (1 - easeIn(fall)) * s * 1.3f + extraY
            val wob = wobble(since(land))
            val sy = 1 - .55f * wob; val sx = 1 + .14f * wob
            val cxm = (X(x0) + X(x1)) / 2; val half = (X(x1) - X(x0)) / 2 * sx; val tt = th / 2 * sy
            val base = yy + th / 2          // 바닥에 붙은 채로 납작해진다
            p.alpha = (255 * minOf(1f, fall * 3f)).toInt()
            c.drawRoundRect(RectF(cxm - half, base - 2 * tt, cxm + half, base), tt, tt, p)
            return yy
        }

        bar(LAND_BOTTOM, .06f, .94f, .80f, press(LAND_MID) + press(LAND_TOP))   // 아래 긴 줄

        // 가운데 줄 → 탄성 있게 상자(曰)로
        val midY = bar(LAND_MID, .20f, .80f, .47f, press(LAND_TOP))
        val open = spring(seg(.40f, .60f))
        if (midY != null && open > 0f) {
            p.alpha = 255
            val half = s * .16f * open
            val r = th / 2
            c.drawRoundRect(RectF(X(.20f), midY - half - r, X(.80f), midY - half + r), r, r, p)
            c.drawRoundRect(RectF(X(.20f), midY + half - r, X(.80f), midY + half + r), r, r, p)
            c.drawRoundRect(RectF(X(.20f) - r, midY - half - r, X(.20f) + r, midY + half + r), r, r, p)
            c.drawRoundRect(RectF(X(.80f) - r, midY - half - r, X(.80f) + r, midY + half + r), r, r, p)
        }

        bar(LAND_TOP, .24f, .76f, .15f, 0f)                                    // 위 짧은 줄

        // 세로 획: 위로 살짝 당겼다가(예비 동작) 빠르게 꽂힌다
        val ready = easeOut(seg(.56f, .63f))
        val shoot = easeIn(seg(.63f, .67f))
        if (ready > 0f) {
            p.alpha = 255
            val pull = s * .07f * ready * (1 - shoot)
            val top = Y(.02f) - pull
            val stub = s * .14f * ready
            val bottom = top + stub + (Y(.98f) - top - stub) * shoot
            val r = th / 2
            c.drawRoundRect(RectF(X(.5f) - r, top, X(.5f) + r, bottom), r, r, p)
            val flash = 1 - seg(.67f, .75f)
            if (shoot >= 1f && flash > 0f) {
                // 꽂힌 자리에서 얇은 고리가 퍼진다
                p.style = Paint.Style.STROKE; p.strokeWidth = th * .35f * flash; p.alpha = (200 * flash).toInt()
                c.drawCircle(X(.5f), Y(.98f), th * (.8f + 3.2f * (1 - flash)), p)
                p.style = Paint.Style.FILL; p.alpha = 255
            }
        }
        c.restore()
    }
}
