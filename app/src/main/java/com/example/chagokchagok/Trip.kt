// 차곡車곡 – 주행 한 번
// 기어 D/R에 들어가면 시작하고 P에 놓으면 끝난다. 그동안 차량 신호와 위치를 모아 두었다가 일기를 쓴다.
// 화면 코드와 떨어져 있어서, 화면이 바뀌거나 창 크기가 바뀌어도 기록은 이어진다.
//
// 한마디는 "남긴 자리"의 좌표에 150m 안으로 다가가고, 진행 방향이 그쪽을 향할 때만 들려준다.
// 좌표가 없는 한마디나 위치를 모를 때는 달리는 중에 들려주지 않는다 (출발 전에 미리 듣게 한다).
package com.example.chagokchagok

import android.os.Handler
import android.os.Looper

interface TripHost {
    fun speak(text: String)
    fun say(text: String)
    fun chime()
    fun tripStarted()
    fun tripChanged()
    fun tripEnded(d: Diary)
    fun diaryUpdated(d: Diary)
    fun lampsChanged()
}

class Trip(private val store: Store, private val car: Car, private val host: TripHost) {
    val recorder = Recorder()
    val active get() = recorder.active

    /** 따라 달리는 길. 출발 전에 정한다. */
    var guide: Guide? = null
    var routeSent = false
    var sim = false; private set
    var prompt: String? = null; private set          // 지금 할 한 가지 (예: 오른쪽 깜빡이)
    var currentTip: String? = null; private set      // 방금 들려준 한마디
    var nextTipMeters: Int? = null; private set       // 다음 한마디까지 거리
    val heard = mutableSetOf<Int>()
    var arrived = false; private set
    var withPassenger = false; private set
    var aiStatus = if (AiDiary.enabled) "대기 중" else "키 없음 (규칙으로 씀)"

    private var praised = 0
    private val spoken = mutableSetOf<String>()
    private var support = 3          // 1 연습(자주) · 2 받은 길(필요할 때) · 3 혼자(안전만)
    private val path = mutableListOf<Pair<Double, Double>>()
    private val main = Handler(Looper.getMainLooper())
    private var simStep = 0

    // ───────── 앱이 꺼져도 이어 쓰기 ─────────
    private var lastSave = 0L

    /** 지금까지의 기록을 저장한다 (3초에 한 번까지, force 면 바로) */
    fun save(force: Boolean = false) {
        if (!active || sim) return   // 모의 주행은 이어 쓰지 않는다
        val now = System.currentTimeMillis(); if (!force && now - lastSave < 3000) return
        lastSave = now
        store.tripSnapshot = org.json.JSONObject().apply {
            put("rec", recorder.snapshot()); put("driver", store.driver); put("saved", now)
            guide?.let { put("guide", it.id) }; put("sim", false); put("arrived", arrived)
            put("heard", org.json.JSONArray(heard.toList())); put("path", org.json.JSONArray().apply { path.forEach { put(org.json.JSONArray().put(it.first).put(it.second)) } })
        }.toString()
    }

    /** 3시간 안에 끊긴 기록이 있으면 이어 간다. 이어 갔으면 true */
    fun resume(findGuide: (String) -> Guide?): Boolean {
        val s = store.tripSnapshot ?: return false
        return try {
            val o = org.json.JSONObject(s)
            if (System.currentTimeMillis() - o.getLong("saved") > 3 * 3600_000L) { store.tripSnapshot = null; return false }
            store.driver = o.getString("driver")
            guide = if (o.has("guide")) findGuide(o.getString("guide")) else null
            recorder.restore(o.getJSONObject("rec"))
            arrived = o.optBoolean("arrived"); heard.clear(); o.optJSONArray("heard")?.let { a -> for (i in 0 until a.length()) heard.add(a.getInt(i)) }
            path.clear(); o.optJSONArray("path")?.let { a -> for (i in 0 until a.length()) a.getJSONArray(i).let { path.add(it.getDouble(0) to it.getDouble(1)) } }
            support = when (guide?.kind) { "연습" -> 1; "길" -> 2; else -> 3 }
            main.removeCallbacks(tick); main.postDelayed(tick, 1000)
            true
        } catch (e: Throwable) { store.tripSnapshot = null; false }
    }

    private val coach = TurnCoach().apply { onPassed = { ok -> recorder.add(Evt.TURN, if (ok) "signaled" else "missed"); if (ok) praise() } }

    // ───────── 차에서 온 신호 ─────────

