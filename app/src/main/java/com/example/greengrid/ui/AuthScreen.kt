package com.example.greengrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.greengrid.data.User
import com.example.greengrid.data.LoginPreferences
import com.example.greengrid.data.FirebaseRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.MainScope

@Composable
fun AuthScreen(
    onAuthSuccess: (User) -> Unit,
    repository: FirebaseRepository
) {
    var isLogin by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var rememberMe by remember { mutableStateOf(true) }
    var showVerificationMessage by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val loginPreferences = remember { LoginPreferences(context) }

    // Beim Start prüfen, ob gespeicherte Login-Daten vorhanden sind
    LaunchedEffect(Unit) {
        if (loginPreferences.isLoggedIn) {
            isLoading = true
            try {
                val result = repository.signIn(loginPreferences.email, loginPreferences.password)
                result.fold(
                    onSuccess = { user -> onAuthSuccess(user) },
                    onFailure = { e -> 
                        error = e.message
                        loginPreferences.clearLoginData()
                    }
                )
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "GreenGrid",
            style = MaterialTheme.typography.headlineLarge,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(32.dp))

        if (!isLogin) {
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Nutzername") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(16.dp))
        }

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("E-Mail") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Passwort") },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth()
        )

        if (isLogin) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = rememberMe,
                    onCheckedChange = { rememberMe = it }
                )
                Text("Angemeldet bleiben")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (error != null) {
            Text(
                error!!,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (showVerificationMessage) {
            Text(
                "Bitte überprüfen Sie Ihre E-Mails und bestätigen Sie Ihre E-Mail-Adresse.",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Button(
            onClick = {
                isLoading = true
                error = null
                showVerificationMessage = false
                kotlinx.coroutines.MainScope().launch {
                    try {
                        val result = if (isLogin) {
                            repository.signIn(email, password)
                        } else {
                            repository.signUp(email, password, username)
                        }
                        result.fold(
                            onSuccess = { user -> 
                                if (!isLogin) {
                                    showVerificationMessage = true
                                } else {
                                    if (rememberMe) {
                                        loginPreferences.email = email
                                        loginPreferences.password = password
                                        loginPreferences.isLoggedIn = true
                                    }
                                    onAuthSuccess(user)
                                }
                            },
                            onFailure = { e -> error = e.message }
                        )
                    } finally {
                        isLoading = false
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading && email.isNotEmpty() && password.isNotEmpty() && (isLogin || username.isNotEmpty())
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(24.dp),
                    color = MaterialTheme.colorScheme.onPrimary
                )
            } else {
                Text(if (isLogin) "Anmelden" else "Registrieren")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        TextButton(
            onClick = { 
                isLogin = !isLogin
                error = null
                showVerificationMessage = false
            }
        ) {
            Text(
                if (isLogin) "Noch kein Konto? Registrieren" else "Bereits registriert? Anmelden",
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}