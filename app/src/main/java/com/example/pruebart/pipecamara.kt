package com.example.pruebart

import android.Manifest
import android.content.Context
import android.graphics.*
import android.util.Log
import android.view.View
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.framework.image.MPImage
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarker
import com.google.mediapipe.tasks.vision.handlandmarker.HandLandmarkerResult
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.sqrt

// Vista personalizada para dibujar solo los puntos (sin conexiones)
class HandLandmarksOverlay(context: Context) : View(context) {
    private var handLandmarks: List<List<NormalizedLandmark>> = emptyList()
    private var viewWidth = 0
    private var viewHeight = 0
    private var imageWidth = 0
    private var imageHeight = 0
    private var isFrontCamera = true

    private val pointPaint = Paint().apply {
        style = Paint.Style.FILL
        isAntiAlias = true
    }

    private val textPaint = Paint().apply {
        color = Color.WHITE
        textSize = 24f
        textAlign = Paint.Align.CENTER
        setShadowLayer(4f, 2f, 2f, Color.BLACK)
    }

    fun updateLandmarks(
        landmarks: List<List<NormalizedLandmark>>,
        imageWidth: Int,
        imageHeight: Int,
        isFrontCamera: Boolean = true
    ) {
        this.handLandmarks = landmarks
        this.imageWidth = imageWidth
        this.imageHeight = imageHeight
        this.isFrontCamera = isFrontCamera
        invalidate() // Redibuja la vista
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        viewWidth = w
        viewHeight = h
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (viewWidth == 0 || viewHeight == 0 || imageWidth == 0 || imageHeight == 0) return

        // Dibujar landmarks para cada mano detectada
        handLandmarks.forEach { landmarks ->
            drawHandPoints(canvas, landmarks)
        }
    }

    private fun drawHandPoints(canvas: Canvas, landmarks: List<NormalizedLandmark>) {
        landmarks.forEachIndexed { index, landmark ->
            // Transformar coordenadas considerando la rotación y el espejo de la cámara frontal
            var x = landmark.x()
            var y = landmark.y()

            // Para cámara frontal: rotar 180 grados y hacer espejo
            if (isFrontCamera) {
                // Rotar 180 grados: invertir tanto X como Y
                x = 1.0f - x
                y = 1.0f - y
            }

            // Escalar a las dimensiones de la vista
            val screenX = x * viewWidth
            val screenY = y * viewHeight

            // Cambiar color según el tipo de punto
            when (index) {
                0 -> pointPaint.color = Color.BLUE // Muñeca
                4, 8, 12, 16, 20 -> pointPaint.color = Color.YELLOW // Puntas de dedos
                else -> pointPaint.color = Color.RED // Otros puntos
            }

            // Dibujar punto más grande y visible
            canvas.drawCircle(screenX, screenY, 12f, pointPaint)

            // Dibujar número del punto con sombra para mejor visibilidad
            canvas.drawText(index.toString(), screenX, screenY + 6, textPaint)
        }
    }
}

// Clase HandDetector (sin cambios significativos)
class HandDetector(private val context: Context) {

    private var handLandmarker: HandLandmarker? = null
    private val MP_HAND_LANDMARKER_TASK = "hand_landmarker.task"

    // Configuración
    private val minHandDetectionConfidence = 0.5f
    private val minTrackingConfidence = 0.5f
    private val minHandPresenceConfidence = 0.5f
    private val maxNumHands = 2

    // Callbacks
    var onHandsDetected: ((HandLandmarkerResult, Int, Int) -> Unit)? = null
    var onError: ((String) -> Unit)? = null

    fun initialize(): Boolean {
        return try {
            val baseOptionsBuilder = BaseOptions.builder()
                .setModelAssetPath(MP_HAND_LANDMARKER_TASK)
            val baseOptions = baseOptionsBuilder.build()

            val optionsBuilder = HandLandmarker.HandLandmarkerOptions.builder()
                .setBaseOptions(baseOptions)
                .setMinHandDetectionConfidence(minHandDetectionConfidence)
                .setMinTrackingConfidence(minTrackingConfidence)
                .setMinHandPresenceConfidence(minHandPresenceConfidence)
                .setNumHands(maxNumHands)
                .setResultListener { result: HandLandmarkerResult, input: MPImage ->
                    handleResult(result, input)
                }
                .setErrorListener { error: RuntimeException ->
                    handleError(error)
                }
                .setRunningMode(RunningMode.LIVE_STREAM)

            val options = optionsBuilder.build()
            handLandmarker = HandLandmarker.createFromOptions(context, options)

            Log.d("HandDetector", "HandLandmarker inicializado correctamente")
            true

        } catch (e: Exception) {
            Log.e("HandDetector", "Error al inicializar: ${e.message}")
            onError?.invoke("Error al inicializar detector: ${e.message}")
            false
        }
    }

    fun detectHands(bitmap: Bitmap) {
        try {
            val mpImage = BitmapImageBuilder(bitmap).build()
            val timestampMs = System.currentTimeMillis()
            handLandmarker?.detectAsync(mpImage, timestampMs)
        } catch (e: Exception) {
            Log.e("HandDetector", "Error en detección: ${e.message}")
            onError?.invoke("Error en detección: ${e.message}")
        }
    }

    private fun handleResult(
        result: HandLandmarkerResult,
        input: MPImage
    ) {
        Log.d("HandDetector", "Manos detectadas: ${result.landmarks().size}")
        onHandsDetected?.invoke(result, input.width, input.height)
    }

    private fun handleError(error: RuntimeException) {
        Log.e("HandDetector", "Error: ${error.message}")
        onError?.invoke("Error en detector: ${error.message}")
    }

