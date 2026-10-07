package com.lingua.english.data.ai

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.lingua.english.LinguaApplication
import com.lingua.english.MainActivity
import com.lingua.english.R

/**
 * Gemini Live 通话的前台保护。音频 I/O 仍由 [GeminiLiveClient] 管理，这个服务只负责告诉
 * Android 「用户正在进行可见的麦克风通话」，避免 Activity 锁屏后进程很快被限制。
 */
class LiveCallForegroundService : Service() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_HANG_UP) {
            (application as? LinguaApplication)?.container?.geminiLive?.stop()
            stopSelf()
            return START_NOT_STICKY
        }

        val openApp = PendingIntent.getActivity(
            this,
            REQUEST_OPEN_APP,
            Intent(this, MainActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val hangUp = PendingIntent.getService(
            this,
            REQUEST_HANG_UP,
            Intent(this, LiveCallForegroundService::class.java).setAction(ACTION_HANG_UP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Poppy 通话进行中")
            .setContentText("麦克风会持续工作 · 点按返回 Lingua")
            .setContentIntent(openApp)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "挂断",
                hangUp,
            )
            .build()

        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            } else {
                0
            },
        )
        return START_NOT_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户主动从最近任务划掉 Lingua 视为挂断，避免隐藏录音。
        (application as? LinguaApplication)?.container?.geminiLive?.stop()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        // 若服务被系统意外停掉，不让 Application scope 里的录音继续成为隐藏任务。
        val live = (application as? LinguaApplication)?.container?.geminiLive
        if (live?.state?.value?.phase != LivePhase.IDLE) live?.stop()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            "实时语音通话",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "通话时保持麦克风连接"
            setSound(null, null)
        }
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val CHANNEL_ID = "live_call"
        private const val NOTIFICATION_ID = 2_401
        private const val ACTION_HANG_UP = "com.lingua.english.action.HANG_UP_LIVE_CALL"
        private const val REQUEST_OPEN_APP = 2_402
        private const val REQUEST_HANG_UP = 2_403

        fun start(context: Context) {
            ContextCompat.startForegroundService(
                context,
                Intent(context, LiveCallForegroundService::class.java),
            )
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, LiveCallForegroundService::class.java))
        }
    }
}
