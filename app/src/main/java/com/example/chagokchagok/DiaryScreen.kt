// 차곡車곡 – 오늘의 일기 · 이 길 건네주기
package com.example.chagokchagok

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import java.util.Locale

internal object DiaryPrefs { var plain = false }

internal fun MainActivity.diaryScreen(id: String?): View {
    val k = kit
    val d = store.diaries().firstOrNull { it.id == id } ?: return todayScreen()
    val guide = store.guide(d.guideId)
    val paper = paperPage(d, guide)
    val side = k.col().apply {
        addView(k.section("이 기록의 근거"))
        addView(evidence(d, guide))
        addView(k.section("주행 타임라인"))
        addView(k.card(pad = 20) {
            addView(Timeline(context, ui, d.drive), k.lp(k.MATCH, ui.dp(176)))
            addView(k.gap(8))
            addView(k.text(timelineSummary(d.drive), Type.Caption, Ink.text2))
        })
        addView(k.section("다음으로"))
        addView(actions(d, guide))
    }
    return k.col().apply {
        addView(k.header(d.title, "${d.driver} · ${dateText(d.date)}" + if (d.drive.sim) " · 모의 주행" else "", onBack = { back() },
            trailing = k.iconButton("speaker", Ink.accent, Ink.accentSoft, 48) { readAloud(d.title, d.text) }))
        addView(k.gap(18))
        if (ui.wide) addView(k.row(24).apply { gravity = Gravity.TOP; addView(paper, k.lp(0, k.WRAP, 1.15f)); addView(side, k.lp(0, k.WRAP, 1f)) })
        else { addView(paper); addView(side) }
    }
}

/** 종이 한 장: 날짜와 날씨, 그림 칸(두 사람의 주행), 원고지 또는 줄글, 도장 */
private fun MainActivity.paperPage(d: Diary, guide: Guide?): View {
    val k = kit
    val page = FrameLayout(this).apply {
        background = k.rounded(Ink.paper, 20)
        elevation = ui.dp(2).toFloat()
        addView(k.col(14).apply {
            setPadding(ui.dp(26), ui.dp(22), ui.dp(26), ui.dp(30))
            addView(k.row().apply {
                addView(k.text(dateText(d.date), Type.Headline, Ink.ink).apply { typeface = ui.batang }, k.lp(0, k.WRAP, 1f))
                val w = store.weather(d.id) ?: if (d.drive.rained) "와이퍼 켬" else null
                if (w != null) addView(k.row(6).apply { addView(k.icon("cloud", Ink.paperLine, 20)); addView(k.text(w, Type.Sub, Ink.ink)) })
            })
            val lanes = mutableListOf<Lane>()
            if (guide != null) lanes.add(Lane(guide.owner, guide.drive, guide.tips, faded = true))
            lanes.add(Lane(d.driver, d.drive))
            addView(FrameLayout(context).apply {
                background = k.rounded(android.graphics.Color.WHITE, 12, 1, Ink.paperLine)
                addView(TraceView(context, ui, onPaper = true).apply { this.lanes = lanes }, FrameLayout.LayoutParams(-1, -1))
            }, k.lp(k.MATCH, ui.dp(if (lanes.size > 1) 190 else 120)))
            addView(k.row().apply {
                addView(k.fill())
                addView(k.text(if (DiaryPrefs.plain) "원고지로 보기" else "줄글로 보기", Type.Caption, Ink.paperLine).apply { setPadding(ui.dp(8), ui.dp(4), 0, ui.dp(4)); setOnClickListener { DiaryPrefs.plain = !DiaryPrefs.plain; render() } })
            })
            if (DiaryPrefs.plain) addView(k.voice(d.text, 23f, Ink.ink))
            else addView(GridText(context, ui).apply { cols = if (ui.wide) 13 else 11; highlight = d.highlight; text = d.text })
            store.feeling(d.id)?.let { addView(k.voice("— 내 한 줄: $it", 19f, Ink.ink)) }
            if (d.ai) addView(k.text("문장은 생성형 AI가 다듬었어요. 사실은 차량 기록 그대로예요.", Type.Caption, Ink.paperLine))
        }, FrameLayout.LayoutParams(-1, -2))
        d.stamp?.let { addView(stamp(it, true, 1, 84), FrameLayout.LayoutParams(ui.dp(84), ui.dp(84), Gravity.BOTTOM or Gravity.END).apply { rightMargin = ui.dp(18); bottomMargin = ui.dp(14) }) }
    }
    return page
}

