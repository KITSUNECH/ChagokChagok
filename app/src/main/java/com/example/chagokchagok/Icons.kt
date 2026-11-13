// 차곡車곡 – 선 아이콘
// 24칸 격자에 선으로 그린다. 글꼴 아이콘이나 이미지 파일 없이 색과 크기를 자유롭게 바꾸려고 직접 그린다.
package com.example.chagokchagok

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.View

class IconView(ctx: Context, var name: String, var color: Int, private val weight: Float = 1.9f) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG).apply { strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }

    fun set(name: String, color: Int) { this.name = name; this.color = color; invalidate() }

    override fun onDraw(c: Canvas) {
        val s = minOf(width, height) / 24f
        c.save(); c.translate((width - s * 24) / 2, (height - s * 24) / 2); c.scale(s, s)
        p.color = color; p.strokeWidth = weight; p.style = Paint.Style.STROKE
        drawIcon(c)
        c.restore()
    }

    private fun path(block: Path.() -> Unit) = Path().apply(block)
    private fun fillOn() { p.style = Paint.Style.FILL }
    private fun strokeOn() { p.style = Paint.Style.STROKE }

    private fun drawIcon(c: Canvas) {
        when (name) {
            // 오늘: 해가 뜨는 길
            "today" -> {
                c.drawArc(RectF(6f, 7f, 18f, 19f), 180f, 180f, false, p)
                c.drawLine(3f, 13f, 21f, 13f, p)
                c.drawLine(12f, 3f, 12f, 4.5f, p); c.drawLine(4.5f, 6f, 5.6f, 7.1f, p); c.drawLine(19.5f, 6f, 18.4f, 7.1f, p)
                c.drawLine(8f, 17f, 16f, 17f, p); c.drawLine(10f, 20.5f, 14f, 20.5f, p)
            }
            // 일기장
            "book" -> {
                c.drawRoundRect(RectF(5f, 3f, 19f, 21f), 2.5f, 2.5f, p)
                c.drawLine(9f, 3f, 9f, 21f, p)
                c.drawLine(12f, 8f, 16f, 8f, p); c.drawLine(12f, 11.5f, 16f, 11.5f, p)
            }
            // 함께
            "people" -> {
                c.drawCircle(9f, 8.5f, 3.2f, p)
                c.drawPath(path { moveTo(3.5f, 19.5f); cubicTo(3.5f, 15.5f, 6f, 13.5f, 9f, 13.5f); cubicTo(12f, 13.5f, 14.5f, 15.5f, 14.5f, 19.5f) }, p)
                c.drawCircle(16.5f, 9.5f, 2.6f, p)
                c.drawPath(path { moveTo(16f, 14.2f); cubicTo(18.8f, 14.2f, 20.5f, 16f, 20.5f, 19.5f) }, p)
            }
            // 내 길
            "road" -> {
                c.drawPath(path { moveTo(6f, 21f); cubicTo(6f, 15f, 10f, 13f, 12f, 12f); cubicTo(14f, 11f, 18f, 9f, 18f, 3f) }, p)
                c.drawCircle(6f, 21f, 0.01f, p)
                c.drawCircle(18f, 5f, 2.2f, p)
                c.drawCircle(6f, 19f, 2.2f, p)
            }
            // 설정
            "settings" -> {
                c.drawCircle(12f, 12f, 3f, p)
                for (i in 0 until 8) {
                    val a = Math.toRadians(i * 45.0)
                    c.drawLine((12 + 6.2 * Math.cos(a)).toFloat(), (12 + 6.2 * Math.sin(a)).toFloat(), (12 + 8.6 * Math.cos(a)).toFloat(), (12 + 8.6 * Math.sin(a)).toFloat(), p)
                }
                c.drawCircle(12f, 12f, 6.2f, p)
            }
            "back" -> c.drawPath(path { moveTo(15f, 4.5f); lineTo(7.5f, 12f); lineTo(15f, 19.5f) }, p)
            "chevron" -> c.drawPath(path { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }, p)
            "down" -> c.drawPath(path { moveTo(5f, 9f); lineTo(12f, 16f); lineTo(19f, 9f) }, p)
            "plus" -> { c.drawLine(12f, 5f, 12f, 19f, p); c.drawLine(5f, 12f, 19f, 12f, p) }
            "minus" -> c.drawLine(5f, 12f, 19f, 12f, p)
            // 쌓인 칸 (성장)
            "layers" -> { c.drawRoundRect(RectF(4f, 15f, 20f, 20f), 1.5f, 1.5f, p); c.drawRoundRect(RectF(5.5f, 9f, 18.5f, 14f), 1.5f, 1.5f, p); c.drawRoundRect(RectF(7f, 3f, 17f, 8f), 1.5f, 1.5f, p) }
            // 잇기
            "link" -> {
                c.drawPath(path { moveTo(10.5f, 13.5f); cubicTo(9f, 12f, 9f, 10f, 10.5f, 8.5f); lineTo(13f, 6f); cubicTo(14.5f, 4.5f, 16.8f, 4.5f, 18.3f, 6f); cubicTo(19.8f, 7.5f, 19.8f, 9.8f, 18.3f, 11.3f); lineTo(17f, 12.6f) }, p)
                c.drawPath(path { moveTo(13.5f, 10.5f); cubicTo(15f, 12f, 15f, 14f, 13.5f, 15.5f); lineTo(11f, 18f); cubicTo(9.5f, 19.5f, 7.2f, 19.5f, 5.7f, 18f); cubicTo(4.2f, 16.5f, 4.2f, 14.2f, 5.7f, 12.7f); lineTo(7f, 11.4f) }, p)
            }
            // 기어 (P R N D)
            "gear" -> {
                c.drawRoundRect(RectF(7f, 3f, 17f, 21f), 5f, 5f, p)
                c.drawLine(12f, 7f, 12f, 17f, p); fillOn(); c.drawCircle(12f, 9.5f, 2.2f, p); strokeOn()
            }
            // 차트
            "chart" -> { c.drawLine(4f, 20f, 20f, 20f, p); c.drawRoundRect(RectF(6f, 12f, 9f, 20f), 1f, 1f, p); c.drawRoundRect(RectF(10.5f, 7f, 13.5f, 20f), 1f, 1f, p); c.drawRoundRect(RectF(15f, 10f, 18f, 20f), 1f, 1f, p) }
            // 비교 (지난번 ↔ 이번)
            "compare" -> { c.drawLine(4f, 8f, 18f, 8f, p); c.drawPath(path { moveTo(15f, 5f); lineTo(18f, 8f); lineTo(15f, 11f) }, p); c.drawLine(20f, 16f, 6f, 16f, p); c.drawPath(path { moveTo(9f, 13f); lineTo(6f, 16f); lineTo(9f, 19f) }, p) }
            // 내비 (화살표 커서)
            "navi" -> c.drawPath(path { moveTo(12f, 3f); lineTo(19f, 20f); lineTo(12f, 16f); lineTo(5f, 20f); close() }, p)
            // 법 (저울)
            "scale" -> {
                c.drawLine(12f, 4f, 12f, 20f, p); c.drawLine(8f, 20f, 16f, 20f, p); c.drawLine(5f, 7f, 19f, 7f, p)
                c.drawPath(path { moveTo(5f, 7f); lineTo(2.5f, 13f); cubicTo(3.5f, 14.5f, 6.5f, 14.5f, 7.5f, 13f); close() }, p)
                c.drawPath(path { moveTo(19f, 7f); lineTo(16.5f, 13f); cubicTo(17.5f, 14.5f, 20.5f, 14.5f, 21.5f, 13f); close() }, p)
            }
            "close" -> { c.drawLine(6f, 6f, 18f, 18f, p); c.drawLine(18f, 6f, 6f, 18f, p) }
            "check" -> c.drawPath(path { moveTo(4.5f, 12.5f); lineTo(9.5f, 17.5f); lineTo(19.5f, 6.5f) }, p)
            "checkCircle" -> { c.drawCircle(12f, 12f, 9f, p); c.drawPath(path { moveTo(8f, 12.3f); lineTo(11f, 15.3f); lineTo(16.5f, 9f) }, p) }
            "circle" -> c.drawCircle(12f, 12f, 9f, p)
            "dot" -> { fillOn(); c.drawCircle(12f, 12f, 5f, p); strokeOn() }
            "warning" -> {
                c.drawPath(path { moveTo(12f, 3.5f); lineTo(21.5f, 20f); lineTo(2.5f, 20f); close() }, p)
                c.drawLine(12f, 9.5f, 12f, 14f, p); fillOn(); c.drawCircle(12f, 17f, 1.1f, p); strokeOn()
            }
            "info" -> { c.drawCircle(12f, 12f, 9f, p); c.drawLine(12f, 11f, 12f, 16.5f, p); fillOn(); c.drawCircle(12f, 7.8f, 1.1f, p); strokeOn() }
            "heart", "heartFill" -> {
                val h = path { moveTo(12f, 20f); cubicTo(5f, 15f, 3f, 11.5f, 3f, 8.6f); cubicTo(3f, 6f, 5f, 4f, 7.5f, 4f); cubicTo(9.4f, 4f, 11f, 5.2f, 12f, 6.8f); cubicTo(13f, 5.2f, 14.6f, 4f, 16.5f, 4f); cubicTo(19f, 4f, 21f, 6f, 21f, 8.6f); cubicTo(21f, 11.5f, 19f, 15f, 12f, 20f); close() }
                if (name == "heartFill") { fillOn(); c.drawPath(h, p); strokeOn() } else c.drawPath(h, p)
            }
            "bookmark", "bookmarkFill" -> {
                val b = path { moveTo(6.5f, 3.5f); lineTo(17.5f, 3.5f); lineTo(17.5f, 20.5f); lineTo(12f, 16f); lineTo(6.5f, 20.5f); close() }
                if (name == "bookmarkFill") { fillOn(); c.drawPath(b, p); strokeOn() } else c.drawPath(b, p)
            }
            "pin" -> {
                c.drawPath(path { moveTo(12f, 21f); cubicTo(7f, 15.5f, 5f, 12.5f, 5f, 9.5f); cubicTo(5f, 5.6f, 8.1f, 3f, 12f, 3f); cubicTo(15.9f, 3f, 19f, 5.6f, 19f, 9.5f); cubicTo(19f, 12.5f, 17f, 15.5f, 12f, 21f); close() }, p)
                c.drawCircle(12f, 9.5f, 2.5f, p)
            }
            "flag" -> { c.drawLine(6f, 21f, 6f, 3.5f, p); c.drawPath(path { moveTo(6f, 4f); lineTo(18f, 4f); lineTo(15.5f, 8f); lineTo(18f, 12f); lineTo(6f, 12f) }, p) }
            "speaker" -> {
                c.drawPath(path { moveTo(4f, 9.5f); lineTo(8f, 9.5f); lineTo(13f, 5f); lineTo(13f, 19f); lineTo(8f, 14.5f); lineTo(4f, 14.5f); close() }, p)
                c.drawArc(RectF(12f, 8f, 18f, 16f), -55f, 110f, false, p); c.drawArc(RectF(12f, 5f, 21.5f, 19f), -55f, 110f, false, p)
            }
            "stop" -> { fillOn(); c.drawRoundRect(RectF(7f, 7f, 17f, 17f), 2f, 2f, p); strokeOn() }
            "play" -> { fillOn(); c.drawPath(path { moveTo(8f, 5f); lineTo(19f, 12f); lineTo(8f, 19f); close() }, p); strokeOn() }
            "share" -> {
                c.drawPath(path { moveTo(12f, 15f); lineTo(12f, 3.5f); moveTo(7.5f, 8f); lineTo(12f, 3.5f); lineTo(16.5f, 8f) }, p)
                c.drawPath(path { moveTo(8f, 11f); lineTo(5f, 11f); lineTo(5f, 20.5f); lineTo(19f, 20.5f); lineTo(19f, 11f); lineTo(16f, 11f) }, p)
            }
            "gift" -> {
                c.drawRoundRect(RectF(4f, 9f, 20f, 13f), 1.5f, 1.5f, p); c.drawRect(5.5f, 13f, 18.5f, 20.5f, p); c.drawLine(12f, 9f, 12f, 20.5f, p)
                c.drawPath(path { moveTo(12f, 9f); cubicTo(9f, 9f, 7f, 7.5f, 8f, 5.5f); cubicTo(9f, 4f, 11.5f, 5.5f, 12f, 9f); cubicTo(12.5f, 5.5f, 15f, 4f, 16f, 5.5f); cubicTo(17f, 7.5f, 15f, 9f, 12f, 9f) }, p)
            }
            "pencil" -> { c.drawPath(path { moveTo(4f, 20f); lineTo(5f, 15.5f); lineTo(15.5f, 5f); lineTo(19f, 8.5f); lineTo(8.5f, 19f); close() }, p); c.drawLine(13.5f, 7f, 17f, 10.5f, p) }
            "quote" -> {
                fillOn()
                c.drawCircle(7.5f, 10f, 2.6f, p); c.drawCircle(15.5f, 10f, 2.6f, p)
                strokeOn(); c.drawPath(path { moveTo(10f, 10f); cubicTo(10f, 14f, 8.5f, 16.5f, 6f, 17.5f) }, p); c.drawPath(path { moveTo(18f, 10f); cubicTo(18f, 14f, 16.5f, 16.5f, 14f, 17.5f) }, p)
            }
            "car" -> {
                c.drawPath(path { moveTo(3.5f, 16f); lineTo(3.5f, 12.5f); lineTo(6f, 7f); cubicTo(6.4f, 6.1f, 7.2f, 5.5f, 8.2f, 5.5f); lineTo(15.8f, 5.5f); cubicTo(16.8f, 5.5f, 17.6f, 6.1f, 18f, 7f); lineTo(20.5f, 12.5f); lineTo(20.5f, 16f); close() }, p)
                c.drawLine(3.5f, 12.5f, 20.5f, 12.5f, p)
                c.drawLine(5.5f, 16f, 5.5f, 18.5f, p); c.drawLine(18.5f, 16f, 18.5f, 18.5f, p)
            }
            "steering" -> {
                c.drawCircle(12f, 12f, 9f, p); c.drawCircle(12f, 12f, 2.3f, p)
                c.drawLine(3.5f, 11f, 9.8f, 11.5f, p); c.drawLine(14.2f, 11.5f, 20.5f, 11f, p); c.drawLine(12f, 14.3f, 12f, 21f, p)
            }
            "cloud" -> c.drawPath(path { moveTo(7f, 18f); cubicTo(4f, 18f, 3f, 15.8f, 3f, 14.3f); cubicTo(3f, 12f, 4.8f, 10.5f, 7f, 10.6f); cubicTo(7.6f, 7.6f, 10f, 5.5f, 13f, 5.5f); cubicTo(16.5f, 5.5f, 18.7f, 8f, 18.8f, 11f); cubicTo(20.3f, 11.3f, 21.5f, 12.8f, 21.5f, 14.5f); cubicTo(21.5f, 16.4f, 20f, 18f, 18f, 18f); close() }, p)
            "fuel" -> {
                c.drawRoundRect(RectF(4f, 4f, 13f, 20.5f), 1.6f, 1.6f, p); c.drawLine(4f, 10f, 13f, 10f, p)
                c.drawPath(path { moveTo(13f, 8f); lineTo(16.5f, 8f); lineTo(18.5f, 10f); lineTo(18.5f, 17f); cubicTo(18.5f, 18.5f, 16.5f, 18.5f, 16.5f, 17f); lineTo(16.5f, 13f); lineTo(13f, 13f) }, p)
            }
            "seat" -> {
                c.drawPath(path { moveTo(8f, 3.5f); lineTo(10f, 13f); lineTo(17f, 13f); lineTo(18f, 17f); lineTo(9f, 17f); lineTo(6f, 4f) }, p)
                c.drawLine(10f, 17f, 9f, 21f, p); c.drawLine(16f, 17f, 17f, 21f, p)
            }
            "moon" -> c.drawPath(path { moveTo(19f, 14.5f); cubicTo(17.9f, 15f, 16.7f, 15.3f, 15.4f, 15.3f); cubicTo(11.2f, 15.3f, 7.8f, 11.9f, 7.8f, 7.7f); cubicTo(7.8f, 6.3f, 8.2f, 5f, 8.9f, 3.9f); cubicTo(5.5f, 5f, 3f, 8.2f, 3f, 12f); cubicTo(3f, 16.7f, 6.8f, 20.5f, 11.5f, 20.5f); cubicTo(15f, 20.5f, 18f, 18f, 19f, 14.5f); close() }, p)
            "sparkle" -> c.drawPath(path { moveTo(12f, 3f); cubicTo(12.6f, 8.5f, 14.5f, 10.4f, 20f, 11f); cubicTo(14.5f, 11.6f, 12.6f, 13.5f, 12f, 19f); cubicTo(11.4f, 13.5f, 9.5f, 11.6f, 4f, 11f); cubicTo(9.5f, 10.4f, 11.4f, 8.5f, 12f, 3f); close() }, p)
            "list" -> { for (y in listOf(6f, 12f, 18f)) { c.drawLine(9f, y, 20f, y, p); fillOn(); c.drawCircle(5f, y, 1.2f, p); strokeOn() } }
            "clock" -> { c.drawCircle(12f, 12f, 9f, p); c.drawPath(path { moveTo(12f, 7f); lineTo(12f, 12f); lineTo(15.5f, 14f) }, p) }
            "stamp" -> {
                c.drawCircle(12f, 12f, 8.5f, p); c.drawCircle(12f, 12f, 5.5f, p)
            }
            "trash" -> { c.drawLine(4f, 6.5f, 20f, 6.5f, p); c.drawPath(path { moveTo(6.5f, 6.5f); lineTo(7.5f, 20.5f); lineTo(16.5f, 20.5f); lineTo(17.5f, 6.5f) }, p); c.drawPath(path { moveTo(9.5f, 6.5f); lineTo(9.5f, 3.5f); lineTo(14.5f, 3.5f); lineTo(14.5f, 6.5f) }, p) }
            "lock" -> { c.drawRoundRect(RectF(5f, 10.5f, 19f, 20.5f), 2f, 2f, p); c.drawPath(path { moveTo(8f, 10.5f); lineTo(8f, 7.5f); cubicTo(8f, 5f, 10f, 3.5f, 12f, 3.5f); cubicTo(14f, 3.5f, 16f, 5f, 16f, 7.5f); lineTo(16f, 10.5f) }, p) }
            "eye" -> { c.drawPath(path { moveTo(2.5f, 12f); cubicTo(5f, 7f, 8.5f, 5.5f, 12f, 5.5f); cubicTo(15.5f, 5.5f, 19f, 7f, 21.5f, 12f); cubicTo(19f, 17f, 15.5f, 18.5f, 12f, 18.5f); cubicTo(8.5f, 18.5f, 5f, 17f, 2.5f, 12f); close() }, p); c.drawCircle(12f, 12f, 3f, p) }
            "breath" -> { c.drawCircle(12f, 12f, 3f, p); c.drawCircle(12f, 12f, 6.5f, p); c.drawArc(RectF(2.5f, 2.5f, 21.5f, 21.5f), 200f, 140f, false, p) }
            "turnLeft" -> c.drawPath(path { moveTo(16f, 21f); lineTo(16f, 12f); cubicTo(16f, 10f, 14.5f, 8.5f, 12.5f, 8.5f); lineTo(5f, 8.5f); moveTo(9f, 4.5f); lineTo(5f, 8.5f); lineTo(9f, 12.5f) }, p)
            "turnRight" -> c.drawPath(path { moveTo(8f, 21f); lineTo(8f, 12f); cubicTo(8f, 10f, 9.5f, 8.5f, 11.5f, 8.5f); lineTo(19f, 8.5f); moveTo(15f, 4.5f); lineTo(19f, 8.5f); lineTo(15f, 12.5f) }, p)
            "uturn" -> c.drawPath(path { moveTo(8f, 21f); lineTo(8f, 9f); cubicTo(8f, 6f, 10f, 4.5f, 12f, 4.5f); cubicTo(14f, 4.5f, 16f, 6f, 16f, 9f); lineTo(16f, 15f); moveTo(12.5f, 12f); lineTo(16f, 15.5f); lineTo(19.5f, 12f) }, p)
            "camera" -> { c.drawRoundRect(RectF(3f, 7f, 21f, 19f), 2.5f, 2.5f, p); c.drawCircle(12f, 13f, 3.5f, p); c.drawPath(path { moveTo(8.5f, 7f); lineTo(10f, 4.5f); lineTo(14f, 4.5f); lineTo(15.5f, 7f) }, p) }
            "speed" -> { c.drawCircle(12f, 12f, 9f, p); fillOn(); p.textSize = 8f; p.textAlign = Paint.Align.CENTER; c.drawText("50", 12f, 15f, p); strokeOn() }
            else -> c.drawCircle(12f, 12f, 8f, p)
        }
    }
}
