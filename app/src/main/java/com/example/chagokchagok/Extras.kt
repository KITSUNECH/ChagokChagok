// 차곡車곡 – 성장 그래프와 오늘 길 퀴즈
package com.example.chagokchagok

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

/** 주차하며 앞뒤로 오간 횟수가 일기마다 어떻게 달라졌는지 보여 주는 그래프. */
class ParkChart(ctx: Context, private val ui: Ui, private val values: List<Int>) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(c: Canvas) {
        if (values.isEmpty()) return
        val w = width.toFloat(); val h = height.toFloat()
        val padX = ui.dp(34).toFloat(); val top = ui.dp(40).toFloat(); val bottom = h - ui.dp(34)
        val max = (values.maxOrNull() ?: 1).coerceAtLeast(1)
        val stepX = if (values.size > 1) (w - padX * 2) / (values.size - 1) else 0f
        fun x(i: Int) = if (values.size > 1) padX + stepX * i else w / 2
        fun y(v: Int) = bottom - (bottom - top) * v / max
        // 바닥 선
        p.style = Paint.Style.STROKE; p.color = Ink.kerb; p.strokeWidth = ui.dp(2).toFloat()
        c.drawLine(padX / 2, bottom, w - padX / 2, bottom, p)
        // 이은 선
        p.color = Ink.go; p.strokeWidth = ui.dp(4).toFloat(); p.strokeCap = Paint.Cap.ROUND
        for (i in 1 until values.size) c.drawLine(x(i - 1), y(values[i - 1]), x(i), y(values[i]), p)
        // 점과 숫자
        p.textAlign = Paint.Align.CENTER; p.typeface = ui.sign
        values.forEachIndexed { i, v ->
            p.style = Paint.Style.FILL; p.color = if (i == values.size - 1) Ink.go else Ink.asphalt
            c.drawCircle(x(i), y(v), ui.dp(8).toFloat(), p)
            p.style = Paint.Style.STROKE; p.color = Ink.go; p.strokeWidth = ui.dp(4).toFloat()
            c.drawCircle(x(i), y(v), ui.dp(8).toFloat(), p)
            p.style = Paint.Style.FILL; p.color = Ink.paint; p.textSize = ui.dp(20).toFloat()
            c.drawText("${v}번", x(i), y(v) - ui.dp(16), p)
            p.color = Ink.dim; p.textSize = ui.dp(15).toFloat()
            c.drawText(Writer.ordinal(i + 1), x(i), bottom + ui.dp(24), p)
        }
        p.typeface = null
    }
}

/** 오늘 달린 길에서 나오는 법규 문제 하나. 내용은 법령에서 가져오고 출처를 함께 보여 준다. */
data class Quiz(val question: String, val choices: List<String>, val answer: Int, val explain: String, val source: String)

object Quizzes {
    private val rain = Quiz(
        "오늘 와이퍼를 켜고 달렸어요. 비가 내려 노면이 젖어 있을 때는 최고속도에서 얼마를 줄여야 할까요?",
        listOf("줄이지 않아도 된다", "100분의 20", "100분의 50"), 1,
        "비가 내려 노면이 젖어 있으면 최고속도의 100분의 20을 줄인 속도로 달려야 합니다. 제한속도가 시속 50km인 길이라면 시속 40km입니다.",
        "도로교통법 시행규칙 제19조 제2항"
    )
    private val signal = Quiz(
        "오늘 깜빡이를 켰어요. 일반도로에서는 방향을 바꾸려는 지점의 몇 미터 전부터 깜빡이를 켜야 할까요?",
        listOf("10미터 전", "30미터 전", "100미터 전"), 1,
        "일반도로에서는 그 지점에 이르기 전 30미터 이상, 고속도로에서는 100미터 이상의 지점에서 신호를 시작해야 합니다.",
        "도로교통법 시행령 별표 2"
    )

    /** 오늘 주행에서 실제로 있었던 일에 맞는 문제를 고른다. 해당하는 일이 없으면 문제를 내지 않는다. */
    fun pick(d: Drive): Quiz? = when {
        d.rained -> rain
        d.signals > 0 -> signal
        else -> null
    }
}
