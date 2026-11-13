// 차곡車곡 – 차대번호 해석 (조사 결과로 채움)
package com.example.chagokchagok

internal object VinData {
    /** 차대번호 → (차종 이름, 변속 방식 id). 확실한 것만 넣고 나머지는 운전자가 고른다. */
    fun decode(v: String): Pair<String, String>? = null
}
