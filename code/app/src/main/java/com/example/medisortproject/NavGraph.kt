package com.example.medisortproject

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.LocalViewModelStoreOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.navigation.navDeepLink
import com.example.medisortproject.screens.AdherenceScreen
import com.example.medisortproject.screens.HomeScreen
import com.example.medisortproject.screens.LoginScreen
import com.example.medisortproject.screens.MedicationDetailScreen
import com.example.medisortproject.screens.MedicationRecordsScreen
import com.example.medisortproject.screens.MedicationViewModel
import com.example.medisortproject.screens.OCRCaptureScreen
import com.example.medisortproject.screens.OCRInfoScreen
import com.example.medisortproject.screens.OCRInstructionScreen
import com.example.medisortproject.screens.QRScannerScreen
import com.example.medisortproject.screens.RegisterScreen
import com.example.medisortproject.screens.ReminderDetailScreen
import com.example.medisortproject.screens.ReminderViewModel
import com.example.medisortproject.screens.RemindersScreen
import com.example.medisortproject.utils.AdherenceViewModel

sealed class NavRoute(val route: String) {
    object Login : NavRoute("login")
    object Home : NavRoute("home")
    object Register : NavRoute("register")
    object QRScanner : NavRoute("qr_scanner")
    object OCRInstruction : NavRoute("ocr_instruction")
    object OCRCamera : NavRoute("ocr_camera")
    object OCRInfo : NavRoute("ocr_info_screen/{name}/{medicine}/{dosage}")
    object MedicationRecords : NavRoute("medication_records")
    object MedicationDetail : NavRoute("medication_detail/{medicationId}")
    object Reminders : NavRoute("reminders")
    object ReminderDetail : NavRoute("reminder_detail/{reminderId}")
    object Adherence : NavRoute("adherence") // New route for adherence tracking
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun NavGraph(
    navController: NavHostController = rememberNavController(),
    adherenceViewModel: AdherenceViewModel = viewModel() // Accept AdherenceViewModel parameter
) {
    val medicationViewModel: MedicationViewModel = viewModel(viewModelStoreOwner = LocalViewModelStoreOwner.current!!)
    val reminderViewModel: ReminderViewModel = viewModel(viewModelStoreOwner = LocalViewModelStoreOwner.current!!)

    NavHost(
        navController = navController,
        startDestination = NavRoute.Login.route
    ) {
        composable(NavRoute.Login.route) {
            LoginScreen(navController, medicationViewModel, reminderViewModel)
        }
        composable(NavRoute.Home.route) {
            HomeScreen(navController)
        }
        composable(NavRoute.Register.route) {
            RegisterScreen(navController)
        }
        composable(NavRoute.OCRInstruction.route) {
            OCRInstructionScreen(navController)
        }

        composable(NavRoute.OCRCamera.route) {
            OCRCaptureScreen(navController)
        }
        composable(
            route = NavRoute.OCRInfo.route,
            arguments = listOf(
                navArgument("name") { type = NavType.StringType },
                navArgument("medicine") { type = NavType.StringType },
                navArgument("dosage") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val name = backStackEntry.arguments?.getString("name") ?: "Unknown"
            val medicine = backStackEntry.arguments?.getString("medicine") ?: "Unknown"
            val dosage = backStackEntry.arguments?.getString("dosage") ?: "Unknown"
            OCRInfoScreen(navController, name, medicine, dosage, medicationViewModel)
        }
        composable(NavRoute.QRScanner.route) {
            QRScannerScreen(navController)
        }
        composable(NavRoute.MedicationRecords.route) {
            MedicationRecordsScreen(navController, medicationViewModel)
        }
        composable(
            route = NavRoute.MedicationDetail.route,
            arguments = listOf(navArgument("medicationId") { type = NavType.StringType }),
            deepLinks = listOf(navDeepLink { uriPattern = "medisort://medication_detail/{medicationId}" }) // Add deep link support
        ) { backStackEntry ->
            val medicationId = backStackEntry.arguments?.getString("medicationId") ?: return@composable
            MedicationDetailScreen(navController, medicationViewModel, medicationId)
        }
        composable(NavRoute.Reminders.route) {
            RemindersScreen(navController, reminderViewModel, medicationViewModel)
        }
        composable(
            route = NavRoute.ReminderDetail.route,
            arguments = listOf(navArgument("reminderId") { type = NavType.StringType })
        ) { backStackEntry ->
            val reminderId = backStackEntry.arguments?.getString("reminderId") ?: return@composable
            ReminderDetailScreen(navController, reminderViewModel, reminderId)
        }
        // Add new route for adherence tracking
        composable(NavRoute.Adherence.route) {
            AdherenceScreen(
                navController = navController,
                adherenceViewModel = adherenceViewModel,
                reminderViewModel = reminderViewModel
            )
        }
    }
}