package com.example.medisortproject.screens

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import com.example.medisortproject.NavRoute
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.runBlocking
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException


@Composable
fun OCRCaptureScreen(navController: NavController) {
    val context = LocalContext.current
    val executor = ContextCompat.getMainExecutor(context)
    val lifecycleOwner = LocalLifecycleOwner.current

    var imageCapture by remember { mutableStateOf<ImageCapture?>(null) }
    var capturedText by remember { mutableStateOf("") }
    var extractedMedicine by remember { mutableStateOf("") }
    var extractedDosage by remember { mutableStateOf("") }
    var cameraActive by remember { mutableStateOf(true) }

    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (cameraActive) {
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    imageCapture = ImageCapture.Builder().build()
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageCapture)
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Ensure the button is positioned at the bottom
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.Bottom,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = {
                    imageCapture?.let { capture ->
                        val photoFile = File(context.cacheDir, "ocr_image.jpg")
                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                        capture.takePicture(outputOptions, executor, object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                                cameraActive = false
                                processImage(photoFile, context) { text ->
                                    capturedText = text
                                    processWithGemini(context, text) { originalName, medicine, dosage ->
                                        extractedMedicine = medicine
                                        extractedDosage = dosage ?: "Not Found"
                                        navController.navigate("ocr_info_screen/$originalName/$extractedMedicine/$extractedDosage")
                                    }
                                }
                            }

                            override fun onError(exception: ImageCaptureException) {
                                Log.e("OCR", "Photo capture failed: ${exception.message}", exception)
                            }
                        })
                    }
                },
                modifier = Modifier
                    .fillMaxWidth(0.8f) // Makes button smaller than full width
                    .padding(16.dp)
            ) {
                Text("Capture Image")
            }
        }
    }
}


@Composable
fun OCRInfoScreen(navController: NavController, originalName: String, medicine: String, dosage: String, viewModel: MedicationViewModel) {
    val context = LocalContext.current
    var drugUsage by remember { mutableStateOf("Fetching...") }
    var sideEffects by remember { mutableStateOf("Fetching...") }
    var storageInfo by remember { mutableStateOf("Fetching...") }
    var showFullText by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) } // Track save state

    if (originalName != "Unknown"){
        LaunchedEffect(originalName) {
            // Log.e("Drug name:", originalName)
            fetchDrugInfo(originalName) { usage, effects, storage ->
                if (usage == null || effects == null || storage == null){
                    drugUsage = "Unknown"
                    sideEffects = "Unknown"
                    storageInfo = "Unknown"
                }
                else{
                    val usagePrompt =  "Summarize the following text under 50 words or less, using layman terms: $usage. "
                    val sideEffectsPrompt =  "Summarize the following text under 50 words or less, using layman terms: $effects. "
                    val storagePrompt =  "Summarize the following text under 50 words or less, " +
                            "describing only how to store medication using layman terms and Celsius: $storage. "
                    summarizeWithGemini(context, usagePrompt){ response ->
                        drugUsage = response
                    }
                    summarizeWithGemini(context, sideEffectsPrompt){ response ->
                        sideEffects = response
                    }
                    summarizeWithGemini(context, storagePrompt){ response ->
                        storageInfo = response
                    }
                }
            }
        }
    }
    else{
        drugUsage = "Unknown"
        sideEffects = "Unknown"
        storageInfo = "Unknown"
    }


    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Extracted Medicine: $medicine", fontWeight = FontWeight.Bold)
        Text("Extracted Dosage: $dosage", fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(16.dp))

        DisplayInfo("💊 Usage:", drugUsage) { showFullText = it }
        DisplayInfo("⚠️ Side Effects:", sideEffects) { showFullText = it }
        DisplayInfo("📦 Storage Instructions:", storageInfo) { showFullText = it }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = { navController.popBackStack() }) {
            Text("Retake Photo")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = {
                isSaving = true
                viewModel.addMedication(
                    context = context,
                    name = medicine,
                    purpose = drugUsage,
                    instructions = dosage,
                    notes = "Side Effects: $sideEffects\nStorage Info: $storageInfo",
                    photoUri = null
                )
                isSaving = false
                navController.navigate(NavRoute.Home.route)
            },
            enabled = !isSaving, // Disable button while saving
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text(if (isSaving) "Saving..." else "Save Medication")
        }
    }

    // AlertDialog to show full text when "Read More" is clicked
    if (showFullText != null) {
        AlertDialog(
            onDismissRequest = { showFullText = null },
            confirmButton = {
                Button(onClick = { showFullText = null }) {
                    Text("Close")
                }
            },
            text = { Text(showFullText!!) }
        )
    }
}

@Composable
fun DisplayInfo(title: String, text: String, onReadMore: (String) -> Unit) {
    val shortText = if (text.length > 100) text.take(100) + "..." else text

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        Text(title, fontWeight = FontWeight.Bold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(shortText, textAlign = TextAlign.Start, modifier = Modifier.weight(1f))
            if (text.length > 100) {
                Text("Read More", color = Color.Blue, modifier = Modifier.clickable { onReadMore(text) })
            }
        }
    }
}

