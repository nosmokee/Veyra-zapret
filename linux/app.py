#!/usr/bin/env python3
"""Veyra Linux. Rootless local SOCKS5 controller; never a system VPN."""
import argparse, atexit, concurrent.futures, fcntl, http.server, json, os, pathlib
import secrets, shutil, signal, socket, ssl, subprocess, threading, time, webbrowser
import html, platform, struct, urllib.parse
VERSION = "1.1-beta.3"
from telegram_bridge import Bridge, WebSocket
BASE = pathlib.Path(__file__).resolve().parent
PROFILES = {'youtube': ('Veyra Base · SNI / TCP', ['--timeout','3','--disorder','1+s','--auto=torst','--split','1+s','--tlsrec','3+s']),
            'split': ('Veyra Alt 1', ['--split', '1']),
            'disorder': ('Veyra Alt 2', ['--disorder', '1']),
            'tls': ('Veyra Alt 3', ['--tlsrec', '1']),
            'plain': ('Veyra Direct · диагностика', [])}
TARGETS = [('YouTube', 'www.youtube.com', '/'), ('Discord', 'discord.com', '/'),
           ('Discord API', 'discord.com', '/api/v9/gateway'), ('Google', 'www.google.com', '/generate_204'),
           ('GitHub', 'github.com', '/')]

def exact(s, count):
    out = bytearray()
    while len(out) < count:
        block = s.recv(count-len(out))
        if not block: raise OSError('Соединение закрыто')
        out.extend(block)
    return bytes(out)

def socks_connect(port, host, target_port):
    s = socket.create_connection(('127.0.0.1', port), timeout=8)
    try:
        s.sendall(b'\x05\x01\x00')
        if exact(s,2) != b'\x05\x00': raise OSError('Ошибка SOCKS5')
        domain = host.encode('idna')
        s.sendall(b'\x05\x01\x00\x03'+bytes([len(domain)])+domain+target_port.to_bytes(2,'big'))
        reply = exact(s,4)
        if reply[0] != 5 or reply[1] != 0: raise OSError('SOCKS5 код '+str(reply[1]))
        if reply[3] == 1: exact(s,4)
        elif reply[3] == 4: exact(s,16)
        elif reply[3] == 3: exact(s,exact(s,1)[0])
        else: raise OSError('Неверный ответ SOCKS5')
        exact(s,2)
        return s
    except BaseException:
        s.close(); raise

