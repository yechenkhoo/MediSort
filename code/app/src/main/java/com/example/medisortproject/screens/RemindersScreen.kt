package com.example.medisortproject.screens

import android.annotation.SuppressLint
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.UUID
import androidx.compose.ui.Alignment
import com.google.firebase.firestore.ListenerRegistration

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.RingtoneManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.os.Build
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.ui.graphics.Color
import android.app.TimePickerDialog
import android.media.AudioAttributes
import androidx.compose.ui.platform.LocalContext
import com.example.medisortproject.utils.NotificationActivity
import com.example.medisortproject.utils.RequestOverlayPermissionUI
import com.example.medisortproject.utils.createNotificationChannel
import java.util.*
import com.example.medisortproject.components.MediSortScaffold


// Data model for a reminder
data class Reminder(
    val id: String = "",
    val name: String = "",
    val time: String = "",
    val selectedDays: List<String> = emptyList(), // List of days
//    val createdAt: Long = System.currentTimeMillis(),
    val userId: String = "",
    val selectedMedications: List<Medication> = emptyList(),
    val takingMedication: Boolean = true
)

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val reminderName = intent.getStringExtra("reminder_name") ?: "Medication Reminder"
        val reminderTime = intent.getLongExtra("reminder_time", -1L)
        val reminderId = intent.getStringExtra("reminder_id") ?: reminderName.hashCode().toString()

        // Get medication information
        val medicationId = intent.getStringExtra("medication_id") ?: ""
        val medicationName = intent.getStringExtra("medication_name") ?: ""

        // Get the array of medications if this is a multi-medication reminder
        val medicationIds = intent.getStringArrayExtra("medication_ids") ?: arrayOf()
        val medicationNames = intent.getStringArrayExtra("medication_names") ?: arrayOf()

        val isMultiMedication = medicationIds.isNotEmpty() && medicationIds.size == medicationNames.size

        println("Reminder Name: $reminderName")
        println("Reminder Time: $reminderTime")
        println("Reminder ID: $reminderId")
        println("Is multi-medication: $isMultiMedication (${medicationIds.size} medications)")

        if (reminderTime == -1L) {
            println("Invalid reminder time received!")
            return
        }

        when (intent.action) {
            "SNOOZE_ACTION" -> {
                scheduleSnooze(context, reminderName, reminderTime, reminderId, medicationId, medicationName,
                    isMultiMedication, medicationIds, medicationNames)
            }
            "MARK_AS_TAKEN_ACTION" -> {
                markAsTaken(context, reminderName, reminderTime, reminderId, medicationId, medicationName,
                    isMultiMedication, medicationIds, medicationNames)
            }
            else -> {
                // Ensure the notification channel exists
                createNotificationChannel(context)

                // Check for notification permission
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS)
                    != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                    println("Notification permission not granted!")
                    return
                }

                val notificationSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)

                // Create notification content text based on medications
                val contentText = if (isMultiMedication) {
                    val medList = medicationNames.joinToString(", ")
                    "Time to take: $reminderName ($medList)"
                } else if (medicationName.isNotEmpty()) {
                    "Time to take: $reminderName ($medicationName)"
                } else {
                    "Time to take: $reminderName"
                }

                // Snooze
                val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
                    action = "SNOOZE_ACTION"
                    putExtra("reminder_name", reminderName)
                    putExtra("reminder_time", reminderTime)
                    putExtra("reminder_id", reminderId)
                    if (isMultiMedication) {
                        putExtra("medication_ids", medicationIds)
                        putExtra("medication_names", medicationNames)
                    } else {
                        putExtra("medication_id", medicationId)
                        putExtra("medication_name", medicationName)
                    }
                }
                val snoozePendingIntent = PendingIntent.getBroadcast(
                    context,
                    reminderName.hashCode(),
                    snoozeIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                // Mark as taken
                val markAsTakenIntent = Intent(context, ReminderReceiver::class.java).apply {
                    action = "MARK_AS_TAKEN_ACTION"
                    putExtra("reminder_name", reminderName)
                    putExtra("reminder_time", reminderTime)
                    putExtra("reminder_id", reminderId)
                    if (isMultiMedication) {
                        putExtra("medication_ids", medicationIds)
                        putExtra("medication_names", medicationNames)
                    } else {
                        putExtra("medication_id", medicationId)
                        putExtra("medication_name", medicationName)
                    }
                }
                val markAsTakenPendingIntent = PendingIntent.getBroadcast(
                    context,
                    reminderName.hashCode(),
                    markAsTakenIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )

                val builder = NotificationCompat.Builder(context, "reminder_channel")
                    .setSmallIcon(android.R.drawable.ic_dialog_info)
                    .setContentTitle("Medication Reminder")
                    .setContentText(contentText)
                    .setPriority(NotificationCompat.PRIORITY_HIGH)
                    .setSound(notificationSound)
                    .addAction(android.R.drawable.ic_media_pause, "Snooze", snoozePendingIntent) // Snooze action
                    .addAction(android.R.drawable.ic_menu_save, "Mark as Taken", markAsTakenPendingIntent) // Mark as Taken action

                // Show the notification
                val notificationManager = NotificationManagerCompat.from(context)
                notificationManager.notify(reminderName.hashCode(), builder.build())
            }
        }
    }

    private fun scheduleSnooze(
        context: Context,
        reminderName: String,
        reminderTime: Long,
        reminderId: String,
        medicationId: String,
        medicationName: String,
        isMultiMedication: Boolean,
        medicationIds: Array<String>,
        medicationNames: Array<String>
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val snoozeIntent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_name", reminderName)
            putExtra("reminder_time", reminderTime)
            putExtra("reminder_id", reminderId)
            if (isMultiMedication) {
                putExtra("medication_ids", medicationIds)
                putExtra("medication_names", medicationNames)
            } else {
                putExtra("medication_id", medicationId)
                putExtra("medication_name", medicationName)
            }
        }
        val snoozePendingIntent = PendingIntent.getBroadcast(
            context,
            reminderName.hashCode(),
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Schedule the alarm after 1 minute
        val snoozeTime = System.currentTimeMillis() + 60 * 1000
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            snoozeTime,
            snoozePendingIntent
        )

        // Record a "snoozed" status for each medication
        if (isMultiMedication) {
            // Record adherence for each medication in the reminder
            for (i in medicationIds.indices) {
                val medId = medicationIds[i]
                val medName = medicationNames[i]
                recordAdherence(context, reminderName, reminderTime, reminderId, medId, medName, "snoozed")
            }
        } else {
            // Record adherence for the single medication
            recordAdherence(context, reminderName, reminderTime, reminderId, medicationId, medicationName, "snoozed")
        }
    }

    private fun markAsTaken(
        context: Context,
        reminderName: String,
        reminderTime: Long,
        reminderId: String,
        medicationId: String,
        medicationName: String,
        isMultiMedication: Boolean,
        medicationIds: Array<String>,
        medicationNames: Array<String>
    ) {
        // Cancel the notification
        val notificationManager = NotificationManagerCompat.from(context)
        notificationManager.cancel(reminderName.hashCode())

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val cancelIntent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_name", reminderName)
            putExtra("reminder_time", reminderTime)
            putExtra("reminder_id", reminderId)
            if (isMultiMedication) {
                putExtra("medication_ids", medicationIds)
                putExtra("medication_names", medicationNames)
            } else {
                putExtra("medication_id", medicationId)
                putExtra("medication_name", medicationName)
            }
        }
        val cancelPendingIntent = PendingIntent.getBroadcast(
            context,
            reminderName.hashCode(),
            cancelIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(cancelPendingIntent)

        println("Reminder marked as taken: $reminderName")

        // Record "taken" adherence status for each medication
        if (isMultiMedication) {
            // Record adherence for each medication in the reminder
            for (i in medicationIds.indices) {
                val medId = medicationIds[i]
                val medName = medicationNames[i]
                println("Recording adherence for medication: $medName")
                recordAdherence(context, reminderName, reminderTime, reminderId, medId, medName, "taken")
            }
        } else {
            // Record adherence for the single medication
            recordAdherence(context, reminderName, reminderTime, reminderId, medicationId, medicationName, "taken")
        }

        // Reschedule for next week
        rescheduleForNextWeek(context, reminderName, reminderTime, reminderId, medicationId, medicationName,
            isMultiMedication, medicationIds, medicationNames)
    }

    private fun recordAdherence(
        context: Context,
        reminderName: String,
        reminderTime: Long,
        reminderId: String,
        medicationId: String,
        medicationName: String,
        status: String
    ) {
        val adherenceIntent = Intent("MEDICATION_TAKEN_ACTION").apply {
            putExtra("reminder_name", reminderName)
            putExtra("reminder_time", reminderTime)
            putExtra("reminder_id", reminderId)
            putExtra("medication_id", medicationId)
            putExtra("medication_name", medicationName)
            putExtra("status", status)
        }
        context.sendBroadcast(adherenceIntent)
    }

    private fun rescheduleForNextWeek(
        context: Context,
        reminderName: String,
        reminderTime: Long,
        reminderId: String,
        medicationId: String,
        medicationName: String,
        isMultiMedication: Boolean,
        medicationIds: Array<String>,
        medicationNames: Array<String>
    ) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val nextWeekTime = reminderTime + 7 * 24 * 60 * 60 * 1000 // 7 days from now

        val rescheduleIntent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("reminder_name", reminderName)
            putExtra("reminder_time", nextWeekTime)
            putExtra("reminder_id", reminderId)
            if (isMultiMedication) {
                putExtra("medication_ids", medicationIds)
                putExtra("medication_names", medicationNames)
            } else {
                putExtra("medication_id", medicationId)
                putExtra("medication_name", medicationName)
            }
        }
        val reschedulePendingIntent = PendingIntent.getBroadcast(
            context,
            reminderName.hashCode(),
            rescheduleIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            nextWeekTime,
            reschedulePendingIntent
        )

        val nextWeekDate = Date(nextWeekTime)

        println("Reminder rescheduled for next week: $nextWeekDate")
    }
}

class ReminderViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _reminders = MutableStateFlow<List<Reminder>>(emptyList())
    val reminders: StateFlow<List<Reminder>> = _reminders

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var snapshotListener: ListenerRegistration? = null

    private val authStateListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
        firebaseAuth.currentUser?.let {
            // New user logged in
            resetState()
        } ?: run {
            // User logged out
            snapshotListener?.remove()
            snapshotListener = null
            _reminders.value = emptyList()
        }
    }

    init {
        // Register the auth state listener
        auth.addAuthStateListener(authStateListener)
        loadReminders()
    }

    fun resetState() {
        viewModelScope.launch {
            // Remove existing listener
            snapshotListener?.remove()
            snapshotListener = null

            // Clear the reminders list
            _reminders.value = emptyList()

            // Start new listener for current user
            loadReminders()
        }
    }

    init {
        loadReminders()
    }

    private fun loadReminders() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val userId = auth.currentUser?.uid ?: return@launch

                // Store the listener registration
                snapshotListener = db.collection("reminders")
                    .whereEqualTo("userId", userId)
//                    .orderBy("createdAt")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            println("Error loading reminders: ${error.message}")
                            return@addSnapshotListener
                        }

                        if (snapshot != null) {
                            _reminders.value = snapshot.documents.mapNotNull { doc ->
                                doc.toObject(Reminder::class.java)
                            }
                        }
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                println("Error loading reminders: ${e.message}")
                _isLoading.value = false
            }
        }
    }

    fun refreshReminders() {
        loadReminders()
    }

    override fun onCleared() {
        super.onCleared()
        // Clean up resources
        snapshotListener?.remove()
        // Remove the auth state listener
        auth.removeAuthStateListener(authStateListener)
    }

    fun addReminder(
        name: String,
        time: String,
        selectedDays: List<String>,
        selectedMedications: List<Medication>,
        context: Context
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val userId = auth.currentUser?.uid ?: return@launch

                // Create reminder document
                val reminder = Reminder(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    time = time,
                    selectedDays = selectedDays,
                    selectedMedications = selectedMedications,
                    userId = userId
                )

                // Add to Firestore
                db.collection("reminders")
                    .document(reminder.id)
                    .set(reminder)
                    .await()

                // Schedule the reminder after it is successfully added
                scheduleReminder(context, reminder)

                // Reload reminders
                loadReminders()
            } catch (e: Exception) {
                println("Error adding reminder: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun editReminder(
        id: String,
        name: String,
        time: String,
        selectedDays: List<String>,
        selectedMedications: List<Medication>,
        context: Context
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val userId = auth.currentUser?.uid ?: return@launch

                // Create reminder document
                val reminder = Reminder(
                    id = id,
                    name = name,
                    time = time,
                    selectedDays = selectedDays,
                    selectedMedications = selectedMedications,
                    userId = userId
                )

                // Add to Firestore
                db.collection("reminders")
                    .document(id)
                    .set(reminder)
                    .await()

                // Schedule the reminder after it is successfully added
                scheduleReminder(context, reminder)

                // Reload reminders
                loadReminders()
            } catch (e: Exception) {
                println("Error adding reminder: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteReminder(context: Context, reminderId: String) {
        viewModelScope.launch {
            try {
                val reminder = _reminders.value.find { it.id == reminderId }
                if (reminder != null) {
                    // Cancel the scheduled alarms before deletion
                    cancelReminder(context, reminder)
                }

                // Delete from Firestore
                db.collection("reminders")
                    .document(reminderId)
                    .delete()
                    .await()

                // Reload reminders to reflect changes
                loadReminders()
            } catch (e: Exception) {
                println("Error deleting reminder: ${e.message}")
            }
        }
    }


    fun scheduleReminder(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager

        val dayOfWeekMap = mapOf(
            "Sunday" to Calendar.SUNDAY,
            "Monday" to Calendar.MONDAY,
            "Tuesday" to Calendar.TUESDAY,
            "Wednesday" to Calendar.WEDNESDAY,
            "Thursday" to Calendar.THURSDAY,
            "Friday" to Calendar.FRIDAY,
            "Saturday" to Calendar.SATURDAY
        )

        // Convert the list of string days to a list of integers
        val daysOfWeek = reminder.selectedDays.map { day ->
            dayOfWeekMap[day] ?: throw IllegalArgumentException("Invalid day: $day")
        }

        // Parse the time once
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        val timeParts = sdf.parse(reminder.time) ?: return

        // Get the current time
        val now = Calendar.getInstance()

        // For logging
        val medicationCount = reminder.selectedMedications.size
        println("Scheduling reminder ${reminder.name} with $medicationCount medications")

        // Iterate over the list of days
        for (day in daysOfWeek) {
            val alarmTime = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, timeParts.hours)
                set(Calendar.MINUTE, timeParts.minutes)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.DAY_OF_WEEK, day)

                // If the time has already passed today, schedule it for the next week
                if (before(now)) {
                    add(Calendar.WEEK_OF_YEAR, 1)
                }
            }

            // Create intent for this day's reminder
            val intent = Intent(context, ReminderReceiver::class.java).apply {
                putExtra("reminder_name", reminder.name)
                putExtra("reminder_time", alarmTime.timeInMillis)
                putExtra("reminder_id", reminder.id)

                // Check if we have multiple medications
                if (reminder.selectedMedications.size > 1) {
                    // Create arrays for medication IDs and names
                    val medicationIds = reminder.selectedMedications.map { it.id }.toTypedArray()
                    val medicationNames = reminder.selectedMedications.map { it.name }.toTypedArray()

                    // Add medication arrays to intent
                    putExtra("medication_ids", medicationIds)
                    putExtra("medication_names", medicationNames)

                    println("Adding ${medicationIds.size} medications to reminder")
                    medicationNames.forEach { println("- $it") }
                }
                // If we have exactly one medication, use the single medication format
                else if (reminder.selectedMedications.size == 1) {
                    val medication = reminder.selectedMedications.first()
                    putExtra("medication_id", medication.id)
                    putExtra("medication_name", medication.name)
                    println("Adding single medication to reminder: ${medication.name}")
                }
                // Otherwise, this is a reminder with no medications
                else {
                    println("No medications for this reminder")
                }
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                reminder.id.hashCode() + day.hashCode(),
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            println("Alarm set for: ${alarmTime.time}")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val alarmPermission = alarmManager.canScheduleExactAlarms()
                if (!alarmPermission) {
                    val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                    context.startActivity(intent)
                    return
                }
            }

            alarmManager.setExactAndAllowWhileIdle(
                AlarmManager.RTC_WAKEUP,
                alarmTime.timeInMillis,
                pendingIntent
            )

            println("Alarm scheduled for: ${alarmTime.time}")
        }
    }

    fun cancelReminder(context: Context, reminder: Reminder) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val dayOfWeekMap = mapOf(
            "Sunday" to Calendar.SUNDAY,
            "Monday" to Calendar.MONDAY,
            "Tuesday" to Calendar.TUESDAY,
            "Wednesday" to Calendar.WEDNESDAY,
            "Thursday" to Calendar.THURSDAY,
            "Friday" to Calendar.FRIDAY,
            "Saturday" to Calendar.SATURDAY
        )

        for (day in reminder.selectedDays) {
            val dayCode = dayOfWeekMap[day] ?: continue

            // Cancel this week's reminder
            cancelReminderForDay(context, alarmManager, reminder, dayCode, 0)

            // Cancel next week's reminder
            cancelReminderForDay(context, alarmManager, reminder, dayCode, 7)
        }
    }

    private fun cancelReminderForDay(
        context: Context,
        alarmManager: AlarmManager,
        reminder: Reminder,
        dayCode: Int,
        daysToAdd: Int
    ) {
        val intent = Intent(context, ReminderReceiver::class.java)

        val requestCode = reminder.id.hashCode() + dayCode.hashCode() + daysToAdd.hashCode()

        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        alarmManager.cancel(pendingIntent)
        println("Canceled alarm for ${reminder.name} on day: $dayCode with offset: $daysToAdd days")
    }


    fun pauseReminder(context: Context, reminder: Reminder) {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // Toggle the isActive state
                val updatedReminder = reminder.copy(takingMedication = !reminder.takingMedication)

                // Update the reminder in Firestore
                db.collection("reminders")
                    .document(reminder.id)
                    .set(updatedReminder)
                    .await()

                // If the reminder is being paused, cancel the alarms
                if (!updatedReminder.takingMedication) {
                    cancelReminder(context, reminder)
                } else {
                    // If the reminder is being resumed, schedule the alarms again
                    scheduleReminder(context, updatedReminder)
                }

                // Reload reminders to reflect changes
                loadReminders()
            } catch (e: Exception) {
                println("Error pausing/resuming reminder: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }
}

@Composable
fun RemindersScreen(
    navController: NavController,
    reminderViewModel: ReminderViewModel,
    medicationViewModel: MedicationViewModel
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    val reminders by reminderViewModel.reminders.collectAsState()
    val isLoading by reminderViewModel.isLoading.collectAsState()
    var selectedReminder by remember { mutableStateOf<Reminder?>(null) }
    val context = LocalContext.current

    RequestOverlayPermissionUI()

    // Use MediSortScaffold to include the top bar and bottom navigation
    MediSortScaffold(
        navController = navController,
        title = "Medication Reminders",
        showLogout = true,
        showBottomNav = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Add Reminders",
                    style = MaterialTheme.typography.headlineSmall
                )
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, "Add Medication Reminder")
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                LazyColumn {
                    items(reminders) { reminder ->
                        ReminderCard(
                            reminder = reminder,
                            reminderViewModel = reminderViewModel,
                            context = context,
                            onEdit = {
                                selectedReminder = reminder
                                showEditDialog = true
                            },
                            onDelete = { reminderViewModel.deleteReminder(context, reminder.id) },
                            onClick = {
                                navController.navigate("reminder_detail/${reminder.id}")
                            },
                            onPause = {reminderViewModel.pauseReminder(context, reminder)}
                        )
                    }
                }
            }
        }
    }


    if (showAddDialog) {
        val medications by medicationViewModel.medications.collectAsState()

        AddReminderDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, time, selectedDays, selectedMedications ->
                reminderViewModel.addReminder(name, time, selectedDays, selectedMedications, context)
                showAddDialog = false
            },
            availableMedications = medications,
            context = context
        )
    }

    if (showEditDialog && selectedReminder != null) {
        val medications by medicationViewModel.medications.collectAsState()

        EditReminderDialog(
            reminder = selectedReminder!!,
            onDismiss = {
                showEditDialog = false
            },
            onEdit = { name, time, selectedDays, selectedMedications ->
                reminderViewModel.editReminder(
                    id = selectedReminder!!.id,
                    name = name,
                    time = time,
                    selectedDays = selectedDays,
                    selectedMedications = selectedMedications,
                    context = context
                )
                showEditDialog = false
            },
            availableMedications = medications,
            context = context
        )
    }
}

