// 차곡車곡 – 일기장 · 기록 분석
// 분석은 점수가 아니라 차가 관측한 값의 변화다. 길과 날씨가 달라 바로 비교하기 어렵다는 것도 함께 적는다.
package com.example.chagokchagok

import android.view.Gravity
import android.view.View
import java.util.Locale

/** 평소의 나: 길과 상관없이 따라다니는 습관의 흐름 (홈의 "평소의 나"에서 들어온다) */
internal fun MainActivity.analysisScreen(): View {
    val k = kit
    return k.col().apply {
        addView(k.header("평소의 나", "어느 길에서든 따라다니는 습관이에요. 길과 날씨가 달라 그대로 견주기는 어려워요.", onBack = { back() }))
        addView(k.gap(18))
        addView(analysis(store.diariesOf(store.driver)))
    }
}

/** 일기장에서 고른 길 (null = 전체) */
internal var journalRoad: String? = null

internal fun MainActivity.journalScreen(): View {
    val k = kit; val me = store.driver
    val all = store.diariesOf(me)
    val roads = all.mapNotNull { it.drive.place }.groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
    if (journalRoad != null && journalRoad !in roads) journalRoad = null
    // 자주 간 곳 네 곳만 바로 보이고, 나머지는 ‘다른 곳’에서 고른다 (고른 곳은 앞으로 나온다)
    val quick = (listOfNotNull(journalRoad?.takeIf { it !in roads.take(4) }) + roads).take(4)
    val shown = (if (journalRoad == null) all else all.filter { it.drive.place == journalRoad }).reversed()
    return k.col().apply {
        addView(k.header("일기장", if (all.isEmpty()) "아직 일기가 없어요" else "일기 ${all.size}장 · 첫 일기 ${dateText(all.first().date, "M월 d일")}"))
        addView(k.gap(14))
        if (roads.isNotEmpty()) {
            addView(k.flow(10).apply {
                addView(k.chip("전체", journalRoad == null) { journalRoad = null; render() })
                quick.forEach { r -> addView(k.chip(r, journalRoad == r, "pin") { journalRoad = r; render() }) }
                val more = roads - quick.toSet()
                if (more.isNotEmpty()) addView(k.chip("다른 곳 ${more.size}", false, "list") {
                    sheets.choose("다녀온 곳", "많이 간 순서예요.", more.map { r -> "$r · ${all.count { it.drive.place == r }}번" }) { i -> journalRoad = more[i]; render() }
                })
            })
            addView(k.gap(14))
            // 길을 고르면 그 길에서의 변화로 바로 갈 수 있다
            journalRoad?.let { r -> shown.firstOrNull()?.takeIf { shown.size >= 2 }?.let { last ->
                addView(k.button("‘$r’에서 내가 늘었는지 보기", Kit.Btn.Tinted, "chart") { go(MainActivity.ROAD, last.id) }, k.lp(if (ui.wide) k.WRAP else k.MATCH, k.WRAP)); addView(k.gap(14)) } }
        }
        addView(dayList(shown))
    }
}

/**
 * 하루를 한 면으로: 출근·잠깐 들른 곳·퇴근처럼 하루에 여러 번 주차해도 그날의 일기는 한 장이다.
 * 맨 위에 그날을 한 줄로 요약하고(차가 본 것을 합쳐서), 아래에 주행마다 한 줄씩. 누르면 그 주행의 일기 한 장으로 간다.
 */
