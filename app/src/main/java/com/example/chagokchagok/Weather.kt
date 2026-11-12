// 차곡車곡 – 도착지 날씨
// 키가 필요 없는 공개 예보(Open-Meteo)를 읽는다. 보내는 것은 좌표 두 개뿐이다.
package com.example.chagokchagok

import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors

data class Forecast(val tempC: Int, val sky: String, val rainChance: Int)

object Weather {
    private val pool = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    /** 지금 날씨와 앞으로 3시간 안 비 올 확률. 실패하면 null. */
    fun at(lat: Double, lng: Double, done: (Forecast?) -> Unit) {
        pool.execute {
            val f = try {
                val url = URL(String.format(Locale.US,
                    "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f&current=temperature_2m,weather_code&hourly=precipitation_probability&forecast_hours=3&timezone=Asia%%2FSeoul", lat, lng))
                val c = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 6000; readTimeout = 6000 }
                val o = JSONObject(c.inputStream.bufferedReader().use { it.readText() })
                val cur = o.getJSONObject("current")
                val probs = o.getJSONObject("hourly").getJSONArray("precipitation_probability")
                val rain = (0 until probs.length()).maxOfOrNull { probs.optInt(it, 0) } ?: 0
                Forecast(cur.getDouble("temperature_2m").toInt(), sky(cur.getInt("weather_code")), rain)
            } catch (e: Throwable) { null }
            main.post { done(f) }
        }
    }

    /** WMO 날씨 코드를 짧은 말로. */
    fun sky(code: Int) = when (code) {
        0 -> "맑음"; 1, 2 -> "구름 조금"; 3 -> "흐림"; 45, 48 -> "안개"
        in 51..57 -> "이슬비"; in 61..67, in 80..82 -> "비"; in 71..77, 85, 86 -> "눈"; in 95..99 -> "뇌우"
        else -> "흐림"
    }
}
