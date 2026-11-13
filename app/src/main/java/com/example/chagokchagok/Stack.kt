// 차곡車곡 – 차곡 탑
// 해낸 일이 블록 하나씩 아래부터 쌓인다. 초보 → 주행 → 소셜 순서의 세 단계, 단계마다 색이 다르다.
// 무엇을 했는지만 센다 (점수나 남과의 비교 없음). 모의 주행은 세지 않는다.
package com.example.chagokchagok

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.view.animation.OvershootInterpolator

internal data class Block(val id: String, val stage: Int, val title: String, val hint: String, val done: Boolean)

internal val STAGES = listOf("초보", "주행", "소셜")
internal fun stageColor(stage: Int) = Color.parseColor(if (Ink.night) listOf("#3987e5", "#199e70", "#d95926")[stage] else listOf("#2a78d6", "#1baf7a", "#eb6834")[stage])

/** 지금 운전자의 차곡 블록 전부 (쌓인 순서대로 정렬) */
internal fun MainActivity.blocks(): List<Block> {
    val me = store.driver
    val ds = store.diariesOf(me).filter { !it.drive.sim }
    val km = ds.sumOf { it.drive.distKm.toDouble() }
    val myGuides = store.guides().filter { it.owner == me }
    val list = listOf(
        Block("seat", 0, "운전석에 앉았어요", "시작 안내를 마쳐요", store.onboarded),
        Block("prep", 0, "차와 친해졌어요", "출발 준비에서 네 가지를 해 봐요", store.prepDone(me)),
        Block("first", 0, "첫 주행 기록", "기어 D로 출발해 P로 주차해요", ds.isNotEmpty()),
        Block("feel", 0, "일기에 내 한 줄", "일기에서 ‘오늘 한 줄 남기기’", ds.any { store.feeling(it.id) != null }),
        Block("follow", 1, "받은 길을 끝까지", "건네받은 길로 목적지까지", ds.any { it.guideId != null && it.drive.completed }),
        Block("signal", 1, "돌기 전에 깜빡이", "내비가 알려 준 회전 전에 미리 켜요", ds.any { it.drive.turnsSignaled > 0 }),
        Block("grad", 1, "길 하나 졸업", "같은 길을 세 번 끝까지", store.guidesFor(me).any { store.graduated(me, it.id) }),
        Block("new", 1, "혼자 새 길", "받은 길 없이 처음 가 보는 곳", store.places(me).isNotEmpty()),
        Block("10km", 1, "함께 달린 10km", "모두 더해 10km", km >= 10),
        Block("give", 2, "길을 건넸어요", "일기에서 ‘이 길 건네주기’", myGuides.isNotEmpty()),
        Block("cheer", 2, "응원을 보냈어요", "받은 소식에 응원 보내기", store.flag("cheered_$me")),
        Block("post", 2, "함께 차곡에 올렸어요", "일기를 함께 차곡에 올리기", ds.any { store.posted(it.id) != null }),
        Block("passed", 2, "내 길을 누군가 달렸어요", "내가 건넨 길을 다른 사람이 따라 달리면", myGuides.any { g -> store.diaries().any { it.guideId == g.id && it.driver != me && !it.drive.sim } }),
    )
    return list
}

