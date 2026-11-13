// 차곡車곡 – 주행을 기록하고 일기를 쓰는 곳
// Recorder 는 주행 중 들어온 신호를 쌓아 두었다가 주행이 끝나면 Drive 로 정리한다.
// Writer 는 그 Drive 를 사실만으로 된 일기 문장으로 바꾼다. 점수는 매기지 않는다.
package com.example.chagokchagok

/**
 * 내비가 알려 준 회전과 깜빡이를 맞춰 본다.
 * 회전이 가까워졌는데 깜빡이가 꺼져 있으면 한 번 알려 주고, 그 회전을 지나면 미리 켰는지 적어 둔다.
 */
class TurnCoach {
    private var dir: String? = null
    private var last = 0
    private var signaled = false
    private var prompted = false

    /** 회전을 하나 지났을 때 불린다. true 면 미리 깜빡이를 켠 것이다. */
    var onPassed: (Boolean) -> Unit = {}

    fun reset() { dir = null; last = 0; signaled = false; prompted = false }

    /** 내비 안내나 깜빡이가 바뀔 때마다 부른다. 지금 깜빡이를 켜라고 알려 줄 때만 방향을 돌려준다. */
    fun update(turn: String?, meters: Int, signal: String): String? {
        val side = turn?.takeIf { it == "LEFT" || it == "RIGHT" }
        val near = side != null && meters in 1..NEAR
        // 가까이 다가왔던 회전이 사라지거나 다음 안내로 넘어갔으면 그 회전을 지난 것이다.
        if (dir != null && (!near || side != dir || meters > last + 50)) { onPassed(signaled); reset() }
        if (!near) return null
        if (dir == null) { dir = side; signaled = false; prompted = false }
        last = meters
        if (signal == side) signaled = true
        if (!signaled && !prompted && meters <= PROMPT) { prompted = true; return side }
        return null
    }

    companion object { const val NEAR = 200; const val PROMPT = 100 }
}

class Recorder {
    private data class Raw(val time: Long, val km: Float, val type: String, val value: String)

    private val raw = mutableListOf<Raw>()
    private var startTime = 0L
    private var startKm: Float? = null
    private var lastKm: Float? = null
    var active = false; private set
    val count get() = raw.size

    fun start(odometerKm: Float?) {
        raw.clear(); active = true
        startTime = System.currentTimeMillis(); startKm = odometerKm; lastKm = odometerKm
    }

    fun distance(km: Float) { if (startKm == null) startKm = km; lastKm = km }

    fun add(type: String, value: String) {
        if (active) raw.add(Raw(System.currentTimeMillis(), kmNow(), type, value))
    }

    /** 이번 주행에서 지금까지 달린 거리(km). 주행거리 신호가 없으면 0. */
    fun kmNow(): Float { val a = startKm; val b = lastKm; return if (a != null && b != null) (b - a).coerceAtLeast(0f) else 0f }
    fun secondsNow() = ((System.currentTimeMillis() - startTime) / 1000).toInt()

    /** 어떤 장치가 켜져 있던 시간을 모두 더한다(초). */
    private fun onSeconds(type: String, end: Long): Int {
        var total = 0L; var since = 0L
        raw.filter { it.type == type }.forEach { if (it.value == "on") { if (since == 0L) since = it.time } else if (since > 0) { total += it.time - since; since = 0L } }
        if (since > 0) total += end - since
        return (total / 1000).toInt()
    }

