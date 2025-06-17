package com.example.greengrid.data

import android.content.Context
import android.content.SharedPreferences

class AppPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences("app_preferences", Context.MODE_PRIVATE)
    
    // Benachrichtigungen
    var notificationSound: Boolean
        get() = prefs.getBoolean("notification_sound", true)
        set(value) = prefs.edit().putBoolean("notification_sound", value).apply()
    
    var notificationVibration: Boolean
        get() = prefs.getBoolean("notification_vibration", true)
        set(value) = prefs.edit().putBoolean("notification_vibration", value).apply()
    
    // App Einstellungen
    var darkTheme: Boolean
        get() = prefs.getBoolean("dark_theme", false)
        set(value) = prefs.edit().putBoolean("dark_theme", value).apply()
    
    var overrideSystemTheme: Boolean
        get() = prefs.getBoolean("override_system_theme", false)
        set(value) = prefs.edit().putBoolean("override_system_theme", value).apply()
    
    // App Version (wird aus BuildConfig gelesen)
    val appVersion: String
        get() = "1.0.1"
    
    // Links
    val homepageUrl = "https://greengrid.onepage.me"
    val privacyUrl = "https://onecdn.io/media/nutzungsbedingungen-ee67fc93-b992-4ad7-9d4a-0b8a7c8bc905.pdf"
    val developerUrl = "https://github.com/Merlin2LmmL"
} 