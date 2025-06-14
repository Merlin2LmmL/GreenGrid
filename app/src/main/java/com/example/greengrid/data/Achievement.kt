package com.example.greengrid.data

enum class AchievementType {
    FIRST_TRADE,
    ECO_BEGINNER,
    MARKET_MASTER,
    GLAETTUNGSMEISTER,
    PROFIT_100,
    TOP10_CO2
}

data class Achievement(
    val type: AchievementType,
    val title: String,
    val description: String,
    val targetValue: Double,
    val currentValue: Double = 0.0,
    val isUnlocked: Boolean = false
) {
    val progress: Float
        get() = if (targetValue > 0) (currentValue / targetValue).toFloat().coerceIn(0f, 1f) else 0f
} 