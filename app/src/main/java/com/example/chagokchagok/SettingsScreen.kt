// 차곡車곡 – 설정 · 함께 쓰는 사람 · 알아 두기 · 연결 상태 · 처음 시작
package com.example.chagokchagok

import android.view.Gravity
import android.view.View
import android.widget.EditText
import android.widget.FrameLayout

/** 혼자 ↔ 함께는 언제든 바꿀 수 있다. 기록과 건넨 길은 지우지 않는다. */
internal fun MainActivity.changeSharing() {
    val solo = store.drivers.size <= 1
    sheets.choose("이 차를 쓰는 방식", "언제든 바꿀 수 있어요. 일기와 건넨 길은 그대로 남아요.", listOf("혼자 써요", "함께 써요"), listOf("steering", "people"), selected = if (solo) 0 else 1) { i ->
        if (i == 1) {
            if (solo) sheets.input("함께 쓰는 사람의 이름", "예: 엄마", ok = "추가") { n -> store.addDriver(n.trim()); store.setRelation(n.trim(), "가족"); toast("$n 님을 더했어요. 운전석을 바꾸면 따로 기록돼요."); go(MainActivity.PEOPLE) }
            else go(MainActivity.PEOPLE)
        } else if (!solo) {
            val others = store.drivers.filter { it != store.driver }
            sheets.confirm("혼자 쓰기로 바꿀까요?", "${others.joinToString(", ")} 님을 운전석 목록에서 빼요. 그동안 쓴 일기와 건넨 길은 남아요.", "혼자 쓰기") {
                others.forEach { store.removeDriver(it) }; render(); toastUndo("혼자 쓰기로 바꿨어요.") { others.forEach { store.addDriver(it) } }
            }
        }
    }
}

internal fun MainActivity.settingsScreen(): View {
    // 설정에는 정말 설정만. 안전·알아 두기는 출발 준비와 홈에서, 시연 도구는 차량 데이터 화면에서.
    val k = kit; val me = store.driver
    return k.col().apply {
        addView(k.header("설정"))
        addView(k.section("운전석"))
        addView(k.groupOf(listOf(
            k.item(me, "지금 운전석", trailing = k.avatar(me, 40)) { switchDriver() },
            k.item("이 차를 쓰는 방식", if (store.drivers.size > 1) store.drivers.joinToString(", ") else "다음의 나에게 길을 남겨요", "people", value = if (store.drivers.size > 1) "함께" else "혼자", chevron = true) { changeSharing() },
        )))
        addView(k.section("안내"))
        addView(k.groupOf(listOf(
            k.toggleItem("소리로 알려 주기", "한마디, 회전 전 깜빡이, 도착을 말로 알려 줘요", store.voiceOn, "speaker") { store.voiceOn = it },
            k.item("말 거는 정도", "달리는 동안 차가 얼마나 자주 말을 걸지", "quote", value = TALK.first { it.first == talkOf(me) }.second, chevron = true) {
                sheets.choose("말 거는 정도", "운전이 익숙해지면 줄여도 돼요. 경고는 언제나 알려 줘요.", TALK.map { it.second + " · " + it.third }, selected = TALK.indexOfFirst { it.first == talkOf(me) }) { i -> store.setType(me, TALK[i].first); render() }
            },
            k.item("목소리 들어 보기", voice.status, "play", chevron = true) { speak("안녕하세요. 차곡차곡이에요. 오늘도 같이 가요.") },
        )))
        addView(k.section("위치와 개인정보"))
        addView(k.groupOf(listOf(
            k.toggleItem("한마디를 남긴 곳 근처에서 듣기", "$me 님의 위치로 한마디를 남긴 곳 근처인지 봐요. 이 차 안에만 저장돼요.", store.locationOn, "pin") {
                store.locationOn = it
                if (it) { places.start(); if (!places.permitted()) askLocation(); if (store.locationConsent(me) == null) askLocationConsent(me) }
                render()
            },
            k.toggleItem("도착지 날씨", "목적지 좌표만 공개 예보(Open-Meteo)로 보내요", store.weatherOn, "cloud") { store.weatherOn = it },
            k.item("개인정보·위치정보 안내", "무엇을 어디에 두고, 누구에게 보이는지", "lock", chevron = true) { go(MainActivity.LEARN, "privacy") },
        )))
        addView(k.section("연결"))
        addView(k.groupOf(listOf(
            k.item("차량 연결 상태", "차량·내비·위치·음성이 잘 이어졌는지", "car", chevron = true) { go(MainActivity.CHECK) },
            *(if (BuildConfig.DEMOS) arrayOf(k.item("시연: 회사 가는 길, 네 번째 출근", "예시 출근 기록 3개와 모의 주행 · 공모전판에만 있어요", "play", chevron = true) { DemoCommute.start(this@settingsScreen) }) else emptyArray()),
        )))
        addView(k.section("데이터"))
        addView(k.groupOf(listOf(
            k.item("기록 모두 지우기", "일기, 건넨 길, 사람, 설정이 모두 사라져요", "trash", Ink.danger) { confirmClearAll() },
        )))
        addView(k.gap(24))
        addView(k.text("차곡차곡 0.3 · 기록은 이 차 안에만 암호화해 저장돼요.\n글꼴: Pretendard, 고운바탕, Noto Sans KR (SIL Open Font License)", Type.Caption, Ink.text3).apply { setPadding(ui.dp(4), 0, 0, 0) })
    }
}

