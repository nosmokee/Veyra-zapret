# 1.1-beta.3 — correct CDN203 endpoint, diagnostic candidate

- Android / Windows / Linux: DC203 uses its own MTProto TCP server, not the DC2 WSS alias. Signed DC IDs and existing local key/port retained.
- Android: bounded Telegram diagnostic ring exported independently of native logcat; versionCode 11.
- Windows: clean packet-boundary client EOF is not a proxy failure; truncated packets and upstream errors remain failures.
- Linux: updated packages for x86_64/aarch64; native binaries unchanged per architecture.
- Experimental prerelease: actual user-account media, physical Android, native Windows and ARM execution remain unverified. See TELEGRAM-FIX-REPORT.md.

---

# Veyra 1.1 Beta / 1.1-beta.1

Beta release sources. See TEST-REPORT.md for test scope and limitations.
- Android: Veyra label, amber/charcoal, advanced entry points hidden, GitHub footer, accessible licenses,
  large-font Telegram action fixed; shared bypass + Telegram connect preserved. Original Next identity/signature.
- Linux: shared SOCKS + Telegram + separate proxied browser start; stable private Telegram endpoint;
  verified-TLS WSS bridge, pyaes fallback; amber responsive no-JavaScript local panel; Refresh/Ping actions.
- Windows: version1.1-beta.1 and warm-amber default; tested existing engine/Telegram logic retained.
  Windows Telegram still has a separate switch. No ChatGPT IP-change feature on any platform.

Read README.md and TEST-REPORT.md before testing. Full platform app sources are supplied;
Windows external native dependencies remain in the original engine archive with upstream source references.
