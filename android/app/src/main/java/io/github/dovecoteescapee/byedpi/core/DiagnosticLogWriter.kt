package io.github.dovecoteescapee.byedpi.core

import java.io.OutputStream

/** The diagnostic report must survive unavailable or failing optional system logs. */
object DiagnosticLogWriter {
    fun write(output: OutputStream, report: String, optionalLogs: () -> String) {
        val body = report.ifBlank { "Veyra: диагностика ещё не запускалась." }
        output.write((body + "\n\n").toByteArray(Charsets.UTF_8))
        output.flush()
        val extra = try { optionalLogs().ifBlank { "Logcat пуст или недоступен." } }
                    catch (e: Exception) { "Logcat недоступен: ${e.javaClass.simpleName}" }
        output.write(("--- App-visible logcat ---\n" + extra + "\n").toByteArray(Charsets.UTF_8))
        output.flush()
    }
}
