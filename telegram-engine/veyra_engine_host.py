"""Private stdin/stdout lifecycle IPC. Secrets never enter command-line arguments."""
import json,sys,threading,time
from veyra_engine import Engine
_guard=threading.Lock()
def emit(value):
 with _guard:
  sys.stdout.write(json.dumps(value,separators=(',',':'))+'\n');sys.stdout.flush()
def main():
 engine=None
 try:
  line=sys.stdin.readline(4096)
  if not line:raise ValueError('Missing private configuration')
  cfg=json.loads(line);engine=Engine(cfg['secret'],cfg['port']);engine.start()
  emit({'event':'ready',**engine.snapshot()})
  def commands():
   try:
    for line in sys.stdin:
     if len(line)>4096:break
     if json.loads(line).get('command')=='stop':break
   except Exception:pass
   finally:
    try:engine.close()
    except Exception:pass
  thread=threading.Thread(target=commands,name='VeyraControl',daemon=True);thread.start()
  while thread.is_alive() and engine.live:
   emit({'event':'state',**engine.snapshot()});time.sleep(.5)
  if engine.error:emit({'event':'error','error':engine.error})
 finally:
  if engine:
   engine.close()
   # Already redacted by the lifecycle adapter; never print original upstream startup logs.
   sys.stderr.write(engine.diagnostics()+'\n')
if __name__=='__main__':
 try:main()
 except Exception as exc:
  emit({'event':'error','error':'Telegram engine: '+type(exc).__name__});sys.exit(1)
