package io.github.dovecoteescapee.byedpi
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.Assert.*
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
/** Requires a physical/emulated Android device. Not a media download test. */
@RunWith(AndroidJUnit4::class)
class FlowsealRuntimeTest {
 @Test fun realPythonAndPlatformAesStartStopThreeTimes(){
  val context=InstrumentationRegistry.getInstrumentation().targetContext
  if(!Python.isStarted())Python.start(AndroidPlatform(context))
  val module=Python.getInstance().getModule("veyra_engine")
  assertTrue(module.callAttr("crypto_self_test").toBoolean())
  repeat(3){
   val engine=module.callAttr("Engine","00112233445566778899aabbccddeeff",0)
   try{val port=engine.callAttr("start").toInt();assertTrue(port>0);assertTrue(engine.get("live")!!.toBoolean())}
   finally{engine.callAttr("close")}
   assertFalse(engine.get("live")!!.toBoolean())
  }
 }
}
