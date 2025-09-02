package com.example.medisortproject.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight.Companion.Bold
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.medisortproject.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReminderDetailScreen(
    navController: NavController,
    reminderViewModel: ReminderViewModel,
    reminderId: String
) {
    val context = LocalContext.current
    val reminders by reminderViewModel.reminders.collectAsState()
    val reminder = reminders.find { it.id == reminderId }

    val medications = reminder?.selectedMedications

    if (reminder == null) {
        navController.navigateUp()
        return
    }

    Scaffold(
        topBar = {
            SmallTopAppBar(
                title = { Text(reminder.name) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = {
                        reminderViewModel.deleteReminder(context, reminderId)
                        navController.navigateUp()
                    }) {
                        Icon(Icons.Default.Delete, "Delete Reminder")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            Text(
                text = "Name",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = reminder.name,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Time",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = reminder.time,
                style = MaterialTheme.typography.bodyLarge
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Days",
                style = MaterialTheme.typography.titleMedium
            )

            reminder.selectedDays.forEach { day ->
                Text(
                    text = day,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .padding(vertical = 4.dp)
                        .background(Color.LightGray, shape = MaterialTheme.shapes.small)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Taking Medication",
                style = MaterialTheme.typography.titleMedium
            )

            if (reminder.takingMedication) {
                Text(
                    text = "Still Taking",
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            else {
                Text(
                    text = "No Longer Taking",
                    style = MaterialTheme.typography.bodyLarge
                )
            }


            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Medications",
                style = MaterialTheme.typography.titleMedium
            )

            Spacer(modifier = Modifier.height(4.dp))

            medications?.forEach { medication ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(106.dp)
                        .border(2.dp, Color.Gray, shape = MaterialTheme.shapes.medium)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = medication.name,
                            fontWeight = Bold,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                        )

                        Text(
                            text = "For: " + medication.purpose,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                        )

                        Text(
                            text = medication.instructions,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier
                        )

                        if (medication.notes.isNotEmpty()) {
                            Text(
                                text = "Notes: " + medication.notes,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier
                            )
                        }
                    }

                    if (medication.photoUrl.isNotEmpty()) {
                        Image(
                            painter = rememberAsyncImagePainter(medication.photoUrl),
                            contentDescription = "Medication Image",
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(90.dp)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxHeight()
                                .width(90.dp)
                                .background(Color.Gray),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No Image",
                                color = Color.White,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}