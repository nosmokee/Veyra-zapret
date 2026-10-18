package io.github.dovecoteescapee.byedpi.core
import java.net.Socket
/** Explicit optional network test. No login, phone, chat history or account required. */
object TelegramLive {
 @JvmStatic fun main(args:Array<String>) {
  for(dc in ((1..5).toList()+203).flatMap { listOf(it,-it) })for(proto in listOf(0xeeeeeeee.toInt(),0xdddddddd.toInt(),0xefefefef.toInt())) {
   val secret=TgWire.random(16)
   TelegramBridge(secret,{id,media->
    if(id==203) TgTcpSocket.connect203(args.firstOrNull()?.toInt()) else {
    var result:TgSocket?=null;var last:Exception?=null
    routes@ for(pair in TgWire.candidates(id,media).chunked(2)) {
     try{val attempts:List<((java.io.Closeable)->Unit)->TgSocket> = pair.map {(host,address)->{track->TgWebSocket.connect(host,address,args.firstOrNull()?.toInt(),track)}};result=TgDialRace.first(attempts);println("TLS verified: DC$id media=$media raced official routes");break@routes}catch(e:Exception){last=e}
    };result ?: throw java.io.IOException("No Telegram route",last)
    }
   },{println(it)}).use {bridge->
    bridge.start(0)
    Socket("127.0.0.1",bridge.port).use {s->
     s.soTimeout=30000;val raw=TgWire.random(64);TgWire.put(raw,56,proto);raw[60]=dc.toByte();raw[61]=(dc shr 8).toByte()
     val tx=TgCtr(TgWire.hash(raw.copyOfRange(8,40),secret),raw.copyOfRange(40,56));val h=raw.clone();tx.apply(raw).copyInto(h,56,56,64)
     val rev=h.copyOfRange(8,56).reversedArray();val rx=TgCtr(TgWire.hash(rev.copyOfRange(0,32),secret),rev.copyOfRange(32,48));s.getOutputStream().write(h)
     val nonce=TgWire.random(16);val body=ByteArray(4).also{TgWire.put(it,0,0xbe7e8ef1.toInt())}+nonce
     val id=(System.currentTimeMillis()/1000) shl 32;var packet=ByteArray(8)+ByteArray(8){(id ushr(it*8)).toByte()}+ByteArray(4).also{TgWire.put(it,0,body.size)}+body
     if(proto==0xdddddddd.toInt())packet+=TgWire.random(8)
     val head=if(proto==0xefefefef.toInt())byteArrayOf((packet.size/4).toByte())else ByteArray(4).also{TgWire.put(it,0,packet.size)}
     s.getOutputStream().write(tx.apply(head+packet));val prefix=rx.apply(TgWire.exact(s.getInputStream(),if(proto==0xefefefef.toInt())1 else 4))
     val size=if(proto==0xefefefef.toInt())(prefix[0].toInt() and 255)*4 else TgWire.le(prefix)
     check(size in 40..TgWire.MAX) {"DC$dc response length $size"};val response=rx.apply(TgWire.exact(s.getInputStream(),size));check(TgWire.le(response,20)==0x05162463) {"DC$dc constructor ${TgWire.le(response,20).toUInt().toString(16)}"};check(response.copyOfRange(24,40).contentEquals(nonce)) {"DC$dc nonce mismatch"};val until=System.nanoTime()+1_000_000_000L;while(bridge.down.get()==0L && System.nanoTime()<until)Thread.sleep(1);check(bridge.down.get()>0) {"DC$dc accounting lag"}
     println("PASS Kotlin bridge DC$dc proto=${proto.toUInt().toString(16)} resPQ nonce matched")
    }
   }
  }
 }
}
