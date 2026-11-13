// 차곡車곡 – 색, 글꼴, 직접 그리는 화면 조각들
// TraceView  : 주행 하나를 차선처럼 그린다 (깜빡이, 브레이크, 와이퍼, 주차, 한마디)
// GridText   : 원고지 칸에 일기 글을 쓴다
// Telltale   : 계기판 표시등처럼 켜지는 아이콘
package com.example.chagokchagok

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.TextView

/** 한 줄의 주행. */
class Lane(val label: String, val drive: Drive, val tips: List<Tip> = emptyList(), val faded: Boolean = false)

class TraceView(ctx: Context, private val ui: Ui, private val onPaper: Boolean = false) : View(ctx) {
    var lanes: List<Lane> = emptyList()
        set(v) { field = v; invalidate() }
    /** 지금 어디쯤인지(0~1). 값이 있으면 첫 줄 위에 점으로 그린다. */
    var marker: Float? = null
        set(v) { field = v; invalidate() }

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val pm = PathMeasure()
    private val pos = FloatArray(2)
    private val tan = FloatArray(2)

    override fun onDraw(c: Canvas) {
        if (lanes.isEmpty()) return
        val w = width.toFloat(); val h = height.toFloat()
        val rowH = h / lanes.size
        val band = (rowH * 0.36f).coerceAtMost(ui.dp(58).toFloat())
        lanes.forEachIndexed { i, lane -> drawLane(c, lane, w, rowH * i + rowH * 0.62f, band) }
    }

    private fun drawLane(c: Canvas, lane: Lane, w: Float, y: Float, band: Float) {
        val endX = w - band * 2.2f
        val path = Path().apply {
            moveTo(0f, y)
            cubicTo(w * .14f, y - band * .55f, w * .23f, y + band * .6f, w * .36f, y + band * .06f)
            cubicTo(w * .50f, y - band * .5f, w * .58f, y - band * .5f, w * .69f, y)
            cubicTo(w * .78f, y + band * .45f, w * .84f, y + band * .4f, endX, y)
        }
        pm.setPath(path, false)
        val len = pm.length
        val lineColor = when {
            onPaper && lane.faded -> Ink.paperLine
            onPaper -> Ink.ink
            lane.faded -> Ink.dim
            else -> Ink.paint
        }
        // 길
        p.style = Paint.Style.STROKE; p.strokeCap = Paint.Cap.ROUND
        p.color = if (onPaper) Ink.paperRoad else Ink.road; p.strokeWidth = band
        c.drawPath(path, p)
        // 와이퍼가 움직인 구간
        var wiperOn = -1f
        val spans = mutableListOf<Pair<Float, Float>>()
        lane.drive.events.filter { it.type == Evt.WIPER }.forEach {
            if (it.value == "on" && wiperOn < 0) wiperOn = it.p
            else if (it.value == "off" && wiperOn >= 0) { spans.add(wiperOn to it.p); wiperOn = -1f }
        }
        if (wiperOn >= 0) spans.add(wiperOn to 1f)
        p.color = Ink.rain; p.alpha = 150; p.strokeWidth = band * .42f
        spans.forEach { (a, b) ->
            val seg = Path(); pm.getSegment(len * a, len * maxOf(b, a + .03f), seg, true); c.drawPath(seg, p)
        }
        p.alpha = 255
        // 주행 선
        p.color = lineColor; p.strokeWidth = band * .09f
        c.drawPath(path, p)
        // 지금 위치
        val m = marker
        if (m != null && lane === lanes.first()) {
            pm.getPosTan(len * m.coerceIn(0f, 1f), pos, tan)
            p.style = Paint.Style.FILL; p.color = Ink.center; c.drawCircle(pos[0], pos[1], band * .3f, p)
            p.style = Paint.Style.STROKE; p.color = Ink.asphalt; p.strokeWidth = band * .08f; c.drawCircle(pos[0], pos[1], band * .3f, p)
            p.color = lineColor; p.strokeWidth = band * .09f
        }
        // 깜빡이와 브레이크
        val tick = band * .1f
        lane.drive.events.forEach { e ->
            pm.getPosTan(len * e.p.coerceIn(0.01f, 0.99f), pos, tan)
            val nx = tan[1]; val ny = -tan[0]
            when (e.type) {
                Evt.SIGNAL -> {
                    p.color = Ink.signal; p.strokeWidth = tick
                    for (k in listOf(-tick, tick)) {
                        val bx = pos[0] + tan[0] * k; val by = pos[1] + tan[1] * k
                        c.drawLine(bx + nx * band * .18f, by + ny * band * .18f, bx + nx * band * .42f, by + ny * band * .42f, p)
                    }
                }
                Evt.BRAKE -> {
                    p.style = Paint.Style.FILL; p.color = Ink.brake
                    c.drawCircle(pos[0] - nx * band * .27f, pos[1] - ny * band * .27f, band * .11f, p)
                    p.style = Paint.Style.STROKE
                }
            }
        }
        // 주차하며 오간 횟수
        if (lane.drive.parkMoves > 0) {
            pm.getPosTan(len, pos, tan)
            val zig = Path().apply { moveTo(pos[0], pos[1]) }
            val step = band * .24f
            for (i in 1..lane.drive.parkMoves.coerceAtMost(8)) zig.lineTo(pos[0] + if (i % 2 == 1) band * .45f else 0f, pos[1] - step * i)
            p.color = lineColor; p.strokeWidth = band * .09f; p.strokeJoin = Paint.Join.ROUND
            c.drawPath(zig, p)
        }
        // 한마디
        p.style = Paint.Style.FILL; p.textAlign = Paint.Align.CENTER; p.typeface = ui.sign
        lane.tips.forEachIndexed { i, t ->
            pm.getPosTan(len * t.p.coerceIn(0.02f, 0.98f), pos, tan)
            p.color = Ink.center; c.drawCircle(pos[0], pos[1], band * .27f, p)
            p.color = Color.WHITE; p.textSize = band * .34f
            c.drawText("${i + 1}", pos[0], pos[1] + band * .12f, p)
        }
        // 누구의 주행인지
        p.textAlign = Paint.Align.LEFT; p.textSize = band * .46f
        p.color = if (onPaper) (if (lane.faded) Ink.dim else Ink.ink) else (if (lane.faded) Ink.dim else Ink.paint)
        c.drawText(lane.label, band * .3f, y - band * .78f, p)
        p.style = Paint.Style.STROKE; p.typeface = null
    }
}

