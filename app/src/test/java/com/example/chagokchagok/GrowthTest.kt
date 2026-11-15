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
        assertTrue(j.top(1).first().metric.key in setOf("signal", "lanelead"))   // 습관 지표가 판정을 이끈다
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

    @Test fun rushingTheLaneChangeIsWorse() {
        // 기다린 시간이 줄어도 늘었다고 하지 않는다. 깜빡이를 켜고 3초가 안 돼 넘어가면 오히려 나빠진 것이다.
        val hist = listOf(drive(1, 4, 3, 20, 5f), drive(2, 4, 3, 20, 5f))
        val today = drive(3, 4, 3, 20, 1f)
        val j = Growth.judge(hist + today, today)!!
        assertEquals(0f, j.changes.first { it.metric.key == "lane" }.weight, .001f)   // 기다린 시간은 참고만
        assertTrue(j.changes.first { it.metric.key == "lanelead" }.score < 0f)
        assertEquals(Verdict.DOWN, j.verdict)
    }

    @Test fun waitingLongerInTrafficIsNotWorse() {
        // 옆 차로에 차가 많아 오래 기다린 날: 깜빡이는 충분히 먼저 켰으니 나빠졌다고 하지 않는다
        val hist = listOf(drive(1, 4, 3, 20, 5f), drive(2, 4, 3, 20, 5f))
        val today = drive(3, 4, 3, 20, 25f)
        assertEquals(Verdict.SAME, Growth.judge(hist + today, today)!!.verdict)
    }

    @Test fun shownChangeMatchesShownNumbers() {
        // 화면에 58% → 100%로 보이면 괄호 안도 +42%p여야 한다
        val m = Metrics.all.first { it.key == "signal" }
        assertEquals("58% → 100% (+42%p)", Change(m, 58.33f, 100f, .4f, 3f).text())
        val lane = Metrics.all.first { it.key == "lane" }
        assertEquals("13초 → 6초 (−54%)", Change(lane, 13.4f, 6f, .5f, 3f).text())
    }

    @Test fun differentConditionHalvesTheWeight() {
        val hist = listOf(drive(1, 4, 3, 20, 8f, hour = 8))
        val today = drive(2, 4, 3, 20, 8f, rain = true, hour = 22)
        val j = Growth.judge(hist + today, today)!!
        assertTrue(j.condDiffers)
        assertEquals(0f, j.changes.first { it.metric.key == "brake" }.weight, .001f)   // 브레이크 횟수는 참고만
    }

    /** 이름이 같아도 멀리 떨어진 곳은 같은 곳으로 보지 않는다 */
    @Test fun sameNameFarAwayIsNotTheSamePlace() {
        fun at(lat: Double, lng: Double) = Drive("나", 0L, 3f, 10, 600, emptyList(), 0, 0, false, false, 0, 0, 0, place = "회사", placeLat = lat, placeLng = lng)
        org.junit.Assert.assertTrue(samePlace(at(37.5000, 127.0000), at(37.5010, 127.0005)))
        org.junit.Assert.assertFalse(samePlace(at(37.5000, 127.0000), at(35.1796, 129.0756)))
    }

    /** 깜빡이만 보고 추정한 차선 변경은 '3초 전 깜빡이' 판정에 넣지 않는다 */
    @Test fun guessedLaneChangeIsNotScored() {
        val guessed = Drive("나", 0L, 3f, 10, 600, listOf(Ev(.5f, Evt.LANE, LaneMove("RIGHT", 6f, true, false, false, false).encode())), 0, 0, false, false, 0, 0, 0)
        org.junit.Assert.assertNull(Metrics.all.first { it.key == "lanelead" }.value(guessed))
    }
}
