package com.example.greengrid.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.greengrid.data.PricePoint
import com.example.greengrid.data.fetchCurrentPrice
import com.example.greengrid.data.findDailyPriceMinimum
import com.example.greengrid.data.PriceMinimum
import com.example.greengrid.data.PriceAlertPreferences
import com.example.greengrid.data.PriceAlert
import com.example.greengrid.notification.PriceAlertNotificationManager
import com.example.greengrid.notification.PriceAlertWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PriceAlertScreen() {
    val context = LocalContext.current
    val preferences = remember { PriceAlertPreferences(context) }
    val workManager = remember { WorkManager.getInstance(context) }
    
    var showAddAlertDialog by remember { mutableStateOf(false) }
    var alerts by remember { mutableStateOf(preferences.getAlerts()) }

    var targetPrice by remember { mutableStateOf(preferences.targetPrice) }
    var isAlertActive by remember { mutableStateOf(preferences.isAlertActive) }
    var isMinPriceAlertActive by remember { mutableStateOf(preferences.isMinPriceAlertActive) }
    var currentPrice by remember { mutableStateOf<Double?>(null) }
    var priceMinimum by remember { mutableStateOf<PriceMinimum?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastUpdateTime by remember { mutableStateOf<Date?>(null) }
    
    val notificationManager = remember { PriceAlertNotificationManager(context) }
    val scope = rememberCoroutineScope()

    // Berechtigungsanfrage für Benachrichtigungen
    val hasNotificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
    } else {
        true
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Berechtigung wurde erteilt
            scope.launch {
                // Test-Benachrichtigung senden
                notificationManager.showPriceAlertNotification(20.0, 15.0)
            }
        }
    }

    // Funktion zum Starten des WorkManagers
    fun startPriceAlertWorker() {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val workRequest = PeriodicWorkRequestBuilder<PriceAlertWorker>(
            15, TimeUnit.MINUTES,
            5, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .build()

        workManager.enqueueUniquePeriodicWork(
            PriceAlertWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            workRequest
        )
    }

    // WorkManager beim Start initialisieren
    LaunchedEffect(Unit) {
        startPriceAlertWorker()
    }

    // WorkManager neu starten, wenn sich die Alarm-Einstellungen ändern
    LaunchedEffect(isAlertActive, isMinPriceAlertActive) {
        if (isAlertActive || isMinPriceAlertActive) {
            startPriceAlertWorker()
        }
    }

    // Funktion zum Senden einer Test-Benachrichtigung
    fun sendTestNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (hasNotificationPermission) {
                notificationManager.showPriceAlertNotification(20.0, 15.0)
            } else {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        } else {
            notificationManager.showPriceAlertNotification(20.0, 15.0)
        }
    }

    // Funktion zum Laden der Preisdaten
    fun loadPriceData() {
        scope.launch {
            isLoading = true
            error = null
            try {
                currentPrice = fetchCurrentPrice()
                priceMinimum = findDailyPriceMinimum()
                if (currentPrice == null || priceMinimum == null) {
                    error = "Fehler beim Laden der Preisdaten"
                } else {
                    lastUpdateTime = Date()
                }
            } catch (e: Exception) {
                error = e.message ?: "Unbekannter Fehler"
            } finally {
                isLoading = false
            }
        }
    }

    // Beim Start und alle 5 Minuten die Daten aktualisieren
    LaunchedEffect(Unit) {
        loadPriceData()
        while (true) {
            delay(5 * 60 * 1000L) // 5 Minuten warten
            loadPriceData()
        }
    }

    // Preis-Überwachung für manuellen Alarm
    LaunchedEffect(isAlertActive, targetPrice, currentPrice) {
        if (isAlertActive && currentPrice != null) {
            if (currentPrice!! <= targetPrice) {
                notificationManager.showPriceAlertNotification(targetPrice, currentPrice!!)
                isAlertActive = false
                preferences.isAlertActive = false
            }
        }
    }

    // Tiefpunkt-Überwachung
    LaunchedEffect(isMinPriceAlertActive, priceMinimum) {
        if (isMinPriceAlertActive && priceMinimum != null) {
            val now = Calendar.getInstance()
            val minTime = Calendar.getInstance().apply {
                timeInMillis = priceMinimum!!.timestamp * 1000
            }
            
            // Wenn wir uns der Tiefpunkt-Stunde nähern (15 Minuten vorher)
            if (now.get(Calendar.HOUR_OF_DAY) == minTime.get(Calendar.HOUR_OF_DAY) &&
                now.get(Calendar.MINUTE) >= 45) {
                notificationManager.showMinPriceNotification(
                    priceMinimum!!.hour,
                    priceMinimum!!.price
                )
                // Alarm für heute deaktivieren
                isMinPriceAlertActive = false
                preferences.isMinPriceAlertActive = false
            }
        }
    }

    // Speichern der Einstellungen bei Änderungen
    LaunchedEffect(isMinPriceAlertActive) {
        preferences.isMinPriceAlertActive = isMinPriceAlertActive
    }

    LaunchedEffect(isAlertActive) {
        preferences.isAlertActive = isAlertActive
    }

    LaunchedEffect(targetPrice) {
        preferences.targetPrice = targetPrice
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Preisalarm-Bereich
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Preisalarme",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Button(
                        onClick = { showAddAlertDialog = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text("+ Alarm")
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                if (alerts.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Keine Preisalarme vorhanden",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        alerts.forEach { alert ->
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            "${if (alert.isAbove) "Über" else "Unter"} ${alert.threshold} ct/kWh",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                        Text(
                                            "Benachrichtigung: ${if (alert.notify) "Aktiv" else "Inaktiv"}",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(
                                        onClick = {
                                            alerts = alerts.filter { it != alert }
                                            preferences.removeAlert(alert)
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Löschen",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Tiefpunkt-Alarm-Bereich
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Automatischer Tiefpunkt-Alarm",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "Benachrichtigung 15 Minuten vor dem Tagestiefpreis",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                    }
                    Switch(
                        checked = isMinPriceAlertActive,
                        onCheckedChange = { 
                            isMinPriceAlertActive = it
                            if (it && !hasNotificationPermission && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            checkedTrackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                            uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            uncheckedTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                        )
                    )
                }
                if (isMinPriceAlertActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Hintergrundprozess aktiv",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (priceMinimum != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Tiefpunkt heute: ${priceMinimum!!.hour}:00 Uhr (${"%.2f".format(priceMinimum!!.price)} ct/kWh)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        HorizontalDivider(
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
            thickness = 2.dp,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(modifier = Modifier.height(8.dp))

        // Settings/Erklärungen Bereich (scrollbar)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Preisalarm-Erklärung & Tipps",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "So funktionieren Preisalarme:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "• Setzen Sie einen Schwellenwert in ct/kWh\n" +
                    "• Wählen Sie, ob Sie bei Preisen über oder unter diesem Wert benachrichtigt werden möchten\n" +
                    "• Die App überwacht kontinuierlich die aktuellen Strompreise\n" +
                    "• Bei Erreichen des Schwellenwerts erhalten Sie eine Push-Benachrichtigung\n" +
                    "• Der automatische Tiefpunkt-Alarm benachrichtigt Sie 15 Minuten vor dem günstigsten Zeitpunkt des Tages",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Tipps für effektive Preisalarme:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    "• Setzen Sie mehrere Alarme für verschiedene Szenarien\n" +
                    "• Beobachten Sie die Preisverläufe, um realistische Schwellenwerte zu finden\n" +
                    "• Nutzen Sie niedrige Preise zum Kaufen und hohe Preise zum Verkaufen\n" +
                    "• Aktivieren Sie den automatischen Tiefpunkt-Alarm für tägliche Benachrichtigungen",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    // Add Alert Dialog
    if (showAddAlertDialog) {
        AddPriceAlertDialog(
            onDismiss = { showAddAlertDialog = false },
            onAddAlert = { alert ->
                alerts = alerts + alert
                preferences.addAlert(alert)
                showAddAlertDialog = false
            }
        )
    }
}

@Composable
fun AddPriceAlertDialog(
    onDismiss: () -> Unit,
    onAddAlert: (PriceAlert) -> Unit
) {
    var threshold by remember { mutableStateOf("") }
    var isAbove by remember { mutableStateOf(true) }
    var notify by remember { mutableStateOf(true) }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Neuen Preisalarm hinzufügen",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = threshold,
                    onValueChange = { threshold = it },
                    label = { Text("Schwellenwert (ct/kWh)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.secondary
                    )
                )

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { isAbove = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isAbove) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (isAbove) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f).padding(end = 4.dp)
                    ) {
                        Text("Über")
                    }
                    Button(
                        onClick = { isAbove = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (!isAbove) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                            contentColor = if (!isAbove) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                        ),
                        modifier = Modifier.weight(1f).padding(start = 4.dp)
                    ) {
                        Text("Unter")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Benachrichtigung senden",
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = notify,
                        onCheckedChange = { notify = it }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = {
                            val thresholdValue = threshold.toDoubleOrNull()
                            if (thresholdValue != null && thresholdValue >= -20.0) {
                                onAddAlert(PriceAlert(
                                    threshold = thresholdValue,
                                    isAbove = isAbove,
                                    notify = notify
                                ))
                            }
                        },
                        enabled = threshold.toDoubleOrNull() != null && threshold.toDoubleOrNull()!! >= -20.0
                    ) {
                        Text("Hinzufügen")
                    }
                }
            }
        }
    }
} 