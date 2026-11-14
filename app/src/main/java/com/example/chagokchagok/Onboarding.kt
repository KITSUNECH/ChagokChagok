// 차곡車곡 – 처음 시작
// 한 화면에 질문 하나. 넓은 화면은 왼쪽에 13칸 성장 게이지를 두고, 고른 운전 경험만큼 채운다.
// 여기서 고른 두 가지(운전 경험, 혼자/함께)가 이후 시나리오를 가른다. 고르면 무엇이 달라지는지 바로 비교해 보여 준다.
// 홈에 가면 게이지가 단계마다 한 칸으로 압축된다 (Stack.kt).
package com.example.chagokchagok

import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout

internal object Onboard {
    var step = 0; var name = "나"; var type = "초보"; var solo: Boolean? = null
    /** 면허 딴 해. null = 기억 안 남 */
    var license: Int? = null; var licenseTouched = false
    val others = mutableListOf<String>()
    /** 게이지가 마지막으로 보여 준 칸 수 (다음 화면에서 거기서부터 이어 채운다) */
    var gaugeShown = 0
    /** 홈에서 압축 모션을 보여 줄 칸 수 (한 번 쓰면 0) */
    var squeeze = 0
    const val LAST = 6
}

/** 운전 경험별로 달라지는 것. 처음 설정의 비교표와 설계 자료에 같은 값을 쓴다. */
internal data class Branch(val type: String, val title: String, val sub: String, val icon: String, val voice: String, val first: String, val stage: String)

internal val BRANCHES = listOf(
    Branch("초보", "면허 딴 지 얼마 안 됐어요", "처음부터 같이 가요", "steering", "자주 · 회전 전 깜빡이까지", "출발 준비 · 처음이라면 3가지", "익히기 1칸"),
    Branch("오랜만", "오랜만에 다시 운전해요", "장롱면허도 여기", "clock", "보통 · 한마디와 경고", "면허 딴 뒤 바뀐 법규", "익히기 2칸"),
    Branch("새 차", "이 차가 처음이에요", "운전은 하는데 차가 낯설어요", "car", "보통 · 한마디와 경고", "출발 준비 · 이 차의 기어", "익히기 2칸"),
    Branch("익숙", "운전이 익숙해요", "기록하고 길을 건네요", "road", "가끔 · 경고만", "내 길 건네주기", "익히기 3칸"),
)

/** 한 줄 선택지: 아이콘 · 제목/설명 · 체크. 세로 공간이 좁은 차 화면에 맞춘 높이 */
private fun MainActivity.option(title: String, sub: String, icon: String, selected: Boolean, onClick: () -> Unit): View {
    val k = kit
    return k.row(16).apply {
        setPadding(ui.dp(16), ui.dp(11), ui.dp(20), ui.dp(11))
        addView(FrameLayout(context).apply {
            background = k.rounded(if (selected) Ink.accent else Ink.accentSoft, 12)
            addView(IconView(context, icon, if (selected) Ink.onAccent else Ink.accent), FrameLayout.LayoutParams(ui.dp(24), ui.dp(24), Gravity.CENTER))
        }, k.lp(ui.dp(42), ui.dp(42)))
        addView(k.col(2).apply { addView(k.text(title, Type.BodyStrong)); addView(k.text(sub, Type.Caption, Ink.text2)) }, k.lp(0, k.WRAP, 1f))
        addView(k.icon(if (selected) "checkCircle" else "circle", if (selected) Ink.accent else Ink.line, 26))
        k.pressable(this, k.rounded(Ink.surface, 18, if (selected) 2 else 1, if (selected) Ink.accent else Ink.line), onClick)
    }
}

/** 표 한 줄: 왼쪽 이름, 오른쪽 값 */
private fun MainActivity.specRow(label: String, value: String, icon: String, color: Int = Ink.text): View {
    val k = kit
    return k.row(14).apply {
        setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(14))
        addView(k.icon(icon, Ink.text2, 20))
        addView(k.text(label, Type.Sub, Ink.text2), k.lp(ui.dp(110), k.WRAP))
        addView(k.text(value, Type.BodyStrong, color), k.lp(0, k.WRAP, 1f))
    }
}

