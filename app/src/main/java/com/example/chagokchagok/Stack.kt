// 차곡車곡 – 성장 바
// 해낸 일이 블록 하나씩 아래부터 쌓인다. 익히기 → 달리기 → 건네기 세 단계, 단계마다 색이 다르다.
// 무엇을 했는지만 센다 (점수나 남과의 비교 없음). 모의 주행은 세지 않는다.
//
// 처음 설정에서는 13칸 게이지를 고른 운전 경험만큼 채운다 (초보는 조금, 주행은 절반쯤, 차곡은 가득).
// 홈에 처음 오면 그 게이지가 압축되며 단계마다 한 칸으로 접힌다: 1칸, 2칸, 3칸.
// 접히면서 모두 익히기 색으로 바뀐다. 경험이 많아도 홈의 바는 익히기 단계에서 시작하고, 칸 수로만 앞서 간다.
// 처음부터 꽉 찬 바를 주지 않고, 나머지는 직접 해내며 쌓게 한다.
package com.example.chagokchagok

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.LinearInterpolator
import kotlin.math.pow

internal data class Block(val id: String, val stage: Int, val title: String, val hint: String, val done: Boolean)

/** 단계 이름: 그 단계에서 하는 일 (차를 익히고, 내 길을 달리고, 길을 건넨다) */
internal val STAGES = listOf("익히기", "달리기", "건네기")
/** 단계 색: 현대자동차 CI 전용색상에서 (익히기 Active Blue · 달리기 Hyundai Blue/밤에는 Sky Blue · 건네기 Active Red). 색약 구분 검사 통과 */
internal fun stageColor(stage: Int) = Color.parseColor(if (Ink.night) listOf("#00AAD2", "#AACAE6", "#E63312")[stage] else listOf("#00AAD2", "#002C5F", "#E63312")[stage])

/** 운전 경험 → 시작 단계 (1 익히기까지 · 2 달리기까지 · 3 건네기까지) */
internal fun levelOf(type: String) = when (type) { "초보" -> 1; "오랜만", "새 차" -> 2; else -> 3 }

/** 처음 설정 게이지: 13칸 중 단계별로 채우는 양 */
internal object Gauge {
    const val SLOTS = 13
    /** 단계가 끝나는 칸 (익히기 4 · 달리기 9 · 건네기 13) */
    val ends = intArrayOf(4, 9, 13)
    fun cap(level: Int) = ends[(level - 1).coerceIn(0, 2)]
    fun stageOfSlot(i: Int) = when { i < ends[0] -> 0; i < ends[1] -> 1; else -> 2 }
}

/** 지금 운전자의 성장 블록 전부 (쌓인 순서대로 정렬) */
internal fun MainActivity.blocks(): List<Block> {
    val me = store.driver
    val ds = store.diariesOf(me).filter { !it.drive.sim }
    val myGuides = store.guides().filter { it.owner == me }
    val level = levelOf(store.typeOf(me))
    val start = listOfNotNull(
        Block("seat", 0, "운전석에 앉았어요", "시작 안내를 마쳐요", store.onboarded),
        if (level >= 2) Block("career", 0, "운전 경험으로 한 칸", "처음 설정에서 고른 운전 경험", true) else null,
        if (level >= 3) Block("seasoned", 0, "익숙한 손으로 한 칸", "처음 설정에서 고른 운전 경험", true) else null,
    )
    return start + listOf(
        Block("prep", 0, "차와 친해졌어요", "출발 준비에서 네 가지를 해 봐요", store.prepDone(me)),
        Block("first", 0, "첫 주행 기록", "기어 D로 출발해 P로 주차해요", ds.isNotEmpty()),
        Block("feel", 0, "일기에 내 한 줄", "일기에서 ‘오늘 한 줄 남기기’", ds.any { store.feeling(it.id) != null }),
        Block("follow", 1, "받은 길을 끝까지", "건네받은 길로 목적지까지", ds.any { it.guideId != null && it.drive.completed }),
        Block("signal", 1, "돌기 전에 깜빡이", "내비가 알려 준 회전 전에 미리 켜요", ds.any { it.drive.turnsSignaled > 0 }),
        Block("self", 1, "다음의 나에게 한마디", "일기에서 ‘다음의 나에게 한마디’", store.guides().any { it.owner == me && it.kind == "나" }),
        Block("grad", 1, "길 하나 졸업", "같은 길을 세 번 끝까지", store.guidesFor(me).any { store.graduated(me, it.id) }),
        Block("new", 1, "혼자 새 길", "받은 길 없이 처음 가 보는 곳", store.places(me).isNotEmpty()),
        Block("give", 2, "길을 건넸어요", "일기에서 ‘이 길 건네주기’", myGuides.isNotEmpty()),
        Block("cheer", 2, "응원을 보냈어요", "받은 소식에 응원 보내기", store.flag("cheered_$me")),
        Block("post", 2, "함께 차곡에 올렸어요", "일기를 함께 차곡에 올리기", ds.any { store.posted(it.id) != null }),
        Block("passed", 2, "내 길을 누군가 달렸어요", "내가 건넨 길을 다른 사람이 따라 달리면", myGuides.any { g -> store.diaries().any { it.guideId == g.id && it.driver != me && !it.drive.sim } }),
    )
}

