// 차곡車곡 – 달리는 동안
// 운전자가 볼 것은 세 가지뿐: 다음 회전, 방금 들려준 한마디, 길 위의 지금 위치.
// 누르는 것은 '숨 고르기'와 '내비' 둘, 그것도 멈췄을 때만(도로교통법 제49조). 나머지는 말로 알려 준다. 글은 3줄을 넘기지 않는다.
// 한눈(2초 안)에 읽히게: 지금 할 일 하나만 크게, 일이 없을 때는 화면이 조용해진다.
// 회전까지 남은 거리는 줄어드는 막대로, 한마디가 나오는 동안만 한마디 칸이 밝아진다.
package com.example.chagokchagok

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale

/** 1초마다 바뀌는 글자들 (화면 전체를 다시 그리지 않으려고 붙잡아 둔다) */
internal object DriveViews {
    var status: TextView? = null
    var turnIcon: IconView? = null
    var turnText: TextView? = null
    var turnSub: TextView? = null
    var turnCard: View? = null
    var tip: TextView? = null
    var tipWho: TextView? = null
    var trace: TraceView? = null
    var nextTip: TextView? = null
    var limit: TextView? = null
    var limitBox: View? = null
    var ahead: TextView? = null
    var turnBar: Bar? = null
    var tipCard: View? = null
    var tipNow: View? = null
    var stats: TextView? = null
    var statsSub: TextView? = null
    var limitCard: View? = null
    var statsCard: View? = null
}

/** 줄어드는 막대 (회전까지 남은 거리) */
internal class Bar(ctx: android.content.Context, private val ui: Ui) : View(ctx) {
    var value = 0f; set(v) { field = v; invalidate() }
    var color = Ink.accent
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: android.graphics.Canvas) {
        val r = height / 2f
        p.color = Ink.fill; c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, p)
        if (value > 0f) { p.color = color; c.drawRoundRect(0f, 0f, width * value.coerceIn(0f, 1f), height.toFloat(), r, r, p) }
    }
}