/** 이 기록의 근거: 무엇을 차가 봤고, 무엇을 사람이 적었고, 무엇을 추정했는지 */
private fun MainActivity.evidence(d: Diary, guide: Guide?): View {
    val k = kit; val v = d.drive
    val rows = mutableListOf<View>()
    fun row(title: String, value: String, source: String, icon: String, color: Int = Ink.accent) = rows.add(k.item(title, source, icon, color, value = value))
    row("주행", facts(v).ifEmpty { "–" }, if (v.path.size > 1 && v.distKm > 0) "주행거리계 · 위치" else "주행거리계", "road")
    row("깜빡이", "${v.signals}번", "차량 신호 · 방향지시등", "turnRight", Ink.signal)
    if (v.turns > 0) row("돌기 전에 켠 깜빡이", "${v.turnsSignaled}/${v.turns}", "내비 회전 안내 + 방향지시등", "check", Ink.signal)
    row("브레이크", "${v.brakes}번", "차량 신호 · 브레이크 페달", "stop", Ink.brake)
    if (v.events.any { it.type == Evt.WIPER }) row("와이퍼", if (v.rained) "1분 넘게" else "잠깐", if (v.rained) "1분 넘게 켜서 비 온 날로 적었어요" else "세척일 수 있어 비로 치지 않았어요", "cloud", Ink.rain)
    if (v.events.any { it.type == Evt.LIGHT }) row("전조등", if (v.night) "밤에 켬" else "켬", if (v.night) "18시~6시에 켜서 밤길로 적었어요" else "낮이라 밤길로 치지 않았어요", "sparkle", Ink.beam)
    if (v.parkMoves > 0) row("후진 뒤 기어 전환", "${v.parkMoves}번", "차량 신호 · 기어 (주차 성공 여부는 알 수 없어요)", "car")
    if (v.passenger) row("함께 탄 사람", "있음", "차량 신호 · 좌석 감지", "people")
    if (guide != null) {
        row("끝까지 달림", if (v.completed) "예" else "중간에 멈춤", if (v.completed) "내비 도착 또는 받은 길 거리의 90% 이상" else "졸업 횟수에 넣지 않았어요", "flag")
        row("들은 한마디", "${guide.tips.size}개 중 남긴 자리에서", "위치 · 한마디를 남긴 자리 150m 안", "pin")
    }
    v.place?.let { row("다녀온 곳", it, "내비 목적지", "pin") }
    store.weather(d.id)?.let { row("날씨", it, "Open-Meteo 공개 예보 (도착한 곳)", "cloud") }
    store.feeling(d.id)?.let { row("내 한 줄", it, "내가 적음", "pencil") }
    return k.groupOf(rows)
}

private fun timelineSummary(v: Drive): String {
    val parts = mutableListOf("깜빡이 ${v.signals}번", "브레이크 ${v.brakes}번")
    if (v.events.any { it.type == Evt.WIPER }) parts.add("와이퍼 사용")
    if (v.events.any { it.type == Evt.LIGHT }) parts.add("전조등 사용")
    return parts.joinToString(" · ") + ". 가로축은 출발부터 주차까지예요."
}

