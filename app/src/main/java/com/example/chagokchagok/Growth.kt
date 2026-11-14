// 차곡차곡 – 같은 길에서 늘었는지: 앱의 판단 + 운전자의 답
// "차가 내 운전을 기억했다가, 같은 길을 다시 달릴 때 내가 늘었다는 걸 보여 준다."
//
// 앱의 판단 (규칙 기반, 기준 공개):
//  · 같은 길(같은 목적지·같은 받은 길)을 끝까지 다녀온 실제 주행끼리만 비교한다.
//  · 기준은 직전 한 번이 아니라 처음 몇 번(최대 3번)의 평균. 오늘과 조건(시간대·비·밤)이 다른 기록은 무게를 절반으로.
//  · 지표마다 무게가 다르다: 길 상황(막힘·신호)에 덜 흔들리는 습관 지표일수록 무겁다. 걸린 시간은 판정에서 뺀다.
//  · 변화는 -1..+1 로 맞춘다: (나아진 쪽으로) 차이 ÷ 둘 중 큰 값(횟수는 1 이상). 0에서 0은 변화 없음.
//  · 무게대로 합쳐 +0.10 이상 "늘었어요", -0.10 이하 "쉽지 않았어요", 그 사이 "비슷해요".
//  · 확신도는 지난 기록 수: 1번 "참고", 2–3번 "보통", 4번 이상 "믿을 만해요".
// 운전자의 답("더 편했어요/비슷했어요/더 긴장됐어요")과 합쳐 최종 한 줄을 만든다. 둘이 다르면 그 이유를 짚는다.
package com.example.chagokchagok

import android.content.Context
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import java.util.Calendar
import kotlin.math.abs
import kotlin.math.max

internal class Metric(
    val key: String, val label: String, val unit: String,
    /** 판정 무게 (0이면 참고로만 보여 준다) */
    val weight: Float,
    val higherBetter: Boolean,
    /** 길 상황(막힘·신호·날씨)에 크게 흔들리는 지표 */
    val traffic: Boolean,
    val value: (Drive) -> Float?,
) {
    fun fmt(v: Float): String = when (unit) {
        "%" -> "${v.toInt()}%"
        "초" -> "${v.toInt()}초"
        "분" -> "${v.toInt()}분"
        "번" -> if (v % 1f == 0f) "${v.toInt()}번" else String.format(java.util.Locale.KOREA, "%.1f번", v)
        else -> String.format(java.util.Locale.KOREA, "%.1f", v) + if (unit.isNotEmpty()) " $unit" else ""
    }
}

internal object Metrics {
    val all = listOf(
        Metric("signal", "돌기 전 깜빡이", "%", 3f, true, false) { d -> if (d.turns > 0) 100f * d.turnsSignaled / d.turns else null },
        Metric("lane", "차선 변경 전 기다림", "초", 3f, false, false) { d -> d.laneWaitMedian() },
        Metric("lost", "경로 재탐색", "번", 2f, false, false) { d -> if (d.sim) null else d.lostWay.toFloat() },
        Metric("park", "주차할 때 기어 바꿈", "번", 2f, false, false) { d -> if (d.completed && !d.sim) d.parkMoves.toFloat() else null },
        Metric("calm", "천천히를 청함", "번", 0f, false, false) { d -> d.calm.toFloat() },
        Metric("brake", "1km마다 브레이크", "번", 1f, false, true) { d -> if (d.distKm > 0.3f) d.brakes / d.distKm else null },
        Metric("eco", "연비", "", 1f, true, true) { d -> d.economy },
        Metric("time", "걸린 시간", "분", 0f, false, true) { d -> if (d.sim) null else max(d.minutes.toFloat(), d.seconds / 60f) },
    )
    /** "이 길의 나" 그래프에 그릴 지표 */
    val charted = listOf("signal", "lane", "brake", "eco", "time")
}

/** 시간대(출근 6–10 · 낮 10–16 · 퇴근 16–20 · 밤) + 비 + 밤 */
internal data class Cond(val band: Int, val rain: Boolean, val dark: Boolean) {
    fun label() = listOfNotNull(listOf("출근 시간", "낮", "퇴근 시간", "밤")[band], if (rain) "비" else null).joinToString(" · ")
}
internal fun Drive.cond(): Cond {
    val h = Calendar.getInstance().apply { timeInMillis = date }.get(Calendar.HOUR_OF_DAY)
    return Cond(when (h) { in 6..9 -> 0; in 10..15 -> 1; in 16..19 -> 2; else -> 3 }, rained, night)
}

