package com.example.greengrid.data

import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class AchievementManager(private val database: FirebaseDatabase) {
    private val achievementsRef = database.getReference("achievements")

    fun getUserAchievements(userId: String): Flow<List<Achievement>> = callbackFlow {
        val listener = achievementsRef.child(userId).addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val achievements = mutableListOf<Achievement>()
                
                // Default achievements
                achievements.addAll(listOf(
                    Achievement(
                        type = AchievementType.FIRST_TRADE,
                        title = "Erster Handel",
                        description = "Führe deinen ersten Kauf oder Verkauf durch",
                        targetValue = 1.0,
                        currentValue = snapshot.child("firstTrade").getValue(Double::class.java) ?: 0.0,
                        isUnlocked = snapshot.child("firstTrade").getValue(Double::class.java) ?: 0.0 >= 1.0
                    ),
                    Achievement(
                        type = AchievementType.ECO_BEGINNER,
                        title = "Öko-Anfänger",
                        description = "Spare 100 g CO₂ ein",
                        targetValue = 100.0,
                        currentValue = snapshot.child("co2Saved").getValue(Double::class.java) ?: 0.0,
                        isUnlocked = snapshot.child("co2Saved").getValue(Double::class.java) ?: 0.0 >= 100.0
                    ),
                    Achievement(
                        type = AchievementType.MARKET_MASTER,
                        title = "Marktmeister",
                        description = "Führe 200 Trades durch",
                        targetValue = 200.0,
                        currentValue = snapshot.child("totalTrades").getValue(Double::class.java) ?: 0.0,
                        isUnlocked = snapshot.child("totalTrades").getValue(Double::class.java) ?: 0.0 >= 200.0
                    ),
                    Achievement(
                        type = AchievementType.GLAETTUNGSMEISTER,
                        title = "Glättungsmeister",
                        description = "Kaufe 7 Tage lang unter dem Tagesdurchschnitt",
                        targetValue = 7.0,
                        currentValue = snapshot.child("smoothingDays").getValue(Double::class.java) ?: 0.0,
                        isUnlocked = snapshot.child("smoothingDays").getValue(Double::class.java) ?: 0.0 >= 7.0
                    ),
                    Achievement(
                        type = AchievementType.PROFIT_100,
                        title = "Profit 100",
                        description = "Erreiche 100€ virtuellen Profit",
                        targetValue = 100.0,
                        currentValue = snapshot.child("profit").getValue(Double::class.java) ?: 0.0,
                        isUnlocked = snapshot.child("profit").getValue(Double::class.java) ?: 0.0 >= 100.0
                    ),
                    Achievement(
                        type = AchievementType.TOP10_CO2,
                        title = "Top 10 CO₂",
                        description = "Erreiche die Top 10% der CO₂-Rangliste",
                        targetValue = 1.0,
                        currentValue = snapshot.child("top10Co2").getValue(Double::class.java) ?: 0.0,
                        isUnlocked = snapshot.child("top10Co2").getValue(Double::class.java) ?: 0.0 >= 1.0
                    )
                ))
                
                trySend(achievements)
            }

            override fun onCancelled(error: DatabaseError) {
                // Handle error
            }
        })

        awaitClose {
            achievementsRef.child(userId).removeEventListener(listener)
        }
    }

    suspend fun updateAchievement(userId: String, type: AchievementType, value: Double) {
        val achievementKey = when (type) {
            AchievementType.FIRST_TRADE -> "firstTrade"
            AchievementType.ECO_BEGINNER -> "co2Saved"
            AchievementType.MARKET_MASTER -> "totalTrades"
            AchievementType.GLAETTUNGSMEISTER -> "smoothingDays"
            AchievementType.PROFIT_100 -> "profit"
            AchievementType.TOP10_CO2 -> "top10Co2"
        }
        
        // Ensure we don't decrease values for certain achievements
        val currentValue = achievementsRef.child(userId).child(achievementKey).get().await().getValue(Double::class.java) ?: 0.0
        val newValue = when (type) {
            AchievementType.FIRST_TRADE -> maxOf(currentValue, value)
            AchievementType.ECO_BEGINNER -> maxOf(currentValue, value)
            AchievementType.MARKET_MASTER -> maxOf(currentValue, value)
            AchievementType.GLAETTUNGSMEISTER -> maxOf(currentValue, value)
            AchievementType.PROFIT_100 -> maxOf(currentValue, value)
            AchievementType.TOP10_CO2 -> maxOf(currentValue, value)
        }
        
        achievementsRef.child(userId).child(achievementKey).setValue(newValue).await()
    }

    suspend fun resetAchievements(userId: String) {
        val defaultValues = mapOf(
            "firstTrade" to 0.0,
            "co2Saved" to 0.0,
            "totalTrades" to 0.0,
            "smoothingDays" to 0.0,
            "profit" to 0.0,
            "top10Co2" to 0.0
        )
        
        achievementsRef.child(userId).setValue(defaultValues).await()
    }
} 