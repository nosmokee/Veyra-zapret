# 1.1-beta.2 — Telegram stability (Android / Windows)

- Android: signed DC203, 32 сессии, bulk AES-CTR / буферизация.
- Windows: 64 сессии, bulk WS frames, постоянный Telegram-порт.
- Обе платформы: приоритет официального DNS, кэш успешного TLS/WSS маршрута без изменения проверки TLS.
- Подробности проверок, обновления и ограничений: TELEGRAM-FIX-REPORT.md.
- Linux не менялся. Видеозагрузка из аккаунта, физический Android VPN и нативный Windows здесь не проверены.

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
