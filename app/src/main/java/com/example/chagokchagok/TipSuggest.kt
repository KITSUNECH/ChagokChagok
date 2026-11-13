// 차곡車곡 – 한마디 후보
// 한마디를 처음부터 쓰기는 어렵다. 이번 주행의 차량 기록에서 "어디서 무슨 일이 있었는지"를 찾아
// 그 자리에 남길 만한 말을 세 개까지 만든다. 여러 개를 고를 수 있고, 직접 쓸 수도 있다.
// 후보마다 근거(어떤 신호가 몇 번, 길의 어디쯤)를 함께 보여 준다.
package com.example.chagokchagok

import android.view.Gravity
import android.view.View

internal data class TipIdea(val p: Float, val text: String, val why: String, val score: Int)

internal object TipSuggest {
    private fun where(p: Float) = when { p < .3f -> "출발하고 곧"; p < .75f -> "가는 길 중간"; else -> "도착 직전" }

    /** forSelf: 다음의 나에게(반말 메모) / 아니면 건네받을 사람에게 */
    fun ideas(d: Drive, forSelf: Boolean): List<TipIdea> {
        val out = mutableListOf<TipIdea>()
        val ev = d.events
        // 브레이크가 몰린 구간 (길의 10% 안에 3번 이상)
        val brakes = ev.filter { it.type == Evt.BRAKE && it.value == "on" }.map { it.p }.sorted()
        var best: Pair<Float, Int>? = null
        for (b in brakes) { val n = brakes.count { it in b..(b + .1f) }; if (n >= 3 && (best == null || n > best.second)) best = b + .05f to n }
        best?.let { (p, n) -> out.add(TipIdea(p, if (forSelf) "여기서 브레이크를 자주 밟았어. 조금 일찍 속도를 줄이자." else "여기는 브레이크를 자주 밟게 돼요. 미리 속도를 줄여요.", "브레이크 ${n}번 · ${where(p)}", 5)) }
        // 숨 고르기
        ev.firstOrNull { it.type == Evt.CALM }?.let { out.add(TipIdea(it.p, if (forSelf) "여기서 숨을 골랐어. 천천히 가도 괜찮아." else "여기서 긴장될 수 있어요. 천천히 가도 괜찮아요.", "숨 고르기 · ${where(it.p)}", 6)) }
        // 길을 놓친 곳
        ev.firstOrNull { it.type == Evt.LOST }?.let { out.add(TipIdea((it.p - .03f).coerceAtLeast(0f), if (forSelf) "여기서 길을 놓쳤어. 미리 차로를 확인하자." else "여기서 길을 놓치기 쉬워요. 미리 차로를 확인해요.", "내비 경로 재탐색 · ${where(it.p)}", 6)) }
        // 회전 전 깜빡이를 놓친 적이 있으면 첫 회전 자리
        if (d.turns > d.turnsSignaled) {
            val p = ev.firstOrNull { it.type == Evt.SIGNAL && it.value != "OFF" }?.p ?: .5f
            out.add(TipIdea((p - .02f).coerceAtLeast(0f), if (forSelf) "돌기 전에 깜빡이를 조금 더 일찍 켜자." else "돌기 전에 깜빡이를 미리 켜 두면 편해요.", "회전 ${d.turns}번 중 깜빡이 ${d.turnsSignaled}번", 4))
        }
        // 후진 뒤 기어 전환 (주차)
        if (d.parkMoves > 0) out.add(TipIdea(.92f, if (forSelf) "주차장에서는 천천히. 후진은 두세 번 해도 괜찮아." else "주차장이 까다로워요. 천천히 넣어도 괜찮아요.", "후진 뒤 기어 전환 ${d.parkMoves}번 · 도착 직전", 4))
        // 비, 밤
        if (d.rained) out.add(TipIdea(.15f, if (forSelf) "비 오는 날 이 길은 차간 거리를 넉넉히." else "비 오면 이 길은 차간 거리를 넉넉히 둬요.", "와이퍼 1분 넘게", 3))
        if (d.night) out.add(TipIdea(.15f, if (forSelf) "밤에는 이 길이 어두워. 전조등 먼저 확인." else "밤에는 어두운 길이에요. 전조등을 먼저 확인해요.", "18시 이후 전조등", 3))
        // 기본: 도착지
        d.place?.let { out.add(TipIdea(.92f, if (forSelf) "$it 입구는 미리 보고 들어가자." else "$it 입구는 미리 보고 들어가요.", "목적지 · 도착 직전", 1)) }
        out.add(TipIdea(.5f, if (forSelf) "오늘처럼만 가면 돼." else "천천히 가도 괜찮아요. 잘하고 있어요.", "응원", 0))
        return out.distinctBy { it.text }.sortedByDescending { it.score }.take(3)
    }
}

/** 후보 세 개 중 여러 개 고르기 + 직접 쓰기. 고른 것을 Tip 목록으로 돌려준다. */
internal fun MainActivity.pickTips(title: String, sub: String, ideas: List<TipIdea>, onDone: (List<Tip>) -> Unit) {
    val chosen = mutableSetOf<Int>()
    sheets.sheet(title, sub) { close ->
        val k = kit
        val boxes = mutableListOf<IconView>()
        val rows = ideas.mapIndexed { i, t ->
            val box = IconView(context, "circle", Ink.line); boxes.add(box)
            k.row(14).apply {
                gravity = Gravity.TOP
                setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(14))
                addView(box, k.lp(ui.dp(26), ui.dp(26)))
                addView(k.col(4).apply {
                    addView(k.text("“${t.text}”", Type.BodyStrong))
                    addView(k.row(6).apply { addView(k.icon("chart", Ink.text3, 14)); addView(k.text("근거 · ${t.why}", Type.Caption, Ink.text2)) })
                }, k.lp(0, k.WRAP, 1f))
                setOnClickListener {
                    if (!chosen.remove(i)) chosen.add(i)
                    box.set(if (i in chosen) "checkCircle" else "circle", if (i in chosen) Ink.accent else Ink.line)
                }
            }
        }
        addView(k.text("이번 주행 기록으로 만든 후보예요. 여러 개 골라도 돼요.", Type.Sub, Ink.text2))
        addView(k.groupOf(rows).apply { background = k.rounded(Ink.fill, 18) })
        addView(k.buttons(
            k.button("직접 쓰기", Kit.Btn.Secondary, "pencil") {
                close()
                sheets.choose("어디쯤에서 들을 말인가요?", null, TIP_PLACES.map { it.first }) { w ->
                    sheets.input("한마디", "예: 두 번째 신호에서 우회전") { onDone(chosen.map { Tip(ideas[it].p, ideas[it].text) } + Tip(TIP_PLACES[w].second, it)) }
                }
            },
            k.button("고른 것 남기기", Kit.Btn.Primary, "check") {
                if (chosen.isEmpty()) { toast("하나 이상 골라 주세요."); return@button }
                close(); onDone(chosen.sorted().map { Tip(ideas[it].p, ideas[it].text) })
            }, stack = false))
    }
}
