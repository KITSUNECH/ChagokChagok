// 차곡車곡 – 차가 내 운전에 대해 하는 말
// 글레오가 물을 때, 화면의 "차에게 물어보기", 주차 후 첫 줄이 모두 여기서 같은 답을 받는다.
// 원칙: 문장은 이 앱이 기록한 차량 신호에서만 나온다. 못 본 것은 못 봤다고 말한다.
//  · 지난 기록이 없어도 말할 수 있는 것(오늘 몇 번 돌았고 몇 번 미리 켰는지)을 먼저,
//  · 습관(깜빡이, 차선 변경 전 기다림)은 길이 달라도 따라다니므로 "평소의 나"와 견주고,
//  · 주차는 같은 자리의 지난번과만 견준다.
package com.example.chagokchagok

import android.view.Gravity
import android.view.View
import java.util.Locale
import kotlin.math.abs

internal object Coach {
    /** 요즘 신경 쓰이는 것 (운전자가 고른다). 다 적어 두되, 이것부터 말한다. */
    val FOCUS = listOf("lane" to "차선 변경", "turn" to "깜빡이", "park" to "주차", "brake" to "브레이크")
    fun label(key: String?) = FOCUS.firstOrNull { it.first == key }?.second
    fun icon(key: String) = when (key) { "lane" -> "compare"; "turn" -> "turnRight"; "park" -> "flag"; else -> "stop" }

    /** 평소의 나: 지금까지의 실제 주행(최근 5번)에서 본 습관 */
    class Usual(val signalPct: Float?, val laneWait: Float?, val drives: Int)
    fun usual(before: List<Diary>): Usual {
        val ds = before.filter { !it.drive.sim }.takeLast(5).map { it.drive }
        val t = ds.sumOf { it.turns }; val s = ds.sumOf { it.turnsSignaled }
        val waits = ds.mapNotNull { it.laneWaitMedian() }
        return Usual(if (t >= 3) 100f * s / t else null, if (waits.isNotEmpty()) waits.average().toFloat() else null, ds.size)
    }

    /** 이 주제로 오늘 말할 거리가 있는지 */
    fun informative(key: String, d: Drive) = when (key) {
        "turn" -> d.turns > 0
        "lane" -> d.lanes().isNotEmpty()
        "park" -> d.completed && d.parkMoves > 0
        else -> d.brakes > 0 || d.hardBrakes() > 0
    }

    /**
     * 한 주제에 대한 답. soFar 는 아직 달리는 중(지금까지의 기록)일 때.
     * before 는 이 주행보다 앞선 일기들 (평소의 나, 같은 자리의 지난번을 찾는 데 쓴다).
     */
    fun about(key: String, d: Drive, before: List<Diary> = emptyList(), soFar: Boolean = false): String {
        val u = usual(before)
        return when (key) {
            "turn" -> {
                if (d.turns == 0) return if (soFar) "아직 내비가 안내한 회전이 없었어요." else "내비가 안내한 회전이 없어서 깜빡이는 볼 수 없었어요."
                val head = if (d.turnsSignaled == d.turns) "${d.turns}번 돌았고, 모두 돌기 전에 깜빡이를 켰어요."
                    else "${d.turns}번 돌았고, 그중 ${d.turnsSignaled}번은 돌기 전에 깜빡이를 켰어요."
                val now = 100f * d.turnsSignaled / d.turns
                head + (u.signalPct?.let { p -> when { now - p >= 15f -> " 평소(${p.toInt()}%)보다 잘 챙겼어요."; p - now >= 15f -> " 평소에는 ${p.toInt()}%쯤 켰어요."; else -> " 평소와 비슷해요." } } ?: "")
            }
            "lane" -> {
                val ls = d.lanes().map { it.second }
                if (ls.isEmpty()) return if (soFar) "아직 기록된 차선 변경이 없어요." else "기록된 차선 변경이 없어요."
                val med = d.laneWaitMedian() ?: ls.map { it.wait }.average().toFloat()
                val short = ls.count { it.wait < LANE_FLOOR }
                "차선을 ${ls.size}번 바꿨고, 깜빡이를 켜고 보통 ${med.toInt()}초 뒤에 넘어갔어요." +
                    (if (short > 0) " 그중 ${short}번은 3초가 안 됐어요. 조금 더 일찍 켜면 뒤차가 알아채기 쉬워요." else "") +
                    (u.laneWait?.takeIf { abs(it - med) >= 2f }?.let { " 평소에는 ${it.toInt()}초쯤 기다렸어요." } ?: "")
            }
            "park" -> {
                if (soFar || !d.completed) return "주차는 도착한 뒤에 볼 수 있어요."
                if (d.parkMoves == 0) return "후진 없이 세웠어요."
                val fixes = d.parkMoves - 1
                val head = if (fixes == 0) "후진을 넣고 한 번에 자리를 잡았어요." else "주차하면서 앞뒤로 ${fixes}번 고쳐 잡았어요."
                // 주차는 자리마다 달라서 같은 곳의 지난번과만 견준다
                val last = d.place?.let { p -> before.lastOrNull { !it.drive.sim && it.drive.place == p && it.drive.completed && it.drive.parkMoves > 0 } }
                head + (last?.let { val f = it.drive.parkMoves - 1; if (f == fixes) " 같은 자리에서 지난번과 같아요." else " 같은 자리에서 지난번에는 ${f}번이었어요." } ?: "")
            }
            else -> (if (d.distKm > 0.3f) "브레이크는 1km마다 ${String.format(Locale.KOREA, "%.1f", d.brakes / d.distKm)}번 밟았어요." else "브레이크는 ${d.brakes}번 밟았어요.") +
                // 급정거는 속도 신호를 받은 주행에서만 말한다 (못 본 것을 "없었다"고 하지 않는다)
                if (!d.speedSeen()) "" else d.hardBrakes().let { h -> if (h == 0) " 급하게 선 적은 없었어요." else " 그중 ${h}번은 급하게 섰어요." }
        }
    }

