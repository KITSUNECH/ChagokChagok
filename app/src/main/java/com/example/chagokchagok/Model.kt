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
    val cruise: Boolean = false   // 주행 보조(크루즈)를 켜고 달렸는지
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
    val sample: Boolean = false
)

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
    val guideId: String?
)

object Evt {
    const val SIGNAL = "signal"   // 방향지시등
    const val BRAKE = "brake"     // 브레이크
    const val WIPER = "wiper"     // 와이퍼
    const val GEAR = "gear"       // 기어
    const val LIGHT = "light"     // 전조등
    const val LOST = "lost"       // 경로 재탐색
    const val CRUISE = "cruise"   // 주행 보조(크루즈)
}

/** 앱의 모든 기록을 읽고 쓰는 곳. */
class Store(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("chagok", Context.MODE_PRIVATE)

    val drivers = listOf("아빠", "나")

    var driver: String
        get() = prefs.getString("driver", "나") ?: "나"
        set(v) = prefs.edit().putString("driver", v).apply()

    fun guides(): List<Guide> = readArray("guides").map { guideOf(it) }
    fun diaries(): List<Diary> = readArray("diaries").map { diaryOf(it) }

    fun addGuide(g: Guide) = writeArray("guides", guides().filter { it.id != g.id } + g) { json(it) }
    fun addDiary(d: Diary) = writeArray("diaries", diaries() + d) { json(it) }

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
        put("parkMoves", d.parkMoves); put("lostWay", d.lostWay); put("longestSignalSec", d.longestSignalSec); put("cruise", d.cruise)
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
            o.getInt("parkMoves"), o.getInt("lostWay"), o.getInt("longestSignalSec"), o.optBoolean("cruise", false)
        )
    }

    private fun json(g: Guide) = JSONObject().apply {
        put("id", g.id); put("owner", g.owner); put("title", g.title)
        if (g.lat != null && g.lng != null) { put("lat", g.lat); put("lng", g.lng) }
        put("drive", json(g.drive)); put("times", g.times); put("sample", g.sample)
        put("tips", JSONArray().apply { g.tips.forEach { put(JSONObject().put("p", it.p.toDouble()).put("text", it.text)) } })
    }

    private fun guideOf(o: JSONObject): Guide {
        val a = o.getJSONArray("tips")
        val tips = (0 until a.length()).map { Tip(a.getJSONObject(it).getDouble("p").toFloat(), a.getJSONObject(it).getString("text")) }
        return Guide(
            o.getString("id"), o.getString("owner"), o.getString("title"),
            if (o.has("lat")) o.getDouble("lat") else null, if (o.has("lng")) o.getDouble("lng") else null,
            driveOf(o.getJSONObject("drive")), tips, o.optInt("times", 1), o.optBoolean("sample", false)
        )
    }

    private fun json(d: Diary) = JSONObject().apply {
        put("id", d.id); put("driver", d.driver); put("date", d.date); put("title", d.title); put("text", d.text)
        put("highlight", d.highlight); if (d.stamp != null) put("stamp", d.stamp)
        put("drive", json(d.drive)); if (d.guideId != null) put("guideId", d.guideId)
    }

    private fun diaryOf(o: JSONObject) = Diary(
        o.getString("id"), o.getString("driver"), o.getLong("date"), o.getString("title"), o.getString("text"),
        o.optString("highlight", ""), if (o.has("stamp")) o.getString("stamp") else null,
        driveOf(o.getJSONObject("drive")), if (o.has("guideId")) o.getString("guideId") else null
    )

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
