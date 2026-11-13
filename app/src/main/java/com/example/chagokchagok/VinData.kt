// 차곡車곡 – 차대번호 제조사 식별
// 앞 세 자리(WMI)만 쓴다. 근거: NHTSA vPIC 제조사 식별부호 목록.
// 국내 판매 차의 차종 부호표는 공개 자료로 확인하지 못해, 차종은 운전자가 고른다.
package com.example.chagokchagok

internal object VinData {
    private val wmi = mapOf(
        "KMH" to "현대자동차 승용", "KMT" to "제네시스", "KM8" to "현대자동차 SUV·MPV",
        "KMU" to "현대자동차 SUV·MPV", "KMF" to "현대자동차 상용", "KPH" to "현대자동차 승용",
    )
    fun maker(prefix: String?): String? = prefix?.let { wmi[it] }
}
