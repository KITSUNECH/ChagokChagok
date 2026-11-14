// 차곡차곡 – 주차 후 첫 화면: 오늘을 돌아보기
// "차가 내 운전을 기억했다가, 같은 길을 다시 달릴 때 내가 늘었다는 걸 보여 준다."
// 차는 사실만 보여 준다 (재탐색, 브레이크, 천천히를 청한 곳). 그게 어려웠던 순간인지, 늘었는지는 운전자가 답한다.
//  1. 길 위에서 들은 한마디가 도움이 됐나요?
//  2. 같은 곳에 다시 왔다면: 지난번보다 어땠어요?
//  3. 오늘 돌아볼 만한 순간 → 이곳에서 기억해 둘 일이 있었나요? → 다음에 이 근처에서 들려준다
package com.example.chagokchagok

import android.view.Gravity
import android.view.View

internal const val Q_HELPED = "helped"
internal const val Q_VS_LAST = "vslast"

private val HELPED = listOf("도움이 됐어요", "잘 모르겠어요", "필요 없었어요")
private val VS_LAST = listOf("더 편했어요", "비슷했어요", "더 긴장됐어요")

/** 다음의 나에게 남기기: 같은 곳의 "나의 길"에 한마디를 더한다. 위치가 저장된 순간은 그 위치, 아니면 경로에서 진행 정도로 찾는다. */
internal fun MainActivity.keepForNextTime(d: Diary, picked: List<Tip>, quiet: Boolean = false) {
    val place = d.drive.place ?: store.guide(d.guideId)?.title ?: "지난번 그 길"
    val old = store.selfGuide(d.driver, place)
    val placed = picked.map { t -> if (t.lat != null && t.lng != null) t else Paths.at(d.drive.path, t.p)?.let { pt -> t.copy(lat = pt.lat, lng = pt.lng) } ?: t }
    val tips = (old?.tips.orEmpty() + placed).distinctBy { it.text }.sortedBy { it.p }
    store.addGuide(Guide(old?.id ?: "self_${d.driver}_${place.hashCode()}", d.driver, place, d.drive.placeLat ?: old?.lat, d.drive.placeLng ?: old?.lng,
        d.drive, tips, (old?.times ?: 0) + 1, kind = "나"))
    if (!quiet) toast("다음에 ‘$place’에 갈 때 이 근처에서 들려줄게요.")
}

/** 이 일기를 쓴 운전자가 아직 답하지 않은 것이 있는지 */
internal fun MainActivity.pending(d: Diary, prev: Diary?): Boolean {
    val moments = TipSuggest.moments(d.drive)
    val handled = store.skippedMoments(d.id) + store.keptMoments(d.id)
    return (store.heardTips(d.id).isNotEmpty() && store.answer(d.id, Q_HELPED) == null) ||
        (prev != null && store.answer(d.id, Q_VS_LAST) == null) ||
        moments.any { it.why !in handled } || unnamed(d)
}

/** 내비 목적지도, 전에 다녀온 곳도 아닌 곳에 주차했다: 이름을 붙이면 다음부터 같은 곳으로 비교한다 */
internal fun MainActivity.unnamed(d: Diary) = d.drive.place == null && d.drive.path.size > 1 && !d.drive.sim && !store.flag("noname_${d.id}")

private val PLACE_NAMES = listOf("집", "회사", "학교", "병원", "마트")

