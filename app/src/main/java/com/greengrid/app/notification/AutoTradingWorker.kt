package com.greengrid.app.notification

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.greengrid.app.data.PriceAlertPreferences
import com.greengrid.app.data.FirebaseRepository
import com.greengrid.app.data.Trade
import com.greengrid.app.data.fetchCurrentPrice

class AutoTradingWorker(
    private val context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val preferences = PriceAlertPreferences(context)
        val repository = FirebaseRepository()

        // Check if auto trading is enabled
        if (!preferences.isAutoTradingEnabled) {
            Log.d("AutoTradingWorker", "Auto trading is disabled")
            return Result.success()
        }

        try {
            // Get current user
            val currentUser = repository.getCurrentUser()
            if (currentUser == null || currentUser.id.isEmpty()) {
                Log.e("AutoTradingWorker", "No valid user found")
                return Result.retry()
            }

            // Get current price from simulation
            val currentPrice = getCurrentPrice()
            
            // Get auto trading parameters
            val buyThreshold = preferences.buyThreshold
            val sellThreshold = preferences.sellThreshold
            val tradeAmount = preferences.tradeAmount

            Log.d("AutoTradingWorker", "Checking auto trading - Price: $currentPrice, Buy: $buyThreshold, Sell: $sellThreshold")

            // Check if we should buy
            if (currentPrice <= buyThreshold && 
                currentUser.balance >= tradeAmount * currentPrice / 100.0 &&
                currentUser.capacity + tradeAmount <= currentUser.maxCapacity) {
                
                val trade = Trade(
                    userId = currentUser.id,
                    amount = tradeAmount,
                    price = currentPrice,
                    isBuy = true,
                    timestamp = System.currentTimeMillis()
                )
                val result = repository.executeTrade(trade)
                if (result.isSuccess) {
                    Log.d("AutoTradingWorker", "Auto buy executed at price: $currentPrice")
                    showNotification("Auto Trading", "Kauf ausgeführt bei ${"%.2f".format(currentPrice)} ct/kWh")
                } else {
                    Log.e("AutoTradingWorker", "Auto buy failed: ${result.exceptionOrNull()}")
                }
            }
            
            // Check if we should sell
            if (currentPrice >= sellThreshold && 
                currentUser.capacity >= tradeAmount) {
                
                val trade = Trade(
                    userId = currentUser.id,
                    amount = tradeAmount,
                    price = currentPrice,
                    isBuy = false,
                    timestamp = System.currentTimeMillis()
                )
                val result = repository.executeTrade(trade)
                if (result.isSuccess) {
                    Log.d("AutoTradingWorker", "Auto sell executed at price: $currentPrice")
                    showNotification("Auto Trading", "Verkauf ausgeführt bei ${"%.2f".format(currentPrice)} ct/kWh")
                } else {
                    Log.e("AutoTradingWorker", "Auto sell failed: ${result.exceptionOrNull()}")
                }
            }

            return Result.success()
        } catch (e: Exception) {
            Log.e("AutoTradingWorker", "Error in auto trading", e)
            return Result.retry()
        }
    }

    private suspend fun getCurrentPrice(): Double {
        // Use real API data from aWATTar instead of simulation
        return fetchCurrentPrice() ?: 25.0 // Fallback to base price if API fails
    }

    private fun showNotification(title: String, message: String) {
        val notificationManager = PriceAlertNotificationManager(context)
        notificationManager.showAutoTradingNotification(title, message)
    }

    companion object {
        const val WORK_NAME = "auto_trading_worker"
    }
} 