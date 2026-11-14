// 차곡車곡 – 함께 차곡 · 내 길
// 함께 차곡: 함께 쓰는 사람들이 건넨 길과 다녀온 이야기. 보기 → 저장 → 다녀오기 → 내 경험 덧붙이기.
// 순위나 숫자 경쟁은 없다. 응원은 누가 했는지만 보여 준다.
package com.example.chagokchagok

import android.view.Gravity
import android.view.View
import android.widget.FrameLayout

internal object FeedPrefs { var filter = 0 }

/** 이 일기를 지금 운전자가 볼 수 있는지 (올린 사람의 공개 설정과 공개 범위) */
internal fun MainActivity.visible(d: Diary): Boolean {
    if (store.posted(d.id) == null) return false
    if (d.driver == store.driver) return true
    if (!store.shares(d.driver)) return false
    return store.audience(d.id) != "가족" || store.relation(store.driver) == "가족"
}

internal fun MainActivity.togetherScreen(): View {
    val k = kit; val me = store.driver
    data class Item(val date: Long, val view: () -> View, val kind: Int)
    val items = mutableListOf<Item>()
    store.guides().filter { it.kind != "나" }.forEach { g -> items.add(Item(g.drive.date, { guideCard(g) }, if (g.kind == "추천") 2 else 1)) }
    store.diaries().filter { visible(it) && it.guideId == null }.forEach { d -> items.add(Item(d.date, { diaryCard(d) }, 3)) }
    if (store.demoOn) Demo.posts.filter { it.friend.id !in store.demoRemoved() }.forEach { p -> items.add(Item(System.currentTimeMillis() - p.daysAgo * 86400000L, { demoPostCard(p) }, when (p.kind) { "추천" -> 2; "일기" -> 3; else -> 1 })) }
    val shown = items.filter { FeedPrefs.filter == 0 || it.kind == FeedPrefs.filter }.sortedByDescending { it.date }
    return k.col().apply {
        addView(k.header("함께 차곡", "함께 쓰는 사람들이 건넨 길과 다녀온 이야기예요. 마음에 들면 나도 가 볼 수 있어요."))
        addView(k.gap(18))
        if (store.demoOn) { addView(demoFriendsSection()); addView(k.gap(18)) }
        addView(k.chips(listOf("전체", "건네준 길", "추천 장소", "일기"), listOf("전체", "건네준 길", "추천 장소", "일기")[FeedPrefs.filter]) { FeedPrefs.filter = listOf("전체", "건네준 길", "추천 장소", "일기").indexOf(it); render() })
        addView(k.gap(18))
        if (shown.isEmpty()) addView(k.card { addView(k.empty("people", "아직 함께 쓰는 사람이 없어요", "혼자서도 괜찮아요. 내가 다녀온 길이 차곡이 되고, 다음의 나에게 한마디를 남길 수 있어요. 누군가 이 차를 함께 쓰게 되면 여기에 그 사람의 길이 쌓여요.",
            k.button("사람 추가", Kit.Btn.Secondary, "plus") { addPerson() },
            *(if (BuildConfig.DEMOS) arrayOf(k.button("예시로 둘러보기", Kit.Btn.Tinted, "sparkle") { store.addSampleGuide(); store.addSampleFriend(); render() }) else emptyArray()))) })
        else addView(k.grid(if (ui.wide) 2 else 1, shown.map { it.view() }, 18))
    }
}

/** 카드 머리: 누가, 언제, 무엇 */
private fun MainActivity.cardHead(author: String, date: Long, tag: String, sample: Boolean) = kit.row(10).apply {
    addView(kit.avatar(author, 40))
    addView(kit.col(2).apply {
        addView(kit.text(author, Type.BodyStrong))
        addView(kit.text("${store.relation(author)} · ${dateText(date, "M월 d일")}", Type.Caption, Ink.text2))
    }, kit.lp(0, kit.WRAP, 1f))
    addView(kit.tag(tag + if (sample) " · 예시" else "", if (tag == "추천 장소") Ink.go else Ink.accent))
}

private fun MainActivity.heartsRow(id: String): View {
    val k = kit; val me = store.driver; val hs = store.hearts(id)
    return k.row(10).apply {
        addView(k.row(6).apply {
            addView(k.icon(if (me in hs) "heartFill" else "heart", if (me in hs) Ink.danger else Ink.text2, 24))
            addView(k.text(if (me in hs) "응원했어요" else "응원", Type.Sub, if (me in hs) Ink.danger else Ink.text2))
            setPadding(ui.dp(4), ui.dp(10), ui.dp(10), ui.dp(10))
            setOnClickListener { store.toggleHeart(id, me); render() }
        })
        if (hs.isNotEmpty()) addView(k.text(hs.joinToString(", ") + " 님이 응원", Type.Caption, Ink.text3), k.lp(0, k.WRAP, 1f))
    }
}

