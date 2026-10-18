package io.github.dovecoteescapee.byedpi.activities

import android.Manifest
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.widget.Toast
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.dovecoteescapee.byedpi.core.UnifiedConnection
import io.github.dovecoteescapee.byedpi.core.TelegramRuntime
import io.github.dovecoteescapee.byedpi.core.VeyraPresets
import kotlinx.coroutines.withContext
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import io.github.dovecoteescapee.byedpi.services.connectionState
import io.github.dovecoteescapee.byedpi.services.ConnectionPhase
import io.github.dovecoteescapee.byedpi.R
import io.github.dovecoteescapee.byedpi.data.*
import io.github.dovecoteescapee.byedpi.fragments.MainSettingsFragment
import io.github.dovecoteescapee.byedpi.databinding.ActivityMainBinding
import io.github.dovecoteescapee.byedpi.services.ServiceManager
import io.github.dovecoteescapee.byedpi.services.appStatus
import io.github.dovecoteescapee.byedpi.utility.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import io.github.dovecoteescapee.byedpi.core.DashboardFormatting
import io.github.dovecoteescapee.byedpi.core.ConnectionPing
import io.github.dovecoteescapee.byedpi.core.TProxyService
import io.github.dovecoteescapee.byedpi.ui.*
import android.content.ClipboardManager
import android.content.ClipData
import io.github.dovecoteescapee.byedpi.core.DiagnosticLogWriter
import io.github.dovecoteescapee.byedpi.core.BoundedLogcat

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private lateinit var dashboard: io.github.dovecoteescapee.byedpi.ui.DashboardUi
    private var waitingForVpnConsent = false
    private var pingBusy = false
    private var pingLabel: String? = null
    private val rates = io.github.dovecoteescapee.byedpi.core.RateSampler()
    private var upRate = "— B/s"
    private var downRate = "— B/s"

    companion object {
        private val TAG: String = MainActivity::class.java.simpleName

        private const val REPORT_PREFS = "veyra_diagnostics"
        private const val REPORT_KEY = "last_report"
    }

    private val vpnRegister =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            waitingForVpnConsent = false
            if (it.resultCode == RESULT_OK) {
                ServiceManager.start(this, Mode.VPN)
            } else {
                Toast.makeText(this, R.string.vpn_permission_denied, Toast.LENGTH_SHORT).show()
                updateStatus()
            }
        }

    private fun savedReport(): String = getSharedPreferences(REPORT_PREFS, MODE_PRIVATE)
        .getString(REPORT_KEY, null) ?: "Veyra ${io.github.dovecoteescapee.byedpi.BuildConfig.VERSION_NAME}\nДиагностика соединения ещё не запускалась."

    private val logsRegister =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                val uri = result.data?.data
                if (uri == null) {
                    Toast.makeText(this, R.string.logs_failed, Toast.LENGTH_LONG).show()
                } else {
                    // The file picker may recreate the activity: load the report from persistent storage.
                    val report = savedReport()
                    lifecycleScope.launch {
                        val outcome = withContext(Dispatchers.IO) {
                            runCatching {
                                val stream = contentResolver.openOutputStream(uri, "wt")
                                    ?: error("Не удалось открыть выбранный файл")
                                stream.use { output ->
                                    // Write and flush the report BEFORE collecting optional logcat.
                                    DiagnosticLogWriter.write(output, report) { BoundedLogcat.collect(cacheDir).replace(Regex("(?i)tg://proxy[^\\s\"<>]+"),"[local proxy link redacted]") }
                                }
                            }
                        }
                        outcome.fold(
                            onSuccess = { Toast.makeText(this@MainActivity, R.string.logs_saved, Toast.LENGTH_LONG).show() },
                            onFailure = { error ->
                                Log.e(TAG, "Failed to export diagnostics", error)
                                MaterialAlertDialogBuilder(this@MainActivity)
                                    .setTitle(R.string.logs_failed)
                                    .setMessage(getString(R.string.logs_failure_help, error.message ?: error.javaClass.simpleName))
                                    .setPositiveButton(android.R.string.ok, null).show()
                            }
                        )
                    }
                }
            }
        }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            Log.d(TAG, "Received intent: ${intent?.action}")

            if (intent == null) {
                Log.w(TAG, "Received null intent")
                return
            }

            val senderOrd = intent.getIntExtra(SENDER, -1)
            val sender = Sender.entries.getOrNull(senderOrd)
            if (sender == null) {
                Log.w(TAG, "Received intent with unknown sender: $senderOrd")
                return
            }

            when (val action = intent.action) {
                STARTED_BROADCAST,
                STOPPED_BROADCAST -> updateStatus()

                FAILED_BROADCAST -> {
                    Toast.makeText(
                        context,
                        getString(R.string.failed_to_start, sender.name),
                        Toast.LENGTH_SHORT,
                    ).show()
                    updateStatus()
                }

                else -> Log.w(TAG, "Unknown action: $action")
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val intentFilter = IntentFilter().apply {
            addAction(STARTED_BROADCAST)
            addAction(STOPPED_BROADCAST)
            addAction(FAILED_BROADCAST)
        }

        @SuppressLint("UnspecifiedRegisterReceiverFlag")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, intentFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(receiver, intentFilter)
        }

        waitingForVpnConsent = savedInstanceState?.getBoolean("vpn_consent_pending") ?: false
        dashboard = DashboardUi(binding) { selectStrategy(it) }
        dashboard.showPage(savedInstanceState?.getString("page") ?: "home")
        binding.navHome.setOnClickListener { dashboard.showPage("home") }
        binding.navStrategies.setOnClickListener { dashboard.showPage("strategies") }
        binding.navSettings.setOnClickListener { dashboard.showPage("settings") }
        binding.presetButton.setOnClickListener { dashboard.showPage("strategies") }
        binding.telegramButton.setOnClickListener { showTelegram() }
        binding.telegramHomeButton.setOnClickListener { showTelegram() }
        binding.aboutButton.setOnClickListener {
            MaterialAlertDialogBuilder(this).setTitle("Veyra ${io.github.dovecoteescapee.byedpi.BuildConfig.VERSION_NAME}")
                .setMessage(R.string.ui_about_text).setPositiveButton(android.R.string.ok,null)
                .setNeutralButton("Лицензии") { _, _ ->
                    val files=assets.list("licenses")?.sorted()?.toTypedArray() ?: emptyArray()
                    MaterialAlertDialogBuilder(this).setTitle("Лицензии компонентов").setItems(files) { _, index ->
                        val text=assets.open("licenses/${files[index]}").bufferedReader().use { it.readText() }
                        MaterialAlertDialogBuilder(this).setTitle(files[index]).setMessage(text).setPositiveButton(android.R.string.ok,null).show()
                    }.setPositiveButton(android.R.string.ok,null).show()
                }.show()
        }
        binding.githubButton.setOnClickListener {
            runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://github.com/nosmokee/Veyra-zapret"))) }
        }
        binding.exportButton.setOnClickListener { exportLogs() }
        binding.refreshButton.setOnClickListener {
            lifecycleScope.launch { refreshMetrics();updateStatus();Toast.makeText(this@MainActivity,R.string.ui_refreshed,Toast.LENGTH_SHORT).show() }
        }
        binding.pingButton.setOnClickListener { ping() }
        binding.statusButton.setOnClickListener {
            val snapshot=connectionState.value
            if(!snapshot.busy) {if(snapshot.phase==ConnectionPhase.Running)stop() else start()}
        }
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {connectionState.collect { if(it.phase!=ConnectionPhase.Running){rates.reset();upRate="— B/s";downRate="— B/s";pingLabel=null};updateStatus()} }
                launch {while(isActive){refreshMetrics();updateStatus();delay(1000)}}
            }
        }
        updateStatus()

        val theme = getPreferences()
            .getString("app_theme", null)
        MainSettingsFragment.setTheme(theme ?: "system")

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(receiver)
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        return false
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val (status, _) = appStatus

        return when (item.itemId) {
            R.id.action_save_logs -> {exportLogs();true}

            else -> super.onOptionsItemSelected(item)
        }
    }

    override fun onSaveInstanceState(outState:Bundle) {
        outState.putString("page",dashboard.page)
        outState.putBoolean("vpn_consent_pending",waitingForVpnConsent)
        super.onSaveInstanceState(outState)
    }

    private fun profileIndex():Int = UnifiedConnection.profileIndex(getPreferences())
    private fun profileName():String=profileIndex().let {if(it<0)getString(R.string.ui_custom) else dashboard.names[it]}

    private fun selectStrategy(index:Int) {
        if(index !in 0..4)return
        if(appStatus.first!=AppStatus.Halted || connectionState.value.busy){Toast.makeText(this,R.string.ui_strategy_locked,Toast.LENGTH_SHORT).show();return}
        MaterialAlertDialogBuilder(this).setTitle(dashboard.names[index]).setMessage(R.string.ui_strategy_warning)
            .setPositiveButton(R.string.ui_apply) {_,_->
                if(appStatus.first==AppStatus.Halted && !connectionState.value.busy) {
                    val command=if(index==0)VeyraPresets.YOUTUBE_COMMAND else VeyraPresets.entries[index-1].command
                    getPreferences().edit().putString("byedpi_mode","vpn").putString("byedpi_proxy_ip","127.0.0.1")
                        .putString("byedpi_proxy_port","1080").putBoolean("byedpi_enable_cmd_settings",true)
                        .putString("byedpi_cmd_args",command).putBoolean("veyra_block_quic",index==0).apply()
                    pingLabel=null;updateStatus();dashboard.showPage("home")
                    Toast.makeText(this,R.string.ui_strategy_saved,Toast.LENGTH_SHORT).show()
                }
            }.setNegativeButton(android.R.string.cancel,null).show()
    }

    private fun addTelegramProxy() {
        val tg=TelegramRuntime.bridge
        if(tg==null || !tg.running) {Toast.makeText(this,R.string.ui_tg_connect_first,Toast.LENGTH_LONG).show();return}
        runCatching {startActivity(Intent(Intent.ACTION_VIEW,android.net.Uri.parse(tg.link)))}
            .onFailure {Toast.makeText(this,"Telegram не установлен или не обрабатывает ссылку",Toast.LENGTH_LONG).show()}
    }

    private fun showTelegram() {
        if(connectionState.value.busy)return
        val tg=TelegramRuntime.bridge
        if(tg==null || !tg.running) {Toast.makeText(this,R.string.ui_tg_connect_first,Toast.LENGTH_LONG).show();return}
        addTelegramProxy()
    }

    private fun maybeOfferTelegramSetup() {
        if(!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))return
        if(connectionState.value.phase!=ConnectionPhase.Running || TelegramRuntime.bridge?.running!=true)return
        val prefs=getPreferences()
        if(prefs.getBoolean("veyra_telegram_setup_offered",false))return
        prefs.edit().putBoolean("veyra_telegram_setup_offered",true).apply()
        MaterialAlertDialogBuilder(this).setTitle(R.string.ui_tg_once_title)
            .setMessage(R.string.ui_tg_once_message)
            .setPositiveButton(R.string.ui_tg_add) {_,_->addTelegramProxy()}
            .setNegativeButton(R.string.ui_later,null).show()
    }

    private fun telegramSummary():String {
        val tg=TelegramRuntime.bridge
        return when {
            tg!=null && tg.running && tg.error.isNotEmpty()->"Telegram: ${tg.error}"
            tg!=null && tg.running && (tg.up.get()>0 || tg.down.get()>0)->getString(R.string.ui_tg_traffic,DashboardFormatting.bytes(tg.up.get()),DashboardFormatting.bytes(tg.down.get()))
            tg!=null && tg.running->getString(R.string.ui_tg_ready)
            else->getString(R.string.ui_tg_pending)
        }
    }

    private suspend fun refreshMetrics() {
        val snapshot=connectionState.value
        if(snapshot.phase!=ConnectionPhase.Running){rates.reset();upRate="— B/s";downRate="— B/s";return}
        if(snapshot.mode!=Mode.VPN){upRate="— B/s";downRate="— B/s";return}
        val values=withContext(Dispatchers.IO){runCatching {TProxyService.TProxyGetStats()}.getOrNull()}
        if(connectionState.value!=snapshot)return
        val result=if(values!=null && values.size>=4)rates.sample(values[1],values[3],System.nanoTime()) else null
        upRate=result?.first?.let {DashboardFormatting.bytes(it)+"/s"} ?: "— B/s"
        downRate=result?.second?.let {DashboardFormatting.bytes(it)+"/s"} ?: "— B/s"
    }

    private fun ping() {
        val snapshot=connectionState.value
        if(pingBusy || snapshot.busy)return
        if(snapshot.phase!=ConnectionPhase.Running){Toast.makeText(this,R.string.ui_ping_none,Toast.LENGTH_SHORT).show();return}
        pingBusy=true;pingLabel=getString(R.string.ui_ping_wait);updateStatus()
        val port=getPreferences().getStringNotNull("byedpi_proxy_port","1080").toIntOrNull() ?: 1080
        lifecycleScope.launch {
            try {
                val result=withContext(Dispatchers.IO){runCatching {ConnectionPing.measure(port)}}
                if(connectionState.value.phase==ConnectionPhase.Running && connectionState.value.sinceNanos==snapshot.sinceNanos) {
                    pingLabel=result.fold({getString(R.string.ui_ping_value,it)},{getString(R.string.ui_ping_failed)})
                } else pingLabel=null
            } finally {pingBusy=false;updateStatus()}
        }
    }

    private fun exportLogs() {
        val snapshot=connectionState.value
        val report=getString(R.string.ui_export_report,io.github.dovecoteescapee.byedpi.BuildConfig.VERSION_NAME,
            snapshot.phase.name,profileName(),pingLabel ?: "—",telegramSummary())
        getSharedPreferences(REPORT_PREFS,MODE_PRIVATE).edit().putString(REPORT_KEY,report).apply()
        logsRegister.launch(Intent(Intent.ACTION_CREATE_DOCUMENT).apply {addCategory(Intent.CATEGORY_OPENABLE);type="text/plain";putExtra(Intent.EXTRA_TITLE,"Veyra-diagnostics.txt")})
    }

    private fun start() {
        if(waitingForVpnConsent || connectionState.value.busy || connectionState.value.phase==ConnectionPhase.Running)return
        try {
            UnifiedConnection.prepare(getPreferences())
            check(getPreferences().edit().putString("byedpi_mode","vpn").commit())
        } catch(e:Exception) {
            io.github.dovecoteescapee.byedpi.services.setPhase(ConnectionPhase.Failed,Mode.VPN,e.message)
            updateStatus();return
        }
        val permission=VpnService.prepare(this)
        if(permission!=null) {
            waitingForVpnConsent=true
            try {vpnRegister.launch(permission)} catch(e:Exception) {
                waitingForVpnConsent=false
                io.github.dovecoteescapee.byedpi.services.setPhase(ConnectionPhase.Failed,Mode.VPN,e.message)
            }
        } else ServiceManager.start(this,Mode.VPN)
        updateStatus()
    }

    private fun stop() {
        ServiceManager.stop(this)
    }

    private fun updateStatus() {
        if(!::dashboard.isInitialized)return
        val snapshot=connectionState.value
        val phase=if(waitingForVpnConsent)DashboardPhase.Starting else when(snapshot.phase){ConnectionPhase.Running->DashboardPhase.Running;ConnectionPhase.Starting->DashboardPhase.Starting;ConnectionPhase.Stopping->DashboardPhase.Stopping;ConnectionPhase.Failed->DashboardPhase.Failed;else->DashboardPhase.Idle}
        val duration=if(snapshot.sinceNanos>0)DashboardFormatting.duration(System.nanoTime()-snapshot.sinceNanos) else "00:00"
        dashboard.render(DashboardState(phase,profileName(),profileIndex(),if(waitingForVpnConsent)getString(R.string.ui_vpn_consent) else if(phase==DashboardPhase.Failed)snapshot.error else null,pingLabel,pingBusy,telegramSummary(),upRate,downRate,duration))
        val prefs=getPreferences()
        binding.proxyAddress.text=getString(R.string.proxy_address,prefs.getStringNotNull("byedpi_proxy_ip","127.0.0.1"),prefs.getStringNotNull("byedpi_proxy_port","1080"))
        maybeOfferTelegramSetup()
    }
}
