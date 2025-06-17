package com.example.greengrid.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ForecastAndPriceAlertScreen() {
    // Tab selection
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Prognosen", "Preisalarm")

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
                // Prognosen Tab
                ForecastScreen(
                    onNavigateToPriceAlert = { selectedTab = 1 }
                )
            }
            1 -> {
                // Preisalarm Tab
                PriceAlertScreen()
            }
        }
    }
} 