/** 세로 탑. 바닥부터 쌓인 블록, 위로는 아직 빈 자리. */
internal class StackView(ctx: Context, private val ui: Ui, private val blocks: List<Block>, private val fresh: Int) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private var drop = if (fresh > 0) 1f else 0f   // 새로 쌓인 블록이 떨어지는 정도 (1 → 0)

    init {
        if (fresh > 0) post {
            android.animation.ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 650; interpolator = OvershootInterpolator(1.2f)
                addUpdateListener { drop = it.animatedValue as Float; invalidate() }
            }.start()
        }
    }

    override fun onDraw(c: Canvas) {
        val n = blocks.size; if (n == 0) return
        val done = blocks.filter { it.done }.sortedBy { it.stage }
        val w = width.toFloat(); val h = height.toFloat()
        val gap = ui.dp(4).toFloat()
        val bh = ((h - gap * (n - 1)) / n).coerceAtMost(ui.dp(44).toFloat())
        val total = bh * n + gap * (n - 1)
        val top0 = h - total
        val r = ui.dp(8).toFloat()
        // 빈 자리 (점선)
        p.style = Paint.Style.STROKE; p.strokeWidth = ui.dp(1.5f).toFloat(); p.color = Ink.line
        p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(ui.dp(4).toFloat(), ui.dp(4).toFloat()), 0f)
        for (i in done.size until n) { val y = h - (i + 1) * bh - i * gap; c.drawRoundRect(RectF(ui.dp(2).toFloat(), y, w - ui.dp(2), y + bh), r, r, p) }
        p.pathEffect = null
        // 쌓인 블록 (바닥부터, 엇갈리게 살짝 밀어 쌓은 느낌)
        p.style = Paint.Style.FILL
        done.forEachIndexed { i, b ->
            var y = h - (i + 1) * bh - i * gap
            val isNew = i >= done.size - fresh
            if (isNew) y -= drop * (top0 + total * .5f)
            val shift = if (i % 2 == 0) 0f else ui.dp(3).toFloat()
            p.color = stageColor(b.stage); p.alpha = if (isNew) (255 * (1 - drop * .5f)).toInt() else 255
            c.drawRoundRect(RectF(ui.dp(2) + shift, y, w - ui.dp(5) + shift, y + bh), r, r, p)
            // 위 면의 밝은 줄 (블록 느낌)
            p.color = Color.WHITE; p.alpha = 60
            c.drawRoundRect(RectF(ui.dp(6) + shift, y + ui.dp(3), w - ui.dp(9) + shift, y + ui.dp(6)), r, r, p)
            p.alpha = 255
        }
    }
}

/** 오른쪽 세로 막대: 제목, 탑, 쌓인 수. 누르면 전부 보여 준다. */
internal fun MainActivity.stackRail(): View {
    val k = kit
    val bs = blocks()
    val doneCount = bs.count { it.done }
    val seen = store.stackSeen(store.driver)
    val fresh = (doneCount - seen).coerceAtLeast(0)
    if (fresh > 0) {
        store.setStackSeen(store.driver, doneCount)
        if (seen > 0 || doneCount > 1) {
            val newest = bs.filter { it.done }.sortedBy { it.stage }.takeLast(fresh)
            window.decorView.postDelayed({ chime(); toast("차곡! ${newest.joinToString(", ") { it.title }}") }, 450)
        }
    }
    val stage = bs.firstOrNull { !it.done }?.stage ?: 2
    return k.col(8).apply {
        setPadding(ui.dp(10), ui.dp(24), ui.dp(10), ui.dp(18))
        setBackgroundColor(Ink.surface)
        addView(k.text("차곡", Type.Micro, Ink.text2).apply { gravity = android.view.Gravity.CENTER })
        addView(k.text("$doneCount", Type.Title).apply { gravity = android.view.Gravity.CENTER })
        addView(StackView(context, ui, bs, fresh), k.lp(k.MATCH, 0, 1f))
        addView(k.text(STAGES[stage], Type.Micro, stageColor(stage)).apply { gravity = android.view.Gravity.CENTER })
        k.pressable(this, null) { stackSheet() }
    }
}

internal fun MainActivity.stackSheet() {
    val bs = blocks()
    sheets.sheet("차곡 ${bs.count { it.done }} / ${bs.size}", "해낼 때마다 한 칸씩 쌓여요. 초보에서 주행, 주행에서 소셜로.") { _ ->
        val k = kit
        STAGES.forEachIndexed { s, name ->
            val mine = bs.filter { it.stage == s }
            addView(k.row(8).apply {
                addView(android.view.View(context).apply { background = k.rounded(stageColor(s), 4) }, k.lp(ui.dp(14), ui.dp(14)))
                addView(k.text("$name  ${mine.count { it.done }}/${mine.size}", Type.Headline))
            })
            addView(k.groupOf(mine.map { b ->
                k.item(b.title, if (b.done) "쌓였어요" else b.hint, if (b.done) "checkCircle" else "circle", if (b.done) stageColor(s) else Ink.text3)
            }).apply { background = k.rounded(Ink.fill, 18) })
        }
    }
}
