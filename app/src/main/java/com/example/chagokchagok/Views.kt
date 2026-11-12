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

/** 도로에서 가져온 색. 색마다 뜻이 하나씩 있다. */
object Ink {
    val tar = Color.parseColor("#17191C")
    val asphalt = Color.parseColor("#24272B")
    val road = Color.parseColor("#2F3338")
    val kerb = Color.parseColor("#3C4147")
    val paint = Color.parseColor("#F2EFE7")      // 차선 흰색: 글자
    val dim = Color.parseColor("#9A9FA6")
    val center = Color.parseColor("#F5BC1D")     // 중앙선 노랑: 누를 것, 한마디
    val signal = Color.parseColor("#FF8B1F")     // 방향지시등
    val brake = Color.parseColor("#E8432E")      // 제동등
    val rain = Color.parseColor("#4C97EE")       // 와이퍼
    val go = Color.parseColor("#33A871")         // 확인됨
    val paper = Color.parseColor("#FBFAF5")
    val ink = Color.parseColor("#22262B")
    val grid = Color.parseColor("#E9A79C")
    val paperRoad = Color.parseColor("#EFEDE6")
    val paperLine = Color.parseColor("#B9B6AE")
}

/** 글꼴과 크기 단위를 한곳에서 쓴다. */
class Ui(val ctx: Context) {
    val sign: Typeface = ctx.resources.getFont(R.font.dohyeon)        // 표지판 같은 제목 글꼴
    val batang: Typeface = ctx.resources.getFont(R.font.gowunbatang)  // 일기와 한마디 글꼴
    private val density = ctx.resources.displayMetrics.density
    /** 화면이 클수록 조금 더 키운다. 차 화면은 팔 길이에서 본다. */
    // 기기 화면이 아니라 지금 앱이 받은 창의 크기로 잰다. 내비 옆 절반 창에서는 창이 좁고 세로로 길다.
    private val w = ctx.resources.configuration.screenWidthDp
    private val h = ctx.resources.configuration.screenHeightDp
    /** 창이 세로로 길거나 좁아서 한 줄로 쌓아야 하는지. */
    val narrow = w < h * 1.15f || w < 700
    val scale = (if (narrow) minOf(w / 560f, h / 760f) else minOf(w / 900f, h / 600f)).coerceIn(0.62f, 1.4f)
    fun dp(v: Float) = (v * density * scale).toInt()
    fun dp(v: Int) = dp(v.toFloat())

    fun text(s: String, size: Float, color: Int = Ink.paint, face: Typeface? = null) = TextView(ctx).apply {
        text = s; textSize = size * scale; setTextColor(color); if (face != null) typeface = face
        includeFontPadding = false
    }

    fun button(s: String, primary: Boolean, onClick: () -> Unit) = TextView(ctx).apply {
        text = s; textSize = 24f * scale; gravity = Gravity.CENTER
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (primary) Color.parseColor("#1B1705") else Ink.paint)
        background = GradientDrawable().apply {
            cornerRadius = dp(14).toFloat()
            if (primary) setColor(Ink.center) else { setColor(Color.TRANSPARENT); setStroke(dp(2), Ink.kerb) }
        }
        setPadding(dp(28), 0, dp(28), 0)
        minHeight = dp(76); minimumHeight = dp(76)
        setOnClickListener { onClick() }
    }
}

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
            p.color = Color.parseColor("#1B1705"); p.textSize = band * .34f
            c.drawText("${i + 1}", pos[0], pos[1] + band * .12f, p)
        }
        // 누구의 주행인지
        p.textAlign = Paint.Align.LEFT; p.textSize = band * .46f
        p.color = if (onPaper) (if (lane.faded) Ink.dim else Ink.ink) else (if (lane.faded) Ink.dim else Ink.paint)
        c.drawText(lane.label, band * .3f, y - band * .78f, p)
        p.style = Paint.Style.STROKE; p.typeface = null
    }
}

/** 원고지. 글을 한 칸에 한 글자씩 넣고, 강조할 문장은 칸을 칠한다. */
class GridText(ctx: Context, private val ui: Ui) : View(ctx) {
    var text: String = ""
        set(v) { field = v; requestLayout(); invalidate() }
    var highlight: String = ""
    var cols = 14
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val w = MeasureSpec.getSize(wSpec)
        val cell = w / cols.toFloat()
        val rows = ((text.length + cols - 1) / cols).coerceAtLeast(1)
        setMeasuredDimension(w, (cell * rows).toInt() + 2)
    }

    override fun onDraw(c: Canvas) {
        val cell = width / cols.toFloat()
        val rows = ((text.length + cols - 1) / cols).coerceAtLeast(1)
        val hlStart = if (highlight.isNotEmpty()) text.indexOf(highlight) else -1
        // 강조 칸
        if (hlStart >= 0) {
            p.style = Paint.Style.FILL; p.color = Color.parseColor("#FCE9B4")
            for (i in hlStart until hlStart + highlight.length) {
                val r = i / cols; val col = i % cols
                c.drawRect(col * cell, r * cell, (col + 1) * cell, (r + 1) * cell, p)
            }
        }
        // 칸
        p.style = Paint.Style.STROKE; p.color = Ink.grid; p.strokeWidth = ui.dp(1.5f).toFloat()
        for (r in 0..rows) c.drawLine(0f, r * cell, width.toFloat(), r * cell, p)
        for (col in 0..cols) c.drawLine((col * cell).coerceAtMost(width - 1f), 0f, (col * cell).coerceAtMost(width - 1f), rows * cell, p)
        // 글자
        p.style = Paint.Style.FILL; p.color = Ink.ink; p.typeface = ui.batang
        p.textAlign = Paint.Align.CENTER; p.textSize = cell * .62f
        text.forEachIndexed { i, ch ->
            if (ch != ' ') c.drawText(ch.toString(), (i % cols + .5f) * cell, (i / cols + .72f) * cell, p)
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
            on -> when (kind) { "signal" -> Ink.signal; "wiper" -> Ink.rain; "light" -> Ink.center; else -> Ink.go }
            waiting -> Ink.center
            else -> Color.parseColor("#5A6068")
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
