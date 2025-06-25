package com.greengrid.app.data

data class PriceSimulationConfig(
    val rangeHours: Long,
    val intervalHours: Long,
    val basePrice: Double,
    val seasonalAmp: Double,
    val dailyAmp: Double,
    val weekendOffset: Double,
    val noiseAmpLong: Double,
    val noiseAmpShort: Double,
    val seedLong: Long,
    val seedShort: Long
)

object PriceSimulationConfigs {
    // Standard-Parameter für die Preissimulation
    val defaultParams = PriceSimulationConfig(
        rangeHours = 1,
        intervalHours = 1,
        basePrice = 25.0,
        seasonalAmp = -7.0,
        dailyAmp = -10.0,
        weekendOffset = -4.0,
        noiseAmpLong = 1.5,
        noiseAmpShort = 0.5,
        seedLong = 12345L,
        seedShort = 54321L
    )

    // Konfiguration für die Tagesansicht (24 Stunden, stündlich)
    val dayConfig = defaultParams.copy(
        rangeHours = 24,
        intervalHours = 1
    )

    // Konfiguration für die Wochenansicht (7 Tage, 3-stündlich)
    val weekConfig = defaultParams.copy(
        rangeHours = 24 * 7,
        intervalHours = 3
    )

    // Konfiguration für die Monatsansicht (31 Tage, täglich)
    val monthConfig = defaultParams.copy(
        rangeHours = 24 * 31,
        intervalHours = 24
    )

    // Konfiguration für die Jahresansicht (365 Tage, monatlich)
    val yearConfig = defaultParams.copy(
        rangeHours = 24 * 365,
        intervalHours = 24 * 7  // Ein Monat zwischen den Punkten
    )

    fun getConfigForTimeRange(selectedRangeIndex: Int): PriceSimulationConfig {
        return when (selectedRangeIndex) {
            0 -> dayConfig
            1 -> weekConfig
            2 -> monthConfig
            else -> yearConfig
        }
    }
} 