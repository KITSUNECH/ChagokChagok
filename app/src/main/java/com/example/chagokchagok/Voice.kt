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
internal class Focus(ctx: Context, attrs: AudioAttributes) {
    private val am = ctx.getSystemService(Context.AUDIO_SERVICE) as android.media.AudioManager
    private val req = android.media.AudioFocusRequest.Builder(android.media.AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
        .setAudioAttributes(attrs).setOnAudioFocusChangeListener { }.build()
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
    private var last: String? = null

    private val attrs = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build()

    private val focus by lazy { Focus(ctx.applicationContext, attrs) }

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
        override fun onDone() {}
        override fun onStop() {}
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
                        override fun onDone(id: String?) { if (id == "chagok") focus.give() }
                        @Deprecated("기본 콜백") override fun onError(id: String?) { focus.give() }
                    })
                    system = t; ready = true; engine = "기본"
                    status += if (lang < 0) " → 기본 음성(한국어 데이터 없음)" else " → 기본 음성"
                    then()
                } else { status += " → 기본 음성도 없음"; ready = false }
            }
        }
    }

    private fun flush() { pending?.let { pending = null; say(it) } }

    /** 기본 음성: 포커스를 받고, 앞에 180ms 무음(문서 권장 150–200ms)을 둔 뒤 읽는다 */
    private fun systemSay(text: String) {
        val t = system ?: return
        focus.take()
        t.playSilentUtterance(180, android.speech.tts.TextToSpeech.QUEUE_FLUSH, "pre")
        t.speak(text, android.speech.tts.TextToSpeech.QUEUE_ADD, null, "chagok")
    }

    fun say(text: String) {
        last = text
        if (!ready) { pending = text; return }
        val p = pleos
        if (p != null) { try { p.stop(); p.speak(text, true); return } catch (e: Throwable) { status = "Pleos 음성 오류: ${e.message}" } }
        systemSay(text)
    }

    fun stop() { try { pleos?.stop() } catch (_: Throwable) {}; system?.stop() }

    fun release() {
        try { pleos?.removeEventListener(events); pleos?.release() } catch (_: Throwable) {}
        system?.shutdown()
    }
}
