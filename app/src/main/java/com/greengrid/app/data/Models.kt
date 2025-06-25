package com.greengrid.app.data

data class User(
    val id: String = "",
    val email: String = "",
    val username: String = "",
    var balance: Double = 0.0,
    val capacity: Double = 0.0,
    var maxCapacity: Double = 100.0,
    val co2Saved: Double = 0.0,
    val totalBought: Double = 0.0,
    val totalSold: Double = 0.0,
    val averagePurchasePrice: Double = 0.0,
    val lastStorageUpdate: Long = System.currentTimeMillis(),
    val totalStorageHours: Double = 0.0
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

data class Report(
    val id: String = "",
    val userId: String = "",
    val reportedMessage: String = "",
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis()
)