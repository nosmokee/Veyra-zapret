# Veyra 1.1-beta.1 — verification

## Completed on Linux x86_64 build host
- Android: release/debug compilation successful; **67 unit/Robolectric tests, 0 failures**.
  Lint: **0 errors, 62 warnings** (not a clean-warning claim). APK non-debuggable; apksigner v1/v2/v3 verified.
  Canonical applicationId io.github.nosmokee.veyra.next, versionCode9; original Veyra Next test certificate.
  Live Kotlin bridge protocol: **30/30** official Telegram exchanges (signed DC±1…5 ×3 transport forms), no account/login.
- Linux: **31/31** unit/integration tests; actual native x86_64 process, SOCKS loopback, TLS,
  CSRF/Host/Origin, private-file rights, stable bridge port, occupied-port rollback, engine-crash cleanup,
  AES NIST vectors for accelerated and vendored fallback, framing/fragmentation/close/error handling.
  Live **33/33** protocol exchanges: 30 Direct all signed DCs +3 Base DC2 through actual SOCKS and bridge.
- Windows: Release x64 C#7.3/.NET4.8 compilation; core suite 88 profile/mode combinations,
  9 invalid ports, extraction/traversal/persistence/ownership; bridge NIST +4 forms +40 lifecycle cycles;
  **15/15** official Telegram exchanges (DC1…5 ×3 transports); diagnostic192-event bound/redaction;
  UI geometry/model tests passed. One test-only live counter race was fixed with bounded wait.
- Visual QA: Android Robolectric-native preview images at 360/390 widths and large fonts;
  off/on/starting/error/permission-wait/main/strategies/settings/dialogs and scroll bottoms inspected individually.
  Fixed Telegram large-font clipped button with wrap-content/min-height/padding.
  Linux actual HTML full-page desktop1280/mobile390, navigation/connected/error inspected individually;
  fixed mobile action row so browser action spans full width. No horizontal overflow in captures.
  Windows9 model-to-HTML previews inspected individually, desktop and narrow/error states.
  These Windows model images are NOT native WPF screenshots. Preview rates/time are fixtures.

## Not verified / release gates
- No physical Android device VPN routing, real YouTube video playback, Telegram account delivery or calls test.
- No native Windows WPF/tray/WinDivert driver execution on Windows.
- Linux aarch64 ELF structure validated only; not executed on ARM hardware.
- Linux desktop browser-launch arguments checked, but actual desktop/browser playback not tested.
- No specific ISP censorship environment simulated; protocol reachability on the build host is not a guarantee.
- No IP change, no ChatGPT regional-unblocking claim, no Telegram voice-call support.
- Android uses an existing TEST signing certificate, not a newly provisioned production release key.
- Windows bundled engine dependency versions/licenses/corresponding-source obligations need final verification
  before public distribution; upstream locations are listed, not represented as a completed compliance audit.

User has approved the Android app for beta publication. Physical platform/ISP tests and Windows dependency review remain incomplete.
