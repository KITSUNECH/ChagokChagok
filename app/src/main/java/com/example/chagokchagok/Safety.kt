// 차곡車곡 – 이 차의 안전 조작
// 출발 준비의 "이 차의 기어와 안전"에서 보여 준다 (주차 중에만 열리는 화면).
// 근거: 현대자동차 사용설명서(ownersmanual.hyundai.com), 한국교통안전공단, 법제처 생활법령정보, 정책브리핑.
// 공식 문구를 찾지 못한 것(예: "양발로 밟기", "기어 N")은 넣지 않는다.
package com.example.chagokchagok

import android.view.Gravity
import android.view.View

internal data class SafetyItem(
    val id: String,
    val title: String,
    val whenText: String,
    val steps: List<String>,
    val caution: String,
    val scope: String,          // all · epb · sbw · button · hold · tpms
    val source: String,
    val url: String,
    val emergency: Boolean = false,
    val icon: String = "warning",
)

internal object Safety {
    private const val M = "https://ownersmanual.hyundai.com/full_webhelp"
    val items = listOf(
        SafetyItem("surge", "차가 혼자 빨라질 때", "의도하지 않은 급가속",
            listOf("브레이크를 강하게, 끝까지 계속 밟아요", "전자식 주차 브레이크(EPB) 스위치를 당긴 채 놓지 않아요", "비상등을 켜고 안전한 곳에 세워요"),
            "EPB는 당기고 있는 동안만 제동되고, 제동 거리가 길어질 수 있어요. 비상시에만 써요.", "epb",
            "한국교통안전공단 · 현대자동차 사용설명서(EPB 비상 제동)", "https://main.kotsa.or.kr/portal/bbs/report_view.do?menuCode=05010200&bbscCode=report&bbscSeqn=18534", emergency = true, icon = "warning"),
        SafetyItem("engine_off", "달리는 중에 시동 끄기", "브레이크로도 멈추지 않는 마지막 수단",
            listOf("시동 버튼을 2초 넘게 길게 눌러요", "또는 시동 버튼을 빠르게 세 번 넘게 눌러요"),
            "시동이 꺼지면 운전대와 브레이크가 무거워져요. 더 세게 밟아 세워요.", "button",
            "현대자동차 사용설명서 · 시동 버튼 (쏘나타 DN8 2026, 투싼 NX4 2025)", "$M/DN8/2026/ko_KR/id60802dd3189.html", emergency = true, icon = "stop"),
        SafetyItem("epb", "주차 브레이크 걸고 풀기", "주차할 때 · 출발할 때",
            listOf("걸기: 브레이크를 밟고 EPB 스위치를 당겨요", "풀기: 브레이크를 밟고 스위치를 눌러요", "안전벨트를 매고 문을 닫은 채 D에서 가속 페달을 밟아도 풀려요"),
            "계기판의 주차 브레이크 경고등이 꺼졌는지 보고 출발해요.", "epb",
            "현대자동차 사용설명서 · 전자식 파킹 브레이크 (그랜저 GN7 2026)", "$M/GN7/2026/ko_KR/id76e01aea2d0.html", icon = "stop"),
        SafetyItem("autohold", "오토 홀드", "신호를 기다릴 때",
            listOf("문을 닫고 브레이크를 밟은 채 AUTO HOLD를 눌러요", "켜지면 발을 떼도 차가 서 있어요 (초록 표시)", "끌 때도 브레이크를 밟고 눌러요"),
            "내리막이나 앞차가 가까울 때 출발은 직접 풀어서 해요. 문을 열거나 10분 넘게 서 있으면 주차 브레이크로 바뀌어요.", "hold",
            "현대자동차 사용설명서 · 오토 홀드 (그랜저 GN7 2026)", "$M/GN7/2026/ko_KR/id390901fe6c7.html", icon = "check"),
        SafetyItem("auto_p", "저절로 P가 될 때", "시동을 끄거나 운전석 문을 열 때",
            listOf("R·D·N에서 시동을 끄면 저절로 P가 돼요", "멈춘 채 운전석 문을 열어도 P가 돼요"),
            "돕는 기능일 뿐이에요. 내리기 전에 계기판의 P 표시를 직접 확인해요.", "sbw",
            "현대자동차 사용설명서 · 전자식 변속 (아이오닉 5 2025)", "$M/NE1/2025/ko_KR/iddf661a37926.html", icon = "gear"),
        SafetyItem("n_hold", "N에 두고 내리기 (이중 주차)", "다른 사람이 차를 밀 수 있게 둘 때",
            listOf("EPB와 오토 홀드를 꺼요", "브레이크를 밟고 N에 놓아요", "운전대의 OK 버튼을 길게 누르고 안내가 뜨면 시동을 꺼요"),
            "3분 안에 운전석 문을 열면 P로 바뀌어요. 오래 두면 배터리가 닳아요.", "sbw",
            "현대자동차 사용설명서 · N단 유지 (아이오닉 5 2025)", "$M/NE1/2025/ko_KR/iddf661a37926.html", icon = "gear"),
        SafetyItem("breakdown", "도로에서 멈췄을 때", "고장이나 사고",
            listOf("비상등을 켜고 갓길로 옮겨요", "트렁크를 열고 뒤차가 볼 수 있는 곳에 안전삼각대를 세워요", "가드레일 밖으로 피한 뒤 신고해요 (고속도로 1588-2504)"),
            "밤에는 섬광신호나 불꽃신호를 더해요. 삼각대를 안 세우면 범칙금 4만 원이에요.", "all",
            "도로교통법 제66조 · 법제처 생활법령정보 · 정책브리핑", "https://easylaw.go.kr/CSP/CnpClsMain.laf?popMenu=ov&csmSeq=684&ccfNo=4&cciNo=1&cnpClsNo=1", icon = "warning"),
        SafetyItem("tpms", "타이어 공기압 경고등", "노란 경고등이 켜졌을 때",
            listOf("바로 속도를 줄이고 급한 커브를 피해요", "가까운 정비소에서 공기압을 맞춰요"),
            "추운 날에는 정상이어도 켜질 수 있어요. 1분쯤 깜빡인 뒤 켜지면 장치 점검이 필요해요.", "all",
            "현대자동차 사용설명서 · 타이어 공기압 (아이오닉 5 2025)", "$M/NE1/2025/ko_KR/id23C5E0QD0YK.html", icon = "warning"),
        SafetyItem("start", "시동 걸기", "출발 전",
            listOf("P나 N에서 브레이크를 밟아요", "브레이크를 밟은 채 시동 버튼을 눌러요"),
            "브레이크를 밟지 않으면 시동이 걸리지 않아요.", "button",
            "현대자동차 사용설명서 · 시동 걸기 (그랜저 GN7 2026)", "$M/GN7/2026/ko_KR/id0c65f0a6e61.html", icon = "play"),
        SafetyItem("door", "내릴 때 뒤 확인", "문을 열기 전",
            listOf("문에서 먼 쪽 손으로 손잡이를 잡아요", "몸이 돌아가며 뒤에서 오는 자전거·오토바이를 봐요"),
            "안전을 확인하지 않고 문을 열면 안 돼요.", "all",
            "정책브리핑 (더치 리치)", "https://www.korea.kr/news/policyNewsView.do?newsId=148838162", icon = "eye"),
    )