class Controller:
    def __init__(self, data, engine=None):
        self.data=pathlib.Path(data); self.data.mkdir(parents=True,exist_ok=True,mode=0o700)
        self.engine=pathlib.Path(engine or BASE/'bin/ciadpi')
        self.lock=threading.RLock(); self.proc=None; self.log=None; self.port=0; self.error=''; self.checks=[]
        self.bridge=None;self.ping_text='Замер WSS до Telegram через Veyra';self.browser_warning=''
        self.profile='youtube'; self.theme='dark'; self.color='amber'; self.background='none'
        try:
            cfg=json.loads((self.data/'settings.json').read_text())
            if not isinstance(cfg,dict):cfg={}
            if isinstance(cfg.get('profile'),str) and cfg.get('profile') in PROFILES: self.profile=cfg['profile']
            if cfg.get('theme') in ('dark','light'): self.theme=cfg['theme']
            if isinstance(cfg.get('color'),str) and cfg.get('color') in COLORS: self.color=cfg['color']
            if isinstance(cfg.get('background'),str) and cfg.get('background') in BACKGROUNDS: self.background=cfg['background']
        except (OSError,ValueError,TypeError): pass
    def running(self): return self.proc is not None and self.proc.poll() is None
    def save(self):
        path=self.data/'settings.json'; tmp=path.with_suffix('.tmp')
        with open(tmp,'w',encoding='utf8') as f:
            os.chmod(tmp,0o600); json.dump({'profile':self.profile,'theme':self.theme,'color':self.color,'background':self.background},f)
        os.replace(tmp,path)
    def state(self):
        with self.lock:
            if self.proc is not None and self.proc.poll() is not None and not self.error:
                self.error='Движок неожиданно завершился (код '+str(self.proc.returncode)+'). '+self.log_tail()
                if self.bridge is not None:self.bridge.close();self.bridge=None

            return {'version':VERSION,'running':self.running(),'port':self.port if self.running() else 0,
                    'profile':self.profile,'profiles':[{ 'id':k,'name':v[0]} for k,v in PROFILES.items()],
                    'theme':self.theme,'color':self.color,'background':self.background,'error':self.error,'checks':self.checks,'telegram':self.telegram_state(),'ping':self.ping_text,'browser_warning':self.browser_warning}
    def log_tail(self):
        try:
            if self.log: self.log.flush()
            return (self.data/'engine.log').read_bytes()[-4000:].decode('utf-8',errors='replace')
        except OSError: return ''
    def validate_engine(self):
        if not self.engine.is_file(): raise ValueError('Не найден bin/ciadpi. Распакуй весь архив, не только app.py.')
        if not os.access(self.engine,os.X_OK): raise ValueError('Нет права запуска bin/ciadpi. Выполни: chmod u+x veyra bin/ciadpi. Если не помогает, папка может быть смонтирована с noexec; перенеси Veyra в домашнюю папку с разрешённым запуском.')
        with self.engine.open('rb') as f:head=f.read(20)
        if len(head)<20 or head[:4]!=b'\x7fELF': raise ValueError('bin/ciadpi не является ELF-файлом из Linux-архива.')
        machine=int.from_bytes(head[18:20], 'little' if head[5]==1 else 'big')
        expected={'x86_64':62,'amd64':62,'aarch64':183,'arm64':183}.get(platform.machine().lower())
        if expected and machine!=expected: raise ValueError('Неподходящая архитектура bin/ciadpi: скачай архив для '+platform.machine()+'.')
    def stop(self):
        with self.lock:
            if self.bridge is not None:self.bridge.close();self.bridge=None
            if self.proc is not None:
                if self.proc.poll() is None:
                    self.proc.terminate()
                    try:self.proc.wait(timeout=4)
                    except subprocess.TimeoutExpired:self.proc.kill();self.proc.wait(timeout=4)
                self.proc=None
            if self.log is not None:self.log.close();self.log=None
            self.port=0
    def start(self, profile):
        with self.lock:
            if not isinstance(profile,str) or profile not in PROFILES: raise ValueError('Неизвестная стратегия')
            if self.running():
                if profile!=self.profile: raise ValueError('Сначала отключи прокси')
                return self.state()
            self.stop();self.profile=profile;self.error='';self.checks=[]
            try:self.validate_engine()
            except (ValueError,OSError) as e:self.error=str(e);raise
            with socket.socket() as s:s.bind(('127.0.0.1',0));self.port=s.getsockname()[1]
            self.log=open(self.data/'engine.log','wb');os.chmod(self.data/'engine.log',0o600)
            args=[str(self.engine),'--ip','127.0.0.1','--port',str(self.port),'--no-udp']+PROFILES[profile][1]
            try:
                self.proc=subprocess.Popen(args,stdin=subprocess.DEVNULL,stdout=self.log,stderr=subprocess.STDOUT,start_new_session=True)
                for _ in range(40):
                    if not self.running():raise RuntimeError('Движок завершился (код '+str(self.proc.returncode)+'). '+self.log_tail())
                    try:
                        with socket.create_connection(('127.0.0.1',self.port),timeout=.2) as ready:
                            ready.settimeout(.2);ready.sendall(b'\x05\x01\x00')
                            if exact(ready,2)!=b'\x05\x00':raise OSError('Нет ответа SOCKS5')
                        self.save();return self.state()
                    except OSError:time.sleep(.05)
                raise RuntimeError('Локальный порт не открылся')
            except BaseException as e:
                self.error=str(e);self.stop();raise
    def telegram_state(self):
        b=self.bridge
        return {'running':bool(b and b.live),'port':b.port if b and b.live else 0,
                'up':b.up if b else 0,'down':b.down if b else 0,'sessions':b.sessions if b else 0,'error':b.error if b else ''}
    def connect_all(self,profile=None,open_browser=True):
        with self.lock:
            if self.running() and self.bridge and self.bridge.live:return self.state()
            try:
                self.start(profile or self.profile)
                path=self.data/'telegram-private.json'
                if path.exists():
                    cfg=json.loads(path.read_text());secret=bytes.fromhex(cfg['secret']);port=cfg['port']
                    if len(secret)!=16 or type(port)!=int or not 1<=port<=65535:raise ValueError('Повреждены локальные настройки Telegram')
                else:secret=secrets.token_bytes(16);port=0
                b=Bridge(secret,connector=lambda host,port:socks_connect(self.port,host,port));self.bridge=b
                b.start(port)
                if not path.exists():
                    tmp=path.with_suffix('.tmp')
                    with open(tmp,'w') as f:
                        os.chmod(tmp,0o600);json.dump({'secret':secret.hex(),'port':b.port},f)
                    os.replace(tmp,path)
                self.browser_warning=''
                if open_browser:
                    try:self.browser()
                    except (OSError,ValueError) as e:self.browser_warning=str(e)
                return self.state()
            except BaseException:
                self.stop();raise
    def add_telegram(self):
        with self.lock:
            if not self.bridge or not self.bridge.live:raise ValueError('Сначала нажми «Подключить всё»')
            # The link is deliberately not exposed through /api/state or diagnostic exports.
            uri='tg://proxy?server=127.0.0.1&port='+str(self.bridge.port)+'&secret=dd'+self.bridge.secret.hex()
            if not webbrowser.open(uri):raise ValueError('Открой установленный Telegram Desktop и добавь локальный прокси вручную из приватного файла telegram-private.json')
            return {'message':'Подтверди прокси в Telegram один раз. Порт и ключ сохраняются.'}
    def ping(self):
        with self.lock:
            if not self.running():raise ValueError('Сначала подключи Veyra')
            port=self.port
        start=time.monotonic();ws=None
        try:
            ws=WebSocket.connect('kws2.web.telegram.org','149.154.167.220',lambda host,p:socks_connect(port,host,p))
            self.ping_text=str(round((time.monotonic()-start)*1000))+' мс · WSS / Telegram'
        except OSError:self.ping_text='Нет ответа · попробуй другую стратегию'
        finally:
            if ws:ws.close()
        return {'message':self.ping_text}
    def probe(self):
        with self.lock:
            if not self.running():raise ValueError('Сначала запусти прокси')
            port=self.port
        def one(target):
            name,host,path=target;start=time.monotonic()
            try:
                with socks_connect(port,host,443) as raw:
                    with ssl.create_default_context().wrap_socket(raw,server_hostname=host) as s:
                        s.sendall(('HEAD '+path+' HTTP/1.1\r\nHost: '+host+'\r\nUser-Agent: Veyra\r\nConnection: close\r\n\r\n').encode())
                        line=b''
                        while b'\r\n' not in line and len(line)<1024:
                            block=s.recv(256)
                            if not block:break
                            line+=block
                        code=int(line.split(b' ',2)[1]);return {'name':name,'ok':200<=code<400,'code':code,'ms':round((time.monotonic()-start)*1000)}
            except Exception as e:return {'name':name,'ok':False,'error':type(e).__name__,'ms':round((time.monotonic()-start)*1000)}
        with concurrent.futures.ThreadPoolExecutor(max_workers=5) as pool:rows=list(pool.map(one,TARGETS))
        with self.lock:
            if self.running() and self.port==port:self.checks=rows
        return rows
    def browser(self):
        with self.lock:
            if not self.running(): raise ValueError('Сначала запусти прокси')
            # A separate profile per proxy port prevents an existing browser instance
            # from silently reusing command-line arguments for a stopped proxy.
            profile=self.data/('browser-'+str(self.port));profile.mkdir(mode=0o700,exist_ok=True)
            for exe in ('chromium','chromium-browser','google-chrome','google-chrome-stable','firefox'):
                binary=shutil.which(exe)
                if not binary: continue
                if exe=='firefox':
                    prefs={'network.proxy.type':1,'network.proxy.socks':'127.0.0.1','network.proxy.socks_port':self.port,
                           'network.proxy.socks_version':5,'network.proxy.socks_remote_dns':True,
                           'network.proxy.no_proxies_on':'localhost, 127.0.0.1','network.http.http3.enable':False,
                           'network.trr.mode':5}
                    (profile/'user.js').write_text('\n'.join('user_pref('+json.dumps(k)+', '+json.dumps(v)+');' for k,v in prefs.items()),encoding='utf8')
                    command=[binary,'--no-remote','--new-instance','--profile',str(profile),'https://www.youtube.com/']
                else:
                    command=[binary,'--user-data-dir='+str(profile),'--proxy-server=socks5://127.0.0.1:'+str(self.port),
                             '--disable-quic','--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE localhost','--new-window','https://www.youtube.com/']
                log=open(self.data/'browser.log','ab');os.chmod(self.data/'browser.log',0o600)
                try: child=subprocess.Popen(command,stdin=subprocess.DEVNULL,stdout=log,stderr=subprocess.STDOUT,start_new_session=True)
                finally: log.close()
                time.sleep(.25)
                if child.poll() not in (None,0): raise ValueError('Браузер завершился с ошибкой. См. browser.log; возможно, установленный браузер или sandbox недоступен.')
                return 'Команда открытия '+exe+' отправлена. Отдельный профиль, SOCKS5 127.0.0.1:'+str(self.port)+', QUIC/HTTP3 выключен. Системные настройки не менялись.'
            raise ValueError('Не найден Chromium/Chrome/Firefox. Установи один из них либо укажи SOCKS5 вручную: 127.0.0.1:'+str(self.port)+', DNS через SOCKS5. Epiphany/GNOME Web автоматически не перенастраивается.')
    def appearance(self, data):
        with self.lock:
            theme=data.get('theme',self.theme);color=data.get('color',self.color);background=data.get('background',self.background)
            if not all(isinstance(v,str) for v in (theme,color,background)) or theme not in ('dark','light') or color not in COLORS or background not in BACKGROUNDS: raise ValueError('Неизвестное оформление')
            self.theme,self.color,self.background=theme,color,background;self.save()
            return self.state()

