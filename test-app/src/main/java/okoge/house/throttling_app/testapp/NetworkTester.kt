package okoge.house.throttling_app.testapp

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

data class TestResult(
    val timestamp: Long,
    val host: String,
    val latencyMs: Long,
    val downloadSpeedKbps: Double,
    val uploadSpeedKbps: Double,
    val httpStatus: Int,
    val error: String? = null,
)

data class TestState(
    val isRunning: Boolean = false,
    val results: List<TestResult> = emptyList(),
    val currentDownloadKbps: Double = 0.0,
    val currentUploadKbps: Double = 0.0,
    val averageLatencyMs: Long = 0,
)

class NetworkTester(private val scope: CoroutineScope) {

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _state = MutableStateFlow(TestState())
    val state: StateFlow<TestState> = _state.asStateFlow()

    private var job: Job? = null

    private companion object {
        const val DOWNLOAD_URL = "https://httpbin.org/bytes/102400" // 100KB
        const val UPLOAD_URL = "https://httpbin.org/post"
        const val LATENCY_URL = "https://httpbin.org/get"
        const val UPLOAD_SIZE_BYTES = 102400 // 100KB
        const val INTERVAL_MS = 3000L
        const val MAX_RESULTS = 50
    }

    fun start() {
        if (job?.isActive == true) return
        _state.update { it.copy(isRunning = true) }
        job = scope.launch {
            while (isActive) {
                val result = runTest()
                _state.update { state ->
                    val results = (listOf(result) + state.results).take(MAX_RESULTS)
                    val recentResults = results.take(5)
                    state.copy(
                        results = results,
                        currentDownloadKbps = result.downloadSpeedKbps,
                        currentUploadKbps = result.uploadSpeedKbps,
                        averageLatencyMs = if (recentResults.isNotEmpty()) {
                            recentResults.map { it.latencyMs }.average().toLong()
                        } else 0,
                    )
                }
                delay(INTERVAL_MS)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        _state.update { it.copy(isRunning = false) }
    }

    private suspend fun runTest(): TestResult = withContext(Dispatchers.IO) {
        try {
            // 1. Latency (TTFB)
            val latencyMs = measureLatency()

            // 2. Download speed
            val downloadKbps = measureDownload()

            // 3. Upload speed
            val uploadKbps = measureUpload()

            TestResult(
                timestamp = System.currentTimeMillis(),
                host = "httpbin.org",
                latencyMs = latencyMs,
                downloadSpeedKbps = downloadKbps,
                uploadSpeedKbps = uploadKbps,
                httpStatus = 200,
            )
        } catch (e: Exception) {
            TestResult(
                timestamp = System.currentTimeMillis(),
                host = "httpbin.org",
                latencyMs = -1,
                downloadSpeedKbps = 0.0,
                uploadSpeedKbps = 0.0,
                httpStatus = -1,
                error = e.message ?: "Unknown error",
            )
        }
    }

    private fun measureLatency(): Long {
        val request = Request.Builder().url(LATENCY_URL).head().build()
        val startNs = System.nanoTime()
        client.newCall(request).execute().use { response ->
            val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
            return elapsedMs
        }
    }

    private fun measureDownload(): Double {
        val request = Request.Builder().url(DOWNLOAD_URL).get().build()
        val startNs = System.nanoTime()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
            val bytes = response.body?.bytes() ?: return 0.0
            val elapsedSec = (System.nanoTime() - startNs) / 1_000_000_000.0
            return if (elapsedSec > 0) (bytes.size / 1024.0) / elapsedSec else 0.0
        }
    }

    private fun measureUpload(): Double {
        val payload = ByteArray(UPLOAD_SIZE_BYTES)
        val body = payload.toRequestBody("application/octet-stream".toMediaType())
        val request = Request.Builder().url(UPLOAD_URL).post(body).build()
        val startNs = System.nanoTime()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
            response.body?.close()
            val elapsedSec = (System.nanoTime() - startNs) / 1_000_000_000.0
            return if (elapsedSec > 0) (UPLOAD_SIZE_BYTES / 1024.0) / elapsedSec else 0.0
        }
    }
}
