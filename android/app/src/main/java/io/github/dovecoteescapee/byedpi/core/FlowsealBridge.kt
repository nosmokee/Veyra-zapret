package io.github.dovecoteescapee.byedpi.core

import android.content.Context
import com.chaquo.python.Python
import com.chaquo.python.PyObject
import com.chaquo.python.android.AndroidPlatform
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong

/** Lifecycle-only adapter. MTProto, WSS and H2 remain the upstream Python engine. */
class FlowsealBridge(private val context:Context,private val secret:ByteArray):TelegramEngine {
    @Volatile private var engine:PyObject?=null
    @Volatile override var running=false;private set
    @Volatile override var port=0;private set
    @Volatile override var error="";private set
    override val up=AtomicLong();override val down=AtomicLong();override val sessions=AtomicLong()
    private var monitor:Thread?=null
    override val link:String get()="tg://proxy?server=127.0.0.1&port=$port&secret=dd"+secret.joinToString(""){"%02x".format(it.toInt() and 255)}
    override fun start(preferredPort:Int,allowPortFallback:Boolean) {
        require(secret.size==16);require(preferredPort in 1..65535)
        synchronized(Python::class.java) {if(!Python.isStarted())Python.start(AndroidPlatform(context.applicationContext))}
        val module=Python.getInstance().getModule("veyra_engine")
        fun open(requested:Int):PyObject {
            val value=module.callAttr("Engine",secret.joinToString(""){"%02x".format(it.toInt() and 255)},requested)
            try {value.callAttr("start");return value} catch(e:Exception){runCatching{value.callAttr("close")};throw e}
        }
        // Fallback is permitted only for a first installation; saved endpoints never change.
        val value=try {open(preferredPort)} catch(e:Exception) {
            if(!allowPortFallback)throw e
            val busy=runCatching {java.net.ServerSocket(preferredPort,1,java.net.InetAddress.getByName("127.0.0.1")).use{false}}.getOrDefault(true)
            if(!busy)throw e
            open(0)
        }
        engine=value;port=value.get("port")!!.toInt();running=true
        TgDiagnostics.record("ENGINE Flowseal/tg-ws-proxy v1.11.2 CF/H2=enabled saved-endpoint=preserved")
        monitor=Thread({
            while(running && engine===value) {
                try {
                    val state=value.callAttr("snapshot").asMap()
                    fun get(key:String)=state[PyObject.fromJava(key)]!!
                    up.set(get("up").toLong());down.set(get("down").toLong());sessions.set(get("sessions").toLong())
                    if(!get("running").toBoolean()) {error=get("error").toString().ifEmpty{"Telegram engine stopped"};running=false;TgDiagnostics.record(error);break}
                    Thread.sleep(500)
                }catch(e:InterruptedException){break}
                catch(e:Exception){error="Ошибка состояния Telegram-ядра: ${e.javaClass.simpleName}";TgDiagnostics.record(error);break}
            }
        },"VeyraTelegramMetrics").apply{isDaemon=true;start()}
    }
    override fun diagnostics():String=runCatching{engine?.callAttr("diagnostics")?.toString()?:""}.getOrDefault("Telegram engine diagnostics unavailable")
    override fun close() {
        running=false;monitor?.interrupt();monitor?.join(1500);monitor=null
        val old=engine
        if(old!=null){runCatching{old.callAttr("diagnostics").toString().lineSequence().forEach{TgDiagnostics.record(it)}};old.callAttr("close")}
        engine=null
    }
}
