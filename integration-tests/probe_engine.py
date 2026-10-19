import asyncio,os,struct,time,hashlib,json,subprocess,sys,socket
from pathlib import Path
from cryptography.hazmat.primitives.ciphers import Cipher,algorithms,modes
ROOT=Path(__file__).resolve().parent;secret=os.urandom(16)
def ctr(k,v):return Cipher(algorithms.AES(k),modes.CTR(v)).encryptor()
async def probe(dc,port):
 start=time.monotonic();w=None
 try:
  r,w=await asyncio.open_connection('127.0.0.1',port)
  h=bytearray(os.urandom(64));h[0]=0x37;h[56:60]=b'\xdd'*4;h[60:62]=struct.pack('<h',dc)
  enc=ctr(hashlib.sha256(h[8:40]+secret).digest(),bytes(h[40:56]));rev=bytes(h[8:56])[::-1]
  dec=ctr(hashlib.sha256(rev[:32]+secret).digest(),rev[32:]);tail=enc.update(bytes(h));h[56:]=tail[56:]
  nonce=os.urandom(16);body=struct.pack('<I',0xbe7e8ef1)+nonce
  pkt=struct.pack('<QQI',0,(int(time.time()*2**32)//4)*4,len(body))+body+os.urandom(8)
  w.write(h+enc.update(struct.pack('<I',len(pkt))+pkt));await w.drain()
  async with asyncio.timeout(35):
   n=struct.unpack('<i',dec.update(await r.readexactly(4)))[0]
   if not 4<=n<=1048576:raise ValueError(f'invalid length/error {n}')
   p=dec.update(await r.readexactly(n))
   if len(p)==4:raise ValueError(f'Telegram error {struct.unpack("<i",p)[0]}')
   if len(p)<40 or p[20:24]!=struct.pack('<I',0x05162463) or p[24:40]!=nonce:raise ValueError('resPQ/nonce mismatch')
  return {'dc':dc,'status':'resPQ nonce verified','seconds':round(time.monotonic()-start,3),'bytes':n}
 except Exception as e:return {'dc':dc,'status':type(e).__name__,'detail':str(e)[:160],'seconds':round(time.monotonic()-start,3)}
 finally:
  if w:w.close();await w.wait_closed()
async def main():
 raise SystemExit('Run test_integrated.py; this module supplies anonymous probe helpers.')
