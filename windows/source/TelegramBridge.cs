// Independent C# implementation of Telegram's obfuscated MTProto transport.
// Design reference: Flowseal/tg-ws-proxy, also identified in supplied SPECTRAL.
// No SPECTRAL executable/code, third-party relay pool or certificate bypass used.
using System;
using System.IO;
using System.Linq;
using System.Net;
using System.Net.Sockets;
using System.Net.WebSockets;
using System.Security.Cryptography;
using System.Threading;
using System.Threading.Tasks;
using System.Collections.Generic;
namespace ZapretDesk {
 public sealed class TgCtr:IDisposable {
  readonly Aes aes;readonly ICryptoTransform cipher;readonly byte[] counter,block=new byte[16];int pos=16;
  public TgCtr(byte[] key,byte[] iv){if(iv.Length!=16)throw new ArgumentException("CTR IV");aes=Aes.Create();aes.Mode=CipherMode.ECB;aes.Padding=PaddingMode.None;aes.Key=key;cipher=aes.CreateEncryptor();counter=(byte[])iv.Clone();}
  public byte[] Apply(byte[] input){var output=new byte[input.Length];Apply(input,0,input.Length,output);return output;}
  public void Apply(byte[] input,int offset,int count,byte[] output){for(int i=0;i<count;i++){if(pos==16){cipher.TransformBlock(counter,0,16,block,0);for(int j=15;j>=0;j--)if(++counter[j]!=0)break;pos=0;}output[i]=(byte)(input[offset+i]^block[pos++]);}}
  public void Dispose(){cipher.Dispose();aes.Dispose();Array.Clear(counter,0,counter.Length);Array.Clear(block,0,block.Length);}
 }
 public static class TgProtocol {
  public const int MaxPacket=8*1024*1024;
  public static byte[] Slice(byte[] x,int offset,int count){var r=new byte[count];Buffer.BlockCopy(x,offset,r,0,count);return r;}
  public static byte[] Random(int n){var r=new byte[n];using(var g=RandomNumberGenerator.Create())g.GetBytes(r);return r;}
  public static byte[] Hash(byte[] key,byte[] secret){using(var h=SHA256.Create())return h.ComputeHash(key.Concat(secret).ToArray());}
  public static bool Decode(byte[] hello,byte[] secret,out short dc,out uint protocol){dc=0;protocol=0;if(hello==null||hello.Length!=64||secret==null||secret.Length!=16)return false;using(var c=new TgCtr(Hash(Slice(hello,8,32),secret),Slice(hello,40,16))){var plain=c.Apply(hello);protocol=BitConverter.ToUInt32(plain,56);dc=BitConverter.ToInt16(plain,60);return (protocol==0xeeeeeeee||protocol==0xdddddddd||protocol==0xefefefef)&&(Math.Abs((int)dc)>=1&&Math.Abs((int)dc)<=5||Math.Abs((int)dc)==203);}}
  public static byte[] Relay(short dc,uint protocol){byte[] h;do{h=Random(64);}while(h[0]==0xef||BitConverter.ToUInt32(h,4)==0||new uint[]{0x44414548,0x54534f50,0x20544547,0xeeeeeeee,0xdddddddd,0x02010316}.Contains(BitConverter.ToUInt32(h,0)));var p=(byte[])h.Clone();Buffer.BlockCopy(BitConverter.GetBytes(protocol),0,p,56,4);Buffer.BlockCopy(BitConverter.GetBytes(dc),0,p,60,2);using(var c=new TgCtr(Slice(h,8,32),Slice(h,40,16))){var encrypted=c.Apply(p);Buffer.BlockCopy(encrypted,56,h,56,8);}return h;}
  public static TgCtr ClientRead(byte[] h,byte[] secret){var c=new TgCtr(Hash(Slice(h,8,32),secret),Slice(h,40,16));c.Apply(new byte[64]);return c;}
  public static TgCtr ClientWrite(byte[] h,byte[] secret){var r=Slice(h,8,48).Reverse().ToArray();return new TgCtr(Hash(Slice(r,0,32),secret),Slice(r,32,16));}
  public static TgCtr RemoteWrite(byte[] h){var c=new TgCtr(Slice(h,8,32),Slice(h,40,16));c.Apply(new byte[64]);return c;}
  public static TgCtr RemoteRead(byte[] h){var r=Slice(h,8,48).Reverse().ToArray();return new TgCtr(Slice(r,0,32),Slice(r,32,16));}
 }
 public interface ITgSocket:IDisposable {Task Send(byte[] data,CancellationToken token);Task<byte[]> Receive(CancellationToken token);}
 public sealed class OfficialTgSocket:ITgSocket {
  readonly ClientWebSocket socket;
  static readonly System.Collections.Concurrent.ConcurrentDictionary<string,Tuple<string,string>> routes=new System.Collections.Concurrent.ConcurrentDictionary<string,Tuple<string,string>>();
  OfficialTgSocket(ClientWebSocket s){socket=s;}
  public static Task<ITgSocket> Connect(int dc,bool media,CancellationToken token){return ConnectDetailed(dc,media,token,null);}
  public static async Task<ITgSocket> ConnectDetailed(int dc,bool media,CancellationToken token,Action<string> report){if(dc==203){if(report!=null)report("MTProto TCP dc=203 endpoint="+TelegramTcpTransport.Cdn203+":443 (not WSS)");return await TelegramTcpTransport.Connect203(token).ConfigureAwait(false);}Exception last=null;string key=dc+":"+media;Tuple<string,string> cached;var choices=TelegramRoutes.Hosts(dc,media).SelectMany(h=>TelegramRoutes.Addresses(dc,h).Select(a=>Tuple.Create(h,a)));if(routes.TryGetValue(key,out cached))choices=new[]{cached}.Concat(choices);foreach(var route in choices.Distinct()){string host=route.Item1,address=route.Item2;using(var timeout=CancellationTokenSource.CreateLinkedTokenSource(token)){timeout.CancelAfter(TimeSpan.FromSeconds(5));try{if(report!=null)report("WSS attempt host="+host+" address="+address+" TLS=verified");var socket=await TelegramTransport.Connect(host,address,timeout.Token).ConfigureAwait(false);routes[key]=route;if(report!=null)report("WSS connected host="+host+" address="+address);return socket;}catch(Exception e){last=e;token.ThrowIfCancellationRequested();if(report!=null)report("WSS failed "+host+" "+TelegramDiagnostics.Describe(e));}}}throw new IOException("Официальный WebSocket Telegram недоступен (TLS/DNS/сеть).",last);}

