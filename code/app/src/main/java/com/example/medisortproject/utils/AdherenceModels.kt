package com.example.medisortproject.utils

import java.util.UUID

// Data model for tracking medication adherence
data class MedicationAdherence(
    val id: String = UUID.randomUUID().toString(),
    val reminderId: String = "",
    val medicationId: String = "",
    val medicationName: String = "",
    val userId: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "taken", // taken, missed, late
    val takenAt: Long = System.currentTimeMillis(),
    val scheduledFor: Long = 0
)

// Model for tracking user streaks
data class UserStreak(
    val userId: String = "",
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val lastAdherenceDate: Long = 0,
    val streakStartDate: Long = System.currentTimeMillis()
)
