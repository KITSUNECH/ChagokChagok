// 차곡車곡 – 글레오(Gleo AI)가 부르면 답하는 곳
// Pleos App Integration Guide 형식: assets/schema.json 의 function 이름이 broadcast action 으로 오고,
// 인자는 ACTION_PARAM(JSON 문자열)으로 온다. 처리한 뒤 APP_ACTION_RESPONSE 로 결과를 돌려준다 (7초 안).
// 공모전 참가팀은 Gleo Interface 를 별도 승인 없이 쓸 수 있다 (공모전 공지 · 개발 범위 안내).
// 달리는 동안 화면을 누를 수 없으니(도로교통법 제49조) 천천히와 한마디 남기기를 말로 할 수 있게 한다.
package com.example.chagokchagok

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference

/** 지금 떠 있는 화면 (글레오 요청을 진행 중인 주행에 전하려고) */
internal object Live { var activity: WeakReference<MainActivity>? = null }

class GleoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        val param = runCatching { JSONObject(intent.getStringExtra("ACTION_PARAM")?.trim('"')?.replace("\\\"", "\"") ?: "{}") }.getOrElse { JSONObject() }
        val a = Live.activity?.get()
        val pending = goAsync()
        Handler(Looper.getMainLooper()).post {
            try { handle(context, action, param, a) } catch (e: Throwable) { reply(context, action, "fail", "An unexpected internal error occurred.") } finally { pending.finish() }
        }
    }

    private fun handle(context: Context, action: String, param: JSONObject, a: MainActivity?) {
        val store = a?.store ?: Store(context)
        if (!store.onboarded) { reply(context, action, "fail", "The app has not been set up yet."); return }
        when (action) {
            "TAKE_IT_SLOW" -> {
                if (a == null || !a.trip.active) { reply(context, action, "fail", "There is no drive in progress."); return }
                a.trip.breathe()   // 앱이 직접 말한다 → 글레오는 말하지 않게 silent
                reply(context, action, "success", "The spot was remembered for review after parking. The car speed is not changed.", JSONArray().put(JSONObject().put("comfortableRouteRequested", a.car.naviConnected)), mode = "silent")
            }
            "SAVE_NOTE_HERE" -> {
                val note = param.optString("note").trim()
                if (note.isEmpty()) { reply(context, action, "asking", "The note to save is required.", JSONArray().put(JSONObject().put("missingField", "note"))); return }
                if (a == null || !a.trip.active) { reply(context, action, "fail", "Notes can be saved only during a drive."); return }
                val placed = a.trip.voiceNote(note)
                reply(context, action, "success", if (placed) "The note was saved at the current position and will be played here on the next drive to this destination." else "The note was saved, but the current position is unknown, so it will be played along the route instead.",
                    JSONArray().put(JSONObject().put("placedAtPosition", placed)))
            }
            "ASK_MY_DRIVING" -> {
                // 운전자의 질문에 이 앱이 기록한 차량 신호로 답한다. 달리는 중이면 지금까지, 아니면 가장 최근 주행.
                val topic = param.optString("topic").trim().lowercase().takeIf { t -> Coach.FOCUS.any { it.first == t } } ?: "today"
                val driver = store.driver; val all = store.diariesOf(driver)
                val live = a?.trip?.takeIf { it.active }
                val last = all.lastOrNull()
                if (live == null && last == null) { reply(context, action, "success", "No drive has been recorded yet."); return }
                val drive = live?.recorder?.summary(driver) ?: last!!.drive
                val before = if (live != null) all else all.dropLast(1)
                val focus = store.answer("focus_$driver", "focus")?.takeIf { Coach.label(it) != null }
                val answer = if (topic == "today") Coach.today(drive, before, focus, live != null) else Coach.about(topic, drive, before, live != null)
                // 앱이 직접 읽어 주고 글레오는 조용히 (운전 기록 문장은 방송으로 내보내지 않는다). 앱 화면이 없을 때만 답을 데이터로 넘긴다.
                if (a != null) { a.speak(answer); reply(context, action, "success", "The app is answering aloud from the vehicle signals it recorded.", JSONArray().put(JSONObject().put("topic", topic)), mode = "silent") }
                else reply(context, action, "success", "The answer was produced from the vehicle signals recorded by the app.", JSONArray().put(JSONObject().put("topic", topic).put("answer", answer)))
            }
            else -> reply(context, action, "fail", "This function is not supported.")
        }
    }

    private fun reply(context: Context, function: String, status: String, message: String, data: JSONArray? = null, mode: String? = null) {
        val body = JSONObject().put("functionName", function).put("status", status).put("message", message)
        if (data != null) body.put("data", data.toString())
        if (mode != null) body.put("mode", mode)
        context.sendBroadcast(
            Intent("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE")
                .putExtra("packageName", context.packageName)
                .putExtra("response", body.toString())
        )
    }
}
