package com.greengrid.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.greengrid.app.data.FirebaseRepository
import kotlinx.coroutines.launch

@Composable
fun ReportDialog(
    message: String,
    onDismiss: () -> Unit,
    onReportSubmitted: () -> Unit
) {
    var reason by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val firebaseRepository = remember { FirebaseRepository() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nachricht melden") },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text("Warum möchten Sie diese Nachricht melden?")
                OutlinedTextField(
                    value = reason,
                    onValueChange = { reason = it },
                    label = { Text("Grund") },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !isSubmitting,
                    minLines = 2,
                    maxLines = 4
                )
                if (isSubmitting) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        CircularProgressIndicator()
                    }
                }
                if (error != null) {
                    Text(
                        text = error!!,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (reason.isBlank()) {
                        error = "Bitte geben Sie einen Grund an"
                        return@Button
                    }
                    isSubmitting = true
                    scope.launch {
                        firebaseRepository.submitReport(message, reason)
                            .onSuccess {
                                onReportSubmitted()
                                onDismiss()
                            }
                            .onFailure {
                                error = it.message ?: "Ein Fehler ist aufgetreten"
                                isSubmitting = false
                            }
                    }
                },
                enabled = !isSubmitting
            ) {
                Text(if (isSubmitting) "Wird gesendet..." else "Melden")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSubmitting
            ) {
                Text("Abbrechen")
            }
        }
    )
} 