// 차곡車곡 – 화면 부품
// 모든 화면이 같은 부품을 쓴다: 글자, 버튼, 카드, 묶음 목록, 칩, 분할 선택, 아바타, 빈 상태.
// 손가락으로 누르는 곳은 높이 56dp 이상 (장갑 낀 손, 흔들리는 차 안).
package com.example.chagokchagok

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.text.TextUtils
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class Kit(val ctx: Context, val ui: Ui) {
    val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
    val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

    // ── 틀 ──
    fun col(gap: Int = 0) = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; if (gap > 0) { showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE; dividerDrawable = spacer(gap) } }
    fun row(gap: Int = 0) = LinearLayout(ctx).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL; if (gap > 0) { showDividers = LinearLayout.SHOW_DIVIDER_MIDDLE; dividerDrawable = spacer(gap) } }
    private fun spacer(g: Int) = GradientDrawable().apply { setSize(ui.dp(g), ui.dp(g)); setColor(Color.TRANSPARENT) }
    fun gap(h: Int) = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(ui.dp(h), ui.dp(h)) }
    fun fill() = View(ctx).apply { layoutParams = LinearLayout.LayoutParams(0, 0, 1f) }
    fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
    fun scroll(child: View) = ScrollView(ctx).apply { isFillViewport = true; isVerticalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; addView(child) }
    fun hscroll(child: View) = HorizontalScrollView(ctx).apply { isHorizontalScrollBarEnabled = false; overScrollMode = View.OVER_SCROLL_NEVER; addView(child) }
    fun rounded(color: Int, radius: Int, stroke: Int = 0, strokeColor: Int = 0) = GradientDrawable().apply {
        setColor(color); cornerRadius = ui.dp(radius).toFloat(); if (stroke > 0) setStroke(ui.dp(stroke), strokeColor)
    }
    /** 누르면 살짝 어두워지는 바탕 */
    fun pressable(v: View, bg: GradientDrawable?, onClick: (() -> Unit)?) {
        v.background = if (bg != null) RippleDrawable(ColorStateList.valueOf(Ink.alpha(Ink.text, .10f)), bg, null) else RippleDrawable(ColorStateList.valueOf(Ink.alpha(Ink.text, .10f)), null, GradientDrawable().apply { setColor(Color.WHITE); cornerRadius = ui.dp(14).toFloat() })
        if (onClick != null) { v.isClickable = true; v.setOnClickListener { onClick() } }
    }

    // ── 글자 ──
    fun text(s: CharSequence, type: Type = Type.Body, color: Int = Ink.text, lines: Int = 0) = TextView(ctx).apply {
        // 한국어 줄바꿈을 어절 단위로 ("처/벌"처럼 낱말 중간에서 끊기지 않게, Android 13+)
        if (android.os.Build.VERSION.SDK_INT >= 33) lineBreakWordStyle = android.graphics.text.LineBreakConfig.LINE_BREAK_WORD_STYLE_PHRASE
        text = s; textSize = ui.sp(type.size); setTextColor(color); typeface = ui.face(type.weight)
        letterSpacing = type.tracking; includeFontPadding = false
        setLineSpacing(0f, if (type.size >= 26) 1.12f else 1.35f)
        if (lines > 0) { maxLines = lines; ellipsize = TextUtils.TruncateAt.END }
    }
    /** 일기·한마디처럼 사람의 말 */
    fun voice(s: CharSequence, size: Float = 22f, color: Int = Ink.text) = TextView(ctx).apply {
        text = s; textSize = ui.sp(size); setTextColor(color); typeface = ui.batang; includeFontPadding = false; setLineSpacing(0f, 1.45f)
    }
    fun eyebrow(s: String, color: Int = Ink.text2) = text(s.uppercase(), Type.Micro, color)

    // ── 아이콘 ──
    fun icon(name: String, color: Int = Ink.text, size: Int = 24) = IconView(ctx, name, color).apply { layoutParams = LinearLayout.LayoutParams(ui.dp(size), ui.dp(size)) }

    // ── 버튼 ──
    enum class Btn { Primary, Secondary, Plain, Danger, Tinted }

    fun button(label: String, kind: Btn = Btn.Primary, icon: String? = null, enabled: Boolean = true, onClick: () -> Unit): View {
        val fg = when (kind) { Btn.Primary -> Ink.onAccent; Btn.Danger -> Ink.danger; Btn.Plain, Btn.Tinted -> Ink.accent; Btn.Secondary -> Ink.text }
        val bgColor = when (kind) { Btn.Primary -> Ink.accent; Btn.Secondary -> Ink.fill; Btn.Tinted -> Ink.accentSoft; Btn.Danger -> Ink.alpha(Ink.danger, .10f); Btn.Plain -> Color.TRANSPARENT }
        return row(8).apply {
            gravity = Gravity.CENTER
            minimumHeight = ui.touch(64)
            setPadding(ui.dp(if (kind == Btn.Plain) 10 else 24), 0, ui.dp(if (kind == Btn.Plain) 10 else 24), 0)
            if (icon != null) addView(icon(icon, fg, 22))
            addView(text(label, Type.BodyStrong, fg).apply { gravity = Gravity.CENTER; maxLines = 1 })
            pressable(this, rounded(bgColor, 16), if (enabled) onClick else null)
            alpha = if (enabled) 1f else .4f
        }
    }
    /** 동그란 아이콘 버튼 */
    fun iconButton(name: String, color: Int = Ink.text, bg: Int = Ink.fill, size: Int = 52, onClick: () -> Unit) = FrameLayout(ctx).apply {
        layoutParams = LinearLayout.LayoutParams(ui.dp(size), ui.dp(size))
        addView(IconView(ctx, name, color), FrameLayout.LayoutParams(ui.dp(size * .46f), ui.dp(size * .46f), Gravity.CENTER))
        pressable(this, GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(bg) }, onClick)
    }
    /** 여러 버튼: 넓으면 가로로 나란히, 좁으면 세로로 */
    fun buttons(vararg items: View, stack: Boolean = ui.narrow): View =
        if (stack) col(10).apply { items.forEach { addView(it, lp(MATCH, WRAP)) } }
        else row(12).apply { items.forEach { addView(it, lp(WRAP, WRAP)) } }

    // ── 카드와 목록 ──
    fun card(pad: Int = 24, color: Int = Ink.surface, onClick: (() -> Unit)? = null, child: LinearLayout.() -> Unit) = col().apply {
        setPadding(ui.dp(pad), ui.dp(pad), ui.dp(pad), ui.dp(pad))
        if (onClick != null) pressable(this, rounded(color, 24), onClick) else background = rounded(color, 24)
        child()
    }
    /** iOS 설정 화면 같은 묶음 목록. rows 사이에 얇은 줄 */
    fun group(vararg rows: View) = col().apply {
        background = rounded(Ink.surface, 22)
        clipToOutline = true
        rows.forEachIndexed { i, r ->
            if (i > 0) addView(View(ctx).apply { setBackgroundColor(Ink.line) }, lp(MATCH, ui.dp(1)).apply { leftMargin = ui.dp(20) })
            addView(r, lp(MATCH, WRAP))
        }
    }
    fun groupOf(rows: List<View>) = group(*rows.toTypedArray())

    /** 목록 한 줄: [아이콘] 제목/설명 ……… 오른쪽 값 › */
    fun item(title: String, sub: String? = null, icon: String? = null, iconColor: Int = Ink.accent, value: String? = null, valueColor: Int = Ink.text2, chevron: Boolean = false, trailing: View? = null, onClick: (() -> Unit)? = null) = row(14).apply {
        minimumHeight = ui.touch(72)
        setPadding(ui.dp(20), ui.dp(14), ui.dp(18), ui.dp(14))
        if (icon != null) addView(FrameLayout(ctx).apply {
            background = rounded(Ink.alpha(iconColor, .14f), 10)
            addView(IconView(ctx, icon, iconColor), FrameLayout.LayoutParams(ui.dp(20), ui.dp(20), Gravity.CENTER))
        }, lp(ui.dp(36), ui.dp(36)))
        addView(col(2).apply {
            addView(text(title, Type.Body))
            if (sub != null) addView(text(sub, Type.Caption, Ink.text2))
        }, lp(0, WRAP, 1f))
        if (value != null) addView(text(value, Type.Sub, valueColor).apply { gravity = Gravity.END; maxLines = 2 })
        if (trailing != null) addView(trailing)
        if (chevron) addView(icon("chevron", Ink.text3, 18))
        if (onClick != null) pressable(this, null, onClick)
    }

    /** 켜고 끄는 스위치가 달린 줄 */
    fun toggleItem(title: String, sub: String?, on: Boolean, icon: String? = null, onChange: (Boolean) -> Unit): View {
        val sw = Switch(ctx, ui, on)
        return item(title, sub, icon, trailing = sw) { sw.on = !sw.on; onChange(sw.on) }.also { sw.setOnClickListener { sw.on = !sw.on; onChange(sw.on) } }
    }

    // ── 칩과 분할 선택 ──
    fun chip(label: String, selected: Boolean = false, icon: String? = null, onClick: () -> Unit) = row(6).apply {
        minimumHeight = ui.touch(56); setPadding(ui.dp(20), 0, ui.dp(20), 0); gravity = Gravity.CENTER   // 차 안에서 손가락으로 누르기 쉬운 크기
        val fg = if (selected) Ink.onAccent else Ink.text
        if (icon != null) addView(icon(icon, fg, 18))
        addView(text(label, Type.Body, fg).apply { typeface = ui.semibold })
        pressable(this, rounded(if (selected) Ink.accent else Ink.fill, 24), onClick)
    }
    /** 칩 묶음: 옆으로 숨기지 않고 줄을 바꿔 모두 보이게 (나중에 더해도 잘리지 않는다) */
    fun chips(labels: List<String>, selected: String?, onPick: (String) -> Unit) = flow(10).apply { labels.forEach { l -> addView(chip(l, l == selected) { onPick(l) }) } }
    fun flow(gap: Int = 10) = Flow(ctx, ui.dp(gap))

    fun segmented(options: List<String>, selected: Int, onPick: (Int) -> Unit) = row().apply {
        setPadding(ui.dp(4), ui.dp(4), ui.dp(4), ui.dp(4)); background = rounded(Ink.fill, 14)
        options.forEachIndexed { i, o ->
            addView(text(o, Type.Body, if (i == selected) Ink.text else Ink.text2).apply {
                gravity = Gravity.CENTER; typeface = ui.semibold; minHeight = ui.touch(52)   // 차 안에서 누르기 쉬운 높이
                if (i == selected) { background = rounded(Ink.surface, 11); elevation = ui.dp(1).toFloat() }
                setOnClickListener { onPick(i) }
            }, lp(0, WRAP, 1f))
        }
    }

    // ── 사람, 상태 ──
    fun avatar(name: String, size: Int = 44) = TextView(ctx).apply {
        text = name.take(1); gravity = Gravity.CENTER; setTextColor(Color.WHITE); typeface = ui.bold; textSize = ui.sp(size * .42f)
        background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ink.person(name)) }
        layoutParams = LinearLayout.LayoutParams(ui.dp(size), ui.dp(size))
    }
    /** 점 하나와 짧은 상태 글 */
    fun status(label: String, color: Int) = row(8).apply {
        addView(View(ctx).apply { background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(color) } }, lp(ui.dp(9), ui.dp(9)))
        addView(text(label, Type.Caption, Ink.text2))
    }
    fun tag(label: String, color: Int = Ink.accent) = text(label, Type.Micro, color).apply {
        setPadding(ui.dp(10), ui.dp(5), ui.dp(10), ui.dp(5)); background = rounded(Ink.alpha(color, .13f), 8)
    }

    /** 큰 숫자 하나와 이름 */
    fun stat(value: String, label: String) = col(4).apply {
        setPadding(ui.dp(20), ui.dp(18), ui.dp(20), ui.dp(18)); background = rounded(Ink.surface, 20)
        addView(text(value, Type.Title)); addView(text(label, Type.Caption, Ink.text2))
    }

    /** 화면 위 큰 제목 (iPad 설정 앱처럼) */
    fun header(title: String, sub: String? = null, onBack: (() -> Unit)? = null, trailing: View? = null) = col(6).apply {
        addView(row(4).apply {
            if (onBack != null) addView(row(2).apply {
                addView(icon("back", Ink.accent, 26)); addView(text("뒤로", Type.BodyStrong, Ink.accent))
                minimumHeight = ui.touch(56); setPadding(0, 0, ui.dp(20), 0); setOnClickListener { onBack() }
            })
            addView(fill())
            if (trailing != null) addView(trailing)
        })
        addView(text(title, Type.Large))
        if (sub != null) addView(text(sub, Type.Sub, Ink.text2))
    }
    fun section(title: String, action: String? = null, onAction: (() -> Unit)? = null) = row().apply {
        setPadding(ui.dp(4), ui.dp(22), ui.dp(4), ui.dp(10))
        addView(text(title, Type.Headline), lp(0, WRAP, 1f))
        if (action != null && onAction != null) addView(text(action, Type.Sub, Ink.accent).apply { setOnClickListener { onAction() }; setPadding(ui.dp(8), ui.dp(6), 0, ui.dp(6)) })
    }

    /** 빈 상태: 아이콘, 한 줄 제목, 설명, 할 일 */
    fun empty(icon: String, title: String, body: String, vararg actions: View) = col(10).apply {
        gravity = Gravity.CENTER_HORIZONTAL; setPadding(ui.dp(28), ui.dp(36), ui.dp(28), ui.dp(36))
        addView(FrameLayout(ctx).apply {
            background = GradientDrawable().apply { shape = GradientDrawable.OVAL; setColor(Ink.accentSoft) }
            addView(IconView(ctx, icon, Ink.accent), FrameLayout.LayoutParams(ui.dp(30), ui.dp(30), Gravity.CENTER))
        }, lp(ui.dp(64), ui.dp(64)))
        addView(text(title, Type.Headline).apply { gravity = Gravity.CENTER })
        addView(text(body, Type.Sub, Ink.text2).apply { gravity = Gravity.CENTER })
        if (actions.isNotEmpty()) { addView(gap(6)); addView(buttons(*actions, stack = false)) }
    }

    /** 가로 막대 진행도 */
    fun progress(value: Float, color: Int = Ink.accent) = object : View(ctx) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(c: Canvas) {
            val r = height / 2f
            p.color = Ink.fill; c.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), r, r, p)
            p.color = color; c.drawRoundRect(0f, 0f, width * value.coerceIn(0f, 1f), height.toFloat(), r, r, p)
        }
    }.apply { layoutParams = lp(MATCH, ui.dp(8)) }

    /** 두 칸 격자 (좁으면 한 칸). 한 줄의 칸들은 높이를 맞춰 밑단이 한 줄에 선다. */
    fun grid(cols: Int, views: List<View>, gap: Int = 16) = col(gap).apply {
        views.chunked(cols).forEach { chunk ->
            addView(row(gap).apply {
                gravity = Gravity.TOP
                chunk.forEach { addView(it, lp(0, MATCH, 1f)) }
                repeat(cols - chunk.size) { addView(android.widget.Space(ctx), lp(0, MATCH, 1f)) }   // 빈 칸. 높이를 고정하면 줄 전체가 그 높이로 줄어든다
            }, lp(MATCH, WRAP))
        }
    }
}

