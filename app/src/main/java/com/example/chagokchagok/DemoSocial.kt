// 차곡車곡 – Pleos 계정 연동 미리보기 (시연용)
//
// 지금 Pleos에는 단독 계정만 있고 친구·가족을 잇는 기능이 없다. 그래서 다른 차를 타는 친구·가족과 이어졌을 때의
// 모습을 "가상의 친구"로 보여 준다. 설정에서 켜고 끌 수 있고, 여기서 나오는 사람·글·위치는 모두 '시연용'으로 표시한다.
// 내 차에서 실제로 나온 기록과 섞이지 않도록, 가상 데이터는 저장소에 넣지 않고 여기서만 만든다.
package com.example.chagokchagok

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout

internal data class Friend(val id: String, val name: String, val relation: String, val car: String, val career: String, val status: String, val driving: Boolean)
internal data class DemoPost(val id: String, val friend: Friend, val daysAgo: Int, val kind: String, val title: String, val text: String, val facts: String, val stamp: String? = null, val guide: Guide? = null)

/** 시연용 Pleos 계정 표시 (가상의 Pleos ID) */
internal val Friend.pleosId get() = "@$id"

/** Pleos 계정 배지: 둥근 표시 + Pleos ID */
internal fun MainActivity.pleosBadge(f: Friend, short: Boolean = false): View = kit.row(6).apply {
    addView(View(context).apply { background = kit.rounded(Ink.accent, 3) }, kit.lp(ui.dp(6), ui.dp(6)))
    addView(kit.text(if (short) f.pleosId else "Pleos ${f.pleosId}", Type.Micro, Ink.accent).apply { maxLines = 1 })
}

internal object Demo {
    val friends = listOf(
        Friend("minsu", "민수", "친구", "디 올 뉴 아반떼", "면허 2년 차", "지금 운전 중 · 판교 쪽", true),
        Friend("jieun", "지은", "친구", "더 뉴 그랜저 (부모님 차)", "장롱면허 6년", "어제 6년 만에 운전석에", false),
        Friend("dad2", "아빠", "가족", "더 뉴 그랜저", "운전 25년", "오늘 출근길 11.2km", false),
        Friend("seoyeon", "서연", "친구", "디 올 뉴 아반떼", "면허 3개월", "주차장 연습 중", false),
    )
    /** 친구 추가에서 찾을 수 있는 사람 */
    val findable = Friend("hajun", "하준", "친구", "더 뉴 그랜저", "면허 1년 차", "주말에 바다 다녀옴", false)

    private fun day(n: Int) = System.currentTimeMillis() - n * 86400000L

    /** 다른 차에서 건넨 길 (아빠의 출근길: 예시 길을 거꾸로) */
    val dadCommute: Guide by lazy {
        val pts = SampleRoute.points.reversed()
        val full = Paths.of(pts)
        fun tip(p: Float, t: String) = Paths.at(full, p).let { Tip(p, t, it?.lat, it?.lng) }
        Guide("demo_dad_commute", "아빠", "아빠 회사 가는 길", pts.last().first, pts.last().second,
            Drive("아빠", day(1), 11.2f, 27, 1620, listOf(Ev(.18f, Evt.SIGNAL, "LEFT"), Ev(.22f, Evt.BRAKE, "on"), Ev(.6f, Evt.SIGNAL, "RIGHT"), Ev(.9f, Evt.BRAKE, "on")), 2, 2, false, false, 1, 0, 5, path = Paths.shareable(full)),
            listOf(tip(.3f, "여기 합류 구간은 깜빡이 일찍 켜고 천천히 들어와."), tip(.85f, "지하 주차장 들어가는 길이 가팔라. 브레이크 살살.")), 300)
    }
    val seoyeonPractice: Guide by lazy {
        Guide("demo_seoyeon_alley", "서연", "학원 앞 좁은 골목", null, null,
            Drive("서연", day(3), 0.8f, 11, 660, listOf(Ev(.3f, Evt.BRAKE, "on"), Ev(.5f, Evt.BRAKE, "on"), Ev(.7f, Evt.SIGNAL, "RIGHT")), 1, 6, false, false, 3, 0, 4),
            listOf(Tip(.4f, "사이드미러 접고 지나가면 마음이 편해."), Tip(.8f, "맞은편 차 오면 넓은 곳에서 먼저 기다려.")), 4, kind = "연습")
    }

