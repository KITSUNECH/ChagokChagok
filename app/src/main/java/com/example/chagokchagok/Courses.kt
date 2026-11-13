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

/** 오늘의 주인공: 내가 가고 싶은 곳 */
internal fun MainActivity.myRouteHero(): View {
    val k = kit; val me = store.driver
    val dest = car.destName?.takeIf { it.isNotBlank() }
    val places = store.diariesOf(me).filter { it.guideId == null && it.drive.place != null && !it.drive.sim && it.drive.placeLat != null }
        .groupBy { it.drive.place!! }.entries.sortedByDescending { e -> e.value.last().date }.take(4)
    return k.card(28) {
        addView(k.text("오늘은 어디로 갈까요?", Type.Display))
        addView(k.gap(8))
        addView(k.text("가고 싶은 곳을 내비에 넣고 출발하세요. 내가 가는 길이 그대로 차곡이 돼요.", Type.Body, Ink.text2))
        addView(k.gap(20))
        if (dest != null) {
            addView(k.item(dest, "내비에 넣은 목적지", "pin", trailing = k.button("이 길로 출발", Kit.Btn.Primary, "play") { trip.guide = null; trip.routeSent = true; trip.start() }).apply { background = k.rounded(Ink.accentSoft, 18) })
        } else {
            addView(k.row(14).apply {
                setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16)); background = k.rounded(Ink.fill, 18)
                addView(k.icon("pin", Ink.accent, 26))
                addView(k.col(2).apply {
                    addView(k.text("내비에서 목적지를 골라 주세요", Type.BodyStrong))
                    addView(k.text("목적지가 없어도 괜찮아요. 기어를 D에 놓으면 오늘 주행이 기록돼요.", Type.Caption, Ink.text2))
                }, k.lp(0, k.WRAP, 1f))
            })
        }
        if (places.isNotEmpty()) {
            addView(k.gap(18))
            addView(k.text("다녀온 곳", Type.Sub, Ink.text2))
            addView(k.gap(8))
            addView(k.hscroll(k.row(10).apply {
                places.forEach { (place, ds) ->
                    val self = store.selfGuide(me, place)
                    addView(k.chip(place + if (self != null && self.tips.isNotEmpty()) " · 한마디 ${self.tips.size}" else "", false, "pin") { goAgain(place, ds.last()) })
                }
            }))
        }
        addView(k.gap(20))
        addView(k.buttons(k.button("지금 기록 시작", if (dest == null) Kit.Btn.Primary else Kit.Btn.Secondary, "play") { trip.guide = null; trip.start() }, stack = false))
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
        addView(k.buttons(k.button("지금 기록 시작", Kit.Btn.Primary, "play") { close(); trip.guide = null; trip.start() }, k.button("나중에", Kit.Btn.Secondary) { close() }, stack = false))
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
    return k.card {
        addView(k.text("이번 주의 나", Type.Headline))
        addView(k.gap(12))
        addView(k.grid(3, listOf(k.stat("${ds.size}번", "달린 날"), k.stat(if (km >= 10) String.format("%.0fkm", km) else String.format("%.1fkm", km), "거리"), k.stat("${places.size}곳", "다녀온 곳")), 10))
        addView(k.gap(10))
        addView(k.text(if (ds.size >= 3) "일주일에 ${ds.size}번 운전석에 앉았어요. 손이 익어 가고 있어요." else "이번 주에도 운전석에 앉았어요. 그게 제일 어려운 일이에요.", Type.Sub, Ink.text2))
    }
}

/** 일기에서: 다음의 나에게 한마디 (같은 곳에 다시 가면 그 자리에서 들려준다) */
internal fun MainActivity.noteToSelf(d: Diary) {
    val place = d.drive.place ?: store.guide(d.guideId)?.title ?: "지난번 그 길"
    sheets.choose("어디쯤에서 들을 말인가요?", "다음에 ‘$place’에 갈 때 그 자리에서 들려줄게요.", TIP_PLACES.map { it.first }) { where ->
        val presets = listOf("여기서 미리 차선 바꾸기", "여기는 천천히", "주차장 입구 좁음", "여기 신호가 짧아")
        sheets.choose("다음의 나에게", null, presets + "직접 쓰기") { i ->
            fun save(t: String) {
                val p = TIP_PLACES[where].second
                val pt = Paths.at(d.drive.path, p)
                val old = store.selfGuide(d.driver, place)
                val tips = (old?.tips.orEmpty() + Tip(p, t, pt?.lat, pt?.lng)).sortedBy { it.p }
                store.addGuide(Guide(old?.id ?: "self_${d.driver}_${place.hashCode()}", d.driver, place, d.drive.placeLat ?: old?.lat, d.drive.placeLng ?: old?.lng,
                    d.drive, tips, (old?.times ?: 0) + 1, kind = "나"))
                toast("다음에 ‘$place’에 갈 때 그 자리에서 들려줄게요.")
                render()
            }
            if (i == presets.size) sheets.input("다음의 나에게", "예: 두 번째 신호에서 우회전") { save(it) } else save(presets[i])
        }
    }
}
