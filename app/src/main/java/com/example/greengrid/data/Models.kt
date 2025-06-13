package com.example.greengrid.data

data class User(
    val id: String = "",
    val email: String = "",
    val username: String = "",
    val balance: Double = 1000.0,
    val capacity: Double = 0.0,
    val maxCapacity: Double = 100.0,
    val totalBought: Double = 0.0,
    val totalSold: Double = 0.0
)

data class Trade(
    val userId: String = "",
    val amount: Double = 0.0,
    val price: Double = 0.0,
    val isBuy: Boolean = true,
    val timestamp: Long = 0L
)

data class MarketState(
    val currentPrice: Double = 25.0,
    val totalVolume: Double = 0.0,
    val buyVolume: Double = 0.0,
    val sellVolume: Double = 0.0,
    val lastUpdate: Long = 0L
)

data class PricePoint(
    val timestamp: Long = 0L,
    val price: Double = 0.0,
    val volume: Double = 0.0
)