internal data class Change(val metric: Metric, val base: Float, val now: Float, val score: Float, val weight: Float) {
    /** 화면에 보일 변화: "50% → 80% (+30%p)", "15초 → 6초 (−60%)" */
    fun text(): String {
        val head = "${metric.fmt(base)} → ${metric.fmt(now)}"
        val tail = when {
            metric.unit == "%" -> " (${if (now >= base) "+" else "−"}${abs(now - base).toInt()}%p)"
            base > 0f -> " (${if (now >= base) "+" else "−"}${(abs(now - base) / base * 100).toInt()}%)"
            else -> ""
        }
        return head + tail
    }
    val better get() = score > 0.02f
    val worse get() = score < -0.02f
}

internal enum class Verdict(val word: String) { UP("늘었어요"), SAME("비슷해요"), DOWN("쉽지 않았어요") }

internal data class Judgement(
    val verdict: Verdict, val score: Float, val changes: List<Change>,
    /** 비교에 쓴 지난 기록 수 */
    val history: Int, val baseCount: Int, val condDiffers: Boolean, val today: Cond,
) {
    /** 지난 기록이 한 번뿐이면 늘었다·줄었다를 말하지 않는다 (한 번의 차이는 우연일 수 있다) */
    val early get() = history < 2
    val confidence get() = when { history >= 4 -> "믿을 만해요"; history >= 2 -> "보통"; else -> "참고" }
    /** 판정에 가장 크게 기여한 변화 (최대 n개) */
    fun top(n: Int = 3) = changes.filter { it.weight > 0f && (it.better || it.worse) }.sortedByDescending { abs(it.score) * it.weight }.take(n)
}

internal object Growth {
    /** 같은 길의 지난 기록 (오래된 것부터). 끝까지 다녀온 실제 주행만 (예시 기록 포함) */
    fun history(all: List<Diary>, d: Diary): List<Diary> = all.filter { o ->
        o.id != d.id && o.date < d.date && !o.drive.sim && o.drive.completed &&
            ((d.drive.place != null && o.drive.place == d.drive.place) || (d.guideId != null && o.guideId == d.guideId))
    }.sortedBy { it.date }

    fun judge(all: List<Diary>, d: Diary): Judgement? {
        if (!d.drive.completed) return null
        val hist = history(all, d); if (hist.isEmpty()) return null
        val base = hist.take(3)
        val today = d.drive.cond()
        val condDiffers = base.none { it.drive.cond() == today }
        val changes = Metrics.all.mapNotNull { m ->
            val now = m.value(d.drive) ?: return@mapNotNull null
            // 기준: 처음 몇 번의 평균. 조건이 다른 기록은 무게 절반
            var sw = 0f; var sv = 0f
            base.forEach { o -> m.value(o.drive)?.let { v -> val w = if (o.drive.cond() == today) 1f else .5f; sw += w; sv += w * v } }
            if (sw == 0f) return@mapNotNull null
            val b = sv / sw
            val diff = if (m.higherBetter) now - b else b - now
            // 횟수 지표는 1번보다 작은 차이를 크게 부풀리지 않게 분모를 1 이상으로 (0.3번 → 0번이 -100%가 되지 않게)
            val floor = if (m.unit == "번") 1f else 0f
            val score = if (b == 0f && now == 0f) 0f else (diff / maxOf(abs(b), abs(now), floor)).coerceIn(-1f, 1f)
            // 길 상황에 흔들리는 지표는 조건이 다를 때 무게를 더 줄인다
            val w = m.weight * if (m.traffic && condDiffers) .5f else 1f
            Change(m, b, now, score, w)
        }
        val weighted = changes.filter { it.weight > 0f }
        val total = weighted.sumOf { it.weight.toDouble() }.toFloat()
        val score = if (total == 0f) 0f else weighted.sumOf { (it.score * it.weight).toDouble() }.toFloat() / total
        val v = when { score >= .10f -> Verdict.UP; score <= -.10f -> Verdict.DOWN; else -> Verdict.SAME }
        return Judgement(v, score, changes, hist.size, base.size, condDiffers, today)
    }

