// 차곡車곡 – 설정 · 함께 쓰는 사람 · 알아 두기 · 연결 상태 · 처음 시작
package com.example.chagokchagok

import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout

internal fun MainActivity.settingsScreen(): View {
    val k = kit; val me = store.driver
    return k.col().apply {
        addView(k.header("설정"))
        addView(k.section("운전석"))
        addView(k.groupOf(listOf(
            k.item(me, "${store.relation(me)} · ${store.typeOf(me)} · 지금 운전석", trailing = k.avatar(me, 36)) { switchDriver() },
            k.item("함께 쓰는 사람", store.drivers.joinToString(", "), "people", chevron = true) { go(MainActivity.PEOPLE) },
        )))
        addView(k.section("안내"))
        addView(k.groupOf(listOf(
            k.toggleItem("소리로 알려 주기", "한마디, 회전 전 깜빡이, 도착을 말로 알려 줘요", store.voiceOn, "speaker") { store.voiceOn = it },
            k.item("목소리 들어 보기", voice.status, "play", chevron = true) { speak("안녕하세요. 차곡차곡이에요. 오늘도 같이 가요.") },
            k.item("일기 말투", "다음 일기부터 바뀌어요", "pencil", value = store.tone(me)) {
                sheets.choose("일기 말투", null, listOf("담백하게", "다정하게"), selected = if (store.tone(me) == "다정하게") 1 else 0) { i -> store.setTone(me, listOf("담백하게", "다정하게")[i]); render() }
            },
        )))
        addView(k.section("위치와 개인정보"))
        addView(k.groupOf(listOf(
            k.toggleItem("한마디를 그 자리에서 듣기", "차의 위치로 한마디를 남긴 자리에 가까워졌는지 봐요. 위치는 이 차 안에만 저장돼요.", store.locationOn, "pin") {
                store.locationOn = it
                if (it) { places.start(); if (!places.permitted()) askLocation() }
                render()
            },
            k.toggleItem("도착지 날씨", "목적지 좌표를 공개 예보(Open-Meteo)에 보내 날씨를 읽어 와요", store.weatherOn, "cloud") { store.weatherOn = it },
            k.toggleItem("내 기록을 가족에게 보여 주기", "켜면 가족 프로필 일기장에 내 요즘 운전이 보여요", store.shares(me), "eye") { store.setShares(me, it) },
            k.item("일기 문장 다듬기 (생성형 AI)", if (AiDiary.enabled) "켜짐 · 일기 초안과 장소 이름이 Anthropic API로 가요" else "꺼짐 · 차량 기록으로 규칙에 따라 써요", "sparkle", value = trip.aiStatus),
        )))
        addView(k.section("알아 두기"))
        addView(k.groupOf(listOf(
            k.item("그동안 바뀐 법규", null, "info", chevron = true) { go(MainActivity.LEARN, "law") },
            k.item("면허시험 차와 다른 곳", null, "steering", chevron = true) { go(MainActivity.LEARN, "newcar") },
            k.item("경고등 색 읽는 법", null, "warning", chevron = true) { go(MainActivity.LEARN, "lamp") },
        )))
        addView(k.section("연결과 시연"))
        addView(k.groupOf(listOf(
            k.item("연결 상태", "차량 신호, 내비, 위치, 음성, 주행 제한", "car", chevron = true) { go(MainActivity.CHECK) },
            k.item("시험용 신호 보내기", "가상 콘솔에 없는 신호를 시험해요 (실제 차량 신호 아님)", "sparkle", chevron = true) { testSignals() },
            k.item("예시 길과 친구 넣기", "아빠가 건넨 길, 친구 민수의 연습과 추천 장소", "gift", chevron = true) { store.addSampleGuide(); store.addSampleFriend(); toast("예시를 넣었어요. 오늘과 함께 차곡에서 볼 수 있어요.") },
        )))
        addView(k.section("데이터"))
        addView(k.groupOf(listOf(
            k.item("기록 모두 지우기", "일기, 건넨 길, 사람, 설정이 모두 사라져요", "trash", Ink.danger) {
                sheets.confirm("모두 지울까요?", "되돌릴 수 없어요.", "모두 지우기", true) { store.clearAll(); store.onboarded = false; go(MainActivity.ONBOARD) }
            },
        )))
        addView(k.gap(24))
        addView(k.text("차곡車곡 알파 0.1 · 기록은 이 차 안에만 저장돼요.\n글꼴: Pretendard, 고운바탕 (SIL Open Font License)", Type.Caption, Ink.text3).apply { setPadding(ui.dp(4), 0, 0, 0) })
    }
}

