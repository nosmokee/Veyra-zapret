package io.github.dovecoteescapee.byedpi.core

import java.io.*
import java.net.*
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.concurrent.*
import java.util.concurrent.atomic.*
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec
import javax.net.ssl.*

/** Local MTProxy. Official TLS/WSS; CDN203 uses its own MTProto TCP endpoint. */
object TgWire {
    const val MAX = 8 * 1024 * 1024
    val random = SecureRandom()
    fun random(n: Int) = ByteArray(n).also { random.nextBytes(it) }
    fun exact(input: InputStream, n: Int): ByteArray {
        require(n in 0..MAX)
        val b = ByteArray(n); var at = 0
        while (at < n) { val k = input.read(b, at, n-at); if (k <= 0) throw EOFException(); at += k }
        return b
    }
    fun le(b: ByteArray, p: Int = 0) = (b[p].toInt() and 255) or ((b[p+1].toInt() and 255) shl 8) or ((b[p+2].toInt() and 255) shl 16) or (b[p+3].toInt() shl 24)
    fun put(b: ByteArray, p: Int, v: Int) { for (i in 0..3) b[p+i] = (v ushr (i*8)).toByte() }
    fun hash(key: ByteArray, secret: ByteArray) = MessageDigest.getInstance("SHA-256").digest(key + secret)
    data class Hello(val dc: Int, val protocol: Int)
    fun decode(h: ByteArray, secret: ByteArray): Hello {
        require(h.size == 64 && secret.size == 16)
        val p = TgCtr(hash(h.copyOfRange(8,40),secret),h.copyOfRange(40,56)).apply(h)
        val dc = ((p[60].toInt() and 255) or (p[61].toInt() shl 8)).toShort().toInt()
        val protocol = le(p,56)
        require((kotlin.math.abs(dc) in 1..5 || kotlin.math.abs(dc)==203) && protocol in listOf(0xeeeeeeee.toInt(),0xdddddddd.toInt(),0xefefefef.toInt())) { "Invalid production DC/protocol" }
        return Hello(dc,protocol)
    }
    fun relay(dc: Int, protocol: Int): ByteArray {
        var h: ByteArray
        do { h=random(64) } while (h[0] == 0xef.toByte() || le(h,4)==0 || le(h) in listOf(0x44414548,0x54534f50,0x20544547,0xeeeeeeee.toInt(),0xdddddddd.toInt(),0x02010316))
        val p=h.clone();put(p,56,protocol);p[60]=dc.toByte();p[61]=(dc shr 8).toByte()
        val encrypted=TgCtr(h.copyOfRange(8,40),h.copyOfRange(40,56)).apply(p)
        encrypted.copyInto(h,56,56,64);return h
    }
    fun hosts(id: Int, media: Boolean): List<String> {
        val dc=id
        require(dc in 1..5)
        val alias=listOf("","pluto","venus","aurora","vesta","flora")[dc]+".web.telegram.org"
        val base="kws$dc.web.telegram.org";val file="kws$dc-1.web.telegram.org"
        return if(media) listOf(file,base,alias) else listOf(base,file,alias)
    }
    fun candidates(dc:Int,media:Boolean,cached:Pair<String,String>?=null):List<Pair<String,String>> {
        val normal=hosts(dc,media).flatMap {h->addresses(dc,h).map {a->h to a}}
        val main="kws$dc.web.telegram.org"
        val preferred=if(dc==2||dc==4)listOf(main to "149.154.167.220",main to main) else emptyList()
        return (preferred+listOfNotNull(cached)+normal).distinct()
    }
    fun addresses(dc: Int,host: String) = if(dc==2 || dc==4) listOf(host,"149.154.167.220") else listOf(host)
}

