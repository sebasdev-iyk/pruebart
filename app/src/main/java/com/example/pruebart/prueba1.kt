package com.example.simpletflite

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel


class SimpleTFLite(private val context: Context) {
    private var interpreter: Interpreter? = null

    private val classNames = listOf("Señal_1", "Señal_2", "Señal_3")

    fun loadModel(): Boolean {
        return try {

            val modelFile = loadModelFile("modelo.tflite")

            interpreter = Interpreter(modelFile)

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    private fun loadModelFile(fileName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(fileName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    fun predict(bitmap: Bitmap): Pair<String, Float> {
        val currentInterpreter = interpreter ?: return Pair("Modelo no cargado", 0f)

        try {
            val input = prepareImage(bitmap)

            val output = Array(1) { FloatArray(classNames.size) }

            currentInterpreter.run(input, output)

            val predictions = output[0]
            val maxIndex = predictions.indices.maxByOrNull { predictions[it] } ?: 0
            val confidence = predictions[maxIndex]

            return Pair(classNames[maxIndex], confidence)

        } catch (e: Exception) {
            e.printStackTrace()
            return Pair("Error: ${e.message}", 0f)
        }
    }

    private fun prepareImage(bitmap: Bitmap): ByteBuffer {

        val resizedBitmap = Bitmap.createScaledBitmap(bitmap, 150, 150, true)

        val buffer = ByteBuffer.allocateDirect(150 * 150 * 3 * 4)
        buffer.order(ByteOrder.nativeOrder())

        val pixels = IntArray(150 * 150)
        resizedBitmap.getPixels(pixels, 0, 150, 0, 0, 150, 150)

        for (pixel in pixels) {
            val r = ((pixel shr 16) and 0xFF) / 255f  // Rojo (0-1)
            val g = ((pixel shr 8) and 0xFF) / 255f   // Verde (0-1)
            val b = (pixel and 0xFF) / 255f           // Azul (0-1)

            buffer.putFloat(r)
            buffer.putFloat(g)
            buffer.putFloat(b)
        }

        return buffer
    }

    fun close() {
        interpreter?.close()
    }
}

@Composable
fun SimpleScreen() {
    val context = LocalContext.current
    val tflite = remember { SimpleTFLite(context) }

    var modelLoaded by remember { mutableStateOf(false) }
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }
    var prediction by remember { mutableStateOf<Pair<String, Float>?>(null) }

    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            selectedImage = BitmapFactory.decodeStream(inputStream)
        }
    }

    DisposableEffect(Unit) {
        onDispose { tflite.close() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "🤖 Simple TensorFlow Lite",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Button(
            onClick = {
                modelLoaded = tflite.loadModel()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("1️⃣ Cargar Modelo")
        }

        if (modelLoaded) {
            Text("✅ Modelo cargado correctamente", color = MaterialTheme.colorScheme.primary)
        }

        if (modelLoaded) {
            Button(
                onClick = { imagePicker.launch("image/*") },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("2️⃣ Seleccionar Imagen")
            }
        }

        selectedImage?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "Imagen seleccionada",
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Button(
                onClick = {
                    prediction = tflite.predict(bitmap)
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("3️⃣ Predecir")
            }
        }

        prediction?.let { (label, confidence) ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "🎯 Resultado: $label",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "📊 Confianza: ${(confidence * 100).toInt()}%",
                        fontSize = 16.sp
                    )
                }
            }
        }

        Card {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("📝 Pasos:", fontWeight = FontWeight.Bold)
                Text("1. Coloca tu archivo 'modelo.tflite' en assets/")
                Text("2. Presiona 'Cargar Modelo'")
                Text("3. Selecciona una imagen")
                Text("4. Presiona 'Predecir'")
            }
        }
    }
}