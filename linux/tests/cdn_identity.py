"""Opt-in public-server test. No account or private auth keys; compares public RSA IDs.
Run: python3 tests/cdn_identity.py. Does not establish successful media playback.
"""
import hashlib,os,pathlib,socket,struct,sys,time
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import telegram_bridge as tg

def packet():
 nonce=os.urandom(16);body=struct.pack('<I',0xbe7e8ef1)+nonce
 return nonce,bytes(8)+struct.pack('<QI',int(time.time())<<32,len(body))+body

def fingerprints(response,nonce):
 assert response[20:24]==struct.pack('<I',0x05162463) and response[24:40]==nonce
 at=56;n=response[at];prefix=1
 if n==254:n=int.from_bytes(response[at+1:at+4],'little');prefix=4
 at+=(prefix+n+3)//4*4
 assert response[at:at+4]==struct.pack('<I',0x1cb5c415)
 count=int.from_bytes(response[at+4:at+8],'little');assert 1<=count<=16
 return sorted(struct.unpack_from('<q',response,at+8+i*8)[0] for i in range(count))

def probe(sock,hello,tx,rx):
 sock.settimeout(15);sock.sendall(hello);nonce,p=packet();sock.sendall(tx.apply(struct.pack('<I',len(p))+p))
 n=int.from_bytes(rx.apply(tg.exact(sock,4)),'little');assert 40<=n<=tg.MAX
 return fingerprints(rx.apply(tg.exact(sock,n)),nonce)

def direct():
 h=tg.relay(203,0xeeeeeeee);tx=tg.Ctr(h[8:40],h[40:56]);tx.apply(bytes(64));r=h[8:56][::-1];rx=tg.Ctr(r[:32],r[32:])
 with socket.create_connection((tg.TcpTransport.CDN203,443),timeout=10) as sock:return probe(sock,h,tx,rx)

def bridge(old=False):
 key=os.urandom(16);b=tg.Bridge(key)
 if old:b.dial=lambda dc,media:tg.WebSocket.connect('kws2.web.telegram.org','kws2.web.telegram.org',track=b.track)
 b.start()
 try:
  raw=bytearray(os.urandom(64));struct.pack_into('<Ih',raw,56,0xeeeeeeee,203);h=bytearray(raw)
  tx=tg.Ctr(hashlib.sha256(raw[8:40]+key).digest(),raw[40:56]);h[56:64]=tx.apply(bytes(raw))[56:64]
  r=h[8:56][::-1];rx=tg.Ctr(hashlib.sha256(r[:32]+key).digest(),r[32:])
  with socket.create_connection(('127.0.0.1',b.port),timeout=10) as sock:return probe(sock,h,tx,rx)
 finally:b.close()

if __name__=='__main__':
 expected=direct();old=bridge(True);fixed=bridge()
 print('Direct CDN203 public RSA IDs:',expected)
 print('Old DC2 WSS alias:',old,'matches CDN203:',old==expected)
 print('Fixed CDN203 route:',fixed,'matches CDN203:',fixed==expected)
 assert fixed==expected and old!=expected
 print('PASS route identity; NOT an authenticated account/file-download test.')
