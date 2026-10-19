package io.github.dovecoteescapee.byedpi.activities

import android.app.Application
import android.content.DialogInterface
import android.os.Looper
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.test.core.app.ApplicationProvider
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.core.TestTelegramBackend
import io.github.dovecoteescapee.byedpi.core.VeyraPresets
import io.github.dovecoteescapee.byedpi.data.Mode
import io.github.dovecoteescapee.byedpi.services.*
import io.github.dovecoteescapee.byedpi.utility.getPreferences
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode
import org.robolectric.shadows.ShadowDialog
import org.robolectric.shadows.ShadowToast

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w390dp-h844dp-mdpi")
@LooperMode(LooperMode.Mode.PAUSED)
@org.robolectric.annotation.GraphicsMode(org.robolectric.annotation.GraphicsMode.Mode.NATIVE)
class DashboardInteractionTest {
 private val app get()=ApplicationProvider.getApplicationContext<Application>()
 @Before fun before(){TestTelegramBackend.install();shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);setPhase(ConnectionPhase.Stopped,Mode.VPN);app.getPreferences().edit().clear().putString("app_theme","dark").putBoolean("byedpi_enable_cmd_settings",true).putString("byedpi_cmd_args",VeyraPresets.YOUTUBE_COMMAND).putBoolean("veyra_block_quic",true).putBoolean("veyra_telegram_enabled",true).commit()}
 @After fun after(){TestTelegramBackend.reset();setPhase(ConnectionPhase.Stopped,Mode.VPN)}
 private fun run(block:(MainActivity)->Unit){val c=Robolectric.buildActivity(MainActivity::class.java).setup();try{shadowOf(Looper.getMainLooper()).idle();block(c.get())}finally{c.pause().stop().destroy()}}
 private fun MainActivity.click(id:Int){findViewById<View>(id).performClick();shadowOf(Looper.getMainLooper()).idle()}
 private fun MainActivity.row(index:Int)=findViewById<LinearLayout>(R.id.strategy_list).getChildAt(index)
 private fun update(a:MainActivity){MainActivity::class.java.getDeclaredMethod("updateStatus").apply{isAccessible=true}.invoke(a)}
 private fun modal(name:String,dialog:AlertDialog){
  val root=dialog.window!!.decorView
  repeat(3){root.measure(View.MeasureSpec.makeMeasureSpec(350,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(740,View.MeasureSpec.AT_MOST));root.layout(0,0,350,root.measuredHeight);shadowOf(Looper.getMainLooper()).idle()}
  for(id in listOf(DialogInterface.BUTTON_POSITIVE,DialogInterface.BUTTON_NEGATIVE,DialogInterface.BUTTON_NEUTRAL)) {
   val button=dialog.getButton(id)
   if(button!=null && button.visibility==View.VISIBLE) {
    val parent=button.parent as View
    assertTrue("Dialog action must fit parent width: ${button.text}",button.left>=0 && button.right<=parent.width)
    assertTrue("Dialog action must fit parent height",button.top>=0 && button.bottom<=parent.height)
    assertTrue("Dialog action minimum touch target",button.width>=44 && button.height>=44)
    button.layout?.let {layout->for(line in 0 until layout.lineCount)assertEquals("No truncated dialog action",0,layout.getEllipsisCount(line))}
   }
  }
  val details=StringBuilder()
  fun inspect(v:View,depth:Int){details.append(" ".repeat(depth)+v.javaClass.simpleName+" ${v.id} ${v.left},${v.top},${v.width},${v.height} visible=${v.visibility} alpha=${v.alpha}"+(if(v is TextView) " text=${v.text}" else "")+"\n");if(v is android.view.ViewGroup)for(i in 0 until v.childCount)inspect(v.getChildAt(i),depth+1)}
  inspect(root,0);java.io.File("build/$name-layout.txt").writeText(details.toString())
  val bitmap=android.graphics.Bitmap.createBitmap(390,780,android.graphics.Bitmap.Config.ARGB_8888)
  val canvas=android.graphics.Canvas(bitmap);canvas.drawColor(android.graphics.Color.parseColor("#111214"));canvas.translate(20f,(780-root.height)/2f);root.draw(canvas)
  val dir=java.io.File("build/veyra-previews").apply{mkdirs()};java.io.File(dir,"$name.png").outputStream().use{bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
 }
 @Test fun advancedSettingsHiddenAndGithubAvailable()=run{a->
  a.click(R.id.nav_settings)
  assertEquals(View.GONE,a.findViewById<View>(R.id.advanced_button).visibility)
  a.click(R.id.github_button)
  val intent=shadowOf(a).nextStartedActivity
  assertEquals(android.content.Intent.ACTION_VIEW,intent.action)
  assertEquals("https://github.com/nosmokee/Veyra-zapret",intent.data.toString())
 }
 @Test fun aboutExplainsPingAndRefresh()=run{a->a.click(R.id.about_button);modal("about-dialog",ShadowDialog.getLatestDialog() as AlertDialog)}
 @Test fun navigationAndRefreshKeepWorkingConfiguration()=run{a->
  a.click(R.id.nav_strategies);assertEquals(View.VISIBLE,a.findViewById<View>(R.id.strategies_page).visibility)
  a.click(R.id.nav_settings);assertEquals(View.VISIBLE,a.findViewById<View>(R.id.settings_page).visibility)
  a.click(R.id.nav_home);a.click(R.id.refresh_button)
  assertEquals("Состояние обновлено",ShadowToast.getTextOfLatestToast())
  assertEquals(VeyraPresets.YOUTUBE_COMMAND,app.getPreferences().getString("byedpi_cmd_args",null))
  assertTrue(app.getPreferences().getBoolean("veyra_telegram_enabled",false));assertEquals(ConnectionPhase.Stopped,connectionState.value.phase)
 }
 @Test fun pingWhileDisconnectedOnlyExplainsRequirement()=run{a->a.click(R.id.ping_button);assertEquals(a.getString(R.string.ui_ping_none),ShadowToast.getTextOfLatestToast());assertEquals(ConnectionPhase.Stopped,connectionState.value.phase)}
 @Test fun selectionRequiresConfirmationAndNeverStartsService()=run{a->
  a.click(R.id.nav_strategies);a.row(2).performClick();val dialog=ShadowDialog.getLatestDialog() as AlertDialog
  modal("strategy-dialog",dialog)
  assertEquals(VeyraPresets.YOUTUBE_COMMAND,app.getPreferences().getString("byedpi_cmd_args",null))
  dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();shadowOf(Looper.getMainLooper()).idle()
  assertEquals(VeyraPresets.entries[1].command,app.getPreferences().getString("byedpi_cmd_args",null));assertFalse(app.getPreferences().getBoolean("veyra_block_quic",true))
  assertTrue(app.getPreferences().getBoolean("veyra_telegram_enabled",false));assertEquals(ConnectionPhase.Stopped,connectionState.value.phase)
  assertEquals("Veyra Alt 2",a.findViewById<TextView>(R.id.selected_profile).text.toString())
 }
 @Test fun cancellingSelectionLeavesBaseIntact()=run{a->a.click(R.id.nav_strategies);a.row(1).performClick();(ShadowDialog.getLatestDialog() as AlertDialog).getButton(DialogInterface.BUTTON_NEGATIVE).performClick();shadowOf(Looper.getMainLooper()).idle();assertEquals(VeyraPresets.YOUTUBE_COMMAND,app.getPreferences().getString("byedpi_cmd_args",null))}
 @Test fun activeConnectionRejectsProfileAndAdvancedChanges()=run{a->
  setPhase(ConnectionPhase.Running,Mode.Proxy);update(a);a.click(R.id.nav_strategies);a.row(3).performClick()
  assertEquals(a.getString(R.string.ui_strategy_locked),ShadowToast.getTextOfLatestToast());assertEquals(VeyraPresets.YOUTUBE_COMMAND,app.getPreferences().getString("byedpi_cmd_args",null))
  a.click(R.id.nav_settings);assertFalse(a.findViewById<View>(R.id.advanced_button).isEnabled)
 }
 @Test fun refreshDoesNotOverwriteCustomInputButShowsPlannedBase()=run{a->
  app.getPreferences().edit().putString("byedpi_cmd_args","--custom preserved").commit();update(a);a.click(R.id.refresh_button)
  assertEquals(a.getString(R.string.ui_base),a.findViewById<TextView>(R.id.selected_profile).text.toString());assertEquals("--custom preserved",app.getPreferences().getString("byedpi_cmd_args",null))
 }
 @Test fun telegramHasNoSeparatePowerToggle()=run{a->
  val before=app.getPreferences().getBoolean("veyra_telegram_enabled",false)
  a.click(R.id.telegram_home_button)
  assertEquals(a.getString(R.string.ui_tg_connect_first),ShadowToast.getTextOfLatestToast())
  assertEquals(before,app.getPreferences().getBoolean("veyra_telegram_enabled",false));assertEquals(ConnectionPhase.Stopped,connectionState.value.phase)
 }
 @Test fun singleShieldPreparesYoutubeAndTelegramBeforeStarting()=run{a->
  app.getPreferences().edit().remove("byedpi_cmd_args").putBoolean("byedpi_enable_cmd_settings",false).putBoolean("veyra_telegram_enabled",false).commit()
  org.robolectric.shadows.ShadowVpnService.setPrepareResult(null)
  a.click(R.id.status_button)
  assertEquals(VeyraPresets.YOUTUBE_COMMAND,app.getPreferences().getString("byedpi_cmd_args",null))
  assertTrue(app.getPreferences().getBoolean("veyra_block_quic",false));assertTrue(app.getPreferences().getBoolean("veyra_telegram_enabled",false))
  assertEquals(ConnectionPhase.Starting,connectionState.value.phase)
  val intent=shadowOf(a).nextStartedService;assertEquals(ByeDpiVpnService::class.java.name,intent.component!!.className)
  assertEquals(io.github.dovecoteescapee.byedpi.data.START_ACTION,intent.action)
  a.click(R.id.status_button);assertNull(shadowOf(a).nextStartedService)
 }
 @Test fun shieldStopsCombinedSessionAndReconnectReenablesBoth()=run{a->
  setPhase(ConnectionPhase.Running,Mode.Proxy);update(a);a.click(R.id.status_button)
  assertEquals(ConnectionPhase.Stopping,connectionState.value.phase);assertEquals(io.github.dovecoteescapee.byedpi.data.STOP_ACTION,shadowOf(a).nextStartedService.action)
  setPhase(ConnectionPhase.Stopped,Mode.VPN);update(a)
  app.getPreferences().edit().putBoolean("veyra_telegram_enabled",false).commit()
  org.robolectric.shadows.ShadowVpnService.setPrepareResult(null);a.click(R.id.status_button)
  assertTrue(app.getPreferences().getBoolean("veyra_telegram_enabled",false));assertEquals(ConnectionPhase.Starting,connectionState.value.phase)
 }
 @Test fun permissionConsentGatesCombinedStartAndDoubleTap()=run{a->
  org.robolectric.shadows.ShadowVpnService.setPrepareResult(android.content.Intent("test.VPN_CONSENT"))
  a.click(R.id.status_button)
  assertEquals(ConnectionPhase.Stopped,connectionState.value.phase);assertNull(shadowOf(a).nextStartedService)
  val request=shadowOf(a).nextStartedActivityForResult
  assertEquals("test.VPN_CONSENT",request.intent.action)
  assertFalse(a.findViewById<View>(R.id.status_button).isEnabled)
  a.click(R.id.status_button);assertNull(shadowOf(a).nextStartedActivityForResult)
  shadowOf(a).receiveResult(request.intent,android.app.Activity.RESULT_CANCELED,null);shadowOf(Looper.getMainLooper()).idle()
  assertTrue(a.findViewById<View>(R.id.status_button).isEnabled);assertNull(shadowOf(a).nextStartedService)
  a.click(R.id.status_button);val retry=shadowOf(a).nextStartedActivityForResult
  shadowOf(a).receiveResult(retry.intent,android.app.Activity.RESULT_OK,null);shadowOf(Looper.getMainLooper()).idle()
  assertEquals(ConnectionPhase.Starting,connectionState.value.phase);assertEquals(ByeDpiVpnService::class.java.name,shadowOf(a).nextStartedService.component!!.className)
 }
 @Test fun telegramSetupIsOfferedOnceAndEndpointSurvivesReconnect()=run{a->
  val owner=Any()
  try{
   io.github.dovecoteescapee.byedpi.core.TelegramRuntime.start(app,owner,1080)
   val link=io.github.dovecoteescapee.byedpi.core.TelegramRuntime.bridge!!.link
   setPhase(ConnectionPhase.Running,Mode.Proxy);update(a)
   val dialog=ShadowDialog.getLatestDialog() as AlertDialog;modal("telegram-once-dialog",dialog)
   assertEquals(a.getString(R.string.ui_tg_add),dialog.getButton(DialogInterface.BUTTON_POSITIVE).text.toString())
   dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();shadowOf(Looper.getMainLooper()).idle();assertFalse(dialog.isShowing)
   update(a);assertSame(dialog,ShadowDialog.getLatestDialog());assertFalse(dialog.isShowing)
   io.github.dovecoteescapee.byedpi.core.TelegramRuntime.stop(owner);setPhase(ConnectionPhase.Stopped,Mode.Proxy);update(a)
   io.github.dovecoteescapee.byedpi.core.TelegramRuntime.start(app,owner,1080);setPhase(ConnectionPhase.Running,Mode.Proxy);update(a)
   assertEquals(link,io.github.dovecoteescapee.byedpi.core.TelegramRuntime.bridge!!.link);assertFalse(dialog.isShowing)
   a.click(R.id.telegram_home_button);val intent=shadowOf(a).nextStartedActivity
   assertEquals(android.content.Intent.ACTION_VIEW,intent.action);assertEquals(link,intent.data.toString())
  }finally{io.github.dovecoteescapee.byedpi.core.TelegramRuntime.stop(owner)}
 }
 @Test fun transientStatesPreventRepeatedStarts()=run{a->
  for(phase in listOf(ConnectionPhase.Starting,ConnectionPhase.Stopping)){setPhase(phase,Mode.Proxy);update(a);assertFalse(a.findViewById<View>(R.id.status_button).isEnabled);assertFalse(a.findViewById<View>(R.id.ping_button).isEnabled)}
 }
}
