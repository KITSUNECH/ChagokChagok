// 차곡車곡 – 처음 시작
// 한 화면에 질문 하나. 넓은 화면은 왼쪽에 차곡 탑을 두고, 한 단계 넘어갈 때마다 블록이 쌓인다.
// 혼자 쓰는 사람도 처음부터 자연스럽게: "혼자 써요"는 "함께 써요"와 같은 무게의 선택지다.
package com.example.chagokchagok

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout

internal object Onboard {
    var step = 0; var name = "나"; var type = "초보"; var solo: Boolean? = null
    val others = mutableListOf<String>()
    var shownBlocks = 0
    const val LAST = 5
}

/** 고르는 타일: 아이콘, 제목, 설명. 고르면 테두리가 강조색 */
private fun MainActivity.tile(title: String, sub: String, icon: String, selected: Boolean, onClick: () -> Unit): View {
    val k = kit
    return k.col(8).apply {
        setPadding(ui.dp(22), ui.dp(20), ui.dp(22), ui.dp(20))
        addView(k.row().apply {
            addView(FrameLayout(context).apply {
                background = k.rounded(if (selected) Ink.accent else Ink.accentSoft, 12)
                addView(IconView(context, icon, if (selected) Ink.onAccent else Ink.accent), FrameLayout.LayoutParams(ui.dp(24), ui.dp(24), Gravity.CENTER))
            }, k.lp(ui.dp(44), ui.dp(44)))
            addView(k.fill())
            addView(k.icon(if (selected) "checkCircle" else "circle", if (selected) Ink.accent else Ink.line, 26))
        })
        addView(k.gap(4))
        addView(k.text(title, Type.Headline))
        addView(k.text(sub, Type.Sub, Ink.text2))
        k.pressable(this, k.rounded(Ink.surface, 22, if (selected) 3 else 1, if (selected) Ink.accent else Ink.line), onClick)
    }
}

