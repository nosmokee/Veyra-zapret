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

/** Local MTProxy, official Telegram TLS/WSS only. No account/password or relay pool. */
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
        require(kotlin.math.abs(dc) in 1..5 && protocol in listOf(0xeeeeeeee.toInt(),0xdddddddd.toInt(),0xefefefef.toInt())) { "Invalid production DC/protocol" }
        return Hello(dc,protocol)
    }
    fun relay(dc: Int, protocol: Int): ByteArray {
        var h: ByteArray
        do { h=random(64) } while (h[0] == 0xef.toByte() || le(h,4)==0 || le(h) in listOf(0x44414548,0x54534f50,0x20544547,0xeeeeeeee.toInt(),0xdddddddd.toInt(),0x02010316))
        val p=h.clone();put(p,56,protocol);p[60]=dc.toByte();p[61]=(dc shr 8).toByte()
        val encrypted=TgCtr(h.copyOfRange(8,40),h.copyOfRange(40,56)).apply(p)
        encrypted.copyInto(h,56,56,64);return h
    }
    fun hosts(dc: Int, media: Boolean): List<String> {
        require(dc in 1..5)
        val alias=listOf("","pluto","venus","aurora","vesta","flora")[dc]+".web.telegram.org"
        val base="kws$dc.web.telegram.org";val file="kws$dc-1.web.telegram.org"
        return if(media) listOf(file,base,alias) else listOf(base,file,alias)
    }
    fun addresses(dc: Int,host: String) = if(dc==2 || dc==4) listOf("149.154.167.220",host) else listOf(host)
}

class TgCtr(key: ByteArray, iv: ByteArray) {
    private val cipher=Cipher.getInstance("AES/ECB/NoPadding").apply { init(Cipher.ENCRYPT_MODE,SecretKeySpec(key,"AES")) }
    private val counter=iv.clone().also { require(it.size==16) }
    private var block=ByteArray(16);private var pos=16
    fun apply(input: ByteArray): ByteArray = ByteArray(input.size) { i ->
        if(pos==16) { block=cipher.doFinal(counter);for(j in 15 downTo 0) { counter[j]=(counter[j]+1).toByte();if(counter[j]!=0.toByte()) break };pos=0 }
        (input[i].toInt() xor block[pos++].toInt()).toByte()
    }
}
interface TgSocket:Closeable { fun send(data: ByteArray);fun receive(): ByteArray?;fun ping() {} }

