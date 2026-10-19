package io.github.dovecoteescapee.byedpi.core
import java.net.ServerSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicLong
/** Real loopback listener for owner/endpoint UI tests, NOT a Flowseal protocol test.
 * CPython's Android JNI cannot run inside Robolectric's Linux JVM.
 */
object TestTelegramBackend {
 fun install(){TelegramRuntime.backendFactory={_,key->object:TelegramEngine{
  private var socket:ServerSocket?=null
  override val running get()=socket?.isClosed==false
  override val port get()=socket?.localPort?:0
  override val error=""
  override val up=AtomicLong();override val down=AtomicLong();override val sessions=AtomicLong()
  override val link get()="tg://proxy?server=127.0.0.1&port=$port&secret=dd"+key.joinToString(""){"%02x".format(it.toInt() and 255)}
  override fun start(preferredPort:Int,allowPortFallback:Boolean){socket=try{ServerSocket(preferredPort,1,InetAddress.getByName("127.0.0.1"))}catch(e:java.net.BindException){if(!allowPortFallback)throw java.net.BindException("Local port $preferredPort is busy");ServerSocket(0,1,InetAddress.getByName("127.0.0.1"))}}
  override fun diagnostics()="Test-only loopback backend; no protocol test"
  override fun close(){socket?.close();socket=null}
 }}}
 fun reset(){TelegramRuntime.backendFactory={context,key->FlowsealBridge(context,key)}}
}