  public Task Send(byte[] data,CancellationToken t){return socket.SendAsync(new ArraySegment<byte>(data),WebSocketMessageType.Binary,true,t);}
  public async Task<byte[]> Receive(CancellationToken t){var buf=new byte[65536];using(var output=new MemoryStream()){while(true){var r=await socket.ReceiveAsync(new ArraySegment<byte>(buf),t).ConfigureAwait(false);if(r.MessageType==WebSocketMessageType.Close){if(r.CloseStatus.HasValue&&r.CloseStatus!=WebSocketCloseStatus.NormalClosure)throw new TelegramCloseException((int)r.CloseStatus.Value);return null;}if(r.MessageType!=WebSocketMessageType.Binary)throw new IOException("Неожиданный ответ WebSocket.");if(output.Length+r.Count>TgProtocol.MaxPacket)throw new IOException("Лимит кадра Telegram.");output.Write(buf,0,r.Count);if(r.EndOfMessage)return output.ToArray();}}}
  public void Dispose(){socket.Abort();socket.Dispose();}
 }
 public sealed class TelegramCloseException:IOException {public readonly int Code;public TelegramCloseException(int code):base("Telegram WebSocket closed"){Code=code;}}
 public static class TelegramDiagnostics {
  static readonly object fileGate=new object();
  public static string Describe(Exception error){var parts=new List<string>();for(int depth=0;error!=null&&depth<8;depth++,error=error.InnerException){string item=error.GetType().FullName+" HResult=0x"+error.HResult.ToString("X8");var close=error as TelegramCloseException;if(close!=null)item+=" Close="+close.Code;var socket=error as SocketException;if(socket!=null)item+=" Socket="+socket.SocketErrorCode;var web=error as WebException;if(web!=null){item+=" Web="+web.Status;var response=web.Response as HttpWebResponse;if(response!=null)item+=" HTTP="+(int)response.StatusCode;}var win=error as System.ComponentModel.Win32Exception;if(win!=null)item+=" Native="+win.NativeErrorCode;parts.Add(item);}return String.Join(" <- ",parts);}
  public static void Write(string folder,string text){try{lock(fileGate){Directory.CreateDirectory(folder);string path=Path.Combine(folder,"telegram-diagnostic.txt");var lines=File.Exists(path)?File.ReadAllLines(path).KeepLatest(191).ToList():new List<string>();lines.Add(DateTime.UtcNow.ToString("o")+" "+text);File.WriteAllLines(path,lines,new System.Text.UTF8Encoding(false));}}catch{/* Diagnostics must not break traffic. */}}
  public static IEnumerable<string> KeepLatest(this IEnumerable<string> items,int keep){var values=items.ToArray();return values.Skip(Math.Max(0,values.Length-keep));}
 }
 public sealed class TelegramBridge:IDisposable {
  readonly object gate=new object();readonly Action<string> diagnostic;readonly byte[] secret;readonly Func<int,bool,CancellationToken,Task<ITgSocket>> dial;readonly HashSet<TcpClient> clients=new HashSet<TcpClient>();TcpListener listener;CancellationTokenSource stop;Task accept;long up,down,success;string error="";int active,port;bool disposed;
  public TelegramBridge(byte[] key,Func<int,bool,CancellationToken,Task<ITgSocket>> connector=null,Action<string> reporter=null){if(key==null||key.Length!=16)throw new ArgumentException("MTProxy secret must be 16 bytes");secret=(byte[])key.Clone();diagnostic=reporter;dial=connector??((dc,media,token)=>OfficialTgSocket.ConnectDetailed(dc,media,token,Report));}
  public bool Running{get{lock(gate)return listener!=null;}}public int Port{get{return port;}}public int Active{get{return Volatile.Read(ref active);}}public long Up{get{return Interlocked.Read(ref up);}}public long Down{get{return Interlocked.Read(ref down);}}public long Sessions{get{return Interlocked.Read(ref success);}}public string Error{get{lock(gate)return error;}}
  public string Link{get{return "tg://proxy?server=127.0.0.1&port="+port+"&secret=dd"+BitConverter.ToString(secret).Replace("-","").ToLowerInvariant();}}
  public void Start(int requestedPort=1087){lock(gate){if(disposed)throw new ObjectDisposedException("TelegramBridge");if(listener!=null)return;var server=new TcpListener(IPAddress.Loopback,requestedPort);try{server.ExclusiveAddressUse=true;server.Start(32);port=((IPEndPoint)server.LocalEndpoint).Port;stop=new CancellationTokenSource();listener=server;error="";Report("START build=4.8-diagnostic OS="+Environment.OSVersion.VersionString+" CLR="+Environment.Version+" x64="+Environment.Is64BitProcess+" loopback-port="+port);accept=Accept(server,stop.Token);}catch{server.Stop();throw;}}}
  public void StartAvailable(int preferredPort=1087){try{Start(preferredPort);}catch(SocketException ex){if(ex.SocketErrorCode!=SocketError.AddressAlreadyInUse&&ex.SocketErrorCode!=SocketError.AccessDenied)throw;Start(0);}}
  public void StartSaved(string folder){
   string path=Path.Combine(folder,"telegram-port.txt");int saved;
   if(File.Exists(path)){if(!Int32.TryParse(File.ReadAllText(path).Trim(),out saved)||saved<1||saved>65535)throw new InvalidDataException("Некорректный сохранённый Telegram-порт.");Start(saved);return;}
   StartAvailable();string temp=path+"."+Guid.NewGuid().ToString("N")+".tmp";
   try{File.WriteAllText(temp,Port.ToString(System.Globalization.CultureInfo.InvariantCulture));File.Move(temp,path);}catch{Stop();throw;}finally{if(File.Exists(temp))File.Delete(temp);}
  }
  public static string StartError(Exception ex){if(ex is InvalidDataException)return "Повреждены сохранённые настройки Telegram. Проверь журнал и папку данных; не меняй ключ вручную.";if(ex is CryptographicException)return "Не удалось прочитать ключ Telegram этого пользователя Windows. Проверь файл telegram-key.dpapi в папке данных.";if(ex is UnauthorizedAccessException)return "Нет доступа к папке данных: не удалось сохранить ключ Telegram.";if(ex is SocketException)return "Локальный Telegram-порт недоступен. Закрой другие копии Veyra/прокси и повтори запуск. Сохранённый адрес не изменён.";if(ex is IOException)return "Не удалось прочитать или сохранить ключ Telegram в папке данных.";return "Не удалось запустить прокси ("+ex.GetType().Name+"). Перезапусти приложение; подробности — в журнале.";}

