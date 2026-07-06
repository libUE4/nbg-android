package com.nbg.android

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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat

object HanakoForegroundServiceController {
  private const val CHANNEL_ID = "nbg_hanakopro_runtime"
  private const val NOTIFICATION_ID = 0x48414E41

  fun start(context: Context) {
    val appContext = context.applicationContext
    Log.i("NBG_HANAKO", "Hanako foreground service requested")
    ContextCompat.startForegroundService(appContext, Intent(appContext, HanakoForegroundService::class.java))
  }

  internal fun ensureChannel(context: Context) {
    val manager = context.applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "NBG HanakoPro", NotificationManager.IMPORTANCE_LOW).apply {
        description = "保持 HanakoPro 后台运行，避免 Android 冻结内置 Node 服务"
      },
    )
  }

  internal fun buildNotification(context: Context): Notification {
    val launchIntent = PendingIntent.getActivity(
      context,
      2,
      Intent(context, MainActivity::class.java),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    return NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_notify_sync)
      .setContentTitle("NBG HanakoPro 正在运行")
      .setContentText("保持内置代码助手服务可响应")
      .setContentIntent(launchIntent)
      .setOngoing(true)
      .setSilent(true)
      .build()
  }

  internal fun startForeground(service: Service) {
    val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    } else {
      0
    }
    ServiceCompat.startForeground(
      service,
      NOTIFICATION_ID,
      buildNotification(service),
      foregroundServiceType,
    )
  }
}

class HanakoForegroundService : Service() {
  override fun onCreate() {
    super.onCreate()
    HanakoForegroundServiceController.ensureChannel(this)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.i("NBG_HANAKO", "Hanako foreground service startId=$startId")
    HanakoForegroundServiceController.startForeground(this)
    HanakoBackgroundWarmup.start(applicationContext)
    return START_STICKY
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
