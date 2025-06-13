package com.example.greengrid.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*

class EntsoeApiService {
    private val apiKey = "YOUR_ENTSOE_API_KEY" // Replace with your actual API key
    private val baseUrl = "https://transparency.entsoe.eu/api"
    private val dateFormat = SimpleDateFormat("yyyyMMddHHmm", Locale.getDefault())

    suspend fun getDayAheadPrices(startDate: Date, endDate: Date): List<PricePoint> = withContext(Dispatchers.IO) {
        try {
            val url = buildUrl(startDate, endDate)
            val response = URL(url).readText()
            parseXmlResponse(response)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private fun buildUrl(startDate: Date, endDate: Date): String {
        return "$baseUrl?" +
                "securityToken=$apiKey" +
                "&documentType=A44" + // Day-ahead prices
                "&in_Domain=10Y1001A1001A83F" + // Germany
                "&out_Domain=10Y1001A1001A83F" +
                "&periodStart=${dateFormat.format(startDate)}" +
                "&periodEnd=${dateFormat.format(endDate)}"
    }

    private fun parseXmlResponse(xmlResponse: String): List<PricePoint> {
        val pricePoints = mutableListOf<PricePoint>()
        val factory = XmlPullParserFactory.newInstance()
        factory.isNamespaceAware = true
        val parser = factory.newPullParser()
        parser.setInput(StringReader(xmlResponse))

        var eventType = parser.eventType
        var currentTime = 0L
        var currentPrice = 0.0

        while (eventType != XmlPullParser.END_DOCUMENT) {
            when (eventType) {
                XmlPullParser.START_TAG -> {
                    when (parser.name) {
                        "time" -> {
                            val timeStr = parser.nextText()
                            currentTime = parseTime(timeStr)
                        }
                        "price.amount" -> {
                            val priceStr = parser.nextText()
                            currentPrice = priceStr.toDoubleOrNull() ?: 0.0
                            pricePoints.add(PricePoint(currentTime, currentPrice))
                        }
                    }
                }
            }
            eventType = parser.next()
        }

        return pricePoints
    }

    private fun parseTime(timeStr: String): Long {
        return try {
            val format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm'Z'", Locale.getDefault())
            format.parse(timeStr)?.time ?: System.currentTimeMillis()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    }
} 