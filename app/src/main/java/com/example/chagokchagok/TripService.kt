// 차곡차곡 – 기록 중임을 알리는 포그라운드 서비스
// 기록은 앱 화면(Activity)에 붙어 있지만, 내비를 전체 화면으로 열거나 다른 앱으로 옮겨도
// 시스템이 앱을 정리하지 않게 "기록 중" 알림과 함께 앞쪽 서비스로 붙잡아 둔다.
// 서비스 자체는 아무 일도 하지 않는다 (차 신호는 이미 열린 연결로 계속 들어온다).
package com.example.chagokchagok

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder

class TripService : Service() {
    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "기록 중", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        val open = PendingIntent.getActivity(this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), PendingIntent.FLAG_IMMUTABLE)
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_chagok)
            .setContentTitle("차곡차곡 기록 중")
            .setContentText(intent?.getStringExtra("text") ?: "P에 놓으면 기록을 마쳐요")
            .setOngoing(true).setContentIntent(open).build()
        try {
            if (Build.VERSION.SDK_INT >= 34) startForeground(ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) else startForeground(ID, n)
        } catch (_: Throwable) { stopSelf() }
        return START_NOT_STICKY
    }

    companion object {
        private const val CHANNEL = "trip"; private const val ID = 7
        fun start(ctx: Context, text: String? = null) {
            if (Car.offline) return
            try { ctx.startForegroundService(Intent(ctx, TripService::class.java).putExtra("text", text)) } catch (_: Throwable) {}
        }
        fun stop(ctx: Context) {
            if (Car.offline) return
            try { ctx.stopService(Intent(ctx, TripService::class.java)) } catch (_: Throwable) {}
        }
    }
}
