package com.example.greengrid.data

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
    
    val stageProgress: Float
        get() = if (totalStages > 1) {
            // Calculate overall progress across all stages
            val completedStages = stage - 1
            val currentStageProgress = progress
            (completedStages + currentStageProgress) / totalStages
        } else {
            progress
        }
    
    val overallProgress: Float
        get() = when (type) {
            AchievementType.MARKET_MASTER -> {
                // For trading: 1 trade = stage 1, 100 trades = stage 2, 500 trades = stage 3
                when {
                    currentValue >= 500 -> 1.0f
                    currentValue >= 100 -> {
                        val stage2Progress = ((currentValue - 100.0) / 400.0).toFloat()
                        (0.2f + stage2Progress * 0.8f).coerceIn(0f, 1f) // Stage 2: 20%-100% of total progress
                    }
                    currentValue >= 1 -> {
                        val stage1Progress = ((currentValue - 1.0) / 99.0).toFloat()
                        (stage1Progress * 0.2f).coerceIn(0f, 1f) // Stage 1: 0%-20% of total progress
                    }
                    else -> 0.0f
                }
            }
            AchievementType.ECO_BEGINNER -> {
                // For CO2: 100g = stage 1, 1000g = stage 2, 10000g = stage 3
                when {
                    currentValue >= 10000 -> 1.0f
                    currentValue >= 1000 -> {
                        val stage3Progress = ((currentValue - 1000.0) / 9000.0).toFloat()
                        (0.67f + stage3Progress * 0.33f).coerceIn(0f, 1f)
                    }
                    currentValue >= 100 -> {
                        val stage2Progress = ((currentValue - 100.0) / 900.0).toFloat()
                        (0.33f + stage2Progress * 0.34f).coerceIn(0f, 1f)
                    }
                    else -> {
                        val stage1Progress = (currentValue / 100.0).toFloat()
                        (stage1Progress * 0.33f).coerceIn(0f, 1f)
                    }
                }
            }
            AchievementType.QUIZ_MASTER -> {
                // For quiz master: 5 questions = stage 1, 10 questions = stage 2, 20 questions = stage 3, 50 questions = stage 4
                when {
                    currentValue >= 50 -> 1.0f
                    currentValue >= 20 -> {
                        val stage4Progress = ((currentValue - 20.0) / 30.0).toFloat()
                        (0.75f + stage4Progress * 0.25f).coerceIn(0f, 1f)
                    }
                    currentValue >= 10 -> {
                        val stage3Progress = ((currentValue - 10.0) / 10.0).toFloat()
                        (0.5f + stage3Progress * 0.25f).coerceIn(0f, 1f)
                    }
                    currentValue >= 5 -> {
                        val stage2Progress = ((currentValue - 5.0) / 5.0).toFloat()
                        (0.25f + stage2Progress * 0.25f).coerceIn(0f, 1f)
                    }
                    else -> {
                        val stage1Progress = (currentValue / 5.0).toFloat()
                        (stage1Progress * 0.25f).coerceIn(0f, 1f)
                    }
                }
            }
            AchievementType.QUIZ_PERFECTIONIST -> {
                // For quiz perfectionist: 60% = stage 1, 80% = stage 2, 100% = stage 3
                when {
                    currentValue >= 100 -> 1.0f
                    currentValue >= 80 -> {
                        val stage3Progress = ((currentValue - 80.0) / 20.0).toFloat()
                        (0.67f + stage3Progress * 0.33f).coerceIn(0f, 1f)
                    }
                    currentValue >= 60 -> {
                        val stage2Progress = ((currentValue - 60.0) / 20.0).toFloat()
                        (0.33f + stage2Progress * 0.34f).coerceIn(0f, 1f)
                    }
                    else -> {
                        val stage1Progress = (currentValue / 60.0).toFloat()
                        (stage1Progress * 0.33f).coerceIn(0f, 1f)
                    }
                }
            }
            else -> progress
        }
} 