package io.github.dovecoteescapee.byedpi.core

import android.content.SharedPreferences

/** One connect action prepares a known DPI profile AND the service-owned Telegram bridge.
 * Explicitly selected Alt profiles survive reconnects. Unconfigured/custom input falls back to Base
 * for the normal one-tap flow; opening/refreshing the dashboard never rewrites preferences. */
object UnifiedConnection {
    fun profileIndex(prefs:SharedPreferences):Int {
        if(!prefs.getBoolean("byedpi_enable_cmd_settings",false))return 0
        val command=prefs.getString("byedpi_cmd_args",null)
        if(command==VeyraPresets.YOUTUBE_COMMAND)return 0
        return (VeyraPresets.entries.indexOfFirst {it.command==command}+1).coerceAtLeast(0)
    }
    fun prepare(prefs:SharedPreferences) {
        val index=profileIndex(prefs)
        val command=if(index==0)VeyraPresets.YOUTUBE_COMMAND else VeyraPresets.entries[index-1].command
        check(prefs.edit().putBoolean("byedpi_enable_cmd_settings",true)
            .putString("byedpi_cmd_args",command).putString("byedpi_proxy_ip","127.0.0.1")
            .putString("byedpi_proxy_port","1080").putBoolean("veyra_block_quic",index==0)
            .putBoolean("veyra_telegram_enabled",true).commit()) {"Не удалось сохранить общий профиль подключения"}
    }
}
