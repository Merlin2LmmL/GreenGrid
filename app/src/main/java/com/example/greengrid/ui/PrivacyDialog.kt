package com.example.greengrid.ui

import android.content.Intent
import android.content.Context
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@Composable
fun PrivacyDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Card(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.4f),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("Datenschutzerklärung & AGB", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Die vollständige Datenschutzerklärung und AGB können Sie hier als PDF öffnen:", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = {
                    openPdfFromRaw(context)
                }) {
                    Text("Datenschutzerklärung öffnen")
                }
                Spacer(modifier = Modifier.height(24.dp))
                Button(onClick = onDismiss) { Text("Schließen") }
            }
        }
    }
}

fun openPdfFromRaw(context: Context) {
    try {
        val fileName = "GreenGrid_Datenschutzerklaerung.pdf"
        val inputStream: InputStream = context.resources.openRawResource(
            context.resources.getIdentifier("greengrid_datenschutzerklaerung", "raw", context.packageName)
        )
        val outFile = File(context.cacheDir, fileName)
        FileOutputStream(outFile).use { output ->
            inputStream.copyTo(output)
        }
        val uri: Uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".provider",
            outFile
        )
        val intent = Intent(Intent.ACTION_VIEW)
        intent.setDataAndType(uri, "application/pdf")
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        context.startActivity(intent)
    } catch (e: Exception) {
        e.printStackTrace()
        // Optional: Zeige einen Toast oder Dialog, dass kein PDF-Viewer installiert ist
    }
} 