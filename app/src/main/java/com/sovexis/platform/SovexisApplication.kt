package com.sovexis.platform

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class SovexisApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_SOVEXIS_SERVICE,
                "Sovexis 服务通知",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Sovexis 后台服务运行通知"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    companion object {
        const val CHANNEL_SOVEXIS_SERVICE = "sovexis_service_channel"
    }
}
