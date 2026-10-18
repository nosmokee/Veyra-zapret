package io.github.dovecoteescapee.byedpi
import io.github.dovecoteescapee.byedpi.core.*
import io.github.dovecoteescapee.byedpi.services.*
import io.github.dovecoteescapee.byedpi.data.Mode
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
class OwnedNativeWorkerTest {
 @Test fun stopWaitsForExitAndCanRestart() = runBlocking {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  var release = CountDownLatch(1); val stops = AtomicInteger(0)
  val backend = object : NativeBackend {
   override suspend fun prepare() = 42
   override suspend fun run(fd: Int): Int { check(release.await(3, TimeUnit.SECONDS)); return 0 }
   override suspend fun requestStop(): Int { stops.incrementAndGet(); release.countDown(); return 0 }
  }
  val worker = OwnedNativeWorker(scope, backend)
  try { repeat(12) { release = CountDownLatch(1); worker.start { }; assertTrue(worker.active); worker.stop(); assertFalse(worker.active); worker.stop() }; assertEquals(12, stops.get()) }
  finally { release.countDown(); scope.cancel() }
 }
 @Test fun unexpectedExitCallbackCanStopWithoutSelfJoin() = runBlocking {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default); val exited = CompletableDeferred<Int>()
  val worker = OwnedNativeWorker(scope, object : NativeBackend {
   override suspend fun prepare() = 42
   override suspend fun run(fd: Int) = 17
   override suspend fun requestStop() = 0
  })
  try { worker.start { code -> worker.stop(); exited.complete(code) }; assertEquals(17, withTimeout(2000) { exited.await() }); assertFalse(worker.active) }
  finally { scope.cancel() }
 }
 @Test fun failedPrepareIsNotActive() = runBlocking {
  val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  val worker = OwnedNativeWorker(scope, object : NativeBackend {
   override suspend fun prepare() = -1
   override suspend fun run(fd: Int) = error("must not run")
   override suspend fun requestStop() = error("must not stop")
  })
  try { try { worker.start { }; fail("prepare must fail") } catch (_: IllegalStateException) { }; assertFalse(worker.active); worker.stop() }
  finally { scope.cancel() }
 }
 @Test fun stateFlowPublishesStopWithoutActivityRestart() = runBlocking {
  setPhase(ConnectionPhase.Stopped, Mode.VPN)
  val phases = mutableListOf<ConnectionPhase>()
  val observer = launch(Dispatchers.Unconfined) { connectionState.collect { phases.add(it.phase) } }
  try {
   setPhase(ConnectionPhase.Starting, Mode.VPN); assertTrue(connectionState.value.busy)
   setPhase(ConnectionPhase.Running, Mode.VPN); setPhase(ConnectionPhase.Stopping, Mode.VPN)
   assertTrue(connectionState.value.busy)
   setPhase(ConnectionPhase.Stopped, Mode.VPN); assertFalse(connectionState.value.busy)
   assertEquals(listOf(ConnectionPhase.Stopped, ConnectionPhase.Starting, ConnectionPhase.Running, ConnectionPhase.Stopping, ConnectionPhase.Stopped), phases)
  } finally { observer.cancel() }
 }
}
