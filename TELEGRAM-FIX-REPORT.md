# Veyra 1.1-beta.2 — Telegram stability checks

## Changes
- Android accepts signed DC203 as well as signed DC1–5; the relay identity is preserved. WSS hostname for DC203 uses the upstream DC2 alias, not a random substituted DC.
- Android uses provider-backed AES/CTR bulk encryption, buffered reads and bulk WebSocket masking. It allows 32 sessions with a correspondingly bounded worker pool (previously 16).
- Windows allows 64 sessions and serializes masked WebSocket payloads in bulk.
- Android and Windows try the official hostname before the fixed alternate official edge IP, remember successful TLS/WSS endpoints in memory, and retain hostname/certificate validation. Failed connections still fall back to the remaining official routes. No payload replay or claim of seamless live-session migration.
- Windows persists the chosen local Telegram port to telegram-port.txt. Restart reuses it; if occupied, startup fails visibly instead of silently changing the endpoint stored in Telegram. Existing key files are unchanged. Android's persisted key/port behavior is retained.

## Verification actually performed on this Linux host
- Full Android Gradle unit/Robolectric suite: 71 tests, zero failures. Includes listener restarts, saved key/port, occupied-port rejection, ownership, UI and existing service lifecycle tests.
- Additional Kotlin protocol run: 16 tests passed; 32 simultaneous loopback sessions, signed DC±203, exact 4 MiB synthetic media echo, chunked CTR, actual fragmented 2 MiB WebSocket message with intervening ping/pong.
- Same regression tests against beta.1 Android code reproduce DC203 rejection and the 16-session capacity limit. A separate route-order assertion also changes intentionally.
- Kotlin live account-free req_pq/resPQ exchanges: 36/36 through the bridge directly; another 36/36 through a local Linux ByeDPI SOCKS using the Base disorder/split/TLS-record arguments. Covers DC±1–5, DC±203 and all 3 supported obfuscated transport markers. Matching nonces verified. This is not Android VPN routing or an authenticated media request.
- C# tests running on Linux/.NET via PowerShell: AES NIST vector; all 4 framing variants; 64 simultaneous sessions; exact 4 MiB synthetic media echo; actual 4 MiB WebSocket masking/fragmentation and interleaved ping/pong; saved-port restart/occupied-port failure; 40 rapid close/rebind cycles; existing failure classification tests.
- C# live account-free req_pq/resPQ exchanges: 36/36 with matching nonce across the same signed DC/protocol matrix, through the local C# bridge to official TLS/WSS endpoints.
- Android release assemble successful. lintDebug: 0 errors, 52 warnings (not claimed warning-free).
- APK v1/v2/v3 verified. Same signing certificate as beta.1; applicationId io.github.nosmokee.veyra.next, versionCode 10, versionName 1.1-beta.2, visible name Veyra. Existing Android Debug development certificate retained, not a new production signing identity. All 8 native .so libraries unchanged byte-for-byte.
- Windows release x64 C#7.3 compiled against .NET Framework4.8 references. Original embedded native engine archive unchanged (SHA256 244314ae1c24538a0d751601da8e0c925c843371eec4456eb15f14c4fd6b7058). EXE is not Authenticode-signed. Existing native dependency licensing/source review limitation is unchanged; engine.zip is not added to the public source tree.

## Not verified / limits
No physical Android VPN/tun routing, native Windows WPF/WinDivert execution, user-account video/media download, battery/background behavior, ISP under restriction, or mobile whitelist bypass was tested here. Synthetic bytes and resPQ responses do NOT prove authenticated video playback or universal stability. TLS stays verified. No account credentials were used. No external IP change, Telegram calls, or LTE whitelist mode is added. Linux packages are unchanged from beta.1.

## Updating / old proxy settings
1. Stop other Veyra/Veyra Test versions and their proxy/bridge before testing. Do not uninstall the Android app or clear its data: that would reset its private key.
2. Install the new APK over canonical Veyra; on Windows fully exit Veyra, extract the new package, preserve the existing application data folder.
3. Start Veyra and its Telegram bridge (Windows still has its separate Telegram switch). If Telegram reports incorrect settings or points at an old test build, remove only the old Veyra proxy entry in Telegram and add the active proxy from Veyra once. Confirm/enable it in Telegram. Never publish the secret from a proxy link.
4. Test a previously uncached video, a file download, idle/reconnect, and Wi-Fi/mobile switching. If the error remains, export Android's journal / Windows telegram-diagnostic.txt; review before sharing. Do not send proxy secrets, phone/login codes, or chat contents.