COLORS={'graphite':'Графит','cosmos':'Космос','amber':'Янтарь','ice':'Лёд','neon':'Неон','emerald':'Изумруд','sunset':'Закат','blood':'Кровь','gold':'Золото'}
BACKGROUNDS={'none':'Без фона','cosmos':'Космос','graphite':'Графит','constellation':'Созвездие'}
TABS={'home':'Главная','strategies':'Стратегии','settings':'Настройки','telegram':'Telegram','about':'О приложении'}
def escape(value): return html.escape(str(value),quote=True)
def form(token,action,tab,body):
    return '<form method="post" action="/action/'+action+'"><input type="hidden" name="token" value="'+escape(token)+'"><input type="hidden" name="tab" value="'+escape(tab)+'">'+body+'</form>'
def options(items,selected):
    return ''.join('<option value="'+escape(k)+'"'+(' selected' if k==selected else '')+'>'+escape(v)+'</option>' for k,v in items.items())
def page(controller,token,tab='home',message='',failed=False):
    state=controller.state();running=state['running'];disabled=' disabled' if running else ''
    profile_names={k:v[0] for k,v in PROFILES.items()}
    address='SOCKS5 · 127.0.0.1:'+str(state['port']) if running else 'Прокси остановлен'
    stop=form(token,'stop',tab,'<button class="primary" id="connect">Отключить</button>')
    start=form(token,'start',tab,'<label for="profile">Стратегия</label><select id="profile" name="profile">'+options(profile_names,state['profile'])+'</select><button class="primary" id="connect">Подключить</button>')
    browser=form(token,'browser',tab,'<button id="browser"'+('' if running else ' disabled')+'>Открыть браузер через прокси</button>')
    checks=''.join('<div class="check"><strong>'+escape(name)+'</strong><small>'+escape(next((('HTTPS · '+str(c['ms'])+' мс') if c.get('ok') else ('HTTP '+str(c['code'])) if c.get('code') else c.get('error','Ошибка') for c in state['checks'] if c['name']==name),'Не проверено'))+'</small></div>' for name,_,_ in TARGETS)
    body=''
    tg=state['telegram'];both=running and tg['running']
    connect=form(token,'stop' if running else 'connect',tab,'<button class="primary connect" id="connect">'+('Отключить всё' if running else 'Подключить всё')+'</button>')
    tgcard='<section class="card"><h2>Telegram · мост</h2><p>'+('Мост запущен · '+str(tg['sessions'])+' сессий' if tg['running'] else 'Запускается общей кнопкой Veyra')+'</p>'+form(token,'telegram',tab,'<button'+('' if tg['running'] else ' disabled')+'>Добавить в Telegram</button>')+'<p>Прокси добавляется один раз. Сообщения и медиа; без звонков. После отключения Veyra выключи этот прокси в Telegram, если он мешает прямому подключению.</p></section>'
    if tab=='home':
        body='<header><small>VEYRA / LINUX · BETA</small><h1>Твоя сеть.<br>Под твоим контролем.</h1><p>Одна кнопка для браузера и Telegram.</p></header><section class="card hero"><div><small>ЛОКАЛЬНОЕ ПОДКЛЮЧЕНИЕ</small><h2 id="status">'+('Обход и Telegram-мост включены' if both else 'Локальный прокси запущен' if running else 'Готов к подключению')+'</h2><p>'+escape(profile_names[state['profile']])+'</p>'+connect+'</div><div class="detail"><span class="shield">◇</span><p id="address">'+escape(address)+'</p><small>'+('Telegram · 127.0.0.1:'+str(tg['port']) if tg['running'] else 'Telegram ждёт запуска')+'</small></div></section><div class="row tools">'+form(token,'refresh',tab,'<button>↻ Обновить</button>')+form(token,'ping',tab,'<button'+('' if running else ' disabled')+'>⌁ Пинг</button>')+browser+'</div><p>'+escape(state['ping'])+'</p>'+('<p class="error">'+escape(state['browser_warning'])+'</p>' if state['browser_warning'] else '')+tgcard+'<section class="card"><h2>Не системный VPN</h2><p>При подключении открывается отдельное окно Chromium, Chrome или Firefox с SOCKS5 и выключенным QUIC/HTTP3. Настройки основного браузера, маршруты и firewall не меняются. Другие приложения автоматически не переключаются. Внешний IP не меняется; региональные ограничения ChatGPT не снимаются.</p></section>'
    elif tab=='strategies':
        body='<h1>Стратегии</h1><p>Выбери профиль для своей сети. Telegram запускается вместе с каждым профилем.</p><div class="grid">'
        for k,(name,args) in PROFILES.items():
            body+='<section class="card'+(' selected' if k==state['profile'] else '')+'"><h2>'+escape(name)+'</h2><p>'+('✓ Текущая стратегия' if k==state['profile'] else 'Локальный профиль ByeDPI')+'</p>'+form(token,'select',tab,'<input type="hidden" name="profile" value="'+k+'"><button'+disabled+'>Выбрать</button>')+'</section>'
        body+='</div><p>Перед сменой стратегии отключи Veyra. Успешный пинг не гарантирует воспроизведение видео.</p>'
    elif tab=='settings':
        body='<h1>Настройки</h1>'+tgcard+'<section class="card"><h2>Диагностика</h2><p>Локальные журналы engine.log и browser.log. Проверь содержимое перед отправкой. Ключ Telegram не включается в отчёт.</p><a class="button" href="/diagnostics.txt" download="Veyra-diagnostics.txt">Сохранить журнал</a></section><section class="card"><h2>Завершение</h2><p>Закрытие вкладки не останавливает Veyra. Кнопка ниже завершает только собственный прокси и мост.</p>'+form(token,'exit',tab,'<button>Завершить Veyra</button>')+'</section><a class="button" href="https://github.com/nosmokee/Veyra-zapret" target="_blank" rel="noopener noreferrer">GitHub · Veyra ↗</a>'
    elif tab=='telegram':body='<h1>Telegram</h1>'+tgcard
    elif tab=='personal':
        body='<h1>Оформление</h1><section class="card"><p>Veyra 1.1 использует тёмную янтарную тему.</p></section>'
    else:
        body='<h1>О приложении</h1><section class="card"><h2>Veyra 1.1 Beta</h2><p>by Nosmoke · @recobalt</p><p>Локальный SOCKS5 на базе hufrea/ByeDPI и MTProxy → Telegram WSS с проверкой TLS; CDN203 подключается к собственному серверу через MTProto TCP. Не удалённый VPN, без смены IP. Пароли и учётная запись Telegram не требуются.</p><p>Python 3.9+, Chromium/Chrome/Firefox. Лицензии: LICENSE и LICENSES. x86_64 и aarch64; платформенные ограничения и результаты проверок находятся в TEST-REPORT.md.</p></section>'
    nav=''.join('<a href="/?tab='+key+'"'+(' class="active" aria-current="page"' if key==tab else '')+'>'+name+'</a>' for key,name in TABS.items())
    notice=message or state['error']
    template=(BASE/'ui.html').read_text(encoding='utf8')
    return (template.replace('__CSRF__',token).replace('__VERSION__',VERSION).replace('__THEME__','dark').replace('__COLOR__','amber').replace('__BACKGROUND__','none').replace('__NAV__',nav).replace('__CONTENT__',body).replace('__NOTICE__',escape(notice)).replace('__NOTICE_CLASS__','error' if failed or state['error'] else 'notice')).encode('utf8')