    fun onCar(type: String, value: String) {
        if (type != Evt.NAVI) recorder.add(type, value)
        if (active && (type == Evt.NAVI || type == Evt.SIGNAL)) {
            val side = coach.update(car.turn, car.turnMeters, if (type == Evt.SIGNAL) value else car.signal)
            if (side != null && support <= 2) { prompt = if (side == "LEFT") "왼쪽 깜빡이" else "오른쪽 깜빡이"; host.speak("지금 $prompt") }
            else if (prompt != null && ((type == Evt.SIGNAL && value != "OFF") || car.turn == null)) { if (type == Evt.SIGNAL) host.chime(); prompt = null }
        }
        if (active && type == Evt.LOST) host.say("괜찮아요. 내비가 경로를 다시 찾았어요.")
        if (type == Evt.SEAT && value == "on") withPassenger = true
        if (type == Evt.LAMP) onLamp(value)
        if (active) roadside(type, value)
        if (type == Evt.GEAR) {
            if ((value == "D" || value == "R") && !active) start()
            else if (value == "P" && active) end()
        }
        if (active) { host.tripChanged(); save() }
    }

    fun onArrived() {
        if (!active || arrived) return
        arrived = true
        host.say("목적지에 도착했어요. 기어를 P에 놓으면 일기를 씁니다.")
    }

    fun onDistance(km: Float) { if (!sim) recorder.distance(km) }

    /** 위치가 바뀌었다 (위치 SDK, 내비, 또는 모의 주행) */
    fun onLocation(lat: Double, lng: Double, heading: Float?) {
        if (!active) return
        val last = path.lastOrNull()
        if (last == null || Places.meters(last.first, last.second, lat, lng) >= 30) { path.add(lat to lng); if (!sim) save() }
        tipsNear(lat, lng, heading)
    }

    private fun tipsNear(lat: Double, lng: Double, heading: Float?) {
        val g = guide ?: return
        if (!store.locationOn) return
        var nearest: Double? = null
        g.tips.forEachIndexed { i, t ->
            if (i in heard || !t.placed) return@forEachIndexed
            val d = Places.meters(lat, lng, t.lat!!, t.lng!!)
            if (nearest == null || d < nearest!!) nearest = d
            if (d <= 150) {
                // 반대 방향으로 지나가는 중이면 들려주지 않는다 (아주 가까우면 방향과 상관없이)
                val toward = heading == null || d < 50 || angle(heading.toDouble(), Places.bearing(lat, lng, t.lat, t.lng)) < 100
                if (toward) play(i, t)
            }
        }
        nextTipMeters = nearest?.toInt()
    }

    private fun angle(a: Double, b: Double) = Math.abs(((a - b + 540) % 360) - 180)

    private fun play(i: Int, t: Tip) {
        heard.add(i); currentTip = t.text
        host.chime()
        host.speak("${Writer.poss(guide!!.owner)} 한마디. ${t.text}")
        host.tripChanged()
    }

    // ───────── 말 ─────────

    /** 칭찬은 그 자리에서. 소리 하나와 짧은 말. 실수는 말하지 않고 일기에만 남긴다. */
    private fun praise() {
        host.chime()
        if (support <= 2 && praised < 2) { praised++; host.speak(if (praised == 1) "미리 켰어요. 좋아요." else "이번에도 미리 켰어요.") }
    }

    /** 지나는 자리에 맞는 규칙 한 줄. 같은 말은 한 번만. */
    private fun roadside(type: String, value: String) {
        fun once(key: String, line: String) { if (spoken.add(key)) host.say(line) }
        if (type == Evt.WIPER && value == "on" && support <= 2) once("rain", "비가 오면 제한속도보다 20% 줄여 달려요.")
        if (type == Evt.NAVI && car.schoolZone && car.cameraMeters in 1..300) once("school", "곧 어린이 보호구역이에요. 시속 30 아래로 가요.")
        if (type == Evt.NAVI && value == "limit" && car.speedLimit in 1..30 && support <= 2) once("limit30", "여기는 제한속도 ${car.speedLimit}이에요.")
    }

    /** 표시등: 빨간 경고는 바로 말하고, 처음 써 보는 기능은 확인해 준다. */
    private fun onLamp(value: String) {
        val type = value.substringBefore(":"); val on = value.endsWith(":on")
        val lamp = Lamps.all[type]
        if (on && lamp != null) when {
            lamp.level == "빨강" -> host.say("${lamp.name} 경고등이 켜졌어요. " + if (active) "안전한 곳에 세워요." else "출발 전에 확인해요.")
            type in Lamps.firstUse && store.diariesOf(store.driver).none { type in it.drive.lamps } -> { host.chime(); if (support <= 2) host.speak("${lamp.name}, 켜졌어요.") }
        }
        host.lampsChanged()
    }