/**
 * 원고지. 한 칸에 한 글자. 읽기 편하게:
 * - 칸은 한 줄에 10~14칸이 되도록 크게 잡고, 줄 사이를 조금 띄운다
 * - 줄 첫 칸에 오는 빈칸은 버리고, 문장부호는 앞 줄 끝 칸에 함께 넣는다 (원고지 쓰는 법)
 * - 격자는 옅게, 글자는 진하게, 강조할 문장은 칸을 연노랑으로 칠한다
 */
class GridText(ctx: Context, private val ui: Ui) : View(ctx) {
    var text: String = ""
        set(v) { field = v; requestLayout(); invalidate() }
    var highlight: String = ""
    var cols = 12
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private data class Cell(val ch: Char, val idx: Int, val extra: Char? = null)

    /** 글을 줄과 칸으로 나눈다 */
    private fun layout(): List<List<Cell>> {
        val lines = mutableListOf<MutableList<Cell>>(mutableListOf())
        text.forEachIndexed { i, ch ->
            var cur = lines.last()
            if (cur.size == cols) {
                // 줄 첫 칸에 올 문장부호는 앞 줄 마지막 칸에 붙인다
                if (ch in ".,!?" && cur.isNotEmpty() && cur.last().extra == null) { cur[cur.lastIndex] = cur.last().copy(extra = ch); return@forEachIndexed }
                cur = mutableListOf(); lines.add(cur)
            }
            if (ch == ' ' && cur.isEmpty() && lines.size > 1) return@forEachIndexed   // 줄 첫 칸 빈칸은 버린다
            cur.add(Cell(ch, i))
        }
        return lines
    }

