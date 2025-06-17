package com.example.greengrid.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AchievementManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _achievements = MutableStateFlow<List<Achievement>>(emptyList())
    val achievements: StateFlow<List<Achievement>> = _achievements.asStateFlow()

    init {
        loadAchievements()
    }

    private fun loadAchievements() {
        val achievements = listOf(
            Achievement(
                type = AchievementType.FIRST_TRADE,
                title = "Erster Handel",
                description = "Führe deinen ersten Kauf oder Verkauf durch",
                targetValue = 1.0,
                currentValue = prefs.getFloat("firstTrade", 0f).toDouble(),
                isUnlocked = prefs.getFloat("firstTrade", 0f) >= 1f
            ),
            Achievement(
                type = AchievementType.ECO_BEGINNER,
                title = "Öko-Anfänger",
                description = "Spare 100 g CO₂ ein",
                targetValue = 100.0,
                currentValue = prefs.getFloat("co2Saved", 0f).toDouble(),
                isUnlocked = prefs.getFloat("co2Saved", 0f) >= 100f
            ),
            Achievement(
                type = AchievementType.MARKET_MASTER,
                title = "Marktmeister",
                description = "Führe 200 Trades durch",
                targetValue = 200.0,
                currentValue = prefs.getFloat("totalTrades", 0f).toDouble(),
                isUnlocked = prefs.getFloat("totalTrades", 0f) >= 200f
            ),
            Achievement(
                type = AchievementType.GLAETTUNGSMEISTER,
                title = "Glättungsmeister",
                description = "Speichere Strom über 7 Tage",
                targetValue = 7.0,
                currentValue = prefs.getFloat("smoothingDays", 0f).toDouble(),
                isUnlocked = prefs.getFloat("smoothingDays", 0f) >= 7f
            ),
            Achievement(
                type = AchievementType.PROFIT_100,
                title = "Profitjäger",
                description = "Mache 100€ Profit",
                targetValue = 100.0,
                currentValue = prefs.getFloat("profit", 0f).toDouble(),
                isUnlocked = prefs.getFloat("profit", 0f) >= 100f
            ),
            Achievement(
                type = AchievementType.TOP10_CO2,
                title = "CO₂-Champion",
                description = "Sei unter den Top 10 CO₂-Einsparern",
                targetValue = 10.0,
                currentValue = prefs.getFloat("top10Co2", 0f).toDouble(),
                isUnlocked = prefs.getFloat("top10Co2", 0f) <= 10f
            )
        )
        _achievements.value = achievements
    }

    fun updateAchievement(type: AchievementType, value: Double) {
        val achievementKey = when (type) {
            AchievementType.FIRST_TRADE -> "firstTrade"
            AchievementType.ECO_BEGINNER -> "co2Saved"
            AchievementType.MARKET_MASTER -> "totalTrades"
            AchievementType.GLAETTUNGSMEISTER -> "smoothingDays"
            AchievementType.PROFIT_100 -> "profit"
            AchievementType.TOP10_CO2 -> "top10Co2"
        }
        
        // Ensure we don't decrease values for certain achievements
        val currentValue = prefs.getFloat(achievementKey, 0f).toDouble()
        val newValue = when (type) {
            AchievementType.FIRST_TRADE -> maxOf(currentValue, value)
            AchievementType.ECO_BEGINNER -> maxOf(currentValue, value)
            AchievementType.MARKET_MASTER -> maxOf(currentValue, value)
            AchievementType.GLAETTUNGSMEISTER -> maxOf(currentValue, value)
            AchievementType.PROFIT_100 -> maxOf(currentValue, value)
            AchievementType.TOP10_CO2 -> maxOf(currentValue, value)
        }
        
        prefs.edit().putFloat(achievementKey, newValue.toFloat()).apply()
        loadAchievements() // Reload achievements after update
    }

    fun resetAchievements() {
        prefs.edit().clear().apply()
        loadAchievements()
    }

    companion object {
        private const val PREFS_NAME = "achievements_preferences"
    }
} 