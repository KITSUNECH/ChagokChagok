package com.example.chagokchagok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LaneTest {
    private var t = 0L
    private fun watch(): Pair<LaneWatch, MutableList<LaneMove>> {
        val got = mutableListOf<LaneMove>(); val w = LaneWatch { t }
        w.onLane = { l, _ -> got.add(l) }
        return w to got
    }

    @Test fun waitUntilSteering() {
        val (w, got) = watch(); t = 0
        w.steer(0f)
        w.signal("LEFT", turnNear = false, routeActive = false, rain = true, dark = false, fix = null)
        t = 7_000; w.steer(5f); t = 9_000; w.steer(0f)
        w.signal("OFF", false, false, false, false, null)
        assertEquals(1, got.size); assertEquals(7f, got[0].wait, .01f)
        assertTrue(got[0].moved); assertTrue(got[0].rain); assertTrue(got[0].steerKnown)
        assertEquals(got[0], LaneMove.decode(got[0].encode()))
    }

    @Test fun gaveUp() {
        val (w, got) = watch(); t = 0
        w.steer(0f); w.signal("RIGHT", false, false, false, true, null)
        t = 12_000; w.signal("OFF", false, false, false, false, null)
        assertFalse(got.single().moved); assertEquals(12f, got.single().wait, .01f)
    }

    @Test fun turnIsNotLaneChange() {
        val (w, got) = watch(); t = 0
        w.steer(0f); w.signal("RIGHT", false, false, false, false, null)
        t = 3_000; w.steer(10f); w.steer(90f)   // 교차로 회전
        t = 6_000; w.signal("OFF", false, false, false, false, null)
        assertTrue(got.isEmpty())
        // 내비가 150m 안 회전을 안내 중이면 처음부터 보지 않는다
        w.signal("LEFT", turnNear = true, routeActive = true, rain = false, dark = false, fix = null)
        t = 9_000; w.signal("OFF", false, false, false, false, null)
        assertTrue(got.isEmpty())
    }

    @Test fun withoutSteeringOnlyDuringRoute() {
        val (w, got) = watch(); t = 0
        w.signal("LEFT", false, routeActive = false, rain = false, dark = false, fix = null)
        t = 5_000; w.signal("OFF", false, false, false, false, null)
        assertTrue(got.isEmpty())
        w.signal("LEFT", false, routeActive = true, rain = false, dark = false, fix = null)
        t = 11_000; w.signal("OFF", false, false, false, false, null)
        assertFalse(got.single().steerKnown); assertEquals(6f, got.single().wait, .01f)
        assertNull(LaneMove.decode("garbage"))
    }
}
