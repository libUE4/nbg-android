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
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import android.util.Log

object NbgFileShareServiceController {
  const val ACTION_STOP = "com.nbg.android.action.STOP_FILE_SHARE"
  private const val CHANNEL_ID = "nbg_file_share"
  private const val NOTIFICATION_ID = 0x4E4247

  fun start(context: Context) {
    val appContext = context.applicationContext
    Log.i("NbgFileShare", "startForegroundService requested")
    ContextCompat.startForegroundService(appContext, Intent(appContext, NbgFileShareService::class.java))
  }

  fun stop(context: Context) {
    val appContext = context.applicationContext
    appContext.stopService(Intent(appContext, NbgFileShareService::class.java))
  }

  internal fun ensureChannel(context: Context) {
    val appContext = context.applicationContext
    val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    if (manager.getNotificationChannel(CHANNEL_ID) != null) return
    manager.createNotificationChannel(
      NotificationChannel(CHANNEL_ID, "NBG 文件共享", NotificationManager.IMPORTANCE_LOW).apply {
        description = "NBG 文件共享 FTP 前台服务"
      },
    )
  }

  internal fun buildNotification(context: Context, state: NbgFileShareServerState): Notification {
    val stopIntent = PendingIntent.getService(
      context,
      1,
      Intent(context, NbgFileShareService::class.java).setAction(ACTION_STOP),
      PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val contentText = when {
      state.running && state.localUrl.isNotBlank() -> "FTP ${state.accessMode.label}: ${state.localUrl.removePrefix("ftp://")}"
      state.message.isNotBlank() -> state.message
      else -> "FTP 共享正在启动"
    }
    return NotificationCompat.Builder(context, CHANNEL_ID)
      .setSmallIcon(android.R.drawable.stat_sys_upload)
      .setContentTitle("NBG FTP 共享")
      .setContentText(contentText)
      .setStyle(NotificationCompat.BigTextStyle().bigText(contentText))
      .setOngoing(true)
      .setSilent(true)
      .addAction(0, "停止", stopIntent)
      .build()
  }

  internal fun startForeground(service: Service, state: NbgFileShareServerState) {
    Log.i("NbgFileShare", "startForeground state running=${state.running} port=${state.port} accessMode=${state.accessMode.wireValue}")
    val foregroundServiceType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
      ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
    } else {
      0
    }
    ServiceCompat.startForeground(
      service,
      NOTIFICATION_ID,
      buildNotification(service, state),
      foregroundServiceType,
    )
  }
}

class NbgFileShareService : Service() {
  override fun onCreate() {
    super.onCreate()
    Log.i("NbgFileShareService", "onCreate")
    NbgFileShareServiceController.ensureChannel(this)
  }

  override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    Log.i("NbgFileShareService", "onStartCommand action=${intent?.action} startId=$startId")
    if (intent?.action == NbgFileShareServiceController.ACTION_STOP) {
      stopSelf()
      return START_NOT_STICKY
    }
    val state = NbgFileShareServerRegistry.start(applicationContext)
    NbgFileShareServiceController.startForeground(this, state)
    if (!state.running) {
      Log.e("NbgFileShareService", "ftp server failed to start: ${state.message}")
      stopSelf()
      return START_NOT_STICKY
    }
    return START_STICKY
  }

  override fun onDestroy() {
    Log.i("NbgFileShareService", "onDestroy")
    NbgFileShareServerRegistry.stop(applicationContext)
    super.onDestroy()
  }

  override fun onBind(intent: Intent?): IBinder? = null
}
