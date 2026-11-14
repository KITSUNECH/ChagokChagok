// 차곡車곡 – 목소리
// Pleos TextToSpeech SDK(기기 안 합성, 앱 등록 필요 없음)로 먼저 말하고,
// 그게 안 되는 환경이면 안드로이드 기본 음성으로 말한다. 어느 쪽으로 말하는지는 설정에서 보인다.
package com.example.chagokchagok

import android.content.Context
import android.media.AudioAttributes
import android.os.Handler
import android.os.Looper
import java.util.Locale
import ai.pleos.playground.tts.TextToSpeech as PleosTts
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener

/**
 * 오디오 포커스 (Pleos 오디오 가이드): 소리를 내기 전에 AudioAttributes 와 함께 포커스를 요청하고,
 * 짧은 안내라 AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK 로 다른 소리(음악·내비)는 잠깐 줄이기만 한다. 끝나면 돌려준다.
 */
internal class Focus(ctx: Context, attrs: AudioAttributes, private val onLoss: () -> Unit = {}, private val onGain: () -> Unit = {}) {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    // 문서(오디오 포커스): 포커스 변화를 반드시 처리한다. 잃으면(글레오가 말하거나 전화 등) 곧바로 멈추고, 다시 받으면 이어 말한다.
    private val req = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs).setOnAudioFocusChangeListener { change ->
            when (change) {
                android.media.AudioManager.AUDIOFOCUS_LOSS, android.media.AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onLoss()
                android.media.AudioManager.AUDIOFOCUS_GAIN -> onGain()
            }
        }.build()
    fun take(): Boolean = try { am.requestAudioFocus(req) == android.media.AudioManager.AUDIOFOCUS_REQUEST_GRANTED } catch (_: Throwable) { true }
    fun give() { try { am.abandonAudioFocusRequest(req) } catch (_: Throwable) {} }
}

class Voice(private val ctx: Context) {
    var status = "준비 중"; private set
    var ready = false; private set
    var engine = ""; private set

    private val main = Handler(Looper.getMainLooper())
    private var pleos: PleosTts? = null
    private var system: android.speech.tts.TextToSpeech? = null
    private var pending: String? = null
    private var pendingPri = 0
    private var pendingAt = 0L
    private var last: String? = null
    /** 지금 하는 말의 무게와, 대략 끝날 때 (글자 수로 어림) */
    private var curPri = -1
    private var busyUntil = 0L
    /** 같은 말을 30초 안에 되풀이하지 않는다 */
    private val recent = HashMap<String, Long>()
    /** 무거운 말(PLACE 이상)이 끊기거나, 기다리다 버려졌을 때 */
    var onDropped: ((String) -> Unit)? = null
    private fun dropped(text: String?, pri: Int) { if (text != null && pri >= PLACE) onDropped?.invoke(text) }

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

    private val focus by lazy { Focus(ctx.applicationContext, attrs, onLoss = { main.post { if (android.os.SystemClock.elapsedRealtime() < busyUntil) dropped(last, curPri); stop() } }, onGain = { main.post { flush() } }) }

    fun start() {
        if (Car.offline) { status = "꺼짐 (화면 확인용)"; return }
        try {
            val t = PleosTts(ctx.applicationContext, Mode.ON_DEVICE)
            t.initialize(object : OnInitializeListener {
                override fun onSuccess() = main.post {
                    try {
                        t.setLocale(Locale.KOREA); t.setAudioAttributes(attrs); t.addEventListener(events)
                        pleos = t; ready = true; engine = "Pleos"; status = "Pleos 음성"
                        flush()
                    } catch (e: Throwable) { fallback("Pleos 설정 실패: ${e.message}") }
                }.let {}
                override fun onFailure(errorType: ErrorType) = main.post { try { t.release() } catch (_: Throwable) {}; fallback("Pleos 음성 실패($errorType)") }.let {}
            })
        } catch (e: Throwable) { fallback("Pleos 음성 없음") }
    }

    private val events = object : EventListener {
        override fun onStart() {}
        override fun onReady() {}
        override fun onDone() { main.post { idle() } }
        override fun onStop() { main.post { idle() } }
        override fun onUpdatedRms(rms: Double) {}
        override fun onError(errorType: ErrorType) {
            // 이번 문장만 기본 음성으로 다시 읽는다
            main.post { status = "Pleos 음성 오류($errorType), 기본 음성 사용"; startSystem { last?.let { systemSay(it) } } }
        }
    }

    private fun fallback(why: String) {
        status = why
        startSystem { flush() }
    }

