package com.example.medisortproject.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.BottomAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.example.medisortproject.NavRoute
import com.google.firebase.auth.FirebaseAuth

// Data class for bottom navigation items
data class BottomNavItem(
    val title: String,
    val icon: ImageVector,
    val route: String
)

@Composable
fun MediSortBottomNavigation(navController: NavController) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val items = listOf(
        BottomNavItem(
            "Home",
            Icons.Default.Home,
            NavRoute.Home.route
        ),
        BottomNavItem(
            "Records",
            Icons.Default.MedicalServices,
            "medication_records"
        ),
        BottomNavItem(
            "Identify",
            Icons.Default.QrCodeScanner,
            NavRoute.OCRInstruction.route
        ),
        BottomNavItem(
            "Reminders",
            Icons.Default.Notifications,
            "reminders"
        ),
        BottomNavItem(
            "Adherence", // New menu item for adherence tracking
            Icons.Default.Assessment,
            "adherence"
        )
    )

    BottomAppBar {
        items.forEach { item ->
            NavigationBarItem(
                icon = { Icon(item.icon, contentDescription = item.title) },
                label = { Text(item.title) },
                selected = currentRoute == item.route,
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
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediSortTopBar(title: String = "MediSort", showLogout: Boolean = true, navController: NavController) {
    TopAppBar(
        title = { Text(title) },
        actions = {
            if (showLogout) {
                IconButton(
                    onClick = {
                        FirebaseAuth.getInstance().signOut()
                        navController.navigate("login") {
                            popUpTo("home") { inclusive = true }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                        contentDescription = "Logout"
                    )
                }
            }
        }
    )
}

@Composable
fun MediSortScaffold(
    navController: NavController,
    title: String = "MediSort",
    showLogout: Boolean = true,
    showBottomNav: Boolean = true,
    content: @Composable () -> Unit
) {
    Scaffold(
        topBar = {
            MediSortTopBar(title, showLogout, navController)
        },
        bottomBar = {
            if (showBottomNav) {
                MediSortBottomNavigation(navController)
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            content()
        }
    }
}
