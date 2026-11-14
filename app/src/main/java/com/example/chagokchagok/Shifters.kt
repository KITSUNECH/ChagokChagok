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

/** 차종 → 변속 방식. 현대차 사용설명서·가격표로 확인한 것만 (설계 자료에 출처). */
internal data class CarModel(val name: String, val shifter: String, val note: String = "")

internal object Shifters {
    val all = listOf(
        Shifter("lever", "기계식 레버", "운전석과 조수석 사이", "브레이크를 밟고 레버를 맨 앞 P까지",
            listOf("P" to "맨 앞", "R" to "레버 버튼 누르고 한 칸 뒤로", "N" to "R 다음 칸", "D" to "맨 뒤"),
            "시동을 꺼도 저절로 P가 되지 않아요. 내리기 전에 직접 P에 넣어요."),
        Shifter("button", "전자식 변속 버튼", "운전석과 조수석 사이의 P·R·N·D 버튼", "P 버튼을 눌러요",
            listOf("P" to "P 버튼", "R" to "브레이크 밟고 R 버튼", "N" to "N 버튼", "D" to "브레이크 밟고 D 버튼"),
            "버튼마다 모양과 촉감이 달라요. 누른 뒤 계기판 글자가 바뀌었는지 봐요."),
        Shifter("column", "칼럼식 변속 다이얼", "운전대 오른쪽 뒤, 끝을 돌리는 레버", "끝단의 P 버튼을 눌러요",
            listOf("P" to "끝단 P 버튼", "R" to "브레이크 밟고 뒤쪽으로 돌리기 · 진동해요", "N" to "D에서 반대로 살짝", "D" to "브레이크 밟고 앞쪽으로 돌리기"),
            "돌리는 방향이 바퀴가 구르는 방향이에요. 시동을 끄면 저절로 P가 되지만 주차 브레이크는 꼭 걸어요."),
        Shifter("stalk", "칼럼식 상하 레버", "운전대 오른쪽, 예전 와이퍼 레버 자리", "레버 끝 버튼을 눌러요",
            listOf("P" to "레버 끝 버튼", "R" to "위로 끝까지 올리기", "N" to "한 칸만, 1초 유지", "D" to "아래로 끝까지 내리기"),
            "예전 차의 와이퍼 레버 자리예요. 와이퍼는 운전대 왼쪽에 있어요."),
    )
    /** 확인된 차종 (Pleos Connect 탑재 차종을 위에) */
    val models = listOf(
        CarModel("더 뉴 그랜저 (2026)", "stalk", "Pleos Connect"),
        CarModel("그랜저 GN7", "column"),
        CarModel("쏘나타 디 엣지", "column", "LPG 렌터카 트림은 레버"),
        CarModel("코나 SX2", "column"),
        CarModel("더 뉴 투싼 (2023-11~)", "column"),
        CarModel("싼타페 MX5", "column"),
        CarModel("디 올 뉴 팰리세이드 LX3", "column"),
        CarModel("아이오닉 5 · 6 · 9", "column"),
        CarModel("캐스퍼 일렉트릭", "column"),
        CarModel("쏘나타 DN8 (~2023)", "button"),
        CarModel("팰리세이드 LX2 (~2024)", "button"),
        CarModel("투싼 NX4 (~2023)", "button"),
        CarModel("아반떼 CN7", "lever"),
        CarModel("캐스퍼 (가솔린)", "lever"),
        CarModel("베뉴", "lever"),
    )
    fun byId(id: String?) = all.firstOrNull { it.id == id }

    /** 이 차의 기어: 고른 차종 → 고른 방식 */
    fun of(a: MainActivity): Shifter? = byId(models.firstOrNull { it.name == a.store.carModel }?.shifter ?: a.store.shifter)

