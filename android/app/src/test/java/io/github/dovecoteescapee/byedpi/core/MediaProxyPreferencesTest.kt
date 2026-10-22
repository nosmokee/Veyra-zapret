package io.github.dovecoteescapee.byedpi.core

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import io.github.dovecoteescapee.byedpi.utility.getPreferences
import kotlinx.coroutines.runBlocking
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class MediaProxyPreferencesTest {
 private val context get()=ApplicationProvider.getApplicationContext<Application>()
 private val prefs get()=context.getPreferences()
 @Before fun setup(){prefs.edit().clear().commit(); File(context.filesDir,"media-assets").deleteRecursively()}
 private fun select(command:String){prefs.edit().putBoolean("byedpi_enable_cmd_settings",true).putString("byedpi_cmd_args",command).commit()}
 @Test fun resolvesAssetWithoutRewritingSavedCommand()=runBlocking {
  select(VeyraPresets.YOUTUBE_QUIC_FAKE_COMMAND)
  val result=MediaProxyPreferences.fromContext(context) as ByeDpiProxyCmdPreferences
  assertFalse(result.args.contains(VeyraPresets.QUIC_FAKE_TOKEN))
  val target=File(result.args[result.args.indexOf("--fake-data")+1])
  assertTrue(target.canonicalPath.startsWith(context.filesDir.canonicalPath+"/"))
  assertArrayEquals(context.assets.open("quic_initial_www_google_com.bin").use{it.readBytes()},target.readBytes())
  assertEquals(VeyraPresets.YOUTUBE_QUIC_FAKE_COMMAND,prefs.getString("byedpi_cmd_args",null))
 }
 @Test fun fastStartResolvesSameAsset()=runBlocking {
  select(VeyraPresets.YOUTUBE_FAST_START_COMMAND)
  val result=MediaProxyPreferences.fromContext(context) as ByeDpiProxyCmdPreferences
  assertFalse(result.args.contains(VeyraPresets.QUIC_FAKE_TOKEN))
  assertEquals("2",result.args[result.args.indexOf("--timeout")+1])
  assertArrayEquals(context.assets.open("quic_initial_www_google_com.bin").use{it.readBytes()},File(result.args[result.args.indexOf("--fake-data")+1]).readBytes())
 }
 @Test fun lightQuicResolvesSameAsset()=runBlocking {
  select(VeyraPresets.YOUTUBE_QUIC_LIGHT_COMMAND)
  val result=MediaProxyPreferences.fromContext(context) as ByeDpiProxyCmdPreferences
  assertFalse(result.args.contains(VeyraPresets.QUIC_FAKE_TOKEN))
  assertEquals("3",result.args[result.args.indexOf("--udp-fake")+1])
  assertArrayEquals(context.assets.open("quic_initial_www_google_com.bin").use{it.readBytes()},File(result.args[result.args.indexOf("--fake-data")+1]).readBytes())
 }
 @Test fun damagedPrivateCopyIsRepaired()=runBlocking {
  select(VeyraPresets.YOUTUBE_QUIC_FAKE_COMMAND)
  val result=MediaProxyPreferences.fromContext(context) as ByeDpiProxyCmdPreferences
  val target=File(result.args[result.args.indexOf("--fake-data")+1]);target.writeText("damaged")
  MediaProxyPreferences.fromContext(context)
  assertArrayEquals(context.assets.open("quic_initial_www_google_com.bin").use{it.readBytes()},target.readBytes())
 }
 @Test fun existingProfilesArePassedThrough()=runBlocking {
  for(command in listOf(VeyraPresets.YOUTUBE_COMMAND,VeyraPresets.YOUTUBE_TCP_COMMAND,VeyraPresets.YOUTUBE_WIFI_COMMAND)){
   select(command)
   assertArrayEquals(ByeDpiProxyCmdPreferences(command).args,(MediaProxyPreferences.fromContext(context) as ByeDpiProxyCmdPreferences).args)
  }
  assertFalse(File(context.filesDir,"media-assets").exists())
 }
}
