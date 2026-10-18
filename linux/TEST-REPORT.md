# Veyra 1.1-beta.3 — CDN203 route correction / diagnostic candidate

## Why this update exists
The user reported repeated proxy disconnections and failed files/videos on Android 1.1-beta.2 and Windows. Private diagnostic files were inspected locally, not committed or published. The Windows tail contains many client-side EndOfStreamException events wrapped as generic IOException 0x80131620, plus cancelled WSS attempts; it does not establish the root cause of every disconnect. The Android report confirms beta.2, but its 500-line native logcat tail displaced Telegram-specific event details. Native `No route to host` entries cannot be attributed to Telegram from this report alone.

## Confirmed/reproduced defect
Beta.2 accepted DC203 but used the DC2 WSS hostname alias. A req_pq/resPQ nonce check was an inadequate regression test: it passed on either server. An independent account-free comparison of the public RSA fingerprint lists showed that the old DC2 alias reached a different RSA-key domain than Telegram CDN203 at 91.105.192.100:443. The corrected bridge route matches the actual CDN203 list. This is a routing defect relevant to CDN files, not proof that all user video/reconnect problems are fixed.

## Changes on all three platforms
- DC±203 now connects to the dedicated Telegram CDN203 endpoint via obfuscated MTProto TCP, NEVER the DC2 WSS alias. Signed DC identity and transport marker are preserved. Android/Linux route that TCP connection through their existing local SOCKS engine; Windows uses the existing system DPI-engine environment.
- Normal DC±1–5 retain authenticated official TLS/WSS and the existing fallback addresses. No TLS certificate bypass. The CDN203 connection is **not TLS/WSS**: Telegram's own MTProto client handles server authentication and inner encryption. The bridge does not decrypt chat/file contents. No account/login credentials or foreign relay is introduced.
- Windows: a clean client EOF at a packet boundary is no longer shown as the generic proxy failure. Truncated packets/payloads and upstream failures remain errors. This corrects error classification; it cannot by itself repair an unreachable server.
- Android: a separate bounded 256-event Telegram diagnostic ring is exported before optional logcat, includes DC, phase, connection time and exception-cause types, and excludes proxy keys/payloads. Native logcat traffic no longer crowds it out. The ring is process-local, not a persistent cross-restart history. A repeated Telegram error prefix was removed.
- Linux includes the previously pending signed-DC203 acceptance, 64-session cap and successful WSS-route cache changes; its CDN203 route is now corrected as above. Linux's unified connect/stop behavior remains. Windows still uses its separate Telegram switch.

## Verification actually performed here
- Android Gradle/Robolectric: 73 tests, 0 failures/errors/skips. Release APK assembled. lintDebug: 0 errors, 61 warnings; not warning-free.
- Additional Kotlin protocol suite: 18 tests passed, including 32 simultaneous sessions / 4 MiB synthetic echo, actual 2 MiB fragmented WebSocket/ping, CDN203 SOCKS destination and raw TCP byte preservation, bounded diagnostics and nested causes.
- Windows C# tests executed on Linux/.NET: raw TCP 1 MiB preservation, clean-EOF classification, saved-port restarts/occupied-port handling, 64 simultaneous sessions / 4 MiB synthetic echo, actual WebSocket framing/masking/fragmentation/ping, NIST AES-CTR and existing lifecycle/error tests. C# 7.3 Release x64 compiled against .NET Framework 4.8 references; NOT native Windows WPF/WinDivert execution.
- Kotlin live loopback bridge exchanges: 36/36 signed DC/protocol nonce matches, including actual CDN203 TCP; not Android VPN routing or an authenticated media request.
- C# live loopback bridge exchanges: 36 signed DC/protocol req_pq/resPQ nonce matches, including the actual CDN203 TCP endpoint after correction. These do NOT authenticate a CDN file session.
- Linux unit suite: 33 tests passed, including CDN routing/no-WebSocket-ping/raw 2 MiB stream and existing controller/lifecycle/HTTP tests.
- Linux live req_pq/resPQ matrix: 39 nonce matches (36 signed DC/protocol direct-profile + 3 DC2 Base-profile). CDN203 route public-key identity checked independently; `python3 linux/tests/cdn_identity.py` reproduces the old mismatch and corrected match without an account.
- Independent Telethon diagnostic on Linux before the CDN route change: anonymous normal-DC2 auth-key generation and encrypted ping/pong passed; a main-DC2 key reused on media DC-2 also passed. Six 512 KiB synthetic RPC bodies were transmitted and returned ConnectionNotInitedError, with following pings successful. This verifies encrypted transport, not successful file upload or media download. Fresh auth-key generation on a media-only endpoint is not a supported success criterion.

## Package and real-device limits
No authenticated account video/file download, physical Android VPN/tun/background behavior, native Windows WPF/WinDivert, user ISP restriction or LTE whitelist was reproduced here. Do not treat this candidate as confirmation of universal video playback. Direct CDN TCP can still be blocked by the user's network; no paid relay/VPS is added and no connection replay is attempted.
Linux x86_64 and aarch64 reuse their respective beta.1 native ciadpi binaries unchanged. x86_64 is executed on this host; ARM64 is architecture/hash inspected only, not executed. Without python3-cryptography Linux uses the vendored slower pure-Python AES fallback; optional distro python3-cryptography improves throughput but is not installed by the app.
Android keeps canonical applicationId io.github.nosmokee.veyra.next, versionCode 11 and the existing development signing identity so in-place updates retain the local proxy key/port. This is NOT a newly established production signing identity. Native .so libraries are unchanged. Windows is not Authenticode-signed; its native engine.zip is unchanged and its previous licensing/source-review limitation remains. No native engine binary is added to public source archives.

## Updating / physical verification required
1. Exit other Veyra/Test copies. Android: update in place, do not uninstall or clear data. Windows/Linux: stop the old process and preserve its application data/private key/port files.
2. Start Veyra (Windows: also its Telegram bridge). Existing proxy address/secret should remain. Re-add only the Veyra proxy entry once if Telegram still points at an old Test build; never share its secret.
3. Try a previously uncached video and file, idle/reconnect and Wi-Fi/mobile switching. If it fails, export the new diagnostic report immediately BEFORE restarting Veyra, so Android's process-local ring is retained.
4. No IP change, Telegram calls, Telegram Web mode, ChatGPT regional unblock or LTE whitelist bypass is provided.