/** 주차 후 첫 화면의 맨 위. 답을 다 하면 한 줄 요약만 남는다. */
internal fun MainActivity.arrivalCard(d: Diary, prev: Diary?): View? {
    if (d.driver != store.driver) return null
    val k = kit
    val heard = store.heardTips(d.id)
    val moments = TipSuggest.moments(d.drive)
    val skipped = store.skippedMoments(d.id); val kept = store.keptMoments(d.id)
    val helped = store.answer(d.id, Q_HELPED); val vs = store.answer(d.id, Q_VS_LAST)
    if (heard.isEmpty() && prev == null && moments.isEmpty() && !unnamed(d)) return null

    val judge = if (prev != null) Growth.judge(store.diariesOf(d.driver), d) else null
    // 다 답했으면: 앱의 판단과 내 답을 합친 한 줄
    if (!pending(d, prev)) {
        if (judge != null) return k.card(pad = 22) {
            addView(judgementView(judge, vs, compact = true))
            addView(k.text(listOfNotNull(helped?.takeIf { it != "답하지 않음" }?.let { "들은 한마디 · $it" }, kept.size.takeIf { it > 0 }?.let { "다음에 들을 한마디 ${it}개" }).joinToString("  ·  "), Type.Sub, Ink.text2))
        }
        val parts = listOfNotNull(
            vs?.let { "지난번보다 $it" },
            helped?.let { "들은 한마디 · $it" },
            kept.size.takeIf { it > 0 }?.let { "다음에 들을 한마디 ${it}개" })
        if (parts.isEmpty()) return null
        return k.row(12).apply {
            setPadding(ui.dp(20), ui.dp(14), ui.dp(20), ui.dp(14)); background = k.rounded(Ink.accentSoft, 18)
            addView(k.icon("checkCircle", Ink.accent, 22))
            addView(k.text(parts.joinToString("  ·  "), Type.BodyStrong, Ink.accent), k.lp(0, k.WRAP, 1f))
        }
    }

    fun question(title: String, sub: String?, options: List<String>, chosen: String?, onPick: (String) -> Unit) = k.col(10).apply {
        addView(k.text(title, Type.Headline))
        sub?.let { addView(k.text(it, Type.Sub, Ink.text2, lines = 2)) }
        addView(k.flow(10).apply { options.forEach { o -> addView(k.chip(o, o == chosen) { onPick(o) }) } })
    }

    return k.card(pad = 26) {
        addView(k.row(10).apply {
            addView(k.icon("flag", Ink.accent, 24))
            addView(k.text(if (ui.narrow) "돌아봐요" else "이 길을 돌아봐요", Type.Title).apply { maxLines = 1 }, k.lp(0, k.WRAP, 1f))
            // 바쁘면 그냥 떠나도 된다. 답하지 않은 길은 홈의 "돌아볼 길"에 남는다
            addView(k.button("나중에", Kit.Btn.Plain) { go(MainActivity.TODAY) })
            addView(k.button("답하지 않기", Kit.Btn.Plain) {
                moments.forEach { if (it.why !in kept) store.skipMoment(d.id, it.why) }
                if (heard.isNotEmpty() && helped == null) store.setAnswer(d.id, Q_HELPED, "답하지 않음")
                if (prev != null && vs == null) store.setAnswer(d.id, Q_VS_LAST, "답하지 않음")
                render()
            })
        })
        addView(k.text("차가 본 것을 먼저 보여 드려요. 바쁘면 나중에 홈에서 이어서 해요.", Type.Sub, Ink.text2))
        val blocks = mutableListOf<View>()
        // 앱이 먼저 판단하고(이 화면의 결론이라 맨 위), 바로 아래에서 운전자의 느낌을 묻는다. 둘을 합쳐 한 줄로 보여 준다
        if (judge != null) blocks.add(judgementView(judge, vs))
        if (prev != null && vs == null) blocks.add(question(
            "내 느낌은 어땠어요?", "${dateText(prev.date, "M월 d일")}에도 같은 곳에 다녀왔어요. 차의 판단과 합쳐서 보여 드려요.", VS_LAST, vs) { store.setAnswer(d.id, Q_VS_LAST, it); render() })
        if (heard.isNotEmpty() && helped == null) blocks.add(question(
            "길 위에서 들은 한마디, 도움이 됐나요?", heard.joinToString("  ·  ") { "“$it”" }, HELPED, helped) { store.setAnswer(d.id, Q_HELPED, it); render() })
        if (unnamed(d)) blocks.add(k.col(10).apply {
            addView(k.text("여기를 뭐라고 부를까요?", Type.Headline))
            addView(k.text("이름을 붙이면 다음에 내비 없이 와도 같은 곳으로 알아보고 지난번과 비교해요.", Type.Sub, Ink.text2, lines = 2))
            addView(k.flow(10).apply {
                fun name(n: String) {
                    val end = d.drive.path.last()
                    val n0 = store.diariesOf(d.driver).count { it.drive.place == n && !it.drive.sim && it.id != d.id }
                    store.addDiary(d.copy(title = "$n · ${n0 + 1}번째", drive = d.drive.copy(place = n, placeLat = end.lat, placeLng = end.lng)))
                    toast("‘$n’로 기억해 둘게요."); render()
                }
                PLACE_NAMES.forEach { n -> addView(k.chip(n, false) { name(n) }) }
                addView(k.chip("직접 쓰기", false, "pencil") { sheets.input("이곳의 이름", "예: 외근지 A사, 어린이집") { t -> if (t.isNotBlank()) name(t.trim().take(20)) } })
                addView(k.chip("이름 붙이지 않기", false) { store.setFlag("noname_${d.id}"); render() })
            })
        })
        val open = moments.filter { it.why !in skipped && it.why !in kept }
        if (open.isNotEmpty()) blocks.add(k.col(10).apply {
            addView(k.text("돌아볼 만한 순간", Type.Headline))
            open.forEach { m ->
                addView(k.row(14).apply {
                    setPadding(ui.dp(16), ui.dp(12), ui.dp(12), ui.dp(12)); background = k.rounded(Ink.fill, 16)
                    addView(k.icon(m.icon, Ink.accent, 24))
                    addView(k.col(2).apply {
                        addView(k.text(m.why.substringBefore(" · "), Type.BodyStrong))
                        addView(k.text(listOfNotNull(m.why.substringAfter(" · ", "").ifEmpty { null }, if (m.lat != null) "위치 저장됨" else "위치는 대략").joinToString(" · "), Type.Caption, Ink.text2))
                    }, k.lp(0, k.WRAP, 1f))
                    addView(k.button("돌아보기", Kit.Btn.Tinted) { reviewMoment(d, m) })
                })
            }
        })
        blocks.forEach { addView(k.gap(14)); addView(it) }
    }
}

