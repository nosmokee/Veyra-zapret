# Veyra Wi-Fi Test 1.2.2

Отдельное тестовое приложение Android, package io.github.nosmokee.veyra.next.wifitest, code19, Android7+ arm64/x86_64.
Не обновляет основную Veyra или предыдущую Veyra Test. Подписано отдельным тестовым ключом, не ключом релиза владельца. Закрытый ключ в исходники не включён.

Цель: сравнить буферизацию YouTube через Wi-Fi с разрешённым QUIC. Причина остановок не установлена; это эксперимент, не подтверждённый фикс.

Остановить основную Veyra и предыдущую Veyra Test. Установить APK, открыть Veyra Wi-Fi Test, выбрать «YouTube Wi-Fi · QUIC» и нажать «Подключить всё», разрешить VPN. Принудительно остановить YouTube через настройки Android и открыть его заново. Проверить ролик 3–5 минут с перемоткой и 5–10 Shorts. Не запускать два обхода одновременно.

Параметры TCP у нового профиля совпадают с прежним YouTube TCP; отличается политика блокировки QUIC — новый профиль НЕ подавляет UDP/443. UDP fake не добавлялся. Дополнительный явный --cache-ttl 100800 равен дефолту ядра и служит только для различения сохранённых строк команд. Native .so, Telegram-ядро, Base/Alt и прежний YouTube TCP не менялись. Прежние профили оставлены для отката.

Разрешённый QUIC не означает успешный обход для QUIC: результат зависит от провайдера. Если стало хуже — остановить тест и вернуться к предыдущему приложению. Новое приложение имеет отдельные Telegram-настройки: для Telegram понадобится его собственный прокси; основной ключ не импортируется.

Сборка: JDK17, Android SDK34, Python3.13.

```sh
cd android
sh gradlew --no-daemon --max-workers=2 -PveyraPrebuiltNative -PveyraIsolatedTest -PveyraWifiTest -PveyraOfflineWheels=/absolute/path/to/build-dependencies/python-wheels testDebugUnitTest assembleDebug
```

Для сохранения своего тестового ключа можно дополнительно передать -PveyraSigningStore=/private/path/to/test.keystore (стандартная debug-конфигурация). Приложенный APK подписан ключом сборки, который в публичные исходники не входит.

Проверки: 93 unit/Robolectric теста PASS; 4 native loopback TLS-теста PASS; APK v2 signature verify PASS; отдельные package/label/ABI проверены. На реальном телефоне/Android VPN/провайдерской сети тесты не выполнялись. Успех воспроизведения не гарантирован.
Windows в этой итерации не менялся.
