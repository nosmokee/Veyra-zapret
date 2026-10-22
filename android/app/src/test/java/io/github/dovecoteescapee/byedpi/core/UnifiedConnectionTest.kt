package io.github.dovecoteescapee.byedpi.core

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import io.github.dovecoteescapee.byedpi.utility.getPreferences
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.net.ServerSocket
import java.net.InetSocketAddress

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34])
class UnifiedConnectionTest {
 private val context get()=ApplicationProvider.getApplicationContext<Application>()
 private val prefs get()=context.getPreferences()
 private val owner=Any()
 @Before fun setup(){TestTelegramBackend.install();prefs.edit().clear().commit();context.getSharedPreferences("telegram_local",Context.MODE_PRIVATE).edit().clear().commit()}
 @After fun cleanup(){TelegramRuntime.stop(owner);TestTelegramBackend.reset()}
 @Test fun firstConnectContainsBaseYoutubeQuicAndTelegram(){UnifiedConnection.prepare(prefs);assertEquals(VeyraPresets.BASE_COMMAND,prefs.getString("byedpi_cmd_args",null));assertTrue(prefs.getBoolean("byedpi_enable_cmd_settings",false));assertFalse(prefs.getBoolean("veyra_block_quic",true));assertTrue(prefs.getBoolean("veyra_telegram_enabled",false));assertEquals("127.0.0.1",prefs.getString("byedpi_proxy_ip",null));assertEquals("1080",prefs.getString("byedpi_proxy_port",null))}
 @Test fun everyConnectReenablesBothEvenAfterOldModuleOff(){repeat(3){prefs.edit().putBoolean("veyra_telegram_enabled",false).putBoolean("veyra_block_quic",false).commit();UnifiedConnection.prepare(prefs);assertTrue(prefs.getBoolean("veyra_telegram_enabled",false));assertFalse(prefs.getBoolean("veyra_block_quic",true))}}
 @Test fun explicitAltSurvivesAndAlsoStartsTelegram(){for(p in VeyraPresets.entries){prefs.edit().putInt("veyra_base_revision",13).putBoolean("byedpi_enable_cmd_settings",true).putString("byedpi_cmd_args",p.command).commit();repeat(2){UnifiedConnection.prepare(prefs);assertEquals(p.command,prefs.getString("byedpi_cmd_args",null));assertTrue(prefs.getBoolean("veyra_telegram_enabled",false));assertEquals(p.id in setOf("youtube-tcp","legacy-base"),prefs.getBoolean("veyra_block_quic",true))}}}
 @Test fun unrelatedSettingsAndTelegramSecretAreNotChanged(){prefs.edit().putString("dns_ip","9.9.9.9").putString("app_theme","dark").commit();val local=context.getSharedPreferences("telegram_local",Context.MODE_PRIVATE);local.edit().putString("secret","persisted-secret").putInt("port",19635).commit();UnifiedConnection.prepare(prefs);assertEquals("9.9.9.9",prefs.getString("dns_ip",null));assertEquals("dark",prefs.getString("app_theme",null));assertEquals("persisted-secret",local.getString("secret",null));assertEquals(19635,local.getInt("port",0))}
 @Test fun unconfiguredAndUnknownInputPlansBaseWithoutWriting(){prefs.edit().putString("byedpi_cmd_args","--old custom input").putBoolean("byedpi_enable_cmd_settings",true).commit();assertEquals(0,UnifiedConnection.profileIndex(prefs));assertEquals("--old custom input",prefs.getString("byedpi_cmd_args",null));UnifiedConnection.prepare(prefs);assertEquals(VeyraPresets.BASE_COMMAND,prefs.getString("byedpi_cmd_args",null))}
 @Test fun realListenerAndKeySurviveThreeRestarts(){UnifiedConnection.prepare(prefs);var link:String?=null;repeat(3){TelegramRuntime.start(context,owner,1080);val b=TelegramRuntime.bridge!!;assertTrue(b.running);assertTrue(b.port>0);val current=b.link;if(link==null)link=current else assertEquals(link,current);assertEquals(b.port,context.getSharedPreferences("telegram_local",Context.MODE_PRIVATE).getInt("port",0));TelegramRuntime.stop(owner);assertNull(TelegramRuntime.bridge)}}
 @Test fun savedPortConflictFailsInsteadOfChangingTelegramConfiguration(){UnifiedConnection.prepare(prefs);TelegramRuntime.start(context,owner,1080);val b=TelegramRuntime.bridge!!;val port=b.port;val link=b.link;TelegramRuntime.stop(owner)
  ServerSocket().use{busy->
   busy.reuseAddress=true;val until=System.nanoTime()+2_000_000_000L
   while(true){try{busy.bind(InetSocketAddress("127.0.0.1",port));break}catch(e:java.net.BindException){if(System.nanoTime()>until)throw e;Thread.sleep(20)}}
   try{TelegramRuntime.start(context,owner,1080);fail("Saved endpoint must not silently move")}catch(e:java.net.BindException){assertTrue(e.message!!.contains(port.toString()))};assertNull(TelegramRuntime.bridge);assertEquals(port,context.getSharedPreferences("telegram_local",Context.MODE_PRIVATE).getInt("port",0))}
  TelegramRuntime.start(context,owner,1080);assertEquals(link,TelegramRuntime.bridge!!.link)
 }
 @Test fun foreignOwnerCannotStopCombinedTelegramBridge(){UnifiedConnection.prepare(prefs);TelegramRuntime.start(context,owner,1080);TelegramRuntime.stop(Any());assertTrue(TelegramRuntime.bridge!!.running);TelegramRuntime.stop(owner);assertNull(TelegramRuntime.bridge)}
 @Test fun duplicateStartCannotReplaceActiveEndpoint(){UnifiedConnection.prepare(prefs);TelegramRuntime.start(context,owner,1080);val b=TelegramRuntime.bridge;try{TelegramRuntime.start(context,Any(),1080);fail()}catch(_:IllegalStateException){};assertSame(b,TelegramRuntime.bridge);assertTrue(b!!.running)}
 @Test fun migrationSelectsNewBaseOnceAndPreservesLaterManualChoice(){
 prefs.edit().putBoolean("byedpi_enable_cmd_settings",true).putString("byedpi_cmd_args",VeyraPresets.YOUTUBE_COMMAND).commit()
 assertEquals(0,UnifiedConnection.profileIndex(prefs));UnifiedConnection.prepare(prefs)
 assertEquals(VeyraPresets.BASE_COMMAND,prefs.getString("byedpi_cmd_args",null));assertEquals(13,prefs.getInt("veyra_base_revision",0))
 prefs.edit().putString("byedpi_cmd_args",VeyraPresets.entries[0].command).commit();UnifiedConnection.prepare(prefs)
 assertEquals(VeyraPresets.entries[0].command,prefs.getString("byedpi_cmd_args",null))
 }
}
