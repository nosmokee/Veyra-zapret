"""Loopback only: bundled fake payload and SOCKS UDP forwarding, not ISP bypass."""
from test_youtube_tcp import commands, read_exact, NATIVE, ROOT
import socket, subprocess, tempfile, shlex, time, unittest
from pathlib import Path

class QuicFakeTest(unittest.TestCase):
 @classmethod
 def setUpClass(cls):
  cls.tmp=tempfile.TemporaryDirectory();cls.binary=Path(cls.tmp.name)/'ciadpi'
  sources=['main.c','packets.c','conev.c','proxy.c','desync.c','mpool.c','extend.c']
  subprocess.run(['cc','-D_DEFAULT_SOURCE','-std=c99','-O2','-I',str(NATIVE),*[str(NATIVE/s) for s in sources],'-o',str(cls.binary)],check=True,capture_output=True)
 @classmethod
 def tearDownClass(cls):cls.tmp.cleanup()
 def exchange(self,matched):
  asset=ROOT/'android/app/src/main/assets/quic_initial_www_google_com.bin'
  with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as server, socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as client:
   server.bind(('127.0.0.1',0));server.settimeout(3);client.bind(('127.0.0.1',0));client.settimeout(3)
   with socket.socket() as probe:probe.bind(('127.0.0.1',0));port=probe.getsockname()[1]
   args=shlex.split(commands()['YOUTUBE_QUIC_FAKE_COMMAND']);args[args.index('--port')+1]=str(port)
   args[args.index('--fake-data')+1]=str(asset)
   if matched:args[args.index('--pf')+1]=str(server.getsockname()[1])
   with tempfile.TemporaryFile() as log:
    p=subprocess.Popen([str(self.binary),*args],stdout=log,stderr=log)
    try:
     for _ in range(100):
      try:control=socket.create_connection(('127.0.0.1',port),timeout=1);break
      except OSError:
       if p.poll() is not None:log.seek(0);self.fail(log.read().decode())
       time.sleep(.03)
     else:self.fail('Proxy did not start')
     with control:
      control.settimeout(3);control.sendall(b'\x05\x01\x00');self.assertEqual(b'\x05\x00',read_exact(control,2))
      control.sendall(b'\x05\x03\x00\x01'+b'\x00'*6)
      header=read_exact(control,4);self.assertEqual(0,header[1]);self.assertIn(header[3],(1,4))
      size=4 if header[3]==1 else 16;address=read_exact(control,size);relayport=int.from_bytes(read_exact(control,2),'big')
      relay=(socket.inet_ntop(socket.AF_INET if size==4 else socket.AF_INET6,address),relayport)
      if size==16:relay=('127.0.0.1',relayport)
      real=b'VEYRA-REAL-UDP-TEST'
      client.sendto(b'\x00\x00\x00\x01'+socket.inet_aton('127.0.0.1')+server.getsockname()[1].to_bytes(2,'big')+real,relay)
      received=[]
      for _ in range(getattr(self,"fake_count",11)+1 if matched else 1):
       data,peer=server.recvfrom(65535);received.append(data)
       if data==real:server.sendto(data,peer)
      self.assertEqual(([asset.read_bytes()]*getattr(self,"fake_count",11) if matched else [])+[real],received)
      self.assertTrue(client.recvfrom(65535)[0].endswith(real))
      server.settimeout(.2)
      with self.assertRaises(socket.timeout):server.recvfrom(65535)
    finally:
     p.terminate()
     try:p.wait(3)
     except subprocess.TimeoutExpired:p.kill();p.wait()
 def test_udp_fake_packets_then_real(self):self.exchange(True)
 def test_non443_udp_passthrough(self):self.exchange(False)

if __name__=='__main__':unittest.main(verbosity=2)
