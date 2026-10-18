package io.github.dovecoteescapee.byedpi.core

import android.content.Context
import android.util.Log
import android.util.Base64
import io.github.dovecoteescapee.byedpi.utility.getPreferences

/** Owned by the existing foreground VPN/proxy service, never by the Activity. */
object TelegramRuntime {
    @Volatile var bridge:TelegramBridge?=null;private set
    private var owner:Any?=null
    @Synchronized fun start(context:Context,service:Any,port:Int) {
        if(!context.getPreferences().getBoolean("veyra_telegram_enabled",false))return
        check(bridge==null) { "Telegram bridge already owned by a service" }
        val prefs=context.getSharedPreferences("telegram_local",Context.MODE_PRIVATE)
        val saved=prefs.getString("secret",null)
        val secret=if(saved==null) TgWire.random(16).also {check(prefs.edit().putString("secret",Base64.encodeToString(it,Base64.NO_WRAP)).commit())} else Base64.decode(saved,Base64.NO_WRAP).also {require(it.size==16)}
        val instance=TelegramBridge(secret,{dc,media->
            val started=System.nanoTime();var last:Exception?=null;var result:TgSocket?=null
            routes@ for(host in TgWire.hosts(dc,media))for(address in TgWire.addresses(dc,host)) {
                if(bridge?.running!=true || (System.nanoTime()-started)>25_000_000_000L)break@routes
                try {Log.i("VeyraTelegram","WSS host=$host address=$address TLS=verified via local SOCKS");result=TgWebSocket.connect(host,address,port);break@routes} catch(e:Exception) {last=e;Log.w("VeyraTelegram","WSS failed: ${e.javaClass.simpleName}")}
            }
            result ?: throw java.io.IOException("Official Telegram WSS unavailable",last)
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
