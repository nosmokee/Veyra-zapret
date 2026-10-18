# Current: Veyra 1.1-beta.1 — Android

Visible label Veyra; canonical applicationId io.github.nosmokee.veyra.next; versionCode 9.
Amber/charcoal native UI; advanced-settings entry points hidden; GitHub link in Settings;
license texts accessible from About. Unified service/Telegram lifecycle retained.
Original private test signing key was recovered after the historical 0.2.2 isolated build.
This APK uses the original Veyra Next certificate, NOT the .unifiedtest identity.
Signing certificate SHA256: 16418e83a8a4aa086003b2ebdc3562005a4138a96cbf57e08781963aa9b6d238.
Private signing material is NOT included. This is a test certificate, not a production-key migration.
Network/native libraries unchanged; local bypass does not change public IP.
67 host tests passed, lint 0 errors / 62 warnings, 30 account-free protocol exchanges passed.
No physical Android VPN/video/account end-to-end test performed. Android approved by the user for beta publication.
Licenses, original source authorship and corresponding Android/native sources retained.

## Historical records — statements below apply only to the named older builds

# Historical: Veyra 0.2.2-test.1

Общий запуск Base/выбранного Alt и Telegram, стабильный локальный Telegram-порт, одноразовое предложение добавления прокси. Нативные библиотеки и команды не менялись. После сбоя восстановлено из архива 0.2.1 и сохранённых изменений с повторной сборкой/тестами. Прежний закрытый ключ недоступен; выданный тестовый APK использует отдельный applicationId `.unifiedtest` и новую тестовую подпись, не обновляет старую установку. Ничего не опубликовано.

Исходные авторы, полный GPL-код Android и лицензии сохранены. Документация прежних версий ниже — историческая.

---

# Historical: Veyra Next 0.2.1-test.1 — Android UI update

Same applicationId io.github.nosmokee.veyra.next; versionCode 7; same test signing certificate as 0.2.0.
Native Android dashboard inspired by the user-provided dark-green/lime reference. No INCY branding,
server infrastructure, countries or subscription claims copied. New shield is a simple original vector
control, not a VPN-server or security guarantee. New bottom tabs, local refresh, true tunnel-rate sampling,
service-state uptime and measured Telegram WSS setup timing. Full HTTPS diagnostic actions removed from UI.
Working Telegram bridge/runtime, service routing, presets and all eight JNI libraries unchanged from 0.2.0.
ByeDpiStatus adds a monotonic timestamp for display only; existing lifecycle phases are unchanged.
No ChatGPT regional/IP workaround, new relay, authentication, telemetry or remote service added.
No release/publication performed. User reported Telegram working on previous 0.2.0, not a lab guarantee.
Robolectric and androidx.test are test-only dependencies, not included as runtime dependencies in the APK.
GPLv3 and all upstream licenses/attributions are retained. Current verification: verification/ui-0.2.1.

# Historical: Veyra Next 0.2.0-test.1

Derived from the supplied Veyra Android 0.1.4 source and APK.
New app ID io.github.nosmokee.veyra.next; versionCode6, separate test key.
Adds independently written Kotlin local MTProxy/WSS bridge, official route
fallbacks, strict TLS, UI opt-in and service-owned lifecycle, ChatGPT diagnostics,
Veyra profile display names. No native engine changes. Exact prebuilt native
libraries and hashes included; original modified C/C++ source below is retained.
The earlier same-key update instructions below describe historical builds only.
Telegram protocol/routing reference: https://github.com/Flowseal/tg-ws-proxy
MIT reference notice included separately. No external relay or account auth added.
Android fork remains GPLv3; dependency licenses remain in their source folders.

# Historical 0.1.4 provenance

# Veyra Android 0.1.4 — provenance and modifications

## Android
Upstream: https://github.com/dovecoteescapee/ByeDPIAndroid
Commit: 43db0955de687d43f9473374dc7c5548eaa55359
License: GPLv3 (Android/LICENSE); see each native dependency License/LICENSE.
This is a derivative fork, not an original implementation of DPI evasion.
Submodule source copies are fully included in this archive, at these original revisions:

