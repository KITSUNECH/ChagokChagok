package com.example.chagokchagok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** 주행 한 번의 규칙: 한마디는 남긴 자리 근처에서 진행 방향일 때만, 끝까지 달렸는지, 거리 계산, 이어 쓰기 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TripTest {
    private val said = mutableListOf<String>()
    private var ended: Diary? = null
    private lateinit var store: Store
    private lateinit var trip: Trip

    private val host = object : TripHost {
        override fun speak(text: String) { said.add(text) }
        override fun say(text: String) { said.add(text) }
        override fun chime() {}
        override fun tripStarted() {}
        override fun tripChanged() {}
        override fun tripEnded(d: Diary) { ended = d }
        override fun diaryUpdated(d: Diary) {}
        override fun lampsChanged() {}
    }

    @Before fun setUp() {
        Car.offline = true
        val ctx = RuntimeEnvironment.getApplication()
        store = Store(ctx).apply { clearAll(); setDrivers(listOf("나", "아빠")); driver = "나"; weatherOn = false; setLocationConsent("나", true); addSampleGuide() }
        val car = Car(ctx, object : Car.Listener { override fun onCarEvent(type: String, value: String) {} })
        trip = Trip(store, car, host)
        trip.guide = store.guide("sample")
        trip.start(); said.clear()
    }

    private fun tip0() = trip.guide!!.tips[0]

    @Test fun tipPlaysOnlyNearTheSpot() {
        val t = tip0(); val tl = t.lat!!; val tg = t.lng!!
        // 2km 떨어진 곳: 들려주지 않는다
        trip.onLocation(tl + 0.018, tg, null)
        assertTrue(trip.heard.isEmpty())
        // 80m 앞, 그쪽을 향해 가는 중: 들려준다
        val lat = tl - 0.00072
        trip.onLocation(lat, tg, Places.bearing(lat, tg, tl, tg).toFloat())
        assertEquals(setOf(0), trip.heard)
        assertTrue(said.any { it.contains(t.text) })
    }

    @Test fun tipSkippedWhenDrivingAway() {
        val t = tip0(); val tl = t.lat!!; val tg = t.lng!!
        val lat = tl - 0.0011   // 120m
        val away = (Places.bearing(lat, tg, tl, tg) + 180) % 360
        trip.onLocation(lat, tg, away.toFloat())
        assertTrue("반대 방향이면 들려주지 않는다", trip.heard.isEmpty())
    }

    @Test fun tipNotPlayedWhenLocationOff() {
        store.locationOn = false
        val t = tip0()
        trip.onLocation(t.lat!!, t.lng!!, null)
        assertTrue(trip.heard.isEmpty())
    }

    @Test fun stoppingEarlyIsNotCompletedAndPathGivesDistance() {
        val pts = SampleRoute.points.take(6)
        pts.forEach { trip.onLocation(it.first, it.second, null) }
        trip.end()
        val d = ended!!
        assertFalse("받은 길의 90%를 못 달렸고 도착하지 않았다", d.drive.completed)
        assertTrue("주행거리 신호가 없으면 위치 경로로 거리를 잰다", d.drive.distKm > 0.5f)
        assertEquals(null, store.tripSnapshot)
    }

    @Test fun arrivalCompletes() {
        trip.onArrived(); trip.end()
        assertTrue(ended!!.drive.completed)
    }

    @Test fun resumesAfterRestart() {
        trip.onCar(Evt.SIGNAL, "LEFT"); trip.save(force = true)
        val ctx = RuntimeEnvironment.getApplication()
        val again = Trip(store, Car(ctx, object : Car.Listener { override fun onCarEvent(type: String, value: String) {} }), host)
        assertTrue(again.resume { store.guide(it) })
        assertTrue(again.active)
        assertEquals("sample", again.guide?.id)
        again.end()
        assertEquals(1, ended!!.drive.signals)
    }
}