private fun ease(x: Float) = if (x < .5f) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2

/**
 * 세로 바. 바닥부터 쌓인 블록, 위로는 아직 빈 자리.
 * fresh: 새로 쌓인 블록 수 (위에서 내려와 앉는다).
 * squeeze: 처음 설정 게이지에서 채웠던 칸 수. 0이 아니면 그 칸들이 단계별 한 칸으로 압축되는 모션을 먼저 보여 준다.
 */
internal class StackView(ctx: Context, private val ui: Ui, private val blocks: List<Block>, private val fresh: Int, private val squeeze: Int = 0) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var drop = if (fresh > 0 && squeeze == 0) 1f else 0f
    private var sq = if (squeeze > 0) 0f else 1f

    init {
        if (squeeze > 0) post {
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = 1900; startDelay = 500; interpolator = LinearInterpolator()
                addUpdateListener { sq = it.animatedValue as Float; invalidate() }
            }.start()
        } else if (fresh > 0) post {
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 700; interpolator = android.view.animation.DecelerateInterpolator(2.2f)
                addUpdateListener { drop = it.animatedValue as Float; invalidate() }
            }.start()
        }
    }

    override fun onDraw(c: Canvas) {
        val n = blocks.size; if (n == 0) return
        val done = blocks.filter { it.done }.sortedBy { it.stage }
        val w = width.toFloat(); val h = height.toFloat()
        val gap = ui.dp(4).toFloat()
        val bh = ((h - gap * (n - 1)) / n).coerceAtMost(ui.dp(40).toFloat())
        val r = ui.dp(7).toFloat()
        fun slotTop(i: Int) = h - (i + 1) * bh - i * gap
        // 빈 자리: 옅게 채운 칸
        p.style = Paint.Style.FILL; p.color = Ink.fill
        for (i in done.size until n) c.drawRoundRect(RectF(0f, slotTop(i), w, slotTop(i) + bh), r, r, p)

        if (sq < 1f) {
            // 압축 모션: 게이지에서 채웠던 칸들이 단계별 한 칸으로 모인다 (앞 20%는 게이지 그대로, 겹치듯 부드럽게).
            // 다 모이면 위 칸들이 아래에서부터 익히기 색으로 차오른다. 색을 섞지 않아 중간에 탁한 색이 나오지 않는다.
            val counts = IntArray(3); for (i in 0 until squeeze) counts[Gauge.stageOfSlot(i)]++
            val seen = IntArray(3)
            for (i in 0 until squeeze) {
                val s = Gauge.stageOfSlot(i); val k = seen[s]++
                val t = ease(((sq - .20f - i * .008f) / .48f).coerceIn(0f, 1f))
                val target = s.coerceAtMost(done.size - 1).coerceAtLeast(0)   // 단계 s의 조각들은 아래에서 s번째 칸으로
                val sliceH = bh / counts[s]
                val y0 = slotTop(i); val h0 = bh
                val y1 = slotTop(target) + (counts[s] - 1 - k) * sliceH; val h1 = sliceH
                val y = y0 + (y1 - y0) * t; val hh = h0 + (h1 - h0) * t
                val rr = r * (1 - t) + 1f * t
                p.color = stageColor(s)
                c.drawRoundRect(RectF(0f, y, w, y + hh + (if (t > .95f) .8f else 0f)), rr, rr, p)
            }
            // 익히기 색으로 차오르기: 달리기 칸 다음 건네기 칸 순서로, 아래에서 위로
            for (s in 1..2) {
                if (counts[s] == 0) continue
                val f = ease(((sq - (.70f + (s - 1) * .09f)) / .14f).coerceIn(0f, 1f))
                if (f <= 0f) continue
                val top = slotTop(s.coerceAtMost(done.size - 1)); val bot = top + bh
                c.save(); c.clipRect(0f, bot - bh * f, w, bot)
                p.color = stageColor(0); c.drawRoundRect(RectF(0f, top, w, bot), r, r, p)
                c.restore()
            }
            // 마지막에 진짜 블록이 덮는다 (같은 색이라 이음매가 보이지 않는다)
            val cover = ((sq - .94f) / .06f).coerceIn(0f, 1f)
            if (cover > 0f) done.forEachIndexed { i, b -> p.color = stageColor(b.stage); p.alpha = (255 * cover).toInt(); c.drawRoundRect(RectF(0f, slotTop(i), w, slotTop(i) + bh), r, r, p); p.alpha = 255 }
            return
        }
        // 쌓인 블록
        done.forEachIndexed { i, b ->
            var y = slotTop(i)
            val isNew = i >= done.size - fresh
            if (isNew) y -= drop * (h * .6f)
            p.color = stageColor(b.stage); p.alpha = if (isNew) (255 * (1 - drop * .6f)).toInt() else 255
            c.drawRoundRect(RectF(0f, y, w, y + bh), r, r, p)
            // 윗면의 옅은 빛 (블록 느낌)
            p.color = Color.WHITE; p.alpha = 46
            c.drawRoundRect(RectF(ui.dp(4).toFloat(), y + ui.dp(3), w - ui.dp(4), y + ui.dp(5)), r, r, p)
            p.alpha = 255
        }
    }
}

