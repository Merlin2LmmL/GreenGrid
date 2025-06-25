package com.greengrid.app.data

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
 * Findet den Tiefpunkt des Strompreises für den restlichen Tag.
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
            
            // Erst alle Daten sammeln und nach Timestamp sortieren
            val priceData = mutableListOf<Pair<Long, Double>>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val price = obj.getDouble("marketprice")
                val timestamp = obj.getLong("start_timestamp")
                priceData.add(timestamp to price)
                
                // Debug: Zeige die ersten paar Timestamps
                if (i < 3) {
                    println("Debug: Raw timestamp from API: $timestamp")
                    println("Debug: As seconds: ${Date(timestamp * 1000)}")
                    println("Debug: As milliseconds: ${Date(timestamp)}")
                }
            }
            
            // Nach Timestamp sortieren (chronologisch)
            priceData.sortBy { it.first }
            
            var minPrice = Double.MAX_VALUE
            var minTimestamp = 0L
            var minHour = 0
            val now = System.currentTimeMillis() // Aktuelle Zeit in Millisekunden
            
            println("Debug: Current time: ${Date(now)}")
            println("Debug: Processing ${priceData.size} price points")
            
            // Durch alle sortierten Preise iterieren und Minimum für die Zukunft finden
            for ((timestamp, price) in priceData) {
                // Nur zukünftige Preise berücksichtigen (mindestens 5 Minuten in der Zukunft)
                if (timestamp > now + 300000) { // 300000 Millisekunden = 5 Minuten
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = timestamp // Timestamp ist bereits in Millisekunden
                    val hour = calendar.get(Calendar.HOUR_OF_DAY)
                    val date = Date(timestamp) // Timestamp ist bereits in Millisekunden
                    
                    println("Debug: Checking future price: ${date} (${hour}:00) = ${price} ct/kWh")
                    
                    if (price < minPrice) {
                        minPrice = price
                        minTimestamp = timestamp
                        minHour = hour
                        println("Debug: New minimum found: ${date} (${hour}:00) = ${price} ct/kWh")
                    }
                }
            }
            
            if (minPrice != Double.MAX_VALUE) {
                val result = PriceMinimum(minPrice, minTimestamp, minHour)
                val minDate = Date(minTimestamp)
                val currentDate = Date(now)
                
                println("Debug: Current time: ${currentDate}")
                println("Debug: Final minimum: ${minDate} (${minHour}:00) = ${minPrice} ct/kWh")
                
                // Check if it's today or tomorrow
                val currentCalendar = Calendar.getInstance()
                currentCalendar.timeInMillis = now
                val minCalendar = Calendar.getInstance()
                minCalendar.timeInMillis = minTimestamp
                
                val isToday = currentCalendar.get(Calendar.DAY_OF_YEAR) == minCalendar.get(Calendar.DAY_OF_YEAR) &&
                             currentCalendar.get(Calendar.YEAR) == minCalendar.get(Calendar.YEAR)
                
                println("Debug: Is minimum today? $isToday")
                println("Debug: Current day of year: ${currentCalendar.get(Calendar.DAY_OF_YEAR)}")
                println("Debug: Minimum day of year: ${minCalendar.get(Calendar.DAY_OF_YEAR)}")
                
                result
            } else {
                println("Debug: No future minimum found")
                null
            }
        } catch (e: Exception) {
            println("Debug: Error in findDailyPriceMinimum: ${e.message}")
            null
        }
    }
} 