// note, FDA API is limited to 40 calls/min as I didnt gen an api key
fun fetchDrugInfo(drugName: String, onResult: (String?, String?, String?) -> Unit) { // data from FDA
    val url = "https://api.fda.gov/drug/label.json?search=openfda.brand_name:\"$drugName\"&limit=1"
    val client = OkHttpClient()
    val request = Request.Builder()
        .url(url)
        .get()
        .build()

    client.newCall(request).enqueue(object : Callback {
        override fun onFailure(call: Call, e: IOException) {
            Log.e("OpenFDA", "API Call Failed: ${e.message}", e)
            onResult(null, null, null)
        }

        override fun onResponse(call: Call, response: Response) {
            val responseBody = response.body?.string()

            if (responseBody.isNullOrBlank()) {
                Log.e("OpenFDA", "Empty API response")
                onResult(null, null, null)
                return
            }

            try {
                val jsonResponse = JSONObject(responseBody)
                val results = jsonResponse.getJSONArray("results").getJSONObject(0)
                val indications = results.optJSONArray("indications_and_usage")?.join("\n") ?: "Not available"
                val sideEffects = results.optJSONArray("adverse_reactions")?.join("\n") ?: "Not available"
                val storage = results.optJSONArray("how_supplied")?.join("\n") ?: "Not available"

                onResult(indications, sideEffects, storage)
            } catch (e: Exception) {
                Log.e("OpenFDA", "Error parsing JSON: ${e.message}", e)
                onResult(null, null, null)
            }
        }
    })
}


fun processImage(file: File, context: Context, onResult: (String) -> Unit) {
    val image = InputImage.fromFilePath(context, Uri.fromFile(file))
    val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    recognizer.process(image)
        .addOnSuccessListener { visionText ->
            onResult(visionText.text)
        }
        .addOnFailureListener { e ->
            Log.e("OCR", "Text recognition failed", e)
            onResult("Failed to extract text")
        }
}

fun processWithGemini(context: Context, ocrText: String, onResult: (String, String, String?) -> Unit) {
    val prompt = """
        {
          "prompt": {
            "text": "Extract the medicine name and dosage from the following text: $ocrText. 
            Return the output in the exact structured format below. Do not deviate from this format, 
            and do not generate any additional information.
***Start Format***
Medicine name: {medicine name}
Medicine Dosage Strength: {medicine dosage strength}
Medicine Dosage: {medicine dosage}
***End Format***
If dosage strength (e.g., "500mg") is missing, return "NA".
If dosage (e.g., "Take two tablets every 6 hours") is missing, return "NA".
Never hallucinate or infer a dosage if it is not explicitly stated in the input text.
Example Output:
Medicine name: Paracetamol
Medicine Dosage Strength: 500mg
Medicine Dosage: Take two tablets every 6 hours
Failure to follow the specified format is not allowed. You must strictly comply with this structure."
          }
        }
    """.trimIndent()
    val response = getAIResponseSync(prompt)


    Log.d("Gemini", "Raw API Response: $response") // Log raw response
    val medicineName = Regex("(?<=Medicine name: )(.*)").find(response)?.value ?: "Unknown"
    val dosageStrength = Regex("(?<=Medicine Dosage Strength: )(.*)").find(response)?.value ?: "NA"
    val dosage = Regex("(?<=Medicine Dosage: )(.*)").find(response)?.value ?: "NA"

    fetchDrugInfo(medicineName) { indications, sideEffects, storage ->
        Log.d("OpenFDA", "Drug Info: Indications: $indications, Side Effects: $sideEffects, Storage: $storage")
    }

    onResult(medicineName, if (dosageStrength != "NA") "$medicineName - $dosageStrength" else medicineName,  dosage)
}

fun summarizeWithGemini(context: Context, prompt: String, onResult: (String) -> Unit) {
    val response = getAIResponseSync(prompt)
    Log.d("Gemini", "Raw API Response: $response") // Log raw response
    //val response = Regex("(?<=Medicine name: )(.*)").find(response)?.value ?: "Unknown"
    onResult(response)
}


fun getAIResponseSync(prompt: String): String {
    return try {
        val apiKey = "AIzaSyCyO8SsO95gDdhumXr23pHbrQoUovvs6OE"
        val model = GenerativeModel(
            modelName = "gemini-2.0-flash", // alt: gemini-pro
            apiKey = apiKey
        )
        runBlocking {
            val response = model.generateContent(content { text(prompt) })
            response.text ?: ("Medicine name: Unknown Name\n" +
                    "Medicine Dosage Strength: Unknown Strength\n" +
                    "Medicine Dosage: Unknown Dosage")
        }
    } catch (e: Exception) {
        "Oops! Something went wrong: ${e.message}"
    }
}