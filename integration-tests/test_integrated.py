import sys,asyncio,os,socket,json,time,struct,hashlib,unittest
from pathlib import Path
from cryptography.hazmat.primitives.ciphers import Cipher,algorithms,modes
ROOT=(Path(__file__).resolve().parents[1]/'telegram-engine');sys.path.insert(0,str(ROOT));sys.path.insert(1,str(ROOT/'python-libs'))
from veyra_engine import Engine
probe_source=Path(__file__).with_name('probe_engine.py').read_text();ns={'__file__':str(Path(__file__).with_name('probe_engine.py'))};exec(probe_source[:probe_source.index('async def main():')],ns)
results=[]
for cycle in range(3):
 key=os.urandom(16);engine=Engine(key.hex(),0)
 try:
  port=engine.start();ns['secret']=key
  async def run():return await asyncio.gather(*(ns['probe'](dc,port) for dc in [1,2,3,4,5,-2,-4,203]))
  checks=asyncio.run(run());results.append({'cycle':cycle,'checks':checks})
  assert all(x['status']=='resPQ nonce verified' for x in checks),checks
  assert key.hex() not in engine.diagnostics();assert 'tg://proxy?' not in engine.diagnostics()
  try:Engine(os.urandom(16).hex(),0).start()
  except RuntimeError:pass
  else:raise AssertionError('Duplicate engine allowed')
 finally:engine.close()
 assert not engine.live
 try:s=socket.create_connection(('127.0.0.1',port),timeout=.3)
 except OSError:pass
 else:s.close();raise AssertionError('Listener leaked')
 print('Cycle',cycle+1,'8 resPQ valid; duplicate engine rejected; listener closed; key redacted',flush=True)
Path(__file__).with_name('integrated-network-results.json').write_text(json.dumps(results,indent=2))
# Port conflicts must fail without moving a saved endpoint.
s=socket.socket();s.bind(('127.0.0.1',0));s.listen();busy=s.getsockname()[1]
e=Engine(os.urandom(16).hex(),busy)
try:
 try:e.start()
 except OSError:pass
 else:raise AssertionError('Busy port accepted')
finally:e.close();s.close()
print('Busy saved port rejected; all checks passed')
