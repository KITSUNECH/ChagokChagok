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
    // 왼쪽: 나와 안내, 시연 / 오른쪽: 위치와 개인정보, 차와 데이터
    val left = k.col(); val right = k.col(); var into = left
    fun put(v: View) = into.addView(v, k.lp(k.MATCH, k.WRAP))
    run {
        put(k.section("운전석"))
        put(k.groupOf(listOf(
            k.item(me, "지금 운전석", trailing = k.avatar(me, 40)) { switchDriver() },
            k.item("이 차를 쓰는 방식", if (store.drivers.size > 1) store.drivers.joinToString(", ") else "다음의 나에게 길을 남겨요", "people", value = if (store.drivers.size > 1) "함께" else "혼자", chevron = true) { changeSharing() },
        )))
        put(k.section("안내"))
        put(k.groupOf(listOf(
            k.toggleItem("소리로 알려 주기", "한마디, 회전 전 깜빡이, 도착을 말로 알려 줘요", store.voiceOn, "speaker") { store.voiceOn = it },
            k.item("말 거는 정도", "달리는 동안 차가 얼마나 자주 말을 걸지", "quote", value = TALK.first { it.first == talkOf(me) }.second, chevron = true) {
                sheets.choose("말 거는 정도", "운전이 익숙해지면 줄여도 돼요. 경고는 언제나 알려 줘요.", TALK.map { it.second + " · " + it.third }, selected = TALK.indexOfFirst { it.first == talkOf(me) }) { i -> store.setType(me, TALK[i].first); render() }
            },
            k.item("목소리 들어 보기", voice.status, "play", chevron = true) { speak("안녕하세요. 차곡차곡이에요. 오늘도 같이 가요.") },
        )))
        into = right
        put(k.section("위치와 개인정보"))
        put(k.groupOf(listOf(
            k.toggleItem("한마디를 남긴 곳 근처에서 듣기", "$me 님의 위치로 한마디를 남긴 곳 근처인지 봐요. 이 차 안에만 저장돼요.", store.locationOn, "pin") {
                store.locationOn = it
                if (it) { places.start(); if (!places.permitted()) askLocation(); if (store.locationConsent(me) == null) askLocationConsent(me) }
                render()
            },
            k.toggleItem("도착지 날씨", "목적지 좌표만 공개 예보(Open-Meteo)로 보내요", store.weatherOn, "cloud") { store.weatherOn = it },
            k.item("개인정보·위치정보 안내", "무엇을 어디에 두고, 누구에게 보이는지", "lock", chevron = true) { go(MainActivity.LEARN, "privacy") },
        )))
        into = left
        if (BuildConfig.DEMOS) {
            put(k.section("시연 (공모전 심사용)"))
            put(k.groupOf(listOf(
                k.item("출근길 시연 보기", "지난 출근 기록(예시)을 넣고, 모의 주행으로 ‘늘었어요’ 화면까지 보여 줘요 · 2분", "play", chevron = true) { DemoCommute.start(this@settingsScreen) },
                k.item("시연 도구", "시연 기록 지우기, 시험용 신호, 아이디어 시연", "list", chevron = true) { go(MainActivity.DEMO) },
            )))
        }
        into = right
        put(k.section("차와 데이터"))
        put(k.groupOf(listOf(
            k.item("차량 연결 상태", "차량·내비·위치·음성이 잘 이어졌는지, 받은 신호", "car", chevron = true) { go(MainActivity.CHECK) },
            k.item("권한 다시 묻기", "위치·속도·알림 권한을 지워요. 앱을 완전히 껐다 켜면 처음처럼 물어봐요", "lock", chevron = true) {
                sheets.confirm("권한을 다시 물을까요?", "위치, 속도, 알림 권한을 지워요. 앱을 완전히 껐다가 다시 켜면 처음처럼 물어봐요. 기록은 지워지지 않아요.", "지우기") {
                    try {
                        if (android.os.Build.VERSION.SDK_INT >= 33) { revokeSelfPermissionsOnKill(listOf(Places.PERMISSION, Car.SPEED_PERMISSION, android.Manifest.permission.POST_NOTIFICATIONS)); toast("지웠어요. 앱을 완전히 껐다가 다시 켜 주세요.") }
                        else toast("이 차에서는 앱이 권한을 지울 수 없어요. 차 설정의 앱 권한에서 바꿔 주세요.")
                    } catch (_: Throwable) { toast("이 차에서는 앱이 권한을 지울 수 없어요. 차 설정의 앱 권한에서 바꿔 주세요.") }
                }
            },
            k.item("기록 모두 지우기", "일기, 건넨 길, 사람, 설정이 모두 사라져요", "trash", Ink.danger) { confirmClearAll() },
        )))
    }
    return k.col().apply {
        addView(k.header("설정"))
        if (ui.wide) addView(k.row(24).apply { gravity = Gravity.TOP; addView(left, k.lp(0, k.WRAP, 1f)); addView(right, k.lp(0, k.WRAP, 1f)) })
        else { addView(left); addView(right) }
        addView(k.gap(20))
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
    val vehicleOk = !car.failures.containsKey("vehicle") && !Car.offline
    fun tile(icon: String, name: String, state: String, ok: Boolean, onClick: (() -> Unit)? = null) = k.card(pad = 18, onClick = onClick) {
        addView(k.row(12).apply {
            addView(k.icon(icon, if (ok) Ink.go else Ink.text3, 26))
            addView(k.col(2).apply { addView(k.text(name, Type.BodyStrong)); addView(k.text(state, Type.Caption, if (ok) Ink.go else Ink.text2, lines = 1)) }, k.lp(0, k.WRAP, 1f))
        })
    }
    val missing = car.failures.keys.filter { it.isNotEmpty() }
    return k.col().apply {
        addView(k.header("차량 연결 상태", "이 앱이 차에서 실제로 받은 신호예요." + if (Telemetry.simulated > 0) " 시연으로 만든 신호 ${Telemetry.simulated}번은 세지 않았어요." else "", onBack = { back() },
            trailing = k.button("새로고침", Kit.Btn.Plain) { car.refreshNavi(); car.refreshCondition(); render() }))
        addView(k.gap(16))
        // 네 가지 연결
        addView(k.grid(if (ui.wide) 4 else 2, listOf(
            tile("car", "차량", if (vehicleOk) "연결됨 · 기어 ${car.gear}" else "연결 안 됨", vehicleOk),
            tile("navi", "내비", if (car.naviConnected) (if (car.routeStarted) "경로 안내 중" else "연결됨") else "연결 안 됨", car.naviConnected),
            tile("pin", "위치", if (places.lat != null) "받는 중" else if (places.permitted()) "허락됨 · 기다리는 중" else "허락 안 됨", places.lat != null) { if (!places.permitted()) askLocation() },
            tile("speaker", "음성", if (voice.ready) "준비됨 · 눌러서 듣기" else voice.status, voice.ready) { speak("목소리 시험이에요. 잘 들리나요?") },
        ), 12))
        // 지금 받는 신호: 한 줄에 네 개씩, 마지막 값과 받은 횟수만
        addView(k.section("받은 신호 ${Telemetry.total}번"))
        addView(k.grid(if (ui.wide) 4 else 2, Telemetry.SIGNALS.map { sig ->
            val n = Telemetry.count[sig.key] ?: 0
            k.col(2).apply {
                setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12)); background = k.rounded(if (n > 0) Ink.surface else Ink.fill, 16)
                addView(k.row(8).apply { addView(k.icon(sig.icon, if (n > 0) Ink.go else Ink.text3, 18)); addView(k.text(sig.name, Type.Sub, if (n > 0) Ink.text else Ink.text2, lines = 1)) })
                addView(k.text(if (n > 0) (Telemetry.last[sig.key]?.take(18) ?: "") + " · ${n}번" else "아직 없음", Type.Caption, Ink.text2, lines = 1))
            }
        }, 10))
        if (missing.isNotEmpty()) {
            addView(k.gap(14))
            addView(k.row(10).apply {
                gravity = Gravity.TOP
                addView(k.icon("info", Ink.text2, 18))
                addView(k.text("이 차가 주지 않는 신호 ${missing.size}가지: " + missing.joinToString(", ") { SIGNAL_NAMES[it] ?: it } + ". 없어도 기록은 되고, 그 값이 필요한 말만 빼요.", Type.Caption, Ink.text2), k.lp(0, k.WRAP, 1f))
            })
        }
        addView(k.gap(10))
        addView(k.text("화면 ${ui.w}×${ui.h}dp · 주행 제한 ${limits.status}", Type.Caption, Ink.text3))
    }
}

