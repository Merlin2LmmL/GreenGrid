package com.example.greengrid.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.copy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.greengrid.data.PricePoint
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PriceGraph(
    priceHistory: List<PricePoint>,
    colorScheme: androidx.compose.material3.ColorScheme,
    mode: Int = 0,
    height: Int = 200,
    showHover: Boolean = true,
    showZeroLine: Boolean = false
) {
    val textMeasurer = rememberTextMeasurer()
    var hoverPosition by remember { mutableStateOf<Float?>(null) }
    var showTooltip by remember { mutableStateOf(false) }
    var hoveredPricePoint by remember { mutableStateOf<PricePoint?>(null) }
    var lastHoverUpdate by remember { mutableStateOf(0L) }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .background(colorScheme.surface, RoundedCornerShape(16.dp))
            .then(
                if (showHover) {
                    Modifier
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onPress = { offset ->
                                    hoverPosition = offset.x
                                    showTooltip = true
                                    tryAwaitRelease()
                                    showTooltip = false
                                    hoverPosition = null
                                    hoveredPricePoint = null
                                }
                            )
                        }
                        .pointerInput(Unit) {
                            detectDragGestures(
                                onDragStart = { offset ->
                                    hoverPosition = offset.x
                                    showTooltip = true
                                    lastHoverUpdate = System.currentTimeMillis()
                                },
                                onDrag = { change, _ ->
                                    val currentTime = System.currentTimeMillis()
                                    // Throttle updates to every 16ms (60fps)
                                    if (currentTime - lastHoverUpdate > 16) {
                                        hoverPosition = change.position.x
                                        lastHoverUpdate = currentTime
                                    }
                                    showTooltip = true
                                },
                                onDragEnd = {
                                    showTooltip = false
                                    hoverPosition = null
                                    hoveredPricePoint = null
                                }
                            )
                        }
                } else {
                    Modifier
                }
            )
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

                // Draw axes
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

                // Draw Y-axis labels
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

                // Draw X-axis labels
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

                // Draw price line
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

                // Draw gradient fill
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

                // Rote Null-Linie bei Y=0, falls negative Preise existieren und showZeroLine aktiviert ist
                if (showZeroLine && minPrice < 0.0 && maxPrice > 0.0) {
                    val yZero = originY - ((0.0 - minPrice) / priceRange * graphHeight).toFloat()
                    drawLine(
                        color = androidx.compose.ui.graphics.Color.Red,
                        start = Offset(originX, yZero),
                        end = Offset(originX + graphWidth, yZero),
                        strokeWidth = 2f
                    )
                }

                // Draw hover line and intersection point
                if (showHover) {
                    hoverPosition?.let { hoverX ->
                        if (hoverX >= originX && hoverX <= originX + graphWidth) {
                            // Draw vertical line
                            drawLine(
                                color = colorScheme.primary.copy(alpha = 0.7f),
                                start = Offset(hoverX, topMargin),
                                end = Offset(hoverX, originY),
                                strokeWidth = 2f
                            )

                            // Calculate interpolated intersection point (optimized)
                            val normalizedX = (hoverX - originX) / graphWidth
                            val exactIndex = normalizedX * (pointCount - 1)
                            
                            // Only interpolate if we're between points
                            val interpolatedPricePoint = if (exactIndex.toInt() < pointCount - 1) {
                                val lowerIndex = exactIndex.toInt().coerceIn(0, pointCount - 2)
                                val upperIndex = lowerIndex + 1
                                val fraction = exactIndex - lowerIndex
                                
                                val lowerPoint = priceHistory[lowerIndex]
                                val upperPoint = priceHistory[upperIndex]
                                
                                // Interpolate price
                                val interpolatedPrice = lowerPoint.price + (upperPoint.price - lowerPoint.price) * fraction
                                val interpolatedTimestamp = lowerPoint.timestamp + ((upperPoint.timestamp - lowerPoint.timestamp) * fraction).toLong()
                                
                                PricePoint(
                                    timestamp = interpolatedTimestamp,
                                    price = interpolatedPrice,
                                    volume = 0.0
                                )
                            } else {
                                // Use exact point if at boundary
                                priceHistory[exactIndex.toInt().coerceIn(0, pointCount - 1)]
                            }
                            
                            hoveredPricePoint = interpolatedPricePoint
                            val intersectionY = originY - ((interpolatedPricePoint.price - minPrice) / priceRange * graphHeight).toFloat()

                            // Draw intersection point
                            drawCircle(
                                color = colorScheme.primary,
                                radius = 6.dp.toPx(),
                                center = Offset(hoverX, intersectionY)
                            )
                            drawCircle(
                                color = colorScheme.surface,
                                radius = 3.dp.toPx(),
                                center = Offset(hoverX, intersectionY)
                            )
                        }
                    }
                }
            }

            // Tooltip
            if (showHover && showTooltip && hoveredPricePoint != null) {
                val dateFormat = when (mode) {
                    0 -> SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
                    1 -> SimpleDateFormat("dd.MM.yyyy EEEE", Locale.getDefault())
                    2 -> SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    else -> SimpleDateFormat("MMM yyyy", Locale.getDefault())
                }
                
                val tooltipText = "${dateFormat.format(Date(hoveredPricePoint!!.timestamp))}\n${"%.2f".format(hoveredPricePoint!!.price)} ct/kWh"
                
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopStart
                ) {
                    Card(
                        modifier = Modifier
                            .padding(start = 8.dp, top = 8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = colorScheme.surfaceVariant.copy(alpha = 0.95f)
                        ),
                        elevation = CardDefaults.cardElevation(4.dp)
                    ) {
                        Text(
                            text = tooltipText,
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                }
            }
        }
    }
} 