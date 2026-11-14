// 차곡車곡 – 내가 가는 길
// 첫 사용자, 혼자 쓰는 사람도 첫날부터 쓸 수 있게: 주인공은 "내가 가고 싶은 곳"이다.
// 내비에 목적지를 넣고 다녀오면 그 길이 그대로 차곡이 되고, 다음의 나에게 한마디를 남겨 둘 수 있다.
// 처음이라 막막한 사람을 위한 길잡이는 세 개만, 선택으로 둔다.
package com.example.chagokchagok

import android.view.Gravity
import android.view.View

internal data class Course(val id: String, val title: String, val what: String, val how: String, val check: (Drive) -> Boolean)

/** 처음이라면 해 볼 만한 것 세 가지 (선택) */
internal val COURSES = listOf(
    Course("lot", "주차장 한 바퀴", "사람과 차가 적은 주차장에서 천천히 돌고 다시 세워요.", "1분 넘게 달리고 P로 주차", { it.seconds >= 60 }),
    Course("right", "오른쪽으로만 한 블록", "좌회전 없이 우회전만 세 번 하면 제자리예요.", "오른쪽 깜빡이 3번, 왼쪽 없이", { d -> d.events.count { it.type == Evt.SIGNAL && it.value == "RIGHT" } >= 3 && d.events.none { it.type == Evt.SIGNAL && it.value == "LEFT" } }),
    Course("near", "가까운 곳 다녀오기", "편의점처럼 3km 안의 곳을 내비에 넣고 다녀와요.", "내비 목적지, 3km 안", { it.place != null && it.distKm in 0.05f..3f }),
)

/** 이번 주행으로 새로 해낸 길잡이 */
internal fun Store.newlyDoneCourses(driver: String, d: Drive): List<Course> {
    if (d.sim) return emptyList()
    val done = coursesDone(driver)
    return COURSES.filter { it.id !in done && it.check(d) }
}

/** 내가 고른 길로 간 주행인지 (혼자 간 길, 다시 가기, 지난번의 내가 남긴 길) */
internal fun Diary.ownTrip() = guideId == null || guideId.startsWith("again_") || guideId.startsWith("self_")

/** 오늘의 주인공: 내가 가고 싶은 곳 */
internal fun MainActivity.myRouteHero(): View {
    val k = kit; val me = store.driver
    val dest = car.destName?.takeIf { it.isNotBlank() }
    // 내가 다녀온 곳: 혼자 간 길과 "다시 가기"(나의 길)로 간 길. 남이 건넨 길은 빼고
    val places = store.diariesOf(me).filter { it.ownTrip() && it.drive.place != null && !it.drive.sim && it.drive.placeLat != null && it.drive.completed }
        .groupBy { it.drive.place!! }.entries.sortedByDescending { e -> e.value.last().date }.take(4)
    return k.card(28) {
        // 다녀온 곳이 있으면 주인공은 "다시 갈 곳": 같은 길을 다시 달리면 지난번의 나와 비교하고, 남긴 한마디를 그 근처에서 들려준다
        if (places.isNotEmpty()) {
            addView(k.text("다시 가 볼까요?", Type.Display))
            addView(k.gap(8))
            addView(k.text("같은 곳에 다시 가면 지난번에 남긴 한마디를 그 근처에서 들려주고, 주차한 뒤 지난번의 나와 나란히 보여 줘요.", Type.Body, Ink.text2))
            addView(k.gap(18))
            addView(k.groupOf(places.take(3).map { (place, ds) ->
                val self = store.selfGuide(me, place)
                val note = self?.tips?.firstOrNull()?.text
                k.item(place, (if (note != null) "지난번 한마디 “$note”" else "지난번 ${dateText(ds.last().date, "M월 d일")}") + " · ${ds.size}번 다녀옴",
                    if (note != null) "quote" else "pin",
                    trailing = if (ds.size >= 2) k.iconButton("chart", Ink.accent, Ink.accentSoft, 44) { go(MainActivity.ROAD, ds.last().id) } else k.icon("chevron", Ink.text3, 20)) { goAgain(place, ds.last()) }
            }).apply { background = k.rounded(Ink.fill, 18) })
            addView(k.gap(18))
            addView(k.text("새로운 곳", Type.Sub, Ink.text2))
            addView(k.gap(8))
        } else {
            addView(k.text("오늘은 어디로 갈까요?", Type.Display))
            addView(k.gap(8))
            addView(k.text("가고 싶은 곳을 내비에 넣고 출발하세요. 차가 오늘 운전을 기억해 두었다가, 같은 곳에 다시 갈 때 꺼내 줘요.", Type.Body, Ink.text2))
            addView(k.gap(20))
        }
        if (dest != null) {
            addView(k.item(dest, "내비에 넣은 목적지 · 아래 단추로 출발해요", "pin").apply { background = k.rounded(Ink.accentSoft, 18) })
        } else {
            // 실제 Pleos 내비의 장소 검색 화면을 연다 (NaviHelper requestNavigationScreen)
            val find = k.button("내비에서 찾기", Kit.Btn.Tinted, "navi") { if (!car.openNavi()) toast("내비가 연결되지 않았어요. 내비 앱에서 목적지를 골라 주세요.") }
            val words = k.row(14).apply {
                addView(k.icon("pin", Ink.accent, 26))
                addView(k.col(2).apply {
                    addView(k.text("목적지가 아직 없어요", Type.BodyStrong))
                    addView(k.text("없어도 괜찮아요. 기어를 D에 놓으면 오늘 주행이 기록돼요.", Type.Caption, Ink.text2))
                }, k.lp(0, k.WRAP, 1f))
            }
            // 좁은 창(내비 옆)에서는 글과 단추를 위아래로 쌓는다 (옆으로 두면 글이 한 글자씩 꺾인다)
            addView((if (ui.narrow) k.col(12) else k.row(14)).apply {
                setPadding(ui.dp(18), ui.dp(14), ui.dp(14), ui.dp(14)); background = k.rounded(Ink.surface, 18, 1, Ink.line)
                addView(words, if (ui.narrow) k.lp(k.MATCH, k.WRAP) else k.lp(0, k.WRAP, 1f))
                addView(find, if (ui.narrow) k.lp(k.MATCH, k.WRAP) else k.lp(k.WRAP, k.WRAP))
            })
        }
    }
}