    val posts: List<DemoPost> by lazy {
        val f = friends.associateBy { it.id }
        listOf(
            DemoPost("demo_jieun_first", f.getValue("jieun"), 1, "일기", "6년 만의 운전석", "아파트 한 바퀴만 돌았는데 손이 기억하더라. 깜빡이 위치부터 헷갈렸지만 다섯 번 켰다.", "1.2km · 14분 · 깜빡이 5번", "첫\n운전"),
            DemoPost("demo_dad_commute_post", f.getValue("dad2"), 1, "길", dadCommute.title, dadCommute.tips.first().text, "11.2km · 27분 · 한마디 2개", guide = dadCommute),
            DemoPost("demo_seoyeon_post", f.getValue("seoyeon"), 3, "연습", seoyeonPractice.title, seoyeonPractice.tips.first().text, "0.8km · 11분 · 한마디 2개", guide = seoyeonPractice),
            DemoPost("demo_minsu_dt", f.getValue("minsu"), 4, "추천", "드라이브 스루 처음 가 본 날", "창문 내리고 주문하는 거 생각보다 안 무서워. 출구 쪽 턱만 조심.", "6.3km · 19분 · 드라이브 스루 카페"),
        )
    }

    /** 처음 보여 줄 댓글 */
    val seedComments = mapOf(
        "demo_jieun_first" to listOf("민수" to "축하해! 다음엔 같이 한 바퀴 돌자", "서연" to "나도 장롱이었는데 힘내!"),
        "demo_dad_commute_post" to listOf("엄마" to "합류 구간 진짜 조심해야 해"),
        "g_sample" to listOf("민수" to "할머니 댁 길 나도 가 봤는데 주차장 좁더라"),
    )

    /** 같이 가기에서 친구가 앞서 가는 정도 (내 진행 정도에 더한다, 시연용) */
    const val LEAD = 0.09f
}

// ═══════════════ 화면 조각 ═══════════════

/** 함께 차곡 맨 위: 시연용 안내 + Pleos 친구 줄 */
internal fun MainActivity.demoFriendsSection(): View {
    val k = kit
    val mine = Demo.friends.filter { it.id !in store.demoRemoved() } + listOfNotNull(Demo.findable.takeIf { it.id in store.demoAdded() })
    return k.col(14).apply {
        addView(k.row(12).apply {
            setPadding(ui.dp(18), ui.dp(14), ui.dp(18), ui.dp(14)); background = k.rounded(Ink.alpha(Ink.warn, .12f), 16)
            addView(k.icon("info", Ink.warn, 22))
            addView(k.text("미리 보기 · 시연용. 다른 차의 친구·가족과 계정으로 이어지면 이렇게 쓸 수 있어요. 여기 나오는 친구는 가상의 사람이에요.", Type.Caption, Ink.text), k.lp(0, k.WRAP, 1f))
        })
        addView(k.row().apply {
            addView(k.text("Pleos 친구 ${mine.size}명", Type.Headline), k.lp(0, k.WRAP, 1f))
            addView(k.button("친구 추가", Kit.Btn.Tinted, "plus") { addFriendSheet() })
        })
        addView(k.hscroll(k.row(12).apply {
            mine.forEach { f ->
                addView(k.col(6).apply {
                    gravity = Gravity.CENTER_HORIZONTAL; setPadding(ui.dp(14), ui.dp(14), ui.dp(14), ui.dp(12))
                    addView(FrameLayout(context).apply {
                        addView(k.avatar(f.name, 56))
                        if (f.driving) addView(View(context).apply { background = android.graphics.drawable.GradientDrawable().apply { shape = android.graphics.drawable.GradientDrawable.OVAL; setColor(Ink.go); setStroke(ui.dp(3), Ink.surface) } },
                            FrameLayout.LayoutParams(ui.dp(18), ui.dp(18), Gravity.BOTTOM or Gravity.END))
                    }, k.lp(ui.dp(56), ui.dp(56)))
                    addView(k.text(f.name, Type.BodyStrong).apply { gravity = Gravity.CENTER })
                    addView(pleosBadge(f, short = true).apply { gravity = Gravity.CENTER })
                    addView(k.text(f.car.substringBefore(" ("), Type.Micro, Ink.text2).apply { gravity = Gravity.CENTER; maxLines = 1 })
                    k.pressable(this, k.rounded(Ink.surface, 18)) { friendSheet(f) }
                }, k.lp(ui.dp(132), k.MATCH))   // 친구 칸 높이를 맞춘다
            }
        }))
    }
}

