package io.github.dovecoteescapee.byedpi.core
import android.os.Build
import io.github.dovecoteescapee.byedpi.BuildConfig
import java.net.InetSocketAddress
import java.net.Socket
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLSocket
import javax.net.ssl.SSLSocketFactory

/** User initiated. Fixed public target, verified TLS. No identifiers, SSID, account data or external upload. */
object ConnectionDiagnostics {
    private fun exact(socket: Socket, length: Int): ByteArray {
        val data = ByteArray(length); var offset = 0
        while (offset < length) {
            val count = socket.getInputStream().read(data, offset, length - offset)
            check(count > 0) { "Соединение закрыто" }; offset += count
        }
        return data
    }
    private fun localSocks(port: Int): Socket {
        val socket = Socket()
        try {
            socket.connect(InetSocketAddress("127.0.0.1", port), 4000); socket.soTimeout = 7000
            socket.getOutputStream().write(byteArrayOf(5, 1, 0))
            check(exact(socket, 2).contentEquals(byteArrayOf(5, 0))) { "Локальный SOCKS5 не отвечает" }
            return socket
        } catch (e: Exception) { socket.close(); throw e }
    }
    private fun https(host: String, proxyPort: Int?): String {
        var stage = if (proxyPort == null) "DNS/TCP" else "локальный SOCKS handshake"
        val started = System.nanoTime()
        try {
        val raw = if (proxyPort == null) Socket() else localSocks(proxyPort)
        raw.use { socket ->
            if (proxyPort == null) { socket.connect(InetSocketAddress(host, 443), 7000); socket.soTimeout = 7000 }
            stage = "SOCKS CONNECT"
            if (proxyPort != null) {
                val domain = host.toByteArray(Charsets.US_ASCII)
                socket.getOutputStream().write(byteArrayOf(5, 1, 0, 3, domain.size.toByte()) + domain + byteArrayOf(1, 0xbb.toByte()))
                val reply = exact(socket, 4)
                check(reply[0] == 5.toByte() && reply[1] == 0.toByte()) { "SOCKS CONNECT: код ${reply[1].toInt() and 255}" }
                val length = when (reply[3].toInt()) { 1 -> 4; 4 -> 16; 3 -> exact(socket, 1)[0].toInt() and 255; else -> error("Неверный ответ SOCKS") }
                exact(socket, length + 2)
            }
            stage = "TLS handshake"
            val factory = SSLSocketFactory.getDefault() as SSLSocketFactory
            (factory.createSocket(socket, host, 443, true) as SSLSocket).use { tls ->
                tls.soTimeout = 7000
                if (Build.VERSION.SDK_INT >= 24) {
                    val parameters = tls.sslParameters; parameters.endpointIdentificationAlgorithm = "HTTPS"; tls.sslParameters = parameters
                }
                tls.startHandshake()
                if (Build.VERSION.SDK_INT < 24) check(HttpsURLConnection.getDefaultHostnameVerifier().verify(host, tls.session)) { "Имя TLS-сервера не совпало" }
                stage = "HTTP response"
                tls.getOutputStream().write("HEAD / HTTP/1.1\r\nHost: $host\r\nConnection: close\r\nUser-Agent: Veyra\r\n\r\n".toByteArray(Charsets.US_ASCII))
                val line = StringBuilder()
                while (line.length < 256) { val ch = tls.getInputStream().read(); if (ch < 0 || ch == 10) break; line.append(ch.toChar()) }
                check(line.startsWith("HTTP/")) { "Нет HTTP-ответа" }
                return line.toString().trim()
            }
        }
        } catch (e: Exception) {
            val ms = (System.nanoTime() - started) / 1_000_000
            throw java.io.IOException("этап=$stage, ${ms}мс · ${e.javaClass.simpleName}: ${e.message?.take(120)}", e)
        }
    }
    private fun result(block: () -> String): String = try { block() } catch (e: Exception) { "ОШИБКА · ${e.javaClass.simpleName}: ${e.message?.take(180)}" }
    fun run(port: Int, phase: String, vpn: Boolean): String {
        val rows = mutableListOf("Veyra ${BuildConfig.VERSION_NAME}", "Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) · ${Build.MANUFACTURER} ${Build.MODEL}", "Состояние: $phase", "Локальный SOCKS: 127.0.0.1:$port")
        rows += "SOCKS handshake: " + result { localSocks(port).use { "OK" } }
        if (vpn) rows += "Туннель: " + result { val s = TProxyService.TProxyGetStats(); "worker=${TProxyService.TProxyGetStatus()}, txPackets=${s.getOrNull(0)}, txBytes=${s.getOrNull(1)}, rxPackets=${s.getOrNull(2)}, rxBytes=${s.getOrNull(3)}" }
        rows += "YouTube HTTPS через SOCKS: " + result { https("www.youtube.com",port) }
        rows += "YouTube HTTPS напрямую (само приложение исключено из VPN): " + result { https("www.youtube.com",null) }
        rows += "ChatGPT HTTPS через SOCKS: " + result { https("chatgpt.com",port) }
        val tg=TelegramRuntime.bridge
        rows += if(tg==null) "Telegram WSS-мост: выключен" else "Telegram WSS-мост: local=${tg.port}, sessions=${tg.sessions.get()}, up=${tg.up.get()}, down=${tg.down.get()}, error=${tg.error}"
        rows += "ChatGPT: HTTP 403/429/проверка браузера не подтверждает доступ к аккаунту. Veyra не меняет внешний IP."
        rows += "HTTPS-проверка не проверяет видео, QUIC/UDP, DNS внутри VPN или сеть других приложений."
        rows += "Отчёт никуда не отправляется. Нажми «Скопировать отчёт» или сохрани журнал через меню. Проверь содержимое перед передачей."
        return rows.joinToString("\n")
    }
}
