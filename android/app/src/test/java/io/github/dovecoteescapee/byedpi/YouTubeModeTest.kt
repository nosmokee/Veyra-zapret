package io.github.dovecoteescapee.byedpi
import io.github.dovecoteescapee.byedpi.core.VeyraPresets
import io.github.dovecoteescapee.byedpi.core.ByeDpiProxyCmdPreferences
import org.junit.Assert.*
import org.junit.Test
class YouTubeModeTest {
 @Test fun youtubeProfileUsesSniAndTimeoutFallback() {
  val args = ByeDpiProxyCmdPreferences(VeyraPresets.YOUTUBE_COMMAND).args.toList()
  assertEquals("127.0.0.1", args[args.indexOf("--ip")+1])
  assertEquals("1080", args[args.indexOf("--port")+1])
  assertEquals("1+s", args[args.indexOf("--disorder")+1])
  assertTrue(args.contains("--auto=torst"))
  assertEquals("1+s", args[args.indexOf("--split")+1])
  assertEquals("3+s", args[args.indexOf("--tlsrec")+1])
  assertFalse(args.contains("--no-udp"))
 }
 @Test fun originalProfilesAreUnchangedAndDistinctFromCompatibility() {
  assertEquals(4, VeyraPresets.entries.size)
  assertTrue(VeyraPresets.entries.none { it.command == VeyraPresets.YOUTUBE_COMMAND })
 }
}