private fun MainActivity.specTable(rows: List<View>): View = kit.col().apply {
    background = kit.rounded(Ink.surface, 18, 1, Ink.line)
    rows.forEachIndexed { i, r ->
        if (i > 0) addView(View(context).apply { setBackgroundColor(Ink.line) }, kit.lp(kit.MATCH, ui.dp(1)).apply { marginStart = ui.dp(18) })
        addView(r)
    }
}

/** 법규 한 줄 (시행일 · 제목 · 출처) */
internal fun MainActivity.lawRow(l: Law, compact: Boolean = false): View {
    val k = kit
    return k.row(16).apply {
        gravity = Gravity.TOP
        setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(14))
        addView(k.col(0).apply {
            if (l.dated) {
                addView(k.text(l.date.take(4), Type.BodyStrong, Ink.accent))
                addView(k.text(l.date.substring(5, 7).trimStart('0') + "월", Type.Caption, Ink.text3))
            } else addView(k.icon("scale", Ink.accent, 22))
        }, k.lp(ui.dp(52), k.WRAP))
        addView(k.col(2).apply {
            addView(k.text(l.title, Type.BodyStrong))
            if (!compact) addView(k.text(l.line, Type.Sub, Ink.text2))
            addView(k.text(l.source, Type.Caption, Ink.text3))
        }, k.lp(0, k.WRAP, 1f))
    }
}