    // ───────── 시작과 끝 ─────────

    fun start() {
        recorder.start(car.odometerKm)
        recorder.add(Evt.GEAR, car.gear)
        if (car.wiper) recorder.add(Evt.WIPER, "on")
        if (car.lowBeam) recorder.add(Evt.LIGHT, "on")
        if (car.cruise) recorder.add(Evt.CRUISE, "on")
        if (car.passenger) recorder.add(Evt.SEAT, "on")
        heard.clear(); currentTip = null; coach.reset(); prompt = null; nextTipMeters = null
        praised = 0; spoken.clear(); withPassenger = car.passenger; arrived = false; path.clear()
        // 말하는 양: 받은 길의 종류가 기본, 처음 설정에서 고른 운전 경험으로 더하거나 줄인다 (초보는 회전 전 깜빡이까지, 익숙은 경고만)
        support = when (guide?.kind) { "연습" -> 1; "길", "나" -> 2; else -> 3 }.let { base ->
            when (store.typeOf(store.driver)) { "초보" -> minOf(base, 2); "익숙" -> 3; else -> base }
        }
        car.seatPos?.let { store.setSeat(store.driver, it) }
        host.tripStarted()
        val g = guide
        val placed = g?.tips?.count { it.placed } ?: 0
        host.say(when {
            g == null -> "출발합니다. 조용히 적고 있을게요."
            g.kind == "나" -> "지난번의 내가 남긴 길로 출발합니다. 그 자리에 가면 알려 드릴게요."
            placed > 0 && store.locationOn -> "${Writer.subj(g.owner)} 남긴 길로 출발합니다. 한마디를 남긴 자리에 가면 알려 드릴게요."
            else -> "${Writer.subj(g.owner)} 남긴 길로 출발합니다. 조용히 적고 있을게요."
        })
        // 받은 응원이 있으면 출발하고 조금 뒤에 대신 전해 준다
        store.news(store.driver).lastOrNull { it.contains("응원") }?.let { cheer ->
            main.postDelayed({ if (active) host.speak(cheer.substringAfter("\t").replace("“", "").replace("”", "")) }, 5000)
        }
        main.removeCallbacks(tick); main.postDelayed(tick, 1000)
        save(force = true)
    }

    private val tick = object : Runnable { override fun run() { if (active) { host.tripChanged(); main.postDelayed(this, 1000) } } }

    fun end() {
        if (!active) return
        val wasSim = sim
        if (sim) { sim = false; car.setTurn(null, 0, ""); car.setAhead(0, 0, 0) }
        main.removeCallbacks(tick)
        store.tripSnapshot = null
        val driver = store.driver
        // 보관함의 "다시 가기"는 남이 건넨 길이 아니라 내가 다녀온 곳이다. 혼자 간 길로 적는다.
        val again = guide?.takeIf { it.kind == "다시" }
        val g = guide?.takeIf { it.kind != "다시" }
        val route = Paths.of(path.toList())
        val drive = recorder.finish(driver).let { d ->
            val km = if (d.distKm < 0.05f && route.size > 1) Paths.lengthKm(route) else d.distKm
            val placed = when {
                again != null -> d.copy(place = again.title, placeLat = again.lat, placeLng = again.lng)
                g == null && !car.destName.isNullOrBlank() -> d.copy(place = car.destName, placeLat = car.destLat ?: route.lastOrNull()?.lat, placeLng = car.destLng ?: route.lastOrNull()?.lng)
                else -> d
            }
            val far = g != null && g.drive.distKm > 0.05f && km >= g.drive.distKm * 0.9f
            placed.copy(distKm = km, path = route, completed = g == null || arrived || far || wasSim, sim = wasSim)
        }
        car.refreshNavi(); car.refreshCondition(); car.resetPassenger()
        saveHandover(driver, "")
        main.postDelayed({ saveHandover(driver, store.handover()?.takeIf { it.from == driver }?.note ?: "") }, 1500) // 차 상태 값이 조금 늦게 온다
        val written = Writer.write(drive, store.diariesOf(driver), g, heard.size, store.tone(driver), seasoned = store.typeOf(driver) == "익숙")
        // 처음이라면(길잡이) 중 이번에 해낸 것이 있으면 일기에 적는다
        val courses = store.newlyDoneCourses(driver, drive)
        courses.forEach { store.addCourseDone(driver, it.id) }
        val d = if (courses.isEmpty()) written else written.copy(text = written.text + " 오늘 ‘${courses.joinToString("’, ‘") { it.title }}’를 해냈다.")
        store.addDiary(d)
        guide = null; routeSent = false
        host.tripEnded(d)
        host.say("주차했어요. 오늘 일기를 썼어요.")
        // 일기 머리의 날씨는 도착한 곳의 실제 날씨로 (설정에서 끌 수 있다)
        val end = route.lastOrNull()
        val la = car.lat ?: end?.lat; val ln = car.lng ?: end?.lng
        if (store.weatherOn && la != null && ln != null) Weather.at(la, ln) { f -> if (f != null) { store.setWeather(d.id, "${f.sky} ${f.tempC}°"); host.diaryUpdated(d) } }
        // 키가 있으면 같은 사실로 문장만 다듬는다. 실패하면 규칙으로 쓴 글을 그대로 둔다.
        if (AiDiary.enabled) AiDiary.polish(d, g, store.tone(driver), store.diariesOf(driver).dropLast(1), store.typeOf(driver)) { text, reason ->
            aiStatus = reason
            if (text != null) {
                val better = d.copy(text = text, highlight = if (d.highlight.isNotEmpty() && d.highlight in text) d.highlight else "", ai = true)
                store.addDiary(better); host.diaryUpdated(better)
            }
        }
    }