/** 시연 도구 (공모전 심사용): 위치가 움직이지 않는 에뮬레이터에서 앱의 흐름을 끝까지 보여 주려고 만든 것. 정식판에는 들어가지 않는다. */
internal fun MainActivity.demoScreen(): View {
    val k = kit
    return k.col().apply {
        addView(k.header("시연 도구", "공모전 심사에서 흐름을 끝까지 보여 주려고 만든 메뉴예요. 여기서 만든 기록과 신호는 화면에 모두 ‘예시’·‘시연용’으로 표시되고, 정식판에는 들어가지 않아요.", onBack = { back() }))
        addView(k.gap(16))
            addView(k.groupOf(listOf(
                k.toggleItem("아이디어 시연: 다른 차의 가족·친구와 잇기", "지금 Pleos에는 차와 차를 잇는 기능이 없어요. 그 기능이 열린다면 이렇게 쓰겠다는 생각을 가상의 친구로만 보여 줘요. 실제 기능이 아니에요.", store.demoOn, "people") { store.demoOn = it; render() },
                *(if (store.demoOn) arrayOf(k.item("아이디어 시연 열기", "함께 차곡 · 화면에 모두 시연용으로 표시돼요", "people", chevron = true) { go(MainActivity.TOGETHER) }) else emptyArray()),
                k.item("출근길 시연 다시 넣기", "예시 출근 기록 3개를 넣고 모의 주행으로 네 단계를 끝까지 보여 줘요 · 2분", "play", chevron = true) { DemoCommute.start(this@demoScreen) },
                k.item("시연 기록 지우기", "예시 출근 기록과 시연으로 달린 모의 주행을 지워요", "trash", chevron = true) {
                    sheets.confirm("시연 기록을 지울까요?", "예시 출근 기록 3개, 남겨 둔 한마디, 시연으로 달린 모의 주행이 지워져요.", "지우기", danger = true) { DemoCommute.clear(this@demoScreen); toast("시연 기록을 지웠어요."); render() }
                },
                k.item("예시 길과 친구 넣기", "아빠가 건넨 길, 친구 민수의 연습과 추천 장소 · 예시", "gift", chevron = true) { store.addSampleGuide(); store.addSampleFriend(); toast("예시를 넣었어요. 오늘과 함께 차곡에서 볼 수 있어요.") },
                k.item("시험용 신호 보내기", "가상 콘솔에 없는 신호를 시험해요 (실제 차량 신호 아님)", "sparkle", chevron = true) { testSignals() },
            )))
    }
}
