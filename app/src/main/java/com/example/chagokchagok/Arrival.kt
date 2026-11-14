// 차곡차곡 – 주차 후 첫 화면: 오늘을 돌아보기
// "차가 내 운전을 기억했다가, 같은 길을 다시 달릴 때 내가 늘었다는 걸 보여 준다."
// 차는 사실만 보여 준다 (재탐색, 브레이크, 천천히를 청한 곳). 그게 어려웠던 순간인지, 늘었는지는 운전자가 답한다.
//  1. 길 위에서 들은 한마디가 도움이 됐나요?
//  2. 같은 곳에 다시 왔다면: 지난번보다 어땠어요?
//  3. 오늘 돌아볼 만한 순간 → 이곳에서 기억해 둘 일이 있었나요? → 다음에 이 근처에서 들려준다
package com.example.chagokchagok

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
private fun MainActivity.pending(d: Diary, prev: Diary?): Boolean {
    val moments = TipSuggest.moments(d.drive)
    val handled = store.skippedMoments(d.id) + store.keptMoments(d.id)
    return (store.heardTips(d.id).isNotEmpty() && store.answer(d.id, Q_HELPED) == null) ||
        (prev != null && store.answer(d.id, Q_VS_LAST) == null) ||
        moments.any { it.why !in handled }
}

/** 주차 후 첫 화면의 맨 위. 답을 다 하면 한 줄 요약만 남는다. */
internal fun MainActivity.arrivalCard(d: Diary, prev: Diary?): View? {
    if (d.driver != store.driver) return null
    val k = kit
    val heard = store.heardTips(d.id)
    val moments = TipSuggest.moments(d.drive)
    val skipped = store.skippedMoments(d.id); val kept = store.keptMoments(d.id)
    val helped = store.answer(d.id, Q_HELPED); val vs = store.answer(d.id, Q_VS_LAST)
    if (heard.isEmpty() && prev == null && moments.isEmpty()) return null

    // 다 답했으면: 내가 남긴 답이 곧 "늘었다"의 근거. 한 줄로.
    if (!pending(d, prev)) {
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
            addView(k.text("오늘을 돌아봐요", Type.Title), k.lp(0, k.WRAP, 1f))
            addView(k.button("다음에", Kit.Btn.Plain) {
                moments.forEach { if (it.why !in kept) store.skipMoment(d.id, it.why) }
                if (heard.isNotEmpty() && helped == null) store.setAnswer(d.id, Q_HELPED, "답하지 않음")
                if (prev != null && vs == null) store.setAnswer(d.id, Q_VS_LAST, "답하지 않음")
                render()
            })
        })
        addView(k.text("차가 본 것만 적었어요. 어땠는지는 직접 골라 주세요.", Type.Sub, Ink.text2))
        val blocks = mutableListOf<View>()
        if (heard.isNotEmpty() && helped == null) blocks.add(question(
            "길 위에서 들은 한마디, 도움이 됐나요?", heard.joinToString("  ·  ") { "“$it”" }, HELPED, helped) { store.setAnswer(d.id, Q_HELPED, it); render() })
        if (prev != null && vs == null) blocks.add(question(
            "지난번보다 어땠어요?", "${dateText(prev.date, "M월 d일")}에 같은 곳에 다녀왔어요. 숫자보다 내 느낌이 먼저예요.", VS_LAST, vs) { store.setAnswer(d.id, Q_VS_LAST, it); render() })
        val open = moments.filter { it.why !in skipped && it.why !in kept }
        if (open.isNotEmpty()) blocks.add(k.col(10).apply {
            addView(k.text("오늘 돌아볼 만한 순간", Type.Headline))
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
        blocks.forEach { addView(k.gap(8)); addView(it) }
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
