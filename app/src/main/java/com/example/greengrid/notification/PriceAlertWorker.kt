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
                val minTime = priceMinimum.timestamp * 1000
                val timeUntilMin = minTime - now

                // Wenn wir uns der Tiefpunkt-Stunde nähern (15 Minuten vorher)
                if (timeUntilMin in 0..(15 * 60 * 1000)) {
                    notificationManager.showMinPriceNotification(
                        priceMinimum.hour,
                        priceMinimum.price
                    )
                    preferences.isMinPriceAlertActive = false
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