package io.github.dovecoteescapee.byedpi
import io.github.dovecoteescapee.byedpi.core.DiagnosticLogWriter
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
class DiagnosticLogWriterTest {
 @Test fun reportFlushedBeforeOptionalLogs() {
  var flushed = false
  val output = object: ByteArrayOutputStream() { override fun flush() { flushed = true } }
  DiagnosticLogWriter.write(output, "Отчёт Xiaomi") {
   assertTrue(flushed); assertTrue(output.toString("UTF-8").contains("Отчёт Xiaomi")); "extra"
  }
  assertTrue(output.toString("UTF-8").contains("extra"))
 }
 @Test fun unavailableLogcatDoesNotEraseReport() {
  val output = ByteArrayOutputStream()
  DiagnosticLogWriter.write(output, "SOCKS OK / TLS timeout") { throw SecurityException() }
  assertTrue(output.toString("UTF-8").contains("SOCKS OK / TLS timeout"))
  assertTrue(output.toString("UTF-8").contains("SecurityException"))
 }
 @Test fun emptyInputsStillProduceNonemptyUtf8Report() {
  val output = ByteArrayOutputStream(); DiagnosticLogWriter.write(output, "") { "" }
  assertTrue(output.size() > 0); assertTrue(output.toString("UTF-8").contains("Veyra"))
 }
 @Test(expected = IOException::class) fun writeFailureIsNotReportedAsSuccess() {
  val output = object: OutputStream() { override fun write(value: Int) { throw IOException("storage") } }
  DiagnosticLogWriter.write(output, "report") { "logs" }
 }
}
