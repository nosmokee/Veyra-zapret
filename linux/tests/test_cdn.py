import pathlib,socket,sys,threading,unittest
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import telegram_bridge as tg
class CdnTests(unittest.TestCase):
 def test_cdn203_never_aliases_dc2(self):
  self.assertRaises(ValueError,tg.hosts,203)
  b=tg.Bridge(bytes(16));b.start()
  listener=socket.socket();listener.bind(('127.0.0.1',0));listener.listen();a=socket.create_connection(listener.getsockname());peer,_=listener.accept();listener.close();seen=[]
  def connector(address,port):seen.append((address,port));return a
  b.connector=connector
  try:
   raw=b.dial(203,True);self.assertIsInstance(raw,tg.TcpTransport);self.assertEqual(seen,[(tg.TcpTransport.CDN203,443)])
   raw.send(b'raw MTProto');self.assertEqual(tg.exact(peer,11),b'raw MTProto');peer.sendall(b'reply');self.assertEqual(raw.receive(),b'reply');raw.frame(9);peer.settimeout(.05);self.assertRaises(socket.timeout,peer.recv,1)
  finally:peer.close();b.close()
 def test_raw_large_stream(self):
  a,b=socket.socketpair();data=bytes(range(256))*8192;raw=tg.TcpTransport(a)
  def echo():
   got=tg.exact(b,len(data));b.sendall(got);b.close()
  t=threading.Thread(target=echo);t.start()
  writer=threading.Thread(target=raw.send,args=(data,));writer.start();out=bytearray()
  try:
   while len(out)<len(data):out.extend(raw.receive() or b'')
   self.assertEqual(out,data)
  finally:raw.close();writer.join(2);t.join(2)