    fun close() {
        handLandmarker?.close()
        handLandmarker = null
        Log.d("HandDetector", "HandDetector cerrado")
    }

    companion object {
        // Índices de landmarks importantes
        const val WRIST = 0
        const val THUMB_TIP = 4
        const val INDEX_TIP = 8
        const val MIDDLE_TIP = 12
        const val RING_TIP = 16
        const val PINKY_TIP = 20

        // Función para contar dedos extendidos
        fun countExtendedFingers(landmarks: List<NormalizedLandmark>): Int {
            var count = 0

            // Pulgar (verificar si está extendido comparando con el punto anterior)
            if (landmarks[THUMB_TIP].x() > landmarks[3].x()) count++

            // Otros dedos (verificar si la punta está por encima del punto medio)
            if (landmarks[INDEX_TIP].y() < landmarks[6].y()) count++
            if (landmarks[MIDDLE_TIP].y() < landmarks[10].y()) count++
            if (landmarks[RING_TIP].y() < landmarks[14].y()) count++
            if (landmarks[PINKY_TIP].y() < landmarks[18].y()) count++

            return count
        }

        // Calcular distancia entre dos puntos
        fun calculateDistance(
            point1: NormalizedLandmark,
            point2: NormalizedLandmark
        ): Float {
            val dx = point1.x() - point2.x()
            val dy = point1.y() - point2.y()
            return sqrt(dx * dx + dy * dy)
        }
    }
}

// Composable principal
@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun pipe() {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    // Estados
    var handDetectionInfo by remember { mutableStateOf("Iniciando detector...") }
    var fingerCount by remember { mutableStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var landmarks by remember { mutableStateOf<List<List<NormalizedLandmark>>>(emptyList()) }
    var imageSize by remember { mutableStateOf(Pair(0, 0)) }

    // Detector y executor
    val handDetector = remember { HandDetector(context) }
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    // Configurar detector
    LaunchedEffect(Unit) {
        handDetector.onHandsDetected = { result, width, height ->
            imageSize = Pair(width, height)
            if (result.landmarks().isNotEmpty()) {
                landmarks = result.landmarks()
                val firstHandLandmarks = result.landmarks()[0]
                val count = HandDetector.countExtendedFingers(firstHandLandmarks)
                fingerCount = count
                handDetectionInfo = "Manos detectadas: ${result.landmarks().size}, Dedos: $count"
            } else {
                landmarks = emptyList()
                handDetectionInfo = "No se detectaron manos"
                fingerCount = 0
            }
        }

        handDetector.onError = { error ->
            errorMessage = error
        }

        if (!handDetector.initialize()) {
            errorMessage = "Error al inicializar detector"
        }
    }

    // Limpiar recursos
    DisposableEffect(Unit) {
        onDispose {
            handDetector.close()
            cameraExecutor.shutdown()
        }
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Información de estado
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "Estado: $handDetectionInfo",
                    style = MaterialTheme.typography.bodyLarge
                )

                if (fingerCount > 0) {
                    Text(
                        text = "Dedos extendidos: $fingerCount",
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                errorMessage?.let { error ->
                    Text(
                        text = "Error: $error",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }

        // Vista de cámara con overlay
        if (cameraPermissionState.status.isGranted) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                CameraPreviewComposable(
                    handDetector = handDetector,
                    lifecycleOwner = lifecycleOwner,
                    cameraExecutor = cameraExecutor,
                    modifier = Modifier.fillMaxSize()
                )

                // Overlay para mostrar solo los puntos
                AndroidView(
                    factory = { ctx ->
                        HandLandmarksOverlay(ctx)
                    },
                    update = { overlay ->
                        overlay.updateLandmarks(
                            landmarks,
                            imageSize.first,
                            imageSize.second,
                            isFrontCamera = true
                        )
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            // Solicitar permisos
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.Center
            ) {
                Text("Se requiere permiso de cámara")
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = { cameraPermissionState.launchPermissionRequest() }
                ) {
                    Text("Conceder permiso")
                }
            }
        }
    }
}

@Composable
fun CameraPreviewComposable(
    handDetector: HandDetector,
    lifecycleOwner: LifecycleOwner,
    cameraExecutor: ExecutorService,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    AndroidView(
        factory = { ctx ->
            val previewView = PreviewView(ctx)
            previewView.scaleType = PreviewView.ScaleType.FILL_CENTER

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

            cameraProviderFuture.addListener({
                val cameraProvider = cameraProviderFuture.get()

                val preview = Preview.Builder()
                    .build()
                    .also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                val imageAnalyzer = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(cameraExecutor) { imageProxy ->
                            processImageProxy(imageProxy, handDetector)
                        }
                    }

                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA

                try {
                    cameraProvider.unbindAll()
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalyzer
                    )
                } catch (exc: Exception) {
                    Log.e("CameraPreview", "Error al iniciar cámara", exc)
                }

            }, ContextCompat.getMainExecutor(ctx))

            previewView
        },
        modifier = modifier
    )
}

private fun processImageProxy(imageProxy: ImageProxy, handDetector: HandDetector) {
    try {
        val bitmap = imageProxyToBitmap(imageProxy)
        handDetector.detectHands(bitmap)
    } catch (e: Exception) {
        Log.e("ImageProcessing", "Error procesando imagen: ${e.message}")
    } finally {
        imageProxy.close()
    }
}

private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap {
    val yBuffer = imageProxy.planes[0].buffer // Y
    val vuBuffer = imageProxy.planes[2].buffer // VU

    val ySize = yBuffer.remaining()
    val vuSize = vuBuffer.remaining()

    val nv21 = ByteArray(ySize + vuSize)

    yBuffer.get(nv21, 0, ySize)
    vuBuffer.get(nv21, ySize, vuSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}