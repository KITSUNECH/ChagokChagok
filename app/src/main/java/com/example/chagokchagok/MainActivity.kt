// 차곡車곡 – 앱의 틀
// 화면 이동, 사이드바(넓은 창)·탭 막대(좁은 창), 주행 모드 전환, 차 신호 연결만 여기서 한다.
// 각 화면은 *Screen.kt 파일에 있다.
//
// 주행 중(기록 중이거나 차가 주행 제한을 걸었을 때)에는 주행 화면 하나만 보인다.
// 일기·건네주기·함께 차곡·설정은 주차한 뒤에만 열린다.
package com.example.chagokchagok

import android.app.Activity
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.media.SoundPool
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

class MainActivity : Activity(), Car.Listener, TripHost {

    internal lateinit var ui: Ui
    internal lateinit var kit: Kit
    internal lateinit var store: Store
    internal lateinit var car: Car
    internal lateinit var trip: Trip
    internal lateinit var voice: Voice
    internal lateinit var places: Places
    internal lateinit var limits: Restrictions
    internal lateinit var sheets: Overlay
    private lateinit var root: FrameLayout
    private lateinit var stage: FrameLayout
    private var sound: SoundPool? = null
    private var chimeId = 0

    data class Route(val name: String, val arg: String? = null)
    companion object {
        const val TODAY = "today"; const val JOURNAL = "journal"; const val TOGETHER = "together"; const val ROADS = "roads"; const val SETTINGS = "settings"
        const val PREP = "prep"; const val READY = "ready"; const val DIARY = "diary"; const val GIVE = "give"; const val LEARN = "learn"
        const val PEOPLE = "people"; const val ONBOARD = "onboard"; const val DRIVE = "drive"; const val ANALYSIS = "analysis"; const val CHECK = "check"; const val PROPOSE = "propose"
        val TABS = listOf(TODAY, JOURNAL, TOGETHER, ROADS, SETTINGS)
        /** 주차 중에만 여는 화면 */
        val PARKED_ONLY = setOf(PREP, READY, DIARY, GIVE, LEARN, PEOPLE, JOURNAL, TOGETHER, ROADS, SETTINGS, ANALYSIS, CHECK, PROPOSE)
        const val REQ_LOCATION = 7
    }

    internal var route = Route(TODAY)
    private val stack = ArrayList<Route>()

