package io.github.dovecoteescapee.byedpi.services

import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import io.github.dovecoteescapee.byedpi.data.Mode
import io.github.dovecoteescapee.byedpi.data.START_ACTION
import io.github.dovecoteescapee.byedpi.data.STOP_ACTION
import io.github.dovecoteescapee.byedpi.core.UnifiedConnection
import io.github.dovecoteescapee.byedpi.utility.getPreferences

object ServiceManager {
    private val TAG: String = ServiceManager::class.java.simpleName

    fun start(context: Context, mode: Mode) {
        if (connectionState.value.busy || connectionState.value.phase == ConnectionPhase.Running) return
        try {
            UnifiedConnection.prepare(context.getPreferences())
            setPhase(ConnectionPhase.Starting, mode)
            when (mode) {
            Mode.VPN -> {
                Log.i(TAG, "Starting VPN")
                val intent = Intent(context, ByeDpiVpnService::class.java)
                intent.action = START_ACTION
                ContextCompat.startForegroundService(context, intent)
            }

            Mode.Proxy -> {
                Log.i(TAG, "Starting proxy")
                val intent = Intent(context, ByeDpiProxyService::class.java)
                intent.action = START_ACTION
                ContextCompat.startForegroundService(context, intent)
            }
        } } catch (e: Exception) { setPhase(ConnectionPhase.Failed, mode, e.message); Log.e(TAG, "Service command failed", e) }
    }

    fun stop(context: Context) {
        if (connectionState.value.busy || connectionState.value.phase == ConnectionPhase.Stopped) return
        val (_, mode) = appStatus
        setPhase(ConnectionPhase.Stopping, mode)
        try { when (mode) {
            Mode.VPN -> {
                Log.i(TAG, "Stopping VPN")
                val intent = Intent(context, ByeDpiVpnService::class.java)
                intent.action = STOP_ACTION
                ContextCompat.startForegroundService(context, intent)
            }

            Mode.Proxy -> {
                Log.i(TAG, "Stopping proxy")
                val intent = Intent(context, ByeDpiProxyService::class.java)
                intent.action = STOP_ACTION
                ContextCompat.startForegroundService(context, intent)
            }
        } } catch (e: Exception) { setPhase(ConnectionPhase.Failed, mode, e.message); Log.e(TAG, "Service command failed", e) }
    }
}
