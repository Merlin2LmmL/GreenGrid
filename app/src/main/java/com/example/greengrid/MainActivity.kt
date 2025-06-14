package com.example.greengrid

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.example.greengrid.ui.theme.GreenGridTheme
import com.example.greengrid.data.FirebaseRepository
import com.example.greengrid.data.User
import com.example.greengrid.data.PricePoint
import com.example.greengrid.ui.AuthScreen
import com.example.greengrid.ui.HomeScreen
import com.example.greengrid.ui.StromboerseScreen
import com.example.greengrid.ui.ForecastScreen
import com.example.greengrid.ui.SettingsScreen
import com.example.greengrid.ui.PriceAlertScreen
import com.example.greengrid.ui.AchievementsScreen
import com.example.greengrid.data.LoginPreferences
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    private val repository = FirebaseRepository()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GreenGridTheme {
                var currentScreen by remember { mutableStateOf("loading") }
                var currentUser by remember { mutableStateOf<User?>(null) }
                var currentName by remember { mutableStateOf("") }
                var priceHistory by remember { mutableStateOf<List<PricePoint>>(emptyList()) }

                // Login-Präferenzen prüfen
                val loginPreferences = remember { LoginPreferences(this) }
                LaunchedEffect(Unit) {
                    if (loginPreferences.isLoggedIn) {
                        try {
                            val result = repository.signIn(loginPreferences.email, loginPreferences.password)
                            result.fold(
                                onSuccess = { user ->
                                    currentUser = user
                                    currentName = user.username
                                    currentScreen = "home"
                                },
                                onFailure = { e ->
                                    loginPreferences.clearLoginData()
                                    currentScreen = "login"
                                }
                            )
                        } catch (e: Exception) {
                            loginPreferences.clearLoginData()
                            currentScreen = "login"
                        }
                    } else {
                        currentScreen = "login"
                    }
                }

                when (currentScreen) {
                    "loading" -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    "login" -> AuthScreen(
                        onAuthSuccess = { user ->
                            currentUser = user
                            currentName = user.username
                            currentScreen = "home"
                        },
                        repository = repository
                    )
                    "home" -> HomeScreen(
                        onNavigateToForecast = { currentScreen = "forecast" },
                        onNavigateToSettings = { currentScreen = "settings" },
                        onNavigateToStromboerse = { currentScreen = "stromboerse" },
                        onNavigateToPriceAlert = { currentScreen = "pricealert" },
                        onNavigateToAchievements = { currentScreen = "achievements" },
                        currentName = currentName,
                        user = currentUser!!
                    )
                    "forecast" -> ForecastScreen(
                        onNavigateToHome = { currentScreen = "home" },
                        onNavigateToPriceAlert = { currentScreen = "pricealert" }
                    )
                    "settings" -> SettingsScreen(
                        onNavigateToHome = { currentScreen = "home" },
                        onLogout = {
                            loginPreferences.clearLoginData()
                            currentUser = null
                            currentScreen = "login"
                        },
                        currentName = currentName,
                        onNameChange = { newName ->
                            currentName = newName
                            currentUser = currentUser?.copy(username = newName)
                        }
                    )
                    "stromboerse" -> StromboerseScreen(
                        onNavigateToHome = { currentScreen = "home" },
                        user = currentUser!!
                    )
                    "pricealert" -> PriceAlertScreen(
                        onNavigateToHome = { currentScreen = "home" }
                    )
                    "achievements" -> AchievementsScreen(
                        onNavigateBack = { currentScreen = "home" }
                    )
                }
            }
        }
    }
}