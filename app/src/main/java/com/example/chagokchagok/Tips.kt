// 차곡車곡 – 이렇게 쓸 수 있어요 (앱 사용 팁)
// 지금 상황에 맞는 팁 하나만 홈 맨 아래에 한 줄로. 닫거나 해 보면 다시 나오지 않는다.
// 전부는 설정 › 알아 두기 › 이렇게 쓸 수 있어요 에서 본다. 모든 팁은 실제로 되는 기능만 말한다.
package com.example.chagokchagok

import android.view.Gravity
import android.view.View

internal data class AppTip(val id: String, val title: String, val body: String, val icon: String, val whenUseful: MainActivity.() -> Boolean = { true })

internal val APP_TIPS = listOf(
    AppTip("no_dest", "목적지가 없어도 기록돼요", "기어를 D에 놓으면 기록이 시작되고, P에 놓으면 일기가 생겨요.", "play") { store.diariesOf(store.driver).isEmpty() },
    AppTip("prep_gear", "이 차의 기어부터 익혀요", "출발 준비에서 차종을 고르면 기어 쓰는 법과 안전 조작을 보여 줘요.", "gear") { !store.prepDone(store.driver) },
    AppTip("split", "내비 옆에 띄워 두세요", "Pleos 분할 화면으로 내비 옆에 두면, 달리는 동안 다음 회전과 한마디가 함께 보여요.", "navi"),
    AppTip("growth", "오른쪽 성장 바를 눌러 보세요", "무엇을 하면 다음 칸이 쌓이는지 단계별로 보여 줘요.", "layers"),
    AppTip("again", "같은 곳에 다시 가 보세요", "같은 목적지로 다시 가면 일기에서 지난번의 나와 나란히 비교해 줘요.", "compare") { store.diariesOf(store.driver).any { it.drive.place != null } },
    AppTip("self_note", "다음의 나에게 한마디", "일기에서 남기면, 같은 곳에 다시 갈 때 그 자리에서 들려줘요.", "quote") { store.diariesOf(store.driver).isNotEmpty() },
    AppTip("seat", "운전석을 바꿀 수 있어요", "함께 쓰는 차라면 이름을 눌러 운전석을 바꿔요. 기록은 사람마다 따로 쌓여요.", "people") { store.drivers.size > 1 },
    AppTip("give", "다니는 길을 건네주세요", "일기에서 ‘이 길 건네주기’를 누르면 함께 쓰는 사람이 같은 길을 달리며 그 자리에서 한마디를 들어요.", "gift") { store.diariesOf(store.driver).isNotEmpty() },
    AppTip("paper", "원고지로도 읽을 수 있어요", "일기 위의 ‘원고지’를 누르면 그림일기처럼 보여요.", "book") { store.diariesOf(store.driver).isNotEmpty() },
    AppTip("voice", "달리는 동안은 말로", "글레오에게 “차곡차곡, 천천히 갈래” 또는 “차곡차곡, 여기 한마디 남겨 줘”라고 말해 보세요. 남긴 말은 다음에 같은 곳에서 들려줘요.", "speaker"),
    AppTip("parked", "화면은 멈췄을 때만", "화면 조작은 멈췄을 때만 돼요. 달리는 동안 필요한 건 소리로 알려 줘요.", "speaker"),
)

/** 지금 보여 줄 팁 하나 (닫지 않았고, 지금 쓸모 있는 것) */
internal fun MainActivity.currentTip(): AppTip? = APP_TIPS.firstOrNull { !store.flag("tip_${it.id}") && it.whenUseful(this) }

/** 홈 맨 아래 한 줄 팁 */
internal fun MainActivity.tipStrip(): View? {
    val t = currentTip() ?: return null
    val k = kit
    return k.row(14).apply {
        setPadding(ui.dp(20), ui.dp(14), ui.dp(12), ui.dp(14))
        background = k.rounded(Ink.surface, 18, 1, Ink.line)
        addView(k.icon(t.icon, Ink.accent, 22))
        addView(k.col(2).apply {
            addView(k.text("이렇게 쓸 수 있어요 · ${t.title}", Type.BodyStrong))
            addView(k.text(t.body, Type.Sub, Ink.text2))
        }, k.lp(0, k.WRAP, 1f))
        addView(k.button("모든 팁", Kit.Btn.Plain) { go(MainActivity.LEARN, "tips") })
        addView(k.iconButton("close", Ink.text2, android.graphics.Color.TRANSPARENT, 40) { store.setFlag("tip_${t.id}"); render() })
    }
}

/** 설정 › 이렇게 쓸 수 있어요: 전부 */
internal fun MainActivity.tipsScreen(): View {
    val k = kit
    return k.col().apply {
        addView(k.header("이렇게 쓸 수 있어요", "차곡차곡을 더 잘 쓰는 방법 ${APP_TIPS.size}가지", onBack = { back() }))
        addView(k.gap(18))
        addView(k.grid(if (ui.wide) 2 else 1, APP_TIPS.map { t ->
            k.card(pad = 20) {
                addView(k.row(14).apply {
                    gravity = Gravity.TOP
                    addView(k.icon(t.icon, Ink.accent, 24))
                    addView(k.col(4).apply { addView(k.text(t.title, Type.Headline)); addView(k.text(t.body, Type.Sub, Ink.text2)) }, k.lp(0, k.WRAP, 1f))
                })
            }
        }, 14))
    }
}
