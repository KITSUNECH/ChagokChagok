// 차곡車곡 – 오늘 · 출발 준비 · 출발 전
package com.example.chagokchagok

import android.graphics.Color
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 화면 사이에서 잠깐 들고 있는 값 */
internal object Session {
    var forecast: Forecast? = null
    /** 같이 가기 상대 (시연용) */
    var convoy: Friend? = null
}

internal fun MainActivity.facts(d: Drive): String {
    val km = if (d.distKm > 0.05f) String.format(Locale.KOREA, "%.1fkm", d.distKm) else null
    val time = if (d.minutes >= 1) "${d.minutes}분" else "${d.seconds}초"
    return listOfNotNull(km, time).joinToString(" · ")
}

internal fun MainActivity.dateText(t: Long, pattern: String = "M월 d일 EEEE") = SimpleDateFormat(pattern, Locale.KOREA).format(Date(t))

/** 지금 운전자가 받은 길 중 오늘 보여 줄 하나: 고른 것 → 가족의 아직 졸업 안 한 길 → 아무거나 */
internal fun MainActivity.todaysGuide(): Guide? {
    val d = store.driver; val gs = store.guidesFor(d).filter { it.kind != "추천" }
    return gs.firstOrNull { it.id == chosenGuide }
        ?: gs.lastOrNull { it.kind == "길" && store.relation(it.owner) == "가족" && !store.graduated(d, it.id) }
        ?: gs.lastOrNull { !store.graduated(d, it.id) } ?: gs.lastOrNull()
}

// ═══════════════ 오늘 ═══════════════

internal fun MainActivity.todayScreen(): View {
    val k = kit; val d = store.driver
    car.refreshNavi()
    val guide = todaysGuide()
    val head = k.header(if (d == "나") "안녕하세요" else "$d 님, 안녕하세요", "${dateText(System.currentTimeMillis())} · 오늘 주행도 차가 적어 둘게요.",
        trailing = if (ui.narrow) k.avatar(d, 44).apply { setOnClickListener { switchDriver() } } else null)
    // 오른쪽: 지금 할 일만 (경고 → 돌아볼 일기 → 요즘 신경 쓰이는 것)
    val side = listOfNotNull(carCard(), reviewCard(), focusCard())
    return k.col().apply {
        addView(head); addView(k.gap(if (ui.wide) 20 else 16))
        if (ui.wide) addView(k.row(20).apply {
            gravity = Gravity.TOP
            addView(k.col(16).apply { addView(startCard(null), k.lp(k.MATCH, k.WRAP)); addView(againCard(guide), k.lp(k.MATCH, k.WRAP)) }, k.lp(0, k.WRAP, 1.4f))
            addView(k.col(16).apply { side.forEach { addView(it, k.lp(k.MATCH, k.WRAP)) } }, k.lp(0, k.WRAP, 1f))
        }) else addView(k.col(16).apply { addView(startCard(null)); addView(againCard(guide)); side.forEach { addView(it, k.lp(k.MATCH, k.WRAP)) } })
    }
}

/**
 * 다시 갈 길: 내가 다녀온 곳(같은 길을 다시 달리면 지난번과 견준다)과 건네받은 길을 한 목록에.
 * 아직 다녀온 곳이 없으면 앱이 하는 일을 네 줄로 보여 준다.
 */