/** 순간 하나: 이곳에서 기억해 둘 일이 있었나요? 운전자가 고르거나 고쳐 쓴 문장만 남긴다. */
private fun MainActivity.reviewMoment(d: Diary, m: TipIdea) {
    val options = listOf("“${m.text}”", "직접 쓸게요", "괜찮았어요 · 남기지 않기")
    sheets.choose("이곳에서 기억해 둘 일이 있었나요?", m.why.substringBefore(" · ") + ". 남기면 다음에 이 근처에서 들려줘요.", options, listOf("quote", "pencil", "check")) { i ->
        when (i) {
            0 -> { keepForNextTime(d, listOf(m.tip())); store.keepMoment(d.id, m.why); render() }
            1 -> sheets.input("다음의 나에게", "예: 병원 입구는 두 번째 골목", m.text) { t ->
                if (t.isNotBlank()) { keepForNextTime(d, listOf(m.tip(t.trim()))); store.keepMoment(d.id, m.why); render() }
            }
            else -> { store.skipMoment(d.id, m.why); render() }
        }
    }
}

/** 아직 돌아보지 않은 길 (최근 7일, 내 기록). 출근길처럼 바로 내려야 했던 날을 저녁에 몰아서 돌아본다 */
internal fun MainActivity.pendingReviews(): List<Diary> {
    val weekAgo = System.currentTimeMillis() - 3 * 86400000L   // 사흘 지난 길은 숙제처럼 쌓아 두지 않는다
    return store.diariesOf(store.driver).filter { it.date >= weekAgo && pending(it, previousSame(it)) }.sortedBy { it.date }
}

/** 홈의 "돌아볼 길 n개" */
internal fun MainActivity.reviewCard(): View? {
    val ds = pendingReviews(); if (ds.isEmpty()) return null
    val k = kit
    return k.card(pad = 22, onClick = { go(MainActivity.DIARY, ds.first().id) }) {
        addView(k.row(14).apply {
            addView(k.icon("flag", Ink.accent, 28))
            addView(k.col(3).apply {
                addView(k.text("돌아볼 일기 ${ds.size}장", Type.Headline))
                addView(k.text(ds.take(2).joinToString(" · ") { "${it.title} ${dateText(it.date, "a h:mm")}" }, Type.Sub, Ink.text2, lines = 2))
            }, k.lp(0, k.WRAP, 1f))
            addView(k.icon("chevron", Ink.text3, 20))
        })
    }
}


// ═══════════════ 일기 한 장 ═══════════════

/**
 * 주차 후 처음 보는 화면. 스크롤 없이 한눈에: 왼쪽은 차가 본 것(같은 길의 판정, 없으면 오늘 본 사실),
 * 오른쪽은 나(느낌, 한 줄, 다음의 나에게 한마디). 질문은 한 번에 하나만 묻고, 답하면 다음 것이 나온다.
 * 지나온 길 그림·타임라인·근거는 "자세히 보기"에 있다.
 */