/** 일기 다음에 할 수 있는 일 */
private fun MainActivity.actions(d: Diary, guide: Guide?): View {
    val k = kit
    val rows = mutableListOf<View>()
    rows.add(k.item(store.feeling(d.id)?.let { "내 한 줄: $it" } ?: "오늘 한 줄 남기기", "오늘 운전이 어땠는지", "pencil", chevron = true) {
        val feels = listOf("설렜어", "무서웠지만 해냈어", "생각보다 괜찮았어", "피곤했어", "풍경이 좋았어")
        sheets.choose("오늘 운전은 어땠어요?", null, feels + "직접 쓰기") { i ->
            if (i == feels.size) sheets.input("한 줄", "예: 주차장 들어갈 때 손에 땀이 났다") { store.setFeeling(d.id, it); render() } else { store.setFeeling(d.id, feels[i]); render() }
        }
    })
    if (d.drive.passenger) rows.add(k.item("함께 탄 사람의 한마디 적기", "옆자리에서 해 준 말을 이 길에 남겨요", "people", chevron = true) {
        val lines = listOf("잘하고 있어.", "여기서 미리 차선 바꿔 두면 편해.", "여기는 천천히 가도 돼.", "주차장 입구 조심해.")
        sheets.choose("어디쯤에서 한 말인가요?", null, TIP_PLACES.map { it.first }) { where ->
            sheets.choose("함께 탄 사람의 한마디", null, lines + "직접 쓰기") { i ->
                fun keep(t: String) { store.addDiary(d.copy(drive = d.drive.copy(events = d.drive.events + Ev(TIP_PLACES[where].second, Evt.SAY, t)))); toast("적어 뒀어요. 이 길을 건네줄 때 함께 가요."); render() }
                if (i == lines.size) sheets.input("한마디", "") { keep(it) } else keep(lines[i])
            }
        }
    })
    rows.add(k.item("이 길 건네주기", "가족·친구가 같은 길을 달릴 수 있게", "gift", chevron = true) {
        giveTitle = d.drive.place ?: ""; giveTips.clear(); giveTips.addAll(d.drive.events.filter { it.type == Evt.SAY }.map { Tip(it.p, it.value) }); giveKind = if (d.drive.place != null && guide == null) "추천" else "길"
        go(MainActivity.GIVE, d.id)
    })
    val posted = store.posted(d.id) != null
    rows.add(k.item(if (posted) "함께 차곡에 올렸어요" else "함께 차곡에 올리기", if (posted) "누르면 내릴 수 있어요" else "미리 보고 누구에게 보일지 골라요", "share", chevron = true) {
        if (posted) sheets.confirm("함께 차곡에서 내릴까요?", "다른 사람의 화면에서 이 일기가 사라져요.", "내리기", danger = true) { store.unpost(d.id); toast("내렸어요."); render() }
        else postSheet(d)
    })
    Quizzes.pick(d.drive)?.let { q -> rows.add(k.item("오늘 길 복습", q.question, "info", chevron = true) { quiz(q) }) }
    if (guide != null && guide.owner != d.driver) {
        val sent = store.newsSent(d.id)
        rows.add(k.item(if (sent) "${guide.owner} 님에게 알렸어요" else "${guide.owner} 님에게 다녀왔다고 알리기", null, "flag", value = if (sent) "보냄" else null) {
            if (sent) return@item
            store.addNews(guide.owner, "${d.driver}\t" + when (guide.kind) {
                "추천" -> "${Writer.subj(d.driver)} 추천해 준 곳에 직접 운전해서 다녀왔어요."
                "연습" -> "${Writer.subj(d.driver)} “${guide.title}” 연습을 해 봤어요."
                else -> "${Writer.subj(d.driver)} “${guide.title}”을 직접 운전했어요." + if (guide.tips.isNotEmpty()) " 남겨 준 한마디도 길 위에서 들었어요." else ""
            })
            store.setNewsSent(d.id); toast("${guide.owner} 님의 홈에 소식을 남겼어요."); render()
        })
    }
    rows.add(k.item("다음 사람에게 한마디", "다음에 이 차를 타는 사람의 홈에 보여요", "car", chevron = true) {
        val notes = listOf("기름 넣어야 해요.", "워셔액 채워 주세요.", "뒷자리에 짐 있어요.", "주차는 평소 자리에 했어요.")
        sheets.choose("다음에 타는 사람에게", null, notes + "직접 쓰기") { i ->
            if (i == notes.size) sheets.input("한마디", "") { trip.saveHandover(store.driver, it); toast("남겼어요.") } else { trip.saveHandover(store.driver, notes[i]); toast("남겼어요.") }
        }
    })
    rows.add(k.item("오늘", null, "today", chevron = true) { go(MainActivity.TODAY) })
    return k.groupOf(rows)
}

