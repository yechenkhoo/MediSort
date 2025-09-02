package com.example.medisortproject.screens

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.navigation.NavController
import coil.compose.rememberAsyncImagePainter
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import com.journeyapps.barcodescanner.BarcodeEncoder
import java.io.OutputStream

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationDetailScreen(
    navController: NavController,
    viewModel: MedicationViewModel,
    medicationId: String
) {
    val medications by viewModel.medications.collectAsState()
    val medication = medications.find { it.id == medicationId }

    var showQRDialog by remember { mutableStateOf(false) }

    if (medication == null) {
        Text("Loading medication details...", modifier = Modifier.padding(16.dp))
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(medication.name) },
                navigationIcon = {
                    IconButton(onClick = { navController.navigateUp() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showQRDialog = true }) {
                        Icon(Icons.Default.QrCode, "Generate QR Code")
                    }
                    IconButton(onClick = {
                        viewModel.deleteMedication(medicationId)
                        navController.navigateUp()
                    }) {
                        Icon(Icons.Default.Delete, "Delete Medication")
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
                .verticalScroll(rememberScrollState())
        ) {
            if (medication.photoUrl.isNotEmpty()) {
                Image(
                    painter = rememberAsyncImagePainter(Uri.parse(medication.photoUrl)),
                    contentDescription = "Medication Image",
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
            }
            Text(text = "Purpose: ${medication.purpose}")
            Text(text = "Instructions: ${medication.instructions}")
            if (medication.notes.isNotEmpty()) {
                Text(text = "Notes: ${medication.notes}")
            }
        }
    }

    if (showQRDialog) {
        QRCodeDialog(medicationId) { showQRDialog = false }
    }
}

@Composable
fun QRCodeDialog(medicationId: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val deepLink = "medisort://medication_detail/$medicationId"  // Deep link for navigation
    val qrBitmap = remember { generateQRCode(deepLink) }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "Scan to View Medication", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(16.dp))

            qrBitmap?.let {
                Image(bitmap = it.asImageBitmap(), contentDescription = "QR Code")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Button(onClick = { qrBitmap?.let { saveQRCodeToGallery(context, it) } }) {
                    Text("Save to Gallery")
                }
                Button(onClick = { qrBitmap?.let { shareQRCode(context, it) } }) {
                    Text("Share")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(onClick = onDismiss) {
                Text("Close")
            }
        }
    }
}

/** Function to Save QR Code to Gallery */
fun saveQRCodeToGallery(context: Context, bitmap: Bitmap) {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "QR_Code_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
    }

    val uri: Uri? = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    uri?.let {
        val outputStream: OutputStream? = context.contentResolver.openOutputStream(it)
        outputStream?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }
        Toast.makeText(context, "QR Code Saved to Gallery!", Toast.LENGTH_SHORT).show()
    } ?: run {
        Toast.makeText(context, "Failed to Save QR Code", Toast.LENGTH_SHORT).show()
    }
}

/** Function to Share QR Code */
fun shareQRCode(context: Context, bitmap: Bitmap) {
    val contentValues = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "QR_Code_${System.currentTimeMillis()}.png")
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
    }

    val uri: Uri? = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

    uri?.let {
        val outputStream: OutputStream? = context.contentResolver.openOutputStream(it)
        outputStream?.use { stream ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)
        }

        // Create Intent to Share
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share QR Code"))
    } ?: run {
        Toast.makeText(context, "Failed to Share QR Code", Toast.LENGTH_SHORT).show()
    }
}

fun generateQRCode(content: String): Bitmap? {
    return try {
        val bitMatrix: BitMatrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, 1000, 1000)
        val barcodeEncoder = BarcodeEncoder()
        barcodeEncoder.createBitmap(bitMatrix)
    } catch (e: Exception) {
        Log.e("QR Code", "Error generating QR Code: ${e.message}")
        null
    }
}