internal fun MainActivity.againCard(guide: Guide?): View {
    val k = kit; val me = store.driver
    val dest = car.destName?.takeIf { it.isNotBlank() }
    val places = store.diariesOf(me).filter { it.ownTrip() && it.drive.place != null && !it.drive.sim && it.drive.placeLat != null && it.drive.completed }
        .groupBy { it.drive.place!! }.entries.sortedByDescending { e -> e.value.last().date }.take(if (guide != null) 2 else 3)
    val rows = places.map { (place, ds) ->
        val note = store.selfGuide(me, place)?.tips?.firstOrNull()?.text
        k.item(place, (if (note != null) "지난번 한마디 “$note”" else "지난번 ${dateText(ds.last().date, "M월 d일")}") + " · ${ds.size}번 다녀옴", if (note != null) "quote" else "pin",
            trailing = if (ds.size >= 2) k.iconButton("chart", Ink.accent, Ink.accentSoft, 48) { go(MainActivity.ROAD, ds.last().id) } else k.icon("chevron", Ink.text3, 20)) { goAgain(place, ds.last()) }
    } + listOfNotNull(guide?.let { g -> k.item(g.title, "${Writer.subj(g.owner)} 건네준 길 · 한마디 ${g.tips.size}개" + if (g.sample) " · 예시" else "", "road", chevron = true) { depart(g) } })
    return k.card(24) {
        if (rows.isNotEmpty()) {
            addView(k.text("다시 갈 길", Type.Headline))
            addView(k.gap(4))
            addView(k.text("같은 길을 다시 달리면 지난번의 나와 견줘 줘요.", Type.Sub, Ink.text2))
            addView(k.gap(12))
            addView(k.groupOf(rows).apply { background = k.rounded(Ink.fill, 18) })
        } else {
            addView(k.text("이렇게 써요", Type.Headline))
            addView(k.gap(6))
            LOOP_STEPS.forEach { (ic, t, s) ->
                addView(k.row(14).apply {
                    setPadding(0, ui.dp(7), 0, ui.dp(7))
                    addView(k.icon(ic, Ink.accent, 24))
                    addView(k.text(t, Type.BodyStrong), k.lp(ui.dp(if (ui.wide) 130 else 108), k.WRAP))
                    addView(k.text(s, Type.Sub, Ink.text2), k.lp(0, k.WRAP, 1f))
                })
            }
        }
        addView(k.gap(12))
        // 새로운 곳: 실제 Pleos 내비의 화면을 연다 (NaviHelper requestNavigationScreen)
        addView(k.row(12).apply {
            addView(k.icon("pin", if (dest != null) Ink.accent else Ink.text3, 22))
            addView(k.text(dest?.let { "내비 목적지 · $it" } ?: "새로운 곳은 내비에 목적지를 넣고 출발해요", Type.Sub, if (dest != null) Ink.text else Ink.text2, lines = 2), k.lp(0, k.WRAP, 1f))
            if (dest == null) addView(k.button("내비 열기", Kit.Btn.Tinted, "navi") { if (!car.openNavi()) toast("내비가 연결되지 않았어요. 내비 앱에서 목적지를 골라 주세요.") })
        })
    }
}

/** 성장 바의 다음 한 칸: 무엇을 하면 한 칸이 쌓이는지 */
private fun MainActivity.nextBlockCard(): View? {
    val k = kit
    val bs = blocks(); val next = bs.firstOrNull { !it.done } ?: return null
    val stageBlocks = bs.filter { it.stage == next.stage }
    val col = stageColor(next.stage)
    return k.card(pad = 22, onClick = { stackSheet() }) {
        gravity = Gravity.CENTER_VERTICAL   // 남는 높이를 채울 때 내용은 가운데
        addView(k.row(16).apply {
            // 쌓일 칸 미리보기: 단계 색 테두리의 빈 블록
            addView(android.widget.FrameLayout(context).apply {
                background = k.rounded(Ink.alpha(col, .10f), 10, 2, col)
                addView(IconView(context, "plus", col), android.widget.FrameLayout.LayoutParams(ui.dp(20), ui.dp(20), Gravity.CENTER))
            }, k.lp(ui.dp(52), ui.dp(38)))
            addView(k.col(3).apply {
                addView(k.text("다음 한 칸 · ${STAGES[next.stage]} ${stageBlocks.count { it.done }}/${stageBlocks.size}", Type.Caption, col))
                addView(k.text(next.title, Type.Headline))
                addView(k.text(next.hint, Type.Sub, Ink.text2))
            }, k.lp(0, k.WRAP, 1f))
        })
    }
}

/** 면허 딴 해 이후 바뀐 법규 (없으면 꼭 알아 둘 것). 한 번 열어 보면 접는다. */
private fun MainActivity.lawCard(): View? {
    val d = store.driver
    if (store.flag("law_$d")) return null
    val year = store.licenseYear(d)
    val since = Laws.since(year)
    val type = store.typeOf(d)
    if (since.isEmpty() && type !in listOf("초보", "오랜만")) return null
    val k = kit
    return k.card(pad = 22, onClick = { store.setFlag("law_$d"); go(MainActivity.LEARN, "law") }) {
        addView(k.row(14).apply {
            addView(k.icon("scale", Ink.accent, 28))
            addView(k.col(3).apply {
                addView(k.text(if (since.isEmpty()) "꼭 알아 둘 법규" else "바뀐 법규 ${since.size}가지", Type.Headline))
                addView(k.text(if (since.isEmpty()) "초보가 많이 놓치는 ${Laws.core.size}가지" else (year?.let { "${it}년 면허 뒤로 · " } ?: "면허 딴 뒤로 · ") + since.last().title, Type.Sub, Ink.text2, lines = 2))
            }, k.lp(0, k.WRAP, 1f))
            addView(k.icon("chevron", Ink.text3, 20))
        })
    }
}

