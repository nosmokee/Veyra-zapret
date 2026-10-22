package io.github.dovecoteescapee.byedpi.core

/** Explicit opt-in alternatives. No automatic configuration overwrite or success claim. */
object VeyraPresets {
    data class Preset(val id: String, val command: String)
    private const val LOCAL = "--ip 127.0.0.1 --port 1080 "
    // Split relative to the SNI hostname, not the first bytes of the TLS record header.
    // The second group is retried by the native engine on timeout/reset.
    const val YOUTUBE_COMMAND = LOCAL + "--timeout 3 --disorder 1+s --auto=torst --split 1+s --tlsrec 3+s"
    // Experimental opt-in profile: split TLS records on the FIRST handshake,
    // retry on reset/invalid TLS with disorder, then a plain TCP split.
    // Keep Base/Alt 1-4 byte-for-byte unchanged for an immediate rollback.
    const val YOUTUBE_TCP_COMMAND = LOCAL + "--timeout 5 --split 1+s --tlsrec 3+s " +
        "--auto=torst,ssl_err --disorder 1+s --auto=torst,ssl_err --split 1"
    // Controlled comparison: same TCP strategy with QUIC allowed, no UDP fakes.
    // The explicit existing cache default only distinguishes persisted command identity.
    const val YOUTUBE_WIFI_COMMAND = YOUTUBE_TCP_COMMAND + " --cache-ttl 100800"
    const val QUIC_FAKE_TOKEN = "@VEYRA_QUIC_FAKE@"
    // UDP/443 gets the bundled Flowseal QUIC payload; other UDP falls through unchanged.
    // Following TCP groups are identical to the previous YouTube TCP strategy.
    const val YOUTUBE_QUIC_FAKE_COMMAND = LOCAL + "--timeout 5 --proto udp --pf 443 --udp-fake 11 --ttl 8 --fake-data " +
        QUIC_FAKE_TOKEN + " --auto=none --split 1+s --tlsrec 3+s " +
        "--auto=torst,ssl_err --disorder 1+s --auto=torst,ssl_err --split 1"
    // Controlled comparison: only TCP auto-retry timeout changes (5 -> 2 seconds).
    // QUIC payload, repetitions, TTL and the established profile stay untouched.
    const val YOUTUBE_FAST_START_COMMAND = LOCAL + "--timeout 2 --proto udp --pf 443 --udp-fake 11 --ttl 8 --fake-data " +
        QUIC_FAKE_TOKEN + " --auto=none --split 1+s --tlsrec 3+s " +
        "--auto=torst,ssl_err --disorder 1+s --auto=torst,ssl_err --split 1"
    // A/B against the established QUIC fake profile: only repetitions 11 -> 3.
    const val YOUTUBE_QUIC_LIGHT_COMMAND = LOCAL + "--timeout 5 --proto udp --pf 443 --udp-fake 3 --ttl 8 --fake-data " +
        QUIC_FAKE_TOKEN + " --auto=none --split 1+s --tlsrec 3+s " +
        "--auto=torst,ssl_err --disorder 1+s --auto=torst,ssl_err --split 1"
    const val BASE_COMMAND = YOUTUBE_QUIC_LIGHT_COMMAND
    fun blocksQuic(index: Int): Boolean =
        entries.getOrNull(index - 1)?.id in setOf("youtube-tcp", "legacy-base")
    val entries = listOf(
        Preset("split-tls", LOCAL + "--split 1 --tlsrec 1"),
        Preset("disorder-tls", LOCAL + "--disorder 1 --tlsrec 1"),
        Preset("split2-tls", LOCAL + "--split 2 --tlsrec 1"),
        Preset("disorder", LOCAL + "--disorder 1"),
        Preset("youtube-tcp", YOUTUBE_TCP_COMMAND),
        Preset("youtube-wifi", YOUTUBE_WIFI_COMMAND),
        Preset("youtube-quic-fake", YOUTUBE_QUIC_FAKE_COMMAND),
        Preset("youtube-fast-start", YOUTUBE_FAST_START_COMMAND),
        Preset("legacy-base", YOUTUBE_COMMAND),
    )
}
