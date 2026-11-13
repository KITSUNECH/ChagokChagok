// 차곡車곡 – 차량 기록 차트
// 원칙: 한 차트에 한 가지 값, 축은 하나, 선은 가늘게, 격자는 흐리게, 숫자는 마지막 값과 누른 점에만.
// 점수가 아니라 관측한 값이다. 차트 아래에 같은 내용을 글로도 적는다 (색에만 기대지 않게).
package com.example.chagokchagok

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View

/** 주행마다 하나씩 찍는 추세선. null 은 그 주행에 값이 없다는 뜻 (선을 끊는다). */
class TrendChart(ctx: Context, private val ui: Ui, private val values: List<Float?>, private val labels: List<String>, private val fmt: (Float) -> String, private val minZero: Boolean = true) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var picked = -1

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.action == MotionEvent.ACTION_DOWN || e.action == MotionEvent.ACTION_MOVE) {
            val n = values.size; if (n == 0) return true
            val i = (((e.x - padL()) / (width - padL() - padR()).coerceAtLeast(1f)) * (n - 1)).let { Math.round(it) }.coerceIn(0, n - 1)
            if (i != picked) { picked = i; invalidate() }
        }
        return true
    }

    private fun padL() = ui.dp(12).toFloat()
    private fun padR() = ui.dp(56).toFloat()

    override fun onDraw(c: Canvas) {
        val pts = values.withIndex().filter { it.value != null }
        if (pts.isEmpty()) return
        val w = width.toFloat(); val h = height.toFloat()
        val top = ui.dp(28).toFloat(); val bottom = h - ui.dp(30)
        val lo = if (minZero) 0f else pts.minOf { it.value!! }
        val hi = pts.maxOf { it.value!! }.let { if (it - lo < 1e-3f) lo + 1f else it }
        val n = values.size
        fun x(i: Int) = if (n > 1) padL() + (w - padL() - padR()) * i / (n - 1) else w / 2
        fun y(v: Float) = bottom - (bottom - top) * (v - lo) / (hi - lo)
        // 흐린 격자 (바닥과 꼭대기)
        p.style = Paint.Style.STROKE; p.strokeWidth = ui.dp(1).toFloat(); p.color = Ink.line
        c.drawLine(padL(), bottom, w - padR() / 2, bottom, p)
        p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(ui.dp(4).toFloat(), ui.dp(4).toFloat()), 0f)
        c.drawLine(padL(), y(hi), w - padR() / 2, y(hi), p); p.pathEffect = null
        p.style = Paint.Style.FILL; p.textSize = ui.dp(13).toFloat(); p.typeface = ui.regular; p.color = Ink.text3; p.textAlign = Paint.Align.LEFT
        c.drawText(fmt(hi), w - padR() / 2 + ui.dp(4), y(hi) + ui.dp(4), p)
        // 선 (값이 없는 주행에서 끊는다)
        p.style = Paint.Style.STROKE; p.strokeWidth = ui.dp(2.5f).toFloat(); p.color = Ink.accent; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        val path = Path(); var open = false
        values.forEachIndexed { i, v -> if (v == null) open = false else if (!open) { path.moveTo(x(i), y(v)); open = true } else path.lineTo(x(i), y(v)) }
        c.drawPath(path, p)
        // 점 (표면색 고리)
        pts.forEach { (i, v) ->
            p.style = Paint.Style.FILL; p.color = Ink.surface; c.drawCircle(x(i), y(v!!), ui.dp(6).toFloat(), p)
            p.color = Ink.accent; c.drawCircle(x(i), y(v), ui.dp(4).toFloat(), p)
        }
        // 이름표: 마지막 값, 그리고 누른 점
        p.typeface = ui.semibold; p.textSize = ui.dp(15).toFloat(); p.color = Ink.text; p.textAlign = Paint.Align.CENTER
        val last = pts.last()
        val show = if (picked >= 0 && values.getOrNull(picked) != null) picked else last.index
        val sv = values[show]!!
        if (show != last.index) { p.style = Paint.Style.STROKE; p.color = Ink.text3; p.strokeWidth = ui.dp(1).toFloat(); c.drawLine(x(show), top - ui.dp(6), x(show), bottom, p); p.style = Paint.Style.FILL; p.color = Ink.text }
        c.drawText(fmt(sv), x(show).coerceIn(ui.dp(30).toFloat(), w - ui.dp(30)), y(sv) - ui.dp(12), p)
        // 아래 이름 (처음, 마지막, 누른 것)
        p.typeface = ui.regular; p.textSize = ui.dp(13).toFloat(); p.color = Ink.text3
        val shown = setOf(0, n - 1, show)
        shown.forEach { i -> labels.getOrNull(i)?.let { c.drawText(it, x(i).coerceIn(ui.dp(24).toFloat(), w - ui.dp(40)), h - ui.dp(8), p) } }
    }
}