/** 모두 지우기: 두 번 묻고, 지운 뒤 알린다 */
internal fun MainActivity.confirmClearAll() {
    sheets.confirm("기록을 모두 지울까요?", "일기 ${store.diaries().size}개, 건넨 길 ${store.guides().size}개, 함께 쓰는 사람과 설정이 이 차에서 사라져요.", "다음", true) {
        sheets.confirm("정말 지울까요?", "지우면 되돌릴 수 없어요.", "모두 지우기", true) {
            store.clearAll(); store.onboarded = false; go(MainActivity.ONBOARD); toast("기록을 모두 지웠어요.")
        }
    }
}

/** 시험용 신호: 가상 콘솔에 없는 조작. 실제 차량 신호가 아니다. */
internal fun MainActivity.testSignals() {
    val items = listOf(
        "깜빡이 왼쪽" to { onCarEvent(Evt.SIGNAL, "LEFT") }, "깜빡이 오른쪽" to { onCarEvent(Evt.SIGNAL, "RIGHT") }, "깜빡이 끄기" to { onCarEvent(Evt.SIGNAL, "OFF") },
        "브레이크" to { onCarEvent(Evt.BRAKE, "on") }, "와이퍼 켜기" to { onCarEvent(Evt.WIPER, "on") }, "와이퍼 끄기" to { onCarEvent(Evt.WIPER, "off") },
        "전조등 켜기" to { onCarEvent(Evt.LIGHT, "on") }, "안전벨트 매기" to { onCarEvent("belt", "on") }, "동승자 탑승" to { onCarEvent(Evt.SEAT, "on") },
        "내비: 150m 앞 우회전" to { car.simTurn("TURN_RIGHT", 150) }, "내비: 80m 앞 우회전" to { car.simTurn("TURN_RIGHT", 80) }, "내비: 회전 지남" to { car.simTurn(null, 0) },
        "표시등: 오토 홀드" to { car.setLamps(car.lampsOn + "AUTO_HOLD") }, "표시등: 타이어 공기압" to { car.setLamps(car.lampsOn + "TPMS") }, "표시등 모두 끄기" to { car.setLamps(emptySet()) },
        "차선 변경 (깜빡이 켜고 8초 뒤)" to { car.simLaneChange("LEFT", 8f, android.os.Handler(mainLooper)) },
        "기어 D" to { onCarEvent(Evt.GEAR, "D") }, "기어 R" to { onCarEvent(Evt.GEAR, "R") }, "기어 P" to { onCarEvent(Evt.GEAR, "P") },
    )
    sheets.choose("시험용 신호", "실제 차량 신호가 아니에요. ‘받은 신호’에 세지 않고 따로 세요.", items.map { it.first }) { i -> Telemetry.muted = true; try { items[i].second() } finally { Telemetry.muted = false } }
}