```
 078842b084853bc30f33eaaec7acc510cf67e560 app/src/main/cpp/byedpi (078842b)
 677bb4530cfc867cd44d88d298960c8d8d9fbfad app/src/main/jni/hev-socks5-tunnel (677bb45)
 103d99705afcc7663a7e7dcee11a94850da263e0 app/src/main/jni/hev-socks5-tunnel/src/core (103d997)
 b2be0b33a236b4776ff92425f61bfa12b2a2a6fd app/src/main/jni/hev-socks5-tunnel/third-part/hev-task-system (b2be0b3)
 9f5fa882d5a2675aae9907be21b463359ba8b632 app/src/main/jni/hev-socks5-tunnel/third-part/lwip (9f5fa88)
 162227cd7d2b6108bc8bc133273e11413222ddf4 app/src/main/jni/hev-socks5-tunnel/third-part/yaml (162227c)

```
Modified: app ID io.github.nosmokee.veyra.test, version/branding/vector logo/main layout and notices,
Russian resources; Veyra local VPN session/notification/log-export naming; manifest disallows backup
and external SettingsActivity; dynamic app receiver is not exported on supported Android versions;
NDK pinned to 28.2.13676358, 16KB ELF linker flags, legacy extracted JNI packaging.
Existing DPI engine and tunnel retained; service lifecycle and JNI ownership modified in 0.1.2 (see below). Original package/namespace
retained for JNI compatibility; application ID intentionally separate. Telegram module not added.
Original code attribution and upstream source links retained. No account/password collection added.
Default Android DNS remains upstream 1.1.1.1; no remote Veyra VPN server added.
This snapshot includes full modified app and recursive native dependency source, GPL text and build scripts.
Prebuilt AndroidX/Kotlin/Material/PreferenceX dependencies resolved by Gradle retain their own licenses;
see app/build.gradle.kts for exact versions. Their source projects and notices are not re-licensed as Veyra.
SDK/NDK/JDK/Gradle caches, generated output and signing keys excluded. Builds download tool/dependency artifacts.


## Android 0.1.1 additions
UI name Veyra, author link https://github.com/nosmokee, upstream attribution retained separately. Opt-in local TCP/TLS presets and tests added. VersionCode2; same applicationId/signing key for upgrade. No native engine changes. Removing Test from UI does not establish stable support or successful YouTube bypass.

## Android 0.1.2 additions
VersionCode3; same applicationId and signing certificate for installation over previous APK.
Connection StateFlow, serialized service lifecycle, IO teardown and owned native worker;
local SOCKS readiness checks and native tunnel status monitor.
Native JNI proxy: defer global parameter reset and CLI argument release until event loop exits;
stop requests shutdown only. hev-jni.c: atomic worker status and TProxyGetStatus JNI method.
User-initiated fixed-target, verified-TLS connection diagnostics, selectable report and log export.
JVM regression tests and Linux-host native lifecycle harness added. No Android device/VPN/video
verification available; these changes do not establish successful YouTube bypass.

## Android 0.1.3 additions
VersionCode4, same ID and signing certificate. Diagnostic report is persisted in private
app preferences, restored after activity recreation, and can be copied to the clipboard
by an explicit user action. Export writes/flushed report first, then optional bounded
logcat capture; storage failures reported visibly. TLS diagnostics show failure stage
and elapsed time. Four JVM export regression tests added. No bypass strategy or native
engine changes in 0.1.3; YouTube connectivity on the reported Xiaomi/network not verified.

## Android 0.1.4 additions
VersionCode5, same ID/certificate. Explicit opt-in YouTube SNI/TCP compatibility action replaces
current CLI profile with SNI-relative disorder plus native torst auto fallback to split/tlsrec.
Native modifications: byedpi/params.h adds veyra_block_quic, proxy.c suppresses only outgoing
SOCKS UDP destination port 443 when explicitly enabled, native-lib.c exposes jniSetBlockQuic.
Kotlin configures policy before event loop startup. DNS/53 and other UDP ports remain enabled.
Policy affects all proxied UDP/443, not only YouTube; TCP fallback is client-dependent.
Settings can disable it; selecting an old preset also disables it. No automatic settings migration.
Linux-host JNI verification includes verified local TLS+SNI payload, UDP policy toggling, TCP cycles.
Full modified engine sources provided. Android device/blocked-network/video success not verified.

Additional 0.1.4 native change: extend.c captures receive errno before logging and treats
EOF independently from stale errno. Local injected-reset harness ran three times successfully,
with desync group index 1 observed. An earlier pre-fix harness run failed during the injected
reset; retained verification material records both initial failure and final reruns. These
local checks do not establish success in the user's Wi-Fi network.
