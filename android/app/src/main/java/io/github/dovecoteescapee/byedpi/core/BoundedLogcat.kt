package io.github.dovecoteescapee.byedpi.core

import java.io.File

/** Finite logcat capture, no pipe read waiting indefinitely for a child process. */
object BoundedLogcat {
    fun collect(cacheDir: File): String {
        val temp = File.createTempFile("veyra-log-", ".txt", cacheDir)
        var process: Process? = null
        return try {
            val child = ProcessBuilder("logcat", "-d", "-t", "500", "-f", temp.absolutePath)
                .redirectErrorStream(true).start()
            process = child
            val deadline = System.nanoTime() + 2_000_000_000L
            var done = false
            while (System.nanoTime() < deadline && !done) {
                try { child.exitValue(); done = true } catch (_: IllegalThreadStateException) { Thread.sleep(25) }
            }
            if (!done) { child.destroy(); "Logcat не завершился за 2 секунды. Диагностический отчёт сохранён выше." }
            else temp.inputStream().bufferedReader().use { reader ->
                val text = StringBuilder(); val buffer = CharArray(4096)
                while (text.length < 200000) {
                    val count = reader.read(buffer, 0, minOf(buffer.size, 200000 - text.length))
                    if (count < 0) break
                    text.append(buffer, 0, count)
                }
                text.toString()
            }
        } finally { process?.destroy(); temp.delete() }
    }
}