/** 시험용 신호: 가상 콘솔에 없는 조작. 실제 차량 신호가 아니다. */
internal fun MainActivity.testSignals() {
    val items = listOf(
        "깜빡이 왼쪽" to { onCarEvent(Evt.SIGNAL, "LEFT") }, "깜빡이 오른쪽" to { onCarEvent(Evt.SIGNAL, "RIGHT") }, "깜빡이 끄기" to { onCarEvent(Evt.SIGNAL, "OFF") },
        "브레이크" to { onCarEvent(Evt.BRAKE, "on") }, "와이퍼 켜기" to { onCarEvent(Evt.WIPER, "on") }, "와이퍼 끄기" to { onCarEvent(Evt.WIPER, "off") },
        "전조등 켜기" to { onCarEvent(Evt.LIGHT, "on") }, "안전벨트 매기" to { onCarEvent("belt", "on") }, "동승자 탑승" to { onCarEvent(Evt.SEAT, "on") },
        "내비: 150m 앞 우회전" to { car.setTurn("TURN_RIGHT", 150, "") }, "내비: 80m 앞 우회전" to { car.setTurn("TURN_RIGHT", 80, "") }, "내비: 회전 지남" to { car.setTurn(null, 0, "") },
        "표시등: 오토 홀드" to { car.setLamps(car.lampsOn + "AUTO_HOLD") }, "표시등: 타이어 공기압" to { car.setLamps(car.lampsOn + "TPMS") }, "표시등 모두 끄기" to { car.setLamps(emptySet()) },
        "기어 D" to { onCarEvent(Evt.GEAR, "D") }, "기어 R" to { onCarEvent(Evt.GEAR, "R") }, "기어 P" to { onCarEvent(Evt.GEAR, "P") },
    )
    sheets.choose("시험용 신호", "실제 차량 신호가 아니에요. 시연 영상에는 가상 콘솔의 실제 신호를 쓰세요.", items.map { it.first }) { i -> items[i].second() }
}

// ═══════════════ 함께 쓰는 사람 ═══════════════

internal val DRIVER_TYPES = listOf("초보" to "면허를 딴 지 얼마 안 됐어요", "오랜만" to "오랜만에 다시 운전해요", "새 차" to "운전은 하는데 이 차가 처음이에요", "익숙" to "운전이 익숙해요")

internal fun MainActivity.peopleScreen(): View {
    val k = kit
    return k.col().apply {
        addView(k.header("함께 쓰는 사람", "이 차를 함께 쓰는 가족과 친구예요. 운전석에 앉은 사람마다 받은 길과 일기가 따로 쌓여요.", onBack = { back() }))
        addView(k.gap(18))
        addView(k.groupOf(store.drivers.map { n ->
            k.item(n, "${store.relation(n)} · ${store.typeOf(n)}" + if (n == store.driver) " · 지금 운전석" else "", trailing = k.avatar(n, 36)) { editPerson(n) }
        } + k.item("사람 추가", icon = "plus") { addPerson() }))
        addView(k.gap(14))
        addView(k.text("다른 차를 쓰는 가족·친구와 잇는 기능은 계정 연동이 필요해 아직 없어요. 지금은 이 차 안에서 운전석을 바꿔 가며 써요.", Type.Caption, Ink.text3).apply { setPadding(ui.dp(4), 0, 0, 0) })
    }
}

internal fun MainActivity.addPerson() {
    sheets.input("이름", "예: 엄마, 민수") { name ->
        store.addDriver(name)
        sheets.choose("$name 님은", null, listOf("가족", "친구")) { r ->
            store.setRelation(name, if (r == 0) "가족" else "친구")
            sheets.choose("$name 님은 어떤 운전자인가요?", null, DRIVER_TYPES.map { it.second }) { t -> store.setType(name, DRIVER_TYPES[t].first); render() }
        }
    }
}