/** 건네준 길·연습·추천 장소 카드 */
private fun MainActivity.guideCard(g: Guide): View {
    val k = kit; val me = store.driver
    val tag = when (g.kind) { "연습" -> "연습"; "추천" -> "추천 장소"; else -> "건네준 길" }
    val followers = store.diaries().filter { it.guideId == g.id && it.driver != g.owner }
    val saved = g.id in store.saved(me)
    return k.card(pad = 22) {
        addView(cardHead(g.owner, g.drive.date, tag, g.sample))
        addView(k.gap(14))
        addView(FrameLayout(context).apply {
            background = k.rounded(Ink.fill, 18)
            setPadding(ui.dp(12), ui.dp(8), ui.dp(12), ui.dp(8))
            addView(TraceView(context, ui).apply { lanes = listOf(Lane("", g.drive, g.tips)) }, FrameLayout.LayoutParams(-1, ui.dp(110)))
        })
        addView(k.gap(14))
        addView(k.text(g.title, Type.Title))
        g.tips.firstOrNull()?.let { addView(k.gap(4)); addView(k.voice("“${it.text}”", 19f, Ink.text2)) }
        val facts = mutableListOf(facts(g.drive))
        g.drive.place?.let { facts.add(it) }
        if (g.tips.size > 1) facts.add("한마디 ${g.tips.size}개")
        if (g.times > 1) facts.add("${g.owner} 님이 ${g.times}번 다닌 길")
        addView(k.text(facts.joinToString("  ·  "), Type.Caption, Ink.text3).apply { setPadding(0, ui.dp(8), 0, 0) })
        // 이어 간 사람들: 이 길을 따라 달리고 올린 일기
        val replies = followers.filter { visible(it) }
        if (replies.isNotEmpty()) {
            addView(k.gap(12))
            addView(k.col(8).apply {
                setPadding(ui.dp(16), ui.dp(12), ui.dp(16), ui.dp(12)); background = k.rounded(Ink.fill, 16)
                addView(k.text("이어 간 사람 ${replies.size}명", Type.Micro, Ink.text2))
                replies.takeLast(3).forEach { r ->
                    addView(k.row(10).apply {
                        addView(k.avatar(r.driver, 26))
                        addView(k.text("${r.driver} · “${store.posted(r.id)}”", Type.Sub), k.lp(0, k.WRAP, 1f))
                        r.stamp?.let { addView(k.tag(it.replace("\n", " "), Ink.stamp)) }
                    })
                }
            })
        }
        addView(k.gap(14))
        if (g.owner != me) {
            val mine = followers.lastOrNull { it.driver == me && it.drive.completed }
            addView(k.buttons(
                k.button(if (g.kind == "연습") "나도 해 보기" else if (g.kind == "추천") "이 목적지로 출발" else "이 길로 출발", Kit.Btn.Primary, "play") { chosenGuide = g.id; depart(g) },
                if (mine != null && store.posted(mine.id) == null) k.button("내 경험 덧붙이기", Kit.Btn.Tinted, "pencil") { postSheet(mine) }
                else k.button(if (saved) "저장됨" else "저장", Kit.Btn.Secondary, if (saved) "bookmarkFill" else "bookmark") { store.toggleSaved(me, g.id); toast(if (saved) "저장을 풀었어요." else "내 길 › 저장한 길에 넣었어요."); render() },
                stack = false))
        } else addView(k.text("내가 건넨 길이에요 · 따라 달린 사람 ${followers.map { it.driver }.distinct().size}명", Type.Caption, Ink.text2))
        addView(commentsBlock("g_${g.id}"))
        addView(heartsRow("g_${g.id}"))
    }
}

/** 올린 일기 카드 */
private fun MainActivity.diaryCard(d: Diary): View {
    val k = kit; val me = store.driver
    return k.card(pad = 22) {
        addView(cardHead(d.driver, d.date, "일기", false))
        addView(k.gap(14))
        addView(k.row(14).apply {
            gravity = Gravity.TOP
            addView(k.col(6).apply {
                addView(k.text(d.title, Type.Title))
                addView(k.voice("“${store.posted(d.id)}”", 19f, Ink.text2))
                addView(k.text(listOfNotNull(facts(d.drive), d.drive.place).joinToString("  ·  "), Type.Caption, Ink.text3))
            }, k.lp(0, k.WRAP, 1f))
            d.stamp?.let { addView(stamp(it, true, 3, 64)) }
        })
        addView(k.gap(12))
        if (d.driver == me) addView(k.buttons(
            k.button("일기 보기", Kit.Btn.Secondary, "book") { go(MainActivity.DIARY, d.id) },
            k.button("내리기", Kit.Btn.Plain) { sheets.confirm("함께 차곡에서 내릴까요?", "다른 사람의 화면에서 이 일기가 사라져요.", "내리기", true) { val cap = store.posted(d.id) ?: ""; store.unpost(d.id); toastUndo("함께 차곡에서 내렸어요.") { store.post(d.id, cap) }; render() } },
            stack = false))
        else if (d.drive.placeLat != null) addView(k.button("나도 가 보기", Kit.Btn.Primary, "play") {
            depart(Guide("visit_${d.id}", d.driver, d.drive.place ?: d.title, d.drive.placeLat, d.drive.placeLng, d.drive.copy(path = Paths.shareable(d.drive.path)), emptyList(), 1, kind = "추천"))
        })
        addView(commentsBlock("d_${d.id}"))
        addView(heartsRow("d_${d.id}"))
    }
}

