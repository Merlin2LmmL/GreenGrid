package com.example.greengrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.greengrid.data.Achievement
import com.example.greengrid.data.AchievementManager
import com.example.greengrid.data.AchievementType
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.ui.graphics.StrokeCap

@Composable
fun AchievementsScreen() {
    val context = LocalContext.current
    val achievementManager = remember { AchievementManager(context) }
    val achievements by achievementManager.achievements.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Achievements",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(achievements) { achievement ->
                AchievementItem(achievement)
            }
        }
    }
}

@Composable
fun AchievementItem(achievement: Achievement) {
    AchievementCard(achievement = achievement)
}

@Composable
fun AchievementCard(achievement: Achievement) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = if (achievement.isUnlocked)
                MaterialTheme.colorScheme.surface
            else
                MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = if (achievement.isUnlocked)
                            Icons.Default.Check
                        else
                            Icons.Default.Star,
                        contentDescription = null,
                        tint = if (achievement.isUnlocked)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = achievement.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (achievement.isUnlocked)
                            MaterialTheme.colorScheme.onSurface
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (!achievement.isUnlocked) {
                    Text(
                        text = "${(achievement.progress * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = achievement.description,
                style = MaterialTheme.typography.bodyMedium,
                color = if (achievement.isUnlocked)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            // Eigene ProgressBar: dünner und mit grauer Hintergrundleiste
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
            ) {
                // Hintergrund: immer 100% gefüllt, grau
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceTint,
                            shape = RoundedCornerShape(2.dp)
                        )
                )
                // Fortschritt: farbig, Breite nach progress
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = achievement.progress.coerceIn(0f, 1f))
                        .background(
                            color = MaterialTheme.colorScheme.primary,
                            shape = RoundedCornerShape(2.dp)
                        )
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (achievement.type) {
                    AchievementType.ECO_BEGINNER -> "${achievement.currentValue.toInt()}/${achievement.targetValue.toInt()} g CO₂"
                    AchievementType.MARKET_MASTER -> "${achievement.currentValue.toInt()}/${achievement.targetValue.toInt()} Trades"
                    AchievementType.GLAETTUNGSMEISTER -> "${achievement.currentValue.toInt()}/${achievement.targetValue.toInt()} Tage"
                    AchievementType.PROFIT_100 -> "%.2f € / %d € Gewinn".format(achievement.currentValue, achievement.targetValue.toInt())
                    AchievementType.TOP10_CO2 -> "Platz ${achievement.currentValue.toInt()}. Noch ${achievement.currentValue.toInt() - achievement.targetValue.toInt()} Plätze"
                    else -> if (achievement.isUnlocked) "Abgeschlossen!" else "Noch nicht erreicht"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
} 