private fun MainActivity.editPerson(n: String) {
    sheets.choose(n, "${store.relation(n)} · ${store.typeOf(n)}", listOf("운전석에 앉기", "운전 경험 바꾸기", "가족/친구 바꾸기", "목록에서 빼기"), listOf("steering", "pencil", "people", "trash")) { i ->
        when (i) {
            0 -> { store.driver = n; go(MainActivity.TODAY) }
            1 -> sheets.choose("$n 님은 어떤 운전자인가요?", null, DRIVER_TYPES.map { it.second }, selected = DRIVER_TYPES.indexOfFirst { it.first == store.typeOf(n) }) { t -> store.setType(n, DRIVER_TYPES[t].first); render() }
            2 -> sheets.choose("$n 님은", null, listOf("가족", "친구"), selected = if (store.relation(n) == "가족") 0 else 1) { r -> store.setRelation(n, if (r == 0) "가족" else "친구"); render() }
            else -> if (store.drivers.size <= 1) toast("한 사람은 남아 있어야 해요.") else sheets.confirm("$n 님을 목록에서 뺄까요?", "쓴 일기와 건넨 길은 남아요.", "빼기", true) { store.removeDriver(n); render() }
        }
    }
}

// ═══════════════ 알아 두기 ═══════════════

internal fun MainActivity.learnScreen(key: String?): View {
    val k = kit
    val (title, sub, notes) = when (key) {
        "lamp" -> Triple("경고등 색 읽는 법", "색으로 먼저 판단하고, 자세한 뜻은 차의 설명서를 따라요.", Notes.lamps + car.lampsOn.mapNotNull { Lamps.all[it] }.map { Note("지금 켜진 표시등 · ${it.name} (${it.level})", it.what) })
        "newcar" -> Triple("면허시험 차와 다른 곳", "차마다 다르니 출발 준비에서 직접 해 보세요.", Notes.newCar)
        else -> Triple("그동안 바뀐 법규", "면허를 딴 뒤나 오랜만에 운전할 때 놓치기 쉬운 것들이에요.", Notes.laws)
    }
    return k.col().apply {
        addView(k.header(title, sub, onBack = { back() }))
        addView(k.gap(18))
        addView(k.grid(if (ui.wide) 2 else 1, notes.map { n ->
            k.card(pad = 22) {
                addView(k.row().apply {
                    addView(k.text(n.title, Type.Headline), k.lp(0, k.WRAP, 1f))
                    addView(k.iconButton("speaker", Ink.accent, Ink.accentSoft, 44) { speak(n.title + ". " + n.body) })
                })
                addView(k.gap(8)); addView(k.text(n.body, Type.Body, Ink.text2))
                if (n.source.isNotEmpty()) { addView(k.gap(10)); addView(k.text("출처: ${n.source}", Type.Caption, Ink.text3)) }
            }
        }, 16))
        if (key == "newcar") { addView(k.gap(18)); addView(k.button("출발 준비에서 직접 해 보기", Kit.Btn.Primary, "steering") { prepDone.clear(); prepSeen.clear(); go(MainActivity.PREP) }) }
    }
}

// ═══════════════ 연결 상태 ═══════════════

