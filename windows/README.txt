Beta 3: DC203 now uses its own MTProto TCP endpoint, not DC2 WSS. Clean client EOF is not a proxy error. Preserve application data/key/port; real account media playback is NOT confirmed here. See TELEGRAM-FIX-REPORT.md.

Telegram fix beta.2: persistent local port, 64 sessions, bulk WebSocket masking. Stop old copies first. Re-add the active proxy in Telegram once if old settings remain. Detailed checks and limits: TELEGRAM-FIX-REPORT.md.

Veyra 1.1 Beta — Windows x64 (1.1-beta.3)

Распакуй архив целиком. Требуются Windows x64 и .NET Framework4.8.
Запусти Veyra.exe. Для winws/WinDivert требуется подтверждение прав администратора.
EXE не имеет Authenticode-подписи; Windows может показывать предупреждение об издателе.
Не отключай антивирус. Если защита блокирует драйвер, сначала проверь происхождение/хэши.

Главная кнопка запускает выбранную стратегию winws. Профили: Veyra Base и Alt1–21.
Telegram в этой Windows-сборке сохраняет отдельную кнопку «Включить мост» на вкладке Telegram:
включи её, затем один раз добавь локальный прокси в Telegram. Общее включение Android/Linux
не переносилось в Windows в этом пакете. Рабочий сетевой код Windows сохранён.
Тема по умолчанию тёплая янтарная; изменение темы доступно в персонализации.

Ни обход, ни локальный Telegram-мост не меняют внешний IP и не снимают региональные ограничения ChatGPT.
Telegram-мост: сообщения/медиа, без звонков/Telegram Web. Успешный HTTPS не гарантирует видео/голос.
После остановки моста отключи локальный прокси в Telegram при необходимости.
Журналы проверь перед отправкой; не публикуй локальный Telegram-ключ/ссылку.
Останавливаются собственные процессы приложения, не чужие процессы по имени.

TESTING.txt описывает реальные границы тестирования: нативный WPF/WinDivert на Windows здесь не запускался.
Лицензии и внешние зависимости: THIRD-PARTY-NOTICES.txt, licenses/.
Перед публичной раздачей ещё требуется сверить версии и corresponding-source обязательства
для winws/WinDivert/Cygwin из исходного engine.zip; пакет пока предназначен для закрытой проверки.
GitHub: https://github.com/nosmokee/Veyra-zapret