/** iOS 스타일 스위치 */
class Switch(ctx: Context, private val ui: Ui, initial: Boolean) : View(ctx) {
    var on = initial
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    init { layoutParams = LinearLayout.LayoutParams(ui.dp(58), ui.dp(34)) }
    override fun onDraw(c: Canvas) {
        val h = height.toFloat(); val w = width.toFloat(); val r = h / 2
        p.color = if (on) Ink.go else Ink.line; c.drawRoundRect(0f, 0f, w, h, r, r, p)
        p.color = Color.WHITE; c.drawCircle(if (on) w - r else r, r, r - ui.dp(3), p)
    }
    override fun onTouchEvent(e: MotionEvent): Boolean { if (e.action == MotionEvent.ACTION_UP) performClick(); return true }
}


/** 줄바꿈 배치: 자식을 왼쪽부터 늘어놓다가 폭이 모자라면 다음 줄로 */
class Flow(ctx: Context, private val gap: Int) : ViewGroup(ctx) {
    override fun onMeasure(wSpec: Int, hSpec: Int) {
        val maxW = MeasureSpec.getSize(wSpec) - paddingLeft - paddingRight
        var x = 0; var y = 0; var lineH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i); if (c.visibility == GONE) continue
            c.measure(MeasureSpec.makeMeasureSpec(maxW, MeasureSpec.AT_MOST), MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED))
            if (x > 0 && x + c.measuredWidth > maxW) { x = 0; y += lineH + gap; lineH = 0 }
            x += c.measuredWidth + gap; lineH = maxOf(lineH, c.measuredHeight)
        }
        setMeasuredDimension(MeasureSpec.getSize(wSpec), y + lineH + paddingTop + paddingBottom)
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val maxW = r - l - paddingLeft - paddingRight
        var x = 0; var y = 0; var lineH = 0
        for (i in 0 until childCount) {
            val c = getChildAt(i); if (c.visibility == GONE) continue
            if (x > 0 && x + c.measuredWidth > maxW) { x = 0; y += lineH + gap; lineH = 0 }
            c.layout(paddingLeft + x, paddingTop + y, paddingLeft + x + c.measuredWidth, paddingTop + y + c.measuredHeight)
            x += c.measuredWidth + gap; lineH = maxOf(lineH, c.measuredHeight)
        }
    }
}