// ═══════════════ 함께 쓰는 사람 ═══════════════

/** 말 거는 정도 (저장은 예전 '운전 경험' 값 그대로: 초보 = 자주, 오랜만·새 차 = 필요할 때, 익숙 = 경고만) */
/** 받지 못한 신호의 사람 말 이름 */
internal val SIGNAL_NAMES = mapOf("washer" to "워셔액", "oil" to "엔진오일", "brakeFluid" to "브레이크액", "fuel" to "연료량", "battery" to "배터리 잔량", "economy" to "연비", "evEconomy" to "전비",
    Evt.SPEED to "속도", Evt.GEAR to "기어", Evt.SIGNAL to "방향지시등", Evt.BRAKE to "브레이크", "steering" to "조향각", Evt.WIPER to "와이퍼", Evt.LIGHT to "전조등", "belt" to "안전벨트",
    Evt.CRUISE to "크루즈", Evt.SEAT to "좌석 감지", "seatPos" to "운전석 위치", "distance" to "주행거리", "navi" to "내비", "vin" to "차대번호", "vehicle" to "차량 연결")

internal val TALK = listOf(Triple("초보", "자주", "돌기 전 깜빡이, 잘한 것까지"), Triple("오랜만", "필요할 때", "한마디와 경고"), Triple("익숙", "경고만", "꼭 필요한 것만"))
internal fun MainActivity.talkOf(n: String) = when (store.typeOf(n)) { "초보" -> "초보"; "익숙" -> "익숙"; else -> "오랜만" }

internal val DRIVER_TYPES = listOf("초보" to "면허를 딴 지 얼마 안 됐어요", "오랜만" to "오랜만에 다시 운전해요", "새 차" to "운전은 하는데 이 차가 처음이에요", "익숙" to "운전이 익숙해요")

