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
  assertEquals(9, VeyraPresets.entries.size)
  assertTrue(VeyraPresets.entries.none { it.command == VeyraPresets.BASE_COMMAND })
 }
 @Test fun youtubeTcpHasRecordSplittingAndTwoFallbacks() {
  val args=ByeDpiProxyCmdPreferences(VeyraPresets.YOUTUBE_TCP_COMMAND).args.toList()
  assertEquals("1+s",args[args.indexOf("--split")+1])
  assertEquals("3+s",args[args.indexOf("--tlsrec")+1])
  assertEquals(2,args.count {it=="--auto=torst,ssl_err"})
  assertTrue(args.indexOf("--tlsrec") < args.indexOf("--auto=torst,ssl_err"))
  assertFalse(args.contains("--no-udp"))
  assertTrue(VeyraPresets.blocksQuic(5))
  assertTrue(VeyraPresets.blocksQuic(5))
  for(index in 1..4) assertFalse(VeyraPresets.blocksQuic(index))
 }
 @Test fun wifiAllowsQuicWithoutChangingTcpStrategy() {
  assertEquals(VeyraPresets.YOUTUBE_TCP_COMMAND+" --cache-ttl 100800",VeyraPresets.YOUTUBE_WIFI_COMMAND)
  assertEquals("youtube-wifi",VeyraPresets.entries[5].id)
  assertFalse(VeyraPresets.blocksQuic(6))
  val args=ByeDpiProxyCmdPreferences(VeyraPresets.YOUTUBE_WIFI_COMMAND).args.toList()
  assertFalse(args.contains("--no-udp"))
  assertFalse(args.contains("--udp-fake"))
 }
 @Test fun quicFakeUsesScopedUdpRuleAndUnchangedTcpFallbacks() {
  val args=ByeDpiProxyCmdPreferences(VeyraPresets.YOUTUBE_QUIC_FAKE_COMMAND).args.toList()
  assertEquals("443",args[args.indexOf("--pf")+1])
  assertEquals("udp",args[args.indexOf("--proto")+1])
  assertEquals("11",args[args.indexOf("--udp-fake")+1])
  assertEquals(VeyraPresets.QUIC_FAKE_TOKEN,args[args.indexOf("--fake-data")+1])
  assertTrue(args.contains("--auto=none"))
  assertFalse(VeyraPresets.blocksQuic(7))
 }
 @Test fun fastStartChangesOnlyTcpRetryTimeout() {
  assertEquals(VeyraPresets.YOUTUBE_QUIC_FAKE_COMMAND.replace("--timeout 5", "--timeout 2"),VeyraPresets.YOUTUBE_FAST_START_COMMAND)
  assertEquals("youtube-fast-start",VeyraPresets.entries[7].id)
  assertFalse(VeyraPresets.blocksQuic(8))
 }
 @Test fun lightQuicChangesOnlyRepetitionCount() {
  assertEquals(VeyraPresets.YOUTUBE_QUIC_FAKE_COMMAND.replace("--udp-fake 11", "--udp-fake 3"),VeyraPresets.YOUTUBE_QUIC_LIGHT_COMMAND)
  assertEquals(VeyraPresets.YOUTUBE_QUIC_LIGHT_COMMAND,VeyraPresets.BASE_COMMAND)
  assertFalse(VeyraPresets.blocksQuic(0));assertTrue(VeyraPresets.blocksQuic(9))
 }
}
