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

    // Developer mode flag (später ggf. aus BuildConfig oder AppPreferences)
    val isDeveloperMode = true
    var showResetDialog by remember { mutableStateOf(false) }
    var resetSuccess by remember { mutableStateOf(false) }

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

        // Experimental Reset Button (nur im Developer Mode)
        if (isDeveloperMode) {
            Button(
                onClick = { showResetDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Text("Alle Achievements zurücksetzen (Experimentell)", color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }

        if (showResetDialog) {
            AlertDialog(
                onDismissRequest = { showResetDialog = false },
                title = { Text("Achievements zurücksetzen?") },
                text = { Text("Bist du sicher, dass du alle Achievements zurücksetzen möchtest? Dieser Vorgang kann nicht rückgängig gemacht werden.") },
                confirmButton = {
                    TextButton(onClick = {
                        achievementManager.resetAchievements()
                        showResetDialog = false
                        resetSuccess = true
                    }) {
                        Text("Zurücksetzen", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showResetDialog = false }) {
                        Text("Abbrechen")
                    }
                }
            )
        }
        if (resetSuccess) {
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(1200)
                resetSuccess = false
            }
            Text(
                "Alle Achievements wurden zurückgesetzt!",
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

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
                        imageVector = if (achievement.isUnlocked && achievement.stage >= achievement.totalStages)
                            Icons.Default.Check
                        else
                            Icons.Default.Star,
                        contentDescription = null,
                        tint = if (achievement.isUnlocked && achievement.stage >= achievement.totalStages)
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Column {
                        Text(
                            text = achievement.title,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (achievement.isUnlocked && achievement.stage >= achievement.totalStages)
                                MaterialTheme.colorScheme.onSurface
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (achievement.totalStages > 1) {
                            Text(
                                text = "Stufe ${achievement.stage}/${achievement.totalStages}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                if (achievement.isUnlocked && achievement.stage >= achievement.totalStages) {
                    Text(
                        text = "Abgeschlossen",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else {
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
                color = if (achievement.isUnlocked && achievement.stage >= achievement.totalStages)
                    MaterialTheme.colorScheme.onSurface
                else
                    MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))
            
            // Progress bar - use progress for current stage only
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
            ) {
                // Background: always 100% filled, gray
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceTint.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
                // Progress: colored, width based on current stage progress
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = if (achievement.isUnlocked && achievement.stage >= achievement.totalStages) 1f else achievement.progress.coerceIn(0f, 1f))
                        .background(
                            color = if (achievement.isUnlocked && achievement.stage >= achievement.totalStages) 
                                MaterialTheme.colorScheme.primary 
                            else 
                                MaterialTheme.colorScheme.primary.copy(alpha = 0.7f),
                            shape = RoundedCornerShape(4.dp)
                        )
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = when (achievement.type) {
                    AchievementType.ECO_BEGINNER -> {
                        when (achievement.stage) {
                            1 -> "${achievement.currentValue.toInt()}/100 g CO₂"
                            2 -> "${achievement.currentValue.toInt()}/1.000 g CO₂"
                            3 -> "${achievement.currentValue.toInt()}/10.000 g CO₂"
                            else -> "${achievement.currentValue.toInt()}/${achievement.targetValue.toInt()} g CO₂"
                        }
                    }
                    AchievementType.MARKET_MASTER -> {
                        when (achievement.stage) {
                            1 -> "${achievement.currentValue.toInt()}/1 Trade"
                            2 -> "${achievement.currentValue.toInt()}/100 Trades"
                            3 -> "${achievement.currentValue.toInt()}/500 Trades"
                            else -> "${achievement.currentValue.toInt()}/${achievement.targetValue.toInt()} Trades"
                        }
                    }
                    AchievementType.GLAETTUNGSMEISTER -> "${achievement.currentValue.toInt()}/${achievement.targetValue.toInt()} Stunden"
                    AchievementType.PROFIT_100 -> "%.2f € / %d € Trading-Gewinn".format(achievement.currentValue, achievement.targetValue.toInt())
                    AchievementType.TOP10_CO2 -> {
                        if (achievement.isUnlocked) {
                            "Platz ${achievement.currentValue.toInt()} - Top 10 erreicht!"
                        } else {
                            val currentRank = achievement.currentValue.toInt()
                            if (currentRank == 999) {
                                "Noch nicht in den Top 10"
                            } else {
                                "Platz ${currentRank} - Noch ${currentRank - 10} Plätze bis Top 10"
                            }
                        }
                    }
                    else -> if (achievement.isUnlocked) "Abgeschlossen!" else "Noch nicht erreicht"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
} 