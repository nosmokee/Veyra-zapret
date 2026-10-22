#!/usr/bin/env python3
"""Offline regression tests: real SOCKS5 engine, local TCP echo, HTTP API."""
import concurrent.futures, http.client, http.server, importlib.util, json, os, pathlib, re, socket, socketserver, subprocess, sys, tempfile, threading, time, unittest
from unittest.mock import patch
ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT));import app
ENGINE=ROOT/'bin/ciadpi'
class Echo(socketserver.BaseRequestHandler):
 def handle(self):
  self.request.settimeout(3)
  try:
   while True:
    b=self.request.recv(65536)
    if not b:break
    self.request.sendall(b)
  except OSError:pass
class ThreadServer(socketserver.ThreadingTCPServer):
 allow_reuse_address=True;daemon_threads=True
class Tests(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.echo=ThreadServer(('127.0.0.1',0),Echo);threading.Thread(target=cls.echo.serve_forever,daemon=True).start()
 @classmethod
 def tearDownClass(cls):cls.echo.shutdown();cls.echo.server_close()
 def setUp(self):
  self.tmp=tempfile.TemporaryDirectory();self.c=app.Controller(self.tmp.name,ENGINE)
  self.token='test-token';self.server=http.server.ThreadingHTTPServer(('127.0.0.1',0),app.handler(self.c,self.token));self.server.daemon_threads=True
  self.thread=threading.Thread(target=self.server.serve_forever,daemon=True);self.thread.start();self.port=self.server.server_port
 def tearDown(self):
  self.c.stop();self.server.shutdown();self.server.server_close();self.thread.join();self.tmp.cleanup()
 def request(self,path='/',data=None,headers=None,method=None):
  h={'Host':f'127.0.0.1:{self.port}'};h.update(headers or {})
  if isinstance(data,dict):data=json.dumps(data);h.setdefault('Content-Type','application/json')
  conn=http.client.HTTPConnection('127.0.0.1',self.port,timeout=15)
  conn.request(method or ('POST' if data is not None else 'GET'),path,body=data,headers=h);r=conn.getresponse();out=(r.status,r.read(),dict(r.getheaders()));conn.close();return out
 def api(self,action,data=None):return self.request('/api/'+action,data or {},{'Origin':f'http://127.0.0.1:{self.port}','X-Veyra-Token':self.token})
 def echo_roundtrip(self,port,payload=b'GET / HTTP/1.1\r\nHost: localhost\r\n\r\n'):
  with app.socks_connect(port,'localhost',self.echo.server_address[1]) as s:
   s.sendall(payload);self.assertEqual(app.exact(s,len(payload)),payload)
 def test_01_all_routes_without_js(self):
  for tab in app.TABS:
   with self.subTest(tab=tab):
    status,b,h=self.request('/?tab='+tab);text=b.decode();self.assertEqual(status,200);self.assertIn('<nav',text);self.assertNotIn('__CONTENT__',text);self.assertNotIn('Загрузка…',text);self.assertNotIn('<script',text);self.assertIn('frame-ancestors',h['Content-Security-Policy'])
  self.assertEqual(self.request('/?tab=bad')[0],404);self.assertEqual(self.request('/unknown')[0],404)
 def test_02_real_traffic_every_profile(self):
  for profile in app.PROFILES:
   with self.subTest(profile=profile):
    state=self.c.start(profile);self.assertTrue(state['running']);self.echo_roundtrip(state['port']);self.c.stop();self.assertFalse(self.c.state()['running'])
 def test_03_twenty_restart_cycles(self):
  for n in range(20):
   state=self.c.start('plain');proc=self.c.proc;port=state['port'];self.echo_roundtrip(port,os.urandom(8192));self.c.stop();self.assertIsNotNone(proc.poll());self.assertEqual(self.c.port,0)
   with self.assertRaises(OSError):socket.create_connection(('127.0.0.1',port),timeout=.1)
 def test_04_parallel_connections(self):
  self.c.start('plain')
  with concurrent.futures.ThreadPoolExecutor(max_workers=10) as pool:list(pool.map(lambda n:self.echo_roundtrip(self.c.port,os.urandom(64000)),range(20)))
 def test_05_api_lifecycle(self):
  self.assertEqual(self.api('start',{'profile':'plain'})[0],200);proc=self.c.proc
  self.assertEqual(self.api('start',{'profile':'plain'})[0],200);self.assertIs(self.c.proc,proc)
  self.assertEqual(self.api('start',{'profile':'split'})[0],400);self.assertEqual(self.api('select',{'profile':'tls'})[0],400)
  self.assertEqual(self.api('stop')[0],200);self.assertEqual(self.api('stop')[0],200)
  self.assertEqual(self.api('select',{'profile':'tls'})[0],200)
 def test_06_security(self):
  self.assertEqual(self.request('/',headers={'Host':'evil.test'})[0],403)
  for headers in ({},{'X-Veyra-Token':self.token},{'Origin':'https://evil.test','X-Veyra-Token':self.token},{'Origin':f'http://127.0.0.1:{self.port}','X-Veyra-Token':'wrong'}):
   self.assertEqual(self.request('/api/start',{},headers)[0],403)
  self.assertFalse(self.c.running())
 def test_07_malformed_requests(self):
  h={'Origin':f'http://127.0.0.1:{self.port}','X-Veyra-Token':self.token,'Content-Type':'application/json'}
  for raw in ('{','[]','null','"text"'):
   self.assertEqual(self.request('/api/start',raw,h)[0],400)
  self.assertEqual(self.request('/api/start','x'*3000,h)[0],400)
  self.assertEqual(self.request('/api/start','hello',{'Content-Type':'text/plain'})[0],415)
  for action,data in [('start',{'profile':[]}),('select',{'profile':{}}),('appearance',{'color':[]})]:self.assertEqual(self.api(action,data)[0],400)
  self.assertEqual(self.api('missing')[0],404)
 def test_08_form_submission(self):
  import urllib.parse
  fields={'token':self.token,'tab':'home','profile':'split'};h={'Content-Type':'application/x-www-form-urlencoded','Origin':f'http://127.0.0.1:{self.port}'}
  code,b,_=self.request('/action/start',urllib.parse.urlencode(fields),h);self.assertEqual(code,200);self.assertIn('Локальный прокси запущен',b.decode())
  fields['token']='bad';self.assertEqual(self.request('/action/stop',urllib.parse.urlencode(fields),h)[0],403)
  fields['token']=self.token;h.pop('Origin');h['Referer']=f'http://127.0.0.1:{self.port}/?tab=home';self.assertEqual(self.request('/action/stop',urllib.parse.urlencode(fields),h)[0],200)
  self.assertEqual(self.request('/action/start',urllib.parse.urlencode(fields)+'&token=duplicate',h)[0],400)
 def test_09_persistence_and_permissions(self):
  self.c.profile='tls';self.c.appearance({'theme':'light','color':'blood','background':'cosmos'})
  c=app.Controller(self.tmp.name,ENGINE);self.assertEqual((c.profile,c.theme,c.color,c.background),('tls','light','blood','cosmos'))
  self.assertEqual((pathlib.Path(self.tmp.name)/'settings.json').stat().st_mode&0o777,0o600)
 def test_10_corrupt_settings(self):
  for raw in ('{','[]','null','{"profile":[],"color":{},"background":[]}', '"text"'):
   (pathlib.Path(self.tmp.name)/'settings.json').write_text(raw);c=app.Controller(self.tmp.name,ENGINE);self.assertEqual(c.profile,'base')
 def test_11_missing_engine(self):
  self.c.engine=pathlib.Path(self.tmp.name)/'absent'
  self.assertEqual(self.api('start')[0],400);self.assertFalse(self.c.running());self.assertIn('bin/ciadpi',self.c.error)
 def test_12_invalid_and_wrong_arch_engine(self):
  p=pathlib.Path(self.tmp.name)/'bad';p.write_bytes(b'not executable');p.chmod(0o600);self.c.engine=p
  with self.assertRaisesRegex(ValueError,'права'):self.c.start('plain')
  p.chmod(0o700)
  with self.assertRaisesRegex(ValueError,'ELF'):self.c.start('plain')
  p.write_bytes(b'\x7fELF\x02\x01'+b'\0'*12+(183 if app.platform.machine()=='x86_64' else 62).to_bytes(2,'little'))
  with self.assertRaisesRegex(ValueError,'архитектура'):self.c.start('plain')
 def test_13_unexpected_engine_exit(self):
  self.c.start('plain');self.c.proc.kill();self.c.proc.wait();self.assertIn('неожиданно',self.c.state()['error']);self.c.start('plain');self.assertEqual(self.c.error,'')
 def test_14_probe_and_browser_require_start(self):
  self.assertEqual(self.api('probe')[0],400);self.assertEqual(self.api('browser')[0],400)
 def test_15_probe_failure_is_not_success(self):
  self.c.start('plain')
  with patch.object(app,'socks_connect',side_effect=OSError('offline')):rows=self.c.probe()
  self.assertEqual(len(rows),len(app.TARGETS));self.assertTrue(all(not r['ok'] for r in rows))
 def test_16_browser_arguments(self):
  self.c.start('plain')
  for exe in ('chromium','firefox'):
   with patch.object(app.shutil,'which',side_effect=lambda name:'/fake/'+exe if name==exe else None),patch.object(app.subprocess,'Popen') as pop,patch.object(app.time,'sleep'):
    pop.return_value.poll.return_value=None;self.c.browser();args=pop.call_args.args[0];self.assertIn(str(self.c.port),' '.join(args) if exe=='chromium' else (self.c.data/('browser-'+str(self.c.port))/'user.js').read_text())
    if exe=='chromium':self.assertIn('--disable-quic',args);self.assertIn('--host-resolver-rules=MAP * ~NOTFOUND, EXCLUDE localhost',args)
    else:self.assertIn('network.proxy.socks_remote_dns', (self.c.data/('browser-'+str(self.c.port))/'user.js').read_text())
  with patch.object(app.shutil,'which',return_value=None):self.assertEqual(self.api('browser')[0],400)
 def test_17_diagnostics(self):
  status,b,h=self.request('/diagnostics.txt');self.assertEqual(status,200);self.assertIn(f'Veyra Linux {app.VERSION}',b.decode());self.assertNotIn(self.token,b.decode())
 def test_18_html_escape(self):
  text=app.page(self.c,self.token,message='<script>alert(1)</script>').decode();self.assertNotIn('<script>',text);self.assertIn('&lt;script&gt;',text)
 def test_19_exit(self):
  self.c.start('plain');self.assertEqual(self.api('exit')[0],200);self.thread.join(3);self.assertFalse(self.thread.is_alive());self.assertFalse(self.c.running())
 def test_20_tls_probe_through_real_socks(self):
  import ssl
  cert=pathlib.Path(self.tmp.name)/'cert.pem';key=pathlib.Path(self.tmp.name)/'key.pem'
  subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(key),'-out',str(cert),'-days','1','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost'],check=True,stdout=subprocess.DEVNULL,stderr=subprocess.DEVNULL)
  class Web(http.server.BaseHTTPRequestHandler):
   def log_message(self,*args):pass
   def do_HEAD(self):self.send_response(204);self.end_headers()
  web=http.server.ThreadingHTTPServer(('127.0.0.1',0),Web);web.daemon_threads=True
  tls=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);tls.load_cert_chain(cert,key);web.socket=tls.wrap_socket(web.socket,server_side=True)
  threading.Thread(target=web.serve_forever,daemon=True).start()
  original=app.socks_connect
  try:
   self.c.start('youtube');client=ssl.create_default_context(cafile=str(cert))
   with patch.object(app,'TARGETS',[('Local TLS','localhost','/')]),patch.object(app,'socks_connect',side_effect=lambda port,host,tp:original(port,host,web.server_port)),patch.object(app.ssl,'create_default_context',return_value=client):rows=self.c.probe()
   self.assertTrue(rows[0]['ok']);self.assertEqual(rows[0]['code'],204)
  finally:web.shutdown();web.server_close()
 def test_21_launcher_lock_and_sigterm(self):
  data=pathlib.Path(self.tmp.name)/'launch';log=pathlib.Path(self.tmp.name)/'launch.log'
  with log.open('w') as out:
   process=subprocess.Popen(['sh',str(ROOT/'veyra'),'--no-browser','--data-dir',str(data)],stdout=out,stderr=subprocess.STDOUT)
  try:
   for _ in range(50):
    text=log.read_text()
    if 'http://' in text:break
    time.sleep(.05)
   address=re.search(r'http://127.0.0.1:(\d+)/',text);self.assertIsNotNone(address)
   duplicate=subprocess.run([str(ROOT/'veyra'),'--no-browser','--data-dir',str(data)],capture_output=True,timeout=5);self.assertNotEqual(duplicate.returncode,0);self.assertIn('уже запущена',duplicate.stderr.decode())
   conn=http.client.HTTPConnection('127.0.0.1',int(address[1]));conn.request('GET','/');response=conn.getresponse();page=response.read().decode();token=re.search(r'name="token" value="([^"]+)"',page)[1]
   conn.request('POST','/api/start',json.dumps({'profile':'plain'}),{'Content-Type':'application/json','Origin':'http://127.0.0.1:'+address[1],'X-Veyra-Token':token});response=conn.getresponse();self.assertEqual(response.status,200);port=json.loads(response.read())['port'];self.echo_roundtrip(port);conn.close()
   process.terminate();process.wait(timeout=6);self.assertEqual(process.returncode,0)
   with self.assertRaises(OSError):socket.create_connection(('127.0.0.1',port),timeout=.1)
  finally:
   if process.poll() is None:process.kill();process.wait()
if __name__=='__main__':unittest.main(verbosity=2)