/** 함께 차곡에 올리기 전 미리 보기와 공개 범위 */
internal fun MainActivity.postSheet(d: Diary) {
    var audience = 0
    val caption = store.feeling(d.id) ?: d.highlight.ifEmpty { d.title }
    sheets.sheet("함께 차곡에 올리기", "이렇게 보여요. 출발한 곳과 지나온 길은 올라가지 않아요.") { close ->
        val k = kit
        addView(k.card(pad = 20, color = Ink.fill) {
            addView(k.row(10).apply { addView(k.avatar(d.driver, 30)); addView(k.text(d.driver, Type.BodyStrong)); addView(k.fill()); addView(k.tag("일기")) })
            addView(k.gap(10)); addView(k.text(d.title, Type.Headline))
            addView(k.voice("“$caption”", 19f, Ink.text2))
            addView(k.text(listOfNotNull(facts(d.drive), d.drive.place).joinToString(" · "), Type.Caption, Ink.text3))
        })
        addView(k.text("누구에게 보일까요?", Type.Sub, Ink.text2))
        val seg = FrameLayout(context)
        fun draw() { seg.removeAllViews(); seg.addView(k.segmented(listOf("함께 쓰는 사람 모두", "가족만"), audience) { audience = it; draw() }) }
        draw(); addView(seg)
        addView(k.buttons(k.button("취소", Kit.Btn.Secondary) { close() }, k.button("올리기", Kit.Btn.Primary, "share") {
            store.setShares(d.driver, true); store.post(d.id, caption); store.setAudience(d.id, if (audience == 1) "가족" else "모두")
            close(); toast("함께 차곡에 올렸어요."); go(MainActivity.TOGETHER)
        }, stack = false))
    }
}

internal fun MainActivity.quiz(q: Quiz) {
    sheets.sheet("오늘 길 복습", q.question) { close ->
        val k = kit
        addView(k.groupOf(q.choices.mapIndexed { i, c ->
            k.item(c, trailing = k.icon("chevron", Ink.text3, 18)) {
                close()
                sheets.sheet(if (i == q.answer) "맞아요" else "정답은 “${q.choices[q.answer]}”", q.explain) { c2 ->
                    addView(k.text("출처: ${q.source}", Type.Caption, Ink.text3))
                    addView(k.button("닫기") { c2() })
                }
            }
        }).apply { background = k.rounded(Ink.fill, 18) })
    }
}

// ═══════════════ 이 길 건네주기 ═══════════════

internal val TIP_PLACES = listOf("출발하고 곧" to .15f, "가는 길 중간" to .5f, "도착 직전" to .92f)

internal fun MainActivity.giveScreen(id: String?): View {
    val k = kit
    val d = store.diaries().firstOrNull { it.id == id } ?: return todayScreen()
    val presets = when (giveKind) { "추천" -> listOf("주말 드라이브", "바닷가 카페", "맛집 가는 길"); "연습" -> listOf("주차장에서 후진 주차", "동네 한 바퀴", "좁은 골목 지나기"); else -> listOf("할머니 댁 가는 길", "마트 가는 길", "학교 가는 길") }
    val hasPath = d.drive.path.size > 1
    val form = k.col().apply {
        addView(k.section("무엇을 건네나요?"))
        addView(k.segmented(listOf("가는 길", "연습", "추천 장소"), listOf("길", "연습", "추천").indexOf(giveKind)) { giveKind = listOf("길", "연습", "추천")[it]; render() })
        addView(k.section("이름"))
        addView(k.chips(presets + "직접 쓰기", giveTitle.takeIf { it in presets }) { t ->
            if (t == "직접 쓰기") sheets.input("이름", "예: 엄마 회사 가는 길", giveTitle) { giveTitle = it; render() } else { giveTitle = t; render() }
        })
        if (giveTitle.isNotEmpty() && giveTitle !in presets) addView(k.text(giveTitle, Type.Headline).apply { setPadding(ui.dp(4), ui.dp(10), 0, 0) })
        addView(k.section(if (giveKind == "추천") "한 줄 후기" else "한마디 ${giveTips.size}개", "더하기") { addTip(d) })
        if (giveTips.isEmpty()) addView(k.card(pad = 20) { addView(k.text(if (giveKind == "추천") "주차장이나 입구처럼 처음 가는 사람에게 도움이 될 말을 남겨 주세요." else "길 위의 한 자리에 한마디를 남기면, 받은 사람이 그 자리에 가까워질 때 들려줘요.", Type.Sub, Ink.text2)) })
        else addView(k.groupOf(giveTips.mapIndexed { i, t ->
            k.item(t.text, TIP_PLACES.minByOrNull { Math.abs(it.second - t.p) }!!.first + if (hasPath) " · 그 자리 좌표 저장" else " · 좌표 없음", if (hasPath) "pin" else "quote",
                trailing = k.iconButton("trash", Ink.danger, Ink.alpha(Ink.danger, .1f), 40) { giveTips.removeAt(i); render() })
        }))
    }
    val preview = k.col().apply {
        addView(k.section("받는 사람에게는"))
        addView(k.card {
            addView(k.row(10).apply { addView(k.avatar(d.driver, 30)); addView(k.text("${Writer.subj(d.driver)} 건네준 ${when (giveKind) { "연습" -> "연습"; "추천" -> "추천 장소"; else -> "길" }}", Type.Sub, Ink.text2)) })
            addView(k.gap(10)); addView(k.text(giveTitle.ifEmpty { "이름을 골라 주세요" }, Type.Title, if (giveTitle.isEmpty()) Ink.text3 else Ink.text))
            addView(k.text(facts(d.drive), Type.Sub, Ink.text2))
            addView(k.gap(12))
            addView(TraceView(context, ui).apply { lanes = listOf(Lane("", d.drive, giveTips)) }, k.lp(k.MATCH, ui.dp(100)))
        })
        addView(k.gap(12))
        addView(k.row(10).apply { addView(k.icon("lock", Ink.text2, 18)); addView(k.text("출발한 곳 근처 500m는 빼고 건네요. 목적지와 한마디를 남긴 자리만 함께 가요.", Type.Caption, Ink.text2), k.lp(0, k.WRAP, 1f)) })
    }
    return k.col().apply {
        addView(k.header("이 길 건네주기", "${dateText(d.date, "M월 d일")}의 주행 · ${facts(d.drive)}", onBack = { back() }))
        addView(k.gap(6))
        if (ui.wide) addView(k.row(26).apply { gravity = Gravity.TOP; addView(form, k.lp(0, k.WRAP, 1f)); addView(preview, k.lp(0, k.WRAP, 1f)) })
        else { addView(form); addView(preview) }
        addView(k.gap(24))
        addView(k.buttons(k.button("건네주기", Kit.Btn.Primary, "gift", enabled = giveTitle.isNotEmpty()) { saveGuide(d) }, k.button("취소", Kit.Btn.Secondary) { back() }, stack = false))
    }
}

