// 차곡車곡 – 달리는 동안
// 운전자가 볼 것은 세 가지뿐: 다음 회전, 방금 들려준 한마디, 길 위의 지금 위치.
// 누르는 것은 '천천히'와 '내비' 둘, 그것도 멈췄을 때만(도로교통법 제49조). 나머지는 말로 알려 준다. 글은 3줄을 넘기지 않는다.
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
    var beltBtn: View? = null
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
    fun clear() { status = null; turnIcon = null; turnText = null; turnSub = null; turnCard = null; tip = null; tipWho = null; trace = null; nextTip = null; limit = null; limitBox = null; ahead = null; turnBar = null; tipCard = null; tipNow = null; stats = null; statsSub = null; limitCard = null; statsCard = null }
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
        addView(k.button("내비", Kit.Btn.Secondary, "navi") { showNavi(null) })
    }

    // 다음 회전 (내비)
    V.turnIcon = IconView(this, "turnRight", Ink.text)
    V.turnText = k.text("", if (ui.narrow) Type.Large else Type.Display)
    V.turnSub = k.text("", Type.Body, Ink.text2)
    V.turnBar = Bar(this, ui)
    val turnCard = k.card(26) {
        addView(k.row(22).apply {
            addView(V.turnIcon, k.lp(ui.dp(84), ui.dp(84)))
            addView(k.col(4).apply { addView(V.turnText); addView(V.turnSub) }, k.lp(0, k.WRAP, 1f))
            // 가상 콘솔에는 안전벨트 조작이 없다: 시연판에서는 눌러서 넘어간다 (실제 신호가 아니므로 받은 신호에 세지 않는다)
            V.beltBtn = if (BuildConfig.DEMOS) k.button("안전벨트 맸어요 (시연)", Kit.Btn.Primary, "check") { trip.beltSkip = true; refreshDrive() } else null
            if (!ui.narrow) V.beltBtn?.let { addView(it) }
        })
        // 좁은 창에서는 글자 옆에 단추를 둘 자리가 없어 아래 줄에 둔다
        if (ui.narrow) V.beltBtn?.let { addView(it, k.lp(k.MATCH, k.WRAP).apply { topMargin = ui.dp(12) }) }
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
        addView(k.text(if (g != null) "지난번에 지나온 길" else "오늘 가는 길", Type.Sub, Ink.text2))
        if (V.trace != null) addView(V.trace, if (ui.wide) k.lp(k.MATCH, 0, 1f) else k.lp(k.MATCH, ui.dp(96)))
        else addView(k.col(8).apply {
            gravity = Gravity.CENTER
            addView(k.icon("road", Ink.text3, 44))
            addView(k.text("주차하면 오늘 지나온 길이 일기에 그려져요", Type.Sub, Ink.text2).apply { gravity = Gravity.CENTER })
        }, if (ui.wide) k.lp(k.MATCH, 0, 1f) else k.lp(k.MATCH, k.WRAP))
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
    // 달리는 동안은 말로: "차곡차곡, 천천히 갈래" (Gleo AI → GleoReceiver TAKE_IT_SLOW)

    // 기어 신호를 못 받는 차에서만 수동 종료를 보여 준다
    // 멈춰 있을 때는 언제나 "기록 끝내기"를 둔다 (기어 신호가 없거나 P로 바뀌지 않아도 빠져나올 수 있게)
    // 단추는 달리는 중에도 잠그지 않는다. 대신 달리는 동안에는 창을 띄우지 않는다: 한 번 누르면 말로만 답한다.
    // 기록은 기어를 P에 놓고 완전히 멈추면 저절로 끝난다. 달리는 중에 '기록 끝내기'를 누르면 그렇게 알려 준다.
    val stop = k.button("기록 끝내기", Kit.Btn.Danger, "stop") {
        if (rolling) speak("기어를 P에 놓고 멈추면 기록을 끝낼게요.")
        else sheets.confirm("기록을 끝낼까요?", "지금까지 달린 것으로 일기를 써요.", "끝내기") { trip.end() }
    }

    // 달릴 때와 멈췄을 때 화면은 같다.
    val view = if (ui.wide) k.row(20).apply {
        gravity = Gravity.TOP
        // 멈춰 있을 때: 두 칸이 화면 높이를 채운다. 맨 아랫줄에는 양쪽 단추가 같은 높이로 나란히 놓인다
        val tall = ui.touch(80)
        addView(k.col(18).apply {
            addView(top); convoyCard()?.let { addView(it) }
            addView(turnCard.apply { gravity = Gravity.CENTER_VERTICAL }, k.lp(k.MATCH, 0, 1.2f))
            addView(tipCard.apply { gravity = Gravity.CENTER_VERTICAL }, k.lp(k.MATCH, 0, 1f))
            // 글레오에게 말로 묻는 것과 같은 답 (멈춰 있을 때만 누를 수 있다)
            addView(k.button("지금까지 어땠어?", Kit.Btn.Tinted, "quote") { askCar(voiceOnly = rolling) }, k.lp(k.MATCH, tall))
        }, k.lp(0, k.MATCH, 1.5f))
        addView(k.col(18).apply {
            // 제한속도는 차의 드라이빙 뷰와 내비가 이미 보여 준다. 겹쳐 그리지 않는다
            addView(routeCard, k.lp(k.MATCH, 0, 1f))
            addView(statsCard.apply { gravity = Gravity.CENTER_VERTICAL; minimumHeight = ui.dp(180) })
            addView(stop, k.lp(k.MATCH, tall))
        }, k.lp(0, k.MATCH, 1f))
    } else k.col(14).apply {
        // 내비 옆 좁은 창: 위에서부터 지금 할 일 · 한마디 · 길 그림이 높이를 나눠 채우고, 맨 아래에 단추 두 개
        addView(top); convoyCard()?.let { addView(it) }
        addView(turnCard.apply { gravity = Gravity.CENTER_VERTICAL }, k.lp(k.MATCH, 0, 1.2f))
        addView(tipCard.apply { gravity = Gravity.CENTER_VERTICAL }, k.lp(k.MATCH, 0, 1f))
        addView(routeCard)
        addView(k.row(10).apply {
            addView(k.button("지금까지 어땠어?", Kit.Btn.Tinted, "quote") { askCar(voiceOnly = rolling) }, k.lp(0, ui.touch(76), 1f))
            addView(stop, k.lp(0, ui.touch(76), 1f))
        }, k.lp(k.MATCH, k.WRAP))
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
    // 기록 상태를 분명히: 차 신호가 끊기면 무엇만 적고 있는지 말한다
    val vehicleOk = Car.offline || !car.failures.containsKey("vehicle")
    V.status?.text = (if (trip.sim) "모의 주행 기록 중" else "기록 중") + (if (sec < 60) " · 방금 출발" else " · ${sec / 60}분") + (if (km > 0.05f) String.format(Locale.KOREA, " · %.1fkm", km) else "") +   // 초가 계속 움직이지 않게 분 단위
        // 멈춰 있을 때는 차가 지금까지 적은 조작을 보여 준다 (조작이 실제로 적히고 있는지 눈으로 확인할 수 있게)
        (if (!rolling) listOfNotNull(trip.recorder.brakesNow().takeIf { it > 0 }?.let { "브레이크 ${it}번" }, trip.recorder.signalsNow().takeIf { it > 0 }?.let { "깜빡이 ${it}번" }).joinToString("") { " · $it" } else "") +
        (if (!vehicleOk) " · 차량 신호가 없어 시간과 위치만 적어요" else if (!car.naviConnected && !trip.sim) " · 내비 연결 안 됨" else "")

    // 다음 회전
    val side = when (car.turn) { "LEFT" -> "좌회전"; "RIGHT" -> "우회전"; "UTURN" -> "유턴"; else -> null }
    val prompt = trip.prompt
    // 깜빡이 안내와 도착 알림을 가리지 않는다. 모의 주행에서는 처음 20초만 보인다
    val beltNag = car.beltKnown && !car.belt && !trip.beltSkip && prompt == null && !trip.arrived && (sec < 20 || (BuildConfig.DEMOS && !rolling && !trip.sim))
    V.beltBtn?.visibility = if (beltNag) View.VISIBLE else View.GONE
    when {
        beltNag -> { V.turnIcon?.set("seat", Ink.warn); V.turnText?.text = "안전벨트를 매 주세요"; V.turnSub?.text = if (BuildConfig.DEMOS) "매면 사라져요. 가상 콘솔에서는 오른쪽 단추로 넘어가요" else "매면 이 안내는 사라져요" }
        prompt != null -> { V.turnIcon?.set(if (prompt.startsWith("왼")) "turnLeft" else "turnRight", Ink.signal); V.turnText?.text = "지금 $prompt"; V.turnSub?.text = "돌기 전에 미리" }
        // 길 안내는 내비의 몫이다 (Pleos Single Route Policy). 여기서는 거리·방향을 다시 말하지 않고 깜빡이 코칭만 한다.
        side != null && car.turnMeters in 1..300 -> { V.turnIcon?.set(when (car.turn) { "LEFT" -> "turnLeft"; "UTURN" -> "uturn"; else -> "turnRight" }, Ink.text); V.turnText?.text = "곧 돌아요"; V.turnSub?.text = "깜빡이를 미리 켜 두면 편해요" }
        trip.arrived -> { V.turnIcon?.set("flag", Ink.go); V.turnText?.text = "도착했어요"; V.turnSub?.text = "기어를 P에 놓으면 일기를 써요" }
        else -> { V.turnIcon?.set("road", Ink.text3); V.turnText?.text = "기록하고 있어요"; V.turnSub?.text = when {
            g != null && g.tips.any { it.placed } && store.locationOn && g.tips.indices.any { it !in trip.heard } -> "남긴 한마디는 그 근처에서 들려줄게요"
            car.routeStarted -> "길은 내비가 안내하고, 저는 운전을 적어요"
            else -> "궁금하면 “차곡차곡, 지금까지 어땠어?”라고 물어보세요" } }
    }

    // 회전까지 남은 거리 막대 (300m부터 줄어든다), 깜빡이 안내 중이면 신호 색
    val meters = if (side != null) car.turnMeters else 0
    V.turnBar?.visibility = View.GONE   // 거리 막대는 내비와 겹치는 길 안내라 쓰지 않는다
    // 일이 없을 때는 조용히
    V.turnText?.setTextColor(if (prompt == null && side == null && !trip.arrived && !beltNag) Ink.text2 else Ink.text)

    // 한마디
    V.tipWho?.text = when {
        g == null -> "혼자 가는 길"
        trip.currentTip != null -> (if (g.owner == store.driver) "내" else Writer.poss(g.owner)) + " 한마디"
        g.owner == store.driver -> "지난번에 다녀온 길"
        else -> "${Writer.poss(g.owner)} 길"
    }
    V.tip?.text = limits.fit(trip.currentTip ?: when {
        g == null -> "오늘 주행을 조용히 적고 있어요."
        g.tips.any { it.placed } && store.locationOn -> "남긴 곳 근처에 가면 들려줄게요."
        g.tips.isNotEmpty() -> "출발 전에 들은 한마디를 기억해 둬요."
        else -> "같은 길을 달리고 있어요."
    })

    // 한마디가 나오는 동안만 밝게
    val speaking = trip.currentTip != null
    (V.tipCard?.background as? GradientDrawable)?.setColor(if (speaking) Ink.accentSoft else Ink.surface)
    V.tipNow?.visibility = if (speaking) View.VISIBLE else View.GONE
    // 달리는 동안은 한마디가 나오는 때만 한마디 카드를 보여 준다 (평소에는 위 카드 하나)
    V.tipCard?.visibility = View.VISIBLE

    // 길 위의 위치
    if (g != null) {
        val driven = trip.drivenKm
        val pr = if (g.drive.distKm > 0.05f && driven > 0.05f) driven / g.drive.distKm else null
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
    V.stats?.text = if (km > 0.05f) String.format(Locale.KOREA, "%.1fkm", km) else if (sec < 60) "방금 출발" else "${sec / 60}분"
    V.statsSub?.text = listOfNotNull(if (km > 0.05f && sec >= 60) "${sec / 60}분" else null, trip.recorder.signalsNow().takeIf { it > 0 }?.let { "깜빡이 ${it}번" }).joinToString(" · ").ifEmpty { "조용히 적고 있어요" }
}
