package com.greengrid.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.greengrid.app.ui.theme.GreenGridTheme
import com.greengrid.app.data.FirebaseRepository
import com.greengrid.app.data.User
import com.greengrid.app.ui.AuthScreen
import com.greengrid.app.ui.MainNavigation
import com.greengrid.app.data.LoginPreferences
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
                                    currentScreen = "main"
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
                            currentScreen = "main"
                        },
                        repository = repository
                    )
                    "main" -> {
                        currentUser?.let { user ->
                            MainNavigation(
                                user = user,
                                onLogout = {
                                    loginPreferences.clearLoginData()
                                    currentUser = null
                                    currentScreen = "login"
                                },
                                onNameChange = { newName ->
                                    currentName = newName
                                    currentUser = currentUser?.copy(username = newName)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}