/** 이 차의 기어 한 줄 (알면) */
internal fun MainActivity.shifterLine(): String? = Shifters.of(this)?.let { "이 차는 ${it.name} · ${it.park}" }

/** 오늘의 길: 가족·친구가 건네준 길 하나 */
private fun MainActivity.heroCard(): View {
    val k = kit; val d = store.driver
    val g = todaysGuide()
    val others = store.guidesFor(d).filter { it.kind != "추천" }
    if (g == null) return k.card {
        addView(k.empty("road", "아직 건네받은 길이 없어요",
            "가족이나 친구가 이 차로 평소 길을 달린 뒤 ‘이 길 건네주기’를 누르면 여기에 나타나요. 기다리지 않아도 돼요. 기어를 D에 놓으면 오늘 주행도 바로 일기가 돼요.",
            *(if (BuildConfig.DEMOS) arrayOf(k.button("예시로 둘러보기", Kit.Btn.Tinted, "sparkle") { store.addSampleGuide(); store.addSampleFriend(); render() }) else emptyArray()),
            k.button("기록 시작", Kit.Btn.Primary, "play") { beginTrip() }))
    }
    val followed = store.timesFollowed(d, g.id)
    val placed = g.tips.count { it.placed }
    return k.card(28) {
        addView(k.row(10).apply {
            addView(k.avatar(g.owner, 30))
            addView(k.text("${Writer.subj(g.owner)} 건네준 ${if (g.kind == "연습") "연습" else "길"}", Type.Sub, Ink.text2))
            if (g.sample) addView(k.tag("예시", Ink.text2))
            addView(k.fill())
            if (others.size > 1) addView(k.text("받은 길 ${others.size}개", Type.Sub, Ink.accent).apply { setOnClickListener { pickGuide() } })
        })
        addView(k.gap(14))
        addView(k.text(g.title, Type.Large))
        addView(k.gap(8))
        val meta = mutableListOf(facts(g.drive), "한마디 ${g.tips.size}개")
        if (g.kind == "길") meta.add(if (store.graduated(d, g.id)) "졸업한 길" else "따라 달린 횟수 $followed/$GRADUATE")
        addView(k.text(meta.joinToString("  ·  "), Type.Sub, Ink.text2))
        addView(k.gap(18))
        addView(TraceView(this@heroCard, ui).apply { lanes = listOf(Lane(g.owner, g.drive, g.tips)) }, k.lp(k.MATCH, ui.dp(92)))
        g.tips.firstOrNull()?.let {
            addView(k.gap(10))
            addView(k.row(12).apply {
                gravity = Gravity.TOP
                addView(k.icon("quote", Ink.accent, 22))
                addView(k.voice(it.text, 21f).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END }, k.lp(0, k.WRAP, 1f))
            })
        }
        addView(k.gap(18))
        addView(k.buttons(
            k.button("한마디 미리 듣기", Kit.Btn.Secondary, "speaker") {
                if (g.tips.isEmpty()) toast("이 길에는 아직 한마디가 없어요.")
                else readAloud("${Writer.poss(g.owner)} 한마디", g.tips.mapIndexed { i, t -> "${i + 1}. ${t.text}" }.joinToString("\n\n"))
            }
        ))
        if (placed > 0) { addView(k.gap(12)); addView(k.status("한마디 $placed 개는 남긴 곳 근처에 가면 들려줘요", Ink.go)) }
    }
}

internal fun MainActivity.pickGuide() {
    val d = store.driver
    val gs = store.guidesFor(d).filter { it.kind != "추천" }
    sheets.choose("받은 길", "오늘 달려 볼 길을 고르세요.", gs.map { g -> "${g.title} · ${g.owner}" + if (store.graduated(d, g.id)) " · 졸업" else "" },
        gs.map { if (it.kind == "연습") "steering" else "road" }, gs.indexOfFirst { it.id == todaysGuide()?.id }) { i -> chosenGuide = gs[i].id; render() }
}