/** 친구 프로필 (시연용) */
internal fun MainActivity.friendSheet(f: Friend) {
    sheets.sheet(f.name, "${f.relation} · ${f.car} · ${f.career}") { close ->
        val k = kit
        addView(k.row(14).apply {
            addView(k.avatar(f.name, 64))
            addView(k.col(4).apply {
                addView(pleosBadge(f))
                addView(k.status(f.status, if (f.driving) Ink.go else Ink.text3))
                addView(k.tag("Pleos 계정 연동 · 시연용 가상 친구", Ink.warn))
            }, k.lp(0, k.WRAP, 1f))
        })
        val posts = Demo.posts.filter { it.friend.id == f.id }
        addView(k.grid(3, listOf(k.stat("${posts.size}", "올린 글"), k.stat(if (f.id == "dad2") "1" else "0", "건넨 길"), k.stat(if (f.driving) "운전 중" else "주차", "지금")), 10))
        addView(k.buttons(
            k.button("같이 가기", Kit.Btn.Primary, "car") { close(); convoyWith(f) },
            k.button("응원 보내기", Kit.Btn.Secondary, "heart") { close(); toast("${f.name} 님에게 응원을 보냈어요 (시연용)") },
            stack = false))
        if (f.id !in Demo.friends.map { it.id } || f.relation == "친구") addView(k.button("친구 끊기", Kit.Btn.Plain) { close(); sheets.confirm("${f.name} 님과 친구를 끊을까요?", "시연용 가상 친구예요.", "끊기", true) { store.demoRemove(f.id); render(); toast("친구를 끊었어요.") } })
    }
}

/** 친구 추가: Pleos ID 검색, 초대 코드 (시연용) */
internal fun MainActivity.addFriendSheet() {
    val code = "CHAGOK-" + (store.driver.hashCode() and 0xffff).toString(16).uppercase().padStart(4, '0')
    sheets.sheet("친구 추가", "Pleos 계정으로 친구를 찾아요. 시연용이라 실제 계정은 찾지 않아요.") { close ->
        val k = kit
        addView(k.card(pad = 20, color = Ink.fill) {
            addView(k.text("내 초대 코드", Type.Caption, Ink.text2))
            addView(k.text(code, Type.Title).apply { letterSpacing = .08f })
            addView(k.text("친구의 차곡車곡 › 함께 › 친구 추가에서 이 코드를 넣으면 이어져요.", Type.Caption, Ink.text2))
        })
        addView(k.button("Pleos ID로 찾기", Kit.Btn.Primary, "people") {
            close()
            sheets.input("Pleos ID 또는 초대 코드", "예: hajun 또는 CHAGOK-1A2B", ok = "찾기") { q ->
                val f = Demo.findable
                sheets.sheet("찾았어요", "‘$q’ (시연용 결과)") { c2 ->
                    addView(k.item(f.name, "${f.car} · ${f.career}", trailing = k.avatar(f.name, 40)))
                    addView(k.button("친구 요청 보내기", Kit.Btn.Primary, "plus") {
                        c2(); toast("${f.name} 님에게 친구 요청을 보냈어요.")
                        window.decorView.postDelayed({ store.demoAdd(f.id); toast("${f.name} 님이 친구 요청을 수락했어요 (시연용)"); if (route.name == MainActivity.TOGETHER) render() }, 2500)
                    })
                }
            }
        })
    }
}