    /** 차대번호 앞 세 자리(제조사 식별부호)로 제조사만 확인한다. 국내 차종 부호표는 공개 자료로 확인하지 못해 차종은 고르게 한다. */
    fun maker(vin: String?): String? = VinData.maker(vin?.uppercase()?.take(3))
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
            "stalk" -> {
                // 운전대 일부와 오른쪽 상하 레버 (위 R, 아래 D, 끝 버튼 P)
                c.drawArc(android.graphics.RectF(4f, 14f, 84f, 94f), 200f, 140f, false, p)
                c.drawCircle(44f, 54f, 10f, p)
                p.strokeWidth = 5f; c.drawLine(66f, 46f, 94f, 46f, p); p.strokeWidth = 3.2f
                p.style = android.graphics.Paint.Style.FILL; c.drawCircle(94f, 46f, 4.5f, p); p.style = android.graphics.Paint.Style.STROKE
                c.drawLine(84f, 34f, 84f, 24f, p); c.drawLine(84f, 58f, 84f, 68f, p)
                label("R", 84f, 14f); label("D", 84f, 80f); label("P", 97f, 30f)
            }
            else -> {
                // 운전대 일부와 오른쪽 칼럼 다이얼 (끝을 앞뒤로 돌린다)
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
    val model = Shifters.models.firstOrNull { it.name == store.carModel }
    val sh = Shifters.of(this)
    val maker = Shifters.maker(car.vin)
    val gear = car.gear.takeIf { it in listOf("P", "R", "N", "D") }
    fun pickModel() = sheets.choose("이 차는 어떤 차인가요?", "고르면 기어 쓰는 법을 맞춰 보여 드려요.", Shifters.models.map { it.name + (if (it.note.isNotEmpty()) " · ${it.note}" else "") } + "목록에 없어요 · 모양으로 고르기",
        selected = Shifters.models.indexOf(model)) { i ->
        if (i < Shifters.models.size) { store.carModel = Shifters.models[i].name; store.shifter = null; render() }
        else { store.carModel = null; store.shifter = null; render() }
    }
    return k.card(pad = 22) {
        addView(k.row(10).apply {
            addView(k.icon("gear", Ink.accent, 24))
            addView(k.text("이 차의 기어와 안전", Type.Headline), k.lp(0, k.WRAP, 1f))
            addView(k.tag(when {
                maker != null -> "차대번호 확인 · $maker"
                car.vin != null -> "차대번호 확인 · 제조사 모름"
                else -> "차대번호를 읽지 못했어요"
            }, if (maker != null) Ink.go else Ink.text2))
        })
        addView(k.gap(14))
        if (sh == null) {
            addView(k.row(12).apply {
                addView(k.text("면허시험장 차와 다를 수 있어요. 차종을 고르거나, 기어 모양을 골라 주세요.", Type.Sub, Ink.text2), k.lp(0, k.WRAP, 1f))
                addView(k.button("차종 고르기", Kit.Btn.Tinted, "car") { pickModel() })
            })
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
                addView(k.text(listOfNotNull(model?.name, sh.where).joinToString(" · "), Type.Sub, Ink.text2))
                addView(k.gap(4))
                addView(k.row(10).apply { gravity = Gravity.TOP; addView(k.icon("warning", Ink.warn, 18)); addView(k.text(sh.watch, Type.Sub), k.lp(0, k.WRAP, 1f)) })
            }, k.lp(0, k.WRAP, 1f))
            if (ui.wide) addView(gearTable(sh, gear), k.lp(ui.dp(320), k.WRAP))
        })
        if (!ui.wide) { addView(k.gap(14)); addView(gearTable(sh, gear)) }
        addView(k.gap(10))
        addView(k.row().apply {
            addView(k.text(if (gear != null) "지금 기어 · $gear  ·  근거: 현대자동차 사용설명서" else "기어 신호를 기다리는 중", Type.Caption, Ink.text2), k.lp(0, k.WRAP, 1f))
            addView(k.button("차종 바꾸기", Kit.Btn.Plain) { pickModel() })
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