    private fun gapRatio() = 0.28f

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val cell = w / cols.toFloat()
        val rows = layout().size.coerceAtLeast(1)
        setMeasuredDimension(w, (rows * cell * (1 + gapRatio()) - cell * gapRatio()).toInt() + 2)
    }

    override fun onDraw(c: Canvas) {
        val cell = width / cols.toFloat()
        val step = cell * (1 + gapRatio())
        val hlStart = if (highlight.isNotEmpty()) text.indexOf(highlight) else -1
        val hlRange = if (hlStart >= 0) hlStart until hlStart + highlight.length else IntRange.EMPTY
        layout().forEachIndexed { r, line ->
            val top = r * step
            // 강조 칸
            p.style = Paint.Style.FILL; p.color = Ink.highlight
            line.forEachIndexed { col, cl -> if (cl.idx in hlRange) c.drawRect(col * cell, top, (col + 1) * cell, top + cell, p) }
            // 칸 (옅게)
            p.style = Paint.Style.STROKE; p.color = Ink.grid; p.strokeWidth = ui.dp(1f).toFloat().coerceAtLeast(1f)
            c.drawRect(0.5f, top, width - 0.5f, top + cell, p)
            for (col in 1 until cols) c.drawLine(col * cell, top, col * cell, top + cell, p)
            // 글자
            p.style = Paint.Style.FILL; p.color = Ink.ink; p.typeface = ui.batang
            p.textAlign = Paint.Align.CENTER; p.textSize = cell * .6f
            line.forEachIndexed { col, cl ->
                if (cl.ch != ' ') c.drawText(cl.ch.toString(), (col + .5f) * cell, top + cell * .73f, p)
                if (cl.extra != null) c.drawText(cl.extra.toString(), (col + .88f) * cell, top + cell * .8f, p)
            }
        }
    }
}

/** 계기판 표시등. on 이면 제 색으로 켜지고, waiting 이면 노란 점선으로 "지금 해 보라"고 알린다. */
class Telltale(ctx: Context, private val kind: String) : View(ctx) {
    var on = false
        set(v) { field = v; invalidate() }
    var waiting = false
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        val ox = (width - s) / 2; val oy = (height - s) / 2
        fun x(v: Float) = ox + s * v / 96f
        fun y(v: Float) = oy + s * v / 96f
        val color = when {
            on -> when (kind) { "signal" -> Ink.signal; "wiper" -> Ink.rain; "light" -> Ink.beam; else -> Ink.go }
            waiting -> Ink.center
            else -> Ink.text3
        }
        p.color = color; p.strokeWidth = s * .065f; p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
        when (kind) {
            "signal" -> {
                p.style = if (on) Paint.Style.FILL else Paint.Style.STROKE
                val l = Path().apply { moveTo(x(40f), y(30f)); lineTo(x(14f), y(48f)); lineTo(x(40f), y(66f)); lineTo(x(40f), y(56f)); lineTo(x(48f), y(56f)); lineTo(x(48f), y(40f)); lineTo(x(40f), y(40f)); close() }
                val r = Path().apply { moveTo(x(56f), y(30f)); lineTo(x(82f), y(48f)); lineTo(x(56f), y(66f)); lineTo(x(56f), y(56f)); lineTo(x(48f), y(56f)); lineTo(x(48f), y(40f)); lineTo(x(56f), y(40f)); close() }
                c.drawPath(l, p); c.drawPath(r, p)
            }
            "wiper" -> {
                p.style = Paint.Style.STROKE
                c.drawArc(RectF(x(6f), y(26f), x(90f), y(96f)), 215f, 110f, false, p)
                c.drawLine(x(48f), y(78f), x(34f), y(34f), p)
                p.style = Paint.Style.FILL; c.drawCircle(x(48f), y(78f), s * .06f, p)
            }
            "light" -> {
                p.style = Paint.Style.STROKE
                val d = Path().apply { moveTo(x(52f), y(24f)); cubicTo(x(86f), y(24f), x(86f), y(72f), x(52f), y(72f)); cubicTo(x(42f), y(72f), x(42f), y(24f), x(52f), y(24f)) }
                c.drawPath(d, p)
                for (yy in listOf(34f, 48f, 62f)) c.drawLine(x(14f), y(yy), x(34f), y(yy), p)
            }
            "belt" -> {
                p.style = Paint.Style.STROKE
                c.drawCircle(x(48f), y(24f), s * .1f, p)
                val body = Path().apply { moveTo(x(30f), y(80f)); lineTo(x(30f), y(56f)); cubicTo(x(30f), y(36f), x(66f), y(36f), x(66f), y(56f)); lineTo(x(66f), y(80f)) }
                c.drawPath(body, p); c.drawLine(x(32f), y(44f), x(66f), y(78f), p)
            }
            "gear" -> {
                p.style = Paint.Style.STROKE
                c.drawRoundRect(RectF(x(30f), y(14f), x(66f), y(82f)), s * .12f, s * .12f, p)
                c.drawLine(x(48f), y(30f), x(48f), y(66f), p)
                p.style = Paint.Style.FILL; c.drawCircle(x(48f), y(if (on) 30f else 62f), s * .09f, p)
            }
        }
    }
}
