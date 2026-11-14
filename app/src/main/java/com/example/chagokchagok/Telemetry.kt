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
        Sig(Evt.GEAR, "기어", "AAOS GEAR_SELECTION", "steering"), Sig(Evt.SIGNAL, "방향지시등", "Vehicle", "turnRight"), Sig(Evt.BRAKE, "브레이크 페달", "Vehicle", "stop"),
        Sig(Evt.WIPER, "와이퍼", "Vehicle", "cloud"), Sig(Evt.LIGHT, "전조등", "Vehicle", "sparkle"), Sig("belt", "안전벨트", "Vehicle", "seat"),
        Sig(Evt.SEAT, "좌석 감지", "Vehicle", "people"), Sig(Evt.CRUISE, "크루즈", "Vehicle", "speed"), Sig(Evt.LAMP, "표시등", "Vehicle", "warning"),
        Sig("odometer", "주행거리", "Vehicle", "road"), Sig(Evt.NAVI, "내비 안내", "NaviHelper", "pin"), Sig(Evt.LOST, "경로 재탐색", "NaviHelper", "pin"),
        Sig("location", "위치", "FusedLocation", "pin"), Sig(Evt.SPEED, "속도", "AAOS PERF_VEHICLE_SPEED", "speed"),
    )
    val count = mutableMapOf<String, Int>()
    val last = mutableMapOf<String, String>()
    val at = mutableMapOf<String, Long>()
    private val recent = ArrayDeque<Long>()
    var muted = false
    val since = System.currentTimeMillis()

    /** 시연·시험으로 만든 신호 수 (실제 차량 신호와 섞지 않고 따로 보여 준다) */
    var simulated = 0; private set
    /** 이 안에서 생긴 신호는 실제 차량 신호가 아니다 */
    inline fun fake(block: () -> Unit) { val was = muted; muted = true; try { block() } finally { muted = was } }

    fun hit(key: String, value: String) {
        if (muted) { simulated++; return }
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
/**
 * 로고 배치: 앱 아이콘(검정 타일 + 안쪽 흰 선 + 차·곡 / 車·곡)과 같은 비율. 앱 안의 마크와 시작 모션이 함께 쓴다.
 * 값은 타일 한 변(s)에 대한 비율이다. 흰 선은 아이콘의 adaptive 전경과 같은 자리(안쪽 5.6%, 모서리 19%).
 */
internal object LogoLayout {
    const val TILE_R = .22f        // 타일 모서리
    const val RING_INSET = .056f   // 흰 선의 안쪽 여백
    const val RING_R = .19f        // 흰 선 모서리
    const val RING_W = .022f       // 흰 선 두께
    const val TEXT = .31f          // 글자 크기
    const val COL_L = .31f; const val COL_R = .69f
    const val ROW_T = .44f; const val ROW_B = .80f
    val SAND = android.graphics.Color.parseColor("#E4DCD3")
    val BLUE = android.graphics.Color.parseColor("#00AAD2")

    /** 흰 선의 사각형 경로 (위 가운데에서 시작해 시계 방향) — 시작 모션이 이 경로를 따라 그린다 */
    fun ring(ox: Float, oy: Float, s: Float): android.graphics.Path {
        val i = s * RING_INSET; val r = s * RING_R
        val l = ox + i; val t = oy + i; val rr = ox + s - i; val b = oy + s - i
        return android.graphics.Path().apply {
            moveTo((l + rr) / 2, t)
            lineTo(rr - r, t); arcTo(rr - 2 * r, t, rr, t + 2 * r, -90f, 90f, false)
            lineTo(rr, b - r); arcTo(rr - 2 * r, b - 2 * r, rr, b, 0f, 90f, false)
            lineTo(l + r, b); arcTo(l, b - 2 * r, l + 2 * r, b, 90f, 90f, false)
            lineTo(l, t + r); arcTo(l, t, l + 2 * r, t + 2 * r, 180f, 90f, false)
            close()
        }
    }
}

internal class BrandMark(ctx: android.content.Context, private val ui: Ui) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val L = LogoLayout
        val s = minOf(width, height).toFloat()
        val ox = (width - s) / 2; val oy = (height - s) / 2
        p.style = Paint.Style.FILL; p.color = android.graphics.Color.BLACK
        c.drawRoundRect(ox, oy, ox + s, oy + s, s * L.TILE_R, s * L.TILE_R, p)
        // 앱 아이콘과 같은 안쪽 흰 선 (작은 크기에서도 1px 이상)
        p.style = Paint.Style.STROKE; p.color = android.graphics.Color.WHITE; p.strokeWidth = maxOf(1f, s * L.RING_W)
        c.drawPath(L.ring(ox, oy, s), p)
        p.style = Paint.Style.FILL
        p.typeface = ui.bold; p.textAlign = Paint.Align.CENTER; p.textSize = s * L.TEXT
        p.color = L.SAND
        c.drawText("차", ox + s * L.COL_L, oy + s * L.ROW_T, p)
        c.drawText("곡", ox + s * L.COL_R, oy + s * L.ROW_T, p)
        c.drawText("곡", ox + s * L.COL_R, oy + s * L.ROW_B, p)
        val b = ChaGlyph.box(p, ox + s * L.COL_L, oy + s * L.ROW_B)
        p.color = L.BLUE
        ChaGlyph.draw(c, p, b[0], b[1], b[2], b[3])
    }
}

/** 화면 위 브랜드 줄: 마크 + 이름 */
internal fun MainActivity.brand(size: Int = 30): View = kit.row(10).apply {
    addView(BrandMark(context, ui), kit.lp(ui.dp(size), ui.dp(size)))
    addView(kit.text("차곡車곡", Type.Title))
}
