"""Local MTProxy -> official TLS/WSS or dedicated CDN203 MTProto TCP. MIT."""
import base64, hashlib, os, socket, ssl, struct, threading, time
MAX = 8*1024*1024

def exact(s,n):
    if not 0<=n<=MAX: raise ValueError('Packet limit')
    out=bytearray()
    while len(out)<n:
        b=s.recv(n-len(out))
        if not b: raise EOFError()
        out.extend(b)
    return bytes(out)

class Ctr:
    def __init__(self,key,iv,pure=False):
        if len(iv)!=16: raise ValueError('IV')
        try:
            if pure: raise ImportError()
            from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes
            self.update=Cipher(algorithms.AES(key),modes.CTR(iv)).encryptor().update
        except ImportError:
            import pyaes
            self.update=pyaes.AESModeOfOperationCTR(key,counter=pyaes.Counter(int.from_bytes(iv,'big'))).encrypt
    def apply(self,b): return self.update(b)

def hosts(dc,media=False):
    if not 1<=dc<=5: raise ValueError('DC')
    a=f'kws{dc}.web.telegram.org';b=f'kws{dc}-1.web.telegram.org'
    return ([b,a] if media else [a,b])+[['','pluto','venus','aurora','vesta','flora'][dc]+'.web.telegram.org']

def decode(h,secret):
    if len(h)!=64 or len(secret)!=16: raise ValueError('Hello')
    p=Ctr(hashlib.sha256(h[8:40]+secret).digest(),h[40:56]).apply(h)
    dc=struct.unpack_from('<h',p,60)[0];proto=struct.unpack_from('<I',p,56)[0]
    if (abs(dc) not in range(1,6) and abs(dc)!=203) or proto not in (0xeeeeeeee,0xdddddddd,0xefefefef): raise ValueError('DC/protocol')
    return dc,proto

def relay(dc,proto):
    while True:
        h=bytearray(os.urandom(64))
        if h[0]!=239 and h[4:8]!=b'\0'*4 and h[:4] not in (b'HEAD',b'POST',b'GET ',b'\xee'*4,b'\xdd'*4,b'\x16\x03\x01\x02'):break
    p=bytearray(h);struct.pack_into('<Ih',p,56,proto,dc)
    h[56:64]=Ctr(bytes(h[8:40]),bytes(h[40:56])).apply(bytes(p))[56:64]
    return bytes(h)

