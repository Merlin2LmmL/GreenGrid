package com.greengrid.app.data

import android.content.Context
import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

data class PriceAlert(
    val threshold: Double,
    val isAbove: Boolean,
    val notify: Boolean,
    val id: String = System.currentTimeMillis().toString()
)

class PriceAlertPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val gson = Gson()

    var isMinPriceAlertActive: Boolean
        get() = prefs.getBoolean(KEY_MIN_PRICE_ALERT, false)
        set(value) = prefs.edit().putBoolean(KEY_MIN_PRICE_ALERT, value).apply()

    var targetPrice: Double
        get() = prefs.getFloat(KEY_TARGET_PRICE, 0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_TARGET_PRICE, value.toFloat()).apply()

    var isAlertActive: Boolean
        get() = prefs.getBoolean(KEY_ALERT_ACTIVE, false)
        set(value) = prefs.edit().putBoolean(KEY_ALERT_ACTIVE, value).apply()

    // Auto Trading Preferences
    var isAutoTradingEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_TRADING_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_AUTO_TRADING_ENABLED, value).apply()

    var buyThreshold: Double
        get() = prefs.getFloat(KEY_BUY_THRESHOLD, 8.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_BUY_THRESHOLD, value.toFloat()).apply()

    var sellThreshold: Double
        get() = prefs.getFloat(KEY_SELL_THRESHOLD, 12.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_SELL_THRESHOLD, value.toFloat()).apply()

    var tradeAmount: Double
        get() = prefs.getFloat(KEY_TRADE_AMOUNT, 5.0f).toDouble()
        set(value) = prefs.edit().putFloat(KEY_TRADE_AMOUNT, value.toFloat()).apply()

    var checkInterval: Int
        get() = prefs.getInt(KEY_CHECK_INTERVAL, 30)
        set(value) = prefs.edit().putInt(KEY_CHECK_INTERVAL, value).apply()

    // Multiple Price Alerts
    fun getAlerts(): List<PriceAlert> {
        val alertsJson = prefs.getString(KEY_ALERTS, "[]")
        val type = object : TypeToken<List<PriceAlert>>() {}.type
        return try {
            gson.fromJson(alertsJson, type) ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun addAlert(alert: PriceAlert) {
        val alerts = getAlerts().toMutableList()
        alerts.add(alert)
        saveAlerts(alerts)
    }

    fun removeAlert(alert: PriceAlert) {
        val alerts = getAlerts().toMutableList()
        alerts.removeAll { it.id == alert.id }
        saveAlerts(alerts)
    }

    private fun saveAlerts(alerts: List<PriceAlert>) {
        val alertsJson = gson.toJson(alerts)
        prefs.edit().putString(KEY_ALERTS, alertsJson).apply()
    }

    companion object {
        private const val PREFS_NAME = "price_alert_preferences"
        private const val KEY_MIN_PRICE_ALERT = "min_price_alert_active"
        private const val KEY_TARGET_PRICE = "target_price"
        private const val KEY_ALERT_ACTIVE = "alert_active"
        private const val KEY_AUTO_TRADING_ENABLED = "auto_trading_enabled"
        private const val KEY_BUY_THRESHOLD = "buy_threshold"
        private const val KEY_SELL_THRESHOLD = "sell_threshold"
        private const val KEY_TRADE_AMOUNT = "trade_amount"
        private const val KEY_CHECK_INTERVAL = "check_interval"
        private const val KEY_ALERTS = "alerts"
    }
} 