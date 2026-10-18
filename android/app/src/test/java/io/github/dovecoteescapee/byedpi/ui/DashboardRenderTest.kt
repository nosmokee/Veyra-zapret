package io.github.dovecoteescapee.byedpi.ui
import android.os.Bundle
import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.databinding.ActivityMainBinding
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File

class DashboardPreviewActivity:AppCompatActivity() {
 lateinit var binding:ActivityMainBinding
 override fun onCreate(savedInstanceState:Bundle?){setTheme(R.style.Theme_Veyra_Dashboard);super.onCreate(savedInstanceState);binding=ActivityMainBinding.inflate(layoutInflater);setContentView(binding.root)}
}
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[34],qualifiers="w390dp-h844dp-mdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class DashboardRenderTest {
 private fun preview(name:String,page:String="home",state:DashboardState=DashboardState(profile="Veyra Base · SNI / TCP",profileIndex=0),width:Int=390,height:Int=780,scale:Float=1f,scrolled:Boolean=false) {
  val resources=androidx.test.core.app.ApplicationProvider.getApplicationContext<android.app.Application>().resources
  val cfg=android.content.res.Configuration(resources.configuration);cfg.fontScale=scale
  resources.updateConfiguration(cfg,resources.displayMetrics)
  val controller=Robolectric.buildActivity(DashboardPreviewActivity::class.java).setup()
  try {
   val activity=controller.get();val b=activity.binding
   val ui=DashboardUi(b){};ui.render(state);ui.showPage(page)
   val root=b.root;root.measure(View.MeasureSpec.makeMeasureSpec(width,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(height,View.MeasureSpec.EXACTLY));root.layout(0,0,width,height)
   assertEquals(5,b.strategyList.childCount)
   assertEquals(page=="home",b.homePage.visibility==View.VISIBLE)
   assertEquals(page=="settings",b.navSettings.isSelected)
   assertTrue(b.navHome.height>=44);assertTrue(b.navHome.width>=44)
   assertEquals(state.phase!=DashboardPhase.Starting && state.phase!=DashboardPhase.Stopping,b.statusButton.isEnabled)
   if(scrolled)b.pageScroll.scrollTo(0,b.pageScroll.getChildAt(0).height)
   val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888);root.draw(Canvas(bitmap))
   val dir=File("build/veyra-previews").apply{mkdirs()};File(dir,"$name.png").outputStream().use{bitmap.compress(Bitmap.CompressFormat.PNG,100,it)};bitmap.recycle()
  } finally {controller.pause().stop().destroy()}
 }
 @Test fun homeBottom()=preview("home-bottom",scrolled=true)
 @Test fun settingsBottom()=preview("settings-bottom","settings",scrolled=true)
 @Test fun strategiesBottom()=preview("strategies-bottom","strategies",scrolled=true)
 @Test fun homeOff()=preview("home-off")
 @Test fun homeOn()=preview("home-on",state=DashboardState(DashboardPhase.Running,"Veyra Base · SNI / TCP",0,ping="142 мс · WSS / Telegram",telegram="Передано: ↑ 8.0 KB · ↓ 1.2 MB",up="179 B/s",down="119 B/s",duration="01:12"))
 @Test fun strategies()=preview("strategies","strategies")
 @Test fun settings()=preview("settings","settings",DashboardState(profile="Veyra Base · SNI / TCP",profileIndex=0,telegram="Мост готов · прокси добавляется один раз"))
 @Test fun consent()=preview("vpn-consent",state=DashboardState(DashboardPhase.Starting,"Veyra Base · SNI / TCP",0,message="Подтверди VPN в системном окне Android"))
 @Test fun starting()=preview("starting",state=DashboardState(DashboardPhase.Starting,"Veyra Base · SNI / TCP",0))
 @Test fun failed()=preview("failed",state=DashboardState(DashboardPhase.Failed,"Veyra Alt 2",2,message="Локальный прокси не запустился. Открой журнал в настройках.",ping="Нет ответа · попробуй другую стратегию"))
 @Test fun narrow()=preview("home-360",width=360,height=720)
 @Test fun homeEnlargedText()=preview("home-large-text",scale=1.3f)
 @Test fun settingsEnlargedText()=preview("settings-large-text","settings",scale=1.3f)
 @Test fun enlargedText()=preview("strategies-large-text","strategies",width=390,height=780,scale=1.3f)
}
