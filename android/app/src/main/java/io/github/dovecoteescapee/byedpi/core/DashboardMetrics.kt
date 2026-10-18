package io.github.dovecoteescapee.byedpi.core
import java.util.Locale

/** No generated traffic values: unknown is null, first sample is a baseline. */
class RateSampler {
    private var previous:LongArray?=null;private var at=0L
    fun reset(){previous=null;at=0L}
    fun sample(tx:Long,rx:Long,now:Long):Pair<Long,Long>? {
        if(tx<0||rx<0){reset();return null}
        val old=previous;val elapsed=now-at;previous=longArrayOf(tx,rx);at=now
        if(old==null||elapsed<=0||tx<old[0]||rx<old[1])return null
        return (((tx-old[0]).toDouble()*1_000_000_000/elapsed).toLong()) to (((rx-old[1]).toDouble()*1_000_000_000/elapsed).toLong())
    }
}
object DashboardFormatting {
    fun bytes(value:Long):String=when {value<1024->"$value B";value<1024*1024->String.format(Locale.ROOT,"%.1f KB",value/1024.0);else->String.format(Locale.ROOT,"%.1f MB",value/(1024.0*1024))}
    fun duration(nanos:Long):String {val secs=nanos.coerceAtLeast(0)/1_000_000_000;return if(secs>=3600)String.format(Locale.ROOT,"%02d:%02d:%02d",secs/3600,secs/60%60,secs%60) else String.format(Locale.ROOT,"%02d:%02d",secs/60,secs%60)}
}
object ConnectionPing {
    /** WSS setup time through the current local SOCKS. No account, no content request. */
    fun measure(port:Int):Long {
        require(port in 1..65535)
        return timed(System::nanoTime) {TgWebSocket.connect("kws2.web.telegram.org","149.154.167.220",port).use { }}
    }
    fun timed(clock:()->Long,connect:()->Unit):Long {val start=clock();connect();return ((clock()-start).coerceAtLeast(0)/1_000_000).coerceAtLeast(1)}
}