/** 차 상태: 마지막에 탄 사람이 남긴 것, 켜진 경고등, 의자 */
private fun MainActivity.carCard(): View? {
    val k = kit; val d = store.driver
    val warns = car.lampsOn.mapNotNull { t -> Lamps.warn(t)?.let { t to it } }
    val h = store.handover()?.takeIf { it.from != d }
    val mine = store.seat(d); val now = car.seatPos
    val seatMoved = mine != null && now != null && mine != now
    if (warns.isEmpty() && h == null && !seatMoved) return null
    return k.card {
        addView(k.row(10).apply { addView(k.icon("car", Ink.text, 24)); addView(k.text("차 상태", Type.Headline)) })
        addView(k.gap(8))
        val rows = mutableListOf<View>()
        warns.forEach { (_, l) -> rows.add(k.item("${l.name} 표시등", if (l.level == "빨강") "출발 전에 확인해요" else "가까운 날에 점검해요", "warning", if (l.level == "빨강") Ink.danger else Ink.warn, chevron = true) { go(MainActivity.LEARN, "lamp") }) }
        if (h != null) {
            h.fuel?.let { rows.add(k.item("남은 연료", "${h.from} 님이 내릴 때", "fuel", if (it < 20) Ink.warn else Ink.accent, value = "$it%", valueColor = if (it < 20) Ink.warn else Ink.text2)) }
            if (h.washerLow) rows.add(k.item("워셔액 부족", "${h.from} 님이 내릴 때", "warning", Ink.warn))
            if (h.note.isNotEmpty()) rows.add(k.item("“${h.note}”", "${h.from} 님이 남긴 말", "quote"))
        }
        if (seatMoved) rows.add(k.item("의자가 옮겨졌어요", "지난번 내 자리와 달라요. 맞추고 출발해요.", "seat", Ink.warn))
        addView(k.groupOf(rows).apply { background = k.rounded(Ink.fill, 18) })
    }
}

/** 받은 소식과 응원 */
private fun MainActivity.newsCard(): View? {
    val k = kit; val d = store.driver
    val news = store.news(d).lastOrNull() ?: return null
    val from = news.substringBefore("\t", "")
    val body = news.substringAfter("\t")
    return k.card(color = Ink.accentSoft) {
        addView(k.row(10).apply {
            if (from.isNotEmpty()) addView(k.avatar(from, 30))
            addView(k.text(if (from.isNotEmpty()) "$from 님의 소식" else "소식", Type.Sub, Ink.accent), k.lp(0, k.WRAP, 1f))
            addView(k.iconButton("close", Ink.text2, Color.TRANSPARENT, 40) { store.clearNews(d); render() })
        })
        addView(k.gap(6))
        addView(k.voice(body, 20f))
        if (from.isNotEmpty()) {
            addView(k.gap(14))
            addView(k.button("응원 보내기", Kit.Btn.Primary, "heart") {
                val cheers = listOf("잘했어. 다음엔 같이 가자.", "수고했어. 조심히 다녀.", "첫 길 축하해!", "주차까지 해냈구나.")
                sheets.choose("$from 님에게 응원", null, cheers + "직접 쓰기") { i ->
                    fun send(t: String) { store.setFlag("cheered_$d"); store.addNews(from, "$d\t${Writer.poss(d)} 응원: “$t”"); store.clearNews(d); toast("응원을 보냈어요. $from 님이 출발할 때 차가 전해 줄 거예요."); render() }
                    if (i == cheers.size) sheets.input("응원 한마디", "예: 천천히 해도 괜찮아") { send(it) } else send(cheers[i])
                }
            })
        }
    }
}

/** 도장첩과 지난 일기 */
private fun MainActivity.stampsCard(diaries: List<Diary>): View {
    val k = kit
    val got = diaries.mapNotNull { it.stamp }.distinct()
    val empty = listOf("첫\n운전", "첫\n빗길", "첫\n밤길", "첫\n동승", "졸업", "첫\n새 길").filter { it !in got }
    return k.card {
        addView(k.row().apply {
            addView(k.text("도장", Type.Headline), k.lp(0, k.WRAP, 1f))
            addView(k.text("${got.size}개", Type.Sub, Ink.text2))
        })
        addView(k.gap(14))
        addView(k.hscroll(k.row(12).apply { (got.reversed() + empty).take(8).forEachIndexed { i, s -> addView(stamp(s, s in got, i)) } }))
        diaries.lastOrNull()?.let { last ->
            addView(k.gap(14))
            addView(k.item(last.title, "지난 일기 · ${dateText(last.date, "M월 d일")}", "book", chevron = true) { go(MainActivity.DIARY, last.id) }.apply { setPadding(0, ui.dp(8), 0, 0) })
        }
    }
}

