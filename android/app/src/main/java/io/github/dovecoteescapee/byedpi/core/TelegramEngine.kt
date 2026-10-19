package io.github.dovecoteescapee.byedpi.core
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong
/** Testable ownership interface; production always uses FlowsealBridge. */
interface TelegramEngine:Closeable {
 val running:Boolean
 val port:Int
 val error:String
 val link:String
 val up:AtomicLong
 val down:AtomicLong
 val sessions:AtomicLong
 fun start(preferredPort:Int,allowPortFallback:Boolean=false)
 fun diagnostics():String
}
