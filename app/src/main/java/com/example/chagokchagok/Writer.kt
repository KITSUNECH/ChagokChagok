// 차곡車곡 – 주행을 기록하고 일기를 쓰는 곳
// Recorder 는 주행 중 들어온 신호를 쌓아 두었다가 주행이 끝나면 Drive 로 정리한다.
// Writer 는 그 Drive 를 사실만으로 된 일기 문장으로 바꾼다. 점수는 매기지 않는다.
package com.example.chagokchagok

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
                it.type == Evt.WIPER || it.type == Evt.LOST
        }.map { Ev(p(it), it.type, it.value) }

        return Drive(
            driver = driver, date = end, distKm = totalKm, minutes = (totalMs / 60000).toInt(), seconds = (totalMs / 1000).toInt(), events = events,
            signals = signals.count { it.value != "OFF" },
            brakes = raw.count { it.type == Evt.BRAKE && it.value == "on" },
            rained = raw.any { it.type == Evt.WIPER && it.value == "on" },
            night = raw.any { it.type == Evt.LIGHT && it.value == "on" },
            parkMoves = if (firstR < 0) 0 else gears.size - firstR,
            lostWay = raw.count { it.type == Evt.LOST },
            longestSignalSec = (longest / 1000).toInt(),
            cruise = raw.any { it.type == Evt.CRUISE && it.value == "on" },
            passenger = raw.any { it.type == Evt.SEAT }
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
     * @param guide  따라 달린 교본이 있으면 그것
     */
    fun write(drive: Drive, before: List<Diary>, guide: Guide?, heardTips: Int): Diary {
        val n = before.size + 1
        val sb = StringBuilder()
        var highlight = ""
        var stamp: String? = null

        if (guide != null) sb.append("${subj(guide.owner)} 다니던 길을 오늘은 네가 운전했다. ")
        else if (n == 1) sb.append("오늘 처음으로 같이 달렸다. ")

        val firstRain = drive.rained && before.none { it.drive.rained }
        val firstNight = drive.night && before.none { it.drive.night }
        if (firstRain) { highlight = "와이퍼를 처음 켰다."; sb.append("$highlight "); stamp = "첫\n빗길" }
        else if (drive.rained) sb.append("비가 와서 와이퍼를 켰다. ")
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

        if (heardTips > 0 && guide != null) sb.append("${poss(guide.owner)} 한마디를 ${times(heardTips)} 들었다. ")
        if (drive.signals > 0) sb.append("깜빡이는 ${times(drive.signals)} 켰다. ")

        if (drive.parkMoves > 0) {
            val last = before.lastOrNull { it.drive.parkMoves > 0 }?.drive?.parkMoves
            val s = "주차는 ${times(drive.parkMoves)} 만에 넣었다."
            sb.append("$s ")
            if (last != null && drive.parkMoves < last) {
                sb.append("지난번엔 ${times(last)}이었다. ")
                if (highlight.isEmpty()) highlight = s
            }
        }
        if (guide != null) {
            val followed = before.count { it.guideId == guide.id } + 1
            if (followed == GRADUATE) { sb.append("이 길은 이제 졸업이다. "); stamp = "졸업" }
            else sb.append("${subj(guide.owner)} ${times(guide.times)} 다닌 길에 네 기록이 생겼다. ")
        }

        // 다음에 챙길 것 하나
        sb.append(
            when {
                drive.longestSignalSec >= 20 -> "다음엔 깜빡이 끄는 것 하나만 챙기자."
                drive.lostWay > 0 -> "길을 한 번 놓쳤지만 괜찮다."
                else -> "오늘은 이대로 좋았다."
            }
        )
        if (stamp == null && n == 1) stamp = "첫\n운전"

        val title = if (guide != null) "같은 길, 다른 자리" else "${ordinal(n)} 운전"
        return Diary("d${drive.date}", drive.driver, drive.date, title, sb.toString().trim(), highlight, stamp, drive, guide?.id)
    }
}