/**
 * 홈 맨 위 "출발 준비": 지금 차가 알려 주는 것(안전벨트, 연료·배터리, 내비 목적지)을 그대로 보여 준다.
 * 기록은 기어를 D에 놓으면 저절로 시작한다. 기어 신호를 받지 못하는 차에서만 이 카드를 눌러 직접 시작한다.
 */
internal fun MainActivity.startCard(guide: Guide?): View {
    val k = kit
    val auto = car.gearKnown
    fun pill(icon: String, label: String, ok: Boolean?) = k.row(8).apply {
        setPadding(ui.dp(14), ui.dp(9), ui.dp(16), ui.dp(9)); background = k.rounded(Ink.alpha(Ink.onAccent, .14f), 22)
        addView(k.icon(if (ok == true) "checkCircle" else if (ok == false) "warning" else icon, Ink.onAccent, 20))
        addView(k.text(label, Type.Sub, Ink.onAccent))
    }
    return k.col().apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(112)
        setPadding(ui.dp(28), ui.dp(22), ui.dp(28), ui.dp(22))
        addView(k.row(20).apply {
            addView(android.widget.FrameLayout(context).apply {
                background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Ink.onAccent) }
                addView(IconView(context, if (auto) "steering" else "play", Ink.accent), android.widget.FrameLayout.LayoutParams(ui.dp(32), ui.dp(32), Gravity.CENTER))
            }, k.lp(ui.dp(64), ui.dp(64)))
            addView(k.col(4).apply {
                addView(k.text(if (auto) "기어를 D에 놓으면 기록을 시작해요" else "기록 시작", Type.Title, Ink.onAccent))
                addView(k.text(if (auto) "따로 누르지 않아도 돼요. 달리는 동안은 말로 알려 줄게요." else "기어 신호를 받지 못했어요. 누르면 바로 기록을 시작해요.", Type.Sub, Ink.alpha(Ink.onAccent, .8f)))
            }, k.lp(0, k.WRAP, 1f))
        })
        // 지금 차가 알려 주는 것 (받은 신호만 보여 준다)
        val pills = listOfNotNull(
            if (car.beltKnown) pill("seat", if (car.belt) "안전벨트 맸어요" else "안전벨트를 매 주세요", car.belt) else null,
            car.energy?.let { pill("fuel", (if (car.isEv == true) "배터리 " else "연료 ") + "$it%", if (it < 20) false else null) },
            car.destName?.takeIf { it.isNotBlank() }?.let { pill("pin", it, null) },
        )
        if (pills.isNotEmpty()) { addView(k.gap(14)); addView(k.flow(8).apply { pills.forEach { addView(it) } }) }
        k.pressable(this, k.rounded(Ink.accent, 26)) { if (guide != null) depart(guide) else beginTrip() }
    }
}