internal fun MainActivity.checkScreen(): View {
    val k = kit
    fun ok(b: Boolean) = if (b) Ink.go else Ink.text3
    val vehicleOk = !car.failures.containsKey("vehicle") && !Car.offline
    return k.col().apply {
        addView(k.header("연결 상태", "앱이 지금 무엇을 받고 있는지 보여 줘요. 시연 전에 확인하세요.", onBack = { back() }, trailing = k.button("새로고침", Kit.Btn.Plain) { car.refreshNavi(); car.refreshCondition(); render() }))
        addView(k.section("차량 (Vehicle SDK)"))
        addView(k.groupOf(listOfNotNull(
            k.item("차량 신호", if (vehicleOk) "연결됨" else "연결 안 됨", "car", ok(vehicleOk)),
            k.item("기어", null, "steering", value = car.gear),
            k.item("주행거리계", null, "road", value = car.odometerKm?.let { String.format("%.1fkm", it) } ?: "읽지 못함"),
            k.item("연료/배터리", null, "fuel", value = car.energy?.let { "$it%" } ?: "읽지 못함"),
            k.item("표시등", null, "warning", value = car.lampsOn.joinToString().ifEmpty { "없음" }),
            k.item("타이어", null, "car", value = car.tires.entries.joinToString { "${it.key.take(2)} ${it.value.toInt()}" }.ifEmpty { "읽지 못함" }),
            k.item("운전석 위치", null, "seat", value = car.seatPos?.toString() ?: "읽지 못함"),
            if (car.failures.isNotEmpty()) k.item("실패한 신호", car.failures.entries.joinToString("\n") { "${it.key}: ${it.value}" }, "info", Ink.warn) else null,
        )))
        addView(k.section("내비 (NaviHelper SDK)"))
        addView(k.groupOf(listOf(
            k.item("내비", if (car.naviConnected) "연결됨" else "연결 안 됨", "pin", ok(car.naviConnected)),
            k.item("경로 안내", null, "road", value = if (car.routeStarted) "안내 중" else "없음"),
            k.item("다음 회전", null, "turnRight", value = car.turn?.let { "$it ${car.turnMeters}m" } ?: "없음"),
            k.item("제한속도", null, "speed", value = if (car.speedLimit > 0) "${car.speedLimit}" else "없음"),
        )))
        addView(k.section("위치 · 음성 · 주행 제한"))
        addView(k.groupOf(listOf(
            k.item("위치 (FusedLocation SDK)", places.status + if (places.lat != null) " · ${places.source}" else "", "pin", ok(places.lat != null),
                value = places.lat?.let { String.format("%.4f, %.4f", it, places.lng) }),
            k.item("위치 권한", if (places.permitted()) "허용됨" else "아직 허용 안 됨", "lock", ok(places.permitted())) { if (!places.permitted()) askLocation() },
            k.item("음성 (TextToSpeech SDK)", voice.status, "speaker", ok(voice.ready), chevron = true) { speak("목소리 시험이에요. 잘 들리나요?") },
            k.item("주행 제한 (CarUxRestrictions)", limits.status, "lock", ok(!limits.status.startsWith("연결 실패"))),
            k.item("화면", "${ui.w}×${ui.h}dp · ${if (ui.wide) "넓은 창" else "좁은 창"} · ${if (Ink.night) "밤" else "낮"}", "eye"),
        )))
    }
}

// ═══════════════ 처음 시작 ═══════════════

internal object Onboard { var step = 0; var name = "나"; var type = "초보"; val others = mutableListOf("아빠") }