    /** 앱의 판단과 운전자의 답을 합친 한 줄. answer 가 없으면 앱의 판단만 */
    fun combined(j: Judgement, answer: String?): String {
        if (j.early) return "지난번 한 번과 견준 값이에요. 같은 길을 한 번 더 달리면 늘었는지 판단해요."
        val up = j.verdict == Verdict.UP
        val main = j.top(1).firstOrNull()
        val why = main?.let { "${it.metric.label}이 ${it.text()}" } ?: ""
        return when {
            answer == null || answer == "답하지 않음" -> when (j.verdict) {
                Verdict.UP -> "가장 큰 변화는 $why."
                Verdict.SAME -> "지난번과 비슷해요. 몇 번 더 달리면 흐름이 보여요."
                Verdict.DOWN -> if (j.condDiffers) "오늘은 조건(${j.today.label()})이 달랐어요." else "다음을 위해 한마디를 남겨 둘까요?"
            }
            up && answer == "더 편했어요" -> "차도 나도 늘었다고 말해요. $why"
            up && answer == "더 긴장됐어요" -> "차는 늘었다고 봐요($why). 오늘 긴장된 건 길 상황 탓일 수 있어요."
            up -> "차는 늘었다고 봐요. 느낌은 비슷했어요. 몸이 먼저 익고 있는 거예요."
            j.verdict == Verdict.SAME && answer == "더 편했어요" -> "느낌은 편했어요. 차가 본 건 아직 비슷해요. 두세 번 더 달리면 숫자도 따라올 거예요."
            j.verdict == Verdict.DOWN && answer == "더 편했어요" -> "느낌은 편했어요. 차가 본 숫자는 오늘 덜 좋았어요" + if (j.condDiffers) " (${j.today.label()})." else "."
            j.verdict == Verdict.DOWN -> "오늘은 쉽지 않은 날이었어요" + (if (j.condDiffers) " (${j.today.label()})" else "") + ". 다음을 위해 한마디를 남겨 둘까요?"
            else -> "차도 나도 지난번과 비슷하다고 봐요. 익숙해지는 중이에요."
        }
    }

    /** 앱과 운전자가 둘 다 늘었다고 했는지 (성장 바의 칸) */
    fun bothUp(j: Judgement?, answer: String?) = j != null && !j.early && j.verdict == Verdict.UP && answer == "더 편했어요"
}

/** 한 지표의 방문별 추이: 선 하나, 점, 첫 기록들의 평균(점선), 마지막 점 강조. 축은 하나. */
internal class MetricTrend(ctx: Context, private val ui: Ui, private val values: List<Float?>, private val metric: Metric, private val marks: List<String> = emptyList()) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val pts = values.withIndex().filter { it.value != null }.map { it.index to it.value!! }
        if (pts.isEmpty()) return
        val padL = ui.dp(8).toFloat(); val padR = ui.dp(56).toFloat(); val padT = ui.dp(10).toFloat(); val padB = ui.dp(22).toFloat()
        val w = width - padL - padR; val h = height - padT - padB
        val lo = minOf(0f, pts.minOf { it.second }); val hi = max(pts.maxOf { it.second } * 1.15f, lo + 1f)
        fun x(i: Int) = padL + if (values.size <= 1) w / 2 else w * i / (values.size - 1)
        fun y(v: Float) = padT + h * (1f - (v - lo) / (hi - lo))
        // 바닥선
        p.style = Paint.Style.STROKE; p.strokeWidth = ui.dp(1).toFloat(); p.color = Ink.line; p.pathEffect = null
        c.drawLine(padL, padT + h, padL + w, padT + h, p)
        // 기준(처음 3번 평균): 점선 + 라벨
        val base = pts.take(3).map { it.second }.average().toFloat()
        p.pathEffect = DashPathEffect(floatArrayOf(ui.dp(4).toFloat(), ui.dp(4).toFloat()), 0f); p.color = Ink.text3
        c.drawLine(padL, y(base), padL + w, y(base), p); p.pathEffect = null
        p.style = Paint.Style.FILL; p.textSize = ui.sp(12f); p.color = Ink.text3
        c.drawText("처음 " + metric.fmt(base), padL + ui.dp(14), if (pts.first().second >= base) y(base) + ui.dp(16) else y(base) - ui.dp(8), p)
        // 선
        p.style = Paint.Style.STROKE; p.strokeWidth = ui.dp(2).toFloat(); p.color = Ink.accent; p.strokeCap = Paint.Cap.ROUND
        val path = Path(); pts.forEachIndexed { i, (ix, v) -> if (i == 0) path.moveTo(x(ix), y(v)) else path.lineTo(x(ix), y(v)) }
        c.drawPath(path, p)
        // 점 (표면색 고리로 선과 떼어 놓는다), 마지막 점은 크게
        pts.forEachIndexed { i, (ix, v) ->
            val last = i == pts.lastIndex
            p.style = Paint.Style.FILL; p.color = Ink.surface; c.drawCircle(x(ix), y(v), ui.dp(if (last) 7 else 5).toFloat(), p)
            p.color = Ink.accent; c.drawCircle(x(ix), y(v), ui.dp(if (last) 5 else 3).toFloat(), p)
        }
        // 마지막 값만 직접 라벨
        val (li, lv) = pts.last()
        p.color = Ink.text; p.textSize = ui.sp(13f); p.typeface = ui.bold
        c.drawText(metric.fmt(lv), x(li) + ui.dp(10), y(lv) + ui.dp(5), p); p.typeface = null
        // 방문 아래 표시 (비·밤 등)
        p.color = Ink.text3; p.textSize = ui.sp(11f); p.textAlign = Paint.Align.CENTER
        marks.forEachIndexed { i, s -> if (s.isNotEmpty() && i < values.size) c.drawText(s, x(i), height - ui.dp(4).toFloat(), p) }
        p.textAlign = Paint.Align.LEFT
    }
}