/**
 * 처음 설정 오른쪽의 게이지: 13칸, 왼쪽에 단계 이름. filled 칸까지 채운다 (바뀌면 부드럽게 따라간다).
 * 막대는 오른쪽 끝에 붙여 홈의 성장 바(StackView)와 같은 x 위치·칸 높이가 되게 한다.
 */
internal class GaugeView(ctx: Context, private val ui: Ui, private val from: Int, private val filled: Int, private val labelW: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var shown = from.toFloat()

    init {
        if (from != filled) post {
            ValueAnimator.ofFloat(from.toFloat(), filled.toFloat()).apply {
                duration = 300L + 80L * kotlin.math.abs(filled - from); interpolator = android.view.animation.DecelerateInterpolator(1.8f)
                addUpdateListener { shown = it.animatedValue as Float; invalidate() }
            }.start()
        } else shown = filled.toFloat()
    }

    override fun onDraw(c: Canvas) {
        val n = Gauge.SLOTS
        val h = height.toFloat()
        val x0 = labelW.toFloat(); val x1 = width.toFloat()
        // StackView 와 같은 칸 계산
        val gap = ui.dp(4).toFloat()
        val bh = ((h - gap * (n - 1)) / n).coerceAtMost(ui.dp(40).toFloat())
        val r = ui.dp(7).toFloat()
        fun top(i: Int) = h - (i + 1) * bh - i * gap
        for (i in 0 until n) {
            val s = Gauge.stageOfSlot(i)
            val f = (shown - i).coerceIn(0f, 1f)
            p.style = Paint.Style.FILL; p.color = Ink.fill
            c.drawRoundRect(RectF(x0, top(i), x1, top(i) + bh), r, r, p)
            if (f > 0f) {
                p.color = stageColor(s); p.alpha = (255 * f).toInt()
                val dy = (1 - f) * bh * 1.4f
                c.drawRoundRect(RectF(x0, top(i) - dy, x1, top(i) - dy + bh), r, r, p)
                p.alpha = 255
            }
        }
        // 단계 이름과 경계선 (막대 왼쪽)
        p.textSize = ui.sp(if (labelW > ui.dp(50)) 14f else 12f) * resources.displayMetrics.density; p.typeface = ui.semibold
        p.textAlign = Paint.Align.RIGHT
        var lo = 0
        Gauge.ends.forEachIndexed { s, end ->
            val yTop = top(end - 1); val yBot = top(lo) + bh
            val reached = shown >= end - .01f
            p.color = if (reached) stageColor(s) else Ink.text3
            val lx = x0 - ui.dp(8)
            p.strokeWidth = ui.dp(2).toFloat(); p.style = Paint.Style.STROKE
            c.drawLine(lx, yTop + ui.dp(3), lx, yBot - ui.dp(3), p)
            p.style = Paint.Style.FILL
            c.drawText(STAGES[s], lx - ui.dp(7), (yTop + yBot) / 2 + p.textSize * .35f, p)
            lo = end
        }
        p.textAlign = Paint.Align.LEFT
    }
}