// ═══════════════ 내 길 ═══════════════

internal fun MainActivity.roadsScreen(): View {
    val k = kit; val me = store.driver
    val mine = store.guides().filter { it.owner == me && it.kind != "나" }
    val saved = (store.guides() + if (store.demoOn) listOf(Demo.dadCommute, Demo.seoyeonPractice) else emptyList()).filter { it.id in store.saved(me) }
    val trips = store.diariesOf(me).filter { it.guideId == null && it.drive.place != null && !it.drive.sim }.groupBy { it.drive.place!! }
    val body = when (roadsTab) {
        0 -> if (mine.isEmpty()) k.card { addView(k.empty("gift", "아직 건넨 길이 없어요", "평소처럼 달린 뒤 일기에서 ‘이 길 건네주기’를 누르면, 가족이나 친구가 같은 길을 달릴 수 있어요.")) }
        else k.groupOf(mine.map { g ->
            val followers = store.diaries().filter { it.guideId == g.id && it.driver != me }.map { it.driver }.distinct()
            k.item(g.title, "${when (g.kind) { "연습" -> "연습"; "추천" -> "추천 장소"; else -> "길" }} · 한마디 ${g.tips.size}개" + if (followers.isNotEmpty()) " · ${followers.joinToString()} 님이 따라 달림" else "", "gift", chevron = true) { guideMenu(g) }
        })
        1 -> if (saved.isEmpty()) k.card { addView(k.empty("bookmark", "저장한 길이 없어요", "함께 차곡에서 마음에 드는 길을 저장해 두면 여기에서 바로 출발할 수 있어요.", k.button("함께 차곡 보기", Kit.Btn.Tinted) { go(MainActivity.TOGETHER) })) }
        else k.groupOf(saved.map { g -> k.item(g.title, "${g.owner} · ${facts(g.drive)}", "bookmarkFill", trailing = k.button("출발", Kit.Btn.Tinted) { depart(g) }) })
        else -> if (trips.isEmpty()) k.card { addView(k.empty("pin", "다녀온 곳이 아직 없어요", "내비에 목적지를 넣고 다녀오면 여기에 남아요. 다시 가거나 추천 장소로 건넬 수 있어요.")) }
        else k.groupOf(trips.map { (place, ds) ->
            val last = ds.last()
            k.item(place, "${Writer.times(ds.size)} · 마지막 ${dateText(last.date, "M월 d일")}" + (store.feeling(last.id)?.let { " · “$it”" } ?: ""), "pin", chevron = true) {
                sheets.choose(place, null, listOfNotNull(if (last.drive.placeLat != null) "다시 가기" else null, if (ds.size >= 2) "이 길의 나 (그래프)" else null, "추천 장소로 건네주기", "일기 보기")) { i ->
                    val opts = listOfNotNull(if (last.drive.placeLat != null) "again" else null, if (ds.size >= 2) "road" else null, "give", "diary")
                    when (opts[i]) {
                        "again" -> goAgain(place, last)
                        "road" -> go(MainActivity.ROAD, last.id)
                        "give" -> { giveTitle = place; giveTips.clear(); giveKind = "추천"; go(MainActivity.GIVE, last.id) }
                        else -> go(MainActivity.DIARY, last.id)
                    }
                }
            }
        })
    }
    return k.col().apply {
        addView(k.header("내 길", "내가 건넨 길, 저장한 길, 다녀온 곳이 쌓이는 보관함이에요."))
        addView(k.gap(18))
        addView(k.segmented(listOf("건넨 길 ${mine.size}", "저장한 길 ${saved.size}", "다녀온 곳 ${trips.size}"), roadsTab) { roadsTab = it; render() }, k.lp(if (ui.wide) ui.dp(560) else k.MATCH, k.WRAP))
        addView(k.gap(18))
        addView(body)
    }
}

private fun MainActivity.guideMenu(g: Guide) {
    sheets.choose(g.title, "내가 건넨 길", listOf("한마디 더하기", "다시 가기", "건네기 취소"), listOf("pencil", "play", "trash")) { i ->
        when (i) {
            0 -> sheets.choose("어디쯤에서 할 말인가요?", null, TIP_PLACES.map { it.first }) { where ->
                sheets.input("한마디", "예: 여기서 미리 왼쪽 차로로") { t ->
                    val p = TIP_PLACES[where].second
                    val pt = Paths.at(g.drive.path, p)
                    store.addGuide(g.copy(tips = (g.tips + Tip(p, t, pt?.lat, pt?.lng)).sortedBy { it.p })); toast("“${g.title}”에 한마디를 더했어요."); render()
                }
            }
            1 -> depart(g.copy(id = "again_${g.id}", kind = "다시"))
            else -> sheets.confirm("건네기를 취소할까요?", "함께 쓰는 사람의 화면에서 이 길이 사라져요. 이미 쓴 일기는 남아요.", "취소하기", true) { store.removeGuide(g.id); render(); toastUndo("“${g.title}” 건네기를 취소했어요.") { store.addGuide(g) } }
        }
    }
}