    /** 앱이 꺼져도 이어 쓸 수 있게 지금까지의 기록을 글자로 남긴다 */
    fun snapshot(): org.json.JSONObject = org.json.JSONObject().apply {
        put("start", startTime); startKm?.let { put("startKm", it.toDouble()) }; lastKm?.let { put("lastKm", it.toDouble()) }
        put("raw", org.json.JSONArray().apply { raw.forEach { put(org.json.JSONArray().put(it.time).put(it.km.toDouble()).put(it.type).put(it.value)) } })
    }
    fun restore(o: org.json.JSONObject) {
        raw.clear(); active = true
        startTime = o.getLong("start")
        startKm = if (o.has("startKm")) o.getDouble("startKm").toFloat() else null
        lastKm = if (o.has("lastKm")) o.getDouble("lastKm").toFloat() else null
        val a = o.getJSONArray("raw")
        for (i in 0 until a.length()) a.getJSONArray(i).let { raw.add(Raw(it.getLong(0), it.getDouble(1).toFloat(), it.getString(2), it.getString(3))) }
    }

    /** 주행을 끝내고 요약을 만든다. */
    fun finish(driver: String): Drive {
        active = false
        val end = System.currentTimeMillis()
        val totalKm = kmNow()
        val totalMs = (end - startTime).coerceAtLeast(1L)
        // 진행 정도: 주행거리 신호가 있으면 거리로, 없으면 시간으로 잰다.
        fun p(r: Raw) = (if (totalKm > 0.05f) r.km / totalKm else (r.time - startTime).toFloat() / totalMs).coerceIn(0f, 1f)

        val signals = raw.filter { it.type == Evt.SIGNAL }
        var longest = 0L; var onAt = 0L
        for (e in signals) {
            if (e.value == "OFF") { if (onAt > 0) longest = maxOf(longest, e.time - onAt); onAt = 0L }
            else if (onAt == 0L) onAt = e.time
        }
        val gears = raw.filter { it.type == Evt.GEAR && (it.value == "D" || it.value == "R") }.map { it.value }
        val firstR = gears.indexOf("R")

        // 그림에 쓸 표시만 남긴다: 켠 깜빡이, 밟은 브레이크, 와이퍼가 켜지고 꺼진 때.
        val events = raw.filter {
            (it.type == Evt.SIGNAL && it.value != "OFF") || (it.type == Evt.BRAKE && it.value == "on") ||
                it.type == Evt.WIPER || it.type == Evt.LIGHT || it.type == Evt.LOST || it.type == Evt.SAY
        }.map { Ev(p(it), it.type, it.value) }

        return Drive(
            driver = driver, date = end, distKm = totalKm, minutes = (totalMs / 60000).toInt(), seconds = (totalMs / 1000).toInt(), events = events,
            signals = signals.count { it.value != "OFF" },
            brakes = raw.count { it.type == Evt.BRAKE && it.value == "on" },
            // 와이퍼는 세척에도 쓰므로 모두 합쳐 1분 넘게 켜 둔 날만 비 온 날로 본다
            rained = onSeconds(Evt.WIPER, end) >= 60,
            // 전조등은 터널에서도 켜므로, 해가 진 시간(18시~6시)에 켠 날만 밤길로 본다
            night = raw.any { it.type == Evt.LIGHT && it.value == "on" } && java.util.Calendar.getInstance().apply { timeInMillis = end }.get(java.util.Calendar.HOUR_OF_DAY).let { it >= 18 || it < 6 },
            parkMoves = if (firstR < 0) 0 else gears.size - firstR,
            lostWay = raw.count { it.type == Evt.LOST },
            longestSignalSec = (longest / 1000).toInt(),
            cruise = raw.any { it.type == Evt.CRUISE && it.value == "on" },
            passenger = raw.any { it.type == Evt.SEAT },
            turns = raw.count { it.type == Evt.TURN },
            turnsSignaled = raw.count { it.type == Evt.TURN && it.value == "signaled" },
            calm = raw.count { it.type == Evt.CALM },
            lamps = raw.filter { it.type == Evt.LAMP && it.value.endsWith(":on") }.map { it.value.substringBefore(":") }.distinct()
        )
    }
}