class WebSocket:
    def __init__(self,s):self.s=s;self.write_lock=threading.Lock()
    @classmethod
    def connect(cls,host,address,connector=None,track=lambda s:None):
        raw=connector(address,443) if connector else socket.create_connection((address,443),timeout=6)
        track(raw)
        try:
            raw.settimeout(7);raw.setsockopt(socket.IPPROTO_TCP,socket.TCP_NODELAY,1)
            s=ssl.create_default_context().wrap_socket(raw,server_hostname=host);track(s)
            key=base64.b64encode(os.urandom(16)).decode()
            s.sendall((f'GET /apiws HTTP/1.1\r\nHost: {host}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: {key}\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: binary\r\n\r\n').encode())
            head=bytearray()
            while len(head)<16384 and not head.endswith(b'\r\n\r\n'):head.extend(exact(s,1))
            if not head.endswith(b'\r\n\r\n'):raise OSError('WS header limit')
            lines=head.decode('ascii').split('\r\n');fields={}
            for line in lines[1:]:
                if ':' in line:
                    k,v=line.split(':',1);k=k.strip().lower()
                    if k in fields:raise OSError('Duplicate WS header')
                    fields[k]=v.strip()
            accept=base64.b64encode(hashlib.sha1((key+'258EAFA5-E914-47DA-95CA-C5AB0DC85B11').encode()).digest()).decode()
            if not (lines[0].startswith('HTTP/1.1 101 ') and fields.get('sec-websocket-accept')==accept and fields.get('upgrade','').lower()=='websocket' and 'upgrade' in [v.strip().lower() for v in fields.get('connection','').split(',')] and fields.get('sec-websocket-protocol')=='binary'):raise OSError('WS upgrade rejected')
            s.settimeout(None);return cls(s)
        except BaseException:
            raw.close()
            if 's' in locals():s.close()
            raise
    def frame(self,op,data=b''):
        if len(data)>MAX:raise ValueError('Frame limit')
        n=len(data);head=bytes([128|op,128|n]) if n<126 else bytes([128|op,254])+struct.pack('>H',n) if n<65536 else bytes([128|op,255])+struct.pack('>Q',n)
        mask=os.urandom(4);payload=bytes(v^mask[i%4] for i,v in enumerate(data))
        with self.write_lock:self.s.sendall(head+mask+payload)
    def send(self,data):self.frame(2,data)
    def receive(self):
        out=bytearray();started=False
        while True:
            h=exact(self.s,2);op=h[0]&15;fin=bool(h[0]&128);n=h[1]&127
            if h[0]&112 or h[1]&128:raise OSError('WS flags')
            if n==126:n=int.from_bytes(exact(self.s,2),'big')
            elif n==127:n=int.from_bytes(exact(self.s,8),'big')
            if n>MAX or (op>=8 and (not fin or n>125)):raise OSError('WS frame limit')
            data=exact(self.s,n)
            if op==8:
                if n==1 or (n>=2 and int.from_bytes(data[:2],'big')!=1000):raise OSError('WS closed with error')
                return None
            if op==9:self.frame(10,data);continue
            if op==10:continue
            if op==2 and not started:started=True
            elif op!=0 or not started:raise OSError('WS message order')
            out.extend(data)
            if len(out)>MAX:raise OSError('WS message limit')
            if fin:return bytes(out)
    def close(self):
        try:self.s.shutdown(socket.SHUT_RDWR)
        except OSError:pass
        self.s.close()

class TcpTransport:
    CDN203='91.105.192.100'
    def __init__(self,s):self.s=s
    @classmethod
    def connect203(cls,connector=None,track=lambda s:None):
        s=connector(cls.CDN203,443) if connector else socket.create_connection((cls.CDN203,443),timeout=6)
        try:track(s);s.settimeout(None);s.setsockopt(socket.IPPROTO_TCP,socket.TCP_NODELAY,1);s.setsockopt(socket.SOL_SOCKET,socket.SO_KEEPALIVE,1);return cls(s)
        except BaseException:s.close();raise
    def send(self,data):self.s.sendall(data)
    def receive(self):return self.s.recv(65536) or None
    def frame(self,op,data=b''):pass # TCP keepalive is not a WebSocket ping.
    def close(self):
        try:self.s.shutdown(socket.SHUT_RDWR)
        except OSError:pass
        self.s.close()

