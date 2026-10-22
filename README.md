# Veyra 1.3

## Главное
Основной профиль называется **Veyra Base** и готов к общей кнопке «Подключить всё», без ручного перебора стратегий. При переходе на 1.3 Base выбирается один раз; последующий ручной выбор резервного варианта сохраняется. Ключи/порты Telegram и оформление не сбрасываются. Старые стратегии оставлены для отката.

- **Android:** Base — ранее проверенный пользователем QUIC-профиль с 3 фейками, TTL 8 и прежними TCP fallback-группами. Root не нужен. Небольшая задержка старта видео остаётся возможной. Профиль затрагивает UDP/443 всех приложений внутри VPN.
- **Windows:** Base — ранее сработавший standalone-профиль «YouTube 1», интегрированный с исходными правилами остальных сервисов. Windows использует winws; Android-параметры сюда не копировались.
- **Linux:** Base — TCP/SNI с fallback для отдельного SOCKS-браузера. QUIC в этом браузере выключен. Это не системный VPN и не перенос Android UDP-обхода.

## Файлы релиза
Veyra-v1.3-Android-64bit.apk; Veyra-v1.3-Windows-x64.zip; Veyra-v1.3-Linux-x86_64.tar.gz; Veyra-v1.3-Linux-aarch64.tar.gz; Source-code.zip / Source-code.tar.gz; SHA256SUMS.txt; TEST-REPORT.md; RELEASE-CHECKS.json.

Релиз: https://github.com/nosmokee/Veyra-zapret/releases/tag/v1.3 (доступен после публикации).

## Установка
**Android:** остановите старые копии/другие VPN. Установите APK поверх тестовой Veyra Wi-Fi Test, нажмите «Подключить всё», подтвердите Android VPN. Название приложения теперь Veyra, версия 1.3. Сохранены packageId io.github.nosmokee.veyra.next.wifitest и сертификат тестовой линейки, поэтому она обновляется без удаления. Оригинальная Veyra 1.2 с другим packageId/ключом НЕ заменяется; не удаляйте её ради установки. Ключ оригинального релиза не предоставлялся. Готовый APK — release-вариант, подписанный сохранённым ключом тестовой линейки; для долгосрочных релизов владелец должен контролировать собственный signing key. Android 7+, arm64-v8a/x86_64. Если нужно, принудительно остановите YouTube через настройки Android и откройте заново.

**Windows:** полностью выйдите из старой копии через трей. Распакуйте ВЕСЬ ZIP в новую папку и запустите Veyra.exe. Не переносите только EXE: рядом должны оставаться python-runtime и telegram-engine. Нужны Windows 10/11 x64 и .NET Framework 4.8. WinDivert может потребовать повышения прав. Не отключайте защиту ради запуска. Настройки — в прежней LocalAppData/ZapretDesk.

**Linux:** распакуйте архив своей архитектуры и выполните ./veyra. Нужны Python 3.10+, системный OpenSSL/libcrypto и Chromium/Chrome/Firefox. Python-зависимости включены. Root и pip при запуске готового архива не нужны. Обход работает в отдельном окне SOCKS-браузера; остальные приложения автоматически через него не идут.

После остановки отключите локальный прокси в Telegram, если он мешает прямой работе. Две версии одновременно не запускайте.

## Проверки и ограничения
Пользователь проверял Android-профиль на своём Wi-Fi: после 1.2.5 осталось небольшое ожидание старта; Windows standalone-профиль 1 ранее работал у пользователя. Это не подтверждение полного релиза 1.3 на всех платформах/провайдерах. Автоматические результаты и ограничения — TEST-REPORT.md. Реальная Windows/WPF/WinDivert, физический Android и Linux ARM64 агентом здесь не запускались. Проверки HTTPS не доказывают воспроизведение видео.

Внешний IP не меняется. Сеть и доступность сервисов не гарантируются. Telegram-движок не переписывался; звонки не поддерживаются. Сторонние CF/H2-ретрансляторы могут видеть IP/метаданные. Не публикуйте ключи, Telegram-сессии и непроверенные журналы.

## Исходники и сборка
Android: JDK17, SDK34, Gradle wrapper. Команда: cd android && sh gradlew -PveyraPrebuiltNative -PveyraWifiTest -PveyraSigningStore=/path/to/keystore -PveyraOfflineWheels=/path/to/build-tools/offline-python testReleaseUnitTest assembleRelease. Ключи в архив не входят. Без флага signingStore release подпишите самостоятельно; другой сертификат не обновит выданный APK.
Windows: windows/BUILD-RELEASE.txt, .NET48/WPF targeting pack; runtime и telegram-engine включаются полностью. Linux: linux/BUILD-LINUX.sh, для тестов cd linux && python3 -m unittest discover -s tests.

Лицензии и исходники сторонних компонентов сохранены в LICENSE/LICENSES/licenses/THIRD-PARTY. Telegram: Flowseal/tg-ws-proxy v1.11.2. QUIC payload Android — Flowseal 1.10.4; источник/SHA256 в ANDROID-QUIC-FAKE-TEST.md. Windows-ядро остаётся из исходного 1.10.3; обновление всех Windows-стратегий до 1.10.4 не заявляется.
