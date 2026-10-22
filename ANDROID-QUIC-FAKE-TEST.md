# Android 1.2.3 QUIC fake — эксперимент без root

Не подтверждённое исправление YouTube: требуется проверка на телефоне и сети пользователя.

## Установка и откат
Установить APK поверх Veyra Wi-Fi Test 1.2.2 (тот же applicationId и тестовый сертификат). Оригинальное приложение не заменяется. Остановить другие VPN/копии Veyra, выбрать «YouTube QUIC · fake», подключить. Принудительно остановить YouTube через настройки Android и открыть заново. Проверить обычное видео 3–5 минут и несколько Shorts. Если стало хуже, отключить и вернуть прежний профиль. Root не требуется.

## Изменение
Новый отдельный профиль ByeDPI: UDP/443 — 11 отправок комплектного QUIC Initial с TTL 8 перед настоящей датаграммой; TCP — прежние TLS split/disorder fallback. QUIC разрешён. Остальные UDP-порты проходят без фейков. Профиль затрагивает UDP/443 всех приложений в VPN, а не только YouTube. TTL 8 экспериментальный и может не подойти конкретной сети. Это не перенос всех Windows-стратегий winws.

Файл quic_initial_www_google_com.bin взят из официального Flowseal/zapret-discord-youtube 1.10.4; SHA256 f4589c57749f956bb30538197a521d7005f8b0a8723b4707e72405e51ddac50a. Источник: https://github.com/Flowseal/zapret-discord-youtube/releases/tag/1.10.4 . Лицензии исходных проектов сохранены.

Встроенный asset проверяется по SHA256 и копируется в приватный filesDir. В argv заменяется только отдельный токен пути; сохранённая команда не переписывается. Повреждённая копия восстанавливается. Нативные библиотеки и Telegram-движок не изменены в этой итерации.

## Проверки и ограничения
- Robolectric unit/UI tests, включая подготовку asset и сохранность прежних настроек.
- integration-tests/test_quic_fake.py: loopback SOCKS UDP, 11 точных копий asset + реальная датаграмма, возврат ответа; non-443 passthrough.
- integration-tests/test_youtube_tcp.py: 3 MiB TLS-передача и fallback на локальном сервере.
- APK signature/package/version inspection.

Эти тесты не доказывают обход DPI, работу реального YouTube или отсутствие буферизации на устройстве. Android emulator/физический телефон не запускался. Для стабильного релиза нужна отдельная device/network-проверка и собственный release signing key.

## Сборка
JDK 17, Android SDK 34, Gradle wrapper. Для тестового APK:

```sh
cd android
sh gradlew --no-daemon --max-workers=2 \
  -PveyraPrebuiltNative -PveyraIsolatedTest -PveyraWifiTest \
  -PveyraOfflineWheels=/absolute/path/to/build-tools/offline-python \
  testDebugUnitTest assembleDebug
```

Архив не содержит приватного тестового ключа: самостоятельно собранный APK нельзя установить обновлением поверх выданного тестового APK без своего совпадающего сертификата. Флаг veyraSigningStore позволяет указать свой debug keystore. Пакет теста io.github.nosmokee.veyra.next.wifitest, versionCode 20. Windows-пакет не менялся в этой итерации.
