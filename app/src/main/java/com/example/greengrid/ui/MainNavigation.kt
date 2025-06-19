package com.example.greengrid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.greengrid.data.User

enum class AppScreen(val title: String, val icon: ImageVector, val showInNav: Boolean = true) {
    STROMBOERSE("Börse", Icons.Default.ShoppingCart),
    FORECAST("Prognose", Icons.Default.Info),
    LEARNING("Lernen", Icons.Default.Build),
    ACHIEVEMENTS("Erfolge", Icons.Default.Star),
    SETTINGS("Einstellungen", Icons.Default.Settings, showInNav = false)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainNavigation(
    user: User,
    onLogout: () -> Unit,
    onNameChange: (String) -> Unit
) {
    var selectedScreen by remember { mutableStateOf(AppScreen.STROMBOERSE) }
    var currentName by remember { mutableStateOf(user.username) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(selectedScreen.title) },
                actions = {
                    IconButton(onClick = { selectedScreen = AppScreen.SETTINGS }) {
                        Icon(Icons.Default.Settings, contentDescription = "Einstellungen")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.height(55.dp)
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier.height(90.dp)
            ) {
                AppScreen.values().filter { it.showInNav }.forEach { screen ->
                    NavigationBarItem(
                        icon = { 
                            Icon(
                                screen.icon, 
                                contentDescription = screen.title,
                                modifier = Modifier.size(24.dp)
                            ) 
                        },
                        label = { 
                            Text(
                                text = screen.title,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1
                            )
                        },
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen },
                        alwaysShowLabel = true
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            when (selectedScreen) {
                AppScreen.STROMBOERSE -> StromboerseScreen(
                    user = user
                )
                AppScreen.FORECAST -> ForecastScreen()
                AppScreen.LEARNING -> LearningScreen()
                AppScreen.ACHIEVEMENTS -> AchievementsScreen()
                AppScreen.SETTINGS -> SettingsScreen(
                    onLogout = onLogout,
                    currentName = currentName,
                    onNameChange = { newName ->
                        currentName = newName
                        onNameChange(newName)
                    }
                )
            }
        }
    }
}