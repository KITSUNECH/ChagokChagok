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
 * 홈의 큰 시작 단추: 카드 하나 크기로 따로 둔다. 누구나 한눈에 찾고 누르기 쉽게.
 * 받은 길이 있으면 그 길로, 없으면 지금 길로. 기어를 D에 놓아도 똑같이 시작된다.
 */
internal fun MainActivity.startCard(guide: Guide?): View {
    val k = kit
    val title = if (guide != null) "이 길로 차곡차곡 시작" else "차곡차곡 시작"
    val sub = if (guide != null) "${Writer.subj(guide.owner)} 건넨 길 · 출발 전 확인으로 가요" else if (ui.narrow) "D에 놓아도 시작돼요" else "기어를 D에 놓으면 저절로 시작돼요"
    return k.col().apply {
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = ui.dp(112)
        setPadding(ui.dp(28), ui.dp(22), ui.dp(28), ui.dp(22))
        addView(k.row(20).apply {
            addView(android.widget.FrameLayout(context).apply {
                background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Ink.onAccent) }
                addView(IconView(context, "play", Ink.accent), android.widget.FrameLayout.LayoutParams(ui.dp(30), ui.dp(30), Gravity.CENTER))
            }, k.lp(ui.dp(64), ui.dp(64)))
            addView(k.col(4).apply {
                addView(k.text(title, Type.Title, Ink.onAccent))
                addView(k.text(sub, Type.Sub, Ink.alpha(Ink.onAccent, .8f)))
            }, k.lp(0, k.WRAP, 1f))
        })
        k.pressable(this, k.rounded(Ink.accent, 26)) { if (guide != null) depart(guide) else beginTrip() }
    }
}

/** 지금 길로 기록 시작. 초보는 출발 전에 안내 한 장만 (다음부터 바로 시작을 고를 수 있다) */
internal fun MainActivity.beginTrip() {
    fun go() { trip.guide = null; if (!car.destName.isNullOrBlank()) trip.routeSent = true; trip.start() }
    val d = store.driver
    if (store.typeOf(d) != "초보" || store.flag("start_guide_off_$d")) { go(); return }
    sheets.sheet("출발 전에 이것만 확인해요", null) { close ->
        val k = kit
        addView(k.groupOf(listOf(
            k.item("안전벨트", "뒷자리까지 모두", "check", Ink.go),
            k.item("의자와 거울", "발이 브레이크 끝까지 닿고, 사이드미러에 차 옆이 조금 보이게", "seat"),
            k.item("주변을 보고 기어 D", "브레이크를 밟은 채로", "gear"),
            k.item("달리는 동안은 말로 알려 줘요", "화면은 멈췄을 때만 봐요", "speaker"),
        )).apply { background = k.rounded(Ink.fill, 18) })
        addView(k.buttons(
            k.button("시작하기", Kit.Btn.Primary, "play") { close(); go() },
            k.button("다음부터 바로 시작", Kit.Btn.Plain) { store.setFlag("start_guide_off_$d"); close(); go() },
            stack = false))
    }
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
        addView(k.buttons(k.button("차곡차곡 시작", Kit.Btn.Primary, "play") { close(); beginTrip() }, k.button("나중에", Kit.Btn.Secondary) { close() }, stack = false))
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
