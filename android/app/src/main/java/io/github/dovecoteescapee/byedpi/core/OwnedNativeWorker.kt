package io.github.dovecoteescapee.byedpi.core
import kotlinx.coroutines.*
interface NativeBackend {
    suspend fun prepare(): Int
    suspend fun run(fd: Int): Int
    suspend fun requestStop(): Int
}
class OwnedNativeWorker(private val scope: CoroutineScope, private val backend: NativeBackend) {
    private var worker: Deferred<Int>? = null
    val active: Boolean get() = worker?.isCompleted == false
    suspend fun start(onExit: suspend (Int) -> Unit) {
        check(worker == null) { "Предыдущий движок ещё не остановлен" }
        val fd = withContext(Dispatchers.IO) { backend.prepare() }
        check(fd >= 0) { "Не удалось открыть локальный SOCKS-порт" }
        val job = scope.async(Dispatchers.IO) { backend.run(fd) }
        worker = job
        job.invokeOnCompletion {
            scope.launch {
                if (worker === job) {
                    val result = try { job.await() } catch (_: Exception) { -1 }
                    onExit(result)
                }
            }
        }
    }
    suspend fun stop() {
        val job = worker ?: return
        // Detach callback ownership first; callback must not recursively initiate shutdown.
        if (!job.isCompleted) withContext(Dispatchers.IO) { backend.requestStop() }
        val finished = withTimeoutOrNull(5000) { job.join(); true } == true
        check(finished) { "Движок не завершился за 5 секунд. Останови Veyra через настройки Android." }
        if (worker === job) worker = null
    }
}
