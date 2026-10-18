# Veyra 1.1 Beta

Локальный обход DPI с интерфейсом для Android и Linux. Внешний IP **не меняется**.
GitHub: https://github.com/nosmokee/Veyra-zapret

## Скачать и установить
Готовые файлы находятся в разделе [Releases](https://github.com/nosmokee/Veyra-zapret/releases).

| Платформа | Файл | Запуск |
|---|---|---|
| Android5.0+ | `Veyra-v1.1-beta.1-Android-universal.apk` | Установить APK |
| Linux Intel/AMD64 | `Veyra-v1.1-beta.1-Linux-x86_64.tar.gz` | Распаковать → `./veyra` |
| Linux ARM64 | `Veyra-v1.1-beta.1-Linux-aarch64.tar.gz` | Распаковать → `./veyra` |

Linux: Python3.9+ и Chromium/Chrome/Firefox; **без sudo**. Нативный ARM64-бинарник в этой среде не запускался.
Windows-код сохранён для разработки, но новый Windows-архив не публикуется до завершения сверки native-лицензий.

## Android
Жёлто-графитовая тема, нижняя навигация, скрытые расширенные настройки, GitHub в настройках.
Щит **«Подключить всё»** запускает локальный обход и Telegram-мост. Один раз разреши Android VPN
и добавь локальный прокси в самом Telegram кнопкой «Добавить». Затем используй общий щит.
Профили Base · SNI / TCP и Alt1–4. Перед сменой профиля отключи Veyra.
APK обновляет прежнюю Veyra Next с тем же тестовым сертификатом; старую отдельную Veyra Test (.unifiedtest)
не заменяет. Не запускай две копии одновременно и не удаляй данные без необходимости.
Подробнее: [Android](android/README-VEYRA.md).

## Linux
«Подключить всё» запускает SOCKS5 и Telegram-мост, затем отдельное окно браузера через прокси.
Основной браузерный профиль, системный прокси, маршруты и firewall не изменяются.
Другие приложения автоматически не переключаются. Один раз добавь прокси в Telegram.
Подробнее: [Linux](linux/README.md).

## Ограничения беты
- Не удалённый VPN: внешний IP не меняется, региональные ограничения ChatGPT не снимаются.
- Telegram-мост: сообщения/медиа; без звонков и Telegram Web.
- После остановки Veyra отключи её прокси в Telegram, если он мешает прямому соединению.
- «Обновить» — локальное состояние, не скачивание профилей.
- «Пинг» — время установки TLS/WSS-соединения до официального Telegram-узла через SOCKS, не ICMP.
- Автотесты и протокольная доступность не гарантируют YouTube/Telegram на каждом устройстве/провайдере.
- Не публикуй tg://-ссылки, закрытые ключи или непроверенные журналы.

## Исходники и сборка
Автоматические GitHub-архивы Source code содержат этот репозиторий с актуальным кодом беты.
Это **исходники**, а не готовые установщики.
- `android/`: Kotlin/XML, полные native/dependency sources и сохранённые JNI. JDK17, SDK34,
  `sh ./gradlew -PveyraPrebuiltNative assembleRelease`; release нужно подписать своим ключом.
  Для полной native-сборки без флага: NDK28.2.13676358, CMake3.22.1. Закрытого ключа подписи нет.
- `linux/`: Python/UI/bridge/pyaes и ByeDPI C-source. Сначала `sh ./BUILD-LINUX.sh` (Zig0.13.0), затем `sh ./veyra`.
- `windows/`: C#/WPF-приложение и тесты. Native `engine.zip` исключён до завершения лицензионной сверки;
  требуемый исходный хэш и инструкции — [BUILD](windows/source/BUILD.txt).

## Проверки и лицензии
[TEST-REPORT.md](TEST-REPORT.md) — реальные границы тестирования, [VISUAL-QA.md](VISUAL-QA.md) — визуальная проверка.
Это производный проект: авторство ByeDPI, ByeDPIAndroid, hev-socks5-tunnel и других зависимостей сохраняется.
Android — GPLv3 и отдельные лицензии компонентов: [LICENSE](android/LICENSE), [PROVENANCE](android/PROVENANCE.md).
Linux: [LICENSE](linux/LICENSE), [third parties](linux/THIRD-PARTY.md), `linux/LICENSES/`.
Windows: [notices](windows/THIRD-PARTY-NOTICES.txt), `windows/licenses/`. Единой лицензии на все компоненты нет.

При загрузке исходников через веб-интерфейс GitHub исполняемый бит shell-скриптов может отсутствовать.
Используй `sh ./gradlew`, `sh ./BUILD-LINUX.sh`, `sh ./veyra` или сначала `chmod +x` для нужного скрипта.
В готовых Linux TAR.GZ права запуска сохранены.