private fun MainActivity.dayList(list: List<Diary>): View {
    val k = kit
    if (list.isEmpty()) return k.card { addView(k.empty("book", "첫 일기를 기다리고 있어요", "기어를 D에 놓고 달린 뒤 P에 놓으면 일기 한 장이 생겨요.")) }
    val all = store.diariesOf(store.driver)
    val gray = android.graphics.Color.parseColor("#7A766C")
    val days = list.groupBy { dateText(it.date, "yyyyMMdd") }.values   // list 가 최근 순이라 묶음도 최근 날부터
    val cards = days.map { ds ->
        val day = ds.first()
        val km = ds.sumOf { it.drive.distKm.toDouble() }
        val turns = ds.sumOf { it.drive.turns }; val signaled = ds.sumOf { it.drive.turnsSignaled }
        val lanes = ds.sumOf { it.drive.lanes().size }; val hard = ds.sumOf { it.drive.hardBrakes() }
        val summary = listOfNotNull(
            "주행 ${ds.size}번" + if (km > 0.05) String.format(Locale.KOREA, " · %.1fkm", km) else "",
            if (turns > 0) "돌기 전 깜빡이 $signaled/$turns" else null,
            if (lanes > 0) "차선 변경 ${lanes}번" else null,
            if (hard > 0) "급하게 선 것 ${hard}번" else null,
        ).joinToString(" · ")
        k.row(18).apply {
            gravity = Gravity.TOP
            setPadding(ui.dp(22), ui.dp(20), ui.dp(18), ui.dp(14)); background = k.rounded(Ink.paper, 20, 1, Ink.line)
            addView(k.col(0).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(k.text(dateText(day.date, "d"), Type.Display, Ink.ink).apply { gravity = Gravity.CENTER })
                addView(k.text(dateText(day.date, "M월 E요일"), Type.Micro, gray).apply { gravity = Gravity.CENTER })
            }, k.lp(ui.dp(84), k.WRAP))
            addView(View(context).apply { setBackgroundColor(Ink.grid) }, k.lp(ui.dp(2), k.MATCH))
            addView(k.col(4).apply {
                addView(k.text(summary, Type.Caption, gray))
                ds.sortedBy { it.date }.forEach { d ->
                    val mine = store.feeling(d.id); val j = Growth.judge(all, d)
                    addView(k.row(10).apply {
                        minimumHeight = ui.touch(56)
                        addView(k.text(dateText(d.date, "a h:mm"), Type.Caption, gray), k.lp(ui.dp(86), k.WRAP))
                        addView(k.col(2).apply {
                            addView(k.voice(mine?.let { "“$it”" } ?: (d.drive.place ?: d.title), 19f, Ink.ink).apply { maxLines = 1; ellipsize = android.text.TextUtils.TruncateAt.END })
                            val tags = listOfNotNull(
                                if (mine != null) d.drive.place?.let { it to Ink.accent } else null,
                                if (j != null && !j.early) ("차: " + j.verdict.word) to when (j.verdict) { Verdict.UP -> Ink.go; Verdict.SAME -> gray; Verdict.DOWN -> Ink.warn } else null,
                                if (d.drive.sim) "모의 주행" to Ink.warn else null,
                                if (store.isSample(d.id)) "예시" to gray else null,
                            )
                            if (tags.isNotEmpty()) addView(k.flow(6).apply { tags.forEach { (t, c) -> addView(k.tag(t, c)) } })
                        }, k.lp(0, k.WRAP, 1f))
                        addView(k.icon("chevron", gray, 18))
                        k.pressable(this, null) { go(MainActivity.DIARY, d.id) }
                    })
                }
            }, k.lp(0, k.WRAP, 1f))
        }
    }
    return k.grid(if (ui.wide) 2 else 1, cards, 16)
}

