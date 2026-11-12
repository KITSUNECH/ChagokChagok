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

/** 읽을거리 한 장. source 가 있으면 화면에 출처로 보여 준다. */
data class Note(val title: String, val body: String, val source: String = "")

object Notes {
    /** 오랜만에 운전하는 사람이 놓치기 쉬운, 그동안 바뀐 법규. */
    val laws = listOf(
        Note("빨간불에 우회전", "앞 신호가 빨간불이면 정지선 앞에서 완전히 멈춘 다음에 우회전합니다. 서행으로 지나가면 안 됩니다.", "도로교통법 시행규칙 별표 2 (2023년 1월 22일 시행)"),
        Note("건너려는 사람이 있어도 멈춤", "횡단보도를 건너고 있는 사람뿐 아니라 건너려고 하는 사람이 있을 때도 일시정지합니다.", "도로교통법 제27조 제1항 (2022년 7월 12일 시행)"),
        Note("어린이 보호구역 횡단보도", "어린이 보호구역 안의 신호등 없는 횡단보도에서는 사람이 없어도 일단 멈춥니다.", "도로교통법 제27조 제7항 (2022년 7월 12일 시행)"),
        Note("시내 제한속도 50", "도시 지역의 일반도로는 기본 제한속도가 시속 50km입니다. 표지판이 있으면 표지판을 따릅니다.", "도로교통법 시행규칙 제19조 (2021년 4월 17일 시행)"),
        Note("모든 자리 안전띠", "뒷자리를 포함해 모든 자리에서 안전띠를 매야 합니다.", "도로교통법 제50조 (2018년 9월 28일 시행)")
    )

    /** 계기판 경고등을 색으로 읽는 법. */
    val lamps = listOf(
        Note("빨간색", "지금 위험하다는 뜻입니다. 안전한 곳에 차를 세우고 확인합니다. 엔진오일 압력, 냉각수 온도, 브레이크, 충전 경고등이 여기에 듭니다."),
        Note("노란색", "당장 멈출 일은 아니지만 가까운 날에 점검하라는 뜻입니다. 엔진 점검, 타이어 공기압, 연료 부족, 워셔액 부족이 여기에 듭니다."),
        Note("초록색과 파란색", "고장이 아니라 지금 켜져 있다는 표시입니다. 방향지시등, 전조등, 상향등, 크루즈가 여기에 듭니다.")
    )

    /** 면허시험장 차와 요즘 차가 다른 곳. 차마다 다르므로 직접 해 보도록 이끈다. */
    val newCar = listOf(
        Note("기어", "막대를 당기는 대신 버튼, 다이얼, 운전대 옆 레버로 바꾸는 차가 많습니다. P는 따로 버튼인 경우가 있습니다. 출발 준비에서 R과 P를 직접 넣어 보세요."),
        Note("주차 브레이크", "손으로 당기거나 발로 밟는 대신 작은 스위치로 겁니다. P에 놓으면 스스로 걸리는 차도 있습니다."),
        Note("오토 홀드", "켜 두면 신호 대기 때 브레이크에서 발을 떼도 차가 서 있습니다. 가속 페달을 밟으면 풀립니다."),
        Note("시동", "열쇠를 돌리지 않고 브레이크를 밟은 채 버튼을 누릅니다.")
    )
}
