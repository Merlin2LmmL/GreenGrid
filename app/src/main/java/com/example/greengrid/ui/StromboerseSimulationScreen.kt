package com.example.greengrid.ui

import android.util.Log
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.copy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.text.style.TextAlign

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
                        mode = selectedRangeIndex
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
                                            Log.d("StromboerseScreen", "Executing trade with user ID: ${currentUser.id}")
                                            val result = repository.executeTrade(trade)
                                            Log.d("StromboerseScreen", "Trade result: $result")
                                            if (result.isSuccess) {
                                                tradeCounter++
                                                delay(1000)
                                                frozenTotalValue = null
                                                delay(1000)
                                                lastTradePrice = null
                                            } else {
                                                Log.e("StromboerseScreen", "Trade failed: ${result.exceptionOrNull()}")
                                            }
                                        } catch (e: Exception) {
                                            Log.e("StromboerseScreen", "Error executing trade", e)
                                        }
                                    }
                                } else {
                                    Log.d("StromboerseScreen", "Buy button disabled. Amount: $amount, Balance: ${currentUser.balance}, Capacity: ${currentUser.capacity}, MaxCapacity: ${currentUser.maxCapacity}")
                                }
                            },
                            enabled = canBuy,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canBuy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                contentColor = if (canBuy) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("Kaufen", fontSize = 14.sp)
                        }

                        Spacer(Modifier.width(8.dp))

                        Button(
                            onClick = {
                                if (canSell) {
                                    scope.launch {
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
                                            delay(1000)
                                            frozenTotalValue = null
                                            delay(1000)
                                            lastTradePrice = null
                                        }
                                    }
                                }
                            },
                            enabled = canSell,
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (canSell) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                                contentColor = if (canSell) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier.height(40.dp)
                        ) {
                            Text("Verkaufen", fontSize = 14.sp)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Auto Trading Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { showAutoTradingDialog = true },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.secondary,
                                contentColor = MaterialTheme.colorScheme.onSecondary
                            ),
                            modifier = Modifier.weight(1f).height(40.dp).padding(end = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    Icons.Default.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Auto Trading", fontSize = 14.sp)
                            }
                        }

                        Button(
                            onClick = {
                                isAutoTradingEnabled = !isAutoTradingEnabled
                            },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isAutoTradingEnabled) 
                                    MaterialTheme.colorScheme.error 
                                else 
                                    MaterialTheme.colorScheme.primary,
                                contentColor = if (isAutoTradingEnabled) 
                                    MaterialTheme.colorScheme.onError 
                                else 
                                    MaterialTheme.colorScheme.onPrimary
                            ),
                            modifier = Modifier.weight(1f).height(40.dp).padding(start = 4.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    if (isAutoTradingEnabled) Icons.Default.Build else Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    if (isAutoTradingEnabled) "Stop" else "Start",
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }

                    // Auto Trading Status
                    if (isAutoTradingEnabled) {
                        Spacer(Modifier.height(8.dp))
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "Auto Trading aktiv (Hintergrund)",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    "Kauf: ${buyThreshold}ct | Verkauf: ${sellThreshold}ct | Menge: ${tradeAmount}kWh",
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
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

@Composable
fun PriceGraph(
    priceHistory: List<PricePoint>,
    colorScheme: ColorScheme,
    mode: Int
) {
    val textMeasurer = rememberTextMeasurer()

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(200.dp)
            .background(colorScheme.surface, RoundedCornerShape(16.dp))
    ) {
        if (priceHistory.size >= 2) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val leftMargin = 40.dp.toPx()
                val rightMargin = 16.dp.toPx()
                val topMargin = 8.dp.toPx()
                val bottomMargin = 30.dp.toPx()

                val graphWidth = size.width - leftMargin - rightMargin
                val graphHeight = size.height - topMargin - bottomMargin

                val originX = leftMargin
                val originY = topMargin + graphHeight

                val minPrice = priceHistory.minOf { it.price }
                val maxPrice = priceHistory.maxOf { it.price }
                val priceRange = (maxPrice - minPrice).coerceAtLeast(0.1)

                drawLine(
                    color = colorScheme.onSurface,
                    start = Offset(originX, topMargin),
                    end = Offset(originX, originY),
                    strokeWidth = 1f
                )
                drawLine(
                    color = colorScheme.onSurface,
                    start = Offset(originX, originY),
                    end = Offset(originX + graphWidth, originY),
                    strokeWidth = 1f
                )

                val segmentsY = 4
                for (i in 0..segmentsY) {
                    val fraction = i.toFloat() / segmentsY
                    val y = topMargin + fraction * graphHeight
                    val price = maxPrice - fraction * priceRange
                    drawLine(
                        color = colorScheme.onSurface,
                        start = Offset(originX - 4.dp.toPx(), y),
                        end = Offset(originX, y),
                        strokeWidth = 1f
                    )
                    val priceText = "%.1f".format(price)
                    val textLayout = textMeasurer.measure(AnnotatedString(priceText))
                    val textX = originX - 6.dp.toPx() - textLayout.size.width
                    val textY = y - textLayout.size.height / 2f
                    drawText(
                        textMeasurer = textMeasurer,
                        text = priceText,
                        topLeft = Offset(textX, textY),
                        style = TextStyle(
                            color = colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                    )
                }

                val pointCount = priceHistory.size
                val labelIndices = mutableListOf<Int>()
                if (pointCount <= 24) {
                    labelIndices += (0 until pointCount)
                } else {
                    labelIndices += 0
                    val maxMiddle = 22
                    val step = (pointCount - 1).toFloat() / (maxMiddle + 1)
                    for (k in 1..maxMiddle) {
                        val idx = (k * step).toInt().coerceIn(1, pointCount - 2)
                        if (labelIndices.lastOrNull() != idx) {
                            labelIndices += idx
                        }
                    }
                    labelIndices += pointCount - 1
                }

                val shortTimeFormat = SimpleDateFormat("HH", Locale.getDefault())
                val shortWeekFormat = SimpleDateFormat("E", Locale.getDefault())
                val shortMonthFormat = SimpleDateFormat("dd", Locale.getDefault())
                val shortYearFormat = SimpleDateFormat("MM.yy", Locale.getDefault())

                val minSpacing = 4.dp.toPx()
                var lastLabelEndX = Float.NEGATIVE_INFINITY

                for (index in labelIndices) {
                    val pricePoint = priceHistory[index]
                    val x = originX + (index.toFloat() / (pointCount - 1)) * graphWidth

                    val rawLabel = when (mode) {
                        0 -> shortTimeFormat.format(Date(pricePoint.timestamp))
                        1 -> shortWeekFormat.format(Date(pricePoint.timestamp))
                        2 -> shortMonthFormat.format(Date(pricePoint.timestamp))
                        else -> shortYearFormat.format(Date(pricePoint.timestamp))
                    }

                    val textLayout = textMeasurer.measure(AnnotatedString(rawLabel))
                    val textX = x - textLayout.size.width / 2f
                    val textY = originY + 4.dp.toPx() + 2.dp.toPx()

                    if (textX > lastLabelEndX + minSpacing && textX + textLayout.size.width < size.width) {
                        drawLine(
                            color = colorScheme.onSurface,
                            start = Offset(x, originY),
                            end = Offset(x, originY + 4.dp.toPx()),
                            strokeWidth = 1f
                        )
                        drawText(
                            textMeasurer = textMeasurer,
                            text = rawLabel,
                            topLeft = Offset(textX, textY),
                            style = TextStyle(
                                color = colorScheme.onSurface,
                                fontSize = 12.sp
                            )
                        )
                        lastLabelEndX = textX + textLayout.size.width
                    }
                }

                val path = Path()
                priceHistory.forEachIndexed { index, pricePoint ->
                    val x = originX + (index.toFloat() / (pointCount - 1)) * graphWidth
                    val y = originY - ((pricePoint.price - minPrice) / priceRange * graphHeight).toFloat()
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                val gradient = Brush.verticalGradient(
                    colors = listOf(
                        colorScheme.primary.copy(alpha = 0.2f),
                        colorScheme.primary.copy(alpha = 0.0f)
                    ),
                    startY = 0f,
                    endY = graphHeight
                )

                val fillPath = path.copy()
                fillPath.lineTo(originX + graphWidth, originY)
                fillPath.lineTo(originX, originY)
                fillPath.close()

                drawPath(
                    path = fillPath,
                    brush = gradient
                )

                drawPath(
                    path = path,
                    color = colorScheme.primary,
                    style = Stroke(width = 2f)
                )
            }
        }
    }
}