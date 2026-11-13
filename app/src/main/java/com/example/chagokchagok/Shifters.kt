// 차곡車곡 – 이 차의 기어
// 면허시험장 차는 막대(레버)지만 요즘 현대차는 버튼, 다이얼, 운전대 옆 칼럼 레버까지 다양하다.
// 차대번호(VIN, CarInfo SDK)로 차종을 알아내 변속 방식을 고르고, 모르면 운전자가 한 번 고른다.
// 차대번호는 차 안에서만 쓰고 저장하거나 밖으로 보내지 않는다.
package com.example.chagokchagok

import android.view.Gravity
import android.view.View

internal data class Shifter(
    val id: String,
    val name: String,
    val where: String,     // 어디에 있나
    val park: String,      // P 넣는 법
    val steps: List<Pair<String, String>>, // 기어 → 하는 법
    val watch: String,     // 처음에 자주 하는 실수
)

internal object Shifters {
    val all = listOf(
        Shifter("lever", "막대형 레버", "운전석과 조수석 사이", "브레이크를 밟고 레버를 맨 앞 P까지 밀어요",
            listOf("P" to "맨 앞", "R" to "버튼 누르고 한 칸 뒤로", "N" to "R 다음", "D" to "맨 뒤"),
            "R과 D 사이에 N이 있어요. 끝까지 당겼는지 계기판 글자로 확인해요."),
        Shifter("button", "버튼식", "운전석과 조수석 사이에 P·R·N·D 버튼", "P 버튼을 눌러요",
            listOf("P" to "P 버튼", "R" to "브레이크 밟고 R 버튼", "N" to "N 버튼", "D" to "브레이크 밟고 D 버튼"),
            "R은 당기는 버튼인 차도 있어요. 누른 뒤 계기판의 글자가 바뀌었는지 봐요."),
        Shifter("dial", "다이얼식", "운전석과 조수석 사이의 둥근 다이얼", "다이얼 가운데나 옆의 P 버튼을 눌러요",
            listOf("P" to "P 버튼", "R" to "브레이크 밟고 왼쪽으로 돌리기", "N" to "가운데", "D" to "브레이크 밟고 오른쪽으로 돌리기"),
            "다이얼에는 P가 없어요. 세울 때 P 버튼을 따로 눌러야 해요."),
        Shifter("column", "칼럼식 전자 변속 레버", "운전대 오른쪽 뒤의 레버", "레버 끝의 P 버튼을 눌러요",
            listOf("P" to "레버 끝 버튼", "R" to "브레이크 밟고 위로 돌리기", "N" to "한 칸만 돌리기", "D" to "브레이크 밟고 아래로 돌리기"),
            "와이퍼 레버와 헷갈리기 쉬워요. 와이퍼는 운전대 왼쪽, 기어는 오른쪽이에요."),
    )
    fun byId(id: String?) = all.firstOrNull { it.id == id }

    /** 이 차의 기어: 운전자가 고른 것 → 차대번호로 알아낸 것 */
    fun of(a: MainActivity): Shifter? = byId(a.store.shifter) ?: fromVin(a.car.vin)?.let { byId(it.second) }

    /** 차대번호 → (차종 이름, 변속 방식). 모르면 null */
    fun fromVin(vin: String?): Pair<String, String>? {
        val v = vin?.uppercase()?.takeIf { it.length >= 8 } ?: return null
        return VinData.decode(v)
    }
}

/** 변속 방식 그림: 선으로 간단히 (레버 · 버튼 · 다이얼 · 칼럼) */
internal class ShifterView(ctx: android.content.Context, private val id: String, private val color: Int, private val active: String? = null) : android.view.View(ctx) {
    private val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { strokeCap = android.graphics.Paint.Cap.ROUND; strokeJoin = android.graphics.Paint.Join.ROUND }
    override fun onDraw(c: android.graphics.Canvas) {
        val s = minOf(width, height) / 100f
        c.save(); c.translate((width - s * 100) / 2, (height - s * 100) / 2); c.scale(s, s)
        p.color = color; p.strokeWidth = 3.2f; p.style = android.graphics.Paint.Style.STROKE
        p.textSize = 15f; p.typeface = android.graphics.Typeface.DEFAULT_BOLD; p.textAlign = android.graphics.Paint.Align.CENTER
        fun label(t: String, x: Float, y: Float) { p.style = android.graphics.Paint.Style.FILL; p.alpha = if (active == null || active == t) 255 else 90; c.drawText(t, x, y + 4f, p); p.alpha = 255; p.style = android.graphics.Paint.Style.STROKE }
        when (id) {
            "lever" -> {
                c.drawRoundRect(android.graphics.RectF(30f, 70f, 70f, 92f), 8f, 8f, p)
                c.drawLine(50f, 70f, 50f, 30f, p)
                p.style = android.graphics.Paint.Style.FILL; c.drawCircle(50f, 24f, 10f, p); p.style = android.graphics.Paint.Style.STROKE
                listOf("P", "R", "N", "D").forEachIndexed { i, t -> label(t, 84f, 16f + i * 20f) }
            }
            "button" -> listOf("P", "R", "N", "D").forEachIndexed { i, t ->
                val y = 10f + i * 21f
                if (active == t) { p.style = android.graphics.Paint.Style.FILL; p.alpha = 40; c.drawRoundRect(android.graphics.RectF(32f, y, 68f, y + 17f), 6f, 6f, p); p.alpha = 255; p.style = android.graphics.Paint.Style.STROKE }
                c.drawRoundRect(android.graphics.RectF(32f, y, 68f, y + 17f), 6f, 6f, p); label(t, 50f, y + 8.5f)
            }
            "dial" -> {
                c.drawCircle(50f, 56f, 26f, p); c.drawCircle(50f, 56f, 18f, p)
                c.drawLine(50f, 38f, 50f, 46f, p)
                label("R", 16f, 30f); label("N", 50f, 18f); label("D", 84f, 30f)
                c.drawRoundRect(android.graphics.RectF(38f, 86f, 62f, 99f), 5f, 5f, p); label("P", 50f, 92.5f)
            }
            else -> {
                // 운전대 일부와 오른쪽 칼럼 레버
                c.drawArc(android.graphics.RectF(4f, 14f, 84f, 94f), 200f, 140f, false, p)
                c.drawCircle(44f, 54f, 10f, p)
                p.strokeWidth = 5f; c.drawLine(66f, 50f, 94f, 40f, p); p.strokeWidth = 3.2f
                p.style = android.graphics.Paint.Style.FILL; c.drawCircle(94f, 40f, 4.5f, p); p.style = android.graphics.Paint.Style.STROKE
                label("R", 82f, 20f); label("D", 84f, 64f); label("P", 94f, 84f)
            }
        }
        c.restore()
    }
}

