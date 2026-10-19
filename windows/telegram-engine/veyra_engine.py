"""Veyra lifecycle adapter for unmodified Flowseal 1.11.2 transport/routing.
One engine per process. No account credentials; no secret-bearing diagnostics.
"""
import asyncio,collections,logging,re,threading,time
from proxy import tg_ws_proxy as core
from proxy.config import proxy_config
from proxy.stats import stats
_guard=threading.Lock();_active=None
class _Log(logging.Handler):
 def __init__(self,engine):super().__init__();self.engine=engine
 def emit(self,record):
  try:
   text=record.getMessage().replace(self.engine.secret.hex(),'[secret]')
   text=re.sub(r'(?:tg://|https?://t\.me/proxy\?)[^\s]+','[proxy-link]',text)
   if 'Secret:' in text or 'Generated secret:' in text:return
   self.engine.events.append(time.strftime('%H:%M:%S')+' '+record.levelname+' '+text[:900])
  except Exception:pass
def crypto_self_test():
 from proxy._aes import Cipher,algorithms,modes
 key=bytes.fromhex('603deb1015ca71be2b73aef0857d77811f352c073b6108d72d9810a30914dff4')
 iv=bytes.fromhex('f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff')
 plain=bytes.fromhex('6bc1bee22e409f96e93d7e117393172aae2d8a571e03ac9c9eb76fac45af8e5130c81c46a35ce411e5fbc1191a0a52eff69f2445df4f9b17ad2b417be66c3710')
 expected=bytes.fromhex('601ec313775789a5b7a7f504bbf3d228f443e3ca4d62b59aca84e990cacaf5c52b0930daa23de94ce87017ba2d84988ddfc9c58db67aada613c2dd08457941a6')
 for chunk in (1,3,7,16,31,64):
  ctr=Cipher(algorithms.AES(key),modes.CTR(iv)).encryptor()
  output=b''.join(ctr.update(plain[i:i+chunk]) for i in range(0,len(plain),chunk))
  if output!=expected or ctr.update(b'')!=b'':raise RuntimeError('AES-CTR provider self-test failed')
 return True
class Engine:
 def __init__(self,secret_hex,preferred_port=1087):
  crypto_self_test()
  self.secret=bytes.fromhex(secret_hex)
  if len(self.secret)!=16:raise ValueError('Secret must be 16 bytes')
  if type(preferred_port)!=int or not 0<=preferred_port<=65535:raise ValueError('Invalid port')
  self.port=preferred_port;self.error='';self.events=collections.deque(maxlen=256)
  self._ready=threading.Event();self._loop=None;self._task=None;self._stop=None;self._thread=None;self._handler=None
 @property
 def live(self):return bool(self._thread and self._thread.is_alive() and self._ready.is_set() and not self.error and core._server_instance is not None)
 @property
 def up(self):return stats.bytes_up
 @property
 def down(self):return stats.bytes_down
 @property
 def sessions(self):return stats.connections_total
 def snapshot(self):return {'running':self.live,'port':self.port,'up':self.up,'down':self.down,'sessions':self.sessions,'error':self.error}
 def diagnostics(self):return '\n'.join(list(self.events))
 def start(self,port=None):
  global _active
  if port is not None:self.port=port
  with _guard:
   if _active is not None:raise RuntimeError('Telegram engine already active')
   _active=self
  self._handler=_Log(self);logging.getLogger('tg-mtproto-proxy').addHandler(self._handler);logging.getLogger('tg-mtproto-proxy').setLevel(logging.INFO)
  self._thread=threading.Thread(target=self._thread_main,name='VeyraTelegramFlowseal',daemon=True);self._thread.start()
  if not self._ready.wait(20):self.close();raise TimeoutError('Telegram engine startup timed out')
  if self.error:self.close();raise OSError(self.error)
  return self.port
 def _thread_main(self):
  loop=asyncio.new_event_loop();self._loop=loop;asyncio.set_event_loop(loop)
  async def run():
   for name in vars(stats):setattr(stats,name,0)
   proxy_config.host='127.0.0.1';proxy_config.port=self.port;proxy_config.secret=self.secret.hex()
   proxy_config.dc_redirects={2:'149.154.167.220',4:'149.154.167.220'}
   proxy_config.pool_size=4;proxy_config.fallback_cfproxy=True
   proxy_config.cfproxy_user_domains=[];proxy_config.cfproxy_worker_domains=[]
   proxy_config.cfproxy_h2_media=True;proxy_config.disable_secure=False
   proxy_config.fake_tls_domain='';proxy_config.proxy_protocol=False;proxy_config.force_test_dc=False
   self._stop=asyncio.Event();self._task=asyncio.create_task(core._run(self._stop))
   while not self._task.done():
    server=core._server_instance
    if server and server.sockets:
     self.port=server.sockets[0].getsockname()[1];self._ready.set();break
    await asyncio.sleep(.02)
   await self._task
  try:loop.run_until_complete(run())
  except asyncio.CancelledError:pass
  except Exception as exc:
   # Never include arbitrary exception text (may contain configuration).
   self.error='Telegram engine stopped: '+type(exc).__name__;self.events.append(self.error)
  finally:
   server=core._server_instance
   if server:server.close()
   core._server_instance=None
   tasks=asyncio.all_tasks(loop)
   for task in tasks:task.cancel()
   if tasks:
    try:loop.run_until_complete(asyncio.wait_for(asyncio.gather(*tasks,return_exceptions=True),5))
    except BaseException:pass
   loop.run_until_complete(loop.shutdown_asyncgens());loop.close();self._ready.set()
 def close(self):
  global _active
  if self._loop and self._loop.is_running() and self._stop:
   try:self._loop.call_soon_threadsafe(self._stop.set)
   except RuntimeError:pass
  if self._thread and self._thread is not threading.current_thread():
   self._thread.join(5)
   if self._thread.is_alive() and self._loop and self._task:
    try:self._loop.call_soon_threadsafe(self._task.cancel)
    except RuntimeError:pass
    self._thread.join(7)
   if self._thread.is_alive():raise RuntimeError('Telegram engine did not stop; cannot restart safely')
  if self._handler:logging.getLogger('tg-mtproto-proxy').removeHandler(self._handler);self._handler=None
  with _guard:
   if _active is self:_active=None