// ═══════════════ 화면 ═══════════════

/** 앱의 판단 카드 (주차 후 · 일기 · 이 길의 나에서 함께 쓴다) */
internal fun MainActivity.judgementView(j: Judgement, answer: String?, compact: Boolean = false): View {
    val k = kit
    val col = if (j.early) Ink.text2 else when (j.verdict) { Verdict.UP -> Ink.go; Verdict.SAME -> Ink.text2; Verdict.DOWN -> Ink.warn }
    return k.col(8).apply {
        addView(k.row(10).apply {
            addView(k.icon(when (j.verdict) { Verdict.UP -> "chart"; Verdict.SAME -> "compare"; Verdict.DOWN -> "info" }, col, 22))
            addView(k.text(if (j.early) "차가 본 변화 · 지난번과 견줘 보면" else "차가 본 변화 · ${j.verdict.word}", Type.Headline, col), k.lp(0, k.WRAP, 1f))
            addView(k.tag("확신 ${j.confidence}", Ink.text2))
        })
        addView(k.text(Growth.combined(j, answer), Type.Body))
        if (!compact) j.top(3).forEach { c ->
            // 좁은 창에서는 이름 위, 값 아래 (이름이 글자 단위로 꺾이지 않게)
            addView((if (ui.narrow) k.col(0) else k.row(8)).apply {
                addView(k.text(c.metric.label, Type.Sub, Ink.text2), if (ui.narrow) k.lp(k.WRAP, k.WRAP) else k.lp(0, k.WRAP, 1f))
                addView(k.row(6).apply {
                    addView(k.text(c.text(), Type.BodyStrong))
                    addView(k.icon(if (c.better) "check" else "info", if (c.better) Ink.go else Ink.text3, 16))
                })
            })
        }
        addView(k.text("왜 이렇게 판단했나요", Type.Sub, Ink.accent).apply { setPadding(0, ui.dp(6), 0, ui.dp(6)); setOnClickListener { evidenceSheet(j) } })
    }
}

