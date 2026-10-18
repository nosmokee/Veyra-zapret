package io.github.dovecoteescapee.byedpi.core
/** Optional Linux/JVM live smoke test; no Android VPN or provider-success claim. */
object ConnectionPingLive {
 @JvmStatic fun main(args:Array<String>){
  val port=args.single().toInt()
  repeat(2){println("Verified Telegram TLS/WSS via local SOCKS: ${ConnectionPing.measure(port)} ms")}
 }
}
