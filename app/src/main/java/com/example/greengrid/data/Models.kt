package com.example.greengrid.data

data class User(
    val id: String = "",
    val email: String = "",
    val username: String = "",
    val balance: Double = 0.0,
    val capacity: Double = 0.0,
    val maxCapacity: Double = 100.0,
    val co2Saved: Double = 0.0,
    val totalBought: Double = 0.0,
    val totalSold: Double = 0.0,
    val lastStorageUpdate: Long = System.currentTimeMillis()
)

data class Trade(
    val userId: String = "",
    val amount: Double = 0.0,
    val price: Double = 0.0,
    val isBuy: Boolean = true,
    val timestamp: Long = 0L
)

data class PricePoint(
    val timestamp: Long = 0L,
    val price: Double = 0.0,
    val volume: Double = 0.0
)