/** 다른 차에서 올라온 글 카드 (시연용) */
internal fun MainActivity.demoPostCard(p: DemoPost): View {
    val k = kit
    val tag = when (p.kind) { "길" -> "건네준 길"; "연습" -> "연습"; "추천" -> "추천 장소"; else -> "일기" }
    return k.card(pad = 22) {
        addView(k.row(10).apply {
            addView(k.avatar(p.friend.name, 40))
            addView(k.col(2).apply {
                addView(k.row(8).apply { addView(k.text(p.friend.name, Type.BodyStrong)); addView(pleosBadge(p.friend)) })
                addView(k.text("${p.friend.car} · ${if (p.daysAgo <= 1) "어제" else "${p.daysAgo}일 전"}", Type.Caption, Ink.text2))
            }, k.lp(0, k.WRAP, 1f))
            addView(k.tag("$tag · 다른 차 · 시연용", Ink.warn))
        })
        addView(k.gap(14))
        p.guide?.let { g ->
            addView(FrameLayout(context).apply {
                background = k.rounded(Ink.fill, 18); setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
                addView(TraceView(context, ui).apply { lanes = listOf(Lane("", g.drive, g.tips)) }, FrameLayout.LayoutParams(-1, ui.dp(110)))
            })
            addView(k.gap(14))
        }
        addView(k.row(12).apply {
            gravity = Gravity.TOP
            addView(k.col(6).apply {
                addView(k.text(p.title, Type.Title))
                addView(k.voice("“${p.text}”", 19f, Ink.text2))
                addView(k.text(p.facts, Type.Caption, Ink.text3))
            }, k.lp(0, k.WRAP, 1f))
            p.stamp?.let { addView(stamp(it, true, 1, 60)) }
        })
        addView(k.gap(12))
        val g = p.guide
        if (g != null) addView(k.buttons(
            k.button(if (g.kind == "연습") "나도 해 보기" else "이 길로 출발", Kit.Btn.Primary, "play") { depart(g) },
            k.button("같이 가기", Kit.Btn.Secondary, "car") { convoyWith(p.friend, g) }, stack = false))
        addView(commentsBlock(p.id))
    }
}

/** 댓글: 두 개까지 보여 주고, 누르면 전부와 입력 */
internal fun MainActivity.commentsBlock(id: String): View {
    val k = kit
    val all = (if (store.demoOn) Demo.seedComments[id].orEmpty() else emptyList()) + store.comments(id)
    return k.col(6).apply {
        setPadding(0, ui.dp(10), 0, 0)
        all.takeLast(2).forEach { (who, t) -> addView(k.row(8).apply { addView(k.text(who, Type.Sub).apply { typeface = ui.semibold }); addView(k.text(t, Type.Sub, Ink.text2), k.lp(0, k.WRAP, 1f)) }) }
        addView(k.text(if (all.isEmpty()) "댓글 달기" else "댓글 ${all.size}개 · 댓글 달기", Type.Sub, Ink.accent).apply {
            setPadding(0, ui.dp(6), 0, ui.dp(4))
            setOnClickListener { commentsSheet(id) }
        })
    }
}