/** 일기 면: 왼쪽에 날짜를 크게, 오른쪽에 내 한 줄(없으면 차가 적은 첫 문장), 아래에 길과 차의 판단 */
private fun MainActivity.diaryList(list: List<Diary>): View {
    val k = kit
    if (list.isEmpty()) return k.card { addView(k.empty("book", "첫 일기를 기다리고 있어요", "기어를 D에 놓고 달린 뒤 P에 놓으면 일기 한 장이 생겨요.")) }
    val all = store.diariesOf(store.driver)
    val cards = list.map { d ->
        val mine = store.feeling(d.id)
        val j = Growth.judge(all, d)
        k.row(18).apply {
            gravity = Gravity.TOP
            setPadding(ui.dp(22), ui.dp(20), ui.dp(22), ui.dp(20))
            // 날짜 (일기장처럼 크게)
            addView(k.col(0).apply {
                gravity = Gravity.CENTER_HORIZONTAL
                addView(k.text(dateText(d.date, "d"), Type.Display, Ink.ink).apply { gravity = Gravity.CENTER })
                addView(k.text(dateText(d.date, "M월 E요일"), Type.Micro, Ink.paperLine).apply { gravity = Gravity.CENTER; setTextColor(android.graphics.Color.parseColor("#7A766C")) })
            }, k.lp(ui.dp(84), k.WRAP))
            addView(View(context).apply { setBackgroundColor(Ink.grid) }, k.lp(ui.dp(2), k.MATCH))
            addView(k.col(8).apply {
                addView(k.voice(mine?.let { "“$it”" } ?: d.text.substringBefore(". ").trimEnd('.') + ".", if (mine != null) 22f else 19f, if (mine != null) Ink.ink else android.graphics.Color.parseColor("#5B5750")).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
                addView(k.flow(8).apply {
                    d.drive.place?.let { addView(k.tag(it, Ink.accent)) }
                    if (j != null && !j.early) addView(k.tag("차: " + j.verdict.word, when (j.verdict) { Verdict.UP -> Ink.go; Verdict.SAME -> Ink.text2; Verdict.DOWN -> Ink.warn }))
                    if (mine == null && d.driver == store.driver) addView(k.tag("내 한 줄을 기다려요", android.graphics.Color.parseColor("#7A766C")))
                    if (d.drive.sim) addView(k.tag("모의 주행", Ink.warn))
                    if (store.isSample(d.id)) addView(k.tag("예시", android.graphics.Color.parseColor("#7A766C")))
                })
                addView(k.text(facts(d.drive), Type.Caption, Ink.ink).apply { setTextColor(android.graphics.Color.parseColor("#7A766C")) })
            }, k.lp(0, k.WRAP, 1f))
            k.pressable(this, k.rounded(Ink.paper, 20, 1, Ink.line)) { go(MainActivity.DIARY, d.id) }
        }
    }
    return k.grid(if (ui.wide) 2 else 1, cards, 16)
}

private fun avg(xs: List<Float>) = if (xs.isEmpty()) null else xs.sum() / xs.size

private fun MainActivity.analysis(all: List<Diary>): View {
    val k = kit
    val ds = all.filter { !it.drive.sim }
    val simCount = all.size - ds.size
    if (ds.size < 2) return k.card { addView(k.empty("list", "두 번째 주행부터 변화가 보여요", "주행마다 차가 관측한 값을 모아 그래프로 보여 줘요. 지금 ${ds.size}번 달렸어요." + if (simCount > 0) " (모의 주행 ${simCount}번은 빼고 세요)" else "")) }
    val labels = ds.map { dateText(it.date, "M/d") }
    val km = ds.sumOf { it.drive.distKm.toDouble() }
    val list = k.col(16)
    list.addView(k.grid(if (ui.wide) 3 else 1, listOf(
        k.stat("${ds.size}번", "달린 횟수"),
        k.stat(String.format(Locale.KOREA, "%.1fkm", km), "모두 달린 거리"),
        k.stat("${store.places(store.driver).size}곳", "다녀온 곳"),
    ), 14))
    list.addView(k.row(10).apply {
        addView(k.icon("info", Ink.text2, 18))
        addView(k.text("점수가 아니라 차가 관측한 값이에요. 길·날씨·시간대가 달라서 그대로 비교하기는 어려워요." + if (simCount > 0) " 모의 주행 ${simCount}번은 뺐어요." else "", Type.Caption, Ink.text2), k.lp(0, k.WRAP, 1f))
    })

    fun card(title: String, source: String, chart: View, summary: String) = k.card(pad = 22) {
        addView(k.text(title, Type.Headline))
        addView(k.text(source, Type.Caption, Ink.text3))
        addView(k.gap(10)); addView(chart, k.lp(k.MATCH, ui.dp(170)))
        addView(k.gap(6)); addView(k.text(summary, Type.Sub, Ink.text2))
    }
    fun change(xs: List<Float?>, f: (Float) -> String): String {
        val v = xs.filterNotNull(); if (v.size < 2) return "값이 있는 주행이 아직 적어요."
        val n = minOf(3, v.size / 2).coerceAtLeast(1)
        return "처음 ${n}번 평균 ${f(avg(v.take(n))!!)} → 최근 ${n}번 평균 ${f(avg(v.takeLast(n))!!)}"
    }

    val charts = mutableListOf<View>()
    val dist = ds.map { it.drive.distKm }
    charts.add(card("주행마다 달린 거리", "주행거리계 · 위치 경로", BarChart(this, ui, dist, labels) { String.format(Locale.KOREA, "%.1fkm", it) },
        "가장 길게 달린 날 ${String.format(Locale.KOREA, "%.1fkm", dist.maxOrNull() ?: 0f)}"))
    val turnRatio = ds.map { if (it.drive.turns > 0) it.drive.turnsSignaled * 100f / it.drive.turns else null }
    if (turnRatio.count { it != null } >= 1) charts.add(card("돌기 전에 켠 깜빡이", "내비 회전 안내 + 방향지시등 · 회전 200m 안에서 켜져 있었는지", TrendChart(this, ui, turnRatio, labels, { "${it.toInt()}%" }), change(turnRatio) { "${it.toInt()}%" }))
    val brakeKm = ds.map { if (it.drive.distKm >= 0.3f) it.drive.brakes / it.drive.distKm else null }
    if (brakeKm.count { it != null } >= 1) charts.add(card("브레이크 밟은 횟수 (1km당)", "브레이크 페달 신호 ÷ 달린 거리 · 막히는 길에서는 늘어나요", TrendChart(this, ui, brakeKm, labels, { String.format(Locale.KOREA, "%.1f번", it) }), change(brakeKm) { String.format(Locale.KOREA, "%.1f번", it) }))
    val park = ds.map { if (it.drive.parkMoves > 0) it.drive.parkMoves.toFloat() else null }
    if (park.count { it != null } >= 1) charts.add(card("후진 뒤 기어 전환", "기어 신호 · 주차할 때 앞뒤로 오간 횟수 (주차 성공 여부는 알 수 없어요)", TrendChart(this, ui, park, labels, { "${it.toInt()}번" }), change(park) { "${it.toInt()}번" }))
    val longest = ds.map { if (it.drive.signals > 0) it.drive.longestSignalSec.toFloat() else null }
    if (longest.count { it != null } >= 1) charts.add(card("깜빡이를 가장 오래 켜 둔 시간", "방향지시등 신호 · 회전 뒤 끄는 걸 잊으면 길어져요", TrendChart(this, ui, longest, labels, { "${it.toInt()}초" }), change(longest) { "${it.toInt()}초" }))
    list.addView(k.grid(if (ui.wide) 2 else 1, charts, 16))

    // 언제, 어떤 날 달렸는지
    list.addView(k.section("어떤 날 달렸나요"))
    list.addView(k.groupOf(listOf(
        k.item("밤에 달린 날", "전조등 신호 + 18시~6시", "moon", value = "${ds.count { it.drive.night }}번"),
        k.item("와이퍼를 1분 넘게 켠 날", "와이퍼 신호", "cloud", Ink.rain, value = "${ds.count { it.drive.rained }}번"),
        k.item("누군가를 태운 날", "좌석 감지 신호", "people", value = "${ds.count { it.drive.passenger }}번"),
        k.item("받은 길을 따라 달린 날", "끝까지 달린 날만", "road", value = "${ds.count { it.guideId != null && it.drive.completed }}번"),
        k.item("어려웠던 곳", "‘여기 어려웠어요’를 남긴 횟수", "breath", value = "${ds.sumOf { it.drive.calm }}번"),
    )))

    // 물려받고 돌려드리기: 보여 주기로 한 가족의 요즘 운전
    val fam = store.drivers.filter { it != store.driver && store.shares(it) && store.relation(it) == "가족" }
    fam.forEach { other ->
        val od = store.diariesOf(other).filter { !it.drive.sim }
        if (od.size >= 2) {
            list.addView(k.section("${Writer.poss(other)} 요즘 운전"))
            val recent = od.takeLast(3); val earlier = od.dropLast(3).takeLast(3)
            fun perKm(l: List<Diary>) = l.sumOf { it.drive.brakes }.toFloat() / l.sumOf { it.drive.distKm.toDouble() }.toFloat().coerceAtLeast(0.1f)
            val rows = mutableListOf(k.item("최근 ${recent.size}번 중 밤에 달린 날", null, "moon", value = "${recent.count { it.drive.night }}번"))
            if (earlier.isNotEmpty()) rows.add(k.item("브레이크 밟은 횟수 (1km당)", "그 전 ${earlier.size}번 → 최근 ${recent.size}번", "stop", Ink.brake, value = String.format(Locale.KOREA, "%.1f → %.1f", perKm(earlier), perKm(recent))))
            list.addView(k.groupOf(rows))
        }
    }
    list.addView(k.gap(8))
    list.addView(k.groupOf(listOf(k.toggleItem("내 기록을 가족에게 보여 주기", "켜면 가족 프로필의 일기장에 내 요즘 운전이 보여요", store.shares(store.driver), "eye") { store.setShares(store.driver, it); render() })))
    return list
}
