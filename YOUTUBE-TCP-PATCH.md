# Veyra 1.2.1-youtube-test — тестовые сборки Windows и Android

Основа: nosmokee/Veyra-zapret, commit 669383363bfbb3d070c3ffad55827b71f7296b95, плюс предоставленный владельцем Windows ZIP 1.2 и Source-code.zip.
Не публичный релиз; в GitHub ничего не записано (подключение отказало в создании ветки: 403).

## Windows

Готовый ZIP содержит Veyra.exe, полный исходный Python runtime и неизменённый Telegram-движок.
Закрыть старую Veyra и отдельный winws-тест, распаковать ZIP целиком в новую папку, запустить Veyra.exe, выбрать «YouTube 1», нажать «Подключить всё».
Существующий выбор стратегии, Telegram-ключ и порт не сбрасываются. Для свежих настроек дефолт — YouTube 1.
Новый профиль стоит первым и содержит приоритетные TCP/QUIC-правила, эквивалентные сработавшему отдельному тесту №1; затем неизменённые правила исходного Base для остальных сервисов.
Работа всех сервисов одновременно в этой сборке ещё не подтверждена на устройстве пользователя. Старую версию не удалять.

Движок engine.zip теперь содержит 2 дополнительных файла: новый .bat и list-youtube-veyra.txt. Все оригинальные entries/бинарники оставлены byte-for-byte неизменными. Это намеренно НОВЫЙ hash архива ресурсов, не hash исходного embedded zip.
Исправлена HTTPS-рекомендация: требуется ответ YouTube, Discord и Discord API; HTTPS не считается проверкой видео.
Сборка Windows: Visual Studio Build Tools/.NET Framework4.8 targeting+WPF pack, C#7.3, x64, `msbuild windows/source/ZapretDesk.csproj /p:Configuration=Release`. engine.zip приложен в исходники теста из предоставленного пользователем пакета; перед публичным распространением учитывать исходные THIRD-PARTY-NOTICES и лицензии.
EXE не подписан Authenticode; не отключать защиту ради запуска.

## Android

Готовый APK — отдельный debug-пакет io.github.nosmokee.veyra.next.unifiedtest с названием Veyra Test, minSdk24, arm64-v8a/x86_64.
Он не обновляет и не удаляет основное приложение. Остановить основную Veyra, открыть Veyra Test → Стратегии → YouTube TCP · test → подтвердить → Подключить всё и разрешить Android VPN.
Новая стратегия: TLS record split сразу, затем fallback disorder по SNI, затем обычный split; блокировщик UDP/443 включён. Профиль экспериментальный, НЕ копия Windows packet-level стратегии.
Base/Alt1–4, C/C++ и native .so не менялись. Проверено совпадение native byedpi/hev-сборок с присланным исходным APK для обеих ABI.
Собственная изолированная конфигурация Telegram: для его проверки добавить прокси из Veyra Test в Telegram заново; сохранённый ключ основного приложения не импортируется.
Не запускать две Veyra одновременно. Откат: остановить Veyra Test и открыть основную Veyra.

Сборка Android: JDK17, SDK34, Python3.13.

```sh
cd android
sh gradlew --no-daemon --max-workers=2 -PveyraPrebuiltNative -PveyraIsolatedTest -PveyraOfflineWheels=/absolute/path/to/build-dependencies/python-wheels testDebugUnitTest assembleDebug
```

Флаг veyraOfflineWheels необязательный. Pure-Python wheels восстановлены из неизменённых файлов предоставленного Source-code.zip с пересозданием RECORD; приложены для сборки без зависания chaquo.com. Приватный debug-ключ сборки в исходники НЕ включён; при следующей сборке стандартный Android debug-ключ будет своим. Приложенный APK подписан только тестовым ключом, не ключом релиза владельца.

## Ограничения проверки

Windows EXE скомпилирован Roslyn с reference assemblies .NET Framework4.8; встроенный Engine загружен и проверен без запуска UI/драйвера. Все 23 профиля разбираются; новые правила и сохранение старых настроек проверены. WPF/WinDivert и реальная Windows-сеть здесь НЕ запускались.
Android APK собран из исходников; подпись и package/ABI проверены. Robolectric/unit-тесты проверяют код и UI в тестовой среде, не физическое устройство/Android VPN/провайдерский DPI.
4 native loopback TLS-теста и 6 тестов рекомендации Windows выполнены отдельно.
Сетевой успех подтвердил пользователь только для отдельного Windows-теста №1. Новые интегрированные сборки нужно проверить на своих устройствах: 10–15 минут видео, перемотка и качество, Discord и незакэшированные Telegram-медиа. Гарантии универсального исправления нет.