internal fun MainActivity.stamp(text: String, on: Boolean, i: Int, size: Int = 70) = android.widget.TextView(this).apply {
    this.text = if (on) text else "?\n" + text.substringAfter("\n")
    textSize = ui.sp(size * .2f); gravity = Gravity.CENTER; typeface = ui.batang; setLineSpacing(0f, 1.0f)
    setTextColor(if (on) Ink.stamp else Ink.text3)
    background = android.graphics.drawable.GradientDrawable().apply {
        shape = android.graphics.drawable.GradientDrawable.OVAL
        if (on) setStroke(ui.dp(3), Ink.stamp) else setStroke(ui.dp(2), Ink.line, ui.dp(5).toFloat(), ui.dp(4).toFloat())
    }
    rotation = if (on) listOf(-8f, 5f, -3f, 9f)[i % 4] else 0f
    layoutParams = LinearLayout.LayoutParams(ui.dp(size), ui.dp(size))
}

/** 알아 두기: 운전자 유형에 따라 맨 위가 달라진다 */
private fun MainActivity.learnCard(): View {
    val k = kit; val t = store.typeOf(store.driver)
    val items = listOf(Triple("law", "그동안 바뀐 법규", "빨간불 우회전, 보호구역…"), Triple("newcar", "면허시험 차와 다른 곳", "기어, 주차 브레이크, 오토 홀드"), Triple("lamp", "경고등 색 읽는 법", "빨강 · 노랑 · 초록"))
    val first = when (t) { "오랜만" -> 0; "새 차", "초보" -> 1; else -> 2 }
    val ordered = listOf(items[first]) + items.filterIndexed { i, _ -> i != first }
    return k.card(pad = 20) {
        addView(k.text("알아 두기", Type.Headline).apply { setPadding(ui.dp(4), 0, 0, ui.dp(8)) })
        addView(k.groupOf(ordered.map { (key, title, sub) -> k.item(title, sub, when (key) { "law" -> "info"; "lamp" -> "warning"; else -> "steering" }, chevron = true) { go(MainActivity.LEARN, key) } }).apply { background = k.rounded(Ink.fill, 18) })
    }
}

// ═══════════════ 출발 준비 ═══════════════

internal val PREP_ITEMS = listOf(
    Triple("signal", "깜빡이", "왼쪽, 오른쪽 한 번씩"),
    Triple("wiper", "와이퍼", "한 번 켜 보기"),
    Triple("light", "전조등", "한 번 켜 보기"),
    Triple("gear", "기어 자리", "눌러서 그림으로 보기"),
    Triple("belt", "안전벨트", "매면 켜져요"),
)
/** 안전벨트는 에뮬레이터에서 체결 상태를 바꿀 수 없을 수 있어 마치는 조건에서 뺀다.
 *  기어는 실제로 R에 넣어 보게 하지 않는다 (앱 과제로 후진 기어를 넣게 할 이유가 없다). 그림으로 보면 확인된다. */
internal val PREP_REQUIRED = listOf("signal", "wiper", "light", "gear")