internal fun MainActivity.onboardScreen(): View {
    val k = kit; val O = Onboard
    val total = 5
    fun next() { O.step++; render() }
    val content = k.col(18).apply {
        when (O.step) {
            0 -> {
                addView(k.text("차곡車곡", Type.Display))
                addView(k.text("차로 쓰는 성장일기", Type.Headline, Ink.text2))
                addView(k.gap(10))
                addView(k.groupOf(listOf(
                    k.item("가족이 다니던 길을, 이제 운전석에서", "아빠·엄마·친구가 건넨 길과 한마디를 따라 달려요", "gift"),
                    k.item("차가 오늘의 운전을 일기로", "깜빡이, 브레이크, 와이퍼… 차가 본 그대로 적어요", "book"),
                    k.item("내 길이 다음 사람의 길이 돼요", "익숙해진 길을 다음 사람에게 건네요", "people"),
                )))
                addView(k.button("시작하기", Kit.Btn.Primary) { next() })
            }
            1 -> {
                addView(k.text("운전석에 앉는 분은\n누구인가요?", Type.Large))
                val field = EditText(context).apply {
                    setText(O.name); textSize = ui.sp(Type.Headline.size); typeface = ui.semibold; setTextColor(Ink.text)
                    background = k.rounded(Ink.fill, 16); setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(18)); setSingleLine()
                }
                addView(field, k.lp(k.MATCH, k.WRAP))
                addView(k.text("앱 안에서 부를 이름이에요. ‘나’로 두어도 돼요.", Type.Caption, Ink.text2))
                addView(k.button("다음", Kit.Btn.Primary) { O.name = field.text.toString().trim().ifEmpty { "나" }; next() })
            }
            2 -> {
                addView(k.text("운전은 얼마나\n해 봤나요?", Type.Large))
                addView(k.groupOf(DRIVER_TYPES.map { (t, desc) ->
                    k.item(desc, when (t) { "초보" -> "차가 자주, 구체적으로 알려 줘요"; "오랜만" -> "바뀐 법규부터 알려 줘요"; "새 차" -> "이 차의 조작부터 익혀요"; else -> "길을 건네고 오늘을 남겨요" },
                        trailing = if (O.type == t) k.icon("checkCircle", Ink.accent, 26) else k.icon("circle", Ink.text3, 26)) { O.type = t; render() }
                }))
                addView(k.button("다음", Kit.Btn.Primary) { next() })
            }
            3 -> {
                addView(k.text("이 차를 함께\n쓰는 사람이 있나요?", Type.Large))
                addView(k.text("길을 건네주거나 응원을 보낼 사람이에요. 나중에 바꿀 수 있어요.", Type.Sub, Ink.text2))
                val presets = (listOf("아빠", "엄마", "형", "누나", "언니", "오빠", "동생") + O.others).distinct()
                addView(k.hscroll(k.row(10).apply { presets.forEach { p -> addView(k.chip(p, p in O.others) { if (p in O.others) O.others.remove(p) else O.others.add(p); render() }) } }))
                addView(k.button("다른 사람 추가", Kit.Btn.Tinted, "plus") { sheets.input("이름", "예: 민수 (친구)") { O.others.add(it); render() } })
                addView(k.button("다음", Kit.Btn.Primary) { next() })
            }
            else -> {
                addView(k.text("마지막으로\n이렇게 쓸게요", Type.Large))
                addView(k.groupOf(listOf(
                    k.toggleItem("한마디를 그 자리에서 듣기", "차의 위치를 써요. 위치는 이 차 안에만 저장돼요.", store.locationOn, "pin") { store.locationOn = it },
                    k.toggleItem("도착지 날씨", "목적지 좌표를 공개 예보에 보내요", store.weatherOn, "cloud") { store.weatherOn = it },
                    k.toggleItem("소리로 알려 주기", "달리는 동안 화면 대신 말로", store.voiceOn, "speaker") { store.voiceOn = it },
                )))
                addView(k.text("기록은 이 차 안에만 저장돼요. 무엇을 누구에게 보여 줄지는 일기마다 직접 골라요.", Type.Caption, Ink.text2))
                addView(k.button("시작", Kit.Btn.Primary) {
                    store.setDrivers(listOf(O.name) + O.others.filter { it != O.name })
                    store.driver = O.name; store.setType(O.name, O.type); store.setRelation(O.name, "가족")
                    O.others.forEach { if (store.relation(it) == "친구" && it in listOf("아빠", "엄마", "형", "누나", "언니", "오빠", "동생")) store.setRelation(it, "가족") }
                    store.onboarded = true; O.step = 0
                    if (store.locationOn) { places.start(); if (!places.permitted()) askLocation() }
                    go(MainActivity.TODAY)
                    speak(if (O.type == "초보") "안녕하세요. 면허 따고 처음은 낯설 수 있어요. 함께 확인해 봐요." else "안녕하세요. 오늘도 같이 가요.")
                })
            }
        }
    }
    val dots = k.row(8).apply { gravity = Gravity.CENTER; repeat(total) { i -> addView(android.view.View(context).apply { background = k.rounded(if (i == O.step.coerceAtMost(total - 1)) Ink.accent else Ink.line, 4) }, k.lp(ui.dp(if (i == O.step) 22 else 8), ui.dp(8))) } }
    return FrameLayout(this).apply {
        setBackgroundColor(Ink.bg)
        addView(k.scroll(FrameLayout(context).apply {
            setPadding(ui.dp(24), ui.dp(30), ui.dp(24), ui.dp(30))
            addView(k.col(22).apply {
                addView(dots, k.lp(k.MATCH, k.WRAP))
                addView(k.card(pad = 34) { addView(content) })
                if (O.step > 0) addView(k.button("뒤로", Kit.Btn.Plain) { O.step--; render() })
            }, FrameLayout.LayoutParams(minOf(ui.dp(640), resources.displayMetrics.widthPixels - ui.dp(48)), -2, Gravity.CENTER))
        }), FrameLayout.LayoutParams(-1, -1))
    }
}
