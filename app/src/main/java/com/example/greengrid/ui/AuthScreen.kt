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
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.database.ktx.database
import com.google.firebase.ktx.Firebase

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
    var showResetPasswordMessage by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val loginPreferences = remember { LoginPreferences(context) }
    val scope = rememberCoroutineScope()
    val database = Firebase.database("https://greengrid-c6bc8-default-rtdb.europe-west1.firebasedatabase.app/")

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
                "Bitte überprüfen Sie Ihre E-Mails und bestätigen Sie Ihre E-Mail-Adresse. Nach der Bestätigung können Sie sich anmelden.",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        if (showResetPasswordMessage) {
            Text(
                "Eine E-Mail zum Zurücksetzen des Passworts wurde an Ihre E-Mail-Adresse gesendet.",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        }

        Button(
            onClick = {
                isLoading = true
                error = null
                showVerificationMessage = false
                showResetPasswordMessage = false
                scope.launch {
                    try {
                        if (isLogin) {
                            val result = repository.signIn(email, password)
                            result.fold(
                                onSuccess = { user -> 
                                    // Check if email is verified
                                    val firebaseUser = FirebaseAuth.getInstance().currentUser
                                    if (firebaseUser?.isEmailVerified == true) {
                                        if (rememberMe) {
                                            loginPreferences.saveLoginData(email, password, true)
                                        }
                                        onAuthSuccess(user)
                                    } else {
                                        error = "Bitte bestätigen Sie zuerst Ihre E-Mail-Adresse"
                                        // Sign out the user since email is not verified
                                        FirebaseAuth.getInstance().signOut()
                                        isLoading = false
                                    }
                                },
                                onFailure = { e -> 
                                    error = e.message
                                    isLoading = false
                                }
                            )
                        } else {
                            // Registrierung
                            val auth = FirebaseAuth.getInstance()
                            auth.createUserWithEmailAndPassword(email, password)
                                .addOnCompleteListener { task ->
                                    if (task.isSuccessful) {
                                        val user = task.result.user
                                        // E-Mail-Verifizierung senden
                                        user?.sendEmailVerification()
                                            ?.addOnCompleteListener { verificationTask ->
                                                if (verificationTask.isSuccessful) {
                                                    // Temporäre Benutzerdaten speichern
                                                    val tempUserData = hashMapOf(
                                                        "email" to email,
                                                        "username" to username,
                                                        "balance" to 500,
                                                        "capacity" to 0,
                                                        "maxCapacity" to 100,
                                                        "co2Saved" to 0,
                                                        "lastLogin" to System.currentTimeMillis()
                                                    )
                                                    database.reference.child("users").child(user!!.uid)
                                                        .setValue(tempUserData)
                                                        .addOnCompleteListener { dbTask ->
                                                            if (dbTask.isSuccessful) {
                                                                showVerificationMessage = true
                                                                // Zurück zum Login wechseln
                                                                isLogin = true
                                                                // Abmelden, da der Account noch nicht verifiziert ist
                                                                auth.signOut()
                                                            } else {
                                                                error = "Fehler beim Speichern der Benutzerdaten"
                                                            }
                                                            isLoading = false
                                                        }
                                                } else {
                                                    error = when (verificationTask.exception) {
                                                        is FirebaseAuthException -> {
                                                            when ((verificationTask.exception as FirebaseAuthException).errorCode) {
                                                                "invalid-email" -> "Ungültige E-Mail-Adresse"
                                                                "user-disabled" -> "Benutzer ist deaktiviert"
                                                                "user-not-found" -> "Benutzer nicht gefunden"
                                                                else -> "Fehler beim Senden der Verifizierungs-E-Mail: ${verificationTask.exception?.message}"
                                                            }
                                                        }
                                                        else -> "Fehler beim Senden der Verifizierungs-E-Mail: ${verificationTask.exception?.message}"
                                                    }
                                                    // Bei Fehler den Benutzer löschen
                                                    user?.delete()
                                                    isLoading = false
                                                }
                                            }
                                    } else {
                                        error = when (task.exception) {
                                            is FirebaseAuthException -> {
                                                when ((task.exception as FirebaseAuthException).errorCode) {
                                                    "email-already-in-use" -> "Diese E-Mail-Adresse wird bereits verwendet"
                                                    "invalid-email" -> "Ungültige E-Mail-Adresse"
                                                    "operation-not-allowed" -> "Registrierung ist derzeit nicht möglich"
                                                    "weak-password" -> "Das Passwort ist zu schwach"
                                                    else -> "Registrierung fehlgeschlagen: ${task.exception?.message}"
                                                }
                                            }
                                            else -> "Registrierung fehlgeschlagen: ${task.exception?.message}"
                                        }
                                        isLoading = false
                                    }
                                }
                        }
                    } catch (e: Exception) {
                        error = e.message
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

        if (isLogin) {
            TextButton(
                onClick = {
                    if (email.isNotEmpty()) {
                        scope.launch {
                            isLoading = true
                            error = null
                            showResetPasswordMessage = false
                            try {
                                // Prüfe zuerst, ob die E-Mail existiert und verifiziert ist
                                FirebaseAuth.getInstance().fetchSignInMethodsForEmail(email)
                                    .addOnCompleteListener { task ->
                                        if (task.isSuccessful) {
                                            val methods = task.result?.signInMethods
                                            if (methods.isNullOrEmpty()) {
                                                error = "Diese E-Mail-Adresse ist nicht registriert"
                                                isLoading = false
                                            } else {
                                                // E-Mail existiert, versuche Passwort zurückzusetzen
                                                scope.launch {
                                                    try {
                                                        repository.resetPassword(email).fold(
                                                            onSuccess = { showResetPasswordMessage = true },
                                                            onFailure = { e -> error = e.message }
                                                        )
                                                    } finally {
                                                        isLoading = false
                                                    }
                                                }
                                            }
                                        } else {
                                            error = "Fehler bei der Überprüfung der E-Mail-Adresse"
                                            isLoading = false
                                        }
                                    }
                            } catch (e: Exception) {
                                error = e.message
                                isLoading = false
                            }
                        }
                    } else {
                        error = "Bitte geben Sie Ihre E-Mail-Adresse ein"
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Passwort vergessen?", color = MaterialTheme.colorScheme.primary)
            }
        }

        TextButton(
            onClick = { 
                isLogin = !isLogin
                error = null
                showVerificationMessage = false
                showResetPasswordMessage = false
            }
        ) {
            Text(
                if (isLogin) "Noch kein Konto? Registrieren" else "Bereits registriert? Anmelden",
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}