internal fun MainActivity.diaryPage(d: Diary, prev: Diary?): View {
    val k = kit
    val all = store.diariesOf(d.driver)
    val mine = d.driver == store.driver
    val judge = Growth.judge(all, d)
    val vs = store.answer(d.id, Q_VS_LAST); val helped = store.answer(d.id, Q_HELPED)
    val heard = store.heardTips(d.id)
    val handled = store.skippedMoments(d.id) + store.keptMoments(d.id)
    val open = TipSuggest.moments(d.drive).filter { it.why !in handled }
    val feeling = store.feeling(d.id)

    fun ask(title: String, sub: String?, options: List<String>, write: String? = null, onPick: (String) -> Unit) = k.card(pad = 22) {
        addView(k.text(title, Type.Headline))
        sub?.let { addView(k.gap(4)); addView(k.text(it, Type.Sub, Ink.text2, lines = 2)) }
        addView(k.gap(12))
        addView(k.flow(10).apply {
            options.forEach { o -> addView(k.chip(o, false) { onPick(o) }) }
            if (write != null) addView(k.chip("직접 쓰기", false, "pencil") { sheets.input(write, "") { t -> if (t.isNotBlank()) onPick(t.trim()) } })
        })
    }
    // 한 번에 하나: 지난번과 견준 느낌 → 들은 한마디 → 이곳의 이름 → 오늘 한 줄
    val question: View? = when {
        !mine -> null
        prev != null && vs == null -> ask("내 느낌은 어땠어요?", "${dateText(prev.date, "M월 d일")}에도 다녀온 길이에요. 차가 본 것과 합쳐서 보여 줄게요.", VS_LAST) { store.setAnswer(d.id, Q_VS_LAST, it); render() }
        heard.isNotEmpty() && helped == null -> ask("길에서 들은 한마디, 도움이 됐나요?", "“${heard.first()}”", HELPED) { store.setAnswer(d.id, Q_HELPED, it); render() }
        unnamed(d) -> ask("여기를 뭐라고 부를까요?", "이름을 붙이면 다음에 같은 곳으로 알아보고 지난번과 견줘요.", PLACE_NAMES, "이곳의 이름") { n ->
            val end = d.drive.path.last(); val name = n.take(20)
            val n0 = all.count { it.drive.place == name && !it.drive.sim && it.id != d.id }
            store.addDiary(d.copy(title = "$name · ${n0 + 1}번째", drive = d.drive.copy(place = name, placeLat = end.lat, placeLng = end.lng)))
            toast("‘$name’로 기억해 둘게요."); render()
        }
        feeling == null -> ask("오늘 운전은 어땠어요?", "한 줄만 남기면 내 일기가 돼요.", FEELS, "한 줄") { store.setFeeling(d.id, it); render() }
        else -> null
    }
    val left: View = if (judge != null) judgementView(judge, vs).apply { (background as? android.graphics.drawable.GradientDrawable)?.setColor(Ink.surface) }
        else carSawCard(d) ?: k.card(pad = 26) { addView(k.text(d.text, Type.Body)) }
    val right = k.col(14).apply {
        if (feeling != null) addView(k.card(pad = 22) {
            addView(k.row(8).apply {
                addView(k.icon("pencil", Ink.accent, 18)); addView(k.text("내 한 줄", Type.Sub, Ink.accent), k.lp(0, k.WRAP, 1f))
                if (mine) addView(k.text("고치기", Type.Sub, Ink.accent).apply { gravity = Gravity.CENTER_VERTICAL; minHeight = ui.touch(48); setPadding(ui.dp(12), 0, 0, 0); setOnClickListener { askFeeling(d) } })
            })
            addView(k.voice("“$feeling”", if (ui.wide) 26f else 22f))
        })
        question?.let { addView(it) }
        if (mine) addView(k.button(if (open.isNotEmpty()) "돌아볼 순간 ${open.size}곳 · 다음에 들을 한마디 남기기" else "다음에 들을 한마디 남기기", if (question == null) Kit.Btn.Primary else Kit.Btn.Tinted, "quote") {
            if (open.size == 1) reviewMoment(d, open.first()) else noteToSelf(d)
        }, k.lp(k.MATCH, k.WRAP))
        if (mine && pending(d, prev)) addView(k.button("나중에 할게요", Kit.Btn.Plain) { go(MainActivity.TODAY) }, k.lp(k.MATCH, k.WRAP))
    }
    return k.col().apply {
        addView(k.header(d.title, dateText(d.date) + (if (d.drive.sim) " · 모의 주행 (시연용)" else "") + if (store.isSample(d.id)) " · 예시 기록" else "", onBack = { back() },
            trailing = k.iconButton("speaker", Ink.accent, Ink.accentSoft, 52) { readAloud(d.title, listOfNotNull(feeling, Coach.today(d.drive, all.filter { it.date < d.date }, store.answer(d.id, "focus"))).joinToString(". ")) }))
        addView(k.gap(16))
        if (ui.wide) addView(k.row(20).apply { gravity = Gravity.TOP; addView(left, k.lp(0, k.WRAP, 1.2f)); addView(right, k.lp(0, k.WRAP, 1f)) })
        else { addView(left); addView(k.gap(14)); addView(right) }
    }
}
