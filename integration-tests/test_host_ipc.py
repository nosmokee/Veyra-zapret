import subprocess,sys,os,json,socket,time
from pathlib import Path
root=(Path(__file__).resolve().parents[1]/'telegram-engine'); key=os.urandom(16).hex()
for mode in ('stop','eof'):
 env=os.environ.copy();env['PYTHONPATH']=str(root)+':'+str(root/'python-libs')
 p=subprocess.Popen([sys.executable,str(root/'veyra_engine_host.py')],stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.PIPE,text=True,env=env)
 p.stdin.write(json.dumps({'secret':key,'port':0})+'\n');p.stdin.flush()
 first=p.stdout.readline();ready=json.loads(first);assert ready['event']=='ready' and ready['running'];port=ready['port']
 if mode=='stop':p.stdin.write('{"command":"stop"}\n');p.stdin.flush()
 p.stdin.close();p.wait(timeout=25);out=first+p.stdout.read();err=p.stderr.read();assert p.returncode==0,(p.returncode,err);assert key not in out+err and 'tg://' not in out+err
 try:s=socket.create_connection(('127.0.0.1',port),timeout=.5)
 except OSError:pass
 else:s.close();raise AssertionError('child listener leaked')
 print(mode+': ready, clean exit, listener closed, key redacted')