    // 화면 사이에서 이어지는 상태
    internal val prepDone = mutableSetOf<String>()
    internal val prepSeen = mutableSetOf<String>()
    internal var prepLine = ""
    internal var chosenGuide: String? = null
    internal var giveKind = "길"
    internal var giveTitle = ""
    internal val giveTips = mutableListOf<Tip>()
    internal var journalTab = 0
    internal var roadsTab = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Ink.apply(Ink.isNight(this))
        ui = Ui(this); kit = Kit(this, ui); store = Store(this)
        root = FrameLayout(this).apply { setBackgroundColor(Ink.bg) }
        stage = FrameLayout(this)
        root.addView(stage, FrameLayout.LayoutParams(-1, -1))
        setContentView(root)
        sheets = Overlay(root) { kit }
        car = Car(this, this)
        trip = Trip(store, car, this)
        voice = Voice(this)
        places = Places(this) { lat, lng, hd -> Telemetry.hit("location", String.format("%.4f, %.4f", lat, lng)); trip.onLocation(lat, lng, hd); if (route.name == DRIVE) refreshDrive() }
        limits = Restrictions(this) { onRestricted() }
        car.start(); voice.start(); limits.start()
        if (store.locationOn) places.start()
        sound = SoundPool.Builder().setMaxStreams(2).build().also { chimeId = it.load(this, R.raw.confirm, 1) }
        if (store.onboarded && trip.resume { id -> store.guide(id) ?: if (id == Demo.dadCommute.id) Demo.dadCommute else null }) {
            tripStarted(); say("끊겼던 기록을 이어 갈게요.")
        } else if (store.onboarded) {
            go(TODAY)
            if (store.locationOn && !places.permitted() && !Car.offline) askLocation()
        } else go(ONBOARD)
    }

    override fun onDestroy() {
        trip.save(force = true)
        car.stop(); voice.release(); places.stop(); limits.stop(); sound?.release()
        super.onDestroy()
    }

    /** 창 크기나 낮/밤이 바뀌면 같은 화면을 새 크기·색으로 다시 그린다 */
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        Ink.apply(Ink.isNight(this)); ui = Ui(this); kit = Kit(this, ui)
        root.setBackgroundColor(Ink.bg); render()
    }

    @Deprecated("Activity 기본 뒤로 가기")
    override fun onBackPressed() {
        if (sheets.back()) return
        if (stack.size > 1 && !driving) { stack.removeAt(stack.lastIndex); route = stack.last(); render(); return }
        if (route.name != TODAY && route.name != ONBOARD && !driving) { go(TODAY); return }
        @Suppress("DEPRECATION") super.onBackPressed()
    }

    // ───────── 위치 권한 ─────────

    internal fun askLocation() { try { requestPermissions(arrayOf(Places.PERMISSION), REQ_LOCATION) } catch (_: Throwable) {} }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQ_LOCATION && places.permitted()) places.listen()
        if (route.name == SETTINGS || route.name == ONBOARD) render()
    }

    // ───────── 화면 이동 ─────────

    /** 지금 운전 중인지: 기록 중이거나, 차가 주행 제한을 걸었을 때 */
    internal val driving get() = trip.active || limits.restricted

    internal fun go(name: String, arg: String? = null) {
        if (driving && name in PARKED_ONLY) { toast("달리는 중에는 열지 않아요. 주차하면 볼 수 있어요."); return }
        val r = Route(name, arg)
        if (name in TABS || name == ONBOARD || name == DRIVE) { stack.clear() }
        else if (stack.isEmpty()) stack.add(Route(TODAY))
        stack.add(r); route = r
        render()
    }

    internal fun back() { onBackPressed() }

    internal fun render() {
        stage.removeAllViews()
        // 달리는 동안 화면이 꺼지지 않게
        if (driving) window.addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) else window.clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        if (driving && route.name != DRIVE) { route = Route(DRIVE); stack.clear(); stack.add(route) }
        if (!driving && route.name == DRIVE) { route = Route(TODAY); stack.clear(); stack.add(route) }
        val view = when (route.name) {
            ONBOARD -> onboardScreen()
            DRIVE -> driveScreen()
            else -> shell(page())
        }
        stage.addView(view, FrameLayout.LayoutParams(-1, -1))
    }

    private fun page(): View = when (route.name) {
        JOURNAL -> journalScreen()
        TOGETHER -> togetherScreen()
        ROADS -> roadsScreen()
        SETTINGS -> settingsScreen()
        PREP -> prepScreen()
        READY -> readyScreen()
        DIARY -> diaryScreen(route.arg)
        GIVE -> giveScreen(route.arg)
        LEARN -> learnScreen(route.arg)
        PEOPLE -> peopleScreen()
        ANALYSIS -> analysisScreen()
        CHECK -> checkScreen()
        PROPOSE -> proposeScreen()
        else -> todayScreen()
    }

    /** 현재 탭 (밀어 넣은 화면이면 그 아래 탭) */
    private fun currentTab() = stack.firstOrNull { it.name in TABS }?.name ?: TODAY

    /** 사이드바(넓은 창) 또는 아래 탭 막대(좁은 창)와 화면 */
    private fun shell(content: View): View {
        val k = kit
        val body = k.scroll(FrameLayout(this).apply {
            val padH = ui.dp(if (ui.wide) 44 else 20); val padV = ui.dp(if (ui.wide) 34 else 20)
            setPadding(padH, padV, padH, padV + ui.dp(20))
            addView(content, FrameLayout.LayoutParams(-1, -2))
        })
        if (ui.wide) return k.row().apply {
            gravity = Gravity.TOP
            addView(sidebar(), k.lp(ui.dp(268), k.MATCH))
            addView(View(this@MainActivity).apply { setBackgroundColor(Ink.line) }, k.lp(ui.dp(1), k.MATCH))
            addView(body, k.lp(0, k.MATCH, 1f))
            addView(View(this@MainActivity).apply { setBackgroundColor(Ink.line) }, k.lp(ui.dp(1), k.MATCH))
            addView(stackRail(), k.lp(ui.dp(84), k.MATCH))
        }
        return k.col().apply {
            addView(k.row().apply { gravity = Gravity.TOP; addView(body, k.lp(0, k.MATCH, 1f)); addView(stackRail(), k.lp(ui.dp(56), k.MATCH)) }, k.lp(k.MATCH, 0, 1f))
            addView(View(this@MainActivity).apply { setBackgroundColor(Ink.line) }, k.lp(k.MATCH, ui.dp(1)))
            addView(tabBar(), k.lp(k.MATCH, k.WRAP))
        }
    }

    private val tabIcons = mapOf(TODAY to ("today" to "오늘"), JOURNAL to ("book" to "일기장"), TOGETHER to ("people" to "함께"), ROADS to ("road" to "내 길"), SETTINGS to ("settings" to "설정"))

    private fun sidebar(): View {
        val k = kit; val tab = currentTab()
        return k.col().apply {
            setBackgroundColor(Ink.surface)
            setPadding(ui.dp(18), ui.dp(30), ui.dp(18), ui.dp(22))
            addView(brand().apply { setPadding(ui.dp(10), 0, 0, 0) })
            addView(k.text("차로 쓰는 성장일기", Type.Caption, Ink.text2).apply { setPadding(ui.dp(10), ui.dp(2), 0, ui.dp(22)) })
            addView(profileButton())
            addView(k.gap(20))
            TABS.forEach { t ->
                val (icon, label) = tabIcons.getValue(t)
                val on = t == tab
                addView(k.row(14).apply {
                    minimumHeight = ui.dp(54); setPadding(ui.dp(14), 0, ui.dp(14), 0)
                    addView(k.icon(icon, if (on) Ink.accent else Ink.text2, 24))
                    addView(k.text(label, Type.Body, if (on) Ink.accent else Ink.text).apply { typeface = if (on) ui.semibold else ui.regular })
                    k.pressable(this, if (on) k.rounded(Ink.accentSoft, 14) else null) { go(t) }
                }, k.lp(k.MATCH, k.WRAP).apply { bottomMargin = ui.dp(4) })
            }
            addView(k.fill())
            addView(carStatus())
        }
    }

    /** 지금 운전석에 앉은 사람. 누르면 바꾼다 */
    private fun profileButton(): View {
        val k = kit; val d = store.driver
        return k.row(12).apply {
            setPadding(ui.dp(12), ui.dp(12), ui.dp(12), ui.dp(12))
            addView(k.avatar(d, 42))
            addView(k.col(2).apply {
                addView(k.text(d, Type.BodyStrong))
                addView(k.text("${store.typeOf(d)} · 운전석", Type.Caption, Ink.text2))
            }, k.lp(0, k.WRAP, 1f))
            addView(k.icon("down", Ink.text3, 18))
            k.pressable(this, k.rounded(Ink.fill, 16)) { switchDriver() }
        }
    }

    internal fun switchDriver() {
        val names = store.drivers
        sheets.sheet("누가 운전하나요?", "운전석에 앉은 사람의 기록과 받은 길이 보여요.") { close ->
            addView(kit.groupOf(names.map { n ->
                kit.item(n, "${store.relation(n)} · ${store.typeOf(n)}", trailing = if (n == store.driver) kit.icon("check", Ink.accent, 20) else kit.avatar(n, 32)) {
                    close(); store.driver = n; chosenGuide = null; go(TODAY); toast("$n 님의 운전석으로 바꿨어요.")
                }
            } + kit.item("사람 추가·관리", icon = "plus") { close(); go(PEOPLE) }).apply { background = kit.rounded(Ink.fill, 18) })
        }
    }

    /** 사이드바 아래: 무엇과 연결돼 있는지 */
    private fun carStatus(): View {
        val k = kit
        val vehicleOk = !car.failures.containsKey("vehicle") && !Car.offline
        return k.row(10).apply {
            setPadding(ui.dp(14), ui.dp(12), ui.dp(12), ui.dp(12))
            addView(k.status(if (vehicleOk) "차량 데이터 ${Telemetry.total}건" else "차량 연결 안 됨", if (vehicleOk) Ink.go else Ink.text3), k.lp(0, k.WRAP, 1f))
            addView(k.text("α0.1", Type.Micro, Ink.text3))
            k.pressable(this, null) { go(CHECK) }
        }
    }

    private fun tabBar(): View {
        val k = kit; val tab = currentTab()
        return k.row().apply {
            setBackgroundColor(Ink.surface); setPadding(ui.dp(6), ui.dp(6), ui.dp(6), ui.dp(8))
            TABS.forEach { t ->
                val (icon, label) = tabIcons.getValue(t); val on = t == tab
                addView(k.col(4).apply {
                    gravity = Gravity.CENTER_HORIZONTAL; setPadding(0, ui.dp(8), 0, ui.dp(6))
                    addView(k.icon(icon, if (on) Ink.accent else Ink.text3, 26))
                    addView(k.text(label, Type.Micro, if (on) Ink.accent else Ink.text2))
                    k.pressable(this, null) { go(t) }
                }, k.lp(0, k.WRAP, 1f))
            }
        }
    }

    // ───────── 차에서 온 신호 ─────────

    override fun onCarEvent(type: String, value: String) {
        Telemetry.hit(type, value)
        if (route.name == CHECK) render()
        if (route.name == PREP && !trip.active) { onPrep(type, value); return }   // 연습 중 기어 조작은 주행으로 치지 않는다
        val wasActive = trip.active
        trip.onCar(type, value)
        if (wasActive != trip.active) return   // 시작/끝은 tripStarted/tripEnded 가 화면을 바꾼다
        when (route.name) {
            DRIVE -> refreshDrive()
            READY -> if (type == "belt" || type == Evt.SEAT || (type == Evt.NAVI && value == "route")) render()
            TODAY -> if (type == Evt.LAMP) render()
        }
    }

    override fun onDistance(km: Float) { Telemetry.hit("odometer", String.format("%.1fkm", km)); trip.onDistance(km) }
    override fun onArrived() = trip.onArrived()
    override fun onNaviLocation(lat: Double, lng: Double) = places.fromNavi(lat, lng)

    private fun onRestricted() {
        if (limits.restricted) sheets.closeAll()
        render()
    }

    // ───────── TripHost ─────────

    override fun speak(text: String) { if (store.voiceOn) voice.say(text) }
    override fun say(text: String) { speak(text); sheets.toast(text, "speaker") }
    override fun chime() { sound?.play(chimeId, 1f, 1f, 1, 0, 1f) }
    override fun tripStarted() { sheets.closeAll(); route = Route(DRIVE); stack.clear(); stack.add(route); render() }
    override fun tripChanged() { if (route.name == DRIVE) refreshDrive() }
    override fun tripEnded(d: Diary) { Session.convoy = null; stack.clear(); stack.add(Route(TODAY)); route = Route(DIARY, d.id); stack.add(route); render() }
    override fun diaryUpdated(d: Diary) { if (route.name == DIARY && route.arg == d.id) render() }
    override fun lampsChanged() { if (route.name == TODAY) render() }

    internal fun toast(s: String) = sheets.toast(s)

    /** 소리로 읽고 글로도 보여 준다 */
    internal fun readAloud(title: String, text: String) {
        speak(text)
        sheets.sheet(title, if (voice.ready) null else "음성: ${voice.status}") { close ->
            addView(kit.voice(text, 22f))
            addView(kit.buttons(kit.button("다시 듣기", Kit.Btn.Secondary, "speaker") { speak(text) }, kit.button("닫기") { voice.stop(); close() }, stack = false))
        }
    }
}