/** 주행마다 막대 하나 (거리 같은 크기 값) */
class BarChart(ctx: Context, private val ui: Ui, private val values: List<Float>, private val labels: List<String>, private val fmt: (Float) -> String) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var picked = -1
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (values.isEmpty()) return true
        val slot = width / values.size.toFloat()
        val i = (e.x / slot).toInt().coerceIn(0, values.size - 1)
        if (i != picked) { picked = i; invalidate() }
        return true
    }
    override fun onDraw(c: Canvas) {
        if (values.isEmpty()) return
        val w = width.toFloat(); val h = height.toFloat()
        val top = ui.dp(28).toFloat(); val bottom = h - ui.dp(30)
        val hi = (values.maxOrNull() ?: 1f).coerceAtLeast(0.1f)
        val slot = w / values.size; val bw = (slot * .56f).coerceAtMost(ui.dp(44).toFloat())
        p.style = Paint.Style.STROKE; p.strokeWidth = ui.dp(1).toFloat(); p.color = Ink.line
        c.drawLine(0f, bottom, w, bottom, p)
        val show = if (picked >= 0) picked else values.lastIndex
        values.forEachIndexed { i, v ->
            val cx = slot * i + slot / 2
            val top2 = bottom - (bottom - top) * v / hi
            p.style = Paint.Style.FILL; p.color = if (i == show) Ink.accent else Ink.alpha(Ink.accent, .45f)
            val r = ui.dp(4).toFloat()
            c.drawRoundRect(RectF(cx - bw / 2, top2, cx + bw / 2, bottom), r, r, p)
            c.drawRect(cx - bw / 2, (top2 + r).coerceAtMost(bottom), cx + bw / 2, bottom, p)   // 바닥은 각지게
        }
        val cx = slot * show + slot / 2
        p.color = Ink.text; p.textSize = ui.dp(15).toFloat(); p.typeface = ui.semibold; p.textAlign = Paint.Align.CENTER
        c.drawText(fmt(values[show]), cx.coerceIn(ui.dp(30).toFloat(), w - ui.dp(30)), bottom - (bottom - top) * values[show] / hi - ui.dp(8), p)
        p.typeface = ui.regular; p.textSize = ui.dp(13).toFloat(); p.color = Ink.text3
        setOf(0, values.lastIndex, show).forEach { i -> labels.getOrNull(i)?.let { c.drawText(it, (slot * i + slot / 2).coerceIn(ui.dp(24).toFloat(), w - ui.dp(24)), h - ui.dp(8), p) } }
    }
}

/**
 * 주행 한 번의 타임라인. 줄마다 장치 하나: 깜빡이(눈금), 와이퍼(띠), 브레이크(점), 전조등(띠).
 * 가로축은 그 주행의 처음부터 끝까지 (거리 신호가 있으면 거리, 없으면 시간).
 */