    /** 다음에 타는 사람을 위해 지금 차 상태를 남긴다. */
    fun saveHandover(driver: String, note: String) =
        store.setHandover(Handover(driver, System.currentTimeMillis(), car.energy, car.washerLow, note))

    /** 달리는 동안 숨 고르기: 기록하고, 내비에 편안한 길을 요청한다 */
    fun breathe() {
        recorder.add(Evt.CALM, "on")
        host.say(if (car.comfortable()) "괜찮아요. 내비에 편안한 길로 바꿔 달라고 요청했어요." else "괜찮아요. 천천히 가도 돼요.")
    }

    // ───────── 모의 주행 (시연용) ─────────
    // 에뮬레이터 내비는 차를 움직여 주지 않는다. 받은 길의 경로를 따라 위치와 거리를 흉내 낸다.
    // 깜빡이·브레이크 같은 차량 신호는 흉내 내지 않는다. 그건 가상 콘솔에서 실제로 들어온다.

    var simLat: Double? = null; private set
    var simLng: Double? = null; private set

    fun startSim() {
        val g = guide ?: return
        sim = true; simStep = 0
        if (!active) start()
        val base = car.odometerKm ?: 0f
        recorder.distance(base)
        val total = g.drive.distKm.coerceAtLeast(1f)
        val full = g.drive.path
        val turns = g.drive.events.filter { it.type == Evt.SIGNAL && (it.value == "LEFT" || it.value == "RIGHT") }
        val steps = 180      // 0.5초마다 한 걸음, 90초쯤
        main.postDelayed(object : Runnable {
            override fun run() {
                if (!sim || !active) return
                simStep++
                val at = simStep / steps.toFloat()
                recorder.distance(base + total * at)
                // 위치: 경로가 있으면 그 위를 지나간다
                if (full.size > 1) {
                    val here = Paths.at(full, at); val ahead = Paths.at(full, (at + .02f).coerceAtMost(1f))
                    if (here != null) {
                        simLat = here.lat; simLng = here.lng
                        val hd = if (ahead != null && ahead !== here) Places.bearing(here.lat, here.lng, ahead.lat, ahead.lng).toFloat() else null
                        onLocation(here.lat, here.lng, hd)
                    }
                } else g.tips.forEachIndexed { i, t -> if (i !in heard && at >= t.p) play(i, t) }   // 경로가 없는 길은 진행 정도로
                val next = turns.firstOrNull { it.p > at }
                val meters = if (next != null) ((next.p - at) * total * 1000).toInt() else 0
                if (next != null && meters <= 500) car.setTurn("TURN_" + next.value, meters, "") else car.setTurn(null, 0, "")
                val cam = ((0.66f - at) * total * 1000).toInt()
                car.setAhead(50, if (cam in 1..600) cam else 0, 50)
                if (at >= 1f) { sim = false; car.setTurn(null, 0, ""); car.setAhead(0, 0, 0); arrived = true; host.say("도착했어요. 기어를 P에 놓으면 일기를 씁니다."); host.tripChanged() }
                else main.postDelayed(this, 500)
            }
        }, 500)
    }
}
