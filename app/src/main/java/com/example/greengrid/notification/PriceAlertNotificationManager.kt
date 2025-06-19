package com.example.greengrid.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.greengrid.MainActivity
import com.example.greengrid.R

class PriceAlertNotificationManager(private val context: Context) {
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "price_alerts"
    private val channelName = "Preis-Alarme"

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                channelName,
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "Benachrichtigungen für Strompreis-Alarme"
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showPriceAlertNotification(targetPrice: Double, currentPrice: Double) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Strompreis-Alarm")
            .setContentText("Der Strompreis ist auf ${"%.2f".format(currentPrice)} ct/kWh gefallen (Ziel: ${"%.2f".format(targetPrice)} ct/kWh)")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(1, notification)
    }

    fun showMinPriceNotification(hour: Int, price: Double, timestamp: Long) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        // Bestimme, ob es heute oder morgen ist
        val calendar = java.util.Calendar.getInstance()
        calendar.timeInMillis = timestamp
        
        val today = java.util.Calendar.getInstance()
        val tomorrow = java.util.Calendar.getInstance()
        tomorrow.add(java.util.Calendar.DAY_OF_YEAR, 1)
        
        val dateText = when {
            calendar.get(java.util.Calendar.DAY_OF_YEAR) == today.get(java.util.Calendar.DAY_OF_YEAR) -> "heute"
            calendar.get(java.util.Calendar.DAY_OF_YEAR) == tomorrow.get(java.util.Calendar.DAY_OF_YEAR) -> "morgen"
            else -> "am ${java.text.SimpleDateFormat("dd.MM.", java.util.Locale.GERMAN).format(java.util.Date(timestamp))}"
        }

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Tiefpunkt des Strompreises")
            .setContentText("In 15 Minuten (${hour}:00 Uhr $dateText) ist der Tiefpunkt mit ${"%.2f".format(price)} ct/kWh")
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(2, notification)
    }

    fun showAutoTradingNotification(title: String, message: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(3, notification)
    }
} 