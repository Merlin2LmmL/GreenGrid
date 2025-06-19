package com.example.greengrid.ui

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.greengrid.data.FirebaseRepository
import com.example.greengrid.data.User
import com.example.greengrid.data.Trade
import com.example.greengrid.data.PricePoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import com.example.greengrid.data.simulatePriceHistory
import com.example.greengrid.data.PriceSimulationConfigs
import kotlinx.coroutines.delay
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.platform.LocalContext
import androidx.work.*
import com.example.greengrid.data.PriceAlertPreferences
import com.example.greengrid.notification.AutoTradingWorker
import java.util.concurrent.TimeUnit
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.text.style.TextAlign
import com.example.greengrid.ui.components.PriceGraph

@Composable
fun StromboerseScreen(
    user: User
) {
    val context = LocalContext.current
    val repository = remember { FirebaseRepository() }
    val preferences = remember { PriceAlertPreferences(context) }
    val workManager = remember { WorkManager.getInstance(context) }
    
    var currentUser by remember { mutableStateOf(user) }
    val scope = rememberCoroutineScope()
    var tradeCounter by remember { mutableStateOf(0) }
    var allUsers by remember { mutableStateOf<List<User>>(emptyList()) }
    var lastLeaderboardUpdate by remember { mutableStateOf(0L) }
    
    // Auto trading state - load from preferences
    var isAutoTradingEnabled by remember { mutableStateOf(preferences.isAutoTradingEnabled) }
    var showAutoTradingDialog by remember { mutableStateOf(false) }

    // Auto trading parameters - load from preferences
    var buyThreshold by remember { mutableStateOf(preferences.buyThreshold.toString()) }
    var sellThreshold by remember { mutableStateOf(preferences.sellThreshold.toString()) }
    var tradeAmount by remember { mutableStateOf(preferences.tradeAmount.toString()) }
    var checkInterval by remember { mutableStateOf(preferences.checkInterval.toString()) }

    // Tab selection
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Strombörse", "Bestenliste")

    // Shop dialog state
    var showShopDialog by remember { mutableStateOf(false) }

    val shopOptions = listOf(
        ShopItem(1.0, 400.0),
        ShopItem(5.0, 1800.0),
        ShopItem(10.0, 3400.0)
    )

    // Function to start/stop background auto trading
    fun toggleBackgroundAutoTrading(enabled: Boolean) {
        if (enabled) {
            // Start background auto trading
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val intervalMinutes = preferences.checkInterval.coerceAtLeast(15) // Minimum 15 minutes for WorkManager
            val workRequest = PeriodicWorkRequestBuilder<AutoTradingWorker>(
                intervalMinutes.toLong(), TimeUnit.MINUTES,
                5, TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .build()

            workManager.enqueueUniquePeriodicWork(
                AutoTradingWorker.WORK_NAME,
                ExistingPeriodicWorkPolicy.REPLACE,
                workRequest
            )
            Log.d("AutoTrading", "Background auto trading started with interval: $intervalMinutes minutes")
        } else {
            // Stop background auto trading
            workManager.cancelUniqueWork(AutoTradingWorker.WORK_NAME)
            Log.d("AutoTrading", "Background auto trading stopped")
        }
    }

    // Update preferences when auto trading state changes
    LaunchedEffect(isAutoTradingEnabled) {
        preferences.isAutoTradingEnabled = isAutoTradingEnabled
        toggleBackgroundAutoTrading(isAutoTradingEnabled)
    }

    // Collect current user updates
    LaunchedEffect(Unit) {
        repository.observeUserData().collect { updatedUser ->
            Log.d("StromboerseScreen", "Current user updated: id=${updatedUser.id}, username=${updatedUser.username}, balance=${updatedUser.balance}, co2Saved=${updatedUser.co2Saved}")
            if (updatedUser.id.isNotEmpty()) {
                currentUser = updatedUser
            } else {
                Log.e("StromboerseScreen", "Received user update with empty ID")
            }
        }
    }

    // Separate LaunchedEffect for loading all users with periodic updates
    LaunchedEffect(Unit) {
        while (true) {
            try {
                val currentTime = System.currentTimeMillis()
                // Update leaderboard only every 5 minutes
                if (currentTime - lastLeaderboardUpdate > 300000) { // 5 minutes in milliseconds
                    Log.d("StromboerseScreen", "Starting to load all users")
                    val users = repository.readAllUsersDirectly()
                    Log.d("StromboerseScreen", "Loaded users directly: $users")
                    allUsers = users
                    lastLeaderboardUpdate = currentTime
                }
            } catch (e: Exception) {
                Log.e("StromboerseScreen", "Error loading users", e)
            }
            delay(60000) // Check every minute
        }
    }

    // Cleanup old data every hour
    LaunchedEffect(Unit) {
        while (true) {
            try {
                repository.cleanupOldData()
            } catch (e: Exception) {
                Log.e("StromboerseScreen", "Error during data cleanup", e)
            }
            delay(3600000) // 1 hour
        }
    }

    var inputAmount by remember { mutableStateOf("10.0") }

    val timeRanges = listOf(
        "Tag" to 24,
        "Woche" to 24 * 7,
        "Monat" to 24 * 30,
        "Jahr" to 24 * 365
    )
    var selectedRangeIndex by remember { mutableStateOf(0) }

    val priceHistory by repository.observePriceHistory().collectAsState(initial = emptyList())

    val basePriceHistory = remember(selectedRangeIndex) {
        val config = PriceSimulationConfigs.getConfigForTimeRange(selectedRangeIndex)
        simulatePriceHistory(
            rangeHours = config.rangeHours,
            basePrice = config.basePrice,
            seasonalAmp = config.seasonalAmp,
            dailyAmp = config.dailyAmp,
            weekendOffset = config.weekendOffset,
            noiseAmpLong = config.noiseAmpLong,
            noiseAmpShort = config.noiseAmpShort,
            seedLong = config.seedLong,
            seedShort = config.seedShort,
            intervalHours = config.intervalHours
        )
    }

    val combinedPriceHistory = remember(basePriceHistory, priceHistory, tradeCounter) {
        val marketPriceByHour = priceHistory.associateBy { it.timestamp }
        basePriceHistory.map { simPoint ->
            marketPriceByHour[simPoint.timestamp] ?: simPoint
        }
    }

    var lastTradePrice by remember { mutableStateOf<Double?>(null) }
    var frozenTotalValue by remember { mutableStateOf<Double?>(null) }

    val currentPrice = remember(priceHistory, basePriceHistory) {
        val lastVisible = combinedPriceHistory.lastOrNull()?.price
        lastVisible ?: basePriceHistory.last().price
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tab Selection
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
                    modifier = Modifier.weight(1f).height(38.dp).padding(horizontal = 2.dp)
                ) {
                    Text(tab, fontSize = 14.sp, maxLines = 1)
                }
            }
        }

        when (selectedTab) {
            0 -> {
                // Strombörse Tab
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    // Zeitbereichs-Buttons gleichmäßig verteilt, kleine Schrift, wenig Padding
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        timeRanges.forEachIndexed { idx, (label, _) ->
                            Button(
                                onClick = { if (selectedRangeIndex != idx) selectedRangeIndex = idx },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (selectedRangeIndex == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                                    contentColor = if (selectedRangeIndex == idx) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                                ),
                                modifier = Modifier.weight(1f).height(36.dp).padding(horizontal = 0.dp)
                            ) {
                                Text(label, fontSize = 12.sp, maxLines = 1, softWrap = false)
                            }
                        }
                    }

                    PriceGraph(
                        priceHistory = combinedPriceHistory,
                        colorScheme = MaterialTheme.colorScheme,
                        mode = selectedRangeIndex,
                        height = 200,
                        showHover = true
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Current Price Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .height(80.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Preis (ct/kWh)",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "${"%.2f".format(currentPrice)}",
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }

                        // Balance Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .height(80.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Verfügbares Guthaben",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "${"%.2f".format(currentUser.balance)}€",
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }

                        // Capacity Card
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .padding(4.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surface
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .padding(10.dp)
                                    .height(80.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Kapazität (kWh)",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    "${"%.0f".format(currentUser.capacity)}/${"%.0f".format(currentUser.maxCapacity)}",
                                    fontSize = 20.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.CenterHorizontally)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                            .padding(16.dp)
                    ) {
                        val storedEnergyValue = currentUser.capacity * currentPrice / 100.0 * (1 - 0.05) // 10% Steuer nur auf gespeicherte Energie
                        val totalValue = storedEnergyValue + currentUser.balance // Balance ohne Steuer
                        Text(
                            text = "Gesamtwert: %.2f €".format(totalValue),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.align(Alignment.CenterStart)
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = inputAmount,
                            onValueChange = { inputAmount = it },
                            label = { Text("Menge (kWh)", color = MaterialTheme.colorScheme.primary) },
                            modifier = Modifier.width(120.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.secondary,
                                cursorColor = MaterialTheme.colorScheme.primary,
                                focusedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )

                        Spacer(Modifier.width(8.dp))

                        val amount = inputAmount.toDoubleOrNull() ?: 0.0
                        val canBuy = amount > 0 && currentUser.balance >= amount * currentPrice / 100.0 && currentUser.capacity + amount <= currentUser.maxCapacity
                        val canSell = amount > 0 && currentUser.capacity >= amount

                        Button(
                            onClick = {
                                if (canBuy) {
                                    Log.d("StromboerseScreen", "Buy button clicked. Amount: $amount, Current price: $currentPrice")
                                    if (currentUser.id.isEmpty()) {
                                        Log.e("StromboerseScreen", "Cannot execute trade: User ID is empty")
                                        return@Button
                                    }
                                    if (currentUser.capacity + amount > currentUser.maxCapacity) {
                                        Log.e("StromboerseScreen", "Cannot execute trade: Would exceed max capacity")
                                        return@Button
                                    }
                                    scope.launch {
                                        try {
                                            lastTradePrice = currentPrice
                                            frozenTotalValue = currentUser.balance + currentUser.capacity * currentPrice / 100.0
                                            val trade = Trade(
                                                userId = currentUser.id,
                                                amount = amount,
                                                price = currentPrice,
                                                isBuy = true,
                                                timestamp = System.currentTimeMillis()
                                            )
                                            val result = repository.executeTrade(trade)
                                            if (result.isSuccess) {
                                                tradeCounter++
                                                Log.d("StromboerseScreen", "Buy trade executed successfully")
                                            } else {
                                                Log.e("StromboerseScreen", "Buy trade failed: ${result.exceptionOrNull()}")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("StromboerseScreen", "Error executing buy trade", e)
                                        }
                                    }
                                }
                            },
                            enabled = canBuy,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Kaufen")
                        }

                        Spacer(Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (canSell) {
                                    Log.d("StromboerseScreen", "Sell button clicked. Amount: $amount, Current price: $currentPrice")
                                    if (currentUser.id.isEmpty()) {
                                        Log.e("StromboerseScreen", "Cannot execute trade: User ID is empty")
                                        return@Button
                                    }
                                    scope.launch {
                                        try {
                                            lastTradePrice = currentPrice
                                            frozenTotalValue = currentUser.balance + currentUser.capacity * currentPrice / 100.0
                                            val trade = Trade(
                                                userId = currentUser.id,
                                                amount = amount,
                                                price = currentPrice,
                                                isBuy = false,
                                                timestamp = System.currentTimeMillis()
                                            )
                                            val result = repository.executeTrade(trade)
                                            if (result.isSuccess) {
                                                tradeCounter++
                                                Log.d("StromboerseScreen", "Sell trade executed successfully")
                                            } else {
                                                Log.e("StromboerseScreen", "Sell trade failed: ${result.exceptionOrNull()}")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("StromboerseScreen", "Error executing sell trade", e)
                                        }
                                    }
                                }
                            },
                            enabled = canSell,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Verkaufen")
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Auto Trading Section
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
                                        "Auto Trading",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "Automatische Käufe und Verkäufe basierend auf Preisschwellen",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                                Switch(
                                    checked = isAutoTradingEnabled,
                                    onCheckedChange = { isAutoTradingEnabled = it },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        checkedTrackColor = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.5f),
                                        uncheckedThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        uncheckedTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                                    )
                                )
                            }
                            if (isAutoTradingEnabled) {
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        Icons.Default.Build,
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
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { showAutoTradingDialog = true },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                        contentColor = MaterialTheme.colorScheme.primaryContainer
                                    ),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Einstellungen")
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Shop Button
                    Button(
                        onClick = { showShopDialog = true },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary
                        ),
                        modifier = Modifier.fillMaxWidth().height(48.dp)
                    ) {
                        Icon(Icons.Default.ShoppingCart, contentDescription = "Shop", modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Batterie-Shop", fontSize = 16.sp)
                    }

                    HorizontalDivider(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        thickness = 2.dp,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    // Trading Platform Information (below trading interface)
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
                                "🏢 GreenGrid Strombörse",
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            Text(
                                "Die GreenGrid Strombörse ist eine simulierte Handelsplattform für Bildungszwecke. " +
                                "Hier können Sie das Prinzip des Stromhandels in einer sicheren, virtuellen Umgebung erlernen. " +
                                "Alle Transaktionen erfolgen mit Spielgeld - es werden keine echten Zahlungen getätigt.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            Text(
                                "Peer-to-Peer Handel:",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Text(
                                "Die Preise werden durch eine Kombination aus Spieleraktivität und simulierten Marktmechanismen bestimmt. " +
                                "Das Angebot und die Nachfrage aller Spieler beeinflussen die Preise, zusätzlich wird eine realistische " +
                                "Preisbildung durch mehrere Sinuskurven und Rauschen simuliert. Dies erzeugt erkennbare Muster wie " +
                                "Tages- und Nachtschwankungen, Wochenend-Effekte und saisonale Trends, ähnlich wie in echten Strombörsen.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Text(
                                "Preisbildung:",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Text(
                                "• Spieleraktivität: Angebot und Nachfrage beeinflussen die Preise direkt\n" +
                                "• Tages-/Sessionsrythmus: Sinuskurven simulieren typische Verbrauchsmuster\n" +
                                "• Wochentrends: Unterschiede zwischen Werktagen und Wochenenden\n" +
                                "• Marktvolatilität: Rauschen simuliert unvorhersehbare Marktbewegungen",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            Text(
                                "Lernziel:",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            
                            Text(
                                "• Verstehen Sie die Grundprinzipien des Stromhandels\n" +
                                "• Lernen Sie, Angebot und Nachfrage zu nutzen\n" +
                                "• Entwickeln Sie Strategien für optimales Timing\n" +
                                "• Erfahren Sie, wie Marktmechanismen funktionieren",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(bottom = 12.dp)
                            )
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.errorContainer
                                )
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        "💰 Simulierte Handelsgebühren:",
                                        style = MaterialTheme.typography.titleMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        "• 5% Steuern auf jeden Trade (simuliert)\n" +
                                        "• 0,10€ Netzsteuer pro Kauf (simuliert)\n" +
                                        "• Alle Transaktionen erfolgen mit virtuellem Guthaben",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onErrorContainer
                                    )
                                }
                            }
                        }
                    }
                }

                // Shop Dialog
                if (showShopDialog) {
                    ShopDialog(
                        currentCapacity = currentUser.maxCapacity,
                        balance = currentUser.balance,
                        onBuy = { extraCapacity, price ->
                            if (currentUser.balance >= price) {
                                currentUser.maxCapacity += extraCapacity
                                currentUser.balance -= price
                                // Update user data in database
                                scope.launch {
                                    try {
                                        repository.updateUser(currentUser)
                                        Log.d("Shop", "User data updated successfully after purchase")
                                    } catch (e: Exception) {
                                        Log.e("Shop", "Error updating user data after purchase", e)
                                        // Optionally revert the changes if database update fails
                                        currentUser.maxCapacity -= extraCapacity
                                        currentUser.balance += price
                                    }
                                }
                                showShopDialog = false
                            }
                        },
                        onDismiss = { showShopDialog = false },
                        shopOptions = shopOptions
                    )
                }
            }
            1 -> {
                // Bestenliste Tab
                var isRefreshing by remember { mutableStateOf(false) }
                var selectedLeaderboard by remember { mutableStateOf("profit") }

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Bestenliste",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.primary
                            )

                            IconButton(
                                onClick = {
                                    isRefreshing = true
                                    scope.launch {
                                        try {
                                            val users = repository.readAllUsersDirectly()
                                            allUsers = users
                                        } catch (e: Exception) {
                                            Log.e("StromboerseScreen", "Error refreshing users", e)
                                        } finally {
                                            isRefreshing = false
                                        }
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Aktualisieren",
                                    modifier = Modifier
                                        .size(20.dp)
                                        .then(
                                            if (isRefreshing) {
                                                Modifier.graphicsLayer {
                                                    rotationZ = 360f
                                                }
                                            } else Modifier
                                        )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                Button(
                                    onClick = { selectedLeaderboard = "profit" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedLeaderboard == "profit")
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.surface,
                                        contentColor = if (selectedLeaderboard == "profit")
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                        .height(36.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Face,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Profit", fontSize = 12.sp)
                                    }
                                }

                                Button(
                                    onClick = { selectedLeaderboard = "co2" },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (selectedLeaderboard == "co2")
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.surface,
                                        contentColor = if (selectedLeaderboard == "co2")
                                            MaterialTheme.colorScheme.onPrimary
                                        else
                                            MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 4.dp)
                                        .height(36.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            Icons.Default.Star,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("CO2", fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(0.975f)
                        ) {
                            LazyColumn {
                                // Sortiere die Spieler nach dem ausgewählten Wert
                                val topPlayers = allUsers
                                    .filter { it.id.isNotEmpty() && it.username.isNotEmpty() } // Nur echte Spieler
                                    .sortedByDescending { user ->
                                        if (selectedLeaderboard == "profit") {
                                            val storedEnergyValue = user.capacity * currentPrice / 100.0 * (1 - 0.1) // 10% Steuer nur auf gespeicherte Energie
                                            val baseValue = storedEnergyValue + user.balance
                                            baseValue
                                        } else {
                                            user.co2Saved
                                        }
                                    }

                                val topPlayersCount = minOf(topPlayers.size, 100)

                                // Fülle die Liste auf n Einträge auf
                                val displayUsers = if (topPlayers.isEmpty()) {
                                    List(topPlayersCount) { index ->
                                        User(
                                            id = "empty_$index",
                                            username = "Unknown User",
                                            balance = 0.0,
                                            capacity = 0.0,
                                            co2Saved = 0.0
                                        )
                                    }
                                } else {
                                    val filledList = topPlayers.toMutableList()
                                    while (filledList.size < topPlayersCount) {
                                        filledList.add(User(
                                            id = "empty_${filledList.size}",
                                            username = "Unknown User",
                                            balance = 0.0,
                                            capacity = 0.0,
                                            co2Saved = 0.0
                                        ))
                                    }
                                    filledList.take(topPlayersCount)
                                }

                                items(displayUsers) { user ->
                                    val isCurrentUser = user.username == currentUser.username
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                            .background(
                                                if (isCurrentUser)
                                                    MaterialTheme.colorScheme.primaryContainer
                                                else
                                                    MaterialTheme.colorScheme.surface,
                                                RoundedCornerShape(8.dp)
                                            )
                                            .padding(horizontal = 8.dp, vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                "#${displayUsers.indexOf(user) + 1}",
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isCurrentUser)
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                else if (user.id.startsWith("empty_"))
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                else
                                                    MaterialTheme.colorScheme.primary
                                            )
                                            Text(
                                                user.username,
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = if (isCurrentUser)
                                                    MaterialTheme.colorScheme.onPrimaryContainer
                                                else if (user.id.startsWith("empty_"))
                                                    MaterialTheme.colorScheme.onSurfaceVariant
                                                else
                                                    MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            if (selectedLeaderboard == "profit") {
                                                "%.2f €".format(user.balance + user.capacity * currentPrice / 100.0)
                                            } else {
                                                "%.0f g".format(user.co2Saved)
                                            },
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = if (isCurrentUser)
                                                MaterialTheme.colorScheme.onPrimaryContainer
                                            else if (user.id.startsWith("empty_"))
                                                MaterialTheme.colorScheme.onSurfaceVariant
                                            else
                                                MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Auto Trading Dialog
    if (showAutoTradingDialog) {
        AutoTradingDialog(
            buyThreshold = buyThreshold,
            onBuyThresholdChange = { 
                buyThreshold = it
                it.toDoubleOrNull()?.let { value -> preferences.buyThreshold = value }
            },
            sellThreshold = sellThreshold,
            onSellThresholdChange = { 
                sellThreshold = it
                it.toDoubleOrNull()?.let { value -> preferences.sellThreshold = value }
            },
            tradeAmount = tradeAmount,
            onTradeAmountChange = { 
                tradeAmount = it
                it.toDoubleOrNull()?.let { value -> preferences.tradeAmount = value }
            },
            checkInterval = checkInterval,
            onCheckIntervalChange = { 
                checkInterval = it
                it.toIntOrNull()?.let { value -> 
                    preferences.checkInterval = value
                    // Restart background worker with new interval if auto trading is enabled
                    if (isAutoTradingEnabled) {
                        scope.launch {
                            toggleBackgroundAutoTrading(false)
                            delay(100)
                            toggleBackgroundAutoTrading(true)
                        }
                    }
                }
            },
            onDismiss = { showAutoTradingDialog = false }
        )
    }
}

@Composable
fun AutoTradingDialog(
    buyThreshold: String,
    onBuyThresholdChange: (String) -> Unit,
    sellThreshold: String,
    onSellThresholdChange: (String) -> Unit,
    tradeAmount: String,
    onTradeAmountChange: (String) -> Unit,
    checkInterval: String,
    onCheckIntervalChange: (String) -> Unit,
    onDismiss: () -> Unit
) {
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
                    "Auto Trading Einstellungen",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                OutlinedTextField(
                    value = buyThreshold,
                    onValueChange = onBuyThresholdChange,
                    label = { Text("Kauf-Schwelle (ct/kWh)") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.secondary
                    )
                )

                OutlinedTextField(
                    value = sellThreshold,
                    onValueChange = onSellThresholdChange,
                    label = { Text("Verkauf-Schwelle (ct/kWh)") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.secondary
                    )
                )

                OutlinedTextField(
                    value = tradeAmount,
                    onValueChange = onTradeAmountChange,
                    label = { Text("Handelsmenge (kWh)") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.secondary
                    )
                )

                OutlinedTextField(
                    value = checkInterval,
                    onValueChange = onCheckIntervalChange,
                    label = { Text("Prüfintervall (Sekunden)") },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.secondary
                    )
                )

                Spacer(modifier = Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Abbrechen")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(onClick = onDismiss) {
                        Text("Speichern")
                    }
                }
            }
        }
    }
}

data class ShopItem(val capacity: Double, val price: Double)

@Composable
fun ShopDialog(
    currentCapacity: Double,
    balance: Double,
    onBuy: (Double, Double) -> Unit,
    onDismiss: () -> Unit,
    shopOptions: List<ShopItem>
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.ShoppingCart, contentDescription = null, modifier = Modifier.size(40.dp))
                Spacer(modifier = Modifier.height(8.dp))
                Text("Batterie-Shop", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Text("Erweitere deinen Speicher realistisch und investiere in größere Batterien!", style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Text("Aktuelle Kapazität: ${"%.1f".format(currentCapacity)} kWh", style = MaterialTheme.typography.bodyLarge)
                Text("Guthaben: ${"%.2f".format(balance)} €", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary)
                Spacer(modifier = Modifier.height(16.dp))
                shopOptions.forEach { item ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("+${item.capacity.toInt()} kWh Speicher", style = MaterialTheme.typography.titleMedium)
                                Text("Preis: ${"%.2f".format(item.price)} €", style = MaterialTheme.typography.bodyMedium)
                            }
                            Button(
                                onClick = { onBuy(item.capacity, item.price) },
                                enabled = balance >= item.price,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (balance >= item.price) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
                                    contentColor = if (balance >= item.price) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            ) {
                                Text("Kaufen")
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                TextButton(onClick = onDismiss) { Text("Schließen") }
            }
        }
    }
}