internal fun MainActivity.onboardScreen(): View {
    val k = kit; val O = Onboard
    fun next() { O.step = (O.step + 1).coerceAtMost(Onboard.LAST); render() }
    fun prev() { O.step = (O.step - 1).coerceAtLeast(0); render() }

    var canNext = true
    var nextLabel = "다음"
    var onNext: () -> Unit = { next() }
    var nameField: EditText? = null

    val content = k.col(18).apply {
        when (O.step) {
            0 -> {
                addView(k.text("차곡車곡", Type.Display))
                addView(k.text("차로 쓰는 성장일기", Type.Title, Ink.text2))
                addView(k.gap(4))
                addView(k.text("가고 싶은 곳으로 운전하면, 차가 오늘의 운전을 일기로 남겨 줘요. 해낸 일은 오른쪽 탑에 한 칸씩 쌓여요.", Type.Body, Ink.text2))
                addView(k.gap(6))
                addView(k.groupOf(listOf(
                    k.item("내가 가는 길이 차곡이 돼요", "깜빡이, 브레이크, 와이퍼… 차가 본 그대로 일기로", "book"),
                    k.item("그 자리에서 들려주는 한마디", "가족이 건넨 한마디, 지난번의 내가 남긴 한마디", "quote"),
                    k.item("운전 중에는 말로만", "화면은 한 줄, 나머지는 주차한 뒤에", "speaker"),
                )))
                nextLabel = "시작하기"
            }
            1 -> {
                addView(k.text("운전석에 앉는 분을\n뭐라고 부를까요?", Type.Large))
                val preview = k.avatar(O.name, 72)
                addView(k.row(16).apply {
                    addView(preview)
                    nameField = EditText(context).apply {
                        setText(O.name); setSelection(O.name.length); textSize = ui.sp(Type.Title.size); typeface = ui.bold; setTextColor(Ink.text)
                        background = k.rounded(Ink.fill, 16); setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(16)); setSingleLine()
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun afterTextChanged(s: android.text.Editable?) { val t = s.toString().trim().ifEmpty { "나" }; preview.text = t.take(1); (preview.background as GradientDrawable).setColor(Ink.person(t)) }
                            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        })
                    }
                    addView(nameField, k.lp(0, k.WRAP, 1f))
                })
                addView(k.text("앱 안에서만 쓰는 이름이에요. ‘나’로 두어도 돼요.", Type.Sub, Ink.text2))
                onNext = { O.name = nameField?.text?.toString()?.trim()?.ifEmpty { "나" } ?: "나"; next() }
            }
            2 -> {
                addView(k.text("${O.name} 님은 운전을\n얼마나 해 봤나요?", Type.Large))
                addView(k.text("고른 것에 따라 차곡이가 말하는 양과 먼저 보여 줄 것이 달라져요. 나중에 바꿀 수 있어요.", Type.Sub, Ink.text2))
                val opts = listOf(
                    listOf("초보", "면허 딴 지 얼마 안 됐어요", "자주, 구체적으로 알려 줄게요", "steering"),
                    listOf("오랜만", "오랜만에 다시 운전해요", "바뀐 법규부터 알려 줄게요", "clock"),
                    listOf("새 차", "이 차가 처음이에요", "조작부터 같이 익혀요", "car"),
                    listOf("익숙", "운전이 익숙해요", "오늘을 기록하고 길을 건네요", "road"),
                )
                addView(k.grid(2, opts.map { (t, title, sub, ic) -> tile(title, sub, ic, O.type == t) { O.type = t; render() } }, 14))
            }
            3 -> {
                addView(k.text("이 차를 누구와\n쓰나요?", Type.Large))
                addView(k.grid(2, listOf(
                    tile("혼자 써요", "내가 가는 길이 차곡이 되고, 다음의 나에게 길을 남겨요", "steering", O.solo == true) { O.solo = true; O.others.clear(); render() },
                    tile("함께 써요", "가족·친구가 길을 건네고 서로 응원해요", "people", O.solo == false) { O.solo = false; render() },
                ), 14))
                if (O.solo == false) {
                    addView(k.text("함께 쓰는 사람을 골라 주세요", Type.Sub, Ink.text2))
                    val presets = (listOf("아빠", "엄마", "형", "누나", "언니", "오빠", "동생", "친구") + O.others).distinct()
                    addView(k.hscroll(k.row(10).apply { presets.forEach { p -> addView(k.chip(p, p in O.others) { if (p in O.others) O.others.remove(p) else O.others.add(p); render() }) } }))
                    addView(k.button("직접 추가", Kit.Btn.Plain, "plus") { sheets.input("이름", "예: 민수") { O.others.add(it); render() } })
                }
                canNext = O.solo != null
            }
            4 -> {
                addView(k.text("이렇게 쓸게요", Type.Large))
                addView(k.text("모두 나중에 설정에서 바꿀 수 있어요.", Type.Sub, Ink.text2))
                addView(k.groupOf(listOf(
                    k.toggleItem("한마디를 그 자리에서 듣기", "차의 위치로 한마디를 남긴 자리에 가까워졌는지 봐요. 위치는 이 차 안에만 저장돼요.", store.locationOn, "pin") { store.locationOn = it; if (it && !places.permitted()) askLocation() },
                    k.toggleItem("소리로 알려 주기", "달리는 동안 화면 대신 말로 알려 줘요", store.voiceOn, "speaker") { store.voiceOn = it },
                    k.toggleItem("도착지 날씨", "목적지 좌표만 공개 예보(Open-Meteo)로 보내요", store.weatherOn, "cloud") { store.weatherOn = it },
                )))
                addView(k.row(10).apply { addView(k.icon("lock", Ink.text2, 18)); addView(k.text("일기와 지나온 길은 이 차 안에만 저장돼요. 누구에게 보여 줄지는 일기마다 직접 골라요.", Type.Caption, Ink.text2), k.lp(0, k.WRAP, 1f)) })
                onNext = { if (store.locationOn && !places.permitted()) askLocation(); next() }
            }
            else -> {
                addView(k.text("준비됐어요,\n${O.name} 님", Type.Large))
                addView(k.groupOf(listOf(
                    k.item("가고 싶은 곳을 내비에 넣어요", "목적지가 없어도 괜찮아요", "pin"),
                    k.item("기어를 D에 놓으면 기록이 시작돼요", "운전 중에는 말로만 알려 줘요", "play"),
                    k.item("P에 놓으면 일기가 생겨요", "해낸 일은 오른쪽 탑에 쌓여요", "book"),
                )))
                addView(k.text(if (O.type in listOf("초보", "새 차")) "처음이라면 출발 준비부터 해 보세요. 깜빡이, 와이퍼, 전조등, 기어를 한 번씩 해 보면 차가 확인해 줘요." else "바로 오늘 화면으로 가도 좋아요.", Type.Sub, Ink.text2))
                nextLabel = "오늘로"
                onNext = { finishOnboarding(false) }
            }
        }
    }

    // 아래 단추 줄
    val bar = k.row(12).apply {
        if (O.step > 0) addView(k.button("뒤로", Kit.Btn.Plain, "back") { prev() })
        addView(k.fill())
        if (O.step == Onboard.LAST && O.type in listOf("초보", "새 차")) addView(k.button("출발 준비부터", Kit.Btn.Secondary, "steering") { finishOnboarding(true) })
        addView(k.button(nextLabel, Kit.Btn.Primary, enabled = canNext) { onNext() })
    }
    val progress = k.row(8).apply {
        addView(k.text("${O.step + 1} / ${Onboard.LAST + 1}", Type.Caption, Ink.text2))
        repeat(Onboard.LAST + 1) { i -> addView(View(context).apply { background = k.rounded(if (i <= O.step) Ink.accent else Ink.line, 4) }, k.lp(ui.dp(if (i == O.step) 26 else 10), ui.dp(6))) }
    }
    val right = k.col().apply {
        val pad = ui.dp(if (ui.wide) 56 else 24)
        setPadding(pad, ui.dp(if (ui.wide) 48 else 24), pad, ui.dp(24))
        addView(progress)
        addView(k.gap(26))
        addView(k.scroll(content), k.lp(k.MATCH, 0, 1f))
        addView(k.gap(16))
        addView(bar, k.lp(k.MATCH, k.WRAP))
    }
    if (!ui.wide) return FrameLayout(this).apply { setBackgroundColor(Ink.bg); addView(right, FrameLayout.LayoutParams(-1, -1)) }

    // 왼쪽: 단계마다 블록이 쌓이는 차곡 탑
    val total = 12
    val doneNow = ((O.step + 1) * 2).coerceAtMost(total)
    val fresh = (doneNow - Onboard.shownBlocks).coerceAtLeast(0); Onboard.shownBlocks = doneNow
    val demo = (0 until total).map { i -> Block("o$i", i * 3 / total, "", "", i < doneNow) }
    val left = k.col(14).apply {
        setBackgroundColor(Ink.surface)
        setPadding(ui.dp(48), ui.dp(48), ui.dp(48), ui.dp(40))
        addView(k.text("차곡車곡", Type.Title))
        addView(k.text("한 칸씩, 차곡차곡", Type.Sub, Ink.text2))
        addView(FrameLayout(context).apply {
            addView(StackView(context, ui, demo, fresh), FrameLayout.LayoutParams(ui.dp(120), -1, Gravity.CENTER_HORIZONTAL))
        }, k.lp(k.MATCH, 0, 1f))
        addView(k.row(14).apply { gravity = Gravity.CENTER; STAGES.forEachIndexed { s, n -> addView(k.row(6).apply { addView(View(context).apply { background = k.rounded(stageColor(s), 3) }, k.lp(ui.dp(12), ui.dp(12))); addView(k.text(n, Type.Caption, Ink.text2)) }) } })
    }
    return k.row().apply {
        setBackgroundColor(Ink.bg)
        addView(left, k.lp(0, k.MATCH, 0.8f))
        addView(right, k.lp(0, k.MATCH, 1.2f))
    }
}

private fun MainActivity.finishOnboarding(prepFirst: Boolean) {
    val O = Onboard
    store.setDrivers(listOf(O.name) + O.others.filter { it != O.name })
    store.driver = O.name; store.setType(O.name, O.type); store.setRelation(O.name, "가족")
    O.others.forEach { store.setRelation(it, if (it == "친구") "친구" else "가족") }
    store.onboarded = true
    if (store.locationOn) places.start()
    val type = O.type
    O.step = 0; O.shownBlocks = 0
    speak(if (type == "초보") "안녕하세요. 면허 따고 처음은 낯설 수 있어요. 함께 확인해 봐요." else "안녕하세요. 오늘도 같이 가요.")
    if (prepFirst) { prepDone.clear(); prepSeen.clear(); go(MainActivity.TODAY); go(MainActivity.PREP) } else go(MainActivity.TODAY)
}