internal fun MainActivity.driveScreen(): View {
    val k = kit; val V = DriveViews
    val g = trip.guide
    val pad = ui.dp(if (ui.wide) 36 else 20)

    if (!trip.active) return k.col(18).apply {
        gravity = Gravity.CENTER; setBackgroundColor(Ink.bg); setPadding(pad, pad, pad, pad)
        addView(k.icon("steering", Ink.text2, 64))
        addView(k.text("주행 중이에요", Type.Large).apply { gravity = Gravity.CENTER })
        addView(k.text("앞을 봐 주세요. 주차하면 다시 열려요.", Type.Body, Ink.text2).apply { gravity = Gravity.CENTER })
    }

    // 맨 위: 기록 중 표시
    val top = k.row(12).apply {
        addView(View(context).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ink.danger) } }, k.lp(ui.dp(12), ui.dp(12)))
        V.status = k.text("", Type.Sub, Ink.text2); addView(V.status, k.lp(0, k.WRAP, 1f))
        if (trip.sim) addView(k.tag("모의 주행 · 시연용", Ink.warn))
        // Pleos 내비를 앞으로 (NaviHelper requestNavigationScreen). 분할 화면이면 옆 창에 뜬다.
        addView(k.gap(8))
        addView(k.button("내비", Kit.Btn.Secondary, "navi", enabled = !limits.restricted) { if (!car.openNavi()) say("내비가 연결되지 않았어요.") })
    }

    // 다음 회전 (내비)
    V.turnIcon = IconView(this, "turnRight", Ink.text)
    V.turnText = k.text("", Type.Display)
    V.turnSub = k.text("", Type.Body, Ink.text2)
    V.turnBar = Bar(this, ui)
    val turnCard = k.card(26) {
        addView(k.row(22).apply {
            addView(V.turnIcon, k.lp(ui.dp(84), ui.dp(84)))
            addView(k.col(4).apply { addView(V.turnText); addView(V.turnSub) }, k.lp(0, k.WRAP, 1f))
        })
        addView(k.gap(16))
        addView(V.turnBar, k.lp(k.MATCH, ui.dp(8)))
    }
    V.turnCard = turnCard

    // 한마디
    V.tipWho = k.text("", Type.Sub, Ink.accent)
    V.tip = k.voice("", if (ui.wide) 32f else 26f).apply { maxLines = 3 }
    V.tipNow = k.tag("지금", Ink.accent)
    val tipCard = k.card(26) {
        addView(k.row(10).apply { if (g != null) addView(k.avatar(g.owner, 30)); addView(V.tipWho, k.lp(0, k.WRAP, 1f)); addView(V.tipNow) })
        addView(k.gap(10)); addView(V.tip)
    }
    V.tipCard = tipCard

    // 길 위의 지금 위치
    V.trace = if (g != null) TraceView(this, ui).apply { lanes = listOf(Lane(g.owner, g.drive, g.tips, faded = true)) } else null
    V.nextTip = k.text("", Type.Sub, Ink.text2)
    val routeCard = k.card(22) {
        V.trace?.let { addView(it, k.lp(k.MATCH, ui.dp(96))) }
        addView(V.nextTip)
    }

    // 제한속도, 앞길
    V.limit = TextView(this).apply {
        gravity = Gravity.CENTER; typeface = ui.bold; textSize = ui.sp(30f); setTextColor(Color.parseColor("#1D1D1F"))
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.WHITE); setStroke(ui.dp(9), Color.parseColor("#E5302A")) }
    }
    V.ahead = k.text("", Type.Sub, Ink.text2)
    val limitCard = k.card(22) {
        gravity = Gravity.CENTER_HORIZONTAL
        V.limitBox = FrameLayout(context).apply { addView(V.limit, FrameLayout.LayoutParams(ui.dp(96), ui.dp(96), Gravity.CENTER)) }
        addView(V.limitBox, k.lp(k.MATCH, ui.dp(104)))
        addView(V.ahead.apply { gravity = Gravity.CENTER })
    }
    V.limitCard = limitCard
    // 제한속도를 모를 때는 빈 표지판 대신 지금까지의 기록을 크게
    V.stats = k.text("", Type.Display)
    V.statsSub = k.text("", Type.Sub, Ink.text2)
    val statsCard = k.card(24) { addView(k.text("지금까지", Type.Caption, Ink.text2)); addView(V.stats); addView(V.statsSub) }
    V.statsCard = statsCard
    // 도로교통법 제49조 제1항 제11호의2: 운전 중 화면 조작은 정지 중에만. 차가 주행 제한을 걸면(움직이는 중) 버튼을 잠근다.
    val moving = limits.restricted
    // 달리는 동안은 말로: "차곡차곡, 숨 고를래" (Gleo AI → GleoReceiver TAKE_A_BREATH)
    val breathe = k.button(if (moving) "말로: 차곡차곡, 숨 고를래" else "숨 고르기", Kit.Btn.Secondary, "breath", enabled = !moving) { trip.breathe() }
    // 기어 신호를 못 받는 차에서만 수동 종료를 보여 준다
    val stop = if (car.failures.containsKey(Evt.GEAR)) k.button("기록 멈추기", Kit.Btn.Danger, "stop") { trip.end() } else null

    val view = if (ui.wide) k.row(20).apply {
        gravity = Gravity.TOP
        addView(k.col(18).apply { addView(top); convoyCard()?.let { addView(it) }; addView(turnCard); addView(tipCard); addView(routeCard) }, k.lp(0, k.MATCH, 1.7f))
        addView(k.col(18).apply {
            addView(limitCard, k.lp(k.MATCH, k.WRAP)); addView(statsCard, k.lp(k.MATCH, k.WRAP)); addView(k.fill())
            addView(breathe, k.lp(k.MATCH, ui.dp(84)))
            stop?.let { addView(it, k.lp(k.MATCH, k.WRAP)) }
        }, k.lp(0, k.MATCH, 1f))
    } else k.col(14).apply {
        addView(top); convoyCard()?.let { addView(it) }; addView(turnCard); addView(tipCard); addView(routeCard)
        addView(k.row(14).apply { addView(limitCard, k.lp(0, k.WRAP, 1f)); addView(statsCard, k.lp(0, k.WRAP, 1f)); addView(breathe, k.lp(0, ui.dp(84), 1.2f)) })
        stop?.let { addView(it, k.lp(k.MATCH, k.WRAP)) }
    }
    return FrameLayout(this).apply {
        setBackgroundColor(Ink.bg); setPadding(pad, pad, pad, pad)
        addView(view, FrameLayout.LayoutParams(-1, -1))
        post { refreshDrive() }
    }
}