private fun MainActivity.commentsSheet(id: String) {
    val all = (if (store.demoOn) Demo.seedComments[id].orEmpty() else emptyList()) + store.comments(id)
    sheets.sheet("댓글 ${all.size}개", "차가 서 있을 때만 쓸 수 있어요.") { close ->
        val k = kit
        if (all.isEmpty()) addView(k.text("첫 댓글을 남겨 보세요.", Type.Sub, Ink.text2))
        else addView(k.groupOf(all.map { (who, t) -> k.item(t, who, trailing = k.avatar(who, 30)) }).apply { background = k.rounded(Ink.fill, 18) })
        val quick = listOf("축하해!", "나도 가 볼래", "조심히 다녀", "다음엔 같이 가자")
        addView(k.chips(quick, null) { t -> store.addComment(id, store.driver, t); close(); render() })
        addView(k.button("직접 쓰기", Kit.Btn.Secondary, "pencil") { close(); sheets.input("댓글", "") { store.addComment(id, store.driver, it); render() } })
    }
}

/** 같이 가기: 친구가 앞서 가고 내가 뒤따른다 (시연용) */
internal fun MainActivity.convoyWith(f: Friend, g: Guide? = null) {
    val guide = g ?: if (f.id == "dad2") Demo.dadCommute else store.guidesFor(store.driver).lastOrNull { it.kind == "길" }
    if (guide == null) { toast("같이 갈 길이 없어요. 먼저 길을 하나 받아 주세요."); return }
    Session.convoy = f
    toast("${f.name} 님이 앞서 가요. 주행 화면에 ${f.name} 님의 위치가 보여요 (시연용)")
    depart(guide)
}

/** 주행 화면의 같이 가기 카드 */
internal object ConvoyViews { var text: android.widget.TextView? = null; var bar: View? = null; var arrivedSaid = false }

internal fun MainActivity.convoyCard(): View? {
    val f = Session.convoy ?: return null
    val k = kit
    ConvoyViews.text = k.text("", Type.Sub, Ink.text2); ConvoyViews.arrivedSaid = false
    return k.card(pad = 20) {
        addView(k.row(12).apply {
            addView(k.avatar(f.name, 36))
            addView(k.col(2).apply { addView(k.text("${f.name} 님과 같이 가기", Type.BodyStrong)); addView(ConvoyViews.text) }, k.lp(0, k.WRAP, 1f))
            addView(k.tag("시연용", Ink.warn))
        })
        addView(k.gap(10))
        ConvoyViews.bar = object : View(context) {
            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
            override fun onDraw(c: android.graphics.Canvas) {
                val me = convoyMe(); val them = (me + Demo.LEAD).coerceAtMost(1f)
                val y = height / 2f; val r = ui.dp(9).toFloat()
                p.color = Ink.fill; c.drawRoundRect(r, y - ui.dp(4), width - r, y + ui.dp(4), r, r, p)
                fun dot(v: Float, color: Int) { val x = r + (width - 2 * r) * v; p.color = Ink.surface; c.drawCircle(x, y, r + ui.dp(3), p); p.color = color; c.drawCircle(x, y, r, p) }
                dot(them, Ink.person(f.name)); dot(me, Ink.accent)
            }
        }
        addView(ConvoyViews.bar, k.lp(k.MATCH, ui.dp(28)))
    }
}

internal fun MainActivity.convoyMe(): Float {
    val g = trip.guide ?: return 0f
    val km = trip.recorder.kmNow()
    return if (g.drive.distKm > 0.05f && km > 0.05f) (km / g.drive.distKm).coerceIn(0f, 1f) else (trip.recorder.secondsNow() / g.drive.seconds.coerceAtLeast(60).toFloat()).coerceIn(0f, 1f)
}

internal fun MainActivity.refreshConvoy() {
    val f = Session.convoy ?: return
    val g = trip.guide ?: return
    val me = convoyMe(); val them = (me + Demo.LEAD).coerceAtMost(1f)
    val gapKm = (them - me) * g.drive.distKm
    ConvoyViews.text?.text = if (them >= 1f) "${f.name} 님은 먼저 도착했어요" else String.format("%.1fkm 앞에서 가는 중", gapKm)
    ConvoyViews.bar?.invalidate()
    if (them >= 1f && !ConvoyViews.arrivedSaid) { ConvoyViews.arrivedSaid = true; speak("${f.name} 님이 먼저 도착했어요. 천천히 오세요.") }
}