internal fun MainActivity.prepScreen(): View {
    val k = kit
    val done = PREP_REQUIRED.count { it in prepDone }
    val all = done == PREP_REQUIRED.size
    val next = PREP_REQUIRED.firstOrNull { it !in prepDone }
    val sh = Shifters.of(this)
    // 차 안에서 한눈에: 세 갈래로 나눠 한 화면씩 (손에 익히기 · 이 차의 기어 · 안전 조작)
    return k.col().apply {
        addView(k.header("출발 준비", "주차한 채로, 기어는 P에 둔 채로 해요.", onBack = { back() },
            trailing = k.button(if (all) "홈으로" else "나중에", if (all) Kit.Btn.Primary else Kit.Btn.Secondary) { back() }))
        addView(k.gap(18))
        addView(k.segmented(if (ui.wide) listOf("손에 익히기 $done/${PREP_REQUIRED.size}", "이 차의 기어", "안전 조작") else listOf("익히기 $done/${PREP_REQUIRED.size}", "기어", "안전"), prepTab) { prepTab = it; if (it == 1) onPrep("view", "gear") else render() }, k.lp(k.MATCH, k.WRAP))
        addView(k.gap(18))
        when (prepTab) {
            0 -> {
                addView(k.row(14).apply {
                    addView(k.text(if (all) "다 됐어요. 이 차가 손에 익었어요." else next?.let { n -> "다음: " + PREP_ITEMS.first { it.first == n }.let { "${it.second} · ${it.third}" } } ?: "", Type.Headline), k.lp(0, k.WRAP, 1f))
                })
                addView(k.gap(10)); addView(k.progress(done / PREP_REQUIRED.size.toFloat(), if (all) Ink.go else Ink.accent))
                addView(k.gap(16))
                val tiles = PREP_ITEMS.map { (key, name, hint) ->
                    val ok = key in prepDone; val now = key == next
                    k.card(pad = 20, color = if (now) Ink.accentSoft else Ink.surface, onClick = if (key == "gear") ({ prepTab = 1; onPrep("view", "gear") }) else null) {
                        gravity = Gravity.CENTER_HORIZONTAL
                        addView(Telltale(this@prepScreen, key).apply { on = ok; waiting = now }, k.lp(ui.dp(84), ui.dp(84)))
                        addView(k.gap(10))
                        addView(k.text(name, Type.Title).apply { gravity = Gravity.CENTER })
                        addView(k.gap(2))
                        addView(k.text(if (ok) "확인했어요" else hint, Type.Sub, if (ok) Ink.go else if (now) Ink.accent else Ink.text2).apply { gravity = Gravity.CENTER })
                    }
                }
                addView(k.grid(if (ui.wide) 5 else 2, tiles, 14))
                if (all) { addView(k.gap(16)); addView(k.button("안전 조작 보기", Kit.Btn.Tinted, "warning") { prepTab = 2; render() }) }
            }
            1 -> addView(shifterCard())
            else -> addView(safetySection(sh))
        }
    }
}

/** 출발 준비 중 들어온 신호. 하나하나는 효과음으로만 알리고, 다 마치면 말로 한다. */
internal fun MainActivity.onPrep(type: String, value: String) {
    val before = prepDone.toSet()
    when (type) {
        Evt.SIGNAL -> { if (value != "OFF") prepSeen.add(value); if (prepSeen.containsAll(listOf("LEFT", "RIGHT"))) prepDone.add("signal") }
        Evt.WIPER -> if (value == "on") prepDone.add("wiper")
        Evt.LIGHT -> if (value == "on") prepDone.add("light")
        "belt" -> if (value == "on") prepDone.add("belt")
        "view" -> if (value == "gear") prepDone.add("gear")
    }
    val newly = (prepDone - before).firstOrNull()
    if (newly != null) {
        chime()
        val finished = prepDone.containsAll(PREP_REQUIRED)
        prepLine = if (finished) "네 가지를 다 해 봤어요. 출발할 때는 주변부터 살펴요." else ""
        if (finished && newly in PREP_REQUIRED) speak(prepLine)
    }
    if (prepDone.containsAll(PREP_REQUIRED)) store.setPrepDone(store.driver)
    // 바뀐 것이 있을 때만 다시 그린다 (다른 차 신호마다 그리면 화면이 깜박이고 스크롤이 튄다)
    if (newly != null || type == "view" || (type == Evt.NAVI && value == "vin")) render()
}

// ═══════════════ 출발 전 ═══════════════

internal fun MainActivity.depart(g: Guide) {
    trip.guide = g
    car.refreshCondition()
    trip.routeSent = g.kind != "연습" && car.requestRoute(g.title, g.lat, g.lng, comfortable = store.typeOf(store.driver) in listOf("초보", "오랜만"))
    Session.forecast = null
    if (store.weatherOn && g.lat != null && g.lng != null) Weather.at(Math.round(g.lat * 100) / 100.0, Math.round(g.lng * 100) / 100.0) { f -> Session.forecast = f; if (route.name == MainActivity.READY && f != null) render() }
    go(MainActivity.READY)
}

