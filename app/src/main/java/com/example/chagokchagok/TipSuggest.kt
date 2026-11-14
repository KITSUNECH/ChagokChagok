// 차곡車곡 – 한마디 후보
// 한마디를 처음부터 쓰기는 어렵다. 이번 주행의 차량 기록에서 "어디서 무슨 일이 있었는지"를 찾아
// 그 자리에 남길 만한 말을 세 개까지 만든다. 여러 개를 고를 수 있고, 직접 쓸 수도 있다.
// 후보마다 근거(어떤 신호가 몇 번, 길의 어디쯤)를 함께 보여 준다.
package com.example.chagokchagok

import android.view.Gravity
import android.view.View

/**
 * 한마디 후보.
 * why: 차가 본 사실만 적는다 ("이 근처에서 내비가 경로를 다시 찾았어요"). 그게 어려웠던 순간인지는 운전자가 정한다.
 * moment: 이번 주행에서 실제로 일어난 사건에서 나온 후보 (도착 화면의 "오늘 돌아볼 만한 순간"에 보인다)
 * lat/lng: 그 순간에 저장한 위치. 없으면 진행 정도(p)로 경로에서 찾는다.
 */
internal data class TipIdea(val p: Float, val text: String, val why: String, val score: Int,
                            val moment: Boolean = false, val lat: Double? = null, val lng: Double? = null, val icon: String = "pin") {
    fun tip(t: String = text) = Tip(p, t, lat, lng)
}

internal object TipSuggest {
    private fun where(p: Float) = when { p < .3f -> "출발하고 곧"; p < .75f -> "가는 길 중간"; else -> "도착 직전" }