class TgWebSocket private constructor(private val socket: Socket):TgSocket {
    private val input=socket.getInputStream();private val output=socket.getOutputStream();private val writing=Any()
    companion object {
        fun connect(host: String,address: String,proxyPort: Int?):TgSocket {
            val raw=Socket()
            try {
                raw.tcpNoDelay=true;raw.keepAlive=true;raw.soTimeout=7000
                raw.connect(InetSocketAddress(if(proxyPort==null) address else "127.0.0.1",proxyPort ?: 443),5000)
                if(proxyPort!=null) {
                    val o=raw.getOutputStream();val i=raw.getInputStream();o.write(byteArrayOf(5,1,0));check(TgWire.exact(i,2).contentEquals(byteArrayOf(5,0)))
                    val name=address.toByteArray(Charsets.US_ASCII);o.write(byteArrayOf(5,1,0,3,name.size.toByte())+name+byteArrayOf(1,0xbb.toByte()))
                    val reply=TgWire.exact(i,4);check(reply[0]==5.toByte()&&reply[1]==0.toByte()) { "SOCKS CONNECT failed" }
                    val n=when(reply[3].toInt()) {1->4;4->16;3->TgWire.exact(i,1)[0].toInt() and 255;else->error("SOCKS address")};TgWire.exact(i,n+2)
                }
                val tls=(SSLSocketFactory.getDefault() as SSLSocketFactory).createSocket(raw,host,443,true) as SSLSocket
                var endpointChecked=false
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
        val mask=TgWire.random(4);packet.write(mask);for(n in b.indices)packet.write(b[n].toInt() xor mask[n%4].toInt());output.write(packet.toByteArray());output.flush()
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
    private val pool=ThreadPoolExecutor(0,48,30,TimeUnit.SECONDS,SynchronousQueue<Runnable>(),{ r->Thread(r,"Veyra-TG").apply {isDaemon=true} })
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
        pool.execute {try{while(live.get()){val client=server.accept();if(!live.get()||clients.size>=16){client.close();continue};clients.add(client);try{pool.execute {serve(client)}}catch(_:RejectedExecutionException){clients.remove(client);client.close()}}}catch(e:Exception){if(live.get()){error="Telegram listener: ${e.javaClass.simpleName}";close()}}}
        timer.scheduleWithFixedDelay({for(ws in remotes)try{ws.ping()}catch(_:Exception){runCatching{ws.close()}}},25,25,TimeUnit.SECONDS)
    }
    private fun serve(client:Socket) {
        var remote:TgSocket?=null
        try {
            client.tcpNoDelay=true;client.soTimeout=8000;val input=client.getInputStream();val hello=TgWire.exact(input,64);val info=TgWire.decode(hello,secret);client.soTimeout=0
            val dc=kotlin.math.abs(info.dc);val relay=TgWire.relay(info.dc,info.protocol);val ws=dial(dc,info.dc<0);remote=ws
            if(!live.get())return
            remotes.add(ws);ws.send(relay);sessions.incrementAndGet();error=""
            val cr=TgCtr(TgWire.hash(hello.copyOfRange(8,40),secret),hello.copyOfRange(40,56));cr.apply(ByteArray(64))
            val reverse=hello.copyOfRange(8,56).reversedArray();val cw=TgCtr(TgWire.hash(reverse.copyOfRange(0,32),secret),reverse.copyOfRange(32,48))
            val rw=TgCtr(relay.copyOfRange(8,40),relay.copyOfRange(40,56));rw.apply(ByteArray(64))
            val rev=relay.copyOfRange(8,56).reversedArray();val rr=TgCtr(rev.copyOfRange(0,32),rev.copyOfRange(32,48))
            pool.execute {try{while(live.get()){val b=ws.receive() ?: break;val encoded=cw.apply(rr.apply(b));client.getOutputStream().write(encoded);down.addAndGet(b.size.toLong())}}catch(e:Exception){if(live.get()&&!client.isClosed){error="Telegram receive: ${e.javaClass.simpleName}";report(error)}}finally{runCatching{client.close()};runCatching{ws.close()}}}
            while(live.get()) {
                var head=cr.apply(TgWire.exact(input,if(info.protocol==0xefefefef.toInt())1 else 4))
                val n=if(info.protocol==0xefefefef.toInt()) {val first=head[0].toInt() and 255;if(first==127||first==255){val tail=cr.apply(TgWire.exact(input,3));head+=tail;((tail[0].toInt() and 255) or ((tail[1].toInt() and 255) shl 8) or ((tail[2].toInt() and 255) shl 16))*4}else(first and 127)*4} else TgWire.le(head) and 0x7fffffff
                check(n in 1..TgWire.MAX);val b=head+cr.apply(TgWire.exact(input,n));ws.send(rw.apply(b));up.addAndGet(b.size.toLong())
            }
        }catch(e:Exception){if(live.get()&&!client.isClosed && e !is EOFException){error="Telegram: ${e.javaClass.simpleName}";report(error)}}finally{clients.remove(client);runCatching{client.close()};remote?.let {remotes.remove(it);runCatching{it.close()}}}
    }
    @Synchronized override fun close() {
        live.set(false);runCatching{listener?.close()};listener=null
        for(c in clients)runCatching{c.close()};for(w in remotes)runCatching{w.close()}
        timer.shutdownNow();pool.shutdownNow()
    }
}
