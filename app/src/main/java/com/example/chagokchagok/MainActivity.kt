// 차곡車곡 – 화면과 흐름
// 화면은 여섯 가지다: 홈, 출발 준비, 달리는 동안, 오늘의 일기, 길 남기기, 일기장.
// 기어를 D나 R에 놓으면 기록을 시작하고, P에 놓으면 일기를 쓴다.
package com.example.chagokchagok

import android.app.Activity
import android.app.AlertDialog
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : Activity(), Car.Listener {

    private lateinit var ui: Ui
    private lateinit var store: Store
    private lateinit var car: Car
    private lateinit var root: FrameLayout
    private val recorder = Recorder()
    private val ticker = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var chime: android.media.SoundPool? = null   // 조작이 확인됐을 때 나는 짧은 효과음
    private var chimeId = 0
    private var ttsReady = false

    private var screen = HOME
    private var activeGuide: Guide? = null      // 지금 따라 달리는 교본
    private var shownTips = mutableSetOf<Int>() // 이번 주행에서 이미 보여 준 한마디
    private var currentTip: String? = null
    private var diary: Diary? = null            // 지금 펼쳐 놓은 일기
    private val prepDone = mutableSetOf<String>() // 출발 준비에서 확인된 것
    private val prepSeen = mutableSetOf<String>()
    private var giveTitle = ""
    private val giveTips = mutableListOf<Tip>()

    companion object {
        const val HOME = "home"; const val PREP = "prep"; const val DRIVING = "driving"
        const val DIARY = "diary"; const val GIVE = "give"; const val BOOK = "book"; const val READY = "ready"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ui = Ui(this); store = Store(this)
        root = FrameLayout(this).apply { setBackgroundColor(Ink.asphalt) }
        setContentView(root)
        car = Car(this, this); car.start()
        chime = android.media.SoundPool.Builder().setMaxStreams(2).build().also { chimeId = it.load(this, R.raw.confirm, 1) }
        tts = TextToSpeech(this) { status ->
            ttsReady = status == TextToSpeech.SUCCESS
            if (ttsReady) tts?.language = Locale.KOREAN
        }
        show(HOME)
    }

    override fun onDestroy() {
        ticker.removeCallbacksAndMessages(null)
        car.stop(); tts?.shutdown(); chime?.release()
        super.onDestroy()
    }

    /** 창 크기가 바뀌면(내비와 나란히 놓일 때 등) 같은 화면을 새 크기로 다시 그린다. */
    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        ui = Ui(this); show(screen)
    }

    // ───────────── 차에서 온 소식 ─────────────

    override fun onCarEvent(type: String, value: String) {
        recorder.add(type, value)
        if (screen == PREP) { onPrep(type, value); return } // 연습 중의 기어 조작은 주행으로 치지 않는다
        if (type == Evt.GEAR) {
            if ((value == "D" || value == "R") && !recorder.active) startTrip()
            else if (value == "P" && recorder.active) endTrip()
        }
        if (screen == DRIVING) refreshDriving()
    }

    override fun onDistance(km: Float) { recorder.distance(km); if (recorder.active) checkTips() }

    private fun startTrip() {
        recorder.start(car.odometerKm)
        recorder.add(Evt.GEAR, car.gear)
        if (car.wiper) recorder.add(Evt.WIPER, "on")
        if (car.lowBeam) recorder.add(Evt.LIGHT, "on")
        shownTips = mutableSetOf(); currentTip = null
        show(DRIVING)
        val g = activeGuide
        say(if (g != null) "${Writer.subj(g.owner)} 남긴 길로 출발합니다. 그 자리에 가면 알려 드릴게요." else "출발합니다. 조용히 적고 있을게요.")
        ticker.removeCallbacksAndMessages(null)
        ticker.post(object : Runnable {
            override fun run() { if (recorder.active) { checkTips(); refreshDriving(); ticker.postDelayed(this, 1000) } }
        })
    }

    private fun endTrip() {
        ticker.removeCallbacksAndMessages(null)
        val driver = store.driver
        val drive = recorder.finish(driver)
        car.refreshNavi()
        val d = Writer.write(drive, store.diariesOf(driver), activeGuide, shownTips.size)
        store.addDiary(d)
        diary = d; activeGuide = null
        show(DIARY)
        say("도착했어요. 오늘 일기를 썼어요.")
    }

    /** 교본의 그 자리에 이르렀으면 한마디를 보여 주고 읽어 준다. 거리 신호가 없으면 시간으로 잰다. */
    private fun checkTips() {
        val g = activeGuide ?: return
        val km = recorder.kmNow()
        val progress = if (g.drive.distKm > 0.05f && km > 0f) km / g.drive.distKm
        else recorder.secondsNow().toFloat() / g.drive.seconds.coerceAtLeast(30)
        g.tips.forEachIndexed { i, tip ->
            if (i !in shownTips && progress >= tip.p - 0.02f) {
                shownTips.add(i); currentTip = tip.text; speak(tip.text); refreshDriving()
            }
        }
    }

    private fun speak(text: String) { if (ttsReady) tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "chagok") }

    /** 버튼으로 읽어 달라고 했을 때: 소리가 나면 읽고, 어느 쪽이든 글로도 보여 준다. */
    private fun readAloud(title: String, text: String) {
        speak(text)
        AlertDialog.Builder(this).setTitle(title)
            .setMessage(text + if (ttsReady) "" else "\n\n(이 기기에서는 음성이 준비되지 않아 글로 보여 드려요.)")
            .setPositiveButton("닫기") { _, _ -> tts?.stop() }.show()
    }

    /** 차가 건네는 말. 소리로 읽고, 같은 문장을 화면에도 잠깐 띄운다. */
    private fun say(text: String) { speak(text); toast(text) }

    /** 화면 위쪽에 잠깐 떴다 사라지는 안내. */
    private fun toast(s: String) {
        val bar = TextView(this).apply {
            text = s; textSize = 20f * ui.scale; setTextColor(Color.parseColor("#1B1705")); gravity = Gravity.CENTER
            setPadding(ui.dp(24), ui.dp(16), ui.dp(24), ui.dp(16))
            background = GradientDrawable().apply { setColor(Ink.paint); cornerRadius = ui.dp(14).toFloat() }
        }
        root.addView(bar, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.TOP or Gravity.CENTER_HORIZONTAL).apply { topMargin = ui.dp(18) })
        ticker.postDelayed({ root.removeView(bar) }, 4000)
    }

    // ───────────── 화면 바꾸기 ─────────────

    private fun show(next: String) {
        screen = next
        root.removeAllViews()
        root.addView(
            when (next) {
                PREP -> prepScreen(); DRIVING -> drivingScreen(); DIARY -> diaryScreen(); READY -> readyScreen()
                GIVE -> giveScreen(); BOOK -> bookScreen(); else -> homeScreen()
            }
        )
    }

    private val wide get() = resources.configuration.screenWidthDp > resources.configuration.screenHeightDp * 1.15f
    private fun col() = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
    private fun row() = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
    private fun gap(h: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(ui.dp(h), ui.dp(h)) }
    private fun fill() = View(this).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) }
    private fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    private val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    private val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    /** 넓으면 좌우로, 좁으면(내비 옆 세로 창) 위아래로 놓는 두 칸 틀. */
    private fun split(left: View, right: View, leftWeight: Float = 1.5f): View {
        val pad = ui.dp(34)
        return if (wide) row().apply {
            gravity = Gravity.TOP; setPadding(pad, pad, pad, pad)
            addView(left, lp(0, MATCH, leftWeight)); addView(gap(30)); addView(right, lp(0, MATCH, 1f))
        } else ScrollView(this).apply {
            addView(col().apply { setPadding(pad, pad, pad, pad); addView(left, lp(MATCH, WRAP)); addView(gap(26)); addView(right, lp(MATCH, WRAP)) })
        }
    }

    private fun header(title: String, back: Boolean = true): View = row().apply {
        if (back) addView(ui.text("‹ ", 40f, Ink.dim).apply { setOnClickListener { show(HOME) } })
        addView(ui.text(title, 34f, Ink.paint, ui.sign))
    }

    private fun rule() = View(this).apply { setBackgroundColor(Ink.kerb); layoutParams = lp(MATCH, ui.dp(2)) }

    // ───────────── 홈: 오늘의 길 ─────────────

    private fun homeScreen(): View {
        val driver = store.driver
        val guide = store.guidesFor(driver).lastOrNull()

        val left = col().apply {
            addView(row().apply {
                addView(ui.text("차곡車곡", 30f, Ink.paint, ui.sign).apply { setOnLongClickListener { settings(); true } })
                addView(ui.text("   차로 쓰는 성장일기", 18f, Ink.dim))
            })
            addView(gap(22))
            if (guide != null) {
                addView(ui.text("${Writer.subj(guide.owner)} 남긴 길" + if (guide.sample) " (예시)" else "", 19f, Ink.dim))
                addView(ui.text(guide.title, 58f, Ink.paint, ui.sign))
                addView(gap(8))
                addView(ui.text(facts(guide.drive) + "   ${Writer.times(guide.times)} 다닌 길", 22f, Color.parseColor("#CFCBC2")))
                addView(TraceView(this@MainActivity, ui).apply { lanes = listOf(Lane("", guide.drive, guide.tips)) }, lp(MATCH, ui.dp(120)))
                guide.tips.lastOrNull()?.let { addView(ui.text("“${it.text}”", 25f, Ink.paint, ui.batang).apply { setLineSpacing(0f, 1.3f) }) }
                addView(fill())
                addView(gap(18))
                addView(row().apply {
                    addView(ui.button("이 길로 출발", true) { depart(guide) })
                    addView(gap(14))
                    addView(ui.button("한마디 듣기", false) {
                        if (guide.tips.isEmpty()) toast("이 길에는 아직 남긴 한마디가 없어요.")
                        else readAloud("${Writer.poss(guide.owner)} 한마디", guide.tips.mapIndexed { i, t -> "${i + 1}. ${t.text}" }.joinToString("\n\n"))
                    })
                })
            } else {
                addView(ui.text("아직 받은 길이 없어요", 50f, Ink.paint, ui.sign))
                addView(gap(12))
                addView(ui.text("가족이 이 차로 달린 뒤 길을 남기면 여기에 나타납니다.\n기어를 D에 놓으면 지금 주행을 바로 기록합니다.", 22f, Color.parseColor("#CFCBC2")).apply { setLineSpacing(0f, 1.35f) })
                addView(fill())
                addView(gap(18))
                addView(row().apply {
                    addView(ui.button("지금 기록 시작", true) { activeGuide = null; startTrip() })
                    addView(gap(14))
                    addView(ui.button("예시 길 넣어 보기", false) { store.addSampleGuide(); show(HOME) })
                })
            }
        }

        val diaries = store.diariesOf(driver)
        val right = col().apply {
            store.news(driver).lastOrNull()?.let { news ->
                addView(col().apply {
                    setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(14))
                    background = GradientDrawable().apply { setColor(Color.parseColor("#1F3A2D")); cornerRadius = ui.dp(12).toFloat() }
                    addView(ui.text("받은 소식", 17f, Ink.go))
                    addView(ui.text(news, 21f, Ink.paint, ui.batang).apply { setLineSpacing(0f, 1.3f) })
                    setOnClickListener { store.clearNews(driver); show(HOME) }
                })
                addView(gap(16))
            }
            addView(row().apply {
                addView(ui.text("운전석에 앉은 사람", 18f, Ink.dim)); addView(View(this@MainActivity), lp(0, 1, 1f))
                addView(ui.text("점검", 18f, Ink.dim).apply { setPadding(ui.dp(12), ui.dp(6), 0, ui.dp(6)); setOnClickListener { settings() } })
            })
            addView(gap(8))
            addView(row().apply {
                store.drivers.forEach { name ->
                    val on = name == driver
                    addView(TextView(this@MainActivity).apply {
                        text = name; textSize = 24f * ui.scale; gravity = Gravity.CENTER; typeface = Typeface.DEFAULT_BOLD
                        setTextColor(if (on) Color.parseColor("#1B1705") else Ink.paint)
                        background = GradientDrawable().apply { cornerRadius = ui.dp(12).toFloat(); if (on) setColor(Ink.paint) else { setColor(Color.TRANSPARENT); setStroke(ui.dp(2), Ink.kerb) } }
                        setOnClickListener { store.driver = name; activeGuide = null; show(HOME) }
                    }, lp(0, ui.dp(64), 1f).apply { rightMargin = ui.dp(10) })
                }
            })
            addView(gap(22)); addView(rule())
            addView(step("차와 친해지기", if (store.prepDone(driver)) "마쳤어요" else "아직", store.prepDone(driver)))
            addView(rule())
            addView(step("아는 길", "일기 ${diaries.count { it.guideId != null }}편", diaries.any { it.guideId != null }))
            addView(rule())
            addView(step("새로운 길", "일기 ${diaries.count { it.guideId == null }}편", false))
            addView(rule())
            addView(fill()); addView(gap(18))
            addView(ui.button("출발 준비", false) {
                prepDone.clear(); prepSeen.clear(); show(PREP)
                prepLine = "안녕하세요. 면허를 따고 처음 타는 차는 낯설 수 있어요. 하나씩 같이 확인해 봐요."
                speak(prepLine); show(PREP)
            }, lp(MATCH, WRAP))
            addView(gap(12))
            addView(ui.button("일기장 ${diaries.size}편", false) { show(BOOK) }, lp(MATCH, WRAP))
        }
        return split(left, right)
    }

    private fun step(name: String, state: String, done: Boolean) = row().apply {
        setPadding(0, ui.dp(15), 0, ui.dp(15))
        addView(ui.text(if (done) "●  " else "○  ", 18f, if (done) Ink.go else Ink.dim))
        addView(ui.text(name, 24f)); addView(View(this@MainActivity), lp(0, 1, 1f)); addView(ui.text(state, 19f, Ink.dim))
    }

    private fun facts(d: Drive): String {
        val km = if (d.distKm > 0.05f) String.format(Locale.KOREA, "%.1fkm", d.distKm) else "거리 기록 없음"
        val time = if (d.minutes >= 1) "${d.minutes}분" else "${d.seconds}초"
        return "$km   $time"
    }

    private fun depart(g: Guide) {
        activeGuide = g
        routeSet = car.requestRoute(g.title, g.lat, g.lng)
        show(READY)
    }

    private var routeSet = false

    /** 출발을 누른 뒤: 무엇이 됐는지 보여 주고, 기어 D 또는 버튼으로 기록을 시작한다. */
    private fun readyScreen(): View {
        val g = activeGuide ?: return homeScreen()
        val pad = ui.dp(34)
        return col().apply {
            setPadding(pad, pad, pad, pad)
            addView(header("출발"))
            addView(gap(14))
            addView(ui.text(g.title, 58f, Ink.paint, ui.sign))
            addView(gap(10))
            addView(ui.text(
                if (routeSet) "●  내비게이션에 목적지를 넣었어요." else "○  내비게이션에 목적지를 넣지 못했어요. 길 안내 없이 기록만 합니다.",
                22f, if (routeSet) Ink.go else Ink.dim
            ))
            addView(gap(6))
            addView(ui.text("●  ${Writer.poss(g.owner)} 한마디 ${g.tips.size}개가 그 자리에서 나옵니다.", 22f, Ink.go))
            addView(gap(20))
            addView(ui.text("기어를 D에 놓으면 기록을 시작합니다.", 30f, Ink.paint, ui.sign))
            addView(fill()); addView(gap(18))
            addView(row().apply {
                addView(ui.button("지금 기록 시작", true) { startTrip() })
                addView(gap(14))
                addView(ui.button("그만두기", false) { activeGuide = null; show(HOME) })
            })
        }
    }

    /** 가상 콘솔에 조작이 없는 신호를 시험해 볼 때 쓴다. 실제 차량 신호가 아니다. */
    private fun testSignals() {
        val items = listOf(
            "깜빡이 왼쪽" to { onCarEvent(Evt.SIGNAL, "LEFT") }, "깜빡이 오른쪽" to { onCarEvent(Evt.SIGNAL, "RIGHT") },
            "깜빡이 끄기" to { onCarEvent(Evt.SIGNAL, "OFF") }, "브레이크 밟기" to { onCarEvent(Evt.BRAKE, "on") },
            "와이퍼 켜기" to { onCarEvent(Evt.WIPER, "on") }, "와이퍼 끄기" to { onCarEvent(Evt.WIPER, "off") },
            "전조등 켜기" to { onCarEvent(Evt.LIGHT, "on") }, "안전벨트 매기" to { onCarEvent("belt", "on") },
            "기어 D" to { onCarEvent(Evt.GEAR, "D") }, "기어 R" to { onCarEvent(Evt.GEAR, "R") }, "기어 P" to { onCarEvent(Evt.GEAR, "P") }
        )
        AlertDialog.Builder(this).setTitle("시험용 신호 (실제 차량 신호 아님)")
            .setItems(items.map { it.first }.toTypedArray()) { _, i -> items[i].second(); if (screen != DIARY) testSignals() }
            .setNegativeButton("닫기", null).show()
    }

    private fun settings() {
        val status = "내비: " + (if (car.naviConnected) "연결됨" else "연결 안 됨") +
            "\n주행거리 신호: " + (car.odometerKm?.let { "${it}km" } ?: "아직 없음") +
            (if (car.failures.isEmpty()) "\n실패한 신호 없음" else "\n\n실패: " + car.failures.entries.joinToString("\n") { "${it.key}: ${it.value}" })
        AlertDialog.Builder(this).setTitle("점검").setMessage(status)
            .setPositiveButton("시험용 신호") { _, _ -> testSignals() }
            .setNeutralButton("예시 길 넣기") { _, _ -> store.addSampleGuide(); show(HOME) }
            .setNegativeButton("기록 모두 지우기") { _, _ -> store.clearAll(); show(HOME) }
            .show()
    }

    // ───────────── 출발 준비: 해 보면 불이 들어온다 ─────────────

    private val prepItems = listOf(
        Triple("signal", "깜빡이", "양쪽을 한 번씩 켜 보세요"),
        Triple("wiper", "와이퍼", "한 번 켜 보세요"),
        Triple("light", "전조등", "한 번 켜 보세요"),
        Triple("gear", "기어", "R에 넣었다가 P로 돌려놓으세요"),
        Triple("belt", "안전벨트", "매면 켜져요")
    )
    // 안전벨트는 에뮬레이터에서 체결 상태를 바꿀 수 없을 수 있어, 준비를 마치는 조건에서는 뺀다.
    private val prepRequired = listOf("signal", "wiper", "light", "gear")

    private var prepLine = ""

    private fun onPrep(type: String, value: String) {
        val before = prepDone.toSet()
        when (type) {
            Evt.SIGNAL -> { if (value != "OFF") prepSeen.add(value); if (prepSeen.containsAll(listOf("LEFT", "RIGHT"))) prepDone.add("signal") }
            Evt.WIPER -> if (value == "on") prepDone.add("wiper")
            Evt.LIGHT -> if (value == "on") prepDone.add("light")
            "belt" -> if (value == "on") prepDone.add("belt")
            Evt.GEAR -> { if (value == "R") prepSeen.add("R"); if (value == "P" && "R" in prepSeen) prepDone.add("gear") }
        }
        val newly = (prepDone - before).firstOrNull()
        if (newly != null) {
            val finished = prepDone.containsAll(prepRequired)
            // 하나하나는 효과음으로만 알리고, 다 마쳤을 때만 말로 한다.
            chime?.play(chimeId, 1f, 1f, 1, 0, 1f)
            if (finished && newly in prepRequired) { prepLine = "다 됐어요. 이제 출발해도 좋아요."; speak(prepLine) }
            else if (!finished) prepLine = ""
        }
        if (prepDone.containsAll(prepRequired)) store.setPrepDone(store.driver)
        show(PREP)
    }

    private fun prepScreen(): View {
        val allDone = prepDone.containsAll(prepRequired)
        val next = prepRequired.firstOrNull { it !in prepDone }
        val pad = ui.dp(34)
        val body = col().apply {
            setPadding(pad, pad, pad, pad)
            addView(header("출발 준비"))
            addView(gap(10))
            if (prepLine.isNotEmpty()) { addView(ui.text("“$prepLine”", 22f, Ink.center, ui.batang)); addView(gap(10)) }
            addView(ui.text(if (allDone) "다 됐어요. 이제 이 차가 손에 익었습니다." else "한 번씩 해 보면 불이 들어와요", 44f, Ink.paint, ui.sign))
            addView(gap(22)); addView(rule())
            val tiles = if (wide) row() else col()
            prepItems.forEach { (key, name, hint) ->
                val done = key in prepDone
                tiles.addView(col().apply {
                    gravity = Gravity.CENTER_HORIZONTAL
                    setPadding(ui.dp(8), ui.dp(26), ui.dp(8), ui.dp(22))
                    if (key == next) setBackgroundColor(Color.parseColor("#2E2B22"))
                    addView(Telltale(this@MainActivity, key).apply { on = done; waiting = key == next }, lp(ui.dp(96), ui.dp(96)))
                    addView(gap(8))
                    addView(ui.text(name, 24f).apply { typeface = Typeface.DEFAULT_BOLD; gravity = Gravity.CENTER })
                    addView(ui.text(if (done) "확인했어요" else if (key == next || key == "belt") hint else "", 17f, if (key == next) Ink.center else Ink.dim).apply { gravity = Gravity.CENTER })
                }, if (wide) lp(0, WRAP, 1f) else lp(MATCH, WRAP))
            }
            addView(tiles, lp(MATCH, WRAP)); addView(rule())
            addView(gap(20))
            addView(ui.text("주차 중에만 하는 연습입니다. 여기서 바꾼 기어는 주행으로 기록하지 않습니다.", 19f, Ink.dim))
            addView(gap(18))
            addView(row().apply { addView(ui.button(if (allDone) "홈으로" else "나중에 하기", allDone) { show(HOME) }) })
        }
        return ScrollView(this).apply { isFillViewport = true; addView(body) }
    }

    // ───────────── 달리는 동안: 한 줄만 ─────────────

    private var drivingTip: TextView? = null
    private var drivingCount: TextView? = null

    private fun drivingScreen(): View {
        val pad = ui.dp(34)
        return col().apply {
            setPadding(pad, pad, pad, pad)
            addView(ui.text("차곡車곡", 28f, Ink.paint, ui.sign))
            addView(gap(28))
            val g = activeGuide
            addView(ui.text(if (g != null) "${Writer.poss(g.owner)} 한마디" else "오늘의 주행", 20f, Ink.dim))
            addView(gap(8))
            drivingTip = ui.text("", 44f, Ink.paint, ui.batang).apply { setLineSpacing(0f, 1.25f) }
            addView(drivingTip)
            addView(fill())
            drivingCount = ui.text("", 20f, Ink.dim)
            addView(drivingCount)
            addView(gap(14))
            addView(row().apply { addView(ui.button("주행 끝내고 일기 쓰기", false) { if (recorder.active) endTrip() }) })
            refreshDriving()
        }
    }

    private fun refreshDriving() {
        val g = activeGuide
        drivingTip?.text = currentTip ?: if (g != null) "그 자리에 가면 알려 줄게요." else "조용히 적고 있어요."
        drivingCount?.text = "●  조용히 적는 중   P에 놓으면 일기를 씁니다"
    }

    // ───────────── 오늘의 일기 ─────────────

    private fun diaryScreen(): View {
        val d = diary ?: return homeScreen()
        val guide = store.guide(d.guideId)
        val pagePad = ui.dp(22)

        val page = FrameLayout(this).apply {
            background = GradientDrawable().apply { setColor(Ink.paper); cornerRadius = ui.dp(6).toFloat() }
            addView(col().apply {
                setPadding(pagePad, ui.dp(18), pagePad, ui.dp(70))
                addView(row().apply {
                    addView(ui.text(SimpleDateFormat("M월 d일 EEEE", Locale.KOREA).format(Date(d.date)), 22f, Ink.ink, ui.batang))
                    addView(View(this@MainActivity), lp(0, 1, 1f))
                    addView(ui.text(if (d.drive.rained) "○ 맑음   ● 비" else "● 맑음   ○ 비", 18f, Ink.ink))
                })
                addView(gap(8))
                // 그림 칸: 오늘의 주행. 교본이 있으면 그 주행을 흐리게 함께 그린다.
                val lanes = mutableListOf<Lane>()
                if (guide != null) lanes.add(Lane(guide.owner, guide.drive, guide.tips, faded = true))
                lanes.add(Lane(d.driver, d.drive))
                addView(FrameLayout(this@MainActivity).apply {
                    background = GradientDrawable().apply { setColor(Color.WHITE); setStroke(ui.dp(2), Ink.ink) }
                    addView(TraceView(this@MainActivity, ui, onPaper = true).apply { this.lanes = lanes })
                }, lp(MATCH, ui.dp(if (lanes.size > 1) 200 else 130)))
                addView(gap(12))
                addView(GridText(this@MainActivity, ui).apply { cols = if (wide) 15 else 11; highlight = d.highlight; text = d.text })
            })
            // 도장
            d.stamp?.let { s ->
                addView(TextView(this@MainActivity).apply {
                    text = s; gravity = Gravity.CENTER; typeface = ui.sign; textSize = 22f * ui.scale; setTextColor(Ink.brake)
                    setLineSpacing(0f, 0.95f); rotation = -11f
                    background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Color.argb(210, 255, 254, 251)); setStroke(ui.dp(4), Ink.brake) }
                }, FrameLayout.LayoutParams(ui.dp(104), ui.dp(104), Gravity.END or Gravity.BOTTOM).apply { bottomMargin = ui.dp(10); rightMargin = ui.dp(12) })
            }
        }
        val left = ScrollView(this).apply { addView(page) }

        val right = col().apply {
            addView(ui.text(d.title, 40f, Ink.paint, ui.sign))
            addView(gap(10)); addView(rule())
            material("와이퍼", if (d.drive.rained) "켰음" else "안 켬").let { addView(it); addView(rule()) }
            material("깜빡이", "${d.drive.signals}번").let { addView(it); addView(rule()) }
            material("주차하며 오간 횟수", if (d.drive.parkMoves > 0) "${d.drive.parkMoves}번" else "없음").let { addView(it); addView(rule()) }
            if (guide != null) material("${Writer.subj(guide.owner)} 다닌 횟수", "${guide.times}번").let { addView(it); addView(rule()) }
            addView(fill()); addView(gap(14))
            addView(ui.button("일기 읽어 주기", false) { readAloud(d.title, d.text) }, lp(MATCH, WRAP))
            if (guide != null) {
                addView(gap(10))
                val sent = store.newsSent(d.id)
                addView(ui.button(if (sent) "${guide.owner}에게 소식을 보냈어요" else "${guide.owner}에게 도착 알리기", !sent) {
                    if (store.newsSent(d.id)) toast("이미 보낸 소식이에요.")
                    else {
                        store.addNews(guide.owner, "${Writer.subj(d.driver)} “${guide.title}”을 직접 운전해서 다녀왔어요.")
                        store.setNewsSent(d.id)
                        show(DIARY); toast("${guide.owner}의 홈 화면에 소식을 남겼어요.")
                    }
                }, lp(MATCH, WRAP))
            }
            addView(gap(10))
            addView(ui.button("이 길을 교본으로 주기", guide == null) { giveTitle = ""; giveTips.clear(); show(GIVE) }, lp(MATCH, WRAP))
            addView(gap(10))
            addView(ui.button("홈으로", false) { show(HOME) }, lp(MATCH, WRAP))
        }
        return split(left, right, 1.7f)
    }

    private fun material(name: String, value: String) = row().apply {
        setPadding(0, ui.dp(10), 0, ui.dp(10))
        addView(ui.text(name, 20f, Color.parseColor("#CFCBC2"))); addView(View(this@MainActivity), lp(0, 1, 1f))
        addView(ui.text(value, 26f, Ink.paint, ui.sign))
    }

    // ───────────── 길 남기기: 방금 달린 길을 교본으로 ─────────────

    private val titlePresets = listOf("할머니 댁 가는 길", "마트 가는 길", "학교 가는 길")
    private val tipPresets = listOf(
        "미리 오른쪽 차로로 가 있어.", "여기는 속도를 줄이는 게 좋아.",
        "주차장 입구가 좁아. 천천히 들어가.", "여기서 차선을 바꾸면 편해."
    )
    private val tipPlaces = listOf("출발하고 곧" to .15f, "가는 중간" to .5f, "도착 직전" to .92f)

    private fun giveScreen(): View {
        val d = diary ?: return homeScreen()
        val left = col().apply {
            addView(header("길을 남기기"))
            addView(gap(8))
            addView(ui.text("이 길을 교본으로 남길까요?", 42f, Ink.paint, ui.sign))
            addView(gap(6))
            addView(ui.text("평소처럼 달린 길이면 됩니다. 이름과 한마디만 얹어 주세요.", 20f, Color.parseColor("#CFCBC2")))
            addView(TraceView(this@MainActivity, ui).apply { lanes = listOf(Lane("", d.drive, giveTips)) }, lp(MATCH, ui.dp(120)))
            addView(ui.text("길 이름", 18f, Ink.dim))
            addView(gap(6))
            addView(row().apply {
                (titlePresets + "직접 쓰기").forEach { t ->
                    val on = t == giveTitle
                    addView(TextView(this@MainActivity).apply {
                        text = t; textSize = 19f * ui.scale; gravity = Gravity.CENTER
                        setTextColor(if (on) Color.parseColor("#1B1705") else Ink.paint)
                        background = GradientDrawable().apply { cornerRadius = ui.dp(10).toFloat(); if (on) setColor(Ink.paint) else { setColor(Color.TRANSPARENT); setStroke(ui.dp(2), Ink.kerb) } }
                        setPadding(ui.dp(14), ui.dp(12), ui.dp(14), ui.dp(12))
                        setOnClickListener { if (t == "직접 쓰기") typeIn("길 이름") { giveTitle = it; show(GIVE) } else { giveTitle = t; show(GIVE) } }
                    }, lp(WRAP, WRAP).apply { rightMargin = ui.dp(8) })
                }
            })
            if (giveTitle.isNotEmpty() && giveTitle !in titlePresets) { addView(gap(6)); addView(ui.text(giveTitle, 24f, Ink.paint, ui.sign)) }
        }
        val right = col().apply {
            addView(ui.text("남긴 한마디 ${giveTips.size}개", 18f, Ink.dim))
            addView(gap(6)); addView(rule())
            giveTips.forEachIndexed { i, t ->
                addView(ui.text("${i + 1}.  “${t.text}”", 22f, Ink.paint, ui.batang).apply { setPadding(0, ui.dp(12), 0, ui.dp(12)); setLineSpacing(0f, 1.25f) })
                addView(rule())
            }
            addView(gap(12))
            addView(ui.button("한마디 더하기", false) { addTip() }, lp(MATCH, WRAP))
            addView(gap(14))
            addView(ui.text("출발한 곳은 교본에 담지 않습니다. 목적지와 주행 요약, 한마디만 갑니다.", 17f, Ink.dim).apply { setLineSpacing(0f, 1.3f) })
            addView(fill()); addView(gap(14))
            addView(ui.button("교본으로 남기기", true) { saveGuide(d) }, lp(MATCH, WRAP))
        }
        return split(left, right)
    }

    private fun addTip() {
        AlertDialog.Builder(this).setTitle("어디쯤에서 할 말인가요?")
            .setItems(tipPlaces.map { it.first }.toTypedArray()) { _, where ->
                AlertDialog.Builder(this).setTitle("한마디")
                    .setItems((tipPresets + "직접 쓰기").toTypedArray()) { _, i ->
                        val place = tipPlaces[where].second
                        if (i == tipPresets.size) typeIn("한마디") { giveTips.add(Tip(place, it)); giveTips.sortBy { t -> t.p }; show(GIVE) }
                        else { giveTips.add(Tip(place, tipPresets[i])); giveTips.sortBy { t -> t.p }; show(GIVE) }
                    }.show()
            }.show()
    }

    private fun typeIn(title: String, done: (String) -> Unit) {
        val input = EditText(this)
        AlertDialog.Builder(this).setTitle(title).setView(input)
            .setPositiveButton("확인") { _, _ -> input.text.toString().trim().let { if (it.isNotEmpty()) done(it) } }
            .setNegativeButton("취소", null).show()
    }

    private fun saveGuide(d: Diary) {
        if (giveTitle.isEmpty()) { toast("길 이름을 골라 주세요."); return }
        val same = store.guides().firstOrNull { it.owner == d.driver && it.title == giveTitle }
        store.addGuide(
            Guide(
                id = same?.id ?: "g${d.date}", owner = d.driver, title = giveTitle,
                lat = car.destLat ?: car.lat, lng = car.destLng ?: car.lng,
                drive = d.drive, tips = giveTips.toList(), times = (same?.times ?: 0) + 1
            )
        )
        toast("“$giveTitle”을 교본으로 남겼어요. 가족의 홈 화면에 나타납니다.")
        show(HOME)
    }

    // ───────────── 일기장 ─────────────

    private fun bookScreen(): View {
        val pad = ui.dp(34)
        val list = store.diariesOf(store.driver).reversed()
        return ScrollView(this).apply {
            addView(col().apply {
                setPadding(pad, pad, pad, pad)
                addView(header("${store.driver}의 일기장"))
                addView(gap(16)); addView(rule())
                if (list.isEmpty()) addView(ui.text("아직 일기가 없어요. 한 번 달리고 오면 첫 장이 생깁니다.", 22f, Ink.dim).apply { setPadding(0, ui.dp(20), 0, 0) })
                list.forEach { d ->
                    addView(col().apply {
                        setPadding(0, ui.dp(16), 0, ui.dp(16))
                        addView(row().apply {
                            addView(ui.text(d.title, 28f, Ink.paint, ui.sign)); addView(View(this@MainActivity), lp(0, 1, 1f))
                            addView(ui.text(SimpleDateFormat("M월 d일", Locale.KOREA).format(Date(d.date)), 18f, Ink.dim))
                        })
                        addView(ui.text(d.text.take(46) + if (d.text.length > 46) "…" else "", 20f, Color.parseColor("#CFCBC2"), ui.batang))
                        setOnClickListener { diary = d; show(DIARY) }
                    })
                    addView(rule())
                }
            })
        }
    }
}