internal fun MainActivity.refreshDrive() {
    val V = DriveViews; val g = trip.guide
    val sec = trip.recorder.secondsNow(); val km = trip.recorder.kmNow()
    V.status?.text = "기록 중 · ${sec / 60}:${String.format(Locale.US, "%02d", sec % 60)}" + if (km > 0.05f) String.format(Locale.KOREA, " · %.1fkm", km) else ""

    // 다음 회전
    val side = when (car.turn) { "LEFT" -> "좌회전"; "RIGHT" -> "우회전"; "UTURN" -> "유턴"; else -> null }
    val prompt = trip.prompt
    when {
        prompt != null -> { V.turnIcon?.set(if (prompt.startsWith("왼")) "turnLeft" else "turnRight", Ink.signal); V.turnText?.text = "지금 $prompt"; V.turnSub?.text = if (side != null) "${car.turnMeters}m 앞 $side" else "" }
        side != null && car.turnMeters > 0 -> { V.turnIcon?.set(when (car.turn) { "LEFT" -> "turnLeft"; "UTURN" -> "uturn"; else -> "turnRight" }, Ink.text); V.turnText?.text = "${car.turnMeters}m 앞 $side"; V.turnSub?.text = "내비 안내" }
        trip.arrived -> { V.turnIcon?.set("flag", Ink.go); V.turnText?.text = "도착했어요"; V.turnSub?.text = "기어를 P에 놓으면 일기를 써요" }
        else -> { V.turnIcon?.set("road", Ink.text3); V.turnText?.text = "이대로 쭉"; V.turnSub?.text = if (trip.routeSent) "내비 안내를 따라가요" else "조용히 적고 있어요" }
    }

    // 회전까지 남은 거리 막대 (300m부터 줄어든다), 깜빡이 안내 중이면 신호 색
    val meters = if (side != null) car.turnMeters else 0
    V.turnBar?.apply { color = if (prompt != null) Ink.signal else Ink.accent; value = if (meters in 1..300) 1f - meters / 300f else 0f; visibility = if (meters in 1..300) View.VISIBLE else View.INVISIBLE }
    // 일이 없을 때는 조용히
    V.turnText?.setTextColor(if (prompt == null && side == null && !trip.arrived) Ink.text2 else Ink.text)

    // 한마디
    V.tipWho?.text = when {
        g == null -> "혼자 가는 길"
        trip.currentTip != null -> "${Writer.poss(g.owner)} 한마디"
        else -> "${Writer.poss(g.owner)} 길"
    }
    V.tip?.text = limits.fit(trip.currentTip ?: when {
        g == null -> "오늘 주행을 조용히 적고 있어요."
        g.tips.any { it.placed } && store.locationOn -> "남긴 자리에 가까워지면 들려줄게요."
        g.tips.isNotEmpty() -> "출발 전에 들은 한마디를 기억해 둬요."
        else -> "같은 길을 달리고 있어요."
    })

    // 한마디가 나오는 동안만 밝게
    val speaking = trip.currentTip != null
    (V.tipCard?.background as? GradientDrawable)?.setColor(if (speaking) Ink.accentSoft else Ink.surface)
    V.tipNow?.visibility = if (speaking) View.VISIBLE else View.GONE

    // 길 위의 위치
    if (g != null) {
        val pr = if (g.drive.distKm > 0.05f && km > 0.05f) km / g.drive.distKm else null
        V.trace?.marker = pr?.coerceIn(0f, 1f)
    }
    val left = g?.tips?.indices?.count { it !in trip.heard } ?: 0
    V.nextTip?.text = when {
        g == null -> "받은 길 없이 혼자 가는 길이에요"
        trip.nextTipMeters != null && left > 0 -> "다음 한마디까지 ${trip.nextTipMeters}m"
        left == 0 && g.tips.isNotEmpty() -> "한마디를 모두 들었어요"
        else -> "남은 한마디 ${left}개"
    }

    // 제한속도, 앞길
    V.limit?.text = if (car.speedLimit > 0) "${car.speedLimit}" else "–"
    V.limitBox?.alpha = if (car.speedLimit > 0) 1f else .35f
    val ahead = mutableListOf<String>()
    if (car.cameraMeters > 0) ahead.add((if (car.schoolZone) "어린이 보호구역 " else "단속 ") + "${car.cameraMeters}m")
    if (car.cautionMeters > 0) ahead.add("주의 구간 ${car.cautionMeters}m")
    refreshConvoy()
    V.ahead?.text = ahead.joinToString("\n").ifEmpty { "지금 길 제한속도" }
    val known = car.speedLimit > 0 || ahead.isNotEmpty()
    V.limitCard?.visibility = if (known) View.VISIBLE else View.GONE
    V.statsCard?.visibility = if (known && ui.narrow) View.GONE else View.VISIBLE
    V.stats?.text = if (km > 0.05f) String.format(Locale.KOREA, "%.1fkm", km) else "${sec / 60}분"
    V.statsSub?.text = listOfNotNull(if (km > 0.05f) "${sec / 60}분" else null, trip.recorder.signalsNow().takeIf { it > 0 }?.let { "깜빡이 ${it}번" }).joinToString(" · ").ifEmpty { "조용히 적고 있어요" }
}
