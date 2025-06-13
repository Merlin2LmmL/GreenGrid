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
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.DocumentSnapshot

class FirebaseRepository {
    private val auth = Firebase.auth
    private val database = Firebase.database
    private val firestore = FirebaseFirestore.getInstance()
    private val usersCollection = firestore.collection("users")
    private val tradesCollection = firestore.collection("trades")
    private val marketStateCollection = firestore.collection("marketState")

    // Authentifizierung
    suspend fun signUp(email: String, password: String, username: String): Result<User> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, password).await()
            result.user?.let { firebaseUser ->
                val user = User(
                    id = firebaseUser.uid,
                    email = email,
                    username = username,
                    balance = 1000.0,
                    capacity = 0.0,
                    maxCapacity = 100.0,
                    totalBought = 0.0,
                    totalSold = 0.0
                )
                database.getReference("users/${user.id}").setValue(user).await()
                Result.success(user)
            } ?: Result.failure(Exception("Benutzer konnte nicht erstellt werden"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

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

    fun observeMarketState(): Flow<MarketState> = callbackFlow {
        val listener = database.getReference("market/state")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val state = snapshot.getValue(MarketState::class.java) ?: MarketState()
                    trySend(state)
                }

                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            })
        awaitClose { database.getReference("market/state").removeEventListener(listener) }
    }

    fun observePriceHistory(): Flow<List<PricePoint>> = callbackFlow {
        val listener = database.getReference("market/prices")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val prices = snapshot.children.mapNotNull {
                        it.getValue(PricePoint::class.java)
                    }.sortedBy { it.timestamp }
                    trySend(prices)
                }

                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            })
        awaitClose { database.getReference("market/prices").removeEventListener(listener) }
    }

    fun observeUserData(): Flow<User> = callbackFlow {
        val userId = auth.currentUser?.uid ?: throw Exception("User not logged in")
        val listener = database.getReference("users/$userId")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val user = snapshot.getValue(User::class.java) ?: User()
                    trySend(user)
                }

                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            })
        awaitClose { database.getReference("users/$userId").removeEventListener(listener) }
    }

    suspend fun executeTrade(trade: Trade): Result<Unit> {
        return try {
            val userId = auth.currentUser?.uid ?: throw Exception("User not logged in")
            val tradeWithUser = trade.copy(userId = userId)
            val userRef = database.getReference("users/$userId")
            val userSnapshot = userRef.get().await()
            val user = userSnapshot.getValue(User::class.java) ?: throw Exception("User not found")

            Log.d("TradeDebug", "Starting trade execution:")
            Log.d("TradeDebug", "User before trade - Balance: ${user.balance}, Capacity: ${user.capacity}")
            Log.d("TradeDebug", "Trade details - Amount: ${trade.amount}, Price: ${trade.price}, IsBuy: ${trade.isBuy}")

            if (trade.isBuy && user.capacity + trade.amount > user.maxCapacity) {
                Log.d("TradeDebug", "Trade rejected: Not enough storage capacity")
                return Result.failure(Exception("Nicht genug Speicherplatz!"))
            }

            if (!trade.isBuy && user.capacity < trade.amount) {
                Log.d("TradeDebug", "Trade rejected: Not enough electricity in storage")
                return Result.failure(Exception("Nicht genug Strom im Speicher!"))
            }

            // Merke alten Stromwert
            val oldTotalValue = user.balance + user.capacity * trade.price / 100.0

            // Calculate new balance and capacity
            val tradeValue = trade.amount * trade.price / 100.0
            val newBalance = if (trade.isBuy) {
                user.balance - tradeValue  // Beim Kauf wird Geld abgezogen
            } else {
                user.balance + tradeValue  // Beim Verkauf wird Geld hinzugefügt
            }
            val newCapacity = if (trade.isBuy) {
                user.capacity + trade.amount  // Beim Kauf wird Kapazität erhöht
            } else {
                user.capacity - trade.amount  // Beim Verkauf wird Kapazität verringert
            }

            // Update user data first (vorläufig)
            var updatedUser = user.copy(
                balance = newBalance,
                capacity = newCapacity,
                totalBought = if (trade.isBuy) user.totalBought + trade.amount else user.totalBought,
                totalSold = if (!trade.isBuy) user.totalSold + trade.amount else user.totalSold
            )

            // Update user data in database
            userRef.setValue(updatedUser).await()
            database.getReference("trades").push().setValue(tradeWithUser).await()

            // Preisänderung im Markt
            try {
                val now = Instant.ofEpochMilli(System.currentTimeMillis())
                val roundedHour = now.truncatedTo(ChronoUnit.HOURS)
                val currentPriceRef = database.getReference("market/prices/price_${roundedHour.toEpochMilli()}")
                val currentPriceSnapshot = currentPriceRef.get().await()
                val currentPricePoint = currentPriceSnapshot.getValue(PricePoint::class.java)
                val simPrices = simulatePriceHistory(
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
                val basePrice = simPrices.last().price
                val currentPrice = currentPricePoint?.price ?: basePrice
                val tradeImpact = if (trade.isBuy) trade.amount * 0.005 else -trade.amount * 0.005
                val newPrice = currentPrice + tradeImpact
                val newPricePoint = PricePoint(
                    timestamp = roundedHour.toEpochMilli(),
                    price = newPrice,
                    volume = (currentPricePoint?.volume ?: 0.0) + trade.amount
                )
                currentPriceRef.setValue(newPricePoint).await()

                // Berechne neuen Gesamtwert mit neuem Preis
                val newTotalValue = newBalance + newCapacity * newPrice / 100.0
                val diff = oldTotalValue - newTotalValue
                if (diff != 0.0) {
                    updatedUser = updatedUser.copy(balance = updatedUser.balance + diff)
                    userRef.setValue(updatedUser).await()
                }

                // Aktualisiere die Preise für die nächsten Stunden
                val decayHours = 6
                val decayK = 0.7
                for (n in 1 until decayHours) {
                    val futureHour = roundedHour.plus(n.toLong(), ChronoUnit.HOURS)
                    val futurePriceRef = database.getReference("market/prices/price_${futureHour.toEpochMilli()}")
                    val futurePriceSnapshot = futurePriceRef.get().await()
                    val futurePricePoint = futurePriceSnapshot.getValue(PricePoint::class.java)
                    val futureBasePrice = simulatePriceHistory(
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
                    ).last().price
                    val currentFuturePrice = futurePricePoint?.price ?: futureBasePrice
                    val futureImpact = tradeImpact * exp(-decayK * n)
                    val newFuturePrice = currentFuturePrice + futureImpact
                    val newFuturePricePoint = PricePoint(
                        timestamp = futureHour.toEpochMilli(),
                        price = newFuturePrice,
                        volume = (futurePricePoint?.volume ?: 0.0) + trade.amount
                    )
                    futurePriceRef.setValue(newFuturePricePoint).await()
                }
            } catch (e: Exception) {
                Log.e("TradeDebug", "Error updating prices, but trade was successful", e)
            }

            Log.d("TradeDebug", "Trade execution completed successfully")
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e("TradeDebug", "Error executing trade", e)
            Result.failure(e)
        }
    }

    fun observeUserTrades(): Flow<List<Trade>> = callbackFlow {
        val listener = database.getReference("trades")
            .addValueEventListener(object : ValueEventListener {
                override fun onDataChange(snapshot: DataSnapshot) {
                    val trades = snapshot.children.mapNotNull { it.getValue(Trade::class.java) }
                        // filtere clientseitig nach aktuellem User
                        .filter { it.userId == auth.currentUser?.uid }
                        .sortedBy { it.timestamp }
                    trySend(trades)
                }
                override fun onCancelled(error: DatabaseError) {
                    close(error.toException())
                }
            })
        awaitClose { database.getReference("trades").removeEventListener(listener) }
    }

    private fun DocumentSnapshot.toUser(): User? {
        return try {
            val data = data ?: return null
            User(
                id = id,
                email = data["email"] as? String ?: "",
                username = data["username"] as? String ?: "",
                balance = (data["balance"] as? Number)?.toDouble() ?: 1000.0,
                capacity = (data["capacity"] as? Number)?.toDouble() ?: 0.0,
                maxCapacity = (data["maxCapacity"] as? Number)?.toDouble() ?: 100.0,
                totalBought = (data["totalBought"] as? Number)?.toDouble() ?: 0.0,
                totalSold = (data["totalSold"] as? Number)?.toDouble() ?: 0.0
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun DocumentSnapshot.toTrade(): Trade? {
        return try {
            val data = data ?: return null
            Trade(
                userId = data["userId"] as String,
                amount = (data["amount"] as Number).toDouble(),
                price = (data["price"] as Number).toDouble(),
                isBuy = data["isBuy"] as Boolean,
                timestamp = (data["timestamp"] as Number).toLong()
            )
        } catch (e: Exception) {
            null
        }
    }

    private fun DocumentSnapshot.toMarketState(): MarketState? {
        return try {
            val data = data ?: return null
            MarketState(
                currentPrice = (data["currentPrice"] as Number).toDouble(),
                lastUpdate = (data["lastUpdate"] as Number).toLong()
            )
        } catch (e: Exception) {
            null
        }
    }
} 