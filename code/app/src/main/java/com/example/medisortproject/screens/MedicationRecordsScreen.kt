package com.example.medisortproject.screens

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Environment
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.example.medisortproject.components.MediSortScaffold
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.UUID


// Data model for a medication
data class Medication(
    val id: String = "",
    val name: String = "",
    val purpose: String = "",
    val instructions: String = "",
    val notes: String = "",
    val photoUrl: String = "",  // Now stores local file path
    val createdAt: Long = System.currentTimeMillis(),
    val userId: String = ""
)

// Function to save images persistently in local storage
fun saveImageToLocalStorage(context: Context, uri: Uri): String {
    val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
    val file = File(context.getExternalFilesDir(null), "${UUID.randomUUID()}.jpg")

    inputStream?.use { input ->
        FileOutputStream(file).use { output ->
            input.copyTo(output)
        }
    }
    return file.absolutePath
}

// ViewModel
class MedicationViewModel : ViewModel() {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private val _medications = MutableStateFlow<List<Medication>>(emptyList())
    val medications: StateFlow<List<Medication>> = _medications

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private var snapshotListener: ListenerRegistration? = null

    fun resetState() {
        snapshotListener?.remove() // Remove existing listener
        _medications.value = emptyList()
        loadMedications()
    }

    init {
        loadMedications()
    }

    private fun loadMedications() {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val userId = auth.currentUser?.uid ?: return@launch

                snapshotListener = db.collection("medications")
                    .whereEqualTo("userId", userId)
                    .orderBy("createdAt")
                    .addSnapshotListener { snapshot, error ->
                        if (error != null) {
                            println("Error loading medications: ${error.message}")
                            return@addSnapshotListener
                        }

                        if (snapshot != null) {
                            _medications.value = snapshot.documents.mapNotNull { doc ->
                                doc.toObject(Medication::class.java)
                            }
                        }
                        _isLoading.value = false
                    }
            } catch (e: Exception) {
                println("Error loading medications: ${e.message}")
                _isLoading.value = false
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        snapshotListener?.remove()
    }

    fun addMedication(
        context: Context,
        name: String,
        purpose: String,
        instructions: String,
        notes: String,
        photoUri: String?
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                val userId = auth.currentUser?.uid ?: return@launch

                // Only save image if photoUri is not null
                val localPhotoPath = if (!photoUri.isNullOrEmpty()) {
                    saveImageToLocalStorage(context, Uri.parse(photoUri))
                } else {
                    "" // Store an empty string if no image is selected
                }

                val medication = Medication(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    purpose = purpose,
                    instructions = instructions,
                    notes = notes,
                    photoUrl = localPhotoPath, // Empty if no image
                    userId = userId
                )

                db.collection("medications")
                    .document(medication.id)
                    .set(medication)
                    .await()

                loadMedications()
            } catch (e: Exception) {
                println("Error adding medication: ${e.message}")
            } finally {
                _isLoading.value = false
            }
        }
    }


    fun deleteMedication(medicationId: String) {
        viewModelScope.launch {
            try {
                db.collection("medications")
                    .document(medicationId)
                    .delete()
                    .await()

                loadMedications()
            } catch (e: Exception) {
                println("Error deleting medication: ${e.message}")
            }
        }
    }
}

@Composable
fun MedicationRecordsScreen(navController: NavController, viewModel: MedicationViewModel) {
    val context = LocalContext.current
    var showAddDialog by remember { mutableStateOf(false) }
    val medications by viewModel.medications.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()

    MediSortScaffold(
        navController = navController,
        title = "Medication Records",
        showLogout = true,
        showBottomNav = true
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp) // ✅ No need to use "then"
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = { showAddDialog = true }) {
                    Icon(Icons.Default.Add, "Add Medication")
                }
            }

            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            } else {
                LazyColumn {
                    items(medications) { medication ->
                        MedicationCard(
                            medication = medication,
                            onDelete = { viewModel.deleteMedication(medication.id) },
                            onClick = {
                                navController.navigate("medication_detail/${medication.id}")
                            }
                        )
                    }
                }
            }
        }
    }


    if (showAddDialog) {
        AddMedicationDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { name, purpose, instructions, notes, photoUri ->
                viewModel.addMedication(context, name, purpose, instructions, notes, photoUri)
                showAddDialog = false
            }
        )
    }
}

@Composable
fun MedicationCard(medication: Medication, onDelete: () -> Unit, onClick: () -> Unit) {
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
            Column {
                Text(text = medication.name, style = MaterialTheme.typography.titleMedium)

                if (medication.photoUrl.isNotEmpty()) {
                    Image(
                        painter = rememberAsyncImagePainter(medication.photoUrl),
                        contentDescription = "Medication Image",
                        modifier = Modifier
                            .size(100.dp)
                            .padding(top = 8.dp)
                    )
                }
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "Delete Medication")
            }
        }
    }
}



@Composable
fun AddMedicationDialog(
    onDismiss: () -> Unit,
    onAdd: (String, String, String, String, String?) -> Unit
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var purpose by remember { mutableStateOf("") }
    var instructions by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var photoUri by remember { mutableStateOf<Uri?>(null) }
    var tempPhotoUri by remember { mutableStateOf<Uri?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (success) {
            tempPhotoUri?.let { uri ->
                photoUri = uri
            }
        }
    }

    val requestPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
        if (isGranted) {
            val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "medication_image_${UUID.randomUUID()}.jpg")
            tempPhotoUri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",  // Ensure this matches AndroidManifest.xml
                file
            )
            tempPhotoUri?.let { uri ->
                cameraLauncher.launch(uri)
            }
        }
    }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        photoUri = uri
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add New Medication") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Medication Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = purpose,
                    onValueChange = { purpose = it },
                    label = { Text("Treatment Purpose") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = instructions,
                    onValueChange = { instructions = it },
                    label = { Text("Dosage Instructions") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Additional Notes") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Button(
                        onClick = {
                            when (PackageManager.PERMISSION_GRANTED) {
                                ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) -> {
                                    val file = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "medication_image_${UUID.randomUUID()}.jpg")
                                    tempPhotoUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                                    tempPhotoUri?.let { uri ->
                                        cameraLauncher.launch(uri)
                                    }
                                }
                                else -> requestPermissionLauncher.launch(Manifest.permission.CAMERA)
                            }
                        }
                    ) {
                        Text("Take Photo")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { imagePicker.launch("image/*") }
                    ) {
                        Text("Choose Photo")
                    }
                }

                if (photoUri != null) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Image(
                        painter = rememberAsyncImagePainter(photoUri),
                        contentDescription = "Selected Image",
                        modifier = Modifier.height(200.dp).fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && purpose.isNotBlank() && instructions.isNotBlank()) {
                        onAdd(name, purpose, instructions, notes, photoUri?.toString())
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