    /** 오늘 운전 전체: 신경 쓰이는 것부터, 말할 거리가 있는 것만 세 가지까지 */
    fun today(d: Drive, before: List<Diary>, focus: String?, soFar: Boolean = false): String {
        val keys = (listOfNotNull(focus) + FOCUS.map { it.first }).distinct().filter { informative(it, d) || (it == focus) }
        val lines = keys.take(3).map { about(it, d, before, soFar) }
        return if (lines.isEmpty()) (if (soFar) "아직 차가 본 조작이 많지 않아요. 조금 더 달리고 물어봐 주세요." else "오늘은 차가 본 조작이 많지 않았어요.") else lines.joinToString(" ")
    }
}

/** 지금 운전자가 고른 "요즘 신경 쓰이는 것" (전부·모르겠어요는 특정 주제가 아니라 null) */
internal fun MainActivity.focusKey(): String? = store.answer("focus_${store.driver}", "focus")?.takeIf { Coach.label(it) != null }

/**
 * 차에게 물어보기: 글레오에게 "오늘 운전 어땠어?"라고 묻는 것과 같은 답을 버튼 한 번으로 바로 듣는다.
 * 달리는 중이면 지금까지의 기록, 아니면 그 일기(없으면 가장 최근 일기)로 답한다. 고르는 단계 없이 바로 말하고 화면에도 보여 준다.
 */
internal fun MainActivity.askCar(about: Diary? = null) {
    val all = store.diariesOf(store.driver)
    val soFar = trip.active && about == null
    val target = about ?: all.lastOrNull()
    if (!soFar && target == null) { toast("아직 기록이 없어요. 한 번 달리고 나서 물어봐 주세요."); return }
    val drive = if (soFar) trip.recorder.summary(store.driver) else target!!.drive
    val before = if (soFar) all else all.filter { it.date < target!!.date }
    readAloud(if (soFar) "지금까지의 운전" else dateText(drive.date, "M월 d일") + "의 운전", Coach.today(drive, before, if (soFar) trip.focus else focusKey(), soFar))
}

/** 홈: 요즘 신경 쓰이는 것을 고른다. 고르면 달리는 동안과 주차 후에 그것부터 말해 준다. */
internal fun MainActivity.focusCard(): View {
    val k = kit; val me = store.driver
    val now = store.answer("focus_$me", "focus").orEmpty()
    val choices = Coach.FOCUS + listOf("all" to "전부", "unsure" to "모르겠어요")
    return k.card(pad = 22) {
        addView(k.text("요즘 제일 신경 쓰이는 건?", Type.Headline))
        addView(k.gap(4))
        addView(k.text(when {
            now == "all" -> "전부 적어 두고, 눈에 띄는 것부터 말해 줄게요."
            now == "unsure" -> "괜찮아요. 달려 보고 차가 본 것부터 말해 줄게요."
            Coach.label(now) != null -> "다 적어 두되, ${Coach.label(now)}부터 말해 줄게요."
            else -> "고르면 달리는 동안과 주차 후에 그것부터 말해 줄게요."
        }, Type.Sub, Ink.text2))
        addView(k.gap(12))
        addView(k.flow(10).apply {
            choices.forEach { (key, label) -> addView(k.chip(label, key == now) { store.setAnswer("focus_$me", "focus", if (key == now) "" else key); render() }) }
        })
    }
}

/**
 * 일기 맨 위 "오늘 차가 본 것": 같은 길의 지난 기록이 없어 견줄 수 없는 날(첫 주행, 처음 간 길)에 보여 준다.
 * 첫 일기는 오늘이 기준이 된다고 말하고, 그다음부터는 평소의 나와 견준다.
 */
