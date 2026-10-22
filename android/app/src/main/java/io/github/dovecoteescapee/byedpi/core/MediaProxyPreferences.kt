package io.github.dovecoteescapee.byedpi.core

import android.content.Context
import io.github.dovecoteescapee.byedpi.utility.getPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.security.MessageDigest

/** Resolve a bundled binary asset into one argv element, never shell-substitute a path. */
object MediaProxyPreferences {
    const val QUIC_SHA256 = "f4589c57749f956bb30538197a521d7005f8b0a8723b4707e72405e51ddac50a"
    suspend fun fromContext(context: Context): ByeDpiProxyPreferences = withContext(Dispatchers.IO) {
        val preferences = context.getPreferences()
        val original = ByeDpiProxyPreferences.fromSharedPreferences(preferences)
        if (original !is ByeDpiProxyCmdPreferences || !original.args.contains(VeyraPresets.QUIC_FAKE_TOKEN)) {
            return@withContext original
        }
        val bytes = context.assets.open("quic_initial_www_google_com.bin").use { it.readBytes() }
        val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
        check(hash == QUIC_SHA256) { "Некорректный комплектный QUIC-фейк" }
        val folder = File(context.filesDir, "media-assets").apply { check(isDirectory || mkdirs()) }
        val target = File(folder, "quic_initial_www_google_com.bin")
        if (!target.isFile || !target.readBytes().contentEquals(bytes)) {
            val temp = File.createTempFile("quic-", ".tmp", folder)
            try {
                temp.writeBytes(bytes)
                check(temp.renameTo(target)) { "Не удалось подготовить QUIC-фейк" }
            } finally { temp.delete() }
        }
        ByeDpiProxyCmdPreferences(original.args.map { if (it == VeyraPresets.QUIC_FAKE_TOKEN) target.absolutePath else it }.toTypedArray())
    }
}