/** 오른쪽 세로 막대: 이름, 탑, 쌓인 수. 누르면 전부 보여 준다. */
internal fun MainActivity.stackRail(): View {
    val k = kit
    val bs = blocks()
    val doneCount = bs.count { it.done }
    val seen = store.stackSeen(store.driver)
    val squeeze = Onboard.squeeze; Onboard.squeeze = 0
    val fresh = if (squeeze > 0) 0 else (doneCount - seen).coerceAtLeast(0)
    if (squeeze > 0) store.setStackSeen(store.driver, doneCount)
    if (fresh > 0) {
        store.setStackSeen(store.driver, doneCount)
        if (seen > 0) {
            val newest = bs.filter { it.done }.sortedBy { it.stage }.takeLast(fresh)
            window.decorView.postDelayed({ chime(); toast("차곡! ${newest.joinToString(", ") { it.title }}") }, 450)
        }
    }
    val stage = bs.firstOrNull { !it.done }?.stage ?: 2
    return k.col(10).apply {
        setPadding(ui.dp(14), ui.dp(24), ui.dp(14), ui.dp(20))
        setBackgroundColor(Ink.surface)
        addView(k.text("성장", Type.Micro, Ink.text2).apply { gravity = android.view.Gravity.CENTER })
        addView(k.text("$doneCount", Type.Title).apply { gravity = android.view.Gravity.CENTER })
        addView(StackView(context, ui, bs, fresh, squeeze), k.lp(k.MATCH, 0, 1f))
        addView(k.text(STAGES[stage], Type.Micro, stageColor(stage)).apply { gravity = android.view.Gravity.CENTER })
        k.pressable(this, null) { stackSheet() }
    }
}

internal fun MainActivity.stackSheet() {
    val bs = blocks()
    sheets.sheet("성장 ${bs.count { it.done }} / ${bs.size}", "해낼 때마다 한 칸씩 쌓여요. 차를 익히고, 내 길을 달리고, 길을 건네요.") { _ ->
        val k = kit
        STAGES.forEachIndexed { s, name ->
            val mine = bs.filter { it.stage == s }
            addView(k.row(8).apply {
                addView(android.view.View(context).apply { background = k.rounded(stageColor(s), 4) }, k.lp(ui.dp(14), ui.dp(14)))
                addView(k.text("$name  ${mine.count { it.done }}/${mine.size}", Type.Headline))
            })
            addView(k.groupOf(mine.map { b ->
                k.item(b.title, if (b.done) (if (b.id == "career" || b.id == "seasoned") "운전 경험으로 채웠어요" else "쌓였어요") else b.hint, if (b.done) "checkCircle" else "circle", if (b.done) stageColor(s) else Ink.text3)
            }).apply { background = k.rounded(Ink.fill, 18) })
        }
    }
}
