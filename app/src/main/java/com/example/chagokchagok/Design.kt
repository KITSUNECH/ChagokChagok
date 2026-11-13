// 차곡車곡 – 디자인 토큰
// 태블릿 같은 차 화면을 위해 Apple·Tesla 쪽 결을 따른다: 넉넉한 여백, 둥근 카드, 색은 강조 하나.
// 낮에는 밝게, 밤(차가 야간 모드일 때)에는 어둡게 바뀐다. 일기 종이와 원고지는 낮밤 상관없이 종이색이다.
package com.example.chagokchagok

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface

/** 색. 이름은 쓰임새로 짓는다. apply()로 낮/밤 값이 바뀐다. */
object Ink {
    var night = false; private set

    // 바탕과 면
    var bg = 0; var surface = 0; var fill = 0; var line = 0
    // 글자
    var text = 0; var text2 = 0; var text3 = 0
    // 강조와 상태
    var accent = 0; var onAccent = 0; var accentSoft = 0
    var go = 0; var warn = 0; var danger = 0
    // 차량 신호 (그림에서 뜻을 가진 색)
    var signal = 0; var brake = 0; var rain = 0; var beam = 0

    // 일기 종이 (낮밤 공통)
    val paper = Color.parseColor("#FBFAF6")
    val ink = Color.parseColor("#1F2328")
    val grid = Color.parseColor("#EBB1A6")
    val stamp = Color.parseColor("#E5484D")
    val paperRoad = Color.parseColor("#EFEDE7")
    val paperLine = Color.parseColor("#B8B5AD")
    val highlight = Color.parseColor("#FCEBB6")

    // 예전 그림 코드가 쓰는 이름 (같은 값을 가리킨다)
    val asphalt get() = bg
    val road get() = fill
    val kerb get() = line
    val paint get() = text
    val dim get() = text3
    val center get() = accent

    init { apply(false) }

    fun apply(night: Boolean) {
        this.night = night
        fun c(s: String) = Color.parseColor(s)
        if (night) {
            bg = c("#0B0B0D"); surface = c("#1C1C1F"); fill = c("#2A2A2E"); line = c("#38383C")
            text = c("#F5F5F7"); text2 = c("#A1A1A6"); text3 = c("#6E6E73")
            accent = c("#0A84FF"); onAccent = c("#FFFFFF"); accentSoft = c("#0A2A4D")
            go = c("#32D74B"); warn = c("#FF9F0A"); danger = c("#FF453A")
            // 차량 신호 색은 색약 검사기를 통과한 조합 (Charts.kt 의 Timeline 과 같다)
            signal = c("#d95926"); brake = c("#e66767"); rain = c("#3987e5"); beam = c("#199e70")
        } else {
            bg = c("#F2F2F5"); surface = c("#FFFFFF"); fill = c("#ECECF0"); line = c("#E1E1E6")
            text = c("#1D1D1F"); text2 = c("#6E6E73"); text3 = c("#A1A1A6")
            accent = c("#0071E3"); onAccent = c("#FFFFFF"); accentSoft = c("#E5F0FD")
            go = c("#1F9D48"); warn = c("#C26A00"); danger = c("#D7261E")
            signal = c("#eb6834"); brake = c("#e34948"); rain = c("#2a78d6"); beam = c("#1baf7a")
        }
    }

    /** 차(시스템)가 야간 모드인지 */
    fun isNight(ctx: Context) = (ctx.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES

    /** 반투명 */
    fun alpha(color: Int, a: Float) = Color.argb((a * 255).toInt(), Color.red(color), Color.green(color), Color.blue(color))

    /** 사람마다 고정된 색 (아바타) */
    fun person(name: String): Int {
        val list = listOf("#0A84FF", "#30B0C7", "#34C759", "#FF9F0A", "#FF6482", "#AF52DE", "#5E5CE6", "#A2845E")
        return Color.parseColor(list[(name.hashCode() and 0x7fffffff) % list.size])
    }
}

/** 글자 크기 단계. 차 화면은 팔 길이에서 보므로 휴대폰보다 한 단계 크다. */
enum class Type(val size: Float, val weight: Int, val tracking: Float = 0f) {
    Display(40f, 700, -0.02f),
    Large(32f, 700, -0.02f),
    Title(26f, 700, -0.01f),
    Headline(21f, 600),
    Body(19f, 400),
    BodyStrong(19f, 600),
    Sub(17f, 400),
    Caption(15f, 400),
    Micro(13f, 600, 0.02f),
}

/** 창 크기에 맞춘 단위와 글꼴. 창이 바뀌면 새로 만든다. */
class Ui(val ctx: Context) {
    val regular: Typeface = ctx.resources.getFont(R.font.pretendard_regular)
    val semibold: Typeface = ctx.resources.getFont(R.font.pretendard_semibold)
    val bold: Typeface = ctx.resources.getFont(R.font.pretendard_bold)
    val batang: Typeface = ctx.resources.getFont(R.font.gowunbatang)   // 일기와 한마디
    /** 예전 이름 (제목 글꼴) */
    val sign: Typeface get() = bold

    private val density = ctx.resources.displayMetrics.density
    val w = ctx.resources.configuration.screenWidthDp
    val h = ctx.resources.configuration.screenHeightDp
    /** 사이드바를 둘 만큼 넓은 창인지 (내비 옆 절반 창은 좁다) */
    val wide = w >= 760 && w >= h
    /** 창이 좁아서 한 줄로 쌓아야 하는지 */
    val narrow = !wide
    val scale = (if (wide) minOf(w / 1180f, h / 700f) else minOf(w / 560f, h / 820f)).coerceIn(0.72f, 1.35f)

    fun dp(v: Float) = (v * density * scale).toInt()
    fun dp(v: Int) = dp(v.toFloat())
    fun sp(v: Float) = v * scale

    fun face(weight: Int) = when { weight >= 700 -> bold; weight >= 600 -> semibold; else -> regular }
}
