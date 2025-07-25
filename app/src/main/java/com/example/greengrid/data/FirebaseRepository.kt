package com.example.greengrid.data

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
import com.google.firebase.firestore.DocumentSnapshot
import kotlin.math.abs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class FirebaseRepository {
    private val auth = Firebase.auth
    private val database = Firebase.database("https://greengrid-c6bc8-default-rtdb.europe-west1.firebasedatabase.app/")
    private val achievementManager = AchievementManager(database)

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
        Log.d("FirebaseRepository", "Observing user data for ID: $userId")
        
        val listener = database.getReference("users/$userId")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java)
                    if (user != null) {
                        // Ensure the user ID is set correctly
                        val updatedUser = user.copy(id = userId)
                        Log.d("FirebaseRepository", "User data updated: $updatedUser")
                        trySend(updatedUser)
                    } else {
                        Log.e("FirebaseRepository", "No user data found for ID: $userId")
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

            // Berechne die Steuer (10%)
            val tax = trade.amount * 0.05
            val finalAmount = trade.amount - tax // Abzüge der Steuer vom Handelsbetrag

            // Berechne die neue Balance
            val newBalance = if (trade.isBuy) {
                user.balance - finalAmount
            } else {
                user.balance + finalAmount
            }

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
                // Je länger der Strom gespeichert war, desto höher die Einsparung
                val storageTimeHours = (System.currentTimeMillis() - user.lastStorageUpdate) / (1000.0 * 60 * 60)
                val baseCo2Saving = 400.0 // Basis-CO2-Einsparung pro kWh
                val timeMultiplier = minOf(storageTimeHours / 24.0, 1.0) // Maximaler Multiplikator nach 24h

                trade.amount * baseCo2Saving * timeMultiplier
            }
            Log.d("FirebaseRepository", "Calculated CO2 savings: $co2Saved")

            // Update user data
            val updatedUser = user.copy(
                id = userId, // Ensure ID is set correctly
                balance = newBalance,
                capacity = newCapacity,
                totalBought = if (trade.isBuy) user.totalBought + trade.amount else user.totalBought,
                totalSold = if (!trade.isBuy) user.totalSold + trade.amount else user.totalSold,
                co2Saved = (user.co2Saved + co2Saved),
                lastStorageUpdate = (if (trade.isBuy) System.currentTimeMillis() else user.lastStorageUpdate)
            )

            // Update user data in database
            userRef.setValue(updatedUser).await()

            // Store the trade in the database with correct format
            val tradeRef = database.getReference("market/trades").push()
            val tradeData = mapOf(
                "amount" to trade.amount,
                "isBuy" to trade.isBuy,
                "price" to trade.price,
                "timestamp" to trade.timestamp,
                "userId" to userId,
                "tax" to tax,
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
                    
                    Log.d("FirebaseRepository", "Processing ${trades.size} trades")
                    
                    // Generate base price history
                    val basePrices = simulatePriceHistory(
                        rangeHours = PriceSimulationConfigs.defaultParams.rangeHours,
                        basePrice = PriceSimulationConfigs.defaultParams.basePrice,
                        seasonalAmp = PriceSimulationConfigs.defaultParams.seasonalAmp,
                        dailyAmp = PriceSimulationConfigs.defaultParams.dailyAmp,
                        weekendOffset = PriceSimulationConfigs.defaultParams.weekendOffset,
                        noiseAmpLong = PriceSimulationConfigs.defaultParams.noiseAmpLong,
                        noiseAmpShort = PriceSimulationConfigs.defaultParams.noiseAmpShort,
                        seedLong = PriceSimulationConfigs.defaultParams.seedLong,
                        seedShort = PriceSimulationConfigs.defaultParams.seedShort,
                        intervalHours = PriceSimulationConfigs.defaultParams.intervalHours
                    )

                    Log.d("FirebaseRepository", "Generated ${basePrices.size} base price points")
                    
                    // Calculate price impact from trades
                    val priceByHour = basePrices.associate { it.timestamp to it.price }.toMutableMap()
                    val volumeByHour = basePrices.associate { it.timestamp to it.volume }.toMutableMap()
                    
                    val decayHours = 6
                    val decayK = 0.7
                    
                    for (trade in trades) {
                        val tradeHour = Instant.ofEpochMilli(trade.timestamp)
                            .truncatedTo(ChronoUnit.HOURS)
                            .toEpochMilli()
                        
                        // Calculate trade impact - FIXED: Now sells decrease price and buys increase it
                        val baseImpact = if (trade.isBuy) {
                            trade.amount * 0.02  // Kauf erhöht den Preis stärker
                        } else {
                            -trade.amount * 0.02  // Verkauf senkt den Preis stärker
                        }
                        
                        Log.d("FirebaseRepository", "Trade: isBuy=${trade.isBuy}, amount=${trade.amount}, baseImpact=$baseImpact")
                        
                        // Apply impact to current hour and future hours
                        for (n in 0 until decayHours) {
                            val targetHour = tradeHour + n * 3600000 // Add n hours in milliseconds
                            val currentPrice = priceByHour[targetHour] ?: continue
                            
                            // Berechne den Impact mit exponentieller Abnahme
                            val impact = baseImpact * exp(-decayK * n)
                            val newPrice = currentPrice + impact
                            
                            // Stelle sicher, dass der Preis nicht unter 0 fällt
                            priceByHour[targetHour] = maxOf(0.0, newPrice)
                            
                            // Aktualisiere das Volumen
                            volumeByHour[targetHour] = (volumeByHour[targetHour] ?: 0.0) + trade.amount
                            
                            Log.d("FirebaseRepository", "Hour $n: currentPrice=$currentPrice, impact=$impact, newPrice=${priceByHour[targetHour]}")
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
                    
                    Log.d("FirebaseRepository", "Final prices: first=${pricePoints.firstOrNull()?.price}, last=${pricePoints.lastOrNull()?.price}")
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
            // Delete trades older than 1 year
            val now = Instant.now()
            val oneYearAgo = now.minus(365, ChronoUnit.DAYS)
            val tradesRef = database.getReference("market/trades")
            val tradesSnapshot = tradesRef.get().await()
            
            var deletedCount = 0
            for (tradeSnapshot in tradesSnapshot.children) {
                try {
                    val trade = tradeSnapshot.getValue(Trade::class.java)
                    if (trade != null && trade.timestamp < oneYearAgo.toEpochMilli()) {
                        tradeSnapshot.ref.removeValue().await()
                        deletedCount++
                    }
                } catch (e: Exception) {
                    Log.e("FirebaseRepository", "Error cleaning up trade data", e)
                }
            }
            Log.d("FirebaseRepository", "Successfully cleaned up old trade data. Deleted $deletedCount old trades")
        } catch (e: Exception) {
            Log.e("FirebaseRepository", "Error during data cleanup", e)
        }
    }

    suspend fun readAllUsersDirectly(): List<User> {
        Log.d("FirebaseRepository", "Starting direct user read")
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
                        co2Saved = (data["co2Saved"] as? Number)?.toDouble() ?: 0.0
                    ))
                } catch (e: Exception) {
                    Log.e("FirebaseRepository", "Error processing user data for ID: ${userSnapshot.key}", e)
                }
            }
            
            Log.d("FirebaseRepository", "Successfully loaded ${users.size} users")
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
} 