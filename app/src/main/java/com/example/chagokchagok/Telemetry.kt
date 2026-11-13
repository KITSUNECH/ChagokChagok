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

/** 브랜드 마크: 정사각형 안에 차곡 / 車곡 네 글자. 車만 주황(차에서 시작한다는 뜻) */
internal class BrandMark(ctx: android.content.Context, private val ui: Ui) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val s = minOf(width, height).toFloat()
        val ox = (width - s) / 2; val oy = (height - s) / 2
        p.style = Paint.Style.FILL; p.color = android.graphics.Color.parseColor("#1D1D1F")
        c.drawRoundRect(ox, oy, ox + s, oy + s, s * .22f, s * .22f, p)
        p.typeface = ui.bold; p.textAlign = Paint.Align.CENTER; p.textSize = s * .34f
        val cells = listOf("차" to 0, "곡" to 1, "車" to 2, "곡" to 3)
        cells.forEach { (ch, i) ->
            val cx = ox + s * (if (i % 2 == 0) .30f else .70f); val cy = oy + s * (if (i < 2) .42f else .82f)
            p.color = if (ch == "車") android.graphics.Color.parseColor("#EB6834") else android.graphics.Color.WHITE
            c.drawText(ch, cx, cy, p)
        }
    }
}

/** 화면 위 브랜드 줄: 마크 + 이름 */
internal fun MainActivity.brand(size: Int = 30): View = kit.row(10).apply {
    addView(BrandMark(context, ui), kit.lp(ui.dp(size), ui.dp(size)))
    addView(kit.text("차곡車곡", Type.Title))
}