internal fun MainActivity.carSawCard(d: Diary): View? {
    if (d.driver != store.driver) return null
    val k = kit
    val before = store.diariesOf(d.driver).filter { it.date < d.date }
    val focus = store.answer(d.id, "focus")?.takeIf { Coach.label(it) != null }
    val keys = (listOfNotNull(focus) + Coach.FOCUS.map { it.first }).distinct().filter { Coach.informative(it, d.drive) || it == focus }
    val real = before.count { !it.drive.sim }
    return k.card(pad = 26) {
        addView(k.row(10).apply { addView(k.icon("car", Ink.accent, 24)); addView(k.text("오늘 차가 본 것", Type.Title), k.lp(0, k.WRAP, 1f)) })
        addView(k.gap(4))
        addView(k.text(when {
            real == 0 -> "첫 일기예요. 오늘이 기준이 되고, 다음 주행부터 견줘 드릴게요."
            d.drive.place != null || d.guideId != null -> "이 길은 처음이라 지난번과는 견줄 수 없어요. 습관은 평소의 나와 견줬어요."
            else -> "습관은 평소의 나와 견줬어요. 주차한 곳에 이름을 붙이면 다음부터 같은 길끼리 견줘요."
        }, Type.Sub, Ink.text2))
        addView(k.gap(14))
        if (keys.isEmpty()) addView(k.text("오늘은 차가 본 조작이 많지 않았어요.", Type.Body))
        else addView(k.groupOf(keys.map { key ->
            k.item(Coach.about(key, d.drive, before), Coach.label(key) + if (key == focus) " · 신경 쓰인다고 한 것" else "", Coach.icon(key), if (key == focus) Ink.accent else Ink.text2)
        }).apply { background = k.rounded(Ink.fill, 18) })
    }
}


/** 홈: 평소의 나. 최근 주행에서 차가 본 습관 두 가지. 누르면 흐름을 본다. 기록이 모자라면 무엇이 쌓이는지 알려 준다. */
internal fun MainActivity.usualCard(): View {
    val k = kit
    val ds = store.diariesOf(store.driver).filter { !it.drive.sim }
    val u = Coach.usual(ds)
    fun stat(value: String, label: String) = k.col(2).apply { addView(k.text(value, Type.Large)); addView(k.text(label, Type.Caption, Ink.text2)) }
    return k.card(pad = 22, onClick = if (ds.size >= 2) ({ go(MainActivity.ANALYSIS) }) else null) {
        addView(k.row(10).apply {
            addView(k.icon("car", Ink.accent, 22))
            addView(k.text("평소의 나", Type.Headline), k.lp(0, k.WRAP, 1f))
            addView(k.text(if (u.drives == 0) "아직 기록이 없어요" else "최근 ${u.drives}번", Type.Sub, Ink.text2))
            if (ds.size >= 2) addView(k.icon("chevron", Ink.text3, 20))
        })
        addView(k.gap(10))
        if (u.signalPct == null && u.laneWait == null) addView(k.text("달릴수록 차가 본 내 습관이 여기에 쌓여요. 돌기 전 깜빡이, 차선 변경 전 기다림.", Type.Sub, Ink.text2))
        else addView(k.row(28).apply {
            gravity = Gravity.TOP
            addView(stat(u.signalPct?.let { "${it.toInt()}%" } ?: "–", "돌기 전 깜빡이"))
            addView(stat(u.laneWait?.let { "${it.toInt()}초" } ?: "–", "차선 변경 전 기다림"))
        })
    }
}


/** 홈의 "내비 열기": 어디로 갈지 받아서 내비의 장소 검색 화면으로 넘긴다 (주차 중에만 글자를 받는다) */
internal fun MainActivity.findInNavi() {
    sheets.input("어디로 갈까요?", "예: 회사, 양재 코스트코", ok = "내비에서 찾기") { q -> showNavi(q.trim().ifEmpty { null }) }
}

/**
 * 내비를 앞으로 부른다. ① 차에 깔린 지도·내비 앱을 표준 안드로이드 방식으로 실행해 앞으로 가져오고,
 * ② Pleos 내비에 화면을 요청한다 (검색어가 있으면 장소 검색 화면: NaviHelper requestNavigationScreen).
 * 화면을 좌우로 나누는 것은 차(시스템)가 정한다. 앱이 시킬 수는 없다.
 */
internal fun MainActivity.showNavi(keyword: String?) {
    val launched = try {
        startActivity(android.content.Intent.makeMainSelectorActivity(android.content.Intent.ACTION_MAIN, android.content.Intent.CATEGORY_APP_MAPS).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)); true
    } catch (_: Throwable) { false }
    window.decorView.postDelayed({
        val asked = car.openNavi(keyword)
        if (!asked && !launched) toast(if (car.naviConnected) "내비 화면을 열지 못했어요. 아래 내비 단추로 열어 주세요." else "내비가 연결되지 않았어요. 아래 내비 단추로 열어 주세요.")
    }, if (launched) 700L else 0L)
}
