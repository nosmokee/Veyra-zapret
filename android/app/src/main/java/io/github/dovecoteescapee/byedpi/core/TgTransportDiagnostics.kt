package io.github.dovecoteescapee.byedpi.core
import java.io.IOException
/** Metadata only, never messages, file contents or arbitrary exception strings. */
class TgSocksException(val stage:String,val version:Int,val reply:Int):IOException("SOCKS $stage rejected")
class TgWsCloseException(val status:Int):IOException("WebSocket closed")
/** Passive observer. Does not change or suppress any forwarded bytes. */
class TgPacketObserver(private val protocol:Int,private val event:(String)->Unit) {
 private val header=ByteArray(4);private val prefix=ByteArray(4)
 private var headAt=0;private var headNeed=if(protocol==0xefefefef.toInt())1 else 4
 private var left=0;private var size=0;private var prefixAt=0;private var enabled=true;private var emitted=0
 private fun reset(){headAt=0;headNeed=if(protocol==0xefefefef.toInt())1 else 4;left=0;size=0;prefixAt=0}
 private fun emit(s:String){if(emitted<8){emitted++;try{event(s)}catch(_:Exception){}}}
 fun accept(bytes:ByteArray) {
  if(!enabled)return
  try{var i=0
   while(i<bytes.size){
    if(left>0){val count=minOf(left,bytes.size-i)
     if(size in 4..56 && prefixAt<4){val n=minOf(4-prefixAt,count);bytes.copyInto(prefix,prefixAt,i,i+n);prefixAt+=n}
     left-=count;i+=count
     if(left==0){if(size in 4..56 && prefixAt==4){val code=TgWire.le(prefix);if(code in setOf(-404,-429,-444))emit("transport-error=$code packet-length=$size")};reset()};continue
    }
    header[headAt++]=bytes[i++];if(headAt<headNeed)continue
    if(protocol==0xefefefef.toInt()){
     val first=header[0].toInt() and 255
     if(headNeed==1 && first and 128!=0){headNeed=4;continue}
     if(first and 128!=0){reset();continue}
     if(headNeed==1 && first==127){headNeed=4;continue}
     size=if(first==127)((header[1].toInt() and 255) or ((header[2].toInt() and 255) shl 8) or ((header[3].toInt() and 255) shl 16))*4 else first*4
    }else{val n=TgWire.le(header);if(n<0){reset();continue};size=n}
    if(size !in 1..TgWire.MAX){emit("framing-invalid-length=$size");enabled=false;return};left=size
   }
  }catch(_:Exception){enabled=false}
 }
}