class Timeline(ctx: Context, private val ui: Ui, private val drive: Drive) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    companion object {
        /** 색약 검사를 통과한 네 가지 (줄 이름과 모양이 함께 구분해 준다) */
        fun colors() = if (Ink.night) listOf("#d95926", "#3987e5", "#e66767", "#199e70") else listOf("#eb6834", "#2a78d6", "#e34948", "#1baf7a")
        val rows = listOf("깜빡이", "와이퍼", "브레이크", "전조등")
    }
    override fun onDraw(c: Canvas) {
        val col = colors().map { Color.parseColor(it) }
        val w = width.toFloat(); val h = height.toFloat()
        val labelW = ui.dp(76).toFloat(); val right = w - ui.dp(8)
        val rowH = (h - ui.dp(26)) / rows.size
        fun x(pr: Float) = labelW + (right - labelW) * pr.coerceIn(0f, 1f)
        p.textSize = ui.dp(14).toFloat(); p.typeface = ui.regular
        rows.forEachIndexed { r, name ->
            val cy = rowH * r + rowH / 2
            p.style = Paint.Style.FILL; p.color = Ink.text2; p.textAlign = Paint.Align.LEFT
            c.drawText(name, 0f, cy + ui.dp(5), p)
            p.color = Ink.fill; c.drawRoundRect(RectF(labelW, cy - ui.dp(3), right, cy + ui.dp(3)), ui.dp(3).toFloat(), ui.dp(3).toFloat(), p)
        }
        fun band(row: Int, type: String) {
            val cy = rowH * row + rowH / 2
            var from = -1f
            val spans = mutableListOf<Pair<Float, Float>>()
            drive.events.filter { it.type == type }.forEach { if (it.value == "on" && from < 0) from = it.p else if (it.value == "off" && from >= 0) { spans.add(from to it.p); from = -1f } }
            if (from >= 0) spans.add(from to 1f)
            p.color = col[row]; p.style = Paint.Style.FILL
            spans.forEach { (a, b) -> c.drawRoundRect(RectF(x(a), cy - ui.dp(7), maxOf(x(b), x(a) + ui.dp(8)), cy + ui.dp(7)), ui.dp(4).toFloat(), ui.dp(4).toFloat(), p) }
        }
        // 깜빡이: 방향을 알 수 있게 짧은 화살촉
        drive.events.filter { it.type == Evt.SIGNAL }.forEach { e ->
            val cy = rowH * 0 + rowH / 2; val cx = x(e.p); val s = ui.dp(8).toFloat()
            p.color = col[0]; p.style = Paint.Style.FILL
            val path = Path().apply {
                if (e.value == "LEFT") { moveTo(cx - s, cy); lineTo(cx + s * .6f, cy - s); lineTo(cx + s * .6f, cy + s) }
                else { moveTo(cx + s, cy); lineTo(cx - s * .6f, cy - s); lineTo(cx - s * .6f, cy + s) }
                close()
            }
            c.drawPath(path, p)
        }
        band(1, Evt.WIPER)
        drive.events.filter { it.type == Evt.BRAKE }.forEach { e ->
            val cy = rowH * 2 + rowH / 2
            p.style = Paint.Style.FILL; p.color = Ink.surface; c.drawCircle(x(e.p), cy, ui.dp(7).toFloat(), p)
            p.color = col[2]; c.drawCircle(x(e.p), cy, ui.dp(5).toFloat(), p)
        }
        band(3, Evt.LIGHT)
        // 아래: 처음 · 끝
        p.color = Ink.text3; p.textSize = ui.dp(13).toFloat(); p.textAlign = Paint.Align.LEFT
        c.drawText("출발", labelW, h - ui.dp(4), p)
        p.textAlign = Paint.Align.RIGHT
        c.drawText(if (drive.distKm > 0.05f) String.format("%.1fkm", drive.distKm) else "${drive.minutes}분", right, h - ui.dp(4), p)
    }
}
