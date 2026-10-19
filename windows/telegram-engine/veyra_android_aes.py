"""Android-only AES-CTR provider for Flowseal's AES shim. No MTProto changes.
Uses the Android platform provider, not an obsolete embedded OpenSSL/cryptography.
Java byte-array conversion is explicit to preserve all octets.
"""
from java import jclass,jarray,jbyte
_JavaCipher=jclass('javax.crypto.Cipher')
_SecretKeySpec=jclass('javax.crypto.spec.SecretKeySpec')
_IvParameterSpec=jclass('javax.crypto.spec.IvParameterSpec')
def _array(data):return jarray(jbyte)(bytes(data))
class algorithms:
 class AES:
  def __init__(self,key):
   self.key=bytes(key)
   if len(self.key) not in (16,24,32):raise ValueError('Invalid AES key size')
class modes:
 class CTR:
  def __init__(self,iv):
   self.iv=bytes(iv)
   if len(self.iv)!=16:raise ValueError('Invalid CTR IV size')
class _Encryptor:
 def __init__(self,key,iv):
  self.cipher=_JavaCipher.getInstance('AES/CTR/NoPadding')
  self.cipher.init(1,_SecretKeySpec(_array(key),'AES'),_IvParameterSpec(_array(iv)))
 def update(self,data):
  if not data:return b''
  result=self.cipher.update(_array(bytes(data)))
  return b'' if result is None else bytes(result)
 def finalize(self):
  result=self.cipher.doFinal()
  return b'' if result is None else bytes(result)
class Cipher:
 def __init__(self,algorithm,mode,backend=None):self.key=algorithm.key;self.iv=mode.iv
 def encryptor(self):return _Encryptor(self.key,self.iv)
 def decryptor(self):return _Encryptor(self.key,self.iv)