object Writer {
    private val ordinals = listOf("첫", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열")
    fun ordinal(n: Int) = if (n in 1..10) "${ordinals[n - 1]} 번째" else "${n}번째"
    private val counts = listOf("한", "두", "세", "네", "다섯", "여섯", "일곱", "여덟", "아홉", "열")
    /** 3 → "세 번" */
    fun times(n: Int) = if (n in 1..10) "${counts[n - 1]} 번" else "${n}번"
    /** 이름에 조사를 붙인다. "나"는 "내가", "나의"로 바뀐다. */
    fun subj(name: String) = if (name == "나") "내가" else "${name}가"
    fun poss(name: String) = if (name == "나") "내" else "${name}의"

    /**
     * 일기를 쓴다. 잘한 것과 나아진 것을 먼저, 다음에 챙길 것은 하나만.
     * @param before 이 운전자가 전에 쓴 일기들 (처음인지, 나아졌는지 보려고)
     * @param guide  따라 달린 건네준 길이 있으면 그것
     */
    fun write(drive: Drive, before: List<Diary>, guide: Guide?, heardTips: Int, tone: String = "담백하게", seasoned: Boolean = false): Diary {
        val n = before.size + 1
        val sb = StringBuilder()
        var highlight = ""
        var stamp: String? = null

        if (guide != null) sb.append(if (guide.kind == "나") "지난번의 나와 같은 길을 다시 달렸다. " else "${subj(guide.owner)} 다니던 길을 오늘은 네가 운전했다. ")
        else if (n == 1) sb.append("오늘 처음으로 같이 달렸다. ")

        val firstRain = drive.rained && before.none { it.drive.rained }
        val firstNight = drive.night && before.none { it.drive.night }
        if (firstRain) { highlight = "와이퍼를 처음 켰다."; sb.append("$highlight "); stamp = "첫\n빗길" }
        else if (drive.rained) sb.append("와이퍼를 켜고 달렸다. ")
        else if (drive.events.any { it.type == Evt.WIPER && it.value == "on" }) sb.append("와이퍼를 잠깐 썼다. ")
        if (firstNight) { sb.append("전조등을 켜고 달린 것도 처음이다. "); if (stamp == null) stamp = "첫\n밤길" }

        val firstCruise = drive.cruise && before.none { it.drive.cruise }
        if (firstCruise) { sb.append("처음으로 차에게 속도를 맡겨 봤다. "); if (stamp == null) stamp = "첫\n크루즈" }
        else if (drive.cruise) sb.append("오늘도 크루즈를 켜고 달렸다. ")

        if (drive.passenger && before.none { it.drive.passenger }) { sb.append("처음으로 누군가를 태우고 달렸다. "); if (stamp == null) stamp = "첫\n동승" }

        // 함께 달린 거리가 10km, 100km를 넘는 날
        val kmBefore = before.sumOf { it.drive.distKm.toDouble() }
        val kmNow = kmBefore + drive.distKm
        for (mark in listOf(100, 10)) if (kmBefore < mark && kmNow >= mark) {
            sb.append("함께 달린 길이 ${mark}km를 넘었다. "); if (stamp == null) stamp = "${mark}\nkm"; break
        }

        if (guide == null && drive.place != null) {
            if (before.none { it.guideId == null && it.drive.place == drive.place }) { sb.append("처음으로 ${drive.place}까지 내 힘으로 가 봤다. "); if (stamp == null) stamp = "첫\n새 길" }
            else sb.append("${drive.place}에 또 다녀왔다. ")
        }
        drive.events.firstOrNull { it.type == Evt.SAY }?.let { sb.append("옆자리에서 “${it.value}”라고 해 줬다. ") }
        for (lamp in drive.lamps) {
            val name = Lamps.firstUse[lamp] ?: continue
            if (before.none { lamp in it.drive.lamps }) { sb.append("처음으로 ${name}을 써 봤다. "); if (stamp == null) stamp = "첫\n" + name.take(4); break }
        }
        // 연습을 건네준 사람의 초보 시절과 나란히 놓는다. 순위가 아니라 "누구나 처음엔"을 보여 주려는 것이다.
        if (guide != null && guide.kind == "연습" && guide.drive.parkMoves > 0 && drive.parkMoves > 0)
            sb.append("${guide.owner}도 처음엔 기어를 ${times(guide.drive.parkMoves)} 바꿨다고 했다. 나는 ${times(drive.parkMoves)}이었다. ")
        if (heardTips > 0 && guide != null) sb.append(if (guide.kind == "나") "지난번의 내가 남긴 한마디를 ${times(heardTips)} 들었다. " else "${poss(guide.owner)} 한마디를 ${times(heardTips)} 들었다. ")
        if (drive.signals > 0) sb.append("깜빡이는 ${times(drive.signals)} 켰다. ")
        if (drive.turns > 0) sb.append(
            if (drive.turnsSignaled == drive.turns) "돌 때마다 미리 깜빡이를 켰다. "
            else "${times(drive.turns)} 도는 동안 ${times(drive.turnsSignaled)}은 미리 깜빡이를 켰다. "
        )
        if (drive.calm > 0) sb.append("${times(drive.calm)} 숨을 골랐다. ")

        if (drive.parkMoves > 0) {
            val last = before.lastOrNull { it.drive.parkMoves > 0 }?.drive?.parkMoves
            val s = "후진을 넣은 뒤 기어를 ${times(drive.parkMoves)} 바꿔 자리를 잡았다."
            sb.append("$s ")
            if (last != null && drive.parkMoves < last) {
                sb.append("지난번엔 ${times(last)}이었다. ")
                if (highlight.isEmpty()) highlight = s
            }
        }
        if (guide != null) {
            // 졸업은 끝까지 달린 날만 센다. 중간에 멈춘 날과 모의 주행은 넣지 않는다.
            val followed = before.count { it.guideId == guide.id && it.drive.completed && !it.drive.sim } + if (drive.completed && !drive.sim) 1 else 0
            if (!drive.completed) sb.append("오늘은 끝까지 가지 않고 멈췄다. ")
            if (drive.completed && !drive.sim && followed == GRADUATE) { sb.append("이 길은 이제 졸업이다. "); stamp = "졸업" }
            else if (guide.kind != "나") sb.append("${subj(guide.owner)} ${times(guide.times)} 다닌 길에 네 기록이 생겼다. ")
        }

        // 다음에 챙길 것 하나
        sb.append(
            when {
                // 운전이 익숙한 사람에게는 고칠 것을 말하지 않고, 오늘의 드라이브를 남겨 둔다
                seasoned -> if (drive.place != null) "${drive.place}까지 다녀온 오늘을 남겨 둔다. 누군가에게 이 길을 건네줘도 좋겠다." else "오늘의 드라이브를 남겨 둔다."
                drive.longestSignalSec >= 20 -> "다음엔 깜빡이 끄는 것 하나만 챙기자."
                drive.turns > drive.turnsSignaled -> "다음엔 돌기 조금 전에 깜빡이를 먼저 켜 보자."
                drive.lostWay > 0 -> "내비가 길을 다시 찾았지만 괜찮다."
                else -> "오늘은 이대로 좋았다."
            }
        )
        if (stamp == null && n == 1) stamp = "첫\n운전"

        if (tone == "다정하게") sb.append(if (n == 1) "시작한 것만으로 충분한 날이다. " else "오늘도 무사히 다녀와 줘서 고맙다. ")

        val title = if (guide != null && guide.kind == "나") "같은 길, 다른 날" else if (guide != null && guide.kind == "추천") "${poss(guide.owner)} 추천 따라" else if (guide != null && guide.kind == "연습") "${poss(guide.owner)} 연습 따라" else if (guide != null) "같은 길, 다른 자리" else if (drive.place != null && before.none { it.drive.place == drive.place }) "처음 가 본 길" else if (seasoned) "드라이브 일기" else "${ordinal(n)} 운전"
        return Diary("d${drive.date}", drive.driver, drive.date, title, sb.toString().trim(), highlight, stamp, drive, guide?.id)
    }
}
