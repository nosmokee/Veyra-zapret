package io.github.dovecoteescapee.byedpi.services
import android.app.Notification
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.lifecycle.LifecycleService
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.core.*
import io.github.dovecoteescapee.byedpi.data.*
import io.github.dovecoteescapee.byedpi.utility.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
class ByeDpiProxyService : LifecycleService() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val operations = Mutex()
    private val nativeProxy = ByeDpiProxy()
    private val worker = OwnedNativeWorker(scope, object : NativeBackend {
        override suspend fun prepare() = nativeProxy.prepareProxy(ByeDpiProxyPreferences.fromSharedPreferences(getPreferences()), getPreferences().getBoolean("veyra_block_quic", false))
        override suspend fun run(fd: Int) = nativeProxy.runPreparedProxy(fd)
        override suspend fun requestStop() = nativeProxy.stopProxy()
    })
    private var active = false
    private var stopping = false
    private var destroying = false
    private var terminated = false
    private val mode = Mode.Proxy
    private val tag = "ByeDpiProxyService"
    private val channel = "VeyraProxy"
    override fun onCreate() {
        super.onCreate()
        registerNotificationChannel(this, channel, R.string.proxy_channel_name)
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)
        scope.launch {
            when (intent?.action) {
                START_ACTION -> start()
                STOP_ACTION -> stop()
            }
        }
        return START_NOT_STICKY
    }
    private suspend fun start() {
        operations.withLock {
            if (active || destroying) return
            try {
                // Failed cleanup must finish before reusing native global parameters.
                if (worker.active) worker.stop()
                setPhase(ConnectionPhase.Starting, mode)
                foreground()
                worker.start { code ->
                    operations.withLock {
                        if (active && !stopping && !destroying) {
                            Log.e(tag, "Native proxy exited: $code")
                            cleanup()
                            fail("Локальный прокси завершился с кодом $code")
                            stopSelf()
                        }
                    }
                }
                checkSocksReady()
                withContext(Dispatchers.IO) { TelegramRuntime.start(this@ByeDpiProxyService,this@ByeDpiProxyService,getPreferences().getStringNotNull("byedpi_proxy_port", "1080").toInt()) }
                check(!destroying) { "Служба Android завершается" }
                active = true
                setPhase(ConnectionPhase.Running, mode)
                broadcast(STARTED_BROADCAST)
            } catch (e: Exception) {
                Log.e(tag, "Start failed", e)
                try { cleanup() } catch (cleanup: Exception) { Log.e(tag, "Cleanup failed", cleanup) }
                fail(e.message ?: "Не удалось запустить подключение")
                stopSelf()
            }
        }
    }
    private suspend fun checkSocksReady() = withContext(Dispatchers.IO) {
        val port = getPreferences().getStringNotNull("byedpi_proxy_port", "1080").toInt()
        var ready = false
        repeat(20) {
            if (!ready) try {
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", port), 250)
                    s.soTimeout = 500
                    s.getOutputStream().write(byteArrayOf(5, 1, 0))
                    check(s.getInputStream().read() == 5 && s.getInputStream().read() == 0) { "SOCKS5 не отвечает" }
                    ready = true
                }
            } catch (_: Exception) { delay(50) }
        }
        check(ready && worker.active) { "Локальный SOCKS5 не готов. Проверь журнал." }
    }
    private suspend fun stop() {
        operations.withLock {
            terminated = true
            setPhase(ConnectionPhase.Stopping, mode)
            try {
                cleanup()
                setPhase(ConnectionPhase.Stopped, mode)
                broadcast(STOPPED_BROADCAST)
            } catch (e: Exception) { Log.e(tag, "Stop failed", e); fail(e.message ?: "Ошибка остановки") }
            finally { stopSelf() }
        }
    }
    private suspend fun cleanup() {
        stopping = true
        active = false
        withContext(Dispatchers.IO) { TelegramRuntime.stop(this@ByeDpiProxyService) }
        try {
            worker.stop()
        } finally {
            stopping = false
            if (Build.VERSION.SDK_INT >= 24) stopForeground(Service.STOP_FOREGROUND_REMOVE)
            else @Suppress("DEPRECATION") stopForeground(true)
        }
    }
    private fun fail(message: String) {
        terminated = true
        setPhase(ConnectionPhase.Failed, mode, message)
        broadcast(FAILED_BROADCAST)
    }
    private fun broadcast(action: String) {
        sendBroadcast(Intent(action).setPackage(packageName).putExtra(SENDER, Sender.Proxy.ordinal))
    }
    private fun foreground() {
        val notification: Notification = createConnectionNotification(this, channel, R.string.notification_title,
            R.string.proxy_notification_content, ByeDpiProxyService::class.java)
        if (Build.VERSION.SDK_INT >= 34) startForeground(2, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(2, notification)
    }
    override fun onDestroy() {
        destroying = true
        super.onDestroy()
        scope.launch {
            operations.withLock {
                try { cleanup() } catch (e: Exception) { Log.e(tag, "Destroy cleanup", e) }
                if (!terminated && connectionState.value.mode == mode && connectionState.value.phase != ConnectionPhase.Failed) {
                    setPhase(ConnectionPhase.Stopped, mode)
                    broadcast(STOPPED_BROADCAST)
                }
            }
            scope.cancel()
        }
    }
}