def handler(controller, token):
    class Handler(http.server.BaseHTTPRequestHandler):
        server_version='VeyraLocal/'+VERSION
        def log_message(self,*args): pass
        def valid_host(self): return self.headers.get('Host')==f'127.0.0.1:{self.server.server_port}'
        def send_data(self,status,data,kind='application/json; charset=utf-8'):
            if not isinstance(data,bytes):data=json.dumps(data,ensure_ascii=False).encode()
            self.send_response(status);self.send_header('Content-Type',kind);self.send_header('Content-Length',str(len(data)))
            self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.send_header('X-Frame-Options','DENY')
            self.send_header('Content-Security-Policy',"default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; img-src 'self' data:; frame-ancestors 'none'; base-uri 'none'; form-action 'self'")
            self.end_headers()
            try:self.wfile.write(data)
            except (BrokenPipeError,ConnectionResetError):pass
        def do_GET(self):
            if not self.valid_host():return self.send_data(403,{'error':'Host запрещён'})
            path=urllib.parse.urlsplit(self.path);query=urllib.parse.parse_qs(path.query)
            if path.path=='/':
                tab=query.get('tab',['home'])[0]
                if tab not in TABS:return self.send_data(404,{'error':'Вкладка не найдена'})
                return self.send_data(200,page(controller,token,tab),'text/html; charset=utf-8')
            if path.path=='/favicon.ico':return self.send_data(204,b'','image/x-icon')
            if path.path=='/api/state':return self.send_data(200,controller.state())
            if path.path=='/diagnostics.txt':
                report='Veyra Linux '+VERSION+'\n'+platform.system()+' '+platform.release()+' / '+platform.machine()+'\n'+json.dumps(controller.state(),ensure_ascii=False,indent=2)+'\n--- engine.log (tail) ---\n'+controller.log_tail()
                return self.send_data(200,report.encode(),'text/plain; charset=utf-8')
            return self.send_data(404,{'error':'Не найдено'})
        def do_POST(self):
            origin=f'http://127.0.0.1:{self.server.server_port}'
            if not self.valid_host():return self.send_data(403,{'error':'Host запрещён'})
            kind=self.headers.get('Content-Type','').split(';')[0].strip()
            form_request=self.path.startswith('/action/')
            try:
                self.connection.settimeout(5)
                length=int(self.headers.get('Content-Length','0'))
                if not 0<=length<=2048:raise ValueError('Слишком большой запрос')
                raw=self.rfile.read(length)
                if len(raw)!=length:raise ValueError('Запрос прочитан не полностью')
                if kind=='application/json' and not form_request:
                    data=json.loads(raw or b'{}');provided=self.headers.get('X-Veyra-Token','')
                elif kind=='application/x-www-form-urlencoded' and form_request:
                    fields=urllib.parse.parse_qs(raw.decode('utf8'),keep_blank_values=True)
                    if any(len(v)!=1 for v in fields.values()):raise ValueError('Повторяющиеся параметры')
                    data={k:v[0] for k,v in fields.items()};provided=data.get('token','')
                else:return self.send_data(415,{'error':'Неподдерживаемый Content-Type'})
                request_origin=self.headers.get('Origin')
                ref=self.headers.get('Referer','')
                same_origin=request_origin==origin or (not request_origin and form_request and (ref==origin+'/' or ref.startswith(origin+'/?')))
                if not same_origin or not isinstance(provided,str) or not secrets.compare_digest(provided,token):return self.send_data(403,{'error':'Запрос запрещён'})
                if not isinstance(data,dict):raise ValueError('Неверный запрос')
                if not (self.path.startswith('/api/') or form_request):return self.send_data(404,{'error':'Не найдено'})
                action=self.path.split('/')[-1];message='';tab=data.get('tab','home')
                if not isinstance(tab,str) or tab not in TABS:tab='home'
                if action=='connect':result=controller.connect_all(data.get('profile',controller.profile))
                elif action=='refresh':result=controller.state();message='Состояние обновлено'
                elif action=='ping':result=controller.ping();message=result['message']
                elif action=='telegram':result=controller.add_telegram();message=result['message']
                elif action=='start':result=controller.start(data.get('profile',controller.profile))
                elif action=='stop':controller.stop();result=controller.state()
                elif action=='select':
                    with controller.lock:
                        if controller.running():raise ValueError('Сначала отключи прокси')
                        profile=data.get('profile')
                        if not isinstance(profile,str) or profile not in PROFILES:raise ValueError('Неизвестная стратегия')
                        controller.profile=profile;controller.save();result=controller.state();message='Стратегия сохранена'
                elif action=='probe':result=controller.probe();message='Проверка завершена. Ответы HTTPS не подтверждают воспроизведение видео.'
                elif action=='browser':message=controller.browser();result={'message':message}
                elif action in ('appearance','theme'):result=controller.appearance(data);message='Оформление сохранено'
                elif action=='exit':
                    controller.stop();self.send_data(200,'<!doctype html><meta charset="utf-8"><title>Veyra</title><h1>Veyra завершена</h1><p>Эту вкладку можно закрыть.</p>'.encode('utf8'),'text/html; charset=utf-8') if form_request else self.send_data(200,{'message':'Veyra завершена'})
                    threading.Thread(target=self.server.shutdown,daemon=True).start();return
                else:return self.send_data(404,{'error':'Не найдено'})
                if form_request:return self.send_data(200,page(controller,token,tab,message),'text/html; charset=utf-8')
                return self.send_data(200,result)
            except (ValueError,OSError,RuntimeError) as e:
                if form_request:return self.send_data(400,page(controller,token,'home',str(e),True),'text/html; charset=utf-8')
                return self.send_data(400,{'error':str(e)})
            except Exception:
                return self.send_data(500,{'error':'Внутренняя ошибка'})
    return Handler

