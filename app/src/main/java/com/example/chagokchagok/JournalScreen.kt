// 차곡車곡 – 일기장 · 기록 분석
// 분석은 점수가 아니라 차가 관측한 값의 변화다. 길과 날씨가 달라 바로 비교하기 어렵다는 것도 함께 적는다.
package com.example.chagokchagok

import android.view.Gravity
import android.view.View
import java.util.Locale

internal fun MainActivity.analysisScreen(): View { journalTab = 1; return journalScreen() }

internal fun MainActivity.journalScreen(): View {
    val k = kit; val me = store.driver
    val all = store.diariesOf(me)
    return k.col().apply {
        addView(k.header("일기장", if (all.isEmpty()) "아직 일기가 없어요" else "${all.size}번 달렸어요 · 첫 일기 ${dateText(all.first().date, "M월 d일")}"))
        addView(k.gap(18))
        addView(k.segmented(listOf("일기", "기록 분석"), journalTab) { journalTab = it; render() }, k.lp(if (ui.wide) ui.dp(420) else k.MATCH, k.WRAP))
        addView(k.gap(18))
        if (journalTab == 0) addView(diaryList(all)) else addView(analysis(all))
    }
}

private fun MainActivity.diaryList(all: List<Diary>): View {
    val k = kit
    if (all.isEmpty()) return k.card { addView(k.empty("book", "첫 일기를 기다리고 있어요", "기어를 D에 놓고 달린 뒤 P에 놓으면, 차가 오늘 주행을 일기로 써 줘요.")) }
    val cards = all.reversed().map { d ->
        k.card(pad = 22, onClick = { go(MainActivity.DIARY, d.id) }) {
            addView(k.row(8).apply {
                addView(k.text(dateText(d.date, "M월 d일 E요일"), Type.Caption, Ink.text2), k.lp(0, k.WRAP, 1f))
                if (d.drive.sim) addView(k.tag("모의", Ink.warn))
                if (store.posted(d.id) != null) addView(k.tag("올림", Ink.go))
            })
            addView(k.gap(6))
            addView(k.row(12).apply {
                gravity = Gravity.TOP
                addView(k.col(6).apply {
                    addView(k.text(d.title, Type.Headline))
                    addView(k.voice(d.text, 17f, Ink.text2).apply { maxLines = 2; ellipsize = android.text.TextUtils.TruncateAt.END })
                }, k.lp(0, k.WRAP, 1f))
                d.stamp?.let { addView(stamp(it, true, 2, 56)) }
            })
            addView(k.gap(8)); addView(k.text(facts(d.drive), Type.Caption, Ink.text3))
        }
    }
    return k.grid(if (ui.wide) 2 else 1, cards, 16)
}

private fun avg(xs: List<Float>) = if (xs.isEmpty()) null else xs.sum() / xs.size

private fun MainActivity.analysis(all: List<Diary>): View {
    val k = kit
    val ds = all.filter { !it.drive.sim }
    val simCount = all.size - ds.size
    if (ds.size < 2) return k.card { addView(k.empty("list", "두 번 넘게 달리면 변화가 보여요", "주행마다 차가 관측한 값을 모아 그래프로 보여 줘요. 지금 ${ds.size}번 달렸어요." + if (simCount > 0) " (모의 주행 ${simCount}번은 빼고 세요)" else "")) }
    val labels = ds.map { dateText(it.date, "M/d") }
    val km = ds.sumOf { it.drive.distKm.toDouble() }
    val list = k.col(16)
    list.addView(k.grid(if (ui.wide) 4 else 2, listOf(
        k.stat("${ds.size}번", "달린 횟수"),
        k.stat(String.format(Locale.KOREA, "%.1fkm", km), "모두 달린 거리"),
        k.stat("${ds.count { it.stamp != null }}개", "받은 도장"),
        k.stat("${store.places(store.driver).size}곳", "혼자 가 본 곳"),
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
    if (turnRatio.count { it != null } >= 1) charts.add(card("돌기 전에 켠 깜빡이", "내비 회전 안내 + 방향지시등 · 회전 100m 안에서 켰는지", TrendChart(this, ui, turnRatio, labels, { "${it.toInt()}%" }), change(turnRatio) { "${it.toInt()}%" }))
    val brakeKm = ds.map { if (it.drive.distKm >= 0.3f) it.drive.brakes / it.drive.distKm else null }
    if (brakeKm.count { it != null } >= 1) charts.add(card("1km마다 브레이크", "브레이크 페달 신호 ÷ 달린 거리 · 막히는 길에서는 늘어나요", TrendChart(this, ui, brakeKm, labels, { String.format(Locale.KOREA, "%.1f번", it) }), change(brakeKm) { String.format(Locale.KOREA, "%.1f번", it) }))
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
        k.item("천천히", "천천히 가자고 한 횟수", "breath", value = "${ds.sumOf { it.drive.calm }}번"),
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
            if (earlier.isNotEmpty()) rows.add(k.item("1km마다 브레이크", "그 전 ${earlier.size}번 → 최근 ${recent.size}번", "stop", Ink.brake, value = String.format(Locale.KOREA, "%.1f → %.1f", perKm(earlier), perKm(recent))))
            list.addView(k.groupOf(rows))
        }
    }
    list.addView(k.gap(8))
    list.addView(k.groupOf(listOf(k.toggleItem("내 기록을 가족에게 보여 주기", "켜면 가족 프로필의 일기장에 내 요즘 운전이 보여요", store.shares(store.driver), "eye") { store.setShares(store.driver, it); render() })))
    return list
}
