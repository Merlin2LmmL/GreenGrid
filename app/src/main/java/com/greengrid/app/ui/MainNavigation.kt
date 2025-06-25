package com.greengrid.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalLayoutDirection
import com.greengrid.app.data.User

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
    val layoutDirection = LocalLayoutDirection.current

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Top
    ) {
        // Fixed TopBar
        TopAppBar(
            title = { 
                Text(
                    text = selectedScreen.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            },
            actions = {
                IconButton(onClick = { selectedScreen = AppScreen.SETTINGS }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = "Einstellungen",
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.background,
                titleContentColor = MaterialTheme.colorScheme.primary,
                actionIconContentColor = MaterialTheme.colorScheme.primary
            )
        )

        // Main content with bottom navigation
        Scaffold(
            modifier = Modifier.weight(1f),
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                NavigationBar(
                    modifier = Modifier.height(95.dp),
                    tonalElevation = 0.dp,
                    containerColor = MaterialTheme.colorScheme.background
                ) {
                    AppScreen.values().filter { it.showInNav }.forEach { screen ->
                        val selected = selectedScreen == screen
                        
                        NavigationBarItem(
                            icon = { 
                                Icon(
                                    screen.icon, 
                                    contentDescription = screen.title,
                                    modifier = Modifier
                                        .size(26.dp)
                                        .padding(bottom = 2.dp)
                                ) 
                            },
                            label = { 
                                Text(
                                    text = screen.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 2.dp)
                                )
                            },
                            selected = selected,
                            onClick = { selectedScreen = screen },
                            alwaysShowLabel = true,
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = paddingValues.calculateStartPadding(layoutDirection),
                            end = paddingValues.calculateEndPadding(layoutDirection),
                            bottom = paddingValues.calculateBottomPadding())
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
}