package io.github.dovecoteescapee.byedpi.core
import org.junit.Test
import org.junit.Assert.*
import java.net.*
import java.io.*
import java.util.concurrent.*

/** No accounts. Synthetic media bytes are not a real Telegram video download. */
class TelegramRegressionTest {
 private class Echo:TgSocket {
  var read:TgCtr?=null;var write:TgCtr?=null
  val q=LinkedBlockingQueue<ByteArray>();@Volatile var closed=false
  override fun send(b:ByteArray){if(read==null){read=TgCtr(b.copyOfRange(8,40),b.copyOfRange(40,56));read!!.apply(ByteArray(64));val r=b.copyOfRange(8,56).reversedArray();write=TgCtr(r.copyOfRange(0,32),r.copyOfRange(32,48))}else q.put(write!!.apply(read!!.apply(b)))}
  override fun receive():ByteArray? {while(!closed){val b=q.poll(100,TimeUnit.MILLISECONDS);if(b!=null)return b};return null}
  override fun close(){closed=true}
 }
 private data class Client(val s:Socket,val tx:TgCtr,val rx:TgCtr):Closeable {override fun close()=s.close()}
 private fun client(port:Int,key:ByteArray,dc:Int=2):Client {
  val s=Socket("127.0.0.1",port);s.soTimeout=10000
  val raw=TgWire.random(64);TgWire.put(raw,56,0xdddddddd.toInt());raw[60]=dc.toByte();raw[61]=(dc shr 8).toByte()
  val tx=TgCtr(TgWire.hash(raw.copyOfRange(8,40),key),raw.copyOfRange(40,56));val h=raw.clone();tx.apply(raw).copyInto(h,56,56,64)
  val r=h.copyOfRange(8,56).reversedArray();val rx=TgCtr(TgWire.hash(r.copyOfRange(0,32),key),r.copyOfRange(32,48));s.getOutputStream().write(h);return Client(s,tx,rx)
 }
 private fun echo(c:Client,n:Int){val data=TgWire.random(n);val packet=ByteArray(4).also{TgWire.put(it,0,n)}+data
  // Write on another thread: a many-MB full duplex echo must not deadlock on TCP buffers.
  val t=Thread {c.s.getOutputStream().write(c.tx.apply(packet))};t.start()
  assertArrayEquals(packet,c.rx.apply(TgWire.exact(c.s.getInputStream(),packet.size)));t.join(10000);assertFalse(t.isAlive)
 }
 @Test fun specialDcSignedIdentityPreserved(){for(dc in listOf(203,-203)){val key=TgWire.random(16);TelegramBridge(key,{id,media->assertEquals(203,id);assertEquals(dc<0,media);Echo()}).use {b->b.start(0);client(b.port,key,dc).use{echo(it,64)}}};assertEquals(TgWire.hosts(2,true),TgWire.hosts(203,true))}
 @Test fun thirtyTwoConcurrentSessionsAndMedia(){val key=TgWire.random(16);val list=mutableListOf<Client>();TelegramBridge(key,{_,_->Echo()}).use {b->b.start(0);try{repeat(32){val c=client(b.port,key,if(it%2==0)2 else -2);list+=c;echo(c,64)};echo(list.last(),4*1024*1024);assertEquals(32,b.sessions.get().toInt())}finally{list.forEach{it.close()}}}}
 @Test fun bulkCtrMatchesArbitrarilySplitStream(){val key=TgWire.random(32);val iv=TgWire.random(16);val data=TgWire.random(2*1024*1024+7);val expected=TgCtr(key,iv).apply(data);val c=TgCtr(key,iv);val out=ByteArrayOutputStream();var at=0;while(at<data.size){val n=minOf(777,data.size-at);out.write(c.apply(data.copyOfRange(at,at+n)));at+=n};assertArrayEquals(expected,out.toByteArray())}
 private fun ws(s:Socket):TgSocket {val ctor=TgWebSocket::class.java.getDeclaredConstructor(Socket::class.java);ctor.isAccessible=true;return ctor.newInstance(s)}
 @Test fun actualWebSocketFragmentedMediaAndPing(){ServerSocket(0,1,InetAddress.getLoopbackAddress()).use {server->Socket("127.0.0.1",server.localPort).use {client->val peer=server.accept();client.soTimeout=10000;peer.soTimeout=10000
   ws(client).use {web->val data=TgWire.random(2*1024*1024);val failure=java.util.concurrent.atomic.AtomicReference<Throwable?>()
    val t=Thread{try{val o=peer.getOutputStream();fun frame(op:Int,b:ByteArray){o.write(op);o.write(127);for(i in 7 downTo 0)o.write((b.size.toLong() ushr(i*8)).toInt());o.write(b)};frame(2,data.copyOfRange(0,900000));o.write(byteArrayOf(0x89.toByte(),1,42));frame(0x80,data.copyOfRange(900000,data.size));o.flush();val h=TgWire.exact(peer.getInputStream(),2);check(h[0]==0x8a.toByte()&&h[1]==0x81.toByte());val mask=TgWire.exact(peer.getInputStream(),4);check((TgWire.exact(peer.getInputStream(),1)[0].toInt() xor mask[0].toInt()).toByte()==42.toByte())}catch(e:Throwable){failure.set(e)}};t.start();assertArrayEquals(data,web.receive());t.join(10000);assertFalse(t.isAlive);failure.get()?.let{throw it}
   };peer.close()
 }}}
}
