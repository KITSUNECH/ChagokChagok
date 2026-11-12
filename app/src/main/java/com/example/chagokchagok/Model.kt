// 차곡車곡 – 데이터 모델과 저장소
// 주행 한 번(Drive), 그 주행에 한마디를 얹은 교본(Guide), 차가 쓴 일기(Diary)를 정의하고
// 앱 안의 저장소(SharedPreferences)에 JSON으로 보관한다.
package com.example.chagokchagok

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** 주행 중 일어난 일 하나. p 는 그 주행에서의 진행 정도(0~1)다. */
data class Ev(val p: Float, val type: String, val value: String)

/** 가족이 길 위의 한 지점에 남긴 한마디. */
data class Tip(val p: Float, val text: String)

/** 주행 한 번의 요약. 일기와 교본이 모두 이것을 재료로 쓴다. */
data class Drive(
    val driver: String,
    val date: Long,
    val distKm: Float,
    val minutes: Int,
    val seconds: Int,
    val events: List<Ev>,
    val signals: Int,
    val brakes: Int,
    val rained: Boolean,
    val night: Boolean,
    val parkMoves: Int,
    val lostWay: Int,
    val longestSignalSec: Int,
    val cruise: Boolean = false,  // 주행 보조(크루즈)를 켜고 달렸는지
    val passenger: Boolean = false, // 운전석 말고 다른 자리에 누가 탔는지
    val turns: Int = 0,           // 내비가 안내한 좌·우회전 횟수
    val turnsSignaled: Int = 0,   // 그중 돌기 전에 깜빡이를 켠 횟수
    val calm: Int = 0,            // 숨 고르기를 누른 횟수
    val place: String? = null,    // 내비 목적지 이름 (교본 없이 간 길)
    val lamps: List<String> = emptyList(), // 달리는 동안 켜진 표시등 종류
    val placeLat: Double? = null,  // 목적지 좌표 (다시 가기, 교본으로 남기기에 쓴다)
    val placeLng: Double? = null
)

/** 교본: 누군가의 주행 한 번 + 목적지 + 한마디. */
data class Guide(
    val id: String,
    val owner: String,
    val title: String,
    val lat: Double?,
    val lng: Double?,
    val drive: Drive,
    val tips: List<Tip>,
    val times: Int,
    val sample: Boolean = false,
    /** "길": 어디까지 가는 길. "연습": 주차장·동네에서 기본기 (먼저 면허 딴 친구의 초보 시절 같은 것) */
    val kind: String = "길"
)

/** 차를 넘겨받는 사람에게 전하는 것. fuel 은 남은 연료나 배터리(%). */
data class Handover(val from: String, val date: Long, val fuel: Int?, val washerLow: Boolean, val note: String)

const val GRADUATE = 3

/** 일기 한 편. guideId 가 있으면 누군가의 교본을 따라 달린 날이다. */
data class Diary(
    val id: String,
    val driver: String,
    val date: Long,
    val title: String,
    val text: String,
    val highlight: String,
    val stamp: String?,
    val drive: Drive,
    val guideId: String?,
    val ai: Boolean = false   // 생성형 AI가 문장을 다듬었는지
)

object Evt {
    const val SIGNAL = "signal"   // 방향지시등
    const val BRAKE = "brake"     // 브레이크
    const val WIPER = "wiper"     // 와이퍼
    const val GEAR = "gear"       // 기어
    const val LIGHT = "light"     // 전조등
    const val LOST = "lost"       // 경로 재탐색
    const val CRUISE = "cruise"   // 주행 보조(크루즈)
    const val SEAT = "seat"       // 동승자 탑승
    const val TURN = "turn"       // 내비가 안내한 회전을 지남 (값: signaled / missed)
    const val CALM = "calm"       // 숨 고르기
    const val SAY = "say"         // 옆자리 한마디 (값: 그 말)
    const val LAMP = "lamp"       // 표시등 (값: 종류:on/off)
    const val NAVI = "navi"       // 내비 안내가 바뀜 (기록하지 않고 화면만 고친다)
}

