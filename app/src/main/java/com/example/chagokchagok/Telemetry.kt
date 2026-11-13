// 차곡車곡 – 차량 데이터 지표
// 이 앱이 실제로 받은 차량 신호를 종류별로 센다. 시험용 신호는 세지 않는다 (muted).
// PT에서 "차량 데이터를 정말 쓰는지"를 보여 주는 화면(차량 데이터)의 재료다.
package com.example.chagokchagok

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View

internal object Telemetry {
    data class Sig(val key: String, val name: String, val sdk: String, val icon: String)
    val SIGNALS = listOf(
        Sig(Evt.GEAR, "기어", "Vehicle", "steering"), Sig(Evt.SIGNAL, "방향지시등", "Vehicle", "turnRight"), Sig(Evt.BRAKE, "브레이크 페달", "Vehicle", "stop"),
        Sig(Evt.WIPER, "와이퍼", "Vehicle", "cloud"), Sig(Evt.LIGHT, "전조등", "Vehicle", "sparkle"), Sig("belt", "안전벨트", "Vehicle", "seat"),
        Sig(Evt.SEAT, "좌석 감지", "Vehicle", "people"), Sig(Evt.CRUISE, "크루즈", "Vehicle", "speed"), Sig(Evt.LAMP, "표시등", "Vehicle", "warning"),
        Sig("odometer", "주행거리", "Vehicle", "road"), Sig(Evt.NAVI, "내비 안내", "NaviHelper", "pin"), Sig(Evt.LOST, "경로 재탐색", "NaviHelper", "pin"),
        Sig("location", "위치", "FusedLocation", "pin"),
    )
    val count = mutableMapOf<String, Int>()
    val last = mutableMapOf<String, String>()
    val at = mutableMapOf<String, Long>()
    private val recent = ArrayDeque<Long>()
    var muted = false
    val since = System.currentTimeMillis()

    fun hit(key: String, value: String) {
        if (muted) return
        val now = System.currentTimeMillis()
        count[key] = (count[key] ?: 0) + 1; last[key] = value; at[key] = now
        recent.addLast(now); while (recent.isNotEmpty() && now - recent.first() > 60_000) recent.removeFirst()
    }
    val total get() = count.values.sum()
    val kinds get() = count.keys.size
    fun perMinute(): Int { val now = System.currentTimeMillis(); while (recent.isNotEmpty() && now - recent.first() > 60_000) recent.removeFirst(); return recent.size }
}

/**
 * 車의 획 구성. Noto Sans KR Bold의 車을 재서 만든 비율이라 한글 "차"와 굵기·키·획 끝이 맞는다.
 * 각 획은 (왼쪽, 위, 오른쪽, 아래)를 글자 상자의 폭·높이에 대한 비율로 적는다. 끝은 평평하다.
 */
object ChaGlyph {
    val TOP = floatArrayOf(.027f, .088f, .967f, .199f)
    val MID = floatArrayOf(.110f, .420f, .885f, .513f)
    val BOTTOM = floatArrayOf(.000f, .742f, 1.00f, .856f)
    val BOX_T = floatArrayOf(.110f, .253f, .885f, .354f)
    val BOX_B = floatArrayOf(.110f, .582f, .885f, .684f)
    val SIDE_L = floatArrayOf(.110f, .253f, .234f, .684f)
    val SIDE_R = floatArrayOf(.755f, .253f, .885f, .684f)
    val VERT = floatArrayOf(.426f, .000f, .560f, 1.00f)
    val ALL = listOf(TOP, MID, BOTTOM, BOX_T, BOX_B, SIDE_L, SIDE_R, VERT)

    /** "차"를 이 글꼴·크기로 쓸 때의 상자에 맞춘 車 상자 (중심 x, 글자 기준선) → 왼쪽, 위, 폭, 높이 */
    fun box(p: Paint, cx: Float, baseline: Float): FloatArray {
        val r = android.graphics.Rect(); p.getTextBounds("차", 0, 1, r)
        val w = r.width() * 1.04f; val h = r.height() * 1.02f
        val cy = baseline + (r.top + r.bottom) / 2f
        return floatArrayOf(cx - w / 2, cy - h / 2, w, h)
    }
    fun draw(c: Canvas, p: Paint, x: Float, y: Float, w: Float, h: Float) {
        ALL.forEach { q -> c.drawRect(x + w * q[0], y + h * q[1], x + w * q[2], y + h * q[3], p) }
    }
}

/** 브랜드 마크: 검정 정사각형에 샌드색 차곡 / 車곡. 車은 Active Blue로, 한글과 같은 굵기의 획으로 그린다 */
internal class BrandMark(ctx: android.content.Context, private val ui: Ui) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        val ox = (width - s) / 2; val oy = (height - s) / 2
        p.style = Paint.Style.FILL; p.color = android.graphics.Color.BLACK
        c.drawRoundRect(ox, oy, ox + s, oy + s, s * .22f, s * .22f, p)
        p.typeface = ui.bold; p.textAlign = Paint.Align.CENTER; p.textSize = s * .34f
        p.color = android.graphics.Color.parseColor("#E4DCD3")
        c.drawText("차", ox + s * .30f, oy + s * .42f, p)
        c.drawText("곡", ox + s * .70f, oy + s * .42f, p)
        c.drawText("곡", ox + s * .70f, oy + s * .82f, p)
        val b = ChaGlyph.box(p, ox + s * .30f, oy + s * .82f)
        p.color = android.graphics.Color.parseColor("#00AAD2")
        ChaGlyph.draw(c, p, b[0], b[1], b[2], b[3])
    }
}

/** 화면 위 브랜드 줄: 마크 + 이름 */
internal fun MainActivity.brand(size: Int = 30): View = kit.row(10).apply {
    addView(BrandMark(context, ui), kit.lp(ui.dp(size), ui.dp(size)))
    addView(kit.text("차곡車곡", Type.Title))
}
