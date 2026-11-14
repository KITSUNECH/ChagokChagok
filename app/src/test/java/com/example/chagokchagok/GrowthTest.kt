package com.example.chagokchagok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class GrowthTest {
    private fun at(day: Int, hour: Int) = Calendar.getInstance().apply { set(2026, 10, day, hour, 10, 0) }.timeInMillis
    private fun drive(day: Int, turns: Int, signaled: Int, brakes: Int, laneWait: Float?, rain: Boolean = false, hour: Int = 8, completed: Boolean = true, sim: Boolean = false): Diary {
        val ev = laneWait?.let { listOf(Ev(.5f, Evt.LANE, LaneMove("LEFT", it, true, true, rain, false).encode())) } ?: emptyList()
        val d = Drive("나", at(day, hour), 10f, 25, 1500, ev, signaled, brakes, rain, false, 0, 0, 3, turns = turns, turnsSignaled = signaled, place = "회사", completed = completed, sim = sim)
        return Diary("d$day", "나", d.date, "회사", "", "", null, d, null)
    }

    @Test fun firstVisitHasNoJudgement() {
        val a = drive(1, 4, 2, 20, 15f)
        assertNull(Growth.judge(listOf(a), a))
    }

    @Test fun habitsImproveMeansUp() {
        val hist = listOf(drive(1, 4, 2, 20, 15f), drive(2, 4, 2, 22, 14f), drive(3, 4, 2, 21, 15f))
        val today = drive(9, 4, 4, 21, 6f)
        val j = Growth.judge(hist + today, today)!!
        assertEquals(Verdict.UP, j.verdict)
        assertEquals("보통", j.confidence)
        assertTrue(j.top(1).first().metric.key in setOf("signal", "lane"))   // 습관 지표가 판정을 이끈다
        assertTrue(Growth.bothUp(j, "더 편했어요"))
        assertTrue(Growth.combined(j, "더 긴장됐어요").startsWith("차는 늘었다고 봐요"))
    }

    @Test fun trafficOnlyChangeIsNotGrowth() {
        // 습관은 그대로, 브레이크만 줄었다 (길이 덜 막힌 날) → 늘었다고 말하지 않는다
        val hist = listOf(drive(1, 4, 3, 30, 8f))
        val today = drive(2, 4, 3, 10, 8f)
        assertEquals(Verdict.SAME, Growth.judge(hist + today, today)!!.verdict)
    }

    @Test fun simAndIncompleteAreIgnored() {
        val hist = listOf(drive(1, 4, 0, 20, 15f, sim = true), drive(2, 4, 0, 20, 15f, completed = false))
        val today = drive(3, 4, 4, 20, 5f)
        assertNull(Growth.judge(hist + today, today))
    }

    @Test fun differentConditionHalvesTheWeight() {
        val hist = listOf(drive(1, 4, 3, 20, 8f, hour = 8))
        val today = drive(2, 4, 3, 20, 8f, rain = true, hour = 22)
        val j = Growth.judge(hist + today, today)!!
        assertTrue(j.condDiffers)
        assertEquals(.5f, j.changes.first { it.metric.key == "brake" }.weight, .001f)
    }
}
