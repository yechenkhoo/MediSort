package com.example.medisortproject.screens

import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.example.medisortproject.components.MediSortScaffold
import com.example.medisortproject.utils.AdherenceViewModel
import com.example.medisortproject.utils.MedicationAdherence
import java.text.SimpleDateFormat
import java.util.*

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun AdherenceScreen(
    navController: NavController,
    adherenceViewModel: AdherenceViewModel,
    reminderViewModel: ReminderViewModel // Add the reminder view model
) {
    val adherenceRecords by adherenceViewModel.adherenceRecords.collectAsState()
    val userStreak by adherenceViewModel.userStreak.collectAsState()
    val isLoading by adherenceViewModel.isLoading.collectAsState()

    // Force refresh data when screen is shown
    LaunchedEffect(Unit) {
        // Ensure fresh data is loaded when the screen is displayed
        adherenceViewModel.refreshData()
        reminderViewModel.refreshReminders()
    }

    MediSortScaffold(
        navController = navController,
        title = "Medication Adherence",
        showLogout = true,
        showBottomNav = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Streak display
            StreakCounter(
                currentStreak = userStreak?.currentStreak ?: 0,
                longestStreak = userStreak?.longestStreak ?: 0
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Calendar heatmap visualization
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally)
                )
            } else {
                // Use the enhanced StreakCalendarView with reminder awareness
                StreakCalendarView(
                    adherenceRecords = adherenceRecords,
                    reminderViewModel = reminderViewModel
                )
            }
        }
    }
}

