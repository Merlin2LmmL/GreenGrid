package com.example.greengrid.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.greengrid.data.PriceAlertPreferences
import com.example.greengrid.data.fetchCurrentPrice
import com.example.greengrid.data.findDailyPriceMinimum

class PriceAlertWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val notificationManager = PriceAlertNotificationManager(context)
        val preferences = PriceAlertPreferences(context)

        try {
            // Aktuellen Preis und Tiefpunkt abrufen
            val currentPrice = fetchCurrentPrice()
            val priceMinimum = findDailyPriceMinimum()

            // Manuellen Preis-Alarm prüfen
            if (preferences.isAlertActive && currentPrice != null) {
                if (currentPrice <= preferences.targetPrice) {
                    notificationManager.showPriceAlertNotification(preferences.targetPrice, currentPrice)
                    preferences.isAlertActive = false
                }
            }

            // Tiefpunkt-Alarm prüfen
            if (preferences.isMinPriceAlertActive && priceMinimum != null) {
                val now = System.currentTimeMillis()
                val minTime = priceMinimum.timestamp
                val timeUntilMin = minTime - now

                // Debug: Log timing information
                val currentDate = java.util.Date(now)
                val minDate = java.util.Date(minTime)
                println("Debug: Current time: $currentDate")
                println("Debug: Minimum time: $minDate")
                println("Debug: Time until minimum: ${timeUntilMin / (1000 * 60)} minutes")

                // Benachrichtigung 15 Minuten vor dem Tiefpunkt senden
                // WorkManager läuft alle 15 Minuten, also prüfen wir einen 20-Minuten-Zeitraum
                if (timeUntilMin in 0..(20 * 60 * 1000)) {
                    println("Debug: Sending minimum price notification!")
                    notificationManager.showMinPriceNotification(
                        priceMinimum.hour,
                        priceMinimum.price,
                        priceMinimum.timestamp
                    )
                    preferences.isMinPriceAlertActive = false
                    println("Debug: Minimum price alert deactivated")
                }
            }

            return Result.success()
        } catch (e: Exception) {
            return Result.retry()
        }
    }

    companion object {
        const val WORK_NAME = "price_alert_worker"
    }
} 