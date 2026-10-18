package io.github.dovecoteescapee.byedpi.core
import org.junit.Test
import org.junit.Assert.*
import java.net.Socket
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit

class TelegramBridgeTest {
    private fun hex(s:String)=s.chunked(2).map {it.toInt(16).toByte()}.toByteArray()
    @Test fun ctrNistVector() {
        val c=TgCtr(hex("2b7e151628aed2a6abf7158809cf4f3c"),hex("f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff"))
        assertArrayEquals(hex("874d6191b620e3261bef6864990db6ce"),c.apply(hex("6bc1bee22e409f96e93d7e117393172a")))
    }
    @Test fun ctrStreamingChunks() {
        val key=TgWire.random(32);val iv=TgWire.random(16);val b=TgWire.random(256)
        val c=TgCtr(key,iv);assertArrayEquals(TgCtr(key,iv).apply(b),c.apply(b.copyOfRange(0,7))+c.apply(b.copyOfRange(7,256)))
    }
    @Test fun base64Vectors() {assertEquals("Zg==",TgWebSocket.base64("f".toByteArray()));assertEquals("Zm8=",TgWebSocket.base64("fo".toByteArray()));assertEquals("Zm9v",TgWebSocket.base64("foo".toByteArray()))}
    @Test fun routesPreserveDcAndUseOfficialHosts() {
        for(dc in 1..5){assertTrue(TgWire.hosts(dc,false)[0].contains("kws$dc."));assertTrue(TgWire.hosts(dc,true)[0].contains("kws$dc-1."));assertTrue(TgWire.hosts(dc,false).all {it.endsWith(".web.telegram.org")})}
        assertEquals("149.154.167.220",TgWire.addresses(2,"kws2.web.telegram.org")[0]);assertEquals(listOf("kws3.web.telegram.org"),TgWire.addresses(3,"kws3.web.telegram.org"))
    }
    @Test fun rejectUnknownDc() {try{TgWire.hosts(9,false);fail()}catch(_:IllegalArgumentException){}}
    @Test fun rejectUnknownHello() {try{TgWire.decode(ByteArray(64),ByteArray(16));fail()}catch(_:IllegalArgumentException){}}
    private class Echo:TgSocket {
        private var relay:ByteArray?=null;private var read:TgCtr?=null;private var write:TgCtr?=null
        private val queue=LinkedBlockingQueue<ByteArray>();@Volatile private var closed=false
        override fun send(data:ByteArray) {
            if(relay==null){relay=data;read=TgCtr(data.copyOfRange(8,40),data.copyOfRange(40,56));read!!.apply(ByteArray(64));val r=data.copyOfRange(8,56).reversedArray();write=TgCtr(r.copyOfRange(0,32),r.copyOfRange(32,48))}
            else queue.put(write!!.apply(read!!.apply(data)))
        }
        override fun receive():ByteArray? {while(!closed){val b=queue.poll(100,TimeUnit.MILLISECONDS);if(b!=null)return b};return null}
        override fun close(){closed=true}
    }
    private fun roundtrip(proto:Int,long:Boolean=false) {
        val secret=TgWire.random(16);val echo=Echo();TelegramBridge(secret,{dc,media->assertEquals(2,dc);assertFalse(media);echo}).use {bridge->
            bridge.start(0);val port=bridge.port
            Socket("127.0.0.1",port).use {socket->
                socket.soTimeout=3000
                val raw=TgWire.random(64);TgWire.put(raw,56,proto);raw[60]=2;raw[61]=0
                val tx=TgCtr(TgWire.hash(raw.copyOfRange(8,40),secret),raw.copyOfRange(40,56));val h=raw.clone();val enc=tx.apply(raw);enc.copyInto(h,56,56,64)
                socket.getOutputStream().write(h);val info=TgWire.decode(h,secret);assertEquals(2,info.dc);assertEquals(proto,info.protocol)
                val reverse=h.copyOfRange(8,56).reversedArray();val rx=TgCtr(TgWire.hash(reverse.copyOfRange(0,32),secret),reverse.copyOfRange(32,48))
                repeat(5) {
                    val head=if(proto==0xefefefef.toInt())if(long)byteArrayOf(127,4,0,0)else byteArrayOf(4) else ByteArray(4).also{TgWire.put(it,0,16)}
                    val packet=head+TgWire.random(16);socket.getOutputStream().write(tx.apply(packet));assertArrayEquals(packet,rx.apply(TgWire.exact(socket.getInputStream(),packet.size)))
                }
                val deadline=System.nanoTime()+1_000_000_000L;while((bridge.up.get()==0L||bridge.down.get()==0L)&&System.nanoTime()<deadline)Thread.sleep(1);assertTrue(bridge.sessions.get()>0);assertTrue(bridge.up.get()>0);assertTrue(bridge.down.get()>0)
            }
            bridge.close();assertFalse(bridge.running)
            // Closing the listener wakes its accept thread asynchronously on Linux/JVM.
            val until=System.nanoTime()+2_000_000_000L
            while(true) {
                try {java.net.ServerSocket().use { check -> check.reuseAddress=true;check.bind(java.net.InetSocketAddress("127.0.0.1",port)) };break}
                catch(e:java.net.BindException) {if(System.nanoTime()>until)throw e;Thread.sleep(20)}
            }
        }
    }
    @Test fun intermediateRoundtrip()=roundtrip(0xeeeeeeee.toInt())
    @Test fun paddedRoundtrip()=roundtrip(0xdddddddd.toInt())
    @Test fun abridgedRoundtrip()=roundtrip(0xefefefef.toInt())
    @Test fun abridgedLongRoundtrip()=roundtrip(0xefefefef.toInt(),true)
    @Test fun wrongKeyRejected() {
        val secret=TgWire.random(16);val raw=TgWire.random(64);TgWire.put(raw,56,0xdddddddd.toInt());raw[60]=2;raw[61]=0
        val enc=TgCtr(TgWire.hash(raw.copyOfRange(8,40),secret),raw.copyOfRange(40,56)).apply(raw);enc.copyInto(raw,56,56,64)
        try{TgWire.decode(raw,TgWire.random(16));fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun keyLengthChecked() {TelegramBridge(ByteArray(3),{_,_->Echo()}).use {try{it.start(0);fail()}catch(_:IllegalArgumentException){}}}
}