internal fun MainActivity.peopleScreen(): View {
    val k = kit
    return k.col().apply {
        addView(k.header("함께 쓰는 사람", "이 차를 함께 쓰는 가족과 친구예요. 운전석에 앉은 사람마다 받은 길과 일기가 따로 쌓여요.", onBack = { back() }))
        addView(k.gap(18))
        addView(k.groupOf(store.drivers.map { n ->
            k.item(n, store.relation(n) + if (n == store.driver) " · 지금 운전석" else "", trailing = k.avatar(n, 36)) { editPerson(n) }
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
            else -> if (store.drivers.size <= 1) toast("한 사람은 남아 있어야 해요.") else sheets.confirm("$n 님을 목록에서 뺄까요?", "쓴 일기와 건넨 길은 남아요.", "빼기", true) { val rel = store.relation(n); store.removeDriver(n); render(); toastUndo("$n 님을 목록에서 뺐어요.") { store.addDriver(n); store.setRelation(n, rel) } }
        }
    }
}

// ═══════════════ 알아 두기 ═══════════════

internal fun MainActivity.learnScreen(key: String?): View {
    val k = kit
    if (key == null || key == "law") return lawScreen()
    if (key == "tips") return tipsScreen()
    if (key == "privacy") return privacyScreen()
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

/** 면허 딴 해 이후 바뀐 법규: 시행일 순 타임라인, 항목마다 위반 시와 출처 */
internal fun MainActivity.lawScreen(): View {
    val k = kit; val d = store.driver
    val year = store.licenseYear(d)
    val since = Laws.since(year)
    val older = Laws.changes.filter { it !in since }
    fun lawCard(l: Law, last: Boolean): View = k.row(18).apply {
        gravity = Gravity.TOP
        // 왼쪽: 시행일과 이어지는 선
        addView(k.col(6).apply {
            gravity = Gravity.CENTER_HORIZONTAL
            if (l.dated) {
                addView(k.text(l.date.take(4), Type.BodyStrong, Ink.accent).apply { gravity = Gravity.CENTER })
                addView(k.text(l.date.substring(5).replace("-", "."), Type.Caption, Ink.text3).apply { gravity = Gravity.CENTER })
            } else addView(k.icon("scale", Ink.accent, 24))
            if (!last) addView(View(context).apply { setBackgroundColor(Ink.line) }, k.lp(ui.dp(2), ui.dp(60)))
        }, k.lp(ui.dp(64), k.WRAP))
        addView(k.card(pad = 20) {
            addView(k.row().apply {
                addView(k.text(l.title, Type.Headline), k.lp(0, k.WRAP, 1f))
                addView(k.iconButton("speaker", Ink.accent, Ink.accentSoft, 40) { speak(l.title + ". " + l.line) })
            })
            addView(k.gap(6)); addView(k.text(l.line, Type.Body, Ink.text2))
            addView(k.gap(12))
            addView(k.row(8).apply {
                l.penalty?.let { addView(k.tag("위반하면 · $it", Ink.danger)) }
            })
            addView(k.gap(8))
            addView(k.row(6).apply { addView(k.icon("book", Ink.text3, 14)); addView(k.text("근거 · ${l.source}" + (if (l.dated) " · ${l.date.replace("-", ".")} 시행" else "") + " · " + l.url.substringAfter("//").substringBefore("/"), Type.Caption, Ink.text3), k.lp(0, k.WRAP, 1f)) })
        }, k.lp(0, k.WRAP, 1f))
    }
    return k.col().apply {
        addView(k.header(if (since.isEmpty()) "꼭 알아 둘 법규" else "면허 딴 뒤 바뀐 법규", 
            (year?.let { "${it}년에 면허를 땄다면 · " } ?: "면허 딴 해를 모를 때는 전부 · ") + "시행일 순 · 법제처 생활법령정보·정책브리핑 기준", onBack = { back() },
            trailing = k.button(year?.let { "${it}년" } ?: "면허 딴 해", Kit.Btn.Tinted, "pencil") { pickLicenseYear() }))
        addView(k.gap(18))
        // 요약: 바뀐 것 · 그 전 것 · 꼭 알 것
        addView(k.grid(3, listOf(k.stat("${since.size}가지", "면허 딴 뒤 바뀜"), k.stat("${older.size}가지", "그 전에 바뀜"), k.stat("${Laws.core.size}가지", "언제든 꼭")), 12).apply { background = k.rounded(Ink.surface, 18); setPadding(ui.dp(20), ui.dp(16), ui.dp(20), ui.dp(16)) })
        addView(k.gap(20))
        if (since.isNotEmpty()) {
            addView(k.section("면허 딴 뒤 바뀐 것 · 최근부터"))
            val list = since.reversed(); list.forEachIndexed { i, l -> addView(lawCard(l, i == list.lastIndex)); addView(k.gap(10)) }
        }
        addView(k.section("언제 땄든 꼭 알아 둘 것"))
        Laws.core.forEachIndexed { i, l -> addView(lawCard(l, i == Laws.core.lastIndex)); addView(k.gap(10)) }
        if (older.isNotEmpty()) {
            addView(k.section("면허 따기 전에 바뀐 것 · 면허 시험에 나왔어요"))
            addView(k.groupOf(older.reversed().map { l -> k.item(l.title, "${l.date.take(4)} · ${l.source}", "check", Ink.text3) }))
        }
    }
}

internal fun MainActivity.pickLicenseYear() {
    val y = Laws.thisYear
    val years = (0..30).map { y - it }
    sheets.choose("면허는 언제 땄나요?", "그 뒤로 바뀐 법규만 골라 보여 드려요.", years.map { "${it}년" + if (it == y) " · 올해" else " · ${y - it}년 전" } + "기억 안 나요",
        selected = store.licenseYear(store.driver)?.let { years.indexOf(it) } ?: years.size) { i ->
        store.setLicenseYear(store.driver, years.getOrNull(i)); render()
    }
}

/** 개인정보·위치정보 안내 (Pleos 심사: 위치를 쓰는 앱은 처리방침과 위치정보 이용 안내가 필요) */
internal fun MainActivity.privacyScreen(): View {
    val k = kit
    val rows = listOf(
        Triple("무엇을 쓰나요", "차량 신호(깜빡이·브레이크·기어·와이퍼·전조등·안전띠·좌석·주행거리), 내비 목적지와 회전 안내, 차의 위치(동의한 운전자만), 운전자가 적은 이름과 한마디", "list"),
        Triple("어디에 두나요", "이 차 안에만 둬요. 안드로이드 키스토어 키로 암호화한 저장소에 저장하고, 앱 서버는 없어요.", "lock"),
        Triple("위치는", "운전자마다 따로 동의를 받아요. 동의하지 않으면 위치를 쓰지도 남기지도 않아요. 근거: 위치정보법 제15조.", "pin"),
        Triple("밖으로 나가는 것", "도착지 날씨를 켜면 목적지 좌표만 공개 예보(Open-Meteo)로, AI 다듬기를 켜면 일기 초안만 Anthropic API로 가요. 둘 다 끌 수 있어요.", "share"),
        Triple("누구에게 보이나요", "함께 쓰는 사람에게는 내가 고른 일기와 건넨 길만 보여요. 길을 건넬 때는 출발한 곳 근처 500m를 빼요.", "people"),
        Triple("지우려면", "설정 › 데이터 › 기록 모두 지우기. 이 차에서 바로 지워져요.", "trash"),
    )
    return k.col().apply {
        addView(k.header("개인정보·위치정보 안내", "차곡차곡이 다루는 정보와 지키는 방법", onBack = { back() }))
        addView(k.gap(18))
        addView(k.groupOf(rows.map { (t, b, ic) -> k.item(t, b, ic) }))
    }
}

// ═══════════════ 연결 상태 ═══════════════

internal fun MainActivity.checkScreen(): View {
    val k = kit
    fun ok(b: Boolean) = if (b) Ink.go else Ink.text3
    val vehicleOk = !car.failures.containsKey("vehicle") && !Car.offline
    return k.col().apply {
        addView(k.header("차량 연결 상태", "이 앱이 실제로 받은 차량 신호예요." + if (Telemetry.simulated > 0) " 시연·시험으로 만든 신호 ${Telemetry.simulated}번은 여기에 세지 않았어요." else " 시연·시험용 신호는 세지 않아요.", onBack = { back() }, trailing = k.button("새로고침", Kit.Btn.Plain) { car.refreshNavi(); car.refreshCondition(); render() }))
        addView(k.gap(16))
        val mins = ((System.currentTimeMillis() - Telemetry.since) / 60000).coerceAtLeast(1)
        addView(k.grid(if (ui.wide) 4 else 2, listOf(
            k.stat("${Telemetry.total}", "받은 신호"),
            k.stat("${Telemetry.kinds} / ${Telemetry.SIGNALS.size}", "신호 종류"),
            k.stat("${Telemetry.perMinute()}", "최근 1분"),
            k.stat("${mins}분", "앱을 켠 뒤"),
        ), 14))
        addView(k.section("신호별"))
        addView(k.groupOf(Telemetry.SIGNALS.map { s ->
            val n = Telemetry.count[s.key] ?: 0
            val ago = Telemetry.at[s.key]?.let { ((System.currentTimeMillis() - it) / 1000).let { sec -> if (sec < 60) "${sec}초 전" else "${sec / 60}분 전" } }
            k.item(s.name, (if (s.sdk.startsWith("AAOS")) s.sdk else "${s.sdk} SDK") + (Telemetry.last[s.key]?.let { " · 마지막 값 $it" } ?: "") + (ago?.let { " · $it" } ?: ""), s.icon, if (n > 0) Ink.go else Ink.text3,
                value = if (n > 0) "${n}건" else "아직 없음", valueColor = if (n > 0) Ink.text else Ink.text3)
        }))
        addView(k.section("연결 상태 · 차량 (Vehicle SDK)"))
        addView(k.groupOf(listOfNotNull(
            k.item("차량 신호", if (vehicleOk) "연결됨" else "연결 안 됨", "car", ok(vehicleOk)),
            k.item("기어", null, "steering", value = car.gear),
            k.item("주행거리계", null, "road", value = car.odometerKm?.let { String.format("%.1fkm", it) } ?: "읽지 못함"),
            k.item("연료/배터리", null, "fuel", value = car.energy?.let { "$it%" } ?: "읽지 못함"),
            k.item("경고 (엔진오일·브레이크액)", "Safety · CAR_SAFETY", "warning", value = car.lampsOn.joinToString().ifEmpty { "없음" }),
            k.item("표시등 · 타이어 공기압", "Pleos 비공개 권한(PRIVATE_CAR_SAFETY)이라 써드파티 앱은 받을 수 없어요 · 시험용 신호로만", "car", value = "쓰지 않음"),
            k.item("운전석 위치", null, "seat", value = car.seatPos?.toString() ?: "읽지 못함"),
            // 이 차(또는 에뮬레이터)가 주지 않는 신호: 오류 글을 그대로 쏟지 않고 이름만. 앱은 그 값 없이 동작한다.
            car.failures.keys.filter { it.isNotEmpty() }.takeIf { it.isNotEmpty() }?.let { ks ->
                k.item("이 차에서 받지 못한 신호 ${ks.size}가지", ks.joinToString(" · ") { SIGNAL_NAMES[it] ?: it } + "\n없어도 기록은 돼요. 그 값이 필요한 문장만 빼고 말해요.", "info", Ink.text2)
            },
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
        // 공모전 시연 도구 (마켓판에는 없음)
        if (BuildConfig.DEMOS) {
            addView(k.section("시연 도구 (공모전 심사용, 정식판에는 없어요)"))
            addView(k.groupOf(listOf(
                k.toggleItem("아이디어 시연: 다른 차의 가족·친구와 잇기", "지금 Pleos에는 차와 차를 잇는 기능이 없어요. 그 기능이 열린다면 이렇게 쓰겠다는 생각을 가상의 친구로만 보여 줘요. 실제 기능이 아니에요.", store.demoOn, "people") { store.demoOn = it; render() },
                *(if (store.demoOn) arrayOf(k.item("아이디어 시연 열기", "함께 차곡 · 화면에 모두 시연용으로 표시돼요", "people", chevron = true) { go(MainActivity.TOGETHER) }) else emptyArray()),
                k.item("시연: 회사 가는 길, 네 번째 출근", "예시 출근 기록 3개를 넣고 모의 주행으로 네 단계를 끝까지 보여 줘요 · 2분", "play", chevron = true) { DemoCommute.start(this@checkScreen) },
                k.item("시연 기록 지우기", "예시 출근 기록과 시연으로 달린 모의 주행을 지워요", "trash", chevron = true) {
                    sheets.confirm("시연 기록을 지울까요?", "예시 출근 기록 3개, 남겨 둔 한마디, 시연으로 달린 모의 주행이 지워져요.", "지우기", danger = true) { DemoCommute.clear(this@checkScreen); toast("시연 기록을 지웠어요."); render() }
                },
                k.item("예시 길과 친구 넣기", "아빠가 건넨 길, 친구 민수의 연습과 추천 장소 · 예시", "gift", chevron = true) { store.addSampleGuide(); store.addSampleFriend(); toast("예시를 넣었어요. 오늘과 함께 차곡에서 볼 수 있어요.") },
                k.item("시험용 신호 보내기", "가상 콘솔에 없는 신호를 시험해요 (실제 차량 신호 아님)", "sparkle", chevron = true) { testSignals() },
            )))
        }
    }
}


// 지금 Pleos에서 실제로 되는 것과, 플랫폼이 이렇게 발전하면 차곡車곡이 할 수 있는 것을 나눠 보여 준다.

