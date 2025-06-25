package com.greengrid.app.data

enum class AchievementType {
    FIRST_TRADE,
    ECO_BEGINNER,
    MARKET_MASTER,
    GLAETTUNGSMEISTER,
    PROFIT_100,
    TOP10_CO2,
    QUIZ_MASTER,
    QUIZ_PERFECTIONIST
}

data class Achievement(
    val type: AchievementType,
    val title: String,
    val description: String,
    val targetValue: Double,
    val currentValue: Double,
    val isUnlocked: Boolean = false,
    val stage: Int = 1,
    val totalStages: Int = 1
) {
    val progress: Float
        get() = when (type) {
            AchievementType.TOP10_CO2 -> {
                // For TOP10_CO2, lower rank is better
                // If rank is 999 (not in top 10), progress is 0
                // If rank is 10 or better, progress is 1
                // Otherwise, progress is (11 - currentRank) / 10
                if (currentValue >= 999.0) 0f
                else if (currentValue <= 10.0) 1f
                else ((11.0 - currentValue) / 10.0).toFloat().coerceIn(0f, 1f)
            }
            else -> if (targetValue > 0) (currentValue / targetValue).toFloat().coerceIn(0f, 1f) else 0f
        }
}

object MarketMasterStages {
    data class Stage(val title: String, val description: String, val targetValue: Double)
    val stages = listOf(
        Stage("Erster Handel", "Führe deinen ersten Kauf oder Verkauf durch", 1.0),
        Stage("Handelsprofi", "Führe 100 Trades durch", 100.0),
        Stage("Handelsmeister", "Führe 500 Trades durch", 500.0)
    )
}