package com.example.chagokchagok

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WriterTest {
    private fun drive(driver: String, park: Int, rain: Boolean, sig: Int = 3, longest: Int = 5) = Drive(
        driver, 1_000_000L * (park + 1), 8.5f, 31, 1860,
        listOf(Ev(.2f, Evt.SIGNAL, "LEFT"), Ev(.3f, Evt.WIPER, "on"), Ev(.7f, Evt.WIPER, "off"), Ev(.5f, Evt.BRAKE, "on")),
        sig, 9, rain, false, park, 0, longest
    )

    @Test fun recorderCountsParkingAndSignals() {
        val r = Recorder(); r.start(100f)
        r.add(Evt.GEAR, "D"); r.add(Evt.SIGNAL, "LEFT"); r.add(Evt.SIGNAL, "OFF"); r.add(Evt.BRAKE, "on"); r.add(Evt.BRAKE, "off")
        r.add(Evt.WIPER, "on"); r.distance(104.2f)
        r.add(Evt.GEAR, "R"); r.add(Evt.GEAR, "D"); r.add(Evt.GEAR, "R")
        val d = r.finish("나")
        assertEquals(1, d.signals); assertEquals(1, d.brakes); assertEquals(3, d.parkMoves)
        // 와이퍼를 잠깐 켠 것은 세척일 수 있어 비 온 날로 치지 않는다 (1분 넘게 켜야 비)
        assertTrue(!d.rained)
        assertEquals(4.2f, d.distKm, 0.01f)
        println("DRIVE " + d)
    }

    @Test fun diaryReadsNaturally() {
        val dad = drive("아빠", 0, false)
        val guide = Guide("g", "아빠", "할머니 댁 가는 길", null, null, dad, listOf(Tip(.5f, "미리 오른쪽 차로로.")), 7)
        val first = Writer.write(drive("나", 5, false), emptyList(), null, 0)
        println("1> " + first.title + " | " + first.text + " | stamp=" + first.stamp?.replace("\n", ""))
        val second = Writer.write(drive("나", 3, true), listOf(first), guide, 1)
        println("2> " + second.title + " | " + second.text + " | hl=" + second.highlight + " | stamp=" + second.stamp?.replace("\n", ""))
        val third = Writer.write(drive("나", 2, true, longest = 30), listOf(first, second), guide, 2)
        val cruise = Writer.write(drive("나", 2, false).copy(cruise = true), listOf(first, second), null, 0)
        println("C> " + cruise.text + " | stamp=" + cruise.stamp?.replace("\n", ""))
        assertTrue(cruise.text.contains("속도를 맡겨"))
        val fourth = Writer.write(drive("나", 2, false).copy(passenger = true), listOf(first, second, third), guide, 1)
        println("G> " + fourth.text + " | stamp=" + fourth.stamp?.replace("\n", ""))
        assertEquals("졸업", fourth.stamp)
        println("3> " + third.title + " | " + third.text + " | hl=" + third.highlight)
        val reverse = Writer.write(drive("아빠", 1, false), emptyList(), guide.copy(owner = "나"), 0)
        println("4> " + reverse.text)
        assertTrue(second.text.contains(second.highlight))
        assertEquals("같은 길, 다른 자리", second.title)
    }

    @Test fun turnCoach() {
        val c = TurnCoach(); val passed = mutableListOf<Boolean>(); c.onPassed = { passed.add(it) }
        assertEquals(null, c.update("RIGHT", 150, "OFF"))
        assertEquals("RIGHT", c.update("RIGHT", 90, "OFF"))   // 가까운데 꺼져 있으면 한 번 알려 준다
        assertEquals(null, c.update("RIGHT", 60, "OFF"))      // 두 번 말하지 않는다
        c.update("RIGHT", 40, "RIGHT"); c.update(null, 0, "OFF")
        assertEquals(listOf(true), passed)
        c.update("LEFT", 120, "OFF"); c.update("LEFT", 30, "RIGHT"); c.update("LEFT", 400, "OFF") // 반대쪽 깜빡이는 치지 않는다
        assertEquals(listOf(true, false), passed)
    }
}