/**
 * 기록 시작. 기어 신호를 받는 차에서 아직 P면 시작하지 않고(기록은 D에서 시작한다) 안전벨트만 확인해 알려 준다.
 * 기어 신호가 없는 차에서는 여기서 바로 시작한다.
 */
internal fun MainActivity.beginTrip() {
    if (car.gearKnown && car.gear != "D") {
        say(if (car.beltKnown && !car.belt) "안전벨트를 매고, 기어를 D에 놓으면 기록을 시작해요." else "기어를 D에 놓으면 기록을 시작해요.")
        return
    }
    trip.guide = null; if (!car.destName.isNullOrBlank()) trip.routeSent = true; trip.start()
}

/** 다녀온 곳에 다시 가기. 지난번의 내가 남긴 한마디가 있으면 그 길로 */
internal fun MainActivity.goAgain(place: String, last: Diary) {
    val me = store.driver
    val self = store.selfGuide(me, place)
    depart(self ?: Guide("again_${place.hashCode()}", me, place, last.drive.placeLat, last.drive.placeLng, last.drive, emptyList(), 1, kind = "다시"))
}

/** 처음이라면 (선택): 길잡이 세 개 */
internal fun MainActivity.starterCard(): View? {
    val k = kit; val done = store.coursesDone(store.driver)
    if (done.size >= COURSES.size) return null
    return k.card(pad = 20) {
        addView(k.row().apply {
            addView(k.text("처음이라면", Type.Headline), k.lp(0, k.WRAP, 1f))
            addView(k.text("선택 · ${done.size}/${COURSES.size}", Type.Caption, Ink.text2))
        })
        addView(k.gap(10))
        addView(k.groupOf(COURSES.map { c ->
            val ok = c.id in done
            k.item(c.title, if (ok) "해냈어요" else c.what, if (ok) "checkCircle" else "circle", if (ok) Ink.go else Ink.text3, chevron = !ok) { if (!ok) startCourse(c) }
        }).apply { background = k.rounded(Ink.fill, 18) })
    }
}

internal fun MainActivity.startCourse(c: Course) {
    sheets.sheet(c.title, c.what) { close ->
        val k = kit
        addView(k.item("해냈는지는 이렇게 확인해요", c.how, "check", Ink.go).apply { background = k.rounded(Ink.fill, 16) })
        addView(k.buttons(k.button("기록 시작", Kit.Btn.Primary, "play") { close(); beginTrip() }, k.button("나중에", Kit.Btn.Secondary) { close() }, stack = false))
    }
}

/** 이번 주의 나: 혼자 쓰는 사람에게 돌려주는 작은 요약 */
internal fun MainActivity.weekCard(): View? {
    val k = kit; val me = store.driver
    val weekAgo = System.currentTimeMillis() - 7 * 86400000L
    val ds = store.diariesOf(me).filter { !it.drive.sim && it.date >= weekAgo }
    if (ds.isEmpty()) return null
    val km = ds.sumOf { it.drive.distKm.toDouble() }
    val places = ds.mapNotNull { it.drive.place }.distinct()
    val stats = k.grid(3, listOf(k.stat("${ds.size}번", "달린 날"), k.stat(if (km >= 10) String.format("%.0fkm", km) else String.format("%.1fkm", km), "거리"), k.stat("${places.size}곳", "다녀온 곳")), 10)
    val words = k.col(6).apply {
        addView(k.text("이번 주의 나", Type.Headline))
        addView(k.text(if (ds.size >= 3) "일주일에 ${ds.size}번 운전석에 앉았어요. 손이 익어 가고 있어요." else "이번 주에도 운전석에 앉았어요. 그게 제일 어려운 일이에요.", Type.Sub, Ink.text2))
    }
    // 넓은 창: 전체 폭 한 줄 (말은 왼쪽, 숫자는 오른쪽). 좁은 창: 위아래.
    return k.card {
        if (ui.wide) addView(k.row(28).apply { addView(words, k.lp(0, k.WRAP, 1f)); addView(stats, k.lp(0, k.WRAP, 1.3f)) })
        else { addView(words); addView(k.gap(12)); addView(stats) }
    }
}

/** 일기에서: 다음의 나에게 한마디 (같은 곳에 다시 가면 그 자리에서 들려준다). 주행 기록으로 만든 후보 셋 중 여러 개 + 직접 쓰기 */
internal fun MainActivity.noteToSelf(d: Diary) {
    val place = d.drive.place ?: store.guide(d.guideId)?.title ?: "지난번 그 길"
    pickTips("다음의 나에게", "다음에 ‘$place’에 갈 때 그 근처에서 들려줄게요.", TipSuggest.ideas(d.drive, forSelf = true)) { picked ->
        keepForNextTime(d, picked)
        render()
    }
}