internal fun MainActivity.onboardScreen(): View {
    val k = kit; val O = Onboard
    fun next() { O.step = (O.step + 1).coerceAtMost(Onboard.LAST); render() }
    fun prev() { O.step = (O.step - 1).coerceAtLeast(0); render() }
    val branch = BRANCHES.first { it.type == O.type }
    val year = Laws.thisYear

    var canNext = true
    var nextLabel = "다음"
    var onNext: () -> Unit = { next() }
    var nameField: EditText? = null

    // 넓은 창: 왼쪽은 질문과 고르기, 오른쪽(aside)은 고른 결과·비교·미리보기. 좁은 창은 위아래로 잇는다.
    val aside = k.col(14)
    /** 오른쪽 비교 칸의 윗선을 맞출 왼쪽의 주된 고르기 칸 (질문 제목 아래) */
    var anchor: View? = null
    fun side(v: View) = aside.addView(v, k.lp(k.MATCH, k.WRAP))
    val content = k.col(18).apply {
        when (O.step) {
            0 -> {
                addView(k.text("차로 쓰는 성장일기", Type.Micro, Ink.accent))
                addView(k.text("차가 기억했다가,\n다시 갈 때 꺼내 줘요", Type.Display))
                addView(k.text("차가 내 운전을 기억했다가, 같은 길을 다시 달릴 때 내가 늘었다는 걸 보여 줘요.", Type.Body, Ink.text2))
                addView(k.gap(6))
                val pillars = LOOP_STEPS
                side(k.col(10).apply { pillars.forEach { (ic, t, s) ->
                    addView(k.row(16).apply {
                        setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16)); background = k.rounded(Ink.surface, 18, 1, Ink.line)
                        addView(FrameLayout(context).apply { background = k.rounded(Ink.accentSoft, 12); addView(IconView(context, ic, Ink.accent), FrameLayout.LayoutParams(ui.dp(22), ui.dp(22), Gravity.CENTER)) }, k.lp(ui.dp(44), ui.dp(44)))
                        addView(k.col(2).apply { addView(k.text(t, Type.BodyStrong)); addView(k.text(s, Type.Caption, Ink.text2)) }, k.lp(0, k.WRAP, 1f))
                    }, k.lp(k.MATCH, k.WRAP))
                } })
                nextLabel = "시작하기"
            }
            1 -> {
                addView(k.text("운전석에 앉는 분을\n뭐라고 부를까요?", Type.Large))
                val preview = k.avatar(O.name, 76)
                addView(k.row(18).apply {
                    addView(preview)
                    nameField = EditText(context).apply {
                        setText(O.name); setSelection(O.name.length); textSize = ui.sp(Type.Title.size); typeface = ui.bold; setTextColor(Ink.text)
                        background = k.rounded(Ink.surface, 18, 1, Ink.line); setPadding(ui.dp(22), ui.dp(18), ui.dp(22), ui.dp(18)); setSingleLine()
                        addTextChangedListener(object : android.text.TextWatcher {
                            override fun afterTextChanged(s: android.text.Editable?) { val t = s.toString().trim().ifEmpty { "나" }; preview.text = t.take(1); (preview.background as GradientDrawable).setColor(Ink.person(t)) }
                            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
                        })
                    }
                    addView(nameField, k.lp(0, k.WRAP, 1f))
                })
                addView(k.row(8).apply { addView(k.icon("lock", Ink.text3, 16)); addView(k.text("앱 안에서만 쓰는 이름이에요. ‘나’로 두어도 돼요.", Type.Sub, Ink.text2)) })
                onNext = { O.name = nameField?.text?.toString()?.trim()?.ifEmpty { "나" } ?: "나"; next() }
            }
            2 -> {
                addView(k.row(10).apply { addView(k.tag("여기서 길이 나뉘어요", Ink.accent)) })
                addView(k.text("${O.name} 님은 운전을\n얼마나 해 봤나요?", Type.Large))
                addView(k.col(8).apply { anchor = this; BRANCHES.forEach { b -> addView(option(b.title, b.sub, b.icon, O.type == b.type) { O.type = b.type; if (!O.licenseTouched) O.license = null; render() }, k.lp(k.MATCH, k.WRAP)) } })
                side(k.text("고르면 이렇게 달라져요", Type.Headline))
                side(k.text("${branch.title} · 나중에 설정에서 바꿀 수 있어요", Type.Sub, Ink.text2))
                side(specTable(listOf(
                    specRow("말로 알려 주기", branch.voice, "speaker"),
                    specRow("홈에서 먼저", branch.first, "today"),
                    specRow("시작 칸", branch.stage, "layers", stageColor(0)),
                )))
            }
            3 -> {
                if (O.license == null && !O.licenseTouched) O.license = when (O.type) { "초보" -> year; "오랜만" -> year - 8; else -> year - 5 }
                val y = O.license
                addView(k.text("면허는 언제\n땄나요?", Type.Large))
                addView(k.text("그 뒤로 바뀐 법규만 골라 알려 드려요. 대략이어도 괜찮아요.", Type.Sub, Ink.text2))
                // 연도 고르기
                addView(k.row(16).apply {
                    anchor = this
                    setPadding(ui.dp(10), ui.dp(10), ui.dp(10), ui.dp(10)); background = k.rounded(Ink.surface, 22, 1, Ink.line)
                    addView(k.iconButton("minus", Ink.text, Ink.fill, 56) { O.licenseTouched = true; O.license = ((y ?: year) - 1).coerceAtLeast(1980); render() })
                    addView(k.col(0).apply {
                        gravity = Gravity.CENTER
                        addView(k.text(y?.let { "${it}년" } ?: "기억 안 나요", Type.Large).apply { gravity = Gravity.CENTER })
                        addView(k.text(y?.let { if (it >= year) "올해" else "${year - it}년 전" } ?: "전부 보여 드릴게요", Type.Caption, Ink.text2).apply { gravity = Gravity.CENTER })
                    }, k.lp(0, k.WRAP, 1f))
                    addView(k.iconButton("plus", Ink.text, Ink.fill, 56) { O.licenseTouched = true; O.license = ((y ?: year - 1) + 1).coerceAtMost(year); render() })
                })
                addView(k.flow(10).apply {
                    listOf("올해" to 0, "2년 전" to 2, "5년 전" to 5, "10년 전" to 10, "20년 전" to 20).forEach { (l, ago) ->
                        addView(k.chip(l, y == year - ago) { O.licenseTouched = true; O.license = year - ago; render() })
                    }
                    addView(k.chip("기억 안 나요", y == null) { O.licenseTouched = true; O.license = null; render() })
                })
                val since = Laws.since(y)
                side(k.text(if (since.isEmpty()) "바뀐 법규는 없어요" else "그 뒤로 바뀐 법규 ${since.size}가지", Type.Headline))
                side(k.text(if (since.isEmpty()) "대신 초보가 꼭 알아 둘 것을 보여 드릴게요." else "시행일 순이에요. 법제처 생활법령정보와 정책브리핑으로 확인했어요.", Type.Sub, Ink.text2))
                val all = if (since.isEmpty()) Laws.core else since
                val list = all.takeLast(if (ui.wide) 4 else 3)
                side(specTable(list.map { lawRow(it, compact = true) }))
                if (all.size > list.size) side(k.text("나머지 ${all.size - list.size}가지는 홈의 ‘바뀐 법규’ 카드에서 볼 수 있어요.", Type.Caption, Ink.text3))
            }
            4 -> {
                addView(k.row(10).apply { addView(k.tag("여기서 한 번 더 나뉘어요", Ink.accent)) })
                addView(k.text("이 차를 누구와\n쓰나요?", Type.Large))
                addView(k.col(10).apply {
                    anchor = this
                    addView(option("혼자 써요", "내가 가는 길이 차곡이 되고, 다음의 나에게 한마디를 남겨요", "steering", O.solo == true) { O.solo = true; O.others.clear(); render() }, k.lp(k.MATCH, k.WRAP))
                    addView(option("함께 써요", "이 차를 같이 타는 가족·친구가 길을 건네고 서로 응원해요", "people", O.solo == false) { O.solo = false; render() }, k.lp(k.MATCH, k.WRAP))
                })
                if (O.solo == false) {
                    addView(k.text("이 차를 함께 쓰는 사람", Type.Sub, Ink.text2))
                    val presets = (listOf("아빠", "엄마", "형", "누나", "언니", "오빠", "동생", "친구") + O.others).distinct()
                    addView(k.flow(10).apply { presets.forEach { p -> addView(k.chip(p, p in O.others) { if (p in O.others) O.others.remove(p) else O.others.add(p); render() }) } })
                    addView(k.button("직접 추가", Kit.Btn.Plain, "plus") { sheets.input("이름", "예: 민수") { O.others.add(it); render() } })
                    side(k.row(12).apply {
                        gravity = Gravity.TOP
                        setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(14)); background = k.rounded(Ink.accentSoft, 16)
                        addView(k.icon("info", Ink.accent, 20))
                        addView(k.text("함께 운전하는 사람이 이 차 자동차보험의 운전자 범위에 드는지 먼저 확인해 주세요. 가족 한정이라도 형제자매는 빠지는 경우가 많아요.", Type.Sub, Ink.text), k.lp(0, k.WRAP, 1f))
                    })
                }
                // 다른 차의 가족·친구: 지금 Pleos에는 계정을 잇는 기능이 없다
                side(k.row(14).apply {
                    gravity = Gravity.TOP
                    setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(18)); background = k.rounded(Ink.fill, 20)
                    addView(k.icon("link", Ink.text2, 24))
                    addView(k.col(4).apply {
                        addView(k.text("다른 차의 가족·친구와 잇기", Type.BodyStrong, Ink.text2))
                        addView(k.row().apply { addView(k.tag("Pleos 계정 연동 필요 · 추후", Ink.warn)) })
                        addView(k.text("지금 Pleos는 차 한 대 안의 계정이라, 이 차를 함께 쓰는 사람끼리만 이어져요." + if (BuildConfig.DEMOS) " 다른 차와 잇는 모습은 설정에서 시연용으로 미리 볼 수 있어요." else " Pleos가 계정 연동을 지원하면 다른 차와도 이어질 수 있어요.", Type.Caption, Ink.text2))
                    }, k.lp(0, k.WRAP, 1f))
                })
                canNext = O.solo != null
            }
            5 -> {
                addView(k.text("이렇게 쓸게요", Type.Large))
                addView(k.text("모두 나중에 설정에서 바꿀 수 있어요.", Type.Sub, Ink.text2))
                addView(k.groupOf(listOf(
                    k.toggleItem("한마디를 남긴 곳 근처에서 듣기", "${O.name} 님의 위치로 한마디를 남긴 곳 근처인지 봐요. 위치는 이 차 안에만 저장되고, 함께 쓰는 사람은 각자 따로 동의해요.", store.locationOn, "pin") { store.locationOn = it; if (it && !places.permitted()) askLocation() },
                    k.toggleItem("소리로 알려 주기", "달리는 동안 화면 대신 말로 알려 줘요", store.voiceOn, "speaker") { store.voiceOn = it },
                    k.toggleItem("도착지 날씨", "목적지 좌표만 공개 예보(Open-Meteo)로 보내요", store.weatherOn, "cloud") { store.weatherOn = it },
                )))
                side(k.row(10).apply { gravity = Gravity.TOP; addView(k.icon("lock", Ink.text2, 18)); addView(k.text("일기와 지나온 길은 이 차 안에만 저장돼요. 누구에게 보여 줄지는 일기마다 직접 골라요.", Type.Caption, Ink.text2), k.lp(0, k.WRAP, 1f)) })
                onNext = { if (store.locationOn && !places.permitted()) askLocation(); next() }
            }
            else -> {
                addView(k.text("준비됐어요,\n${O.name} 님", Type.Large))
                val lic = O.license
                addView(specTable(listOf(
                    specRow("운전 경험", branch.title, branch.icon),
                    specRow("면허", lic?.let { "${it}년 · 그 뒤 바뀐 법규 ${Laws.since(it).size}가지" } ?: "기억 안 남 · 법규 전부", "info"),
                    specRow("함께", if (O.solo == false) (listOf(O.name) + O.others).joinToString(" · ") else "혼자 · 다음의 나에게", "people"),
                    specRow("시작 칸", branch.stage, "layers", stageColor(0)),
                )))
                side(k.text("이렇게 시작해요", Type.Headline))
                side(k.grid(1, listOf(
                    Triple("pin", "1  목적지", "가고 싶은 곳을 내비에 넣어요. 없어도 괜찮아요."),
                    Triple("play", "2  기어 D", "기록이 시작돼요. 달리는 동안은 말로만."),
                    Triple("book", "3  기어 P", "일기가 생기고 해낸 일이 한 칸 쌓여요."),
                ).map { (ic, t, s) -> k.row(14).apply {
                    setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16)); background = k.rounded(Ink.surface, 18, 1, Ink.line)
                    addView(k.icon(ic, Ink.accent, 24))
                    addView(k.col(2).apply { addView(k.text(t, Type.BodyStrong)); addView(k.text(s, Type.Caption, Ink.text2)) }, k.lp(0, k.WRAP, 1f))
                } }, 10))
                nextLabel = "오늘로"
                onNext = { finishOnboarding(false) }
            }
        }
    }

    // 아래 단추 줄
    val prepFirst = if (O.step == Onboard.LAST && O.type in listOf("초보", "새 차")) k.button("출발 준비부터", Kit.Btn.Secondary, "steering") { finishOnboarding(true) } else null
    val barRow = k.row(12).apply {
        if (O.step > 0) addView(k.button("뒤로", Kit.Btn.Plain, "back") { prev() })
        addView(k.fill())
        if (prepFirst != null && ui.wide) addView(prepFirst)
        addView(k.button(nextLabel, Kit.Btn.Primary, enabled = canNext) { onNext() })
    }
    // 좁은 창(내비 옆)에서는 단추 셋이 한 줄에 들어가지 않는다: '출발 준비부터'를 윗줄로 올린다
    val bar = if (prepFirst != null && ui.narrow) k.col(10).apply { addView(prepFirst, k.lp(k.MATCH, k.WRAP)); addView(barRow, k.lp(k.MATCH, k.WRAP)) } else barRow
    val progress = k.row(6).apply {
        repeat(Onboard.LAST + 1) { i -> addView(View(context).apply { background = k.rounded(if (i <= O.step) Ink.accent else Ink.line, 4) }, k.lp(ui.dp(if (i == O.step) 28 else 10), ui.dp(6))) }
        addView(k.gap(6))
        addView(k.text("${O.step + 1} / ${Onboard.LAST + 1}", Type.Caption, Ink.text2))
    }
    val right = k.col().apply {
        val pad = ui.dp(if (ui.wide) 64 else 24)
        setPadding(pad, ui.dp(if (ui.wide) 52 else 24), pad, ui.dp(28))
        addView(progress)
        addView(k.gap(28))
        val body = if (ui.wide && aside.childCount > 0) k.row(40).apply {
            gravity = Gravity.TOP
            addView(content, k.lp(0, k.WRAP, 1.05f)); addView(aside, k.lp(0, k.WRAP, 1f))
            // 오른쪽 칸은 왼쪽 고르기 칸과 같은 높이에서 시작한다 (제목 높이만큼 내려 맞춤)
            anchor?.let { a -> content.viewTreeObserver.addOnGlobalLayoutListener(object : android.view.ViewTreeObserver.OnGlobalLayoutListener {
                override fun onGlobalLayout() {
                    content.viewTreeObserver.removeOnGlobalLayoutListener(this)
                    var top = 0; var v: View? = a
                    while (v != null && v !== content) { top += v.top; v = v.parent as? View }
                    if (aside.paddingTop != top) aside.setPadding(0, top, 0, 0)
                }
            }) }
        } else k.col(18).apply { addView(content); if (aside.childCount > 0) addView(aside) }
        addView(k.scroll(body), k.lp(k.MATCH, 0, 1f))
        addView(k.gap(16))
        addView(bar, k.lp(k.MATCH, k.WRAP))
    }
    // 오른쪽: 13칸 성장 게이지. 홈의 성장 바와 같은 자리, 같은 폭에 둬서
    // 처음 설정을 마치면 같은 자리에서 그대로 압축되며 홈의 바로 이어진다.
    val cap = Gauge.cap(levelOf(O.type))
    val target = if (O.step == Onboard.LAST) cap else Math.round(cap * (O.step + 1).toFloat() / (Onboard.LAST + 1)).coerceIn(1, cap)
    val from = O.gaugeShown; O.gaugeShown = target
    val labelW = ui.dp(if (ui.wide) 70 else 64)
    val rail = k.col(10).apply {
        // 홈 바(stackRail)와 같은 안쪽 여백: 위 24 · 옆 14 · 아래 20
        setPadding(ui.dp(14), ui.dp(24), ui.dp(14), ui.dp(20))
        setBackgroundColor(Ink.surface)
        addView(k.text("$target", Type.Title).apply { gravity = Gravity.END; setPadding(0, 0, ui.dp(if (ui.wide) 22 else 8), 0) })
        addView(k.text("칸", Type.Micro, Ink.text2).apply { gravity = Gravity.END; setPadding(0, 0, ui.dp(if (ui.wide) 34 else 14), 0) })
        addView(GaugeView(context, ui, from, target, labelW), k.lp(k.MATCH, 0, 1f))
        addView(k.text(STAGES[levelOf(O.type) - 1], Type.Micro, stageColor(levelOf(O.type) - 1)).apply { gravity = Gravity.END; setPadding(0, 0, ui.dp(if (ui.wide) 16 else 2), 0) })
    }
    val railW = labelW + ui.dp(if (ui.wide) 84 else 56)
    if (ui.wide) right.addView(k.row(12).apply { addView(brand(30)); addView(k.fill()) }, 0)
    if (ui.wide) right.addView(k.gap(26), 1)
    return k.row().apply {
        setBackgroundColor(Ink.bg)
        addView(right, k.lp(0, k.MATCH, 1f))
        addView(View(context).apply { setBackgroundColor(Ink.line) }, k.lp(ui.dp(1), k.MATCH))
        addView(rail, k.lp(railW, k.MATCH))
    }
}

private fun MainActivity.finishOnboarding(prepFirst: Boolean) {
    val O = Onboard
    store.setDrivers(listOf(O.name) + O.others.filter { it != O.name })
    store.driver = O.name; store.setType(O.name, O.type); store.setRelation(O.name, "가족")
    store.setLicenseYear(O.name, O.license)
    store.setLocationConsent(O.name, store.locationOn)   // 첫 설정의 위치 켜기 = 이 운전자의 동의
    O.others.forEach { store.setRelation(it, if (it == "친구") "친구" else "가족") }
    store.onboarded = true
    if (store.locationOn) places.start()
    val type = O.type
    // 홈 오른쪽 바에서 게이지를 압축해 보여 준다 (좁은 창도 같은 모션)
    O.step = 0; O.gaugeShown = 0; O.licenseTouched = false
    speak(if (type == "초보") "안녕하세요. 면허 따고 처음은 낯설 수 있어요. 함께 확인해 봐요." else "안녕하세요. 오늘도 같이 가요.")
    if (prepFirst) { prepDone.clear(); prepSeen.clear(); go(MainActivity.TODAY) }
    O.squeeze = Gauge.cap(levelOf(type))
    go(if (prepFirst) MainActivity.PREP else MainActivity.TODAY)
}
