package io.github.dovecoteescapee.byedpi.core
import org.junit.Assert.*
import org.junit.Test
import io.github.dovecoteescapee.byedpi.services.*
import io.github.dovecoteescapee.byedpi.data.Mode

class DashboardMetricsTest {
 @Test fun ratesNeedTwoActualSamples(){val s=RateSampler();assertNull(s.sample(100,200,1_000_000_000));assertEquals(200L to 500L,s.sample(300,700,2_000_000_000));s.reset();assertNull(s.sample(10,10,3_000_000_000))}
 @Test fun noNegativeRatesAfterReset(){val s=RateSampler();s.sample(100,200,1_000_000_000);assertNull(s.sample(1,1,2_000_000_000));assertEquals(10L to 20L,s.sample(11,21,3_000_000_000));assertNull(s.sample(-1,1,4_000_000_000))}
 @Test fun formatDurationAndBytes(){assertEquals("00:00",DashboardFormatting.duration(-1));assertEquals("01:12",DashboardFormatting.duration(72_000_000_000));assertEquals("01:01:01",DashboardFormatting.duration(3661_000_000_000));assertEquals("179 B",DashboardFormatting.bytes(179));assertEquals("1.0 KB",DashboardFormatting.bytes(1024))}
 @Test fun pingTimesActualWorkAndPropagatesFailure(){var time=10_000_000L;assertEquals(125L,ConnectionPing.timed({time}) {time+=125_000_000});try{ConnectionPing.timed({0}){throw java.io.IOException("offline")};fail()}catch(_:java.io.IOException){}}
 @Test fun sessionTimeSurvivesRepeatedRunningNotNewSession(){setPhase(ConnectionPhase.Stopped,Mode.VPN);setPhase(ConnectionPhase.Running,Mode.VPN);val t=connectionState.value.sinceNanos;assertTrue(t>0);setPhase(ConnectionPhase.Running,Mode.VPN);assertEquals(t,connectionState.value.sinceNanos);setPhase(ConnectionPhase.Stopping,Mode.VPN);assertEquals(t,connectionState.value.sinceNanos);setPhase(ConnectionPhase.Stopped,Mode.VPN);assertEquals(0,connectionState.value.sinceNanos);setPhase(ConnectionPhase.Running,Mode.VPN);assertTrue(connectionState.value.sinceNanos>=t);setPhase(ConnectionPhase.Stopped,Mode.VPN)}
}
