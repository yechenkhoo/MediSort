package com.example.medisortproject.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext

fun createNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val name = "Reminder Channel"
        val descriptionText = "Channel for medication reminders"
        val importance = NotificationManager.IMPORTANCE_HIGH
        val channel = NotificationChannel("reminder_channel", name, importance).apply {
            description = descriptionText
            setShowBadge(true)
            lockscreenVisibility = NotificationManager.IMPORTANCE_HIGH
        }
        val notificationManager: NotificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }
}


@Composable
fun RequestOverlayPermissionUI() {
    val context = LocalContext.current
    var showDialog by remember { mutableStateOf(!Settings.canDrawOverlays(context)) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text("Enable Overlay Permission") },
            text = { Text("To show full-screen medication reminders, please allow 'Display over other apps' in settings.") },
            confirmButton = {
                Button(onClick = {
                    openOverlaySettings(context)
                    showDialog = false
                }) {
                    Text("Open Settings")
                }
            },
            dismissButton = {
                Button(onClick = { showDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

fun openOverlaySettings(context: Context) {
    val intent = Intent(
        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    context.startActivity(intent)
}