    /** forSelf: 다음의 나에게(반말 메모) / 아니면 건네받을 사람에게 */
    fun ideas(d: Drive, forSelf: Boolean): List<TipIdea> {
        val out = mutableListOf<TipIdea>()
        val ev = d.events
        // 브레이크가 몰린 구간 (길의 10% 안에 3번 이상). 자리는 그 구간 가운데 브레이크의 위치
        val brakes = ev.filter { it.type == Evt.BRAKE && it.value == "on" }.sortedBy { it.p }
        var best: List<Ev>? = null
        for (b in brakes) { val run = brakes.filter { it.p in b.p..(b.p + .1f) }; if (run.size >= 3 && run.size > (best?.size ?: 0)) best = run }
        best?.let { run ->
            val mid = run[run.size / 2]
            out.add(TipIdea(mid.p, if (forSelf) "여기선 조금 일찍 속도를 줄이자." else "여기는 미리 속도를 줄이면 편해요.",
                "브레이크를 ${run.size}번 밟은 구간이에요 · ${where(mid.p)}", 5, moment = true, lat = mid.lat, lng = mid.lng, icon = "stop"))
        }
        // 차선 변경: 가장 오래 기다린 한 번 (6초 넘게), 또는 깜빡이를 켰다가 바꾸지 못한 때 (조향각을 받을 때만 알 수 있다)
        // 안내는 그 구간에 들어서기 전에 들려야 하니, 자리를 경로에서 400m 앞으로 당겨 둔다
        val ls = d.lanes()
        val gaveUp = ls.firstOrNull { !it.second.moved && it.second.steerKnown }
        val longest = ls.filter { it.second.moved && it.second.wait >= 6f }.maxByOrNull { it.second.wait }
        (gaveUp ?: longest)?.let { (e, l) ->
            val cond = listOfNotNull(if (l.rain) "비" else null, if (l.dark) "밤" else null).joinToString("·")
            val fact = if (!l.moved) "깜빡이를 켰다가 차선을 바꾸지 못했어요" else "깜빡이를 켜고 ${l.wait.toInt()}초 기다린 뒤 차선을 바꿨어요" + if (!l.steerKnown) " (추정)" else ""
            val lead = if (e.lat != null && e.lng != null) Paths.before(d.path, e.lat, e.lng, 400) else null
            out.add(TipIdea(e.p, if (forSelf) "곧 합류 구간이야. 미리 차선을 옮겨 두자." else "곧 합류 구간이에요. 미리 차선을 옮겨 두면 편해요.",
                fact + " · ${where(e.p)}" + if (cond.isNotEmpty()) " · $cond" else "", 7, moment = true,
                lat = lead?.lat ?: e.lat, lng = lead?.lng ?: e.lng, icon = if (l.side == "LEFT") "turnLeft" else "turnRight"))
        }
        // 운전자가 직접 "천천히"를 청한 자리
        ev.firstOrNull { it.type == Evt.CALM }?.let { out.add(TipIdea(it.p, if (forSelf) "여기선 서두르지 말자. 천천히 가도 괜찮아." else "여기서는 천천히 가도 괜찮아요.",
            "어려웠다고 한 곳이에요 · ${where(it.p)}", 6, moment = true, lat = it.lat, lng = it.lng, icon = "breath")) }
        // 내비가 경로를 다시 찾은 곳. 길을 놓쳤는지는 모른다 (운전자가 확인한다). 위치가 없으면 조금 앞(진행 3%)을 추정한다
        ev.firstOrNull { it.type == Evt.LOST }?.let { e ->
            out.add(TipIdea(if (e.lat != null) e.p else (e.p - .03f).coerceAtLeast(0f), if (forSelf) "여기선 미리 차로를 확인하자." else "여기서는 미리 차로를 확인해요.",
                "이 근처에서 내비가 경로를 다시 찾았어요 · ${where(e.p)}", 6, moment = true, lat = e.lat, lng = e.lng, icon = "navi"))
        }
        // 회전 전 깜빡이를 켜지 못한 회전이 있었으면 (자리는 대략)
        if (d.turns > d.turnsSignaled) {
            val p = ev.firstOrNull { it.type == Evt.SIGNAL && it.value != "OFF" }?.p ?: .5f
            out.add(TipIdea((p - .02f).coerceAtLeast(0f), if (forSelf) "돌기 전에 깜빡이를 조금 더 일찍 켜자." else "돌기 전에 깜빡이를 미리 켜 두면 편해요.",
                "회전 ${d.turns}번 중 미리 켠 깜빡이 ${d.turnsSignaled}번이에요", 4, moment = true, icon = "turnRight"))
        }
        // 후진 뒤 기어 전환 (주차)
        if (d.parkMoves > 0) out.add(TipIdea(.92f, if (forSelf) "주차장에서는 천천히. 후진은 두세 번 해도 괜찮아." else "주차장이 까다로워요. 천천히 넣어도 괜찮아요.",
            "주차하며 기어를 ${d.parkMoves}번 바꿨어요 · 도착 직전", 4, moment = true, icon = "gear"))
        // 비, 밤
        if (d.rained) out.add(TipIdea(.15f, if (forSelf) "비 오는 날 이 길은 차간 거리를 넉넉히." else "비 오면 이 길은 차간 거리를 넉넉히 둬요.", "와이퍼를 1분 넘게 썼어요", 3))
        if (d.night) out.add(TipIdea(.15f, if (forSelf) "밤에는 이 길이 어두워. 전조등 먼저 확인." else "밤에는 어두운 길이에요. 전조등을 먼저 확인해요.", "18시 이후에 전조등을 켰어요", 3))
        // 기본: 도착지
        d.place?.let { out.add(TipIdea(.92f, if (forSelf) "$it 입구는 미리 보고 들어가자." else "$it 입구는 미리 보고 들어가요.", "목적지 · 도착 직전", 1)) }
        out.add(TipIdea(.5f, if (forSelf) "오늘처럼만 가면 돼." else "천천히 가도 괜찮아요. 잘하고 있어요.", "응원", 0))
        return out.distinctBy { it.text }.sortedByDescending { it.score }.take(3)
    }

    /** 도착 화면의 "오늘 돌아볼 만한 순간": 실제 사건에서 나온 것만, 세 개까지 */
    fun moments(d: Drive): List<TipIdea> = ideas(d, forSelf = true).filter { it.moment }
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
                    sheets.input("한마디", "예: 두 번째 신호에서 우회전") { onDone(chosen.map { ideas[it].tip() } + Tip(TIP_PLACES[w].second, it)) }
                }
            },
            k.button("고른 것 남기기", Kit.Btn.Primary, "check") {
                if (chosen.isEmpty()) { toast("하나 이상 골라 주세요."); return@button }
                close(); onDone(chosen.sorted().map { ideas[it].tip() })
            }, stack = false))
    }
}
