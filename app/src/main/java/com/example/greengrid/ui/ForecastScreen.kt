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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.copy
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.greengrid.data.PricePoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.*


/**
 * Haupt-Screen, genannt ForecastScreen.
 * Zeigt Chart für die nächsten 24h (aWATTar), dreht Darstellung so, dass aktueller Preis rechts ist.
 * Zeigt Erklärung, falls negative Preise vorhanden.
 */
@Composable
fun ForecastScreen(
    onNavigateToHome: () -> Unit,
    onNavigateToPriceAlert: () -> Unit
) {
    var priceHistory by remember { mutableStateOf<List<PricePoint>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // Beim Start laden
    LaunchedEffect(Unit) {
        isLoading = true
        error = null
        try {
            val fetched = fetchNext24hPrices()
            priceHistory = fetched
        } catch (e: Exception) {
            error = e.message ?: "Fehler beim Laden"
        } finally {
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top App Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onNavigateToHome) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Zurück")
            }
            Text(
                "Prognosen der nächsten 24h",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Graph-Bereich
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            when {
                isLoading -> CircularProgressIndicator()
                error != null -> Text(error!!, color = MaterialTheme.colorScheme.error)
                priceHistory.size >= 2 -> {
                    PriceGraph24h(
                        priceHistory = priceHistory,
                        colorScheme = MaterialTheme.colorScheme
                    )
                }
                else -> Text("Keine Daten verfügbar")
            }
        }

        // Erklärung, falls negative Preise vorhanden
        if (priceHistory.any { it.price < 0.0 }) {
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(8.dp))
                    .padding(12.dp)
            ) {
                Text(
                    text = "Die rote Linie im Graphen markiert den Nullpreis. Werte unterhalb dieser Linie sind negative Preise, d.h. Anbieter zahlen, um Strom abzunehmen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Aktueller Preis: das erste Element in priceHistory (jetzt), passend zur rechten Position im Graphen
        val currentPrice = priceHistory.firstOrNull()?.price
        if (currentPrice != null) {
            Text(
                "Aktueller Preis: ${"%.2f".format(currentPrice)} ct/kWh",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
        }
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
            val json = JSONObject(text)
            val arr = json.getJSONArray("data")
            val list = mutableListOf<PricePoint>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                val tsSec = obj.getLong("start_timestamp")
                val price = obj.getDouble("marketprice")
                // PricePoint(timestampMillis, priceCtPerKwh)
                list += PricePoint(timestamp = tsSec * 1000, price = price)
            }
            // Liste ist in der Regel bereits sortiert aufsteigend (jetzt → später). Zur Sicherheit:
            list.sortedBy { it.timestamp }
        } finally {
            conn.disconnect()
        }
    }
}

/**
 * Zeichnet den Preisverlauf der nächsten 24h.
 * Wir erwarten priceHistory aufsteigend sortiert: [jetzt, ... , 24h später].
 * Für die Darstellung drehen wir die Reihenfolge um, sodass 'jetzt' rechts erscheint.
 * X-Achse zeigt drei Zeitpunkte: links = 24h später, Mitte ~12h später, rechts = jetzt.
 * Rote Linie bei Y=0, falls negative Preise existieren.
 */
@Composable
fun PriceGraph24h(
    priceHistory: List<PricePoint>,
    colorScheme: ColorScheme
) {
    val textMeasurer = rememberTextMeasurer()
    val formatHour = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }

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

                // Sicherstellen: absteigend sortiert [später .. jetzt]
                val sortedDesc = priceHistory.sortedByDescending { it.timestamp }
                // Für Darstellung: jetzt ist links, 24h später ist rechts
                val plotList = sortedDesc.reversed() // [jetzt .. 24h später]

                // Wertebereich
                val minPrice = sortedDesc.minOf { it.price }
                val maxPrice = sortedDesc.maxOf { it.price }
                val priceRange = (maxPrice - minPrice).coerceAtLeast(0.1)

                // Achsen
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

                // Y-Ticks und Labels
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
                    val priceText = "%.2f".format(price)
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

                // X-Achse Labels: drei Zeitpunkte basierend auf der aktuellen Zeit
                val now = System.currentTimeMillis()  // Aktuelle Zeit

                val numberOfTimePoints = 7  // Anzahl der Zeitpunkte (jetzt + 6 weitere)
                val timePoints = (0 until numberOfTimePoints).map { index ->
                    // Gleichmäßige Verteilung über 24 Stunden
                    val hoursToAdd = (index * 24.0 / (numberOfTimePoints - 1)).toLong()
                    now + (hoursToAdd * 60 * 60 * 1000)
                }

                // Zeichnen der Labels
                for ((index, timePoint) in timePoints.withIndex()) {
                    val x = originX + (index.toFloat() / (timePoints.size - 1)) * graphWidth
                    val rawLabel = formatHour.format(Date(timePoint))
                    val textLayout = textMeasurer.measure(AnnotatedString(rawLabel))
                    val textX = x - textLayout.size.width / 2f
                    val textY = originY + 4.dp.toPx() + 2.dp.toPx()
                    // Tick
                    drawLine(
                        color = colorScheme.onSurface,
                        start = Offset(x, originY),
                        end = Offset(x, originY + 4.dp.toPx()),
                        strokeWidth = 1f
                    )
                    // Text
                    drawText(
                        textMeasurer = textMeasurer,
                        text = rawLabel,
                        topLeft = Offset(textX, textY),
                        style = TextStyle(
                            color = colorScheme.onSurface,
                            fontSize = 12.sp
                        )
                    )
                }

                // Pfad für Preisverlauf über plotList
                val path = Path()
                plotList.forEachIndexed { index, pricePoint ->
                    val x = originX + (index.toFloat() / (plotList.size - 1)) * graphWidth
                    val y = originY - ((pricePoint.price - minPrice) / priceRange * graphHeight).toFloat()
                    if (index == 0) {
                        path.moveTo(x, y)
                    } else {
                        path.lineTo(x, y)
                    }
                }

                // Gradient-Füllung unter der Kurve
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
                drawPath(path = fillPath, brush = gradient)

                // Kurve zeichnen
                drawPath(
                    path = path,
                    color = colorScheme.primary,
                    style = Stroke(width = 2f)
                )

                // Rote Null-Linie bei Y=0, falls negative existieren
                if (minPrice < 0.0 && maxPrice > 0.0) {
                    val yZero = originY - ((0.0 - minPrice) / priceRange * graphHeight).toFloat()
                    drawLine(
                        color = Color.Red,
                        start = Offset(originX, yZero),
                        end = Offset(originX + graphWidth, yZero),
                        strokeWidth = 2f
                    )
                }
            }
        }
    }
}