/** 출발 준비: 이 차의 기어 카드 */
internal fun MainActivity.shifterCard(): View {
    val k = kit
    val chosen = Shifters.byId(store.shifter)
    val byVin = Shifters.fromVin(car.vin)
    val sh = chosen ?: byVin?.let { Shifters.byId(it.second) }
    val gear = car.gear.takeIf { it in listOf("P", "R", "N", "D") }
    return k.card(pad = 22) {
        addView(k.row(10).apply {
            addView(k.icon("gear", Ink.accent, 24))
            addView(k.text("이 차의 기어", Type.Headline), k.lp(0, k.WRAP, 1f))
            addView(k.tag(when {
                chosen != null -> "직접 고름"
                byVin != null -> "차대번호로 확인 · ${byVin.first}"
                car.vin != null -> "차대번호로 차종을 몰라요"
                else -> "차대번호를 읽지 못했어요"
            }, if (byVin != null && chosen == null) Ink.go else Ink.text2))
        })
        addView(k.gap(14))
        if (sh == null) {
            addView(k.text("면허시험장 차와 다를 수 있어요. 이 차의 기어는 어떻게 생겼나요?", Type.Sub, Ink.text2))
            addView(k.gap(12))
            addView(k.grid(if (ui.wide) 4 else 2, Shifters.all.map { s ->
                k.col(8).apply {
                    gravity = Gravity.CENTER_HORIZONTAL; setPadding(ui.dp(12), ui.dp(14), ui.dp(12), ui.dp(14))
                    addView(ShifterView(context, s.id, Ink.text), k.lp(ui.dp(84), ui.dp(84)))
                    addView(k.text(s.name, Type.BodyStrong).apply { gravity = Gravity.CENTER })
                    addView(k.text(s.where, Type.Caption, Ink.text2).apply { gravity = Gravity.CENTER })
                    k.pressable(this, k.rounded(Ink.fill, 18)) { store.shifter = s.id; render() }
                }
            }, 12))
            return@card
        }
        addView(k.row(22).apply {
            gravity = Gravity.TOP
            addView(ShifterView(context, sh.id, Ink.accent, gear).apply { background = k.rounded(Ink.accentSoft, 18) }, k.lp(ui.dp(120), ui.dp(120)))
            addView(k.col(6).apply {
                addView(k.text(sh.name, Type.Title))
                addView(k.text(sh.where, Type.Sub, Ink.text2))
                addView(k.gap(4))
                addView(k.row(10).apply { gravity = Gravity.TOP; addView(k.icon("warning", Ink.warn, 18)); addView(k.text(sh.watch, Type.Sub), k.lp(0, k.WRAP, 1f)) })
            }, k.lp(0, k.WRAP, 1f))
            if (ui.wide) addView(gearTable(sh, gear), k.lp(ui.dp(300), k.WRAP))
        })
        if (!ui.wide) { addView(k.gap(14)); addView(gearTable(sh, gear)) }
        addView(k.gap(10))
        addView(k.row().apply {
            addView(k.text(if (gear != null) "지금 기어 · $gear" else "기어 신호를 기다리는 중", Type.Caption, Ink.text2), k.lp(0, k.WRAP, 1f))
            addView(k.button("다른 방식이에요", Kit.Btn.Plain) {
                sheets.choose("이 차의 기어", null, Shifters.all.map { it.name }, selected = Shifters.all.indexOf(sh)) { i -> store.shifter = Shifters.all[i].id; render() }
            })
        })
    }
}

/** P R N D 표: 지금 기어 줄을 강조 */
private fun MainActivity.gearTable(sh: Shifter, gear: String?): View = kit.col().apply {
    val k = kit
    background = k.rounded(Ink.fill, 16)
    sh.steps.forEachIndexed { i, (g, how) ->
        val on = g == gear
        addView(k.row(14).apply {
            setPadding(ui.dp(14), ui.dp(10), ui.dp(14), ui.dp(10))
            if (on) background = k.rounded(Ink.accentSoft, 12)
            addView(k.text(g, Type.Headline, if (on) Ink.accent else Ink.text).apply { gravity = Gravity.CENTER }, k.lp(ui.dp(28), k.WRAP))
            addView(k.text(how, Type.Sub, if (on) Ink.accent else Ink.text2), k.lp(0, k.WRAP, 1f))
        })
    }
}
