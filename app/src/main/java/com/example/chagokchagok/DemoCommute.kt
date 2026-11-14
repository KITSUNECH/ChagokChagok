// 차곡차곡 – 시연: 회사 가는 길, 네 번째 출근 (공모전판에만, 모두 "예시"·"시연용"으로 표시)
// 2분 안에 앱이 하는 일 네 단계를 끝까지 보여 준다.
//  1. 지난 출근 세 번을 예시 기록으로 넣는다: 합류 구간에서 깜빡이를 켜고 15→13→12초 기다렸고, 돌기 전 깜빡이 2/4…
//     세 번째 출근 뒤에 "곧 합류 구간이야. 미리 차선을 옮겨 두자."를 남겨 두었다 (합류 400m 앞).
//  2. 출발 전 화면: "이번에 참고할 지난번 한마디".
//  3. 모의 주행: 경로를 따라 움직이며, 그 한마디 근처에서 소리로 들려주고, 돌기 전에 깜빡이, 합류에서 6초 만에 차선 변경.
//  4. 주차(P)하면: 차가 본 변화(늘었어요: 차선 변경 전 기다림 13초→6초, 돌기 전 깜빡이…) + 내 느낌 → 합친 평가, "이 길의 나" 그래프.
// 경로는 tools/예시길.gpx 와 같아서, 에뮬레이터에서 그 GPX를 재생하며 실제로 달려도 같은 장면이 나온다.
package com.example.chagokchagok

internal object DemoCommute {
    const val PLACE = "회사"
    const val NOTE = "곧 합류 구간이야. 미리 차선을 옮겨 두자."
    private const val DAY = 86400000L

    private fun ids(me: String) = (1..3).map { "demo_commute_${me}_$it" }
    fun loaded(a: MainActivity) = a.store.diaries().any { it.id == ids(a.store.driver).first() }

    /** 예시 기록 세 개와 남겨 둔 한마디를 넣는다 */
    fun load(a: MainActivity) {
        val store = a.store; val me = store.driver
        val full = Paths.of(SampleRoute.points)
        val end = SampleRoute.points.last()
        val lane = Paths.at(full, .55f)!!
        val lead = Paths.before(full, lane.lat, lane.lng, 400)
        val cal = java.util.Calendar.getInstance()
        data class Spec(val daysAgo: Int, val wait: Float, val signaled: Int, val brakes: Int, val minutes: Int, val eco: Float, val lost: Int, val park: Int, val rain: Boolean)
        val specs = listOf(Spec(9, 15f, 2, 22, 27, 12.6f, 1, 3, false), Spec(6, 13f, 2, 20, 26, 12.9f, 0, 2, true), Spec(3, 12f, 3, 19, 26, 13.2f, 0, 2, false))
        val made = mutableListOf<Diary>()
        specs.forEachIndexed { i, s ->
            cal.timeInMillis = System.currentTimeMillis() - s.daysAgo * DAY
            cal.set(java.util.Calendar.HOUR_OF_DAY, 8); cal.set(java.util.Calendar.MINUTE, 20)
            val evs = listOf(
                Ev(.20f, Evt.SIGNAL, "RIGHT"), Ev(.24f, Evt.BRAKE, "on"), Ev(.52f, Evt.BRAKE, "on"), Ev(.53f, Evt.BRAKE, "on"),
                Ev(.55f, Evt.LANE, LaneMove("LEFT", s.wait, true, true, s.rain, false).encode(), lane.lat, lane.lng), Ev(.57f, Evt.BRAKE, "on"),
                Ev(.80f, Evt.SIGNAL, "RIGHT"), Ev(.90f, Evt.BRAKE, "on"),
            )
            val drive = Drive(me, cal.timeInMillis, 11.2f, s.minutes, s.minutes * 60, evs, 3, s.brakes, s.rain, false, s.park, s.lost, 6,
                turns = 4, turnsSignaled = s.signaled, place = PLACE, placeLat = end.first, placeLng = end.second, path = full, economy = s.eco)
            val w = Writer.write(drive, made.toList(), null, if (i == 2) 0 else 0)
            val d = w.copy(id = ids(me)[i], title = "$PLACE · ${i + 1}번째")
            store.addDiary(d); store.setFlag("sample_d_${d.id}"); made.add(d)
            // 예시 기록은 돌아볼 것을 남기지 않는다 (홈의 "돌아볼 길"에 쌓이지 않게)
            TipSuggest.moments(drive).forEach { store.skipMoment(d.id, it.why) }
        }
        store.setAnswer(made[1].id, Q_VS_LAST, "비슷했어요")
        store.setAnswer(made[2].id, Q_VS_LAST, "더 편했어요")
        // 세 번째 출근 뒤에 남겨 둔 한마디 (합류 400m 앞)
        store.addGuide(Guide("self_${me}_${PLACE.hashCode()}", me, PLACE, end.first, end.second, made[2].drive,
            listOf(Tip(.5f, NOTE, lead?.lat, lead?.lng)), 3, sample = true, kind = "나"))
    }

    /** 시연을 시작한다: 예시가 없으면 넣고, 위치 동의를 확인한 뒤 출발 전 화면으로 */
    fun start(a: MainActivity) {
        if (!loaded(a)) load(a)
        val me = a.store.driver
        fun go() {
            a.trip.demoAuto = true
            val last = a.store.diariesOf(me).last { it.drive.place == PLACE && !it.drive.sim }
            a.goAgain(PLACE, last)
            a.toast("시연: 지난 출근 세 번은 예시 기록이에요. ‘모의 주행 (시연용)’을 누르세요.")
        }
        if (a.store.locationConsent(me) == null) { a.askLocationConsent(me); a.toast("위치에 동의하면 한마디를 남긴 곳 근처에서 들려줘요.") }
        go()
    }

    /** 시연 기록을 모두 지운다 (예시 세 개, 남겨 둔 한마디, 시연으로 달린 모의 주행) */
    fun clear(a: MainActivity) {
        val store = a.store; val me = store.driver
        ids(me).forEach { store.removeDiary(it) }
        store.diariesOf(me).filter { it.drive.place == PLACE && it.drive.sim }.forEach { store.removeDiary(it.id) }
        store.removeGuide("self_${me}_${PLACE.hashCode()}")
        a.trip.demoAuto = false
    }
}