@Composable
fun StreakCounter(
    currentStreak: Int,
    longestStreak: Int
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(16.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Text(
                text = currentStreak.toString(),
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "DAY STREAK",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Longest streak: $longestStreak days",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@RequiresApi(Build.VERSION_CODES.O)
@Composable
fun StreakCalendarView(
    adherenceRecords: List<MedicationAdherence>,
    reminderViewModel: ReminderViewModel
) {
    val calendar = Calendar.getInstance()
    val currentMonth = calendar.get(Calendar.MONTH)
    val currentYear = calendar.get(Calendar.YEAR)
    val daysInMonth = calendar.getActualMaximum(Calendar.DAY_OF_MONTH)
    val today = Calendar.getInstance().get(Calendar.DAY_OF_MONTH)

    // Get current time for comparison
    val currentTime = System.currentTimeMillis()

    // Get active reminders
    val activeReminders by reminderViewModel.reminders.collectAsState()

    // Get first day of month starting position (0 = Sunday, 1 = Monday, etc.)
    calendar.set(Calendar.DAY_OF_MONTH, 1)
    val firstDayOfMonth = calendar.get(Calendar.DAY_OF_WEEK) - 1 // Adjust to 0-based

    // Format month and year
    val monthYearFormatter = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
    val monthYearText = monthYearFormatter.format(calendar.time)

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Adherence Calendar",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = monthYearText,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        // Day of week headers
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val dayNames = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")
            dayNames.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Calendar grid
        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier
                .fillMaxWidth()
                .height((((firstDayOfMonth + daysInMonth - 1) / 7 + 1) * 45).dp)
        ) {
            // Empty cells for days before the 1st of the month
            items(firstDayOfMonth) {
                Box(modifier = Modifier.size(45.dp))
            }

            // Calendar days
            items(daysInMonth) { dayIndex ->
                val dayOfMonth = dayIndex + 1

                // Set calendar to the current grid day
                calendar.set(currentYear, currentMonth, dayOfMonth)
                val gridDate = calendar.timeInMillis

                // Find day of week for this date
                val dayOfWeek = calendar.get(Calendar.DAY_OF_WEEK)
                val dayName = when(dayOfWeek) {
                    Calendar.SUNDAY -> "Sunday"
                    Calendar.MONDAY -> "Monday"
                    Calendar.TUESDAY -> "Tuesday"
                    Calendar.WEDNESDAY -> "Wednesday"
                    Calendar.THURSDAY -> "Thursday"
                    Calendar.FRIDAY -> "Friday"
                    Calendar.SATURDAY -> "Saturday"
                    else -> ""
                }

                // Check if this day has any scheduled reminders
                val dayStart = Calendar.getInstance().apply {
                    set(currentYear, currentMonth, dayOfMonth, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val dayEnd = Calendar.getInstance().apply {
                    set(currentYear, currentMonth, dayOfMonth, 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis

                // Get adherence records for this day
                val adherenceForDay = adherenceRecords.filter {
                    it.takenAt in dayStart..dayEnd
                }

                // Count actual reminders for this day
                val remindersForDay = if (dayOfMonth == today) {
                    // Only check active reminders for today
                    activeReminders.filter { reminder ->
                        reminder.takingMedication && reminder.selectedDays.contains(dayName)
                    }.flatMap { reminder ->
                        if (reminder.selectedMedications.isEmpty()) listOf(reminder) else reminder.selectedMedications
                    }.size
                } else 0

                // Find adherence records with explicit IDs to avoid duplicates
                val uniqueReminderIds = adherenceForDay.map { it.reminderId }.distinct()
                val uniqueMedicationIds = adherenceForDay.map { it.medicationId }.distinct()

                // Total reminders is the maximum of the explicitly tracked ones and the scheduled ones
                val recordedReminders = if (uniqueMedicationIds.isEmpty() || uniqueMedicationIds.all { it.isEmpty() }) {
                    uniqueReminderIds.size
                } else {
                    uniqueMedicationIds.size
                }

                // For today, we consider both recorded adherence and scheduled reminders
                val totalReminders = if (dayOfMonth == today) {
                    maxOf(recordedReminders, remindersForDay)
                } else {
                    recordedReminders
                }

                val takenReminders = adherenceForDay.count { it.status == "taken" }

                // Past dates should show their adherence status
                // Today shows current progress including upcoming reminders
                // Future dates are transparent
                val isPastDay = gridDate < dayStart || (dayOfMonth < today && currentMonth == calendar.get(Calendar.MONTH))
                val isFutureDay = dayOfMonth > today && currentMonth == calendar.get(Calendar.MONTH)

                // For today's date, check if any reminders have actually passed their time
                val activeRemindersForToday = if (dayOfMonth == today) {
                    // Only count reminders whose scheduled times have already passed
                    activeReminders.filter { reminder ->
                        if (!reminder.takingMedication || !reminder.selectedDays.contains(dayName)) return@filter false

                        // Parse the scheduled time
                        val timeParts = reminder.time.split(":")
                        if (timeParts.size != 2) return@filter false

                        val hour = timeParts[0].toIntOrNull() ?: return@filter false
                        val minute = timeParts[1].toIntOrNull() ?: return@filter false

                        // Create calendar for reminder time today
                        val reminderTime = Calendar.getInstance().apply {
                            set(Calendar.HOUR_OF_DAY, hour)
                            set(Calendar.MINUTE, minute)
                            set(Calendar.SECOND, 0)
                        }.timeInMillis

                        // Only count if time has passed
                        reminderTime < System.currentTimeMillis()
                    }.size
                } else 0

                // Simpler backgroundColor logic - transparent until reminders have occurred
                val backgroundColor = when {
                    totalReminders == 0 || (dayOfMonth == today && activeRemindersForToday == 0) ->
                        Color.Transparent // No reminders or reminders haven't occurred yet
                    takenReminders == 0 && totalReminders > 0 ->
                        Color(0xFFEF5350) // Red for missed
                    takenReminders < totalReminders ->
                        Color(0xFFFFA726) // Orange for partial
                    takenReminders == totalReminders ->
                        Color(0xFF66BB6A) // Green for all taken
                    else ->
                        Color.Transparent
                }

                Box(
                    modifier = Modifier
                        .padding(4.dp)
                        .size(36.dp)
                        .background(
                            color = backgroundColor,
                            shape = RoundedCornerShape(4.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = dayOfMonth.toString(),
                        color = if (backgroundColor == Color.Transparent)
                            MaterialTheme.colorScheme.onSurface
                        else
                            Color.White,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Color legend
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            ColorLegendItem(color = Color(0xFF66BB6A), label = "All Taken")
            ColorLegendItem(color = Color(0xFFFFA726), label = "Partially Taken")
            ColorLegendItem(color = Color(0xFFEF5350), label = "Missed")
        }
    }
}

@Composable
fun ColorLegendItem(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(16.dp)
                .background(color, RoundedCornerShape(2.dp))
        )

        Spacer(modifier = Modifier.width(4.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