internal fun MainActivity.readyScreen(): View {
    val k = kit
    val g = trip.guide ?: return todayScreen()
    val d = store.driver
    fun check(ok: Boolean?, good: String, bad: String, unknown: String, icon: String): View? {
        if (ok == null && unknown.isEmpty()) return null
        if (ok == false && bad.isEmpty()) return null
        return k.item(when (ok) { true -> good; false -> bad; null -> unknown }, icon = icon,
            iconColor = when (ok) { true -> Ink.go; false -> Ink.warn; null -> Ink.text3 },
            trailing = k.icon(when (ok) { true -> "checkCircle"; false -> "warning"; null -> "circle" }, when (ok) { true -> Ink.go; false -> Ink.warn; null -> Ink.text3 }, 22))
    }
    val navi = when {
        g.kind == "연습" -> check(true, "연습이라 내비 없이 기록만 해요", "", "", "pin")
        trip.routeSent && car.routeStarted -> check(true, "내비가 경로 안내를 시작했어요", "", "", "pin")
        trip.routeSent -> check(null, "", "", "내비에 목적지를 보냈어요 (안내 시작을 기다리는 중)", "pin")
        else -> check(null, "", "", "내비 안내 없이 기록만 해요", "pin")
    }
    val comfy = if (trip.routeSent && store.typeOf(d) in listOf("초보", "오랜만")) check(true, "편안한 길로 부탁했어요", "", "", "road") else null
    val checks = listOfNotNull(
        navi, comfy,
        check(car.energy?.let { it >= 20 }, "연료 ${car.energy}%", "연료 ${car.energy}% · 가는 길에 넣어요", "", "fuel"),
        check(if (car.washerKnown) !car.washerLow else null, "워셔액 충분", "워셔액이 부족해요", "", "info"),
        check(car.belt, "안전벨트를 맸어요", "안전벨트를 매 주세요", "", "seat"),
        check(if ("ENGINE_OIL_LEVEL" in car.lampsOn || "BRAKE_FLUID_LEVEL" in car.lampsOn) false else if (!car.failures.containsKey("oil")) true else null, "엔진오일·브레이크액 정상", "엔진오일이나 브레이크액이 적어요", "", "car"),
        store.seat(d)?.let { mine -> car.seatPos?.let { now -> check(now == mine, "의자가 지난번 내 자리 그대로예요", "의자가 지난번 내 자리와 달라요", "", "seat") } },
        Session.forecast?.let { f -> check(f.rainChance < 50, "도착지 ${f.sky} ${f.tempC}°", "도착지 ${f.sky} ${f.tempC}° · 3시간 안 비 올 확률 ${f.rainChance}%", "", "cloud") },
    ) + car.lampsOn.mapNotNull { Lamps.warn(it) }.map { check(false, "", "${it.name} 표시등이 켜져 있어요", "", "warning")!! }

    val placed = g.tips.count { it.placed }
    val canPlace = store.locationOn && (places.lat != null || places.status == "켜짐")
    val tipRows = g.tips.mapIndexed { i, t ->
        k.item(t.text, if (t.placed && store.locationOn) "남긴 곳 근처에 가면 들려줘요" else "자리 정보가 없어요 · 지금 미리 들어 두세요",
            if (t.placed) "pin" else "quote", if (t.placed) Ink.accent else Ink.text3,
            trailing = k.iconButton("speaker", Ink.accent, Ink.accentSoft, 44) { speak(t.text) })
    }

    val left = k.col().apply {
        addView(k.section("떠나기 전에"))
        addView(k.groupOf(checks))
    }
    val right = k.col().apply {
        addView(k.section(if (g.kind == "나") "이번에 참고할 지난번 한마디 ${g.tips.size}개" else "${Writer.poss(g.owner)} 한마디 ${g.tips.size}개"))
        if (tipRows.isEmpty()) addView(k.card { addView(k.text("이 길에는 한마디가 없어요. 조용히 기록만 해요.", Type.Sub, Ink.text2)) })
        else addView(k.groupOf(tipRows))
        if (placed > 0 && !canPlace) addView(k.text("위치를 받지 못하고 있어요. 설정에서 위치를 켜면 그 근처에서 들려줘요." + if (BuildConfig.DEMOS) " 모의 주행(시연용)은 경로를 따라 들려줘요." else "", Type.Caption, Ink.warn).apply { setPadding(ui.dp(4), ui.dp(10), 0, 0) })
    }
    return k.col().apply {
        Session.convoy?.let { f -> addView(k.row(10).apply { setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12)); background = k.rounded(Ink.accentSoft, 14); addView(k.avatar(f.name, 30)); addView(k.text("${f.name} 님과 같이 가요. ${f.name} 님이 앞서 가요.", Type.Sub), k.lp(0, k.WRAP, 1f)); addView(k.tag("시연용", Ink.warn)); addView(k.text("그만", Type.Sub, Ink.accent).apply { setOnClickListener { Session.convoy = null; render() } }) }); addView(k.gap(12)) }
        addView(k.header(g.title, (when (g.kind) { "나" -> "지난번의 내가 다녀온 길"; "다시" -> "다시 가는 길"; "연습" -> "${Writer.subj(g.owner)} 건네준 연습"; else -> "${Writer.subj(g.owner)} 건네준 길" }) + " · ${facts(g.drive)}", onBack = { trip.guide = null; Session.convoy = null; back() }))
        addView(k.gap(8))
        if (ui.wide) addView(k.row(24).apply { gravity = Gravity.TOP; addView(left, k.lp(0, k.WRAP, 1f)); addView(right, k.lp(0, k.WRAP, 1f)) })
        else { addView(left); addView(right) }
        addView(k.gap(26))
        addView(k.card(pad = 22, color = Ink.accentSoft) {
            addView(k.row(14).apply {
                addView(k.icon("steering", Ink.accent, 28))
                addView(k.col(2).apply {
                    addView(k.text("기어를 D에 놓으면 기록을 시작해요", Type.Headline))
                    addView(k.text("달리는 동안 화면에는 꼭 필요한 것만 보여 주고, 나머지는 말로 알려 줄게요.", Type.Sub, Ink.text2))
                }, k.lp(0, k.WRAP, 1f))
            })
        })
        addView(k.gap(16))
        addView(k.buttons(*listOfNotNull(
            k.button("기록 시작", Kit.Btn.Primary, "play") { trip.start() },
            if (g.owner != d) k.button("${g.owner} 님에게 출발 알리기", Kit.Btn.Secondary, "share") { tellDeparture(g) } else null,
            if (BuildConfig.DEMOS) k.button("모의 주행 (시연용)", Kit.Btn.Plain) { trip.startSim() } else null,   // 마켓판에는 없음
        ).toTypedArray()))
    }
}