    /** 이 차에 맞는 항목. 변속 방식을 모르면 전부 보여 준다. */
    fun forShifter(sh: Shifter?): List<SafetyItem> = items.filter { item ->
        when (item.scope) {
            "sbw" -> sh == null || sh.id != "lever"
            "button" -> true   // 요즘 현대차는 대부분 버튼 시동
            else -> true
        }
    }
}

/** 출발 준비: 안전 조작 카드들 (위급 상황 둘이 맨 앞, 강조) */
internal fun MainActivity.safetySection(sh: Shifter?): View {
    val k = kit
    val list = Safety.forShifter(sh)
    fun card(it: SafetyItem): View = k.card(pad = 20, color = if (it.emergency) Ink.alpha(Ink.danger, if (Ink.night) .16f else .07f) else Ink.surface) {
        addView(k.row(10).apply {
            addView(k.icon(it.icon, if (it.emergency) Ink.danger else Ink.accent, 22))
            addView(k.col(0).apply { addView(k.text(it.title, Type.Headline)); addView(k.text(it.whenText, Type.Caption, Ink.text2)) }, k.lp(0, k.WRAP, 1f))
            addView(k.iconButton("speaker", Ink.accent, Ink.accentSoft, 40) { speak(it.title + ". " + it.steps.joinToString(". ") + ". " + it.caution) })
        })
        addView(k.gap(10))
        it.steps.forEachIndexed { i, s ->
            addView(k.row(10).apply {
                gravity = Gravity.TOP
                addView(k.text("${i + 1}", Type.BodyStrong, if (it.emergency) Ink.danger else Ink.accent), k.lp(ui.dp(18), k.WRAP))
                addView(k.text(s, Type.Body), k.lp(0, k.WRAP, 1f))
            }); addView(k.gap(4))
        }
        addView(k.gap(6))
        addView(k.row(8).apply { gravity = Gravity.TOP; addView(k.icon("info", Ink.text2, 16)); addView(k.text(it.caution, Type.Sub, Ink.text2), k.lp(0, k.WRAP, 1f)) })
        addView(k.fill())
        addView(k.gap(8))
        addView(k.text("근거 · ${it.source}", Type.Caption, Ink.text3))
    }
    return k.col(14).apply {
        addView(k.row().apply {
            addView(k.text("안전 조작 ${list.size}가지", Type.Headline), k.lp(0, k.WRAP, 1f))
            addView(k.text("주차 중에 미리 읽어 두세요 · 정확한 조작은 이 차의 사용설명서", Type.Caption, Ink.text2))
        })
        addView(k.grid(if (ui.wide) 2 else 1, list.map { card(it) }, 14))
    }
}