    private fun startSystem(then: () -> Unit) {
        if (system != null) { then(); return }
        var t: android.speech.tts.TextToSpeech? = null
        t = android.speech.tts.TextToSpeech(ctx.applicationContext) { s ->
            main.post {
                if (s == android.speech.tts.TextToSpeech.SUCCESS) {
                    val lang = t!!.setLanguage(Locale.KOREA)
                    t!!.setAudioAttributes(attrs)
                    t!!.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
                        override fun onStart(id: String?) {}
                        override fun onDone(id: String?) { if (id == "chagok") main.post { idle() } }
                        @Deprecated("기본 콜백") override fun onError(id: String?) { main.post { idle() } }
                    })
                    system = t; ready = true; engine = "기본"
                    status += if (lang < 0) " → 기본 음성(한국어 데이터 없음)" else " → 기본 음성"
                    then()
                } else { status += " → 기본 음성도 없음"; ready = false }
            }
        }
    }

    /** 말을 마쳤다: 포커스를 돌려주고, 기다리던 말이 아직 의미가 있으면 이어서 한다 */
    private fun idle() { curPri = -1; busyUntil = 0L; focus.give(); flush() }

    /** 기다리던 말. 8초 넘게 기다린 말은 버린다 (그 자리를 이미 지났거나 때가 지난 말) */
    private fun flush() {
        val t = pending ?: return
        pending = null
        if (android.os.SystemClock.elapsedRealtime() - pendingAt > 8_000 && pendingPri < URGENT) { dropped(t, pendingPri); return }
        say(t, pendingPri)
    }

    private fun hold(text: String, pri: Int) {
        // 기다리는 자리는 하나. 더 무거운 말이 기다리고 있으면 가벼운 말은 버린다
        if (pending != null && pendingPri > pri) { dropped(text, pri); return }
        if (pending != text) dropped(pending, pendingPri)
        pending = text; pendingPri = pri; pendingAt = android.os.SystemClock.elapsedRealtime()
    }

    /** 기본 음성: 포커스를 받고, 앞에 180ms 무음(문서 권장 150–200ms)을 둔 뒤 읽는다 */
    private fun systemSay(text: String) {
        val t = system ?: return
        t.playSilentUtterance(180, android.speech.tts.TextToSpeech.QUEUE_FLUSH, "pre")
        t.speak(text, android.speech.tts.TextToSpeech.QUEUE_ADD, null, "chagok")
    }

    /**
     * 말하기. pri: LOW(칭찬·응원·위로) < NORMAL(출발·도착) < PLACE(그 자리의 한마디·회전 전 깜빡이) < URGENT(빨간 경고등).
     * 말하는 중에는 더 무거운 말만 끊고 들어간다. 같거나 무거운 말이 나오는 중이면 가벼운 말은 버리고, 나머지는 끝난 뒤에 한다.
     */
    fun say(text: String, pri: Int = NORMAL) {
        val now = android.os.SystemClock.elapsedRealtime()
        recent[text]?.let { if (now - it < 30_000) return }
        if (!ready) { hold(text, pri); return }
        if (now < busyUntil && pri <= curPri) { if (pri > LOW) hold(text, pri); return }
        // 포커스를 받지 못하면(다른 음성·통화가 쓰는 중) 말하지 않고, 포커스가 돌아오면 이어 말한다
        if (!focus.take()) { if (pri > LOW) hold(text, pri); return }
        if (now < busyUntil) dropped(last, curPri)   // 더 무거운 말이 끼어들어 지금 말이 끊긴다
        last = text; recent[text] = now; curPri = pri
        busyUntil = now + 800 + text.length * 130L
        val p = pleos
        if (p != null) {
            try {
                p.stop()
                // 문서(음성 안내 프리롤): 소리가 잘리지 않게 150–200ms 쉬고 말한다
                main.postDelayed({ try { p.speak(text, true) } catch (e: Throwable) { status = "Pleos 음성 오류: ${e.message}"; idle() } }, 180)
                return
            } catch (e: Throwable) { status = "Pleos 음성 오류: ${e.message}" }
        }
        systemSay(text)
    }

    fun stop() { try { pleos?.stop() } catch (_: Throwable) {}; system?.stop(); curPri = -1; busyUntil = 0L }

    companion object { const val LOW = 0; const val NORMAL = 1; const val PLACE = 2; const val URGENT = 3 }

    fun release() {
        try { pleos?.removeEventListener(events); pleos?.release() } catch (_: Throwable) {}
        system?.shutdown()
    }
}
