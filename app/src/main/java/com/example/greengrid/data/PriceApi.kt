package com.example.greengrid.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

/**
 * Holt den aktuellen Strompreis von aWATTar.
 * @return Der aktuelle Preis in ct/kWh oder null bei Fehler
 */
suspend fun fetchCurrentPrice(): Double? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.awattar.at/v1/marketdata")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            
            if (conn.responseCode != 200) {
                return@withContext null
            }

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val arr = json.getJSONArray("data")
            
            // Der erste Eintrag ist der aktuelle Preis
            if (arr.length() > 0) {
                val obj = arr.getJSONObject(0)
                obj.getDouble("marketprice")
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
}

data class PriceMinimum(
    val price: Double,
    val timestamp: Long,
    val hour: Int
)

/**
 * Findet den Tiefpunkt des Strompreises für den aktuellen Tag.
 * @return PriceMinimum mit Preis, Zeitstempel und Stunde oder null bei Fehler
 */
suspend fun findDailyPriceMinimum(): PriceMinimum? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("https://api.awattar.at/v1/marketdata")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 5000
                readTimeout = 5000
            }
            
            if (conn.responseCode != 200) {
                return@withContext null
            }

            val text = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(text)
            val arr = json.getJSONArray("data")
            
            var minPrice = Double.MAX_VALUE
            var minTimestamp = 0L
            var minHour = 0
            
            // Durch alle Preise iterieren und Minimum finden
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val price = obj.getDouble("marketprice")
                val timestamp = obj.getLong("start_timestamp")
                
                // Nur Preise für den aktuellen Tag berücksichtigen
                val calendar = Calendar.getInstance()
                calendar.timeInMillis = timestamp * 1000
                val hour = calendar.get(Calendar.HOUR_OF_DAY)
                
                if (price < minPrice) {
                    minPrice = price
                    minTimestamp = timestamp
                    minHour = hour
                }
            }
            
            if (minPrice != Double.MAX_VALUE) {
                PriceMinimum(minPrice, minTimestamp, minHour)
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }
} 