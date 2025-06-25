package com.greengrid.app.ui

import android.content.Context
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.greengrid.app.R

@Composable
fun PrivacyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val datenschutzText = remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        datenschutzText.value = loadPrivacyText(context)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Datenschutzerklärung & Nutzungsbedingungen") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.7f)
                    .verticalScroll(rememberScrollState())
            ) {
                Text(datenschutzText.value)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Schließen")
            }
        }
    )
}

private fun loadPrivacyText(context: Context): String {
    return try {
        context.resources.openRawResource(R.raw.greengrid_datenschutzerklaerung)
            .bufferedReader().use { it.readText() }
    } catch (e: Exception) {
        "Datenschutzerklärung konnte nicht geladen werden."
    }
} 