def main():
    args=argparse.ArgumentParser(description='Veyra Linux — local SOCKS5, no root')
    args.add_argument('--no-browser',action='store_true');args.add_argument('--data-dir',type=pathlib.Path)
    opt=args.parse_args();data=opt.data_dir or pathlib.Path(os.environ.get('XDG_DATA_HOME',pathlib.Path.home()/'.local/share'))/'veyra'
    data.mkdir(parents=True,exist_ok=True,mode=0o700)
    lock=open(data/'instance.lock','a')
    try:fcntl.flock(lock,fcntl.LOCK_EX|fcntl.LOCK_NB)
    except BlockingIOError:raise SystemExit('Veyra уже запущена для этого пользователя. Используй адрес из первого терминала или заверши предыдущий запуск.')
    controller=Controller(data);atexit.register(controller.stop)
    server=http.server.ThreadingHTTPServer(('127.0.0.1',0),handler(controller,secrets.token_hex(32)))
    server.daemon_threads=True
    url=f'http://127.0.0.1:{server.server_port}/';print('Veyra '+VERSION+': '+url,flush=True)
    print('Без root. Только приложения с SOCKS5; не системный VPN. Ctrl+C завершает Veyra.',flush=True)
    def end(*_):raise KeyboardInterrupt
    signal.signal(signal.SIGTERM,end)
    if not opt.no_browser:
        try:
            if not webbrowser.open(url):print('Открой адрес выше в браузере вручную.',flush=True)
        except Exception as e:print('Не удалось открыть браузер: '+str(e)+'. Открой адрес выше вручную.',flush=True)
    try:server.serve_forever()
    except KeyboardInterrupt:pass
    finally:controller.stop();server.server_close();lock.close()
if __name__=='__main__':main()
