package com.example.greengrid.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.example.greengrid.data.MarketState
import com.example.greengrid.data.PricePoint
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch
import com.example.greengrid.data.simulatePriceHistory
import com.example.greengrid.data.PriceSimulationConfigs
import kotlinx.coroutines.delay

@Composable
fun StromboerseScreen(
    user: User,
    onNavigateToHome: () -> Unit
) {
    val repository = remember { FirebaseRepository() }
    var currentUser by remember { mutableStateOf(user) }
    val scope = rememberCoroutineScope()
    var tradeCounter by remember { mutableStateOf(0) }

    LaunchedEffect(Unit) {
        repository.observeUserData().collect { updatedUser ->
            currentUser = updatedUser
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

    val rangeHours = when (selectedRangeIndex) {
        0 -> 24L
        1 -> 24L * 7
        2 -> 24L * 30
        else -> 24L * 365
    }
    val pointsCount = when (selectedRangeIndex) {
        0 -> 24
        1 -> 28
        2 -> 30
        else -> 12
    }
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

    val trades by repository.observeUserTrades().collectAsState(initial = emptyList())

    val marketState by repository.observeMarketState().collectAsState(initial = MarketState())

    val filteredTrades = remember(trades, selectedRangeIndex) {
        val now = System.currentTimeMillis()
        val rangeHours = when (selectedRangeIndex) {
            0 -> 24L
            1 -> 24L * 7
            2 -> 24L * 30
            else -> 24L * 365
        }
        val startTime = now - (rangeHours * 60 * 60 * 1000)
        trades.filter { it.timestamp >= startTime }
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
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateToHome) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
            }
            Text(
                "Strombörse",
                fontSize = 28.sp,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            timeRanges.forEachIndexed { idx, (label, _) ->
                Button(
                    onClick = { if (selectedRangeIndex != idx) selectedRangeIndex = idx },
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedRangeIndex == idx) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface,
                        contentColor = if (selectedRangeIndex == idx) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary
                    ),
                    modifier = Modifier.weight(1f).height(38.dp).padding(horizontal = 2.dp)
                ) {
                    Text(label, fontSize = 14.sp, maxLines = 1)
                }
            }
        }

        PriceGraph(
            priceHistory = combinedPriceHistory,
            colorScheme = MaterialTheme.colorScheme,
            mode = selectedRangeIndex
        )

        Spacer(Modifier.height(16.dp))

        Text(
            "Aktueller Preis: ${"%.2f".format(currentPrice)} ct/kWh",
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(Modifier.height(8.dp))

        Text("Guthaben: ${"%.2f".format(currentUser.balance)} €", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
        Text("Verfügbare Kapazität: ${"%.2f".format(currentUser.capacity)} kWh / ${currentUser.maxCapacity} kWh", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
        Text("Gesamt gekauft: ${"%.2f".format(currentUser.totalBought)} kWh", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)
        Text("Gesamt verkauft: ${"%.2f".format(currentUser.totalSold)} kWh", fontSize = 16.sp, color = MaterialTheme.colorScheme.onBackground)

        Spacer(Modifier.height(16.dp))

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
                        scope.launch {
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
                                delay(1000)
                                frozenTotalValue = null
                                delay(1000)
                                lastTradePrice = null
                            }
                        }
                    }
                },
                enabled = canBuy,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (canBuy) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    contentColor = if (canBuy) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
                )
            ) {
                Text("Kaufen", fontSize = 16.sp)
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
                )
            ) {
                Text("Verkaufen", fontSize = 16.sp)
            }
        }

        Spacer(Modifier.height(20.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(16.dp)
        ) {
            val priceForValue = lastTradePrice ?: currentPrice
            val totalValue = frozenTotalValue ?: (currentUser.balance + currentUser.capacity * priceForValue / 100.0)
            Text(
                "Gesamtwert: ${"%.2f".format(totalValue)} €",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.align(Alignment.CenterStart)
            )
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
    val dateFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val weekFormat = remember { SimpleDateFormat("EEE", Locale.getDefault()) }
    val monthFormat = remember { SimpleDateFormat("dd.MM", Locale.getDefault()) }
    val yearFormat = remember { SimpleDateFormat("MM.yy", Locale.getDefault()) }

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