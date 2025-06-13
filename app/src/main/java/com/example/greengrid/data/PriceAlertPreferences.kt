package com.example.greengrid.data

import android.content.Context
import android.content.SharedPreferences

class PriceAlertPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    var isMinPriceAlertActive: Boolean
        get() = prefs.getBoolean(KEY_MIN_PRICE_ALERT, false)
        set(value) = prefs.edit().putBoolean(KEY_MIN_PRICE_ALERT, value).apply()

    var targetPrice: Double
        get() = prefs.getFloat(KEY_TARGET_PRICE, 0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_TARGET_PRICE, value.toFloat()).apply()

    var isAlertActive: Boolean
        get() = prefs.getBoolean(KEY_ALERT_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_ALERT_ACTIVE, value).apply()

    companion object {
        private const val PREFS_NAME = "price_alert_preferences"
        private const val KEY_MIN_PRICE_ALERT = "min_price_alert_active"
        private const val KEY_TARGET_PRICE = "target_price"
        private const val KEY_ALERT_ACTIVE = "alert_active"
    }
} 