"""Explicit opt-in network test. Only MTProto req_pq/resPQ; no login or messages."""
import concurrent.futures,hashlib,os,pathlib,socket,struct,sys,tempfile,time
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parents[1]))
import app,telegram_bridge as tg

def one(c,dc,proto):
 b=c.bridge;secret=b.secret
 with socket.create_connection(('127.0.0.1',b.port),timeout=25) as s:
  s.settimeout(25);raw=bytearray(os.urandom(64));struct.pack_into('<Ih',raw,56,proto,dc);hello=bytearray(raw)
  tx=tg.Ctr(hashlib.sha256(raw[8:40]+secret).digest(),raw[40:56]);hello[56:64]=tx.apply(bytes(raw))[56:64]
  rev=hello[8:56][::-1];rx=tg.Ctr(hashlib.sha256(rev[:32]+secret).digest(),rev[32:]);s.sendall(hello)
  nonce=os.urandom(16);body=struct.pack('<I',0xbe7e8ef1)+nonce;packet=bytes(8)+struct.pack('<QI',int(time.time())<<32,len(body))+body
  if proto==0xdddddddd:packet+=os.urandom(8)
  head=bytes([len(packet)//4]) if proto==0xefefefef else struct.pack('<I',len(packet));s.sendall(tx.apply(head+packet))
  prefix=rx.apply(tg.exact(s,1 if proto==0xefefefef else 4))
  n=(int.from_bytes(rx.apply(tg.exact(s,3)),'little')*4 if prefix[0]==127 else prefix[0]*4) if proto==0xefefefef else int.from_bytes(prefix,'little')
  if not 40<=n<=tg.MAX:raise AssertionError('Response length '+str(n))
  response=rx.apply(tg.exact(s,n));assert response[20:24]==struct.pack('<I',0x05162463),'resPQ';assert response[24:40]==nonce,'nonce'
 return f'PASS dc={dc} proto={proto:x} resPQ nonce matched'
if __name__=='__main__':
 failures=0
 for profile,items in [('plain',[(dc,p) for dc in list(range(-5,0))+list(range(1,6))+[-203,203] for p in (0xeeeeeeee,0xdddddddd,0xefefefef)]),('youtube',[(2,p) for p in (0xeeeeeeee,0xdddddddd,0xefefefef)])]:
  with tempfile.TemporaryDirectory() as d:
   c=app.Controller(d);c.connect_all(profile,open_browser=False)
   try:
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
     futures={pool.submit(one,c,dc,p):(dc,p) for dc,p in items}
     for f in concurrent.futures.as_completed(futures):
      try:print(profile,f.result(),flush=True)
      except Exception as e:failures+=1;print('FAIL',profile,futures[f],type(e).__name__,str(e),flush=True)
    print('STATS',profile,c.telegram_state(),flush=True)
   finally:c.stop()
 print('FAILURES',failures,flush=True);sys.exit(bool(failures))
