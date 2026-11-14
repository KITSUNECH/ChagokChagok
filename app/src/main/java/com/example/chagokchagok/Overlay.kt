// 차곡車곡 – 시트와 알림 띠
// 안드로이드 기본 팝업 대신 앱과 같은 모양의 시트를 화면 가운데(좁으면 아래)에 띄운다.
// 주행이 시작되면 열린 시트는 모두 닫힌다 (closeAll).
package com.example.chagokchagok

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout

class Overlay(private val host: FrameLayout, private val kitOf: () -> Kit) {
    private val ctx: Context = host.context
    private val stack = mutableListOf<View>()
    private var banner: View? = null
    val isOpen get() = stack.isNotEmpty()

    /** 시트 한 장. 내용은 body 안에 쌓는다. 닫기는 바깥을 누르거나 뒤로 가기. */
    fun sheet(title: String, message: String? = null, dismissible: Boolean = true, body: (LinearLayout.(close: () -> Unit) -> Unit)? = null) {
        val k = kitOf(); val ui = k.ui
        val scrim = FrameLayout(ctx).apply {
            setBackgroundColor(Ink.alpha(Color.BLACK, if (Ink.night) .6f else .32f))
            isClickable = true
        }
        val close = { dismiss(scrim) }
        if (dismissible) scrim.setOnClickListener { close() }
        val card = k.col(14).apply {
            setPadding(ui.dp(28), ui.dp(26), ui.dp(28), ui.dp(24))
            background = GradientDrawable().apply { setColor(Ink.surface); cornerRadius = ui.dp(28).toFloat() }
            elevation = ui.dp(12).toFloat()
            isClickable = true
            addView(k.row().apply {
                addView(k.text(title, Type.Title), k.lp(0, k.WRAP, 1f))
                if (dismissible) addView(k.iconButton("close", Ink.text2, Ink.fill, 40) { close() })
            })
            if (message != null) addView(k.text(message, Type.Sub, Ink.text2))
            body?.invoke(this, close)
        }
        val maxW = ui.dp(620)
        val wrapper = k.scroll(FrameLayout(ctx).apply {
            setPadding(ui.dp(20), ui.dp(24), ui.dp(20), ui.dp(24))
            addView(card, FrameLayout.LayoutParams(k.MATCH, k.WRAP, Gravity.CENTER))
        }).apply { isFillViewport = true }
        scrim.addView(wrapper, FrameLayout.LayoutParams(minOf(maxW, host.width.takeIf { it > 0 } ?: maxW), k.WRAP, if (ui.narrow) Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL else Gravity.CENTER))
        host.addView(scrim, FrameLayout.LayoutParams(k.MATCH, k.MATCH))
        stack.add(scrim)
        scrim.alpha = 0f; scrim.animate().alpha(1f).setDuration(160).start()
        card.translationY = ui.dp(16).toFloat(); card.animate().translationY(0f).setDuration(200).start()
    }

    /** 고를 것 여러 개. 고르면 닫힌다. */
    fun choose(title: String, message: String? = null, options: List<String>, icons: List<String>? = null, selected: Int = -1, onPick: (Int) -> Unit) =
        sheet(title, message) { close ->
            val k = kitOf()
            addView(k.groupOf(options.mapIndexed { i, o ->
                k.item(o, icon = icons?.getOrNull(i), trailing = if (i == selected) k.icon("check", Ink.accent, 20) else null) { close(); onPick(i) }
            }).apply { background = k.rounded(Ink.fill, 18) })
        }

    /** 확인 한 번 */
    fun confirm(title: String, message: String, yes: String, danger: Boolean = false, onYes: () -> Unit) =
        sheet(title, message) { close ->
            val k = kitOf()
            addView(k.buttons(k.button("취소", Kit.Btn.Secondary) { close() }, k.button(yes, if (danger) Kit.Btn.Danger else Kit.Btn.Primary) { close(); onYes() }, stack = false))
        }

    /** 글 한 줄 입력 */
    /** 차가 글자 입력을 막았는지 (CarUxRestrictions NO_KEYBOARD). MainActivity 가 채운다. */
    var keyboardBlocked: () -> Boolean = { false }

