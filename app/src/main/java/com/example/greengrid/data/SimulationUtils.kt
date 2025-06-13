package com.example.greengrid.data

import java.time.DayOfWeek
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.util.Random

import com.example.greengrid.data.PricePoint

fun simulatePriceHistory(
    rangeHours: Long,
    intervalHours: Long,
    basePrice: Double = 25.0,
    seasonalAmp: Double = 5.0,
    dailyAmp: Double = 3.0,
    weekendOffset: Double = 2.0,
    noiseAmpLong: Double = 1.5,
    noiseAmpShort: Double = 0.7,
    seedLong: Long = 12345L,
    seedShort: Long = 54321L
): List<PricePoint> {
    require(intervalHours > 0) { "intervalHours muss größer als 0 sein" }
    val now = Instant.ofEpochMilli(System.currentTimeMillis()).truncatedTo(ChronoUnit.HOURS)
    val pointsCount = (rangeHours / intervalHours).toInt() + 1
    val startHour = now.minus((pointsCount - 1L) * intervalHours, ChronoUnit.HOURS)
    val zone = ZoneId.systemDefault()

    val randLong = Random(seedLong)
    val randShort = Random(seedShort)
    val noiseLongList = List(pointsCount) { randLong.nextGaussian() * noiseAmpLong }
    val noiseShortList = List(pointsCount) { randShort.nextGaussian() * noiseAmpShort }

    return List(pointsCount) { idx ->
        val hourInstant = startHour.plus(idx * intervalHours, ChronoUnit.HOURS)
        val zdt = hourInstant.atZone(zone)
        val dayOfYear = zdt.dayOfYear.toDouble()
        val hourOfDay = zdt.hour.toDouble()
        val dayOfWeek = zdt.dayOfWeek

        val seasonal = seasonalAmp * kotlin.math.sin(2 * Math.PI * (dayOfYear / 365.0))
        val phaseShift = -Math.PI / 2
        val daily = dailyAmp * kotlin.math.sin(2 * Math.PI * (hourOfDay / 24.0) + phaseShift)
        val isWeekend = (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY)
        val weekend = if (isWeekend) weekendOffset else 0.0
        val noiseLong = noiseLongList[idx]
        val noiseShort = noiseShortList[idx]
        var price = basePrice + seasonal + daily + weekend + noiseLong + noiseShort
        if (price < 5.0) price = 5.0
        PricePoint(timestamp = hourInstant.toEpochMilli(), price = price)
    }
} 