/** 판단의 근거: 지표별 기준·오늘·변화·무게, 규칙 */
internal fun MainActivity.evidenceSheet(j: Judgement) {
    sheets.sheet("이렇게 판단했어요", "같은 길 처음 ${j.baseCount}번의 평균과 오늘을 비교했어요 (지난 기록 ${j.history}번).") { close ->
        val k = kit
        addView(k.groupOf(j.changes.map { c ->
            k.item(c.metric.label, c.text() + if (c.weight == 0f) " · 참고만" else " · 무게 ${"%.1f".format(c.weight)}",
                if (c.better) "check" else if (c.worse) "info" else "minus", if (c.better) Ink.go else Ink.text3)
        }).apply { background = k.rounded(Ink.fill, 18) })
        addView(k.text("· 습관 지표(돌기 전 깜빡이, 차선 변경 전 기다림, 재탐색, 주차)를 무겁게, 길 상황에 흔들리는 지표(브레이크, 연비)를 가볍게 봐요. 걸린 시간은 판단에서 빼요.\n· 오늘과 조건(시간대·비·밤)이 다른 기록은 절반만 반영해요." + if (j.condDiffers) " 오늘은 ${j.today.label()}이라 조건이 달랐어요." else "" +
            "\n· 합쳐서 +10% 넘게 나아지면 ‘늘었어요’, 10% 넘게 나빠지면 ‘쉽지 않았어요’예요. 지금 점수 ${(j.score * 100).toInt()}%.", Type.Sub, Ink.text2))
        addView(k.buttons(k.button("닫기", Kit.Btn.Primary) { close() }))
    }
}

/** 이 길의 나: 같은 길을 다녀온 기록마다 지표 추이 + 앱의 판단과 내 답 */
internal fun MainActivity.roadScreen(anchorId: String?): View {
    val k = kit
    val all = store.diariesOf(store.driver)
    val anchor = all.firstOrNull { it.id == anchorId } ?: return todayScreen()
    val visits = (Growth.history(all, anchor) + anchor).filter { !it.drive.sim || it.id == anchor.id }.sortedBy { it.date }.distinctBy { it.id }
    val name = anchor.drive.place ?: store.guide(anchor.guideId)?.title ?: anchor.title
    val marks = visits.map { v -> listOfNotNull(if (v.drive.rained) "비" else null, if (v.drive.night) "밤" else null).joinToString("·") }
    val charts = Metrics.charted.mapNotNull { key ->
        val m = Metrics.all.first { it.key == key }
        val vals = visits.map { m.value(it.drive) }
        if (vals.count { it != null } < 2) null else k.card(pad = 18) {
            addView(k.row().apply {
                addView(k.text(m.label, Type.Headline), k.lp(0, k.WRAP, 1f))
                addView(k.text(if (m.weight == 0f) "참고" else if (m.higherBetter) "높을수록 좋아요" else "낮을수록 편해요", Type.Caption, Ink.text3))
            })
            addView(MetricTrend(context, ui, vals, m, marks), k.lp(k.MATCH, ui.dp(120)))
        }
    }
    val rows = visits.reversed().map { v ->
        val j = Growth.judge(all, v); val ans = store.answer(v.id, Q_VS_LAST)
        k.item("${dateText(v.date, "M월 d일 a h:mm")} · ${v.drive.cond().label()}" + (if (store.isSample(v.id)) " · 예시" else "") + if (v.drive.sim) " · 시연" else "",
            listOfNotNull(j?.let { if (it.early) "차: 판단은 다음부터" else "차: ${it.verdict.word}" }, ans?.takeIf { it != "답하지 않음" }?.let { "나: $it" }).joinToString(" · ").ifEmpty { "첫 기록" },
            if (Growth.bothUp(j, ans)) "checkCircle" else "circle", if (Growth.bothUp(j, ans)) Ink.go else Ink.text3, chevron = true) { go(MainActivity.DIARY, v.id) }
    }
    val last = visits.last(); val lj = Growth.judge(all, last)
    return k.col().apply {
        addView(k.header("이 길의 나 · $name", "${visits.size}번 다녀왔어요. 같은 길을 다시 달릴 때마다 차가 본 변화가 쌓여요.", onBack = { back() }))
        addView(k.gap(16))
        lj?.let { addView(k.card(pad = 22) { addView(judgementView(it, store.answer(last.id, Q_VS_LAST))) }); addView(k.gap(16)) }
        if (charts.isEmpty()) addView(k.card { addView(k.text("두 번 넘게 다녀오면 지표마다 그래프가 그려져요.", Type.Sub, Ink.text2)) })
        else addView(k.grid(if (ui.wide) 2 else 1, charts, 14))
        addView(k.section("다녀온 기록"))
        addView(k.groupOf(rows))
        addView(k.text("그래프의 점선은 처음 세 번의 평균이에요. 아래 ‘비·밤’은 그날의 조건이에요. 예시 기록이 섞여 있으면 일기에 ‘예시’로 표시돼요.", Type.Caption, Ink.text3).apply { setPadding(ui.dp(4), ui.dp(10), 0, 0); gravity = Gravity.START })
    }
}
