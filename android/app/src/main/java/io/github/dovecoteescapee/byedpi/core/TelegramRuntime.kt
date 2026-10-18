package io.github.dovecoteescapee.byedpi.core

import android.content.Context
import android.util.Log
import android.util.Base64
import io.github.dovecoteescapee.byedpi.utility.getPreferences

/** Owned by the existing foreground VPN/proxy service, never by the Activity. */
object TelegramRuntime {
    @Volatile var bridge:TelegramBridge?=null;private set
    private var owner:Any?=null
    private val routes=java.util.concurrent.ConcurrentHashMap<String,Pair<String,String>>()
    @Synchronized fun start(context:Context,service:Any,port:Int) {
        if(!context.getPreferences().getBoolean("veyra_telegram_enabled",false))return
        check(bridge==null) { "Telegram bridge already owned by a service" }
        val prefs=context.getSharedPreferences("telegram_local",Context.MODE_PRIVATE)
        val saved=prefs.getString("secret",null)
        val secret=if(saved==null) TgWire.random(16).also {check(prefs.edit().putString("secret",Base64.encodeToString(it,Base64.NO_WRAP)).commit())} else Base64.decode(saved,Base64.NO_WRAP).also {require(it.size==16)}
        val instance=TelegramBridge(secret,{dc,media->
            if(dc==203) {
                TgDiagnostics.record("CONNECT dc=203 transport=MTProto-TCP endpoint=${TgTcpSocket.CDN203}:443 via=local-SOCKS")
                TgTcpSocket.connect203(port)
            } else {
            val started=System.nanoTime();var last:Exception?=null;var result:TgSocket?=null
            val routeKey="$dc:$media"
            val candidates=TgWire.candidates(dc,media,routes[routeKey])
            routes@ for(pair in candidates.chunked(2)) {
                if(bridge?.running!=true || (System.nanoTime()-started)>25_000_000_000L)break@routes
                val selected=java.util.concurrent.ConcurrentHashMap<TgSocket,Pair<String,String>>()
                val attempts:List<((java.io.Closeable)->Unit)->TgSocket> = pair.map {(host,address)->
                    {track->
                        val clock=System.nanoTime()
                        TgDiagnostics.record("WSS attempt dc=$dc media=$media host=$host address=$address")
                        try {
                            val socket=TgWebSocket.connect(host,address,port,track)
                            selected[socket]=host to address
                            TgDiagnostics.record("WSS connected TLS=verified dc=$dc host=$host address=$address elapsed-ms=${(System.nanoTime()-clock)/1_000_000}")
                            socket
                        }catch(e:Exception){TgDiagnostics.record("WSS failed dc=$dc host=$host address=$address elapsed-ms=${(System.nanoTime()-clock)/1_000_000} ${TgDiagnostics.describe(e)}");throw e}
                    }
                }
                try {val socket=TgDialRace.first(attempts){bridge?.running==true};result=socket;selected[socket]?.let{routes[routeKey]=it};break@routes}
                catch(e:Exception){last=e;routes.remove(routeKey);Log.w("VeyraTelegram","WSS pair failed: ${TgDiagnostics.describe(e)}")}
            }
            result ?: throw java.io.IOException("Official Telegram WSS unavailable",last)
            }
        },{text->Log.w("VeyraTelegram",text)})
        try {
            owner=service;bridge=instance
            val savedPort=prefs.getInt("port",1087)
            require(savedPort in 1..65535) {"Некорректный сохранённый Telegram-порт"}
            // Keep the same endpoint and secret across every stop/start. A busy saved port must
            // fail visibly, never silently change a proxy already stored in Telegram.
            instance.start(savedPort,allowPortFallback=!prefs.contains("port"))
            check(prefs.edit().putInt("port",instance.port).commit()) {"Не удалось сохранить Telegram-порт"}
        } catch(e:Exception) {instance.close();owner=null;bridge=null;throw e}
    }
    @Synchronized fun stop(service:Any) {if(owner!==service)return;bridge?.close();bridge=null;owner=null}
}
