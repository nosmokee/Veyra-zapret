import hashlib,json,os,pathlib,socket,struct,sys,tempfile,time,unittest
from unittest.mock import patch
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import app,telegram_bridge as tg
class Tests(unittest.TestCase):
 def test_ctr_nist_both_backends_streaming(self):
  key=bytes.fromhex('2b7e151628aed2a6abf7158809cf4f3c');iv=bytes.fromhex('f0f1f2f3f4f5f6f7f8f9fafbfcfdfeff');plain=bytes.fromhex('6bc1bee22e409f96e93d7e117393172a');expected=bytes.fromhex('874d6191b620e3261bef6864990db6ce')
  for pure in (False,True):
   c=tg.Ctr(key,iv,pure);self.assertEqual(c.apply(plain[:3])+c.apply(plain[3:]),expected)
 def test_hello_every_dc_protocol(self):
  secret=os.urandom(16)
  for dc in list(range(-5,0))+list(range(1,6))+[-203,203]:
   for proto in (0xeeeeeeee,0xdddddddd,0xefefefef):
    h=bytearray(os.urandom(64));struct.pack_into('<Ih',h,56,proto,dc);wire=bytearray(h);wire[56:]=tg.Ctr(hashlib.sha256(h[8:40]+secret).digest(),h[40:56]).apply(bytes(h))[56:];self.assertEqual(tg.decode(bytes(wire),secret),(dc,proto))
    self.assertRaises(ValueError,tg.decode,bytes(wire),os.urandom(16))
 def test_bad_hello_and_routes(self):
  self.assertRaises(ValueError,tg.decode,bytes(63),bytes(16));self.assertRaises(ValueError,tg.hosts,6)
  self.assertEqual(tg.hosts(2)[0],'kws2.web.telegram.org');self.assertEqual(tg.hosts(2,True)[0],'kws2-1.web.telegram.org')
 def test_ws_rejects_masked_rsv_and_oversize(self):
  for head in (b'\x82\x80',b'\xc2\x00',b'\x82\x7f'+struct.pack('>Q',tg.MAX+1),b'\x09\x00'):
   a,b=socket.socketpair();b.sendall(head)
   try:self.assertRaises(OSError,tg.WebSocket(a).receive)
   finally:a.close();b.close()
 def test_ws_fragment_and_ping(self):
  a,b=socket.socketpair();b.sendall(b'\x02\x02ab\x89\x01!\x80\x02cd');ws=tg.WebSocket(a)
  try:self.assertEqual(ws.receive(),b'abcd');h=tg.exact(b,2);self.assertEqual(h,b'\x8a\x81');mask=tg.exact(b,4);self.assertEqual(tg.exact(b,1)[0]^mask[0],33)
  finally:ws.close();b.close()
 def test_stop_releases_listener_and_pending_clients(self):
  b=tg.Bridge(os.urandom(16));b.start();port=b.port;c=socket.create_connection(('127.0.0.1',port));time.sleep(.05);b.close();c.close();self.assertFalse(b.live)
  with socket.socket() as s:s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1);s.bind(('127.0.0.1',port))
 def test_unified_lifecycle_stable_endpoint_and_no_secret_export(self):
  with tempfile.TemporaryDirectory() as d:
   c=app.Controller(d)
   try:
    s=c.connect_all('plain',open_browser=False);self.assertTrue(s['running']);self.assertTrue(s['telegram']['running']);cfg=json.loads((pathlib.Path(d)/'telegram-private.json').read_text());self.assertNotIn(cfg['secret'],json.dumps(s));self.assertEqual((pathlib.Path(d)/'telegram-private.json').stat().st_mode&0o777,0o600)
    c.stop();self.assertIsNone(c.bridge);c.connect_all('plain',open_browser=False);self.assertEqual(c.bridge.port,cfg['port']);self.assertEqual(c.bridge.secret.hex(),cfg['secret'])
   finally:c.stop()
 def test_occupied_saved_endpoint_rolls_back_engine(self):
  with tempfile.TemporaryDirectory() as d, socket.socket() as occupied:
   occupied.bind(('127.0.0.1',0));occupied.listen();(pathlib.Path(d)/'telegram-private.json').write_text(json.dumps({'secret':os.urandom(16).hex(),'port':occupied.getsockname()[1]}));c=app.Controller(d)
   try:self.assertRaises(OSError,c.connect_all,'plain',False);self.assertFalse(c.running());self.assertIsNone(c.bridge)
   finally:c.stop()
 def test_browser_failure_does_not_fake_success(self):
  with tempfile.TemporaryDirectory() as d:
   c=app.Controller(d)
   try:
    with patch.object(c,'browser',side_effect=ValueError('Нет браузера')):s=c.connect_all('plain');self.assertEqual(s['browser_warning'],'Нет браузера');self.assertTrue(s['telegram']['running'])
   finally:c.stop()

 def test_engine_crash_stops_telegram(self):
  with tempfile.TemporaryDirectory() as d:
   c=app.Controller(d)
   try:
    c.connect_all('plain',open_browser=False);b=c.bridge;c.proc.kill();c.proc.wait();state=c.state();self.assertFalse(state['telegram']['running']);self.assertFalse(b.live)
   finally:c.stop()
