package com.example.pruebart


import android.Manifest
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import org.tensorflow.lite.Interpreter
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import java.util.concurrent.Executors

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
            val r = ((pixel shr 16) and 0xFF) / 255f
            val g = ((pixel shr 8) and 0xFF) / 255f
            val b = (pixel and 0xFF) / 255f

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

// Clase para manejar el análisis de imágenes de la cámara
class ImageAnalyzer(
    private val tflite: SimpleTFLite,
    private val onResult: (Pair<String, Float>) -> Unit,
) : ImageAnalysis.Analyzer {

    override fun analyze(imageProxy: ImageProxy) {
        val bitmap = imageProxyToBitmap(imageProxy)
        if (bitmap != null) {
            val result = tflite.predict(bitmap)
            onResult(result)
        }
        imageProxy.close()
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        return try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage =
                YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, imageProxy.width, imageProxy.height), 100, out)
            val imageBytes = out.toByteArray()
            BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun CameraScreen() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val tflite = remember { SimpleTFLite(context) }

    var modelLoaded by remember { mutableStateOf(false) }
    var prediction by remember { mutableStateOf<Pair<String, Float>?>(null) }
    var isCameraActive by remember { mutableStateOf(false) }
    var selectedImage by remember { mutableStateOf<Bitmap?>(null) }

    // Permisos de cámara
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Selector de imágenes (mantener opción original)
    val imagePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            val inputStream = context.contentResolver.openInputStream(it)
            selectedImage = BitmapFactory.decodeStream(inputStream)
            isCameraActive = false
        }
    }

    // Executor para la cámara
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Variables para la cámara
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }

    // Función para iniciar la cámara
    fun startCamera() {
        val cameraProvider = cameraProviderFuture.get()

        val preview = Preview.Builder().build()
        val imageAnalyzer = ImageAnalysis.Builder()
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .build()

        imageAnalyzer.setAnalyzer(cameraExecutor, ImageAnalyzer(tflite) { result ->
            prediction = result
        })

        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

        try {
            cameraProvider.unbindAll()
            cameraProvider.bindToLifecycle(
                lifecycleOwner,
                cameraSelector,
                preview,
                imageAnalyzer
            )
            previewView?.let { preview.setSurfaceProvider(it.surfaceProvider) }
        } catch (exc: Exception) {
            exc.printStackTrace()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            tflite.close()
            cameraExecutor.shutdown()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "📱 Detector de Señales",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        // Botón para cargar modelo
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Botón para usar cámara
                Button(
                    onClick = {
                        if (cameraPermissionState.status.isGranted) {
                            isCameraActive = true
                            selectedImage = null
                            startCamera()
                        } else {
                            cameraPermissionState.launchPermissionRequest()
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("📸 Cámara")
                }

                // Botón para seleccionar imagen
                Button(
                    onClick = {
                        imagePicker.launch("image/*")
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🖼️ Galería")
                }
            }
        }

        // Vista de la cámara o imagen seleccionada
        if (isCameraActive && cameraPermissionState.status.isGranted) {
            AndroidView(
                factory = { context ->
                    PreviewView(context).also {
                        previewView = it
                        startCamera()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp)
                    .clip(RoundedCornerShape(8.dp))
            )

            Button(
                onClick = {
                    isCameraActive = false
                    val cameraProvider = cameraProviderFuture.get()
                    cameraProvider.unbindAll()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("❌ Detener Cámara")
            }
        } else if (selectedImage != null) {
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
                    Text("🔍 Analizar Imagen")
                }
            }
        }

        // Mostrar permisos requeridos
        if (!cameraPermissionState.status.isGranted) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("⚠️ Permiso de cámara requerido", fontWeight = FontWeight.Bold)
                    Text("Para usar la detección en tiempo real, necesitas permitir el acceso a la cámara.")
                }
            }
        }

        // Resultados de predicción
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
                        text = "🎯 Señal detectada: $label",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "📊 Confianza: ${(confidence * 100).toInt()}%",
                        fontSize = 16.sp
                    )

                    // Barra de confianza visual
                    LinearProgressIndicator(
                        progress = confidence,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp)
                    )
                }
            }
        }

        // Instrucciones
        Card {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("📝 Instrucciones:", fontWeight = FontWeight.Bold)
                Text("1. Coloca 'modelo.tflite' en assets/")
                Text("2. Carga el modelo")
                Text("3. Usa la cámara para detección en tiempo real")
                Text("4. O selecciona una imagen de la galería")
            }
        }
    }
}