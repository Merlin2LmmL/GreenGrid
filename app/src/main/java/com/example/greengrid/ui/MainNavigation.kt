package com.example.greengrid.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.example.greengrid.data.User

enum class AppScreen(val title: String, val icon: ImageVector) {
    STROMBOERSE("Strombörse", Icons.Default.ShoppingCart),
    FORECAST_AND_PRICE_ALERT("Prognosen", Icons.Default.Info),
    ACHIEVEMENTS("Erfolge", Icons.Default.Star),
    SETTINGS("Einstellungen", Icons.Default.Settings)
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary
                )
            )
        },
        bottomBar = {
            NavigationBar {
                AppScreen.values().forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = screen.title) },
                        label = { Text(screen.title) },
                        selected = selectedScreen == screen,
                        onClick = { selectedScreen = screen }
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
                AppScreen.FORECAST_AND_PRICE_ALERT -> ForecastAndPriceAlertScreen()
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