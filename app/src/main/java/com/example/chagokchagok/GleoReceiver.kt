// 차곡車곡 – 글레오가 부르면 답하는 곳
// 글레오는 assets/schema.json 에 적힌 기능 이름으로 방송(broadcast)을 보낸다.
// 여기서 기록을 꺼내 정해진 형식으로 돌려주면, 글레오가 그것을 바탕으로 말로 답한다.
package com.example.chagokchagok

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import org.json.JSONArray
import org.json.JSONObject

class GleoReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store(context)
        val driver = store.driver
        when (intent.action) {
            "READ_LATEST_DIARY" -> {
                val d = store.diariesOf(driver).lastOrNull()
                if (d == null) reply(context, "READ_LATEST_DIARY", "success", "There is no diary entry yet.")
                else reply(
                    context, "READ_LATEST_DIARY", "success", "The latest driving diary entry was found.",
                    JSONArray().put(JSONObject().put("title", d.title).put("diary", d.text))
                )
            }
            "GET_NEXT_ROUTE" -> {
                val g = store.guidesFor(driver).firstOrNull()
                if (g == null) reply(context, "GET_NEXT_ROUTE", "success", "No shared route is available yet.")
                else reply(
                    context, "GET_NEXT_ROUTE", "success", "A route shared by a family member was found.",
                    JSONArray().put(
                        JSONObject().put("route", g.title).put("sharedBy", g.owner).put("distanceKm", g.drive.distKm.toDouble())
                            .put("advice", JSONArray(g.tips.map { it.text }))
                    )
                )
            }
            "CHECK_DRIVING_PROGRESS" -> {
                val parks = store.diariesOf(driver).map { it.drive.parkMoves }.filter { it > 0 }
                if (parks.size < 2) reply(context, "CHECK_DRIVING_PROGRESS", "success", "There are not enough drives to compare yet.")
                else reply(
                    context, "CHECK_DRIVING_PROGRESS", "success", "Parking maneuver counts of the first and latest drives were found.",
                    JSONArray().put(JSONObject().put("firstDriveParkingMoves", parks.first()).put("latestDriveParkingMoves", parks.last()).put("totalDiaries", store.diariesOf(driver).size))
                )
            }
        }
    }

    private fun reply(context: Context, function: String, status: String, message: String, data: JSONArray? = null) {
        val body = JSONObject().put("functionName", function).put("status", status).put("message", message)
        if (data != null) body.put("data", data.toString())
        context.sendBroadcast(
            Intent("ai.pleos.playground.intent.action.APP_ACTION_RESPONSE")
                .putExtra("packageName", context.packageName)
                .putExtra("response", body.toString())
        )
    }
}
