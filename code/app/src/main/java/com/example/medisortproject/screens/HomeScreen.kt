package com.example.medisortproject.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.medisortproject.NavRoute
import com.example.medisortproject.components.MediSortScaffold

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(navController: NavController) {
    MediSortScaffold(
        navController = navController,
        title = "MediSort Home",
        showLogout = true,
        showBottomNav = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Welcome text
            Text(
                text = "Welcome to MediSort",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(vertical = 16.dp)
            )

            Text(
                text = "Your Medication Management Assistant",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Card buttons grid
            val navigationItems = listOf(
                HomeCardItem(
                    title = "Medication Records",
                    icon = Icons.Default.MedicalServices,
                    route = "medication_records",
                    description = "View and manage your medications"
                ),
                HomeCardItem(
                    title = "Identify Medication",
                    icon = Icons.Default.QrCodeScanner,
                    route = NavRoute.OCRInstruction.route,
                    description = "Scan and identify your medications"
                ),
                HomeCardItem(
                    title = "Reminders",
                    icon = Icons.Default.Notifications,
                    route = "reminders",
                    description = "Set medication reminders"
                ),
                HomeCardItem(
                    title = "Adherence Tracking",
                    icon = Icons.Default.Assessment,
                    route = "adherence",
                    description = "Track your medication adherence"
                )
            )

            // Create a grid with 2 columns
            val rows = navigationItems.chunked(2)
            rows.forEach { rowItems ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    rowItems.forEach { item ->
                        NavigationCard(
                            item = item,
                            navController = navController,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // If there's only one item in the row, add an empty weight
                    // to maintain the layout
                    if (rowItems.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

// Data class for home screen card items
data class HomeCardItem(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavigationCard(
    item: HomeCardItem,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = {
            navController.navigate(item.route) {
                // Pop up to the start destination of the graph to
                // avoid building up a large stack of destinations
                popUpTo(NavRoute.Home.route) {
                    saveState = true
                }
                // Avoid multiple copies of the same destination
                launchSingleTop = true
                // Restore state when reselecting a previously selected item
                restoreState = true
            }
        },
        modifier = modifier
            .height(160.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.title,
                modifier = Modifier.size(48.dp),
                tint = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.description,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// Keeping the data class for bottom navigation items
data class BottomNavItem(
    val title: String,
    val icon: ImageVector,
    val route: String
)