@Composable
fun ReminderCard(
    reminder: Reminder,
    reminderViewModel: ReminderViewModel,
    context: Context,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    onPause: () -> Unit
) {

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable(onClick = onClick),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column()
            {
                Text(
                    text =  reminder.time ,
                    style = MaterialTheme.typography.displayMedium
                )

                Text(
                    text = reminder.name,
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(start = 5.dp)
                )

                if(reminder.takingMedication) {
                    val reminderDays = reminder.selectedDays
                        .map { day -> day.take(3) }
                        .joinToString(", ")

                    Text(
                        text = reminderDays,
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(start = 5.dp)
                    )
                }
                else {
                    Text(
                        text = "No Longer Taking",
                        style = MaterialTheme.typography.titleSmall.copy(Color.Red),
                        modifier = Modifier.padding(start = 5.dp)
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Switch(
                    checked = reminder.takingMedication,
                    onCheckedChange = { onPause() }
                )

                IconButton(onClick = { onEdit() }) {
                    Icon(Icons.Default.Edit, "Edit Medication Reminder")
                }

                IconButton(onClick = { onDelete() }) {
                    Icon(Icons.Default.Delete, "Delete Reminder")
                }
            }
        }
    }
}

@Composable
fun DaySelector(
    selectedDays: List<String>,
    onDaySelected: (String) -> Unit
) {
    val daysOfWeek = listOf("M", "Tu", "W", "Th", "F", "Sa", "Su")
    val fullDays = listOf("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        daysOfWeek.forEachIndexed { index, day ->
            val isSelected = selectedDays.contains(fullDays[index])

            Column (
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedButton(
                    onClick = { onDaySelected(fullDays[index]) },
                    colors = ButtonDefaults.outlinedButtonColors(
                        containerColor = if (isSelected) Color.Gray else Color.Transparent,
                        contentColor = if (isSelected) Color.White else Color.Black
                    ),
                    border = BorderStroke(2.dp, Color.Gray),
                    modifier = Modifier.size(32.dp)
                ) {
                }
                Text(
                    text = day,
                    color = Color.Black,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1
                )
            }
        }
    }
}