/** Races at most a pair of connection attempts, not MTProto/user packets. */
object TgDialRace {
    fun first(attempts:List<((Closeable)->Unit)->TgSocket>,running:()->Boolean={true}):TgSocket {
        val lock=Object();val tracked=attempts.map {mutableListOf<Closeable>()}
        var winner:TgSocket?=null;var winnerIndex=-1;var done=false;var left=attempts.size;var last:Exception?=null
        fun close(c:Closeable){try{c.close()}catch(_:Exception){}}
        synchronized(lock) {
            attempts.forEachIndexed {index,attempt->
                Thread {
                    var result:TgSocket?=null
                    try {
                        result=attempt {c->synchronized(lock){if(done&&winnerIndex!=index){close(c);throw IOException("Dial cancelled")};tracked[index].add(c);Unit}}
                        synchronized(lock){if(!done&&running()){winner=result;winnerIndex=index;done=true;result=null};lock.notifyAll()}
                    }catch(e:Exception){synchronized(lock){last=e}}
                    finally{result?.let{close(it)};synchronized(lock){left--;if(winnerIndex!=index)tracked[index].forEach{close(it)};lock.notifyAll()}}
                }.apply{isDaemon=true;name="Veyra-TgDial";start()}
            }
            while(!done&&left>0&&running())lock.wait(100)
            if(!running())winnerIndex=-1
            done=true
            tracked.forEachIndexed {index,list->if(index!=winnerIndex)list.forEach{close(it)}}
            if(winnerIndex>=0)return winner!!
            winner?.let{close(it)}
            throw IOException(if(running())"No WSS route connected" else "Stopped",last)
        }
    }
}

/** Provider-backed streaming CTR: one bulk operation, not one JCE call per 16 bytes. */
class TgCtr(key: ByteArray, iv: ByteArray) {
    private val cipher=Cipher.getInstance("AES/CTR/NoPadding").apply {
        require(iv.size==16)
        init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES"),javax.crypto.spec.IvParameterSpec(iv))
    }
    fun apply(input: ByteArray): ByteArray = if(input.isEmpty()) byteArrayOf() else cipher.update(input)
}
interface TgSocket:Closeable { fun send(data: ByteArray);fun receive(): ByteArray?;fun ping() {} }

/** Bounded, payload/key-free Telegram events, independent of noisy native logcat. */
object TgDiagnostics {
    private val events=java.util.ArrayDeque<String>()
    @Synchronized fun record(text:String) {while(events.size>=256)events.removeFirst();events.addLast("${System.currentTimeMillis()} $text")}
    @Synchronized fun snapshot():String = events.joinToString("\n").ifEmpty {"Telegram events: none in this process"}
    fun describe(error:Throwable):String {val parts=mutableListOf<String>();var e:Throwable?=error;repeat(6){val current=e ?: return parts.joinToString(" <- ");parts+=current.javaClass.simpleName;e=current.cause};return parts.joinToString(" <- ")}
}

/** Raw obfuscated MTProto is end-to-end authenticated by Telegram, NOT TLS/WSS.
 * Never route CDN203 to DC2: a successful resPQ alone cannot establish DC identity. */
class TgTcpSocket private constructor(private val socket:Socket):TgSocket {
    private val input=socket.getInputStream();private val output=socket.getOutputStream()
    companion object {
        const val CDN203="91.105.192.100"
        fun connect203(proxyPort:Int?):TgSocket = connect(CDN203,443,proxyPort)
        internal fun connect(address:String,port:Int,proxyPort:Int?):TgSocket {
            val raw=Socket()
            try {
                raw.tcpNoDelay=true;raw.keepAlive=true;raw.soTimeout=7000
                raw.connect(InetSocketAddress(if(proxyPort==null)address else "127.0.0.1",proxyPort ?: port),5000)
                if(proxyPort!=null) {
                    val o=raw.getOutputStream();val i=raw.getInputStream();o.write(byteArrayOf(5,1,0));check(TgWire.exact(i,2).contentEquals(byteArrayOf(5,0)))
                    val name=address.toByteArray(Charsets.US_ASCII);o.write(byteArrayOf(5,1,0,3,name.size.toByte())+name+byteArrayOf((port ushr 8).toByte(),port.toByte()))
                    val reply=TgWire.exact(i,4);check(reply[0]==5.toByte()&&reply[1]==0.toByte()) {"SOCKS CONNECT failed"}
                    val n=when(reply[3].toInt()){1->4;4->16;3->TgWire.exact(i,1)[0].toInt() and 255;else->error("SOCKS address")};TgWire.exact(i,n+2)
                }
                raw.soTimeout=0;return TgTcpSocket(raw)
            }catch(e:Exception){raw.close();throw e}
        }
    }
    override fun send(data:ByteArray){output.write(data);output.flush()}
    override fun receive():ByteArray? {val b=ByteArray(65536);val n=input.read(b);return if(n<0)null else b.copyOf(n)}
    override fun close(){socket.close()}
}

