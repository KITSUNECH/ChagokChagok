// 차곡車곡 – 암호화 저장소
// Pleos 보안 가이드: 민감한 데이터는 암호화해 저장하고 비밀값은 코드에 넣지 않는다.
// 일기·지나온 길·건넨 길을 안드로이드 키스토어 키(AES-256-GCM)로 암호화한 SharedPreferences 에 둔다.
// 예전 평문 저장소("chagok")가 있으면 처음 한 번 옮기고 비운다.
package com.example.chagokchagok

import android.content.Context
import android.content.SharedPreferences

internal object SecurePrefs {
    private const val PLAIN = "chagok"
    private const val SECURE = "chagok_secure"

    fun open(ctx: Context): SharedPreferences {
        // 화면 확인 테스트(Robolectric)에는 키스토어가 없어 평문을 쓴다. 실제 앱에서는 늘 암호화 저장소.
        if (Car.offline) return ctx.getSharedPreferences(PLAIN, Context.MODE_PRIVATE)
        return try {
            val key = androidx.security.crypto.MasterKey.Builder(ctx).setKeyScheme(androidx.security.crypto.MasterKey.KeyScheme.AES256_GCM).build()
            val secure = androidx.security.crypto.EncryptedSharedPreferences.create(ctx, SECURE, key,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM)
            migrate(ctx.getSharedPreferences(PLAIN, Context.MODE_PRIVATE), secure)
            secure
        } catch (e: Throwable) {
            // 키스토어를 못 쓰는 드문 기기: 앱은 계속 돌아가야 하므로 평문으로 (점검 화면에 표시)
            status = "암호화 저장소를 열지 못함: ${e.javaClass.simpleName}"
            ctx.getSharedPreferences(PLAIN, Context.MODE_PRIVATE)
        }
    }

    var status = "암호화 (AES-256-GCM, 안드로이드 키스토어)"; private set

    @Suppress("UNCHECKED_CAST")
    private fun migrate(from: SharedPreferences, to: SharedPreferences) {
        val all = from.all; if (all.isEmpty()) return
        val e = to.edit()
        all.forEach { (k, v) ->
            when (v) {
                is String -> e.putString(k, v); is Int -> e.putInt(k, v); is Long -> e.putLong(k, v)
                is Float -> e.putFloat(k, v); is Boolean -> e.putBoolean(k, v); is Set<*> -> e.putStringSet(k, v as Set<String>)
            }
        }
        e.apply(); from.edit().clear().apply()
    }
}
