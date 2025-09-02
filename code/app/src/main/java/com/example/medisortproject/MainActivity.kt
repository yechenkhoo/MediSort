package com.example.medisortproject

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import com.example.medisortproject.ui.theme.MediSortTheme
import com.example.medisortproject.utils.AdherenceViewModel
import com.google.firebase.Firebase
import com.google.firebase.app
import com.google.firebase.messaging.FirebaseMessaging

class MainActivity : ComponentActivity() {
    private lateinit var adherenceViewModel: AdherenceViewModel

    // Broadcast receiver for medication adherence tracking
    private val adherenceReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == "MEDICATION_TAKEN_ACTION") {
                val reminderId = intent.getStringExtra("reminder_id") ?: return
                val reminderTime = intent.getLongExtra("reminder_time", 0)
                val status = intent.getStringExtra("status") ?: "taken"
                val medicationId = intent.getStringExtra("medication_id") ?: ""
                val medicationName = intent.getStringExtra("medication_name") ?: ""

                // Log the adherence action
                Log.d("AdherenceReceiver", "Recording adherence: $status for $medicationName")

                // Record the adherence in the ViewModel
                adherenceViewModel.recordAdherence(
                    reminderId = reminderId,
                    medicationId = medicationId,
                    medicationName = medicationName,
                    status = status,
                    scheduledTime = reminderTime
                )
            }
        }
    }

    @Composable
    fun RequestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) { // Android 13+
            val context = LocalContext.current
            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestPermission(),
                onResult = { isGranted ->
                    if (!isGranted) {
                        println("Notification permission denied!")
                    }
                }
            )

            LaunchedEffect(Unit) {
                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }


    private val requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
        if (isGranted) {
            println("Notification permission granted!")
        } else {
            println("Notification permission denied!")
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize the AdherenceViewModel
        adherenceViewModel = ViewModelProvider(this)[AdherenceViewModel::class.java]

        // Register the adherence receiver
        registerReceiver(
            adherenceReceiver,
            IntentFilter("MEDICATION_TAKEN_ACTION"),
            RECEIVER_EXPORTED
        )

        // Request notification permission if Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            requestNotificationPermission()
        }

        // Firebase initialization
        try {
            Firebase.app.name
            Log.d("Firebase", "Firebase initialized successfully")
        } catch (e: Exception) {
            Log.e("Firebase", "Firebase initialization failed", e)
        }

        // Get FCM token
        FirebaseMessaging.getInstance().token.addOnCompleteListener { task ->
            if (task.isSuccessful) {
                val token = task.result
                Log.d("FCM", "Token: $token")
            } else {
                Log.w("FCM", "Token generation failed", task.exception)
            }
        }

        setContent {
            MediSortTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    NavGraph(adherenceViewModel = adherenceViewModel)
                }
            }
            RequestNotificationPermission()
        }
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun requestNotificationPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // Unregister the receiver
        unregisterReceiver(adherenceReceiver)
    }
}


@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    MediSortTheme {
        Greeting("Android")
    }
}
