package com.example.greengrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.greengrid.data.PricePoint
import com.example.greengrid.ui.components.PriceGraph
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*
import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.text.style.TextAlign
import androidx.work.*
import com.example.greengrid.data.PriceAlertPreferences
import com.example.greengrid.data.fetchCurrentPrice
import com.example.greengrid.data.findDailyPriceMinimum
import com.example.greengrid.notification.PriceAlertNotificationManager
import com.example.greengrid.notification.PriceAlertWorker
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Vereinter Screen für Prognosen und Alarme.
 * Zeigt Chart für die nächsten 24h (aWATTar) und Preisalarme in einem Tab-System.
 */
@Composable
fun ForecastScreen() {
    val tabs = listOf("Prognose", "Alarme")
    var selectedTab by remember { mutableStateOf(0) }
    
    // Tab Selection
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEachIndexed { idx, tab ->
                Button(
                    onClick = { selectedTab = idx },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedTab == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (selectedTab == idx) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .height(38.dp)
                        .padding(horizontal = 2.dp)
                ) {
                    Text(tab, fontSize = 14.sp, maxLines = 1)
                }
            }
        }

        when (selectedTab) {
            0 -> PrognoseTab()
            1 -> AlarmeTab()
        }
    }
}

@Composable
fun PrognoseTab() {
    var priceHistory by remember { mutableStateOf<List<PricePoint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Beim Start laden
    LaunchedEffect(Unit) {
        isLoading = true
        error = null
        try {
            val fetched = fetchNext24hPrices()
            println("Debug: Fetched ${fetched.size} price points")
            if (fetched.isNotEmpty()) {
                println("Debug: First timestamp: ${Date(fetched.first().timestamp)}")
                println("Debug: Last timestamp: ${Date(fetched.last().timestamp)}")
            }
            
            // Sicherstellen, dass wir Daten haben
            if (fetched.isEmpty()) {
                error = "Keine Daten von der API erhalten"
                println("Debug: No data received from API")
            } else {
                // Die ersten 24 Einträge nehmen (aWATTar liefert stündliche Daten)
                priceHistory = fetched.take(24)
                println("Debug: Filtered to ${priceHistory.size} price points")
                
                if (priceHistory.isNotEmpty()) {
                    println("Debug: Filtered first timestamp: ${Date(priceHistory.first().timestamp)}")
                    println("Debug: Filtered last timestamp: ${Date(priceHistory.last().timestamp)}")
                }
            }
        } catch (e: Exception) {
            error = e.message ?: "Fehler beim Laden"
            println("Debug: Error loading data: $error")
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(12.dp))

        // Aktuelle Preisdaten Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "24h Strompreis-Prognose",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // Graph-Bereich
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(20.dp))
                        .padding(0.dp),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        isLoading -> CircularProgressIndicator()
                        error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                        priceHistory.size >= 2 -> {
                            PriceGraph(
                                priceHistory = priceHistory,
                                colorScheme = MaterialTheme.colorScheme,
                                mode = 0,
                                height = 200,
                                showHover = true,
                                showZeroLine = true
                            )
                        }
                        else -> Text("Keine Daten verfügbar")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Aktueller Preis
                val currentPrice = priceHistory.firstOrNull()?.price
                if (currentPrice != null) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Aktueller Preis:",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                " ${"%.2f".format(currentPrice)} ct/kWh",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                            )
                        }
                    }
                }

                // Erklärung für negative Preise
                if (priceHistory.any { it.price < 0.0 }) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⚠️ Negative Preise: Anbieter zahlen für Stromabnahme",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
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

        // Erklärung Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(1.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    "Wie funktionieren Strompreis-Prognosen?",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // API Information
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "📡 Echte Börsenpreise von aWATTar",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Diese Prognosen basieren auf echten EPEX Spot Börsenpreisen für Österreich. " +
                            "aWATTar stellt stündliche Marktpreise für die nächsten 24 Stunden bereit. " +
                            "Die Daten werden kontinuierlich aktualisiert und sind jederzeit verfügbar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Preisbestimmende Faktoren:",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    "• Erneuerbare Energien: Wind- und Solarstrom senken die Preise\n" +
                    "• Nachfrage: Höherer Verbrauch treibt Preise nach oben\n" +
                    "• Wetter: Sonnige/windige Tage = niedrigere Preise\n" +
                    "• Tageszeit: Nachts sind Preise oft günstiger\n" +
                    "• Wochenende: Geringere Nachfrage = niedrigere Preise",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    "Negative Preise:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                
                Text(
                    "Bei sehr hoher Produktion erneuerbarer Energien können die Preise negativ werden. " +
                    "Stromanbieter zahlen dann dafür, dass ihr Strom abgenommen wird.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
fun AlarmeTab() {
    // Importiere alle notwendigen Funktionen aus PriceAlertScreen
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = rememberCoroutineScope()
    val workManager = WorkManager.getInstance(context)
    val notificationManager = remember { PriceAlertNotificationManager(context) }
    val preferences = remember { PriceAlertPreferences(context) }

    // State für UI
    var isAlertActive by remember { mutableStateOf(preferences.isAlertActive) }
    var isMinPriceAlertActive by remember { mutableStateOf(preferences.isMinPriceAlertActive) }
    var targetPrice by remember { mutableStateOf(preferences.targetPrice.toString()) }
    var currentPrice by remember { mutableStateOf<Double?>(null) }
    var priceMinimum by remember { mutableStateOf<com.example.greengrid.data.PriceMinimum?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var lastUpdateTime by remember { mutableStateOf<Date?>(null) }
    var showAddAlertDialog by remember { mutableStateOf(false) }
    var alerts by remember { mutableStateOf(preferences.getAlerts()) }

    // Notification Permission
    var hasNotificationPermission by remember { mutableStateOf(true) }
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        hasNotificationPermission = isGranted
    }

    // WorkManager Funktionen
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
            if (currentPrice!! <= targetPrice.toDoubleOrNull() ?: 0.0) {
                notificationManager.showPriceAlertNotification(targetPrice.toDoubleOrNull() ?: 0.0, currentPrice!!)
                isAlertActive = false
                preferences.isAlertActive = false
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
        preferences.targetPrice = targetPrice.toDoubleOrNull() ?: 0.0
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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
                    
                    // Bestimme, ob es heute oder morgen ist
                    val calendar = Calendar.getInstance()
                    calendar.timeInMillis = priceMinimum!!.timestamp
                    
                    val today = Calendar.getInstance()
                    val tomorrow = Calendar.getInstance()
                    tomorrow.add(Calendar.DAY_OF_YEAR, 1)
                    
                    val dateText = when {
                        calendar.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR) -> "heute"
                        calendar.get(Calendar.DAY_OF_YEAR) == tomorrow.get(Calendar.DAY_OF_YEAR) -> "morgen"
                        else -> "am ${java.text.SimpleDateFormat("dd.MM.", java.util.Locale.GERMAN).format(Date(priceMinimum!!.timestamp))}"
                    }
                    
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.DateRange,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "Tiefpunkt $dateText: ${priceMinimum!!.hour}:00 Uhr (${"%.2f".format(priceMinimum!!.price)} ct/kWh)",
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
                Text(
                    "Preisalarme & Benachrichtigungen",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )
                
                // API Information
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    ),
                    elevation = CardDefaults.cardElevation(1.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            "📡 Echte Börsenpreise von aWATTar",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            "Der automatische Tiefpunkt-Alarm basiert auf denselben EPEX Spot Börsenpreisen " +
                            "wie die Prognose. Die aWATTar API liefert stündliche Marktpreise für die nächsten 24 Stunden, " +
                            "die kontinuierlich aktualisiert werden.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
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

    // Dialog für neue Alarme
    if (showAddAlertDialog) {
        var threshold by remember { mutableStateOf("") }
        var isAbove by remember { mutableStateOf(false) }
        var notify by remember { mutableStateOf(true) }

        AlertDialog(
            onDismissRequest = { showAddAlertDialog = false },
            title = { Text("Neuer Preisalarm") },
            text = {
                Column {
                    OutlinedTextField(
                        value = threshold,
                        onValueChange = { threshold = it },
                        label = { Text("Schwellenwert (ct/kWh)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row {
                        RadioButton(
                            selected = !isAbove,
                            onClick = { isAbove = false }
                        )
                        Text("Unter diesem Wert", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row {
                        RadioButton(
                            selected = isAbove,
                            onClick = { isAbove = true }
                        )
                        Text("Über diesem Wert", modifier = Modifier.padding(start = 8.dp))
                    }
                    Row {
                        Checkbox(
                            checked = notify,
                            onCheckedChange = { notify = it }
                        )
                        Text("Benachrichtigung senden", modifier = Modifier.padding(start = 8.dp))
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val thresholdValue = threshold.toDoubleOrNull()
                        if (thresholdValue != null) {
                            val newAlert = com.example.greengrid.data.PriceAlert(
                                threshold = thresholdValue,
                                isAbove = isAbove,
                                notify = notify
                            )
                            alerts = alerts + newAlert
                            preferences.addAlert(newAlert)
                            showAddAlertDialog = false
                        }
                    },
                    enabled = threshold.toDoubleOrNull() != null && threshold.toDoubleOrNull()!! >= -20.0
                ) {
                    Text("Hinzufügen")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddAlertDialog = false }) {
                    Text("Abbrechen")
                }
            }
        )
    }
}

/**
 * Holt die Prognose für die nächsten ~24 Stunden von aWATTar.
 * aWATTar liefert in "marketprice" ct/kWh, "start_timestamp" in Sekunden.
 * Die zurückgegebene Liste ist aufsteigend nach timestamp (jetzt bis 24h später).
 */
suspend fun fetchNext24hPrices(): List<PricePoint> {
    return withContext(Dispatchers.IO) {
        val url = URL("https://api.awattar.at/v1/marketdata")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5000
            readTimeout = 5000
        }
        try {
            if (conn.responseCode != 200) {
                throw Exception("HTTP ${conn.responseCode}")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            println("Debug: API Response length: ${text.length}")
            
            val json = JSONObject(text)
            val arr = json.getJSONArray("data")
            println("Debug: API returned ${arr.length()} data points")
            
            val list = mutableListOf<PricePoint>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val tsSec = obj.getLong("start_timestamp")
                val price = obj.getDouble("marketprice")
                // aWATTar liefert Unix-Timestamps in Sekunden
                // Versuchen wir es ohne Multiplikation
                list += PricePoint(timestamp = tsSec, price = price)
            }
            // Liste ist in der Regel bereits sortiert aufsteigend (jetzt → später). Zur Sicherheit:
            val sortedList = list.sortedBy { it.timestamp }
            println("Debug: Processed ${sortedList.size} price points")
            if (sortedList.isNotEmpty()) {
                println("Debug: Earliest timestamp: ${Date(sortedList.first().timestamp)}")
                println("Debug: Latest timestamp: ${Date(sortedList.last().timestamp)}")
                println("Debug: Current time: ${Date()}")
            }
            sortedList
        } finally {
            conn.disconnect()
        }
    }
}
