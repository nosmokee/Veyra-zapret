package io.github.dovecoteescapee.byedpi.services
import io.github.dovecoteescapee.byedpi.data.AppStatus
import io.github.dovecoteescapee.byedpi.data.Mode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class ConnectionPhase { Stopped, Starting, Running, Stopping, Failed }
data class ConnectionSnapshot(val phase: ConnectionPhase = ConnectionPhase.Stopped, val mode: Mode = Mode.VPN, val error: String? = null, val sinceNanos:Long = 0L) {
    val busy: Boolean get() = phase == ConnectionPhase.Starting || phase == ConnectionPhase.Stopping
}
private val mutableConnectionState = MutableStateFlow(ConnectionSnapshot())
val connectionState = mutableConnectionState.asStateFlow()
val appStatus get() = (if (connectionState.value.phase in listOf(ConnectionPhase.Running, ConnectionPhase.Stopping)) AppStatus.Running else AppStatus.Halted) to connectionState.value.mode
fun setPhase(phase: ConnectionPhase, mode: Mode, error: String? = null) { val old=mutableConnectionState.value; val since=if(phase==ConnectionPhase.Running) {if(old.phase==ConnectionPhase.Running && old.mode==mode) old.sinceNanos else System.nanoTime()} else if(phase==ConnectionPhase.Stopping) old.sinceNanos else 0L; mutableConnectionState.value = ConnectionSnapshot(phase, mode, error, since) }
fun setStatus(status: AppStatus, mode: Mode) { setPhase(if (status == AppStatus.Running) ConnectionPhase.Running else ConnectionPhase.Stopped, mode) }