class Bridge:
    def __init__(self,secret,connector=None):
        if len(secret)!=16:raise ValueError('Secret')
        self.secret=secret;self.connector=connector;self.lock=threading.RLock();self.sockets=set();self.clients=set();self.remotes=set();self.live=False;self.port=0;self.up=0;self.down=0;self.sessions=0;self.error='';self.workers=set();self.stopping=threading.Event();self.routes={}
    def track(self,s):
        with self.lock:
            if not self.live:s.close();raise OSError('Bridge stopped')
            self.sockets.add(s)
    def spawn(self,f,*args):
        def run():
            try:f(*args)
            finally:
                with self.lock:self.workers.discard(threading.current_thread())
        t=threading.Thread(target=run,daemon=True)
        with self.lock:self.workers.add(t)
        t.start()
    def start(self,port=0):
        if self.live:raise RuntimeError('Already started')
        s=socket.socket();s.setsockopt(socket.SOL_SOCKET,socket.SO_REUSEADDR,1)
        try:s.bind(('127.0.0.1',port));s.listen(64);s.settimeout(.3)
        except BaseException:s.close();raise
        self.listener=s;self.port=s.getsockname()[1];self.live=True;self.track(s);self.spawn(self.accept);self.spawn(self.heartbeat)
    def accept(self):
        while self.live:
            try:c,_=self.listener.accept()
            except socket.timeout:continue
            except OSError:break
            with self.lock:
                if not self.live or len(self.clients)>=64:c.close();continue
                self.clients.add(c)
            self.track(c);self.spawn(self.serve,c)
    def heartbeat(self):
        while not self.stopping.wait(25):
            with self.lock:remotes=list(self.remotes)
            for ws in remotes:
                try:ws.frame(9)
                except OSError:ws.close()
    def dial(self,dc,media):
        if dc==203:return TcpTransport.connect203(self.connector,self.track)
        key=(dc,media)
        candidates=[(host,address) for host in hosts(dc,media) for address in ([host,'149.154.167.220'] if dc in (2,4,203) else [host])]
        with self.lock:cached=self.routes.get(key)
        if cached:candidates.insert(0,cached)
        for host,address in dict.fromkeys(candidates):
            if not self.live:raise OSError('Stopped')
            try:
                ws=WebSocket.connect(host,address,self.connector,self.track)
                with self.lock:self.routes[key]=(host,address)
                return ws
            except (OSError,ValueError):pass
        raise OSError('Official Telegram WSS unavailable')
    def serve(self,c):
        ws=None
        try:
            c.settimeout(8);h=exact(c,64);dc,proto=decode(h,self.secret);c.settimeout(None)
            r=relay(dc,proto);ws=self.dial(abs(dc),dc<0)
            with self.lock:
                if not self.live:return
                self.remotes.add(ws);self.sessions+=1;self.error=''
            ws.send(r)
            cr=Ctr(hashlib.sha256(h[8:40]+self.secret).digest(),h[40:56]);cr.apply(bytes(64))
            rev=h[8:56][::-1];cw=Ctr(hashlib.sha256(rev[:32]+self.secret).digest(),rev[32:])
            rw=Ctr(r[8:40],r[40:56]);rw.apply(bytes(64));rev=r[8:56][::-1];rr=Ctr(rev[:32],rev[32:])
            def receive():
                try:
                    while self.live:
                        b=ws.receive()
                        if b is None:break
                        c.sendall(cw.apply(rr.apply(b)))
                        with self.lock:self.down+=len(b)
                except (OSError,EOFError,ValueError):pass
                finally:
                    try:c.shutdown(socket.SHUT_RDWR)
                    except OSError:pass
                    ws.close()
            self.spawn(receive)
            while self.live:
                head=cr.apply(exact(c,1 if proto==0xefefefef else 4))
                if proto==0xefefefef:
                    if head[0] in (127,255):
                        tail=cr.apply(exact(c,3));head+=tail;n=int.from_bytes(tail,'little')*4
                    else:n=(head[0]&127)*4
                else:n=int.from_bytes(head,'little')&0x7fffffff
                if not 1<=n<=MAX:raise ValueError('Packet limit')
                b=head+cr.apply(exact(c,n));ws.send(rw.apply(b))
                with self.lock:self.up+=len(b)
        except EOFError:pass
        except (OSError,ValueError):
            if self.live:self.error='Telegram: соединение не установлено или прервано'
        finally:
            with self.lock:self.clients.discard(c);self.remotes.discard(ws)
            try:c.shutdown(socket.SHUT_RDWR)
            except OSError:pass
            c.close()
            if ws:ws.close()
            with self.lock:self.sockets={s for s in self.sockets if s.fileno()!=-1}
    def close(self):
        self.live=False;self.stopping.set()
        with self.lock:sockets=list(self.sockets);workers=list(self.workers)
        for s in sockets:
            try:s.shutdown(socket.SHUT_RDWR)
            except OSError:pass
            s.close()
        for t in workers:
            if t is not threading.current_thread():t.join(.4)
        with self.lock:self.sockets.clear();self.clients.clear();self.remotes.clear()
