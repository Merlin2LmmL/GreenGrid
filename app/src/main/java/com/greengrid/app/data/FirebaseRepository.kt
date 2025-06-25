package com.greengrid.app.data

import android.util.Log
import com.google.firebase.auth.ktx.auth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import java.time.Instant
import java.time.temporal.ChronoUnit
import kotlin.math.exp

class FirebaseRepository {
    private val auth = Firebase.auth
    private val database = Firebase.database("https://greengrid-c6bc8-default-rtdb.europe-west1.firebasedatabase.app/")
    private val achievementManager = AchievementManager(database.app.applicationContext)

    suspend fun signIn(email: String, password: String): Result<User> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, password).await()
            result.user?.let { firebaseUser ->
                val snapshot = database.getReference("users/${firebaseUser.uid}").get().await()
                val user = snapshot.getValue(User::class.java)
                if (user != null) {
                    Result.success(user)
                } else {
                    Result.failure(Exception("Benutzerdaten nicht gefunden"))
                }
            } ?: Result.failure(Exception("Anmeldung fehlgeschlagen"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun observeUserData(): Flow<User> = callbackFlow {
        val userId = auth.currentUser?.uid ?: throw Exception("User not logged in")
        
        val listener = database.getReference("users/$userId")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java)
                    if (user != null) {
                        // Ensure the user ID is set correctly
                        val updatedUser = user.copy(id = userId)
                        trySend(updatedUser)
                    }
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseRepository", "Error observing user data", error.toException())
                    close(error.toException())
                }
            })
        awaitClose { database.getReference("users/$userId").removeEventListener(listener) }
    }

    suspend fun executeTrade(trade: Trade): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("User not logged in")
            val userRef = database.getReference("users/$userId")
            val userSnapshot = userRef.get().await()
            val user = userSnapshot.getValue(User::class.java) ?: throw Exception("User not found")

            if (trade.isBuy && user.capacity + trade.amount > user.maxCapacity) {
                return Result.failure(Exception("Nicht genug Speicherplatz!"))
            }

            if (!trade.isBuy && user.capacity < trade.amount) {
                return Result.failure(Exception("Nicht genug Strom im Speicher!"))
            }

            // Berechne die Steuer (5%)
            val tax = trade.amount * 0.05
            val finalAmount = trade.amount - tax // Abzüge der Steuer vom Handelsbetrag

            // Berechne die neue Balance
            var newBalance = if (trade.isBuy) {
                // Beim Kauf: Abzug des Preises pro kWh (inkl. Steuer)
                user.balance - (trade.amount * trade.price / 100.0)
            } else {
                // Beim Verkauf: Addition des Preises pro kWh (abzüglich Steuer)
                user.balance + (finalAmount * trade.price / 100.0)
            }

            // 0.2 ct Netzsteuer für jeden Kauf (Um die Datenbank zu entlasten)
            newBalance -= 0.1

            // Berechne die neue Kapazität
            val newCapacity = if (trade.isBuy) {
                user.capacity + trade.amount
            } else {
                user.capacity - trade.amount
            }

            // Berechne die CO2-Einsparung basierend auf der tatsächlichen Energiespeicherung
            val co2Saved = if (trade.isBuy) {
                // Beim Kauf: Keine CO2-Einsparung, da der Strom erst gespeichert wird
                0.0
            } else {
                // Beim Verkauf: CO2-Einsparung basierend auf der gespeicherten Zeit
                // Annahme: Durchschnittlicher CO2-Ausstoß pro kWh in Deutschland: ~400g
                val now = System.currentTimeMillis()
                val lastUpdate = user.lastStorageUpdate
                var storageTimeHours = (now - lastUpdate) / (1000.0 * 60 * 60)
                if (storageTimeHours < 0) storageTimeHours = 0.0
                if (storageTimeHours > 48) storageTimeHours = 48.0 // Maximal 2 Tage berücksichtigen
                val baseCo2Saving = 400.0 // Basis-CO2-Einsparung pro kWh
                val timeMultiplier = minOf(storageTimeHours / 24.0, 1.0) // Maximaler Multiplikator nach 24h
                trade.amount * baseCo2Saving * timeMultiplier
            }

            // Update user data
            val updatedUser = user.copy(
                id = userId, // Ensure ID is set correctly
                balance = newBalance,
                capacity = newCapacity,
                totalBought = if (trade.isBuy) user.totalBought + trade.amount else user.totalBought,
                totalSold = if (!trade.isBuy) user.totalSold + trade.amount else user.totalSold,
                averagePurchasePrice = if (trade.isBuy) {
                    // Calculate new average purchase price
                    val totalSpent = user.averagePurchasePrice * user.totalBought + (trade.amount * trade.price)
                    val totalBought = user.totalBought + trade.amount
                    if (totalBought > 0) totalSpent / totalBought else 0.0
                } else user.averagePurchasePrice,
                co2Saved = (user.co2Saved + co2Saved),
                lastStorageUpdate = (if (trade.isBuy) System.currentTimeMillis() else user.lastStorageUpdate),
                totalStorageHours = user.totalStorageHours // Will be updated below if needed
            )

            // Update user data in database
            userRef.setValue(updatedUser).await()

            // Calculate real trading profit only when selling at a higher price than average purchase price
            val currentProfit = if (!trade.isBuy && user.averagePurchasePrice > 0) {
                val profitPerKwh = trade.price - user.averagePurchasePrice
                if (profitPerKwh > 0) {
                    profitPerKwh * trade.amount // Only count positive profit
                } else {
                    0.0 // No profit if selling at lower price
                }
            } else {
                0.0
            }

            // Update achievements
            achievementManager.updateAchievement(AchievementType.FIRST_TRADE, 1.0)
            achievementManager.updateAchievement(AchievementType.ECO_BEGINNER, updatedUser.co2Saved)
            achievementManager.updateAchievement(AchievementType.MARKET_MASTER, updatedUser.totalBought + updatedUser.totalSold)
            achievementManager.updateAchievement(AchievementType.PROFIT_100, newBalance)

            // Update Glättungsmeister achievement - Gesamtspeicherzeit über alle Sessions
            if (trade.isBuy) {
                // Beim Kauf: Speichere den Zeitpunkt
                userRef.child("lastStorageUpdate").setValue(System.currentTimeMillis()).await()
            } else {
                // Beim Verkauf: Berechne die Speicherzeit für diese Session und addiere zur Gesamtspeicherzeit
                val storageTimeHours = (System.currentTimeMillis() - user.lastStorageUpdate) / (1000.0 * 60 * 60)
                val newTotalStorageHours = user.totalStorageHours + storageTimeHours
                
                // Update totalStorageHours in database
                userRef.child("totalStorageHours").setValue(newTotalStorageHours).await()
                
                // Update achievement with total storage hours (target: 24 hours)
                achievementManager.updateAchievement(AchievementType.GLAETTUNGSMEISTER, newTotalStorageHours)
                
                Log.d("TradeDebug", "Storage session: ${storageTimeHours}h, Total storage: ${newTotalStorageHours}h")
            }

            // Update CO2 Champion achievement
            val allUsers = readAllUsersDirectly()
            val userRank = allUsers
                .filter { it.co2Saved > 0 }
                .sortedByDescending { it.co2Saved }
                .indexOfFirst { it.id == userId }
                .let { if (it == -1) allUsers.size else it + 1 }

            // Fix: Store the actual rank (lower is better for top 10)
            // If user is in top 10, store their rank (1-10), otherwise store a large number
            val achievementValue = if (userRank <= 10) userRank.toDouble() else 999.0
            
            achievementManager.updateAchievement(
                AchievementType.TOP10_CO2,
                achievementValue
            )
            Log.d("TradeDebug", "Position in CO2 Leaderboard: $userRank, Achievement value: $achievementValue")

            // Store the trade in the database with correct format
            val tradeRef = database.getReference("market/trades").push()
            val tradeData = mapOf(
                "amount" to trade.amount,
                "isBuy" to trade.isBuy,
                "price" to trade.price,
                "timestamp" to trade.timestamp,
                "userId" to userId,
                "finalAmount" to finalAmount
            )
            tradeRef.setValue(tradeData).await()

            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("TradeDebug", "Error executing trade", e)
            Result.failure(e)
        }
    }

    fun observePriceHistory(): Flow<List<PricePoint>> = callbackFlow {
        val listener = database.getReference("market/trades")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val trades = snapshot.children.mapNotNull { tradeSnapshot ->
                        try {
                            val data = tradeSnapshot.value as? Map<*, *> ?: return@mapNotNull null
                            Trade(
                                amount = (data["amount"] as? Number)?.toDouble() ?: return@mapNotNull null,
                                isBuy = data["isBuy"] as? Boolean ?: return@mapNotNull null,
                                price = (data["price"] as? Number)?.toDouble() ?: return@mapNotNull null,
                                timestamp = (data["timestamp"] as? Number)?.toLong() ?: return@mapNotNull null,
                                userId = data["userId"] as? String ?: return@mapNotNull null
                            )
                        } catch (e: Exception) {
                            Log.e("FirebaseRepository", "Error parsing trade", e)
                            null
                        }
                    }.sortedBy { it.timestamp }
                    
                    // Generate base price history for a longer range to capture historical trades
                    val basePrices = simulatePriceHistory(
                        rangeHours = 24 * 7, // 1 Woche für bessere historische Abdeckung
                        basePrice = PriceSimulationConfigs.defaultParams.basePrice,
                        seasonalAmp = PriceSimulationConfigs.defaultParams.seasonalAmp,
                        dailyAmp = PriceSimulationConfigs.defaultParams.dailyAmp,
                        weekendOffset = PriceSimulationConfigs.defaultParams.weekendOffset,
                        noiseAmpLong = PriceSimulationConfigs.defaultParams.noiseAmpLong,
                        noiseAmpShort = PriceSimulationConfigs.defaultParams.noiseAmpShort,
                        seedLong = PriceSimulationConfigs.defaultParams.seedLong,
                        seedShort = PriceSimulationConfigs.defaultParams.seedShort,
                        intervalHours = 1 // Stündliche Auflösung für präzise Trade-Impacts
                    )
                    
                    // Calculate price impact from trades
                    val priceByHour = basePrices.associate { it.timestamp to it.price }.toMutableMap()
                    val volumeByHour = basePrices.associate { it.timestamp to it.volume }.toMutableMap()
                    
                    val decayHours = 8
                    val decayK = 0.7
                    
                    // Hilfsstruktur: Trades nach Stunde gruppieren
                    val tradesByHour = trades.groupBy { trade ->
                        Instant.ofEpochMilli(trade.timestamp).truncatedTo(ChronoUnit.HOURS).toEpochMilli()
                    }
                    
                    for (trade in trades) {
                        val tradeHour = Instant.ofEpochMilli(trade.timestamp)
                            .truncatedTo(ChronoUnit.HOURS)
                            .toEpochMilli()
                        
                        // Anzahl Trades in der letzten Stunde (inkl. diesem Trade)
                        val recentTrades = trades.filter {
                            val t = it.timestamp
                            t >= trade.timestamp - 3600000 && t <= trade.timestamp
                        }
                        val recentTradeCount = recentTrades.size
                        val impactScale = 1.0 / kotlin.math.sqrt(1.0 + recentTradeCount)
                        
                        // Calculate trade impact - FIXED: Now sells decrease price and buys increase it
                        val baseImpact = if (trade.isBuy) {
                            trade.amount * 0.05  // Kauf erhöht den Preis stärker
                        } else {
                            -trade.amount * 0.05  // Verkauf senkt den Preis stärker
                        }
                        val scaledImpact = baseImpact * impactScale
                        
                        // Apply impact to current hour and future hours
                        for (n in 0 until decayHours) {
                            val targetHour = tradeHour + n * 3600000 // Add n hours in milliseconds
                            val currentPrice = priceByHour[targetHour] ?: continue
                            
                            // Berechne den Impact mit exponentieller Abnahme
                            val impact = scaledImpact * exp(-decayK * n)
                            val newPrice = currentPrice + impact
                            
                            // Stelle sicher, dass der Preis nicht unter 0 fällt
                            priceByHour[targetHour] = maxOf(0.0, newPrice)
                            
                            // Aktualisiere das Volumen
                            volumeByHour[targetHour] = (volumeByHour[targetHour] ?: 0.0) + trade.amount
                        }
                    }
                    
                    // Convert to PricePoint list
                    val pricePoints = priceByHour.map { (timestamp, price) ->
                        PricePoint(
                            timestamp = timestamp,
                            price = price,
                            volume = volumeByHour[timestamp] ?: 0.0
                        )
                    }.sortedBy { it.timestamp }

                    trySend(pricePoints)
                }

                override fun onCancelled(error: DatabaseError) {
                    Log.e("FirebaseRepository", "Error loading trade history", error.toException())
                    close(error.toException())
                }
            })
        awaitClose { database.getReference("market/trades").removeEventListener(listener) }
    }

    suspend fun cleanupOldData() {
        try {
            // Delete trades older than 1 week (since we only use 1 week for price impact calculation)
            val now = Instant.now()
            val oneWeekAgo = now.minus(7, ChronoUnit.DAYS)
            val tradesRef = database.getReference("market/trades")
            val tradesSnapshot = tradesRef.get().await()
            
            var deletedCount = 0
            for (tradeSnapshot in tradesSnapshot.children) {
                try {
                    val trade = tradeSnapshot.getValue(Trade::class.java)
                    if (trade != null && trade.timestamp < oneWeekAgo.toEpochMilli()) {
                        tradeSnapshot.ref.removeValue().await()
                        deletedCount++
                    }
                } catch (e: Exception) {
                    Log.e("FirebaseRepository", "Error cleaning up trade data", e)
                }
            }
            if (deletedCount > 0) {
                Log.d("FirebaseRepository", "Successfully cleaned up old trade data. Deleted $deletedCount old trades")
            }
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error during data cleanup", e)
        }
    }

    suspend fun readAllUsersDirectly(): List<User> {
        val usersRef = database.getReference("users")
        
        try {
            // Read all users in one go
            val snapshot = usersRef.get().await()
            val users = mutableListOf<User>()
            
            for (userSnapshot in snapshot.children) {
                try {
                    val id = userSnapshot.key ?: continue
                    val data = userSnapshot.value as? Map<*, *> ?: continue
                    
                    users.add(User(
                        id = id,
                        email = data["email"] as? String ?: "",
                        username = data["username"] as? String ?: "",
                        balance = (data["balance"] as? Number)?.toDouble() ?: 0.0,
                        capacity = (data["capacity"] as? Number)?.toDouble() ?: 0.0,
                        maxCapacity = (data["maxCapacity"] as? Number)?.toDouble() ?: 100.0,
                        totalBought = (data["totalBought"] as? Number)?.toDouble() ?: 0.0,
                        totalSold = (data["totalSold"] as? Number)?.toDouble() ?: 0.0,
                        averagePurchasePrice = (data["averagePurchasePrice"] as? Number)?.toDouble() ?: 0.0,
                        co2Saved = (data["co2Saved"] as? Number)?.toDouble() ?: 0.0,
                        lastStorageUpdate = (data["lastStorageUpdate"] as? Number)?.toLong() ?: System.currentTimeMillis(),
                        totalStorageHours = (data["totalStorageHours"] as? Number)?.toDouble() ?: 0.0
                    ))
                } catch (e: Exception) {
                    Log.e("FirebaseRepository", "Error processing user data for ID: ${userSnapshot.key}", e)
                }
            }
            return users
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error reading users from database", e)
            throw e
        }
    }

    suspend fun resetPassword(email: String): Result<Unit> {
        return try {
            auth.sendPasswordResetEmail(email).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getCurrentUser(): User? {
        return try {
            val userId = auth.currentUser?.uid ?: return null
            val snapshot = database.getReference("users/$userId").get().await()
            val user = snapshot.getValue(User::class.java)
            user?.copy(id = userId)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error getting current user", e)
            null
        }
    }

    suspend fun updateUser(user: User): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("User not logged in")
            val userRef = database.getReference("users/$userId")
            
            // Update the user data in the database
            userRef.setValue(user).await()
            
            Log.d("FirebaseRepository", "User updated successfully: balance=${user.balance}, maxCapacity=${user.maxCapacity}")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error updating user", e)
            Result.failure(e)
        }
    }

    suspend fun submitReport(reportedMessage: String, reason: String): Result<Report> {
        return try {
            val currentUser = auth.currentUser
            if (currentUser == null) {
                return Result.failure(Exception("Nicht eingeloggt"))
            }

            val reportRef = database.getReference("reports").push()
            val report = Report(
                id = reportRef.key ?: "",
                userId = currentUser.uid,
                reportedMessage = reportedMessage,
                reason = reason
            )
            
            reportRef.setValue(report).await()
            Result.success(report)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
} 