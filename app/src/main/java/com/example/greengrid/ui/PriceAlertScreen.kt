package com.example.greengrid.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.work.*
import com.example.greengrid.data.fetchCurrentPrice
import com.example.greengrid.data.findDailyPriceMinimum
import com.example.greengrid.data.PriceMinimum
import com.example.greengrid.data.PriceAlertPreferences
import com.example.greengrid.notification.PriceAlertNotificationManager
import com.example.greengrid.notification.PriceAlertWorker
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.TimeUnit

@Composable
fun PriceAlertScreen(
    onNavigateToHome: () -> Unit
) {
    val context = LocalContext.current
    val preferences = remember { PriceAlertPreferences(context) }
    
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

    // WorkManager für Hintergrund-Benachrichtigungen
    val workManager = remember { WorkManager.getInstance(context) }

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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top App Bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateToHome) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
            }
            Text(
                "Strompreis Alarm",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
            IconButton(onClick = { loadPriceData() }) {
                Icon(Icons.Default.Refresh, contentDescription = "Aktualisieren")
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Aktueller Preis und Tiefpunkt
        when {
            isLoading -> CircularProgressIndicator()
            error != null -> Text(
                error!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            currentPrice != null -> Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Aktueller Preis: ${"%.2f".format(currentPrice)} ct/kWh",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )
                priceMinimum?.let { min ->
                    Text(
                        "Tiefpunkt heute: ${"%.2f".format(min.price)} ct/kWh um ${min.hour}:00 Uhr",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                lastUpdateTime?.let { time ->
                    Text(
                        "Letzte Aktualisierung: ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(time)} Uhr",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Tiefpunkt-Alarm
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Täglich an Tiefpunkt erinnern",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Switch(
                        checked = isMinPriceAlertActive,
                        onCheckedChange = { isMinPriceAlertActive = it }
                    )
                }
                if (isMinPriceAlertActive) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Sie werden 15 Minuten vor dem Tiefpunkt benachrichtigt.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Manueller Preis-Alarm
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Manueller Preis-Alarm",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(16.dp))
                if (isAlertActive) {
                    Text(
                        "Aktiver Alarm bei ${"%.2f".format(targetPrice)} ct/kWh",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { isAlertActive = false },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(
                            Icons.Default.Notifications,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Alarm deaktivieren")
                    }
                } else {
                    OutlinedTextField(
                        value = targetPrice.toString(),
                        onValueChange = { 
                            targetPrice = it.toDoubleOrNull() ?: 0.0 
                        },
                        label = { Text("Preis in ct/kWh") },
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal
                        ),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { isAlertActive = true },
                        enabled = targetPrice > 0,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Alarm aktivieren")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Erklärung
        Text(
            "Sie werden benachrichtigt, wenn der Strompreis unter Ihren festgelegten Wert fällt oder wenn der tägliche Tiefpunkt erreicht wird. Die Preise werden alle 5 Minuten aktualisiert.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        // Debug-Button für Test-Benachrichtigungen
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "Debug-Benachrichtigungen",
                    style = MaterialTheme.typography.titleMedium
                )
                if (!hasNotificationPermission) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Benachrichtigungen sind deaktiviert",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = { sendTestNotification() }
                    ) {
                        Text("Test Preis-Alarm")
                    }
                    Button(
                        onClick = {
                            if (hasNotificationPermission) {
                                notificationManager.showMinPriceNotification(14, 12.5)
                            } else {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        }
                    ) {
                        Text("Test Tiefpunkt")
                    }
                }
            }
        }
    }
} 