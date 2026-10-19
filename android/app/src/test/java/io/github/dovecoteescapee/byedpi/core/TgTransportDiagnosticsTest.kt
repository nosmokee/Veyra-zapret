package io.github.dovecoteescapee.byedpi.core
import org.junit.Test
import org.junit.Assert.*
class TgTransportDiagnosticsTest {
 private val dd=0xdddddddd.toInt()
 private fun int(n:Int)=ByteArray(4).also{TgWire.put(it,0,n)}
 private fun frame(body:ByteArray)=int(body.size)+body
 @Test fun allowedCodesAndPadding(){val logs=mutableListOf<String>();val o=TgPacketObserver(dd){logs.add(it)};for(c in listOf(-404,-429,-444))o.accept(frame(int(c)+ByteArray(11)));assertEquals(3,logs.size);assertTrue(logs.last().contains("transport-error=-444"))}
 @Test fun everyFragmentBoundary(){val b=frame(int(-444)+ByteArray(7));for(cut in 0..b.size){val logs=mutableListOf<String>();val o=TgPacketObserver(dd){logs.add(it)};o.accept(b.copyOfRange(0,cut));o.accept(b.copyOfRange(cut,b.size));assertEquals(1,logs.size)}}
 @Test fun quickAcksAndSeveralFrames(){val logs=mutableListOf<String>();val o=TgPacketObserver(dd){logs.add(it)};o.accept(int(0x87654321.toInt())+frame(int(-444))+frame(int(-404)));assertEquals(2,logs.size)}
 @Test fun abridgedAndLongHeader(){val logs=mutableListOf<String>();val o=TgPacketObserver(0xefefefef.toInt()){logs.add(it)};o.accept(byteArrayOf(0x81.toByte(),2,3,4,1)+int(-444));o.accept(byteArrayOf(127,127,0,0)+ByteArray(508));o.accept(byteArrayOf(1)+int(-404));assertEquals(2,logs.size)}
 @Test fun noPayloadOrUnlistedCodeExported(){val logs=mutableListOf<String>();val o=TgPacketObserver(dd){logs.add(it)};o.accept(frame(int(-123456)));o.accept(frame(int(-444)+ByteArray(1024){65}));o.accept(frame("private chat data never export".toByteArray()));assertTrue(logs.isEmpty())}
 @Test fun diagnosticCannotMutateTraffic(){val o=TgPacketObserver(dd){};val b=frame(int(-444));val copy=b.clone();o.accept(b);assertArrayEquals(copy,b);TgPacketObserver(dd){throw IllegalStateException("synthetic")}.accept(b);assertArrayEquals(copy,b)}
 @Test fun invalidLengthAndEventLimit(){val logs=mutableListOf<String>();val o=TgPacketObserver(dd){logs.add(it)};repeat(50){o.accept(frame(int(-444)))};assertEquals(8,logs.size);val bad=mutableListOf<String>();val q=TgPacketObserver(dd){bad.add(it)};q.accept(int(TgWire.MAX+1));q.accept(frame(int(-444)));assertEquals(listOf("framing-invalid-length=${TgWire.MAX+1}"),bad)}
 @Test fun safeExceptionMetadata(){assertEquals("TgSocksException stage=connect version=5 reply=4",TgDiagnostics.describe(TgSocksException("connect",5,4)));assertEquals("TgWsCloseException status=1008",TgDiagnostics.describe(TgWsCloseException(1008)));assertEquals("IOException",TgDiagnostics.describe(java.io.IOException("private text")))}
 @Test fun allowedCacheRetainedAndForeignDiscarded(){val good="venus.web.telegram.org" to "venus.web.telegram.org";assertTrue(good in TgWire.candidates(2,true,good));for(c in listOf("foreign.example" to "foreign.example","kws4.web.telegram.org" to "kws4.web.telegram.org")){assertFalse(c in TgWire.candidates(2,false,c))}}
 @Test fun recoveryRestoresWorkingEdgeForBothDc2And4(){for(dc in listOf(2,4))for(media in listOf(true,false)){val main="kws$dc.web.telegram.org";val c=TgWire.candidates(dc,media);assertEquals(main to "149.154.167.220",c[0]);assertEquals(main to main,c[1]);assertEquals(c.size,c.distinct().size);assertTrue(TgWire.hosts(dc,media).all{h->h to h in c})}}
}