@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddReminderDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, List<String>, List<Medication>) -> Unit,
    availableMedications: List<Medication>,
    context: Context
) {
    var name by remember { mutableStateOf("") }
    var time by remember { mutableStateOf("Time Picker") }
    var selectedDays by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedMedications by remember { mutableStateOf<List<Medication>>(emptyList()) }
    var expanded by remember { mutableStateOf(false) }

    @SuppressLint("DefaultLocale")
    fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                time = String.format("%02d:%02d", selectedHour, selectedMinute)
            },
            hour,
            minute,
            true
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Reminder") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Reminder Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showTimePicker() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = time)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Select Days",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                )

                DaySelector(
                    selectedDays = selectedDays,
                    onDaySelected = { day ->
                        selectedDays = if (selectedDays.contains(day)) {
                            selectedDays - day
                        } else {
                            selectedDays + day
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Select Medications", style = MaterialTheme.typography.bodyLarge)

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    TextField(
                        value = selectedMedications.joinToString { it.name },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Medications") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        availableMedications.forEach { medication ->
                            DropdownMenuItem(
                                text = { Text(medication.name) },
                                onClick = {
                                    if (!selectedMedications.contains(medication)) {
                                        selectedMedications = selectedMedications + medication
                                    }
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && time != "Time Picker" && selectedDays.isNotEmpty()) {
                        onAdd(name, time, selectedDays, selectedMedications)
                    }
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@SuppressLint("DefaultLocale")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditReminderDialog(
    reminder: Reminder,
    onDismiss: () -> Unit,
    onEdit: (String, String, List<String>, List<Medication>) -> Unit,
    availableMedications: List<Medication>,
    context: Context
) {
    var name by remember { mutableStateOf(reminder.name) }
    var time by remember { mutableStateOf(reminder.time) }
    var selectedDays by remember { mutableStateOf(reminder.selectedDays) }
    var selectedMedications by remember { mutableStateOf(reminder.selectedMedications) }
    var expanded by remember { mutableStateOf(false) }

    @SuppressLint("DefaultLocale")
    fun showTimePicker() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)
        val minute = calendar.get(Calendar.MINUTE)

        TimePickerDialog(
            context,
            { _, selectedHour, selectedMinute ->
                time = String.format("%02d:%02d", selectedHour, selectedMinute)
            },
            hour,
            minute,
            true
        ).show()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Reminder") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Reminder Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))

                Button(
                    onClick = { showTimePicker() },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(text = time)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text("Select Days",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier
                        .padding(vertical = 2.dp)
                )

                DaySelector(
                    selectedDays = selectedDays,
                    onDaySelected = { day ->
                        selectedDays = if (selectedDays.contains(day)) {
                            selectedDays - day
                        } else {
                            selectedDays + day
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                Text("Select Medications", style = MaterialTheme.typography.bodyLarge)

                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it }
                ) {
                    TextField(
                        value = selectedMedications.joinToString { it.name },
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Medications") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )

                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        availableMedications.forEach { medication ->
                            DropdownMenuItem(
                                text = { Text(medication.name) },
                                onClick = {
                                    if (!selectedMedications.contains(medication)) {
                                        selectedMedications = selectedMedications + medication
                                    }
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && time != "Time Picker" && selectedDays.isNotEmpty()) {
                        onEdit(name, time, selectedDays, selectedMedications)
                    }
                }
            ) {
                Text("Update")
            }
        },
        dismissButton = {
            Button(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
