using System;
using System.Diagnostics;
using System.IO;
using System.Net;
using System.Net.Sockets;
using System.Runtime.Serialization;
using System.Runtime.Serialization.Json;
using System.Text;
using System.Threading;

namespace ZapretDesk {
 [DataContract] public sealed class FlowsealState {
  [DataMember(Name="event")] public string Event;
  [DataMember(Name="running")] public bool Running;
  [DataMember(Name="port")] public int Port;
  [DataMember(Name="up")] public long Up;
  [DataMember(Name="down")] public long Down;
  [DataMember(Name="sessions")] public long Sessions;
  [DataMember(Name="error")] public string Error;
 }
 /// <summary>Owns a bundled Python child running Flowseal's original Telegram core.
 /// Secret crosses only a private stdin pipe; parent death closes stdin and stops it.</summary>
 public sealed class FlowsealProcessBridge:IDisposable {
  readonly object gate=new object();readonly byte[] secret;readonly Action<string> report;
  Process process;ManualResetEventSlim ready;volatile bool running;volatile string error="";
  int port;long up,down,sessions;bool disposed;
  public FlowsealProcessBridge(byte[] key,Action<string> reporter=null){if(key==null||key.Length!=16)throw new ArgumentException("Invalid MTProxy key");secret=(byte[])key.Clone();report=reporter;}
  public bool Running{get{return running;}}public int Port{get{return port;}}public long Up{get{return Interlocked.Read(ref up);}}public long Down{get{return Interlocked.Read(ref down);}}public long Sessions{get{return Interlocked.Read(ref sessions);}}public string Error{get{return error;}}
  string Hex{get{return BitConverter.ToString(secret).Replace("-","").ToLowerInvariant();}}
  public string Link{get{return "tg://proxy?server=127.0.0.1&port="+port+"&secret=dd"+Hex;}}
  void Report(string text){try{if(report!=null)report(text.Replace(Hex,"[secret]"));}catch{}}
  public void StartSaved(string folder){
   string path=Path.Combine(folder,"telegram-port.txt");int requested=1087;
   if(File.Exists(path)){if(!Int32.TryParse(File.ReadAllText(path).Trim(),out requested)||requested<1||requested>65535)throw new InvalidDataException("Некорректный сохранённый Telegram-порт.");}
   else {var probe=new TcpListener(IPAddress.Loopback,requested);try{probe.Start();}catch(SocketException){probe.Stop();probe=new TcpListener(IPAddress.Loopback,0);probe.Start();requested=((IPEndPoint)probe.LocalEndpoint).Port;}finally{probe.Stop();}}
   Start(requested);
   if(!File.Exists(path)){string temp=path+"."+Guid.NewGuid().ToString("N")+".tmp";try{File.WriteAllText(temp,port.ToString(System.Globalization.CultureInfo.InvariantCulture));File.Move(temp,path);}catch{Dispose();throw;}finally{if(File.Exists(temp))File.Delete(temp);}}
  }
  public void Start(int requested){
   lock(gate){
    if(disposed)throw new ObjectDisposedException("FlowsealProcessBridge");if(process!=null)throw new InvalidOperationException("Telegram engine already started");
    string root=AppDomain.CurrentDomain.BaseDirectory;string python=Path.Combine(root,"python-runtime","python.exe"),host=Path.Combine(root,"telegram-engine","veyra_engine_host.py");
    if(!File.Exists(python)||!File.Exists(host))throw new FileNotFoundException("Не найдено встроенное ядро Telegram. Распакуй весь архив Veyra, не только EXE.");
    ready=new ManualResetEventSlim(false);port=requested;error="";
    var p=new Process{StartInfo=new ProcessStartInfo(python,"-I -u \""+host+"\""){UseShellExecute=false,CreateNoWindow=true,RedirectStandardInput=true,RedirectStandardOutput=true,RedirectStandardError=true,StandardOutputEncoding=Encoding.UTF8,StandardErrorEncoding=Encoding.UTF8,WorkingDirectory=root},EnableRaisingEvents=true};
    p.OutputDataReceived+=(s,e)=>{if(String.IsNullOrEmpty(e.Data)||e.Data.Length>8192)return;try{FlowsealState state;using(var ms=new MemoryStream(Encoding.UTF8.GetBytes(e.Data))){state=(FlowsealState)new DataContractJsonSerializer(typeof(FlowsealState)).ReadObject(ms);}Interlocked.Exchange(ref up,state.Up);Interlocked.Exchange(ref down,state.Down);Interlocked.Exchange(ref sessions,state.Sessions);if(state.Port>0)port=state.Port;if(state.Event=="ready"){running=state.Running;ready.Set();}else if(state.Event=="state")running=state.Running;else if(state.Event=="error"){error=state.Error??"Telegram engine error";running=false;ready.Set();Report(error);}}catch{error="Некорректный ответ Telegram-ядра";ready.Set();}};
    p.ErrorDataReceived+=(s,e)=>{if(!String.IsNullOrEmpty(e.Data))Report(e.Data.Length>1000?e.Data.Substring(0,1000):e.Data);};
    p.Exited+=(s,e)=>{running=false;if(!disposed){error="Telegram-ядро завершилось";try{ready.Set();}catch(ObjectDisposedException){}}};
    process=p;
    try {p.Start();p.BeginOutputReadLine();p.BeginErrorReadLine();p.StandardInput.WriteLine("{\"secret\":\""+Hex+"\",\"port\":"+requested+"}");p.StandardInput.Flush();}
    catch{process=null;p.Dispose();throw;}
   }
   if(!ready.Wait(25000)||!running){string message=String.IsNullOrEmpty(error)?"Не удалось запустить Telegram-ядро":error;Dispose();throw new IOException(message+". Проверь журнал и занятость сохранённого порта.");}
   Report("ENGINE Flowseal/tg-ws-proxy v1.11.2 CF/H2=enabled saved-endpoint=preserved");
  }
  public void Dispose(){
   Process p;lock(gate){if(disposed)return;disposed=true;running=false;p=process;process=null;}
   if(p!=null){try{if(!p.HasExited){p.StandardInput.WriteLine("{\"command\":\"stop\"}");p.StandardInput.Flush();p.StandardInput.Close();if(!p.WaitForExit(15000)){p.Kill();p.WaitForExit(3000);}}}catch{try{if(!p.HasExited)p.Kill();}catch{}}finally{p.Dispose();}}
   // The event stays alive until callbacks are gone; it contains no secret.
   Array.Clear(secret,0,secret.Length);
  }
 }
}
