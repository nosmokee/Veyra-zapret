package io.github.dovecoteescapee.byedpi
import io.github.dovecoteescapee.byedpi.core.VeyraPresets
import io.github.dovecoteescapee.byedpi.core.ByeDpiProxyCmdPreferences
import org.junit.Assert.*
import org.junit.Test
class VeyraPresetsTest {
 @Test fun presetsAreDistinctAndLocal() {
  val presets = VeyraPresets.entries
  assertEquals(4, presets.size)
  assertEquals(4, presets.map { it.command }.toSet().size)
  presets.forEach {
   val args = ByeDpiProxyCmdPreferences(it.command).args.toList()
   assertEquals("ciadpi", args.first())
   assertEquals("127.0.0.1", args[args.indexOf("--ip") + 1])
   assertEquals("1080", args[args.indexOf("--port") + 1])
   assertFalse(it.command.contains(";"))
  }
 }
 @Test fun tlsIsOptionalAndExplicit() {
  assertTrue(VeyraPresets.entries.take(3).all { it.command.contains("--tlsrec 1") })
  assertFalse(VeyraPresets.entries.last().command.contains("--tlsrec"))
 }
}
