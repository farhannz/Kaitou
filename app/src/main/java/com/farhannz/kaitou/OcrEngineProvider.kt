package com.farhannz.kaitou

import android.content.Context
import com.farhannz.kaitou.domain.OcrEngine
import com.farhannz.kaitou.domain.TextRecognizer
import com.farhannz.kaitou.helpers.Logger
import com.farhannz.kaitou.impl.OnnxEngineFactory
import com.farhannz.kaitou.impl.OnnxEngineType
import com.farhannz.kaitou.impl.OnnxTextRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import org.opencv.android.OpenCVLoader

/**
 * Holds the OCR engines and loads them asynchronously off the main thread.
 *
 * ONNX session creation (including hardware acceleration setup) can take
 * seconds on device; doing it synchronously in Application.onCreate blocked
 * the main thread before the first frame was ever drawn. UI observes [state]
 * to show a warm-up indicator, or calls [awaitReady] before inferring.
 */
object OcrEngineProvider {
    sealed interface EngineState {
        data object Idle : EngineState
        data object Loading : EngineState
        data class Ready(
            val detectionEngine: OcrEngine,
            val recognitionEngine: OcrEngine,
            val textRecognizer: TextRecognizer
        ) : EngineState

        data class Error(val throwable: Throwable) : EngineState
    }

    private val logger = Logger(OcrEngineProvider::class.simpleName!!)
    private val _state = MutableStateFlow<EngineState>(EngineState.Idle)
    val state: StateFlow<EngineState> = _state.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    fun initializeAsync(context: Context) {
        when (_state.value) {
            is EngineState.Ready, is EngineState.Loading -> return
            else -> {}
        }
        _state.value = EngineState.Loading
        val appContext = context.applicationContext
        scope.launch {
            val started = System.nanoTime()
            // Load the OpenCV native library before the engines — the first
            // infer() call converts bitmaps to Mat, which needs it ready.
            if (OpenCVLoader.initLocal()) {
                logger.DEBUG("OpenCV initialized successfully")
            } else {
                logger.ERROR("OpenCV initialization failed")
            }
            _state.value = runCatching {
                val detection = OnnxEngineFactory.create(appContext, OnnxEngineType.Detection)
                val recognition = OnnxEngineFactory.create(appContext, OnnxEngineType.Recognition)
                EngineState.Ready(detection, recognition, OnnxTextRecognizer(recognition))
            }.getOrElse {
                logger.ERROR("OCR engine loading failed: ${it.message}")
                EngineState.Error(it)
            }
            val elapsed = (System.nanoTime() - started) / 1_000_000.0
            logger.INFO("[latency] OCR engine load: %.2f ms".format(elapsed))
        }
    }

    fun retry(context: Context) {
        _state.value = EngineState.Idle
        initializeAsync(context)
    }

    /** Suspends until the engines are loaded. Safe to call from any dispatcher. */
    suspend fun awaitReady(): EngineState.Ready =
        state.filterIsInstance<EngineState.Ready>().first()
}
