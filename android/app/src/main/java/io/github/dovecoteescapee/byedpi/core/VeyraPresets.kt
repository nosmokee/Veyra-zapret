package io.github.dovecoteescapee.byedpi.core

/** Explicit opt-in alternatives. No automatic configuration overwrite or success claim. */
object VeyraPresets {
    data class Preset(val id: String, val command: String)
    private const val LOCAL = "--ip 127.0.0.1 --port 1080 "
    // Split relative to the SNI hostname, not the first bytes of the TLS record header.
    // The second group is retried by the native engine on timeout/reset.
    const val YOUTUBE_COMMAND = LOCAL + "--timeout 3 --disorder 1+s --auto=torst --split 1+s --tlsrec 3+s"
    val entries = listOf(
        Preset("split-tls", LOCAL + "--split 1 --tlsrec 1"),
        Preset("disorder-tls", LOCAL + "--disorder 1 --tlsrec 1"),
        Preset("split2-tls", LOCAL + "--split 2 --tlsrec 1"),
        Preset("disorder", LOCAL + "--disorder 1"),
    )
}
