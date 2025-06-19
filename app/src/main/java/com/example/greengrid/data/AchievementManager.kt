package com.example.greengrid.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AchievementData(
    val title: String,
    val description: String,
    val targetValue: Double,
    val currentStageValue: Double
)

class AchievementManager(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _achievements = MutableStateFlow<List<Achievement>>(emptyList())
    val achievements: StateFlow<List<Achievement>> = _achievements.asStateFlow()

    init {
        loadAchievements()
    }

    private fun loadAchievements() {
        val achievements = mutableListOf<Achievement>()
        
        // Trading Achievements - Multi-stage (show only current stage)
        val totalTrades = prefs.getFloat("totalTrades", 0f).toDouble()
        
        // Determine current trading stage and if it's completed
        val tradingStage = when {
            totalTrades >= 500 -> 3
            totalTrades >= 100 -> 2
            totalTrades >= 1 -> 1
            else -> 0
        }
        
        if (tradingStage >= 1) {
            val achievementData = when (tradingStage) {
                1 -> AchievementData("Erster Handel", "Führe deinen ersten Kauf oder Verkauf durch", 1.0, totalTrades)
                2 -> AchievementData("Handelsprofi", "Führe 100 Trades durch", 100.0, totalTrades)
                3 -> AchievementData("Handelsmeister", "Führe 500 Trades durch", 500.0, totalTrades)
                else -> AchievementData("", "", 0.0, 0.0)
            }
            
            achievements.add(Achievement(
                type = AchievementType.MARKET_MASTER,
                title = achievementData.title,
                description = achievementData.description,
                targetValue = achievementData.targetValue,
                currentValue = achievementData.currentStageValue,
                isUnlocked = totalTrades >= achievementData.targetValue,
                stage = tradingStage,
                totalStages = 3
            ))
        }
        
        // CO2 Achievements - Multi-stage (show only current stage)
        val co2Saved = prefs.getFloat("co2Saved", 0f).toDouble()
        
        // Determine current CO2 stage and if it's completed
        val co2Stage = when {
            co2Saved >= 10000 -> 3
            co2Saved >= 1000 -> 2
            co2Saved >= 100 -> 1
            else -> 0
        }
        
        if (co2Stage >= 1) {
            val achievementData = when (co2Stage) {
                1 -> AchievementData("Öko-Anfänger", "Spare 100 g CO₂ ein", 100.0, co2Saved)
                2 -> AchievementData("Öko-Experte", "Spare 1.000 g CO₂ ein", 1000.0, co2Saved)
                3 -> AchievementData("Öko-Champion", "Spare 10.000 g CO₂ ein", 10000.0, co2Saved)
                else -> AchievementData("", "", 0.0, 0.0)
            }
            
            achievements.add(Achievement(
                type = AchievementType.ECO_BEGINNER,
                title = achievementData.title,
                description = achievementData.description,
                targetValue = achievementData.targetValue,
                currentValue = achievementData.currentStageValue,
                isUnlocked = co2Saved >= achievementData.targetValue,
                stage = co2Stage,
                totalStages = 3
            ))
        }
        
        // Other achievements (single stage)
        achievements.add(Achievement(
            type = AchievementType.GLAETTUNGSMEISTER,
            title = "Glättungsmeister",
            description = "Speichere Strom über insgesamt 24 Stunden",
            targetValue = 24.0,
            currentValue = prefs.getFloat("smoothingDays", 0f).toDouble(),
            isUnlocked = prefs.getFloat("smoothingDays", 0f) >= 24f
        ))
        
        achievements.add(Achievement(
            type = AchievementType.PROFIT_100,
            title = "Profitjäger",
            description = "Mache 100€ Profit",
            targetValue = 100.0,
            currentValue = prefs.getFloat("profit", 0f).toDouble(),
            isUnlocked = prefs.getFloat("profit", 0f) >= 100f
        ))
        
        achievements.add(Achievement(
            type = AchievementType.TOP10_CO2,
            title = "CO₂-Champion",
            description = "Sei unter den Top 10 CO₂-Einsparern",
            targetValue = 10.0,
            currentValue = prefs.getFloat("top10Co2", 999f).toDouble(),
            isUnlocked = prefs.getFloat("top10Co2", 999f) <= 10f
        ))
        
        // Quiz Master Achievement - Multi-stage (questions answered with 80%+ accuracy)
        val quizQuestionsAnswered = prefs.getFloat("quizQuestionsAnswered", 0f).toDouble()
        
        // Determine current quiz master stage
        val quizMasterStage = when {
            quizQuestionsAnswered >= 50 -> 4
            quizQuestionsAnswered >= 20 -> 3
            quizQuestionsAnswered >= 10 -> 2
            quizQuestionsAnswered >= 5 -> 1
            else -> 0
        }
        
        if (quizMasterStage >= 1) {
            val achievementData = when (quizMasterStage) {
                1 -> AchievementData("Quiz-Anfänger", "Beantworte 5 Fragen mit mindestens 80% Richtigkeit", 5.0, quizQuestionsAnswered)
                2 -> AchievementData("Quiz-Kenner", "Beantworte 10 Fragen mit mindestens 80% Richtigkeit", 10.0, quizQuestionsAnswered)
                3 -> AchievementData("Quiz-Experte", "Beantworte 20 Fragen mit mindestens 80% Richtigkeit", 20.0, quizQuestionsAnswered)
                4 -> AchievementData("Quiz-Meister", "Beantworte 50 Fragen mit mindestens 80% Richtigkeit", 50.0, quizQuestionsAnswered)
                else -> AchievementData("", "", 0.0, 0.0)
            }
            
            achievements.add(Achievement(
                type = AchievementType.QUIZ_MASTER,
                title = achievementData.title,
                description = achievementData.description,
                targetValue = achievementData.targetValue,
                currentValue = achievementData.currentStageValue,
                isUnlocked = quizQuestionsAnswered >= achievementData.targetValue,
                stage = quizMasterStage,
                totalStages = 4
            ))
        }
        
        // Quiz Perfectionist Achievement - Multi-stage (20 questions with increasing accuracy)
        val quizPerfectionistScore = prefs.getFloat("quizPerfectionistScore", 0f).toDouble()
        
        // Determine current quiz perfectionist stage
        val quizPerfectionistStage = when {
            quizPerfectionistScore >= 100 -> 3
            quizPerfectionistScore >= 80 -> 2
            quizPerfectionistScore >= 60 -> 1
            else -> 0
        }
        
        if (quizPerfectionistStage >= 1) {
            val achievementData = when (quizPerfectionistStage) {
                1 -> AchievementData("Quiz-Grundlagen", "Beantworte 20 Fragen mit mindestens 60% Richtigkeit", 60.0, quizPerfectionistScore)
                2 -> AchievementData("Quiz-Fortgeschritten", "Beantworte 20 Fragen mit mindestens 80% Richtigkeit", 80.0, quizPerfectionistScore)
                3 -> AchievementData("Quiz-Perfektionist", "Beantworte 20 Fragen mit 100% Richtigkeit", 100.0, quizPerfectionistScore)
                else -> AchievementData("", "", 0.0, 0.0)
            }
            
            achievements.add(Achievement(
                type = AchievementType.QUIZ_PERFECTIONIST,
                title = achievementData.title,
                description = achievementData.description,
                targetValue = achievementData.targetValue,
                currentValue = achievementData.currentStageValue,
                isUnlocked = quizPerfectionistScore >= achievementData.targetValue,
                stage = quizPerfectionistStage,
                totalStages = 3
            ))
        }
        
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
            AchievementType.QUIZ_MASTER -> "quizQuestionsAnswered"
            AchievementType.QUIZ_PERFECTIONIST -> "quizPerfectionistScore"
        }
        
        // Ensure we don't decrease values for certain achievements
        val currentValue = when (type) {
            AchievementType.TOP10_CO2 -> prefs.getFloat(achievementKey, 999f).toDouble()
            else -> prefs.getFloat(achievementKey, 0f).toDouble()
        }
        val newValue = when (type) {
            AchievementType.FIRST_TRADE -> maxOf(currentValue, value)
            AchievementType.ECO_BEGINNER -> maxOf(currentValue, value)
            AchievementType.MARKET_MASTER -> maxOf(currentValue, value)
            AchievementType.GLAETTUNGSMEISTER -> maxOf(currentValue, value)
            AchievementType.PROFIT_100 -> maxOf(currentValue, value)
            AchievementType.TOP10_CO2 -> minOf(currentValue, value) // Keep the best (lowest) rank
            AchievementType.QUIZ_MASTER -> maxOf(currentValue, value)
            AchievementType.QUIZ_PERFECTIONIST -> maxOf(currentValue, value)
        }
        
        prefs.edit().putFloat(achievementKey, newValue.toFloat()).apply()
        
        // Add logging for TOP10_CO2 achievement
        if (type == AchievementType.TOP10_CO2) {
            android.util.Log.d("AchievementManager", "TOP10_CO2 updated: currentValue=$currentValue, newValue=$newValue, isUnlocked=${newValue <= 10.0}")
        }
        
        loadAchievements() // Reload achievements after update
    }

    fun getCurrentAchievementValue(type: AchievementType): Double {
        val achievementKey = when (type) {
            AchievementType.FIRST_TRADE -> "firstTrade"
            AchievementType.ECO_BEGINNER -> "co2Saved"
            AchievementType.MARKET_MASTER -> "totalTrades"
            AchievementType.GLAETTUNGSMEISTER -> "smoothingDays"
            AchievementType.PROFIT_100 -> "profit"
            AchievementType.TOP10_CO2 -> "top10Co2"
            AchievementType.QUIZ_MASTER -> "quizQuestionsAnswered"
            AchievementType.QUIZ_PERFECTIONIST -> "quizPerfectionistScore"
        }
        return prefs.getFloat(achievementKey, 0f).toDouble()
    }

    fun resetAchievements() {
        prefs.edit().clear().apply()
        loadAchievements()
    }

    companion object {
        private const val PREFS_NAME = "achievements_preferences"
    }
} 