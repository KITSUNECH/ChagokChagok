// 차곡車곡 – 법규 데이터
// 확인 방법: 법제처 「찾기쉬운 생활법령정보」(easylaw.go.kr, 2026-10-15 기준)와 정책브리핑(korea.kr, 경찰청·법제처 보도자료).
// 위반 시 금액은 승용차 기준. 생활법령정보는 유권해석이 아니므로 출시 전 국가법령정보센터 원문과 대조한다.
package com.example.chagokchagok

internal object LawData {
    private const val EASY_PHONE = "https://easylaw.go.kr/CSP/CnpClsMain.laf?csmSeq=684&ccfNo=2&cciNo=1&cnpClsNo=5"

    val changes = listOf(
        Law("2014-02-14", "운전 중 화면 조작 금지", "달리는 동안 화면을 만지거나 영상을 보지 않아요. 지도·교통 안내만 볼 수 있고, 조작은 멈췄을 때만.", "범칙금 6만 원, 벌점 15점", "도로교통법 제49조 제1항 제11호·제11호의2", "https://m.easylaw.go.kr/MOB/NtcInfoRetrieve.laf?ntcSeq=566"),
        Law("2018-09-28", "모든 자리 안전띠", "고속도로뿐 아니라 모든 도로에서 뒷자리까지 매요.", "동승자 미착용 시 운전자 과태료 3만 원 (13세 미만 6만 원)", "도로교통법 제50조 제1항", "https://m.korea.kr/news/policyNewsView.do?newsId=148854211"),
        Law("2019-06-25", "음주운전 0.03%부터", "소주 한 잔으로도 정지될 수 있어요. 0.08%부터는 취소예요.", "0.03~0.08%: 벌점 100, 1년 이하 징역 또는 500만 원 이하 벌금", "도로교통법 제44조 제4항, 제148조의2", "https://www.korea.kr/news/policyNewsView.do?newsId=148861933"),
        Law("2020-03-25", "어린이 보호구역 30", "스쿨존은 시속 30km. 어린이를 다치게 하면 무겁게 처벌돼요.", "상해 1~15년 징역 또는 500만~3천만 원 벌금", "특정범죄가중처벌법 제5조의13 (민식이법)", "https://www.korea.kr/news/policyNewsView.do?newsId=148870845"),
        Law("2021-04-17", "시내 50, 동네 30", "도시의 일반도로는 시속 50km, 주택가·이면도로는 30km가 기본이에요.", "20km/h 이하 초과 3만 원, 20~40km/h 초과 6만 원·벌점 15", "도로교통법 시행규칙 제19조 (안전속도 5030)", "https://www.korea.kr/briefing/pressReleaseView.do?newsId=156446612"),
        Law("2021-05-11", "스쿨존 주정차 3배", "어린이 보호구역에는 잠깐도 세우지 않아요.", "과태료 12만 원 (같은 곳 2시간 넘으면 13만 원)", "도로교통법 시행령 별표 6", "https://www.korea.kr/briefing/pressReleaseView.do?newsId=156448595"),
        Law("2022-07-12", "건너려는 사람이 있어도 멈춤", "횡단보도를 건너는 사람뿐 아니라 건너려는 사람이 있어도 일시정지해요.", "범칙금 6만 원, 벌점 10점", "도로교통법 제27조 제1항", "https://m.easylaw.go.kr/MOB/NtcInfoRetrieve.laf?ntcSeq=1333"),
        Law("2022-07-12", "스쿨존 신호 없는 횡단보도는 무조건 멈춤", "어린이 보호구역의 신호등 없는 횡단보도에서는 사람이 없어도 일단 멈춰요.", "범칙금 6만 원, 벌점 10점 (스쿨존 가중)", "도로교통법 제27조 제7항", "https://m.easylaw.go.kr/MOB/NtcInfoRetrieve.laf?ntcSeq=1333"),
        Law("2023-01-22", "빨간불 우회전은 멈춘 뒤", "앞 신호가 빨간불이면 정지선에서 완전히 멈춘 뒤 우회전해요.", "범칙금 6만 원, 벌점 15점", "도로교통법 시행규칙 별표 2", "https://www.korea.kr/news/policyNewsView.do?newsId=148914126"),
        Law("2023-01-22", "우회전 신호등은 초록 화살표에만", "우회전 신호등이 있는 곳은 초록 화살표가 켜졌을 때만 돌아요.", "범칙금 6만 원, 벌점 15점", "도로교통법 시행규칙 별표 2", "https://www.korea.kr/news/policyNewsView.do?newsId=148914126"),
        Law("2025-06-04", "단속 뒤 술 더 마시기도 처벌", "단속을 피하려고 술을 더 마시면 측정 거부처럼 처벌돼요.", "1~5년 징역 또는 500만~2천만 원 벌금", "도로교통법 제44조 제5항 (음주측정 방해)", "https://m.korea.kr/news/customizedNewsView.do?newsId=148938732"),
        Law("2026-01-01", "면허 갱신은 생일 앞뒤 6개월", "갱신 기간이 생일을 기준으로 바뀌었어요. 장롱면허라면 갱신 날짜부터 확인해요.", "기간을 넘기면 과태료, 적성검사 1년 넘기면 면허 취소", "도로교통법 제87조", "https://www.korea.kr/news/policyNewsView.do?newsId=148957500"),
        Law("2026-03-19", "2종에서 1종 전환은 운전 경력 필요", "장롱면허는 7년 무사고만으로 1종으로 바뀌지 않아요. 실제로 운전한 증빙이 필요해요.", null, "도로교통법 제87조 (운전경력 증빙)", "https://www.korea.kr/news/policyNewsView.do?newsId=148957331"),
        Law("2026-04-02", "약물운전 처벌 강화", "졸음을 부르는 약을 먹었으면 운전하지 않아요. 약물 측정을 거부해도 처벌돼요.", "5년 이하 징역 또는 2천만 원 이하 벌금, 면허 취소", "도로교통법 제45조, 제148조의2", "https://www.korea.kr/news/policyNewsView.do?newsId=148960914"),
    )

