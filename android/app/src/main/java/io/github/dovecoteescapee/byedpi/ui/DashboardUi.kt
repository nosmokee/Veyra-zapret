package io.github.dovecoteescapee.byedpi.ui

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import com.google.android.material.button.MaterialButton
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.databinding.ActivityMainBinding

/** Presentation only. Rendering and navigation never start or change a network service. */
enum class DashboardPhase { Idle, Starting, Running, Stopping, Failed }
data class DashboardState(
    val phase:DashboardPhase=DashboardPhase.Idle,val profile:String="",val profileIndex:Int=-1,
    val message:String?=null,val ping:String?=null,val pingBusy:Boolean=false,
    val telegram:String="",val up:String="— B/s",val down:String="— B/s",val duration:String="00:00"
)
class DashboardUi(private val binding:ActivityMainBinding,private val select:(Int)->Unit) {
    private val context=binding.root.context
    var page="home";private set
    private var listStamp=""
    val names=listOf(context.getString(R.string.ui_base))+ (1..4).map {"Veyra Alt $it"}
    private val descriptions=listOf(R.string.ui_base_desc,R.string.ui_alt1_desc,R.string.ui_alt2_desc,R.string.ui_alt3_desc,R.string.ui_alt4_desc)
    fun showPage(next:String) {
        page=if(next in listOf("home","strategies","settings"))next else "home"
        binding.homePage.visibility=if(page=="home")View.VISIBLE else View.GONE
        binding.strategiesPage.visibility=if(page=="strategies")View.VISIBLE else View.GONE
        binding.settingsPage.visibility=if(page=="settings")View.VISIBLE else View.GONE
        for((id,button) in listOf("home" to binding.navHome,"strategies" to binding.navStrategies,"settings" to binding.navSettings)) {
            val active=id==page;val color=Color.parseColor(if(active) "#FFD15C" else "#B7B1A5")
            button.isSelected=active;button.setTextColor(color);button.iconTint=ColorStateList.valueOf(color)
            button.backgroundTintList=ColorStateList.valueOf(Color.parseColor(if(active) "#3B321F" else "#001B1C20"))
            button.strokeWidth=if(active)(context.resources.displayMetrics.density).toInt().coerceAtLeast(1) else 0
            button.strokeColor=ColorStateList.valueOf(Color.parseColor("#A58A43"))
        }
        binding.pageScroll.scrollTo(0,0)
    }
    fun render(state:DashboardState) {
        val busy=state.phase==DashboardPhase.Starting||state.phase==DashboardPhase.Stopping
        val running=state.phase==DashboardPhase.Running
        val action=context.getString(when(state.phase){DashboardPhase.Starting->R.string.ui_connecting;DashboardPhase.Stopping->R.string.ui_stopping;DashboardPhase.Running->R.string.ui_disconnect;else->R.string.ui_connect})
        binding.statusButton.connected=running;binding.statusButton.busy=busy;binding.statusButton.isEnabled=!busy;binding.statusButton.contentDescription=action
        binding.connectionAction.text=action
        binding.statusText.text=state.message ?: context.getString(when(state.phase){DashboardPhase.Running->R.string.ui_connected;DashboardPhase.Starting->R.string.ui_connecting;DashboardPhase.Stopping->R.string.ui_stopping;else->R.string.ui_disconnected})
        binding.statusText.setTextColor(Color.parseColor(if(state.phase==DashboardPhase.Failed) "#FFADA1" else "#B7B1A5"))
        binding.selectedProfile.text=state.profile.ifEmpty {context.getString(R.string.ui_custom)}
        binding.uploadRate.text=state.up;binding.downloadRate.text=state.down;binding.sessionTime.text=state.duration
        binding.uploadRate.contentDescription=context.getString(R.string.ui_upload)+": "+state.up
        binding.downloadRate.contentDescription=context.getString(R.string.ui_download)+": "+state.down
        binding.sessionTime.contentDescription=context.getString(R.string.ui_duration)+": "+state.duration
        binding.pingButton.isEnabled=!busy&&!state.pingBusy
        binding.pingResult.text=state.ping ?: context.getString(R.string.ui_ping_hint)
        binding.telegramSummary.text=state.telegram.ifEmpty {context.getString(R.string.ui_tg_off)}
        binding.telegramSettingsStatus.text=binding.telegramSummary.text
        binding.telegramButton.isEnabled=!busy;binding.telegramHomeButton.isEnabled=!busy
        binding.advancedButton.isEnabled=!busy&&!running
        for(button in listOf(binding.pingButton,binding.telegramButton,binding.telegramHomeButton,binding.advancedButton)) button.alpha=if(button.isEnabled)1f else 0.45f
        val stamp="${state.profileIndex}/$busy/$running"
        if(stamp!=listStamp) {
            listStamp=stamp;binding.strategyList.removeAllViews()
            for(index in names.indices) {
                val row=LayoutInflater.from(context).inflate(R.layout.item_veyra_strategy,binding.strategyList,false)
                row.findViewById<TextView>(R.id.strategy_name).text=names[index]
                row.findViewById<TextView>(R.id.strategy_description).setText(descriptions[index])
                row.isSelected=state.profileIndex==index
                row.findViewById<View>(R.id.strategy_check).visibility=if(row.isSelected)View.VISIBLE else View.INVISIBLE
                row.contentDescription=names[index]+if(row.isSelected) ", "+context.getString(R.string.ui_selected_badge) else ""
                row.setOnClickListener {select(index)}
                binding.strategyList.addView(row)
            }
        }
    }
}