private fun MainActivity.addTip(d: Diary) {
    val presets = if (giveKind == "추천") listOf("주차장이 넓어요.", "입구가 좁으니 천천히.", "풍경이 좋아요.") else listOf("미리 오른쪽 차로로 가 있어.", "여기는 속도를 줄이는 게 좋아.", "주차장 입구가 좁아. 천천히 들어가.", "여기서 차선을 바꾸면 편해.")
    sheets.choose("어디쯤에서 할 말인가요?", null, TIP_PLACES.map { it.first }) { where ->
        sheets.choose("한마디", null, presets + "직접 쓰기") { i ->
            fun add(t: String) { giveTips.add(Tip(TIP_PLACES[where].second, t)); giveTips.sortBy { it.p }; render() }
            if (i == presets.size) sheets.input("한마디", "예: 큰 사거리 지나면 바로 우회전") { add(it) } else add(presets[i])
        }
    }
}

private fun MainActivity.saveGuide(d: Diary) {
    if (giveTitle.isEmpty()) { toast("이름을 골라 주세요."); return }
    val full = d.drive.path
    val same = store.guides().firstOrNull { it.owner == d.driver && it.title == giveTitle }
    // 한마디는 주행 경로 위 그 진행 정도의 자리에 붙인다 (경로가 있을 때만)
    val tips = giveTips.map { t -> Paths.at(full, t.p)?.let { pt -> t.copy(lat = pt.lat, lng = pt.lng) } ?: t }
    val lat = if (giveKind == "연습") null else d.drive.placeLat ?: store.guide(d.guideId)?.lat ?: full.lastOrNull()?.lat
    val lng = if (giveKind == "연습") null else d.drive.placeLng ?: store.guide(d.guideId)?.lng ?: full.lastOrNull()?.lng
    store.addGuide(Guide(same?.id ?: "g${d.date}", d.driver, giveTitle, lat, lng, d.drive.copy(path = Paths.shareable(full)), tips, (same?.times ?: 0) + 1, kind = giveKind))
    if (giveKind != "연습" && lat == null) toast("목적지가 기록되지 않은 주행이라 내비 안내 없이 건네요.")
    else toast("“$giveTitle”을 건네줬어요. 함께 쓰는 사람의 오늘 화면에 나타나요.")
    go(MainActivity.TODAY)
}