class TgWebSocket private constructor(private val socket: Socket):TgSocket {
    private val input=BufferedInputStream(socket.getInputStream(),65536);private val output=socket.getOutputStream();private val writing=Any()
    companion object {
        fun connect(host: String,address: String,proxyPort: Int?,track:(Closeable)->Unit={}):TgSocket {
            val raw=Socket()
            try {
                track(raw);raw.tcpNoDelay=true;raw.keepAlive=true;raw.soTimeout=7000
                raw.connect(InetSocketAddress(if(proxyPort==null) address else "127.0.0.1",proxyPort ?: 443),5000)
                if(proxyPort!=null) {
                    val o=raw.getOutputStream();val i=raw.getInputStream();o.write(byteArrayOf(5,1,0));check(TgWire.exact(i,2).contentEquals(byteArrayOf(5,0)))
                    val name=address.toByteArray(Charsets.US_ASCII);o.write(byteArrayOf(5,1,0,3,name.size.toByte())+name+byteArrayOf(1,0xbb.toByte()))
                    val reply=TgWire.exact(i,4);check(reply[0]==5.toByte()&&reply[1]==0.toByte()) { "SOCKS CONNECT failed" }
                    val n=when(reply[3].toInt()) {1->4;4->16;3->TgWire.exact(i,1)[0].toInt() and 255;else->error("SOCKS address")};TgWire.exact(i,n+2)
                }
                val tls=(SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw,host,443,true) as SSLSocket
                track(tls);var endpointChecked=false
                try {val params=tls.sslParameters;SSLParameters::class.java.getMethod("setEndpointIdentificationAlgorithm",String::class.java).invoke(params,"HTTPS");tls.sslParameters=params;endpointChecked=true} catch (_:NoSuchMethodException) { }
                tls.startHandshake()
                if(!endpointChecked) check(HttpsURLConnection.getDefaultHostnameVerifier().verify(host,tls.session)) { "TLS hostname mismatch" }
                val key=base64(TgWire.random(16));val request="GET /apiws HTTP/1.1\r\nHost: $host\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: $key\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: binary\r\n\r\n"
                tls.getOutputStream().write(request.toByteArray(Charsets.US_ASCII));val bytes=ByteArrayOutputStream()
                while(bytes.size()<16384) {bytes.write(TgWire.exact(tls.getInputStream(),1));val b=bytes.toByteArray();if(b.size>=4 && b.takeLast(4)==listOf<Byte>(13,10,13,10)) break}
                val header=String(bytes.toByteArray(),Charsets.US_ASCII);check(header.endsWith("\r\n\r\n")) { "WS header limit" }
                val lines=header.split("\r\n");val fields=lines.drop(1).filter {it.contains(':')}.associate { it.substringBefore(':').trim().lowercase(java.util.Locale.ROOT) to it.substringAfter(':').trim() }
                val accept=base64(MessageDigest.getInstance("SHA-1").digest((key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11").toByteArray(Charsets.US_ASCII)))
                check(lines[0].startsWith("HTTP/1.1 101 ") && fields["sec-websocket-accept"]==accept && fields["upgrade"].equals("websocket",true) && fields["connection"]?.split(',')?.any {it.trim().equals("upgrade",true)}==true && fields["sec-websocket-protocol"]=="binary") { "WS upgrade rejected: ${lines[0]}" }
                tls.soTimeout=0;return TgWebSocket(tls)
            } catch(e: Exception) { raw.close();throw e }
        }
        // API21-compatible encoding; no Android dependency in the wire implementation.
        fun base64(data: ByteArray):String {
            val chars="ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";val out=StringBuilder();var p=0
            while(p<data.size) {val a=data[p++].toInt() and 255;val b=if(p<data.size) data[p++].toInt() and 255 else -1;val c=if(p<data.size) data[p++].toInt() and 255 else -1;out.append(chars[a shr 2]);out.append(chars[((a and 3) shl 4) or (if(b>=0) b shr 4 else 0)]);out.append(if(b<0) '=' else chars[((b and 15) shl 2) or (if(c>=0)c shr 6 else 0)]);out.append(if(c<0) '=' else chars[c and 63]) };return out.toString()
        }
    }
    private fun frame(op: Int,b: ByteArray) = synchronized(writing) {
        require(b.size<=TgWire.MAX);val packet=ByteArrayOutputStream();packet.write(0x80 or op)
        when {b.size<126->packet.write(0x80 or b.size);b.size<65536->{packet.write(0xfe);packet.write(b.size shr 8);packet.write(b.size)};else->{packet.write(0xff);for(n in 7 downTo 0)packet.write((b.size.toLong() ushr (8*n)).toInt())}}
        val mask=TgWire.random(4);packet.write(mask);val masked=ByteArray(b.size) { n -> (b[n].toInt() xor mask[n%4].toInt()).toByte() };packet.write(masked);output.write(packet.toByteArray());output.flush()
    }
    override fun send(data: ByteArray) { frame(2,data) }
    override fun ping() {frame(9,byteArrayOf())}
    override fun receive():ByteArray? {
        val message=ByteArrayOutputStream();var started=false
        while(true) {
            val h=TgWire.exact(input,2);val op=h[0].toInt() and 15;val fin=h[0].toInt() and 128!=0
            check(h[0].toInt() and 112==0 && h[1].toInt() and 128==0) { "WS flags" }
            var len=(h[1].toInt() and 127).toLong()
            if(len==126L) {val e=TgWire.exact(input,2);len=((e[0].toInt() and 255)*256+(e[1].toInt() and 255)).toLong()} else if(len==127L) {len=0;for(c in TgWire.exact(input,8))len=(len shl 8) or (c.toLong() and 255)}
            check(len in 0..TgWire.MAX.toLong() && (op<8 || (fin && len<=125))) { "WS frame limit" }
            val data=TgWire.exact(input,len.toInt())
            when(op) {8->{check(data.size!=1);if(data.size>=2)check((data[0].toInt() and 255)*256+(data[1].toInt() and 255)==1000) { "WS close" };return null};9->{frame(10,data);continue};10->continue}
            if(op==2 && !started)started=true else check(op==0 && started) { "WS message order" }
            check(message.size()+data.size<=TgWire.MAX);message.write(data);if(fin)return message.toByteArray()
        }
    }
    override fun close() { socket.close() }
}

class TelegramBridge(private val secret: ByteArray,private val dial:(Int,Boolean)->TgSocket,private val report:(String)->Unit={}):Closeable {
    private val live=AtomicBoolean(false)
    private val clients=java.util.Collections.newSetFromMap(ConcurrentHashMap<Socket,Boolean>());private val remotes=java.util.Collections.newSetFromMap(ConcurrentHashMap<TgSocket,Boolean>())
    private val pool=ThreadPoolExecutor(0,66,30,TimeUnit.SECONDS,SynchronousQueue<Runnable>(),{ r->Thread(r,"Veyra-TG").apply {isDaemon=true} })
    private val timer=Executors.newSingleThreadScheduledExecutor {r->Thread(r,"Veyra-TG-ping").apply{isDaemon=true}}
    private var listener:ServerSocket?=null
    val up=AtomicLong();val down=AtomicLong();val sessions=AtomicLong()
    @Volatile var error="";private set
    val port:Int get()=listener?.localPort ?: 0
    val running:Boolean get()=live.get()
    val link:String get()="tg://proxy?server=127.0.0.1&port=$port&secret=dd"+secret.joinToString("") {"%02x".format(it.toInt() and 255)}
    @Synchronized fun start(preferred:Int=1087,allowPortFallback:Boolean=true) {
        check(!live.get());require(secret.size==16)
        var server=ServerSocket().apply { reuseAddress=true }
        val releaseDeadline=System.nanoTime()+500_000_000L
        while(true) {
            try {server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"),preferred),16);break}
            catch(e:BindException) {
                server.close()
                if(!allowPortFallback && System.nanoTime()<releaseDeadline) {
                    Thread.sleep(20);server=ServerSocket().apply {reuseAddress=true};continue
                }
                if(!allowPortFallback)throw java.net.BindException("Сохранённый Telegram-порт $preferred занят. Закрой другую копию прокси и подключись снова.")
                server=ServerSocket().apply {reuseAddress=true}
                server.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"),0),16);break
            }
        }
        listener=server;live.set(true)
        pool.execute {try{while(live.get()){val client=server.accept();if(!live.get()||clients.size>=32){client.close();continue};clients.add(client);try{pool.execute {serve(client)}}catch(_:RejectedExecutionException){clients.remove(client);client.close()}}}catch(e:Exception){if(live.get()){error="Telegram listener: ${e.javaClass.simpleName}";close()}}}
        timer.scheduleWithFixedDelay({for(ws in remotes)try{ws.ping()}catch(_:Exception){runCatching{ws.close()}}},25,25,TimeUnit.SECONDS)
    }
    private fun serve(client:Socket) {
        var remote:TgSocket?=null;var sessionDc=0;var phase="hello"
        val started=System.nanoTime()
        try {
            client.tcpNoDelay=true;client.soTimeout=8000;val input=BufferedInputStream(client.getInputStream(),65536);val hello=TgWire.exact(input,64);val info=TgWire.decode(hello,secret);client.soTimeout=0
            sessionDc=info.dc;TgDiagnostics.record("CLIENT dc=${info.dc} protocol=${info.protocol.toUInt().toString(16)}");phase="connect"
            val dc=kotlin.math.abs(info.dc);val relay=TgWire.relay(info.dc,info.protocol);val ws=dial(dc,info.dc<0);remote=ws
            if(!live.get())return
            remotes.add(ws);ws.send(relay);sessions.incrementAndGet();error="";phase="upload";TgDiagnostics.record("CONNECTED dc=$sessionDc elapsedMs=${(System.nanoTime()-started)/1_000_000}")
            val cr=TgCtr(TgWire.hash(hello.copyOfRange(8,40),secret),hello.copyOfRange(40,56));cr.apply(ByteArray(64))
            val reverse=hello.copyOfRange(8,56).reversedArray();val cw=TgCtr(TgWire.hash(reverse.copyOfRange(0,32),secret),reverse.copyOfRange(32,48))
            val rw=TgCtr(relay.copyOfRange(8,40),relay.copyOfRange(40,56));rw.apply(ByteArray(64))
            val rev=relay.copyOfRange(8,56).reversedArray();val rr=TgCtr(rev.copyOfRange(0,32),rev.copyOfRange(32,48))
            pool.execute {try{while(live.get()){val b=ws.receive() ?: break;val encoded=cw.apply(rr.apply(b));client.getOutputStream().write(encoded);down.addAndGet(b.size.toLong())}}catch(e:Exception){if(live.get()&&!client.isClosed){error="Telegram receive dc=$sessionDc: ${TgDiagnostics.describe(e)}";TgDiagnostics.record(error);report(error)}}finally{runCatching{client.close()};runCatching{ws.close()}}}
            while(live.get()) {
                var head=cr.apply(TgWire.exact(input,if(info.protocol==0xefefefef.toInt())1 else 4))
                val n=if(info.protocol==0xefefefef.toInt()) {val first=head[0].toInt() and 255;if(first==127||first==255){val tail=cr.apply(TgWire.exact(input,3));head+=tail;((tail[0].toInt() and 255) or ((tail[1].toInt() and 255) shl 8) or ((tail[2].toInt() and 255) shl 16))*4}else(first and 127)*4} else TgWire.le(head) and 0x7fffffff
                check(n in 1..TgWire.MAX);val b=head+cr.apply(TgWire.exact(input,n));ws.send(rw.apply(b));up.addAndGet(b.size.toLong())
            }
        }catch(e:Exception){if(live.get()&&!client.isClosed && e !is EOFException){error="Telegram dc=$sessionDc phase=$phase: ${TgDiagnostics.describe(e)}";TgDiagnostics.record(error);report(error)}}finally{TgDiagnostics.record("END dc=$sessionDc phase=$phase elapsedMs=${(System.nanoTime()-started)/1_000_000}");clients.remove(client);runCatching{client.close()};remote?.let {remotes.remove(it);runCatching{it.close()}}}
    }
    @Synchronized override fun close() {
        live.set(false);runCatching{listener?.close()};listener=null
        for(c in clients)runCatching{c.close()};for(w in remotes)runCatching{w.close()}
        timer.shutdownNow();pool.shutdownNow()
    }
}