/** 길을 건네준 사람에게 출발 소식을 남긴다. 메시지 앱이 있으면 밖으로도 보낼 수 있다. */
internal fun MainActivity.tellDeparture(g: Guide) {
    val me = store.driver
    store.addNews(g.owner, "$me\t${Writer.subj(me)} “${g.title}”로 출발했어요.")
    val lat = places.lat ?: car.lat; val lng = places.lng ?: car.lng
    val text = "$me: “${g.title}”로 출발했어요." + if (lat != null && lng != null) " 지금 여기예요 https://map.kakao.com/link/map/출발,$lat,$lng" else ""
    val send = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain").putExtra(android.content.Intent.EXTRA_TEXT, text)
    if (send.resolveActivity(packageManager) != null) startActivity(android.content.Intent.createChooser(send, "출발 알리기"))
    else toast("${g.owner} 님의 홈에 출발 소식을 남겼어요. 이 차에는 메시지 앱이 없어 밖으로는 보내지 않았어요.")
}

/** 처음 쓰는 사람에게: 차곡차곡이 하는 일 네 단계 */
internal fun MainActivity.loopCard(): View {
    val k = kit
    return k.card(pad = 20) {
        addView(k.text("이렇게 써요", Type.Headline))
        addView(k.gap(8))
        LOOP_STEPS.forEachIndexed { i, (ic, t, s) ->
            addView(k.row(12).apply {
                setPadding(0, ui.dp(6), 0, ui.dp(6))
                addView(k.text("${i + 1}", Type.Title, Ink.accent), k.lp(ui.dp(22), k.WRAP))
                addView(k.icon(ic, Ink.accent, 22))
                addView(k.col(1).apply { addView(k.text(t, Type.BodyStrong)); addView(k.text(s, Type.Caption, Ink.text2)) }, k.lp(0, k.WRAP, 1f))
            })
        }
    }
}
