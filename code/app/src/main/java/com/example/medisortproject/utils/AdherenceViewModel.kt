package com.example.medisortproject.utils

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.util.Calendar
import java.util.Date

class AdherenceViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _adherenceRecords = MutableStateFlow<List<MedicationAdherence>>(emptyList())
    val adherenceRecords: StateFlow<List<MedicationAdherence>> = _adherenceRecords

    private val _userStreak = MutableStateFlow<UserStreak?>(null)
    val userStreak: StateFlow<UserStreak?> = _userStreak

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    // Track current user to detect user changes
    private var currentUserId: String? = null

    // Track if a reminder has been snoozed for the day (to prevent streak continuation)
    private val reminderSnoozeStatus = mutableMapOf<String, Boolean>()

    // Track reminders for the day - key is the date, value is a map of reminder IDs to their status
    private val dailyReminderStatus = mutableMapOf<Long, MutableMap<String, Boolean>>()

    init {
        // Listen for user auth changes
        auth.addAuthStateListener { firebaseAuth ->
            val userId = firebaseAuth.currentUser?.uid
            if (userId != currentUserId) {
                // User has changed, reset state and load new data
                resetState()
                currentUserId = userId
                if (userId != null) {
                    loadUserData()
                }
            }
        }
    }

    // Reset all state when changing users
    private fun resetState() {
        _adherenceRecords.value = emptyList()
        _userStreak.value = null
        _isLoading.value = false
        reminderSnoozeStatus.clear()
    }

    // Load all user-specific data
    private fun loadUserData() {
        loadAdherenceData()
        loadUserStreak()
    }

    fun loadAdherenceData() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val userId = auth.currentUser?.uid ?: return@launch

                // Get the current month's start and end dates
                val calendar = Calendar.getInstance()
                val year = calendar.get(Calendar.YEAR)
                val month = calendar.get(Calendar.MONTH)

                val startDate = Calendar.getInstance().apply {
                    set(year, month, 1, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val endDate = Calendar.getInstance().apply {
                    set(year, month, calendar.getActualMaximum(Calendar.DAY_OF_MONTH), 23, 59, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis

                // Query Firestore for adherence records within the current month
                val querySnapshot = db.collection("medicationAdherence")
                    .whereEqualTo("userId", userId)
                    .whereGreaterThanOrEqualTo("timestamp", startDate)
                    .whereLessThanOrEqualTo("timestamp", endDate)
                    .get()
                    .await()

                val records = querySnapshot.documents.mapNotNull { doc ->
                    doc.toObject(MedicationAdherence::class.java)
                }

                // Update the local reminderSnoozeStatus map based on the records
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                val endOfDay = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 23)
                    set(Calendar.MINUTE, 59)
                    set(Calendar.SECOND, 59)
                    set(Calendar.MILLISECOND, 999)
                }.timeInMillis

                // Process today's records to update snooze status
                records.filter { it.timestamp in today..endOfDay }.forEach { record ->
                    if (record.status == "snoozed") {
                        // Mark that this reminder has been snoozed at least once today
                        reminderSnoozeStatus[record.reminderId] = true
                    }
                }

                _adherenceRecords.value = records
            } catch (e: Exception) {
                println("Error loading adherence data: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun loadUserStreak() {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                println("Loading streak data for userId: $userId")

                val streakDoc = db.collection("userStreaks")
                    .document(userId)
                    .get()
                    .await()

                val streak = streakDoc.toObject(UserStreak::class.java) ?: UserStreak(userId = userId)
                println("Retrieved streak data: $streak")
                _userStreak.value = streak
            } catch (e: Exception) {
                println("Error loading user streak: ${e.message}")
            }
        }
    }

    fun recordAdherence(reminderId: String, medicationId: String, medicationName: String, status: String, scheduledTime: Long) {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                val adherence = MedicationAdherence(
                    reminderId = reminderId,
                    medicationId = medicationId,
                    medicationName = medicationName,
                    userId = userId,
                    status = status,
                    takenAt = System.currentTimeMillis(),
                    scheduledFor = scheduledTime
                )

                // Save the adherence record
                db.collection("medicationAdherence")
                    .document(adherence.id)
                    .set(adherence)
                    .await()

                // Get today's date (normalized to start of day)
                val today = Calendar.getInstance().apply {
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis

                // Initialize the map for today if it doesn't exist
                if (!dailyReminderStatus.containsKey(today)) {
                    dailyReminderStatus[today] = mutableMapOf()
                }

                // If this is a snooze action, mark reminder as snoozed for today
                if (status == "snoozed") {
                    dailyReminderStatus[today]?.put(reminderId, false) // false = not taken on first notification
                    reminderSnoozeStatus[reminderId] = true
                } else if (status == "taken") {
                    // If this is the first time we're recording this reminder today,
                    // and it hasn't been snoozed before, mark it as taken on first try
                    if (!dailyReminderStatus[today]?.containsKey(reminderId)!! &&
                        !reminderSnoozeStatus.getOrDefault(reminderId, false)) {
                        dailyReminderStatus[today]?.put(reminderId, true) // true = taken on first notification
                    }
                }

                // Count all reminders for today
                val allReminders = dailyReminderStatus[today]?.size ?: 0

                // Count reminders taken on first notification
                val takenOnFirstTry = dailyReminderStatus[today]?.count { it.value } ?: 0

                // Streak increases only if ALL reminders were taken on first notification
                val allTakenOnTime = allReminders > 0 && takenOnFirstTry == allReminders

                // Update the streak
                updateStreak(allTakenOnTime)

                // Reload adherence data to reflect changes
                loadAdherenceData()
            } catch (e: Exception) {
                println("Error recording adherence: ${e.message}")
            }
        }
    }

    private fun updateStreak(takenOnTime: Boolean) {
        viewModelScope.launch {
            try {
                val userId = auth.currentUser?.uid ?: return@launch
                val streakRef = db.collection("userStreaks").document(userId)

                db.runTransaction { transaction ->
                    val streakDoc = transaction.get(streakRef)
                    val streak = streakDoc.toObject(UserStreak::class.java) ?: UserStreak(userId = userId)

                    val calendar = Calendar.getInstance()
                    val today = calendar.apply {
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                    val lastDate = Calendar.getInstance().apply {
                        timeInMillis = streak.lastAdherenceDate
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                    val yesterday = Calendar.getInstance().apply {
                        add(Calendar.DAY_OF_YEAR, -1)
                        set(Calendar.HOUR_OF_DAY, 0)
                        set(Calendar.MINUTE, 0)
                        set(Calendar.SECOND, 0)
                        set(Calendar.MILLISECOND, 0)
                    }.timeInMillis

                    val newStreak = if (takenOnTime) {
                        when {
                            // Same day - no change to streak
                            lastDate == today -> streak.currentStreak
                            // Yesterday - continue streak
                            lastDate == yesterday -> streak.currentStreak + 1
                            // Gap in days - reset streak
                            else -> 1
                        }
                    } else {
                        // Medication not taken on time - reset streak
                        0
                    }

                    val updatedStreak = streak.copy(
                        currentStreak = newStreak,
                        longestStreak = maxOf(streak.longestStreak, newStreak),
                        lastAdherenceDate = today,
                        streakStartDate = if (newStreak == 1) today else streak.streakStartDate
                    )

                    transaction.set(streakRef, updatedStreak)

                    // Update local state immediately
                    _userStreak.value = updatedStreak

                    return@runTransaction null
                }.await()

            } catch (e: Exception) {
                println("Error updating streak: ${e.message}")
            }
        }
    }

    // Call this method when user logs out
    fun clearUserData() {
        resetState()
        currentUserId = null
    }

    // Force refresh data - useful when navigating back to the adherence screen
    fun refreshData() {
        val userId = auth.currentUser?.uid ?: return
        if (userId == currentUserId) {
            loadUserData()
        }
    }
}