  async Task Accept(TcpListener server,CancellationToken token){try{while(!token.IsCancellationRequested){var c=await server.AcceptTcpClientAsync().ConfigureAwait(false);c.NoDelay=true;lock(gate){if(token.IsCancellationRequested||listener!=server||disposed||clients.Count>=64){c.Dispose();continue;}clients.Add(c);}var task=Serve(c,token);}}catch(ObjectDisposedException){}catch(SocketException){if(!token.IsCancellationRequested)SetError("Остановлен локальный слушатель.");}catch(Exception){if(!token.IsCancellationRequested)SetError("Ошибка локального слушателя.");}}
  void Report(string text){try{if(diagnostic!=null)diagnostic(text);}catch{}}
  void SetError(string text){lock(gate)error=text;}
  static string FailureText(short dc,string phase,Exception e){return "Сбой Telegram (DC"+Math.Abs((int)dc)+", "+phase+"). Код 0x"+e.HResult.ToString("X8")+". Подробности: telegram-diagnostic.txt в «Папке данных».";}
  static async Task<byte[]> Exact(Stream s,int count,CancellationToken token){var b=new byte[count];int n=0;while(n<count){int read=await s.ReadAsync(b,n,count-n,token).ConfigureAwait(false);if(read==0){if(n==0)throw new EndOfStreamException();throw new IOException("Truncated Telegram packet.");}n+=read;}return b;}
  async Task Serve(TcpClient client,CancellationToken parent){Interlocked.Increment(ref active);using(var life=CancellationTokenSource.CreateLinkedTokenSource(parent)){ITgSocket remote=null;short dc=0;string phase="локальный клиент";try{using(var stream=client.GetStream()){byte[] hello;using(var deadline=CancellationTokenSource.CreateLinkedTokenSource(life.Token)){deadline.CancelAfter(TimeSpan.FromSeconds(8));using(deadline.Token.Register(()=>{if(deadline.IsCancellationRequested)client.Close();})){hello=await Exact(stream,64,deadline.Token).ConfigureAwait(false);}}
    uint protocol;byte[] sessionKey;lock(gate){if(disposed||parent.IsCancellationRequested)return;sessionKey=(byte[])secret.Clone();}try{if(!TgProtocol.Decode(hello,sessionKey,out dc,out protocol)){Report("CLIENT invalid handshake (no bytes/key recorded)");return;}Report("CLIENT accepted DC="+Math.Abs((int)dc)+" media="+(dc<0)+" protocol=0x"+protocol.ToString("X8"));
    var relay=TgProtocol.Relay(dc,protocol);phase="WebSocket/TLS";remote=await dial(Math.Abs((int)dc),dc<0,life.Token).ConfigureAwait(false);phase="отправка handshake";await remote.Send(relay,life.Token).ConfigureAwait(false);Interlocked.Increment(ref success);SetError("");
    using(var cr=TgProtocol.ClientRead(hello,sessionKey))using(var cw=TgProtocol.ClientWrite(hello,sessionKey))using(var rw=TgProtocol.RemoteWrite(relay))using(var rr=TgProtocol.RemoteRead(relay)){
     phase="передача пакетов";Task a=Upload(stream,remote,cr,rw,protocol,life.Token),b=Download(stream,remote,rr,cw,life.Token);var finished=await Task.WhenAny(a,b).ConfigureAwait(false);phase=finished==a?"клиент → Telegram":"Telegram → клиент";Exception failure=finished.IsFaulted?finished.Exception.GetBaseException():null;life.Cancel();remote.Dispose();remote=null;client.Close();try{await Task.WhenAll(a,b).ConfigureAwait(false);}catch{}if(failure is EndOfStreamException && finished==a){Report("CLIENT EOF DC="+Math.Abs((int)dc)+" clean=true");return;}if(failure!=null)throw new IOException("Передача Telegram прервана.",failure);Report("SESSION ended DC="+Math.Abs((int)dc)+" phase="+phase+" clean=true"); }
   }finally{Array.Clear(sessionKey,0,sessionKey.Length);}}}catch(OperationCanceledException){if(!parent.IsCancellationRequested)Report("SESSION cancelled DC="+Math.Abs((int)dc)+" phase="+phase);}catch(EndOfStreamException){Report("CLIENT EOF DC="+Math.Abs((int)dc)+" phase="+phase);}catch(Exception e){if(!parent.IsCancellationRequested){Report("FAIL DC="+Math.Abs((int)dc)+" phase="+phase+" "+TelegramDiagnostics.Describe(e));SetError(FailureText(dc,phase,e));}}finally{life.Cancel();if(remote!=null)remote.Dispose();client.Dispose();lock(gate)clients.Remove(client);Interlocked.Decrement(ref active);}}}
  static async Task<byte[]> Continuation(Stream input,int size,CancellationToken token){try{return await Exact(input,size,token).ConfigureAwait(false);}catch(EndOfStreamException e){throw new IOException("Truncated Telegram packet.",e);}}
  async Task Upload(Stream input,ITgSocket ws,TgCtr cr,TgCtr rw,uint proto,CancellationToken token){while(true){int header=proto==0xefefefef?1:4;var first=cr.Apply(await Exact(input,header,token).ConfigureAwait(false));int size;
    if(proto==0xefefefef){if(first[0]==0x7f||first[0]==0xff){var tail=cr.Apply(await Continuation(input,3,token).ConfigureAwait(false));first=first.Concat(tail).ToArray();size=(tail[0]|tail[1]<<8|tail[2]<<16)*4;}else size=(first[0]&0x7f)*4;}
    else size=(int)(BitConverter.ToUInt32(first,0)&0x7fffffff);
    if(size<=0||size>TgProtocol.MaxPacket)throw new IOException("Лимит пакета Telegram.");byte[] payloadBytes;try{payloadBytes=await Exact(input,size,token).ConfigureAwait(false);}catch(EndOfStreamException e){throw new IOException("Truncated Telegram payload.",e);}var payload=cr.Apply(payloadBytes);var packet=rw.Apply(first.Concat(payload).ToArray());await ws.Send(packet,token).ConfigureAwait(false);Interlocked.Add(ref up,packet.Length);
   }}
  async Task Download(Stream output,ITgSocket ws,TgCtr rr,TgCtr cw,CancellationToken token){while(true){var data=await ws.Receive(token).ConfigureAwait(false);if(data==null)return;var plain=cw.Apply(rr.Apply(data));await output.WriteAsync(plain,0,plain.Length,token).ConfigureAwait(false);Interlocked.Add(ref down,data.Length);}}
  public void Stop(){lock(gate){if(listener==null)return;Report("STOP");stop.Cancel();listener.Stop();listener=null;foreach(var c in clients.ToArray())c.Close();}}
  public void Dispose(){lock(gate){if(disposed)return;disposed=true;Stop();Array.Clear(secret,0,secret.Length);}}
  public static byte[] LoadKey(string folder){string path=Path.Combine(folder,"telegram-key.dpapi");byte[] entropy=System.Text.Encoding.UTF8.GetBytes("Veyra.Telegram.v1");if(File.Exists(path)){var k=ProtectedData.Unprotect(File.ReadAllBytes(path),entropy,DataProtectionScope.CurrentUser);if(k.Length!=16)throw new InvalidDataException("Ключ Telegram повреждён.");return k;}var key=TgProtocol.Random(16);string temp=path+"."+Guid.NewGuid().ToString("N")+".tmp";try{var data=ProtectedData.Protect(key,entropy,DataProtectionScope.CurrentUser);using(var file=new FileStream(temp,FileMode.CreateNew,FileAccess.Write,FileShare.None)){file.Write(data,0,data.Length);file.Flush(true);}File.Move(temp,path);return key;}finally{if(File.Exists(temp))File.Delete(temp);}}
 }
}