    /** 면허를 언제 땄든 초보·복귀 운전자가 늘 알아야 할 것 */
    val core = listOf(
        Law("", "운전 중 휴대전화·화면 조작 금지", "손에 들고 통화하거나 화면을 만지지 않아요. 멈췄을 때만 조작해요. 이 앱도 달리는 동안은 말로만 알려 줘요.", "범칙금 6만 원, 벌점 15점", "도로교통법 제49조 제1항 제10호·제11호의2", EASY_PHONE),
        Law("", "벌점 40점이면 면허 정지", "처분 벌점이 40점을 넘으면 1점에 하루씩 정지돼요. 1년 121점이면 취소예요.", null, "도로교통법 시행규칙 별표 28", "https://easylaw.go.kr/CSP/CnpClsMain.laf?popMenu=ov&csmSeq=668&ccfNo=4&cciNo=1&cnpClsNo=2"),
        Law("", "초보운전자는 면허 2년까지", "처음 면허를 받고 2년이 안 됐으면 초보운전자예요. 이때 정지 처분을 받으면 특별교육을 받아야 해요.", "교육을 받지 않으면 범칙금", "도로교통법 제2조 제27호, 제73조", "https://easylaw.go.kr/CSP/CnpClsMain.laf?popMenu=ov&csmSeq=668&ccfNo=4&cciNo=1&cnpClsNo=4"),
        Law("", "고속도로 1차로는 앞지르기용", "편도 3차로 이상이면 1차로는 앞지를 때만 써요. 갓길로 달리지 않아요.", "범칙금 4만 원, 벌점 10점", "도로교통법 제60조, 시행규칙 별표 9", "https://easylaw.go.kr/CSP/CnpClsMain.laf?popMenu=ov&csmSeq=684&ccfNo=2&cciNo=3&cnpClsNo=1"),
        Law("", "어린이 통학버스가 서 있으면", "점멸등을 켜고 선 통학버스 옆 차로에서는 일단 멈췄다가 천천히 지나요. 앞지르지 않아요.", "범칙금 9만 원, 벌점 30점", "도로교통법 제51조", "https://easylaw.go.kr/CSP/CnpClsMain.laf?popMenu=ov&csmSeq=684&ccfNo=2&cciNo=1&cnpClsNo=5"),
    )
}