/** 앱의 모든 기록을 읽고 쓰는 곳. */
class Store(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("chagok", Context.MODE_PRIVATE)

    /** 이 차를 함께 쓰는 사람들. 가족이나 친구를 더할 수 있다. */
    val drivers: List<String>
        get() = (prefs.getString("drivers", null) ?: "아빠|나").split("|").filter { it.isNotBlank() }
    fun addDriver(name: String) { if (name !in drivers) prefs.edit().putString("drivers", (drivers + name).joinToString("|")).apply() }

    /** 운전자가 어떤 사람인지: 초보, 오랜만, 새 차, 익숙. 고른 것에 따라 인사와 권하는 순서가 달라진다. */
    fun typeOf(name: String) = prefs.getString("type_$name", if (name == "나") "초보" else "익숙") ?: "익숙"
    fun setType(name: String, type: String) = prefs.edit().putString("type_$name", type).apply()

    /** 가족인지 친구인지. 친구의 교본은 주로 연습용이다. */
    fun relation(name: String) = prefs.getString("rel_$name", if (name == "나" || name in listOf("아빠", "엄마")) "가족" else "친구") ?: "친구"
    fun setRelation(name: String, rel: String) = prefs.edit().putString("rel_$name", rel).apply()

    /** 내 기록을 다른 사람에게 보여 줄지. 기본은 보여 주지 않는다. */
    fun shares(name: String) = prefs.getBoolean("share_$name", false)
    fun setShares(name: String, on: Boolean) = prefs.edit().putBoolean("share_$name", on).apply()

    /** 함께 차곡에 올린 일기와 그때 남긴 한 줄. */
    fun posted(diaryId: String): String? = prefs.getString("post_$diaryId", null)
    fun post(diaryId: String, caption: String) = prefs.edit().putString("post_$diaryId", caption).apply()
    /** 응원(하트)을 누른 사람들. 숫자로 줄 세우지 않고 누가 응원했는지만 보여 준다. */
    fun hearts(id: String): Set<String> = prefs.getStringSet("heart_$id", emptySet()) ?: emptySet()
    fun toggleHeart(id: String, name: String) { val h = hearts(id).toMutableSet(); if (!h.remove(name)) h.add(name); prefs.edit().putStringSet("heart_$id", h).apply() }
    /** 일기를 쓴 곳의 실제 날씨 (예: "맑음 12°"). */
    fun weather(diaryId: String): String? = prefs.getString("wx_$diaryId", null)
    fun setWeather(diaryId: String, w: String) = prefs.edit().putString("wx_$diaryId", w).apply()

    /** 일기에 내가 남긴 한 줄 (기분). */
    fun feeling(diaryId: String): String? = prefs.getString("feel_$diaryId", null)
    fun setFeeling(diaryId: String, f: String) = prefs.edit().putString("feel_$diaryId", f).apply()
    /** 처음 탈 때 운전자 유형을 물어봤는지. */
    fun asked(name: String) = prefs.contains("type_$name")


    /** 이 운전자의 운전석 앞뒤 위치. 다른 사람이 탄 뒤에 의자가 옮겨졌는지 본다. */
    fun seat(name: String): Int? = if (prefs.contains("seat_$name")) prefs.getInt("seat_$name", 0) else null
    fun setSeat(name: String, pos: Int) = prefs.edit().putInt("seat_$name", pos).apply()

    /** 이 운전자가 교본 없이 다녀온 곳들. */
    fun places(name: String) = diariesOf(name).mapNotNull { if (it.guideId == null) it.drive.place else null }.distinct()

    /** 일기 말투: 담백하게, 다정하게. */
    fun tone(name: String) = prefs.getString("tone_$name", "담백하게") ?: "담백하게"
    fun setTone(name: String, tone: String) = prefs.edit().putString("tone_$name", tone).apply()

    /** 마지막에 탄 사람이 다음 사람에게 남기는 차 상태와 한마디. */
    fun handover(): Handover? = prefs.getString("handover", null)?.let {
        val o = JSONObject(it)
        Handover(o.getString("from"), o.getLong("date"), if (o.has("fuel")) o.getInt("fuel") else null, o.optBoolean("washerLow", false), o.optString("note", ""))
    }
    fun setHandover(h: Handover) = prefs.edit().putString("handover", JSONObject().apply {
        put("from", h.from); put("date", h.date); if (h.fuel != null) put("fuel", h.fuel); put("washerLow", h.washerLow); put("note", h.note)
    }.toString()).apply()

    /** 이 운전자가 이 교본을 몇 번 따라 달렸는지. 세 번이면 졸업이다. */
    fun timesFollowed(driver: String, guideId: String) = diaries().count { it.driver == driver && it.guideId == guideId }
    fun graduated(driver: String, guideId: String) = timesFollowed(driver, guideId) >= GRADUATE
    fun totalKm(driver: String) = diariesOf(driver).sumOf { it.drive.distKm.toDouble() }.toFloat()

    var driver: String
        get() = prefs.getString("driver", "나") ?: "나"
        set(v) = prefs.edit().putString("driver", v).apply()

    fun guides(): List<Guide> = readArray("guides").map { guideOf(it) }
    fun diaries(): List<Diary> = readArray("diaries").map { diaryOf(it) }

    fun addGuide(g: Guide) = writeArray("guides", guides().filter { it.id != g.id } + g) { json(it) }
    fun addDiary(d: Diary) = writeArray("diaries", diaries().filter { it.id != d.id } + d) { json(it) }

    /** 지금 운전자가 받아 볼 수 있는 교본: 다른 가족이 남긴 길. */
    fun guidesFor(driver: String) = guides().filter { it.owner != driver }
    fun diariesOf(driver: String) = diaries().filter { it.driver == driver }
    fun guide(id: String?) = guides().firstOrNull { it.id == id }

    /** 차와 친해지기를 마쳤는지. */
    fun prepDone(driver: String) = prefs.getBoolean("prep_$driver", false)
    fun setPrepDone(driver: String) = prefs.edit().putBoolean("prep_$driver", true).apply()

    /** 가족에게 보낸 소식. 받는 사람의 홈에 나타난다. (한 차 안에서만 전달된다) */
    fun news(to: String): List<String> = JSONArray(prefs.getString("news_$to", "[]")).let { a -> (0 until a.length()).map { a.getString(it) } }
    fun addNews(to: String, text: String) = prefs.edit().putString("news_$to", JSONArray(news(to) + text).toString()).apply()
    fun clearNews(to: String) = prefs.edit().remove("news_$to").apply()
    fun newsSent(diaryId: String) = prefs.getBoolean("sent_$diaryId", false)
    fun setNewsSent(diaryId: String) = prefs.edit().putBoolean("sent_$diaryId", true).apply()

    fun clearAll() = prefs.edit().clear().apply()

    // ── JSON 변환 ──
    private fun readArray(key: String): List<JSONObject> {
        val a = JSONArray(prefs.getString(key, "[]"))
        return (0 until a.length()).map { a.getJSONObject(it) }
    }

    private fun <T> writeArray(key: String, items: List<T>, f: (T) -> JSONObject) {
        val a = JSONArray()
        items.forEach { a.put(f(it)) }
        prefs.edit().putString(key, a.toString()).apply()
    }

    private fun json(d: Drive) = JSONObject().apply {
        put("driver", d.driver); put("date", d.date); put("distKm", d.distKm.toDouble()); put("minutes", d.minutes); put("seconds", d.seconds)
        put("signals", d.signals); put("brakes", d.brakes); put("rained", d.rained); put("night", d.night)
        put("parkMoves", d.parkMoves); put("lostWay", d.lostWay); put("longestSignalSec", d.longestSignalSec); put("cruise", d.cruise); put("passenger", d.passenger); put("turns", d.turns); put("turnsSignaled", d.turnsSignaled); put("calm", d.calm); if (d.place != null) put("place", d.place); put("lamps", d.lamps.joinToString("|")); if (d.placeLat != null && d.placeLng != null) { put("placeLat", d.placeLat); put("placeLng", d.placeLng) }
        put("events", JSONArray().apply {
            d.events.forEach { put(JSONObject().put("p", it.p.toDouble()).put("t", it.type).put("v", it.value)) }
        })
    }

    private fun driveOf(o: JSONObject): Drive {
        val a = o.getJSONArray("events")
        val evs = (0 until a.length()).map {
            val e = a.getJSONObject(it)
            Ev(e.getDouble("p").toFloat(), e.getString("t"), e.getString("v"))
        }
        return Drive(
            o.getString("driver"), o.getLong("date"), o.getDouble("distKm").toFloat(), o.getInt("minutes"), o.optInt("seconds", o.getInt("minutes") * 60), evs,
            o.getInt("signals"), o.getInt("brakes"), o.getBoolean("rained"), o.getBoolean("night"),
            o.getInt("parkMoves"), o.getInt("lostWay"), o.getInt("longestSignalSec"), o.optBoolean("cruise", false), o.optBoolean("passenger", false), o.optInt("turns", 0), o.optInt("turnsSignaled", 0), o.optInt("calm", 0), if (o.has("place")) o.getString("place") else null, o.optString("lamps", "").split("|").filter { it.isNotEmpty() }, if (o.has("placeLat")) o.getDouble("placeLat") else null, if (o.has("placeLng")) o.getDouble("placeLng") else null
        )
    }

    private fun json(g: Guide) = JSONObject().apply {
        put("id", g.id); put("owner", g.owner); put("title", g.title)
        if (g.lat != null && g.lng != null) { put("lat", g.lat); put("lng", g.lng) }
        put("drive", json(g.drive)); put("times", g.times); put("sample", g.sample); put("kind", g.kind)
        put("tips", JSONArray().apply { g.tips.forEach { put(JSONObject().put("p", it.p.toDouble()).put("text", it.text)) } })
    }

    private fun guideOf(o: JSONObject): Guide {
        val a = o.getJSONArray("tips")
        val tips = (0 until a.length()).map { Tip(a.getJSONObject(it).getDouble("p").toFloat(), a.getJSONObject(it).getString("text")) }
        return Guide(
            o.getString("id"), o.getString("owner"), o.getString("title"),
            if (o.has("lat")) o.getDouble("lat") else null, if (o.has("lng")) o.getDouble("lng") else null,
            driveOf(o.getJSONObject("drive")), tips, o.optInt("times", 1), o.optBoolean("sample", false), o.optString("kind", "길")
        )
    }

    private fun json(d: Diary) = JSONObject().apply {
        put("id", d.id); put("driver", d.driver); put("date", d.date); put("title", d.title); put("text", d.text)
        put("highlight", d.highlight); if (d.stamp != null) put("stamp", d.stamp)
        put("drive", json(d.drive)); if (d.guideId != null) put("guideId", d.guideId); put("ai", d.ai)
    }

    private fun diaryOf(o: JSONObject) = Diary(
        o.getString("id"), o.getString("driver"), o.getLong("date"), o.getString("title"), o.getString("text"),
        o.optString("highlight", ""), if (o.has("stamp")) o.getString("stamp") else null,
        driveOf(o.getJSONObject("drive")), if (o.has("guideId")) o.getString("guideId") else null, o.optBoolean("ai", false)
    )

    /** 시연용 예시 친구와 그 친구의 초보 시절 연습. 화면에 "예시"라고 표시된다. */
    fun addSampleFriend() {
        addDriver("민수"); setRelation("민수", "친구"); setType("민수", "익숙")
        val evs = listOf(Ev(.30f, Evt.SIGNAL, "LEFT"), Ev(.35f, Evt.BRAKE, "on"), Ev(.62f, Evt.BRAKE, "on"), Ev(.80f, Evt.BRAKE, "on"))
        val drive = Drive("민수", System.currentTimeMillis() - 200 * 86400000L, 0.6f, 9, 540, evs, 1, 3, false, false, 4, 0, 3)
        addGuide(Guide("sample_friend", "민수", "주차장에서 후진 주차", null, null, drive,
            listOf(Tip(.2f, "나도 처음엔 네 번 넘게 오갔어. 천천히 해도 돼."), Tip(.7f, "뒷바퀴가 주차선에 닿을 때 핸들을 풀면 편해.")),
            12, sample = true, kind = "연습"))
        // 친구의 주말 드라이브: 다녀온 곳을 함께 차곡에 올렸다. 나도 가보기를 누르면 내 도전이 된다.
        val trip = Drive("민수", System.currentTimeMillis() - 2 * 86400000L, 38.5f, 62, 3720,
            listOf(Ev(.2f, Evt.SIGNAL, "RIGHT"), Ev(.55f, Evt.BRAKE, "on"), Ev(.9f, Evt.SIGNAL, "LEFT"), Ev(.95f, Evt.BRAKE, "on")), 2, 2, false, false, 1, 0, 4,
            place = "바닷가 카페")
        addGuide(Guide("sample_friend_trip", "민수", "민수의 주말 드라이브", 37.4473, 126.3729, trip,
            listOf(Tip(.95f, "주차장이 넓고 풍경도 좋았어.")), 1, sample = true, kind = "추천"))
        if (news("나").none { it.startsWith("민수\t") }) addNews("나", "민수\t민수의 응원: “면허 축하해! 처음엔 다 무서워. 주차장부터 같이 하자.”")
    }

    /** 시연용 예시 교본. 화면에 "예시"라고 표시된다. 좌표는 Pleos 문서의 목적지 예시 값이다. */
    fun addSampleGuide() {
        val evs = listOf(
            Ev(.20f, Evt.SIGNAL, "RIGHT"), Ev(.24f, Evt.BRAKE, "on"), Ev(.52f, Evt.SIGNAL, "LEFT"),
            Ev(.56f, Evt.BRAKE, "on"), Ev(.80f, Evt.SIGNAL, "RIGHT"), Ev(.90f, Evt.BRAKE, "on")
        )
        val drive = Drive("아빠", System.currentTimeMillis() - 3 * 86400000L, 8.2f, 24, 1440, evs, 3, 3, false, false, 0, 0, 6)
        addGuide(
            Guide(
                "sample", "아빠", "할머니 댁 가는 길", 37.48544722, 127.03636666, drive,
                listOf(Tip(.46f, "큰 사거리 지나면 바로 우회전이야. 미리 오른쪽 차로로 가 있어."), Tip(.93f, "주차장 입구가 좁아. 천천히 들어가도 아무도 뭐라 안 해.")),
                7, sample = true
            )
        )
    }
}
