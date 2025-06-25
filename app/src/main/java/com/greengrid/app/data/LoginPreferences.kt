package com.greengrid.app.data

import android.content.Context
import android.content.SharedPreferences

class LoginPreferences(context: Context) {
    private val prefs: SharedPreferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    
    // Cache for frequently accessed values
    private var cachedEmail: String? = null
    private var cachedPassword: String? = null
    private var cachedIsLoggedIn: Boolean? = null

    var email: String
        get() {
            if (cachedEmail == null) {
                cachedEmail = prefs.getString(KEY_EMAIL, "") ?: ""
            }
            return cachedEmail!!
        }
        set(value) {
            cachedEmail = value
            prefs.edit().putString(KEY_EMAIL, value).commit()
        }

    var password: String
        get() {
            if (cachedPassword == null) {
                cachedPassword = prefs.getString(KEY_PASSWORD, "") ?: ""
            }
            return cachedPassword!!
        }
        set(value) {
            cachedPassword = value
            prefs.edit().putString(KEY_PASSWORD, value).commit()
        }

    var isLoggedIn: Boolean
        get() {
            if (cachedIsLoggedIn == null) {
                cachedIsLoggedIn = prefs.getBoolean(KEY_LOGGED_IN, false)
            }
            return cachedIsLoggedIn!!
        }
        set(value) {
            cachedIsLoggedIn = value
            prefs.edit().putBoolean(KEY_LOGGED_IN, value).commit()
        }

    fun clearLoginData() {
        // Clear cache
        cachedEmail = null
        cachedPassword = null
        cachedIsLoggedIn = null
        // Clear preferences
        prefs.edit().clear().commit()
    }

    fun saveLoginData(email: String, password: String, isLoggedIn: Boolean) {
        // Update cache
        cachedEmail = email
        cachedPassword = password
        cachedIsLoggedIn = isLoggedIn
        // Batch save to preferences
        prefs.edit()
            .putString(KEY_EMAIL, email)
            .putString(KEY_PASSWORD, password)
            .putBoolean(KEY_LOGGED_IN, isLoggedIn)
            .commit()
    }

    companion object {
        private const val PREFS_NAME = "login_preferences"
        private const val KEY_EMAIL = "email"
        private const val KEY_PASSWORD = "password"
        private const val KEY_LOGGED_IN = "logged_in"
    }
} 