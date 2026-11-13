// 차곡車곡 – 법규
// 면허를 딴 해를 알면 "그 뒤로 바뀐 것"만 골라 보여 준다. 모든 항목에 법령 이름과 시행일, 출처 주소를 붙인다.
// 문장은 운전자가 바로 알아듣게 쓰고, 정확한 조문은 출처에서 확인하게 한다.
package com.example.chagokchagok

import java.util.Calendar

internal data class Law(
    val date: String,          // 시행일 YYYY-MM-DD
    val title: String,
    val line: String,          // 운전자에게 하는 한 줄
    val penalty: String?,      // 위반하면
    val source: String,        // 법령과 조항
    val url: String,
) { val year get() = date.take(4).toInt() }

internal object Laws {
    /** 시행일 순. 조사 근거는 README 와 설계 자료에 함께 적었다. */
    val changes: List<Law> = LawData.changes.sortedBy { it.date }

    /** 면허를 언제 땄든 초보가 꼭 알아야 할 것 */
    val core: List<Law> = LawData.core

    /** 면허를 딴 해부터 지금까지 바뀐 것 (그 해 포함). 모르면 전부. */
    fun since(year: Int?): List<Law> = if (year == null) changes else changes.filter { it.year >= year }

    val thisYear: Int get() = Calendar.getInstance().get(Calendar.YEAR)
}
