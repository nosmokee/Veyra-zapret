# Android 1.2.4: отдельный профиль быстрого старта

Эксперимент, не подтверждённое исправление задержки YouTube.

## Что изменено
Добавлен «YouTube · быстрый старт». По сравнению с «YouTube QUIC · fake» изменён ровно один параметр: TCP auto-retry timeout 5 -> 2 секунды. Payload QUIC, 11 повторов, TTL 8, разрешение QUIC и TCP split/disorder не менялись. Это может помочь только если задержка связана с TCP-повтором; ожидание внутри YouTube/QUIC данным параметром не контролируется. На медленной сети возможны лишние переподключения.

Рабочий «YouTube QUIC · fake» сохранён без изменения команды, его выбор не заменяется автоматически. Root не нужен. Новый профиль также затрагивает UDP/443 всех приложений внутри VPN. Windows не менялся.

## Проверка пользователем
1. Отключить Veyra Wi-Fi Test, установить APK обновлением без удаления.
2. Выбрать «YouTube · быстрый старт», подключить.
3. Принудительно остановить YouTube в настройках Android, открыть заново.
4. На том же Wi-Fi сравнить запуск нескольких обычных видео и 10 Shorts. Проверить, что после запуска нет остановок.
5. Если быстрее не стало или появились обрывы, отключить обход и вернуть «YouTube QUIC · fake».

## Локальная проверка
Robolectric unit/UI tests; integration-tests/test_fast_start.py проверяет 3 MiB TLS-передачи, обе fallback-группы, QUIC payload 11 повторов и non-443 UDP passthrough. Тестовый стенд учитывает, что UDP-группа сдвигает индексы TCP-групп на 1. Проверяются подпись и совместимость обновления с 1.2.3. Реальный YouTube, провайдер и Android-устройство здесь не тестировались. Уменьшение задержки не измерено и не гарантируется.

## Сборка
JDK17 + SDK34:

```sh
cd android
sh gradlew --no-daemon --max-workers=2 \
  -PveyraPrebuiltNative -PveyraIsolatedTest -PveyraWifiTest \
  -PveyraOfflineWheels=/absolute/path/to/build-tools/offline-python \
  testDebugUnitTest assembleDebug
```

Пакет io.github.nosmokee.veyra.next.wifitest, versionCode 21. Приватный тестовый signing key не включён: для своей сборки/релиза нужен свой ключ. Самостоятельная сборка с другим сертификатом не установится обновлением поверх выданного теста. Смотрите также ANDROID-QUIC-FAKE-TEST.md для происхождения payload и ограничений.