    fun input(title: String, hint: String, initial: String = "", ok: String = "확인", onDone: (String) -> Unit) =
        if (keyboardBlocked()) toast("달리는 동안은 글자를 입력할 수 없어요. 멈춘 뒤에 써 주세요.") else sheet(title) { close ->
            val k = kitOf(); val ui = k.ui
            val field = EditText(ctx).apply {
                setText(initial); this.hint = hint; textSize = ui.sp(Type.Body.size); typeface = ui.regular
                setTextColor(Ink.text); setHintTextColor(Ink.text3); background = k.rounded(Ink.fill, 14)
                setPadding(ui.dp(18), ui.dp(16), ui.dp(18), ui.dp(16)); inputType = InputType.TYPE_CLASS_TEXT
                imeOptions = EditorInfo.IME_ACTION_DONE; setSingleLine()
            }
            fun submit() { val t = field.text.toString().trim(); if (t.isNotEmpty()) { hideKeyboard(field); close(); onDone(t) } }
            field.setOnEditorActionListener { _, _, _ -> submit(); true }
            addView(field, k.lp(k.MATCH, k.WRAP))
            addView(k.buttons(k.button("취소", Kit.Btn.Secondary) { hideKeyboard(field); close() }, k.button(ok) { submit() }, stack = false))
            field.requestFocus()
            field.postDelayed({ (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).showSoftInput(field, 0) }, 200)
        }

    private fun hideKeyboard(v: View) = (ctx.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(v.windowToken, 0)

    private fun dismiss(v: View) {
        stack.remove(v)
        v.animate().alpha(0f).setDuration(120).withEndAction { host.removeView(v) }.start()
    }

    /** 뒤로 가기: 맨 위 시트를 닫았으면 true */
    fun back(): Boolean { val top = stack.lastOrNull() ?: return false; dismiss(top); return true }
    fun closeAll() { stack.toList().forEach { host.removeView(it) }; stack.clear() }

    /** 화면 위쪽에 잠깐 떴다 사라지는 띠. 차가 하는 말이면 icon=speaker */
    /** 짧은 알림. undo 가 있으면 "되돌리기" 단추를 함께 보여 준다 (지운 뒤 알림) */
    /** 주행 중인지 (MainActivity 가 채운다). 주행 중에는 한 줄로만 */
    var driving: () -> Boolean = { false }

    fun toast(s: String, icon: String = "info", undo: (() -> Unit)? = null) {
        val k = kitOf(); val ui = k.ui
        banner?.let { host.removeView(it) }
        val bar = k.row(12).apply {
            setPadding(ui.dp(20), ui.dp(14), ui.dp(if (undo != null) 12 else 24), ui.dp(14))
            // 밤에도 바탕과 구분되게: 밤에는 밝은 알림, 낮에는 어두운 알림
            val fg = if (Ink.night) Color.parseColor("#0B1A2C") else Color.WHITE
            background = GradientDrawable().apply { setColor(if (Ink.night) Color.parseColor("#F3EFEC") else Color.parseColor("#1D1D1F")); cornerRadius = ui.dp(22).toFloat() }
            elevation = ui.dp(8).toFloat()
            addView(k.icon(icon, fg, 22))
            addView(k.text(s, Type.Sub, fg).apply { maxLines = if (driving()) 1 else 3; ellipsize = android.text.TextUtils.TruncateAt.END })
            if (undo != null) addView(k.text("되돌리기", Type.BodyStrong, if (Ink.night) Color.parseColor("#005B73") else Color.parseColor("#7FD8F0")).apply {
                setPadding(ui.dp(16), ui.dp(10), ui.dp(12), ui.dp(10))
                setOnClickListener { banner?.let { b -> host.removeView(b) }; banner = null; undo() }
            })
        }
        // 아래 가운데: 제목과 겹치지 않게. 좁은 창은 탭 막대 위로.
        // 오른쪽 위: 시작 단추·숨 고르기 같은 주요 단추를 가리지 않는 자리 (성장 바 왼쪽)
        host.addView(bar, FrameLayout.LayoutParams(k.WRAP, k.WRAP, Gravity.TOP or Gravity.END).apply { topMargin = ui.dp(18); rightMargin = ui.dp(if (ui.wide) 104 else 72); leftMargin = ui.dp(24) })
        banner = bar
        bar.alpha = 0f; bar.translationY = -ui.dp(12).toFloat(); bar.animate().alpha(1f).translationY(0f).setDuration(220).setInterpolator(android.view.animation.DecelerateInterpolator(2f)).start()
        bar.postDelayed({ if (banner === bar) { bar.animate().alpha(0f).setDuration(200).withEndAction { host.removeView(bar) }.start(); banner = null } }, if (undo != null) 6500 else 4200)
    }
}
