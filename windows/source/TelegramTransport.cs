// Independent bounded RFC6455 transport. TLS is authenticated against the official
// Telegram hostname even when an alternate official Telegram IP is used.
using System;
using System.IO;
using System.Linq;
using System.Net.Security;
using System.Net.Sockets;
using System.Security.Authentication;
using System.Security.Cryptography;
using System.Text;
using System.Threading;
using System.Threading.Tasks;
namespace ZapretDesk {
 public sealed class TelegramTransport:ITgSocket {
  readonly TcpClient tcp;readonly Stream stream;readonly SemaphoreSlim writer=new SemaphoreSlim(1,1);readonly CancellationTokenSource life=new CancellationTokenSource();Task heartbeat;
  TelegramTransport(TcpClient t,Stream s){tcp=t;stream=s;heartbeat=Task.Run(async()=>{try{while(!life.IsCancellationRequested){await Task.Delay(25000,life.Token);await Frame(9,new byte[0],life.Token);}}catch{Dispose();}});}
  public static async Task<ITgSocket> Connect(string host,string address,CancellationToken cancel,Action<string> trace=null){var tcp=new TcpClient();try{
   using(cancel.Register(()=>tcp.Close())){if(trace!=null)trace("tcp-connect");await tcp.ConnectAsync(address,443).ConfigureAwait(false);tcp.NoDelay=true;if(trace!=null)trace("tcp-ok; tls-start");var tls=new SslStream(tcp.GetStream(),false);await tls.AuthenticateAsClientAsync(host,null,SslProtocols.Tls12,true).ConfigureAwait(false);
    if(trace!=null)trace("tls-verified; websocket-upgrade-start");string key=Convert.ToBase64String(TgProtocol.Random(16));string request="GET /apiws HTTP/1.1\r\nHost: "+host+"\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: "+key+"\r\nSec-WebSocket-Version: 13\r\nSec-WebSocket-Protocol: binary\r\n\r\n";var bytes=Encoding.ASCII.GetBytes(request);await tls.WriteAsync(bytes,0,bytes.Length,cancel).ConfigureAwait(false);
    var header=new MemoryStream();while(header.Length<16384){var b=await Read(tls,1,cancel);header.WriteByte(b[0]);if(header.Length>=4){var x=header.GetBuffer();int n=(int)header.Length;if(x[n-4]==13&&x[n-3]==10&&x[n-2]==13&&x[n-1]==10)break;}}
    string text=Encoding.ASCII.GetString(header.ToArray());if(!text.EndsWith("\r\n\r\n"))throw new IOException("WebSocket header limit");var lines=text.Split(new[]{"\r\n"},StringSplitOptions.None);var fields=lines.Skip(1).Where(l=>l.Contains(":")).Select(l=>l.Split(new[]{':'},2)).ToDictionary(a=>a[0].Trim(),a=>a[1].Trim(),StringComparer.OrdinalIgnoreCase);
    string accept;using(var sha=SHA1.Create())accept=Convert.ToBase64String(sha.ComputeHash(Encoding.ASCII.GetBytes(key+"258EAFA5-E914-47DA-95CA-C5AB0DC85B11")));
    if(!lines[0].StartsWith("HTTP/1.1 101 ")||!fields.ContainsKey("Sec-WebSocket-Accept")||fields["Sec-WebSocket-Accept"]!=accept||!fields.ContainsKey("Upgrade")||!fields["Upgrade"].Equals("websocket",StringComparison.OrdinalIgnoreCase)||!fields.ContainsKey("Connection")||!fields["Connection"].Split(',').Any(x=>x.Trim().Equals("Upgrade",StringComparison.OrdinalIgnoreCase))||!fields.ContainsKey("Sec-WebSocket-Protocol")||fields["Sec-WebSocket-Protocol"]!="binary")throw new IOException("WebSocket upgrade rejected: "+lines[0]);
    cancel.ThrowIfCancellationRequested();if(trace!=null)trace("websocket-upgrade-verified");return new TelegramTransport(tcp,tls);
   }
  }catch{tcp.Close();cancel.ThrowIfCancellationRequested();throw;}}
  static async Task<byte[]> Read(Stream s,int n,CancellationToken t){var b=new byte[n];int p=0;while(p<n){int k=await s.ReadAsync(b,p,n-p,t).ConfigureAwait(false);if(k==0)throw new EndOfStreamException();p+=k;}return b;}
  async Task Frame(int opcode,byte[] b,CancellationToken token){if(b.Length>TgProtocol.MaxPacket)throw new IOException("WS size limit");await writer.WaitAsync(token).ConfigureAwait(false);try{var packet=new MemoryStream();packet.WriteByte((byte)(0x80|opcode));if(b.Length<126)packet.WriteByte((byte)(0x80|b.Length));else if(b.Length<65536){packet.WriteByte(0xfe);packet.WriteByte((byte)(b.Length>>8));packet.WriteByte((byte)b.Length);}else{packet.WriteByte(0xff);for(int n=7;n>=0;n--)packet.WriteByte((byte)((ulong)b.Length>>(8*n)));}var mask=TgProtocol.Random(4);packet.Write(mask,0,4);var masked=new byte[b.Length];for(int n=0;n<b.Length;n++)masked[n]=(byte)(b[n]^mask[n%4]);packet.Write(masked,0,masked.Length);var output=packet.ToArray();await stream.WriteAsync(output,0,output.Length,token).ConfigureAwait(false);}finally{writer.Release();}}
  public Task Send(byte[] data,CancellationToken token){return Frame(2,data,token);}
  public async Task<byte[]> Receive(CancellationToken token){using(token.Register(()=>tcp.Close()))using(var message=new MemoryStream()){bool started=false;while(true){var h=await Read(stream,2,token).ConfigureAwait(false);int op=h[0]&15;bool final=(h[0]&128)!=0;if((h[0]&112)!=0||(h[1]&128)!=0)throw new IOException("WS flags");ulong len=(ulong)(h[1]&127);if(len==126){var e=await Read(stream,2,token);len=(ulong)(e[0]*256+e[1]);}else if(len==127){var e=await Read(stream,8,token);len=0;foreach(byte c in e)len=(len<<8)|c;}
   if(len>(ulong)TgProtocol.MaxPacket||op>=8&&(!final||len>125))throw new IOException("WS frame limit");var data=await Read(stream,(int)len,token);if(op==8){if(data.Length==1)throw new IOException("WS close length");if(data.Length>=2){int code=data[0]*256+data[1];if(code!=1000)throw new TelegramCloseException(code);}return null;}if(op==9){await Frame(10,data,token);continue;}if(op==10)continue;
   if(op==2&&!started)started=true;else if(op!=0||!started)throw new IOException("WS message order");if(message.Length+data.Length>TgProtocol.MaxPacket)throw new IOException("WS message limit");message.Write(data,0,data.Length);if(final)return message.ToArray();
  }}}
  int disposed;public void Dispose(){if(Interlocked.Exchange(ref disposed,1)!=0)return;life.Cancel();tcp.Close();stream.Dispose();}
 }
 // CDN203 has its own MTProto RSA/auth-key domain, not the DC2 WSS server.
 public sealed class TelegramTcpTransport:ITgSocket {
  public const string Cdn203="91.105.192.100";
  readonly TcpClient client;readonly NetworkStream stream;
  TelegramTcpTransport(TcpClient c){client=c;stream=c.GetStream();}
  public static Task<ITgSocket> Connect203(CancellationToken token){return Connect(Cdn203,443,token);}
  internal static async Task<ITgSocket> Connect(string address,int port,CancellationToken token){var c=new TcpClient();try{using(var deadline=CancellationTokenSource.CreateLinkedTokenSource(token)){deadline.CancelAfter(TimeSpan.FromSeconds(6));using(deadline.Token.Register(()=>c.Close())){await c.ConnectAsync(address,port).ConfigureAwait(false);deadline.Token.ThrowIfCancellationRequested();c.NoDelay=true;c.Client.SetSocketOption(SocketOptionLevel.Socket,SocketOptionName.KeepAlive,true);return new TelegramTcpTransport(c);}}}catch{c.Close();token.ThrowIfCancellationRequested();throw;}}
  public Task Send(byte[] data,CancellationToken token){return stream.WriteAsync(data,0,data.Length,token);}
  public async Task<byte[]> Receive(CancellationToken token){var b=new byte[65536];using(token.Register(()=>client.Close())){int n=await stream.ReadAsync(b,0,b.Length,token).ConfigureAwait(false);if(n==0)return null;Array.Resize(ref b,n);return b;}}
  public void Dispose(){client.Close();}
 }
 // Race only connection establishment, never replay MTProto/user packets.
 public static class TelegramDialRace {
  public static async Task<ITgSocket> First(Func<CancellationToken,Task<ITgSocket>>[] attempts,CancellationToken parent){
   parent.ThrowIfCancellationRequested();Exception last=null;
   using(var stop=CancellationTokenSource.CreateLinkedTokenSource(parent)){
    var tasks=attempts.Select(async attempt=>{try{return await attempt(stop.Token).ConfigureAwait(false);}catch(Exception e){Interlocked.Exchange(ref last,e);return null;}}).ToList();
    try{while(tasks.Count>0){var done=await Task.WhenAny(tasks).ConfigureAwait(false);tasks.Remove(done);var result=await done.ConfigureAwait(false);if(result!=null){if(parent.IsCancellationRequested){result.Dispose();parent.ThrowIfCancellationRequested();}return result;}parent.ThrowIfCancellationRequested();}throw new IOException("No WSS route connected.",last);}
    finally{stop.Cancel();foreach(var pending in tasks){var cleanup=pending.ContinueWith(t=>{if(t.Status==TaskStatus.RanToCompletion&&t.Result!=null)try{t.Result.Dispose();}catch{}},CancellationToken.None,TaskContinuationOptions.ExecuteSynchronously,TaskScheduler.Default);}}
   }
  }
 }
 public static class TelegramRoutes {
  // Production DC identity is preserved. No foreign relays or random IP scanning.
  // 149.154.167.220 is an alternate Telegram WSS edge, not a replacement DC id.
  // Prefer the official edge/main hostname verified reachable in the user's probe.
  // media stays a signed DC in the MTProto relay; it is NOT a different DC/key.
  public static Tuple<string,string>[] Candidates(int dc,bool media,Tuple<string,string> cached=null){
   var normal=Hosts(dc,media).SelectMany(h=>Addresses(dc,h).Select(a=>Tuple.Create(h,a)));
   var preferred=(dc==2||dc==4)?new[]{Tuple.Create("kws"+dc+".web.telegram.org","149.154.167.220")}:new Tuple<string,string>[0];
   if(preferred.Length>0)preferred=preferred.Concat(new[]{Tuple.Create("kws"+dc+".web.telegram.org","kws"+dc+".web.telegram.org")}).ToArray();
   return preferred.Concat(cached==null?new Tuple<string,string>[0]:new[]{cached}).Concat(normal).Distinct().ToArray();
  }
  public static string[] Addresses(int dc,string host){return dc==2||dc==4?new[]{host,"149.154.167.220"}:new[]{host};}
  public static string[] Hosts(int dc,bool media){if(dc<1||dc>5)throw new ArgumentException("Production Telegram DC must be 1..5");string alias=new[]{"","pluto","venus","aurora","vesta","flora"}[dc];return media?new[]{"kws"+dc+"-1.web.telegram.org","kws"+dc+".web.telegram.org",alias+".web.telegram.org"}:new[]{"kws"+dc+".web.telegram.org","kws"+dc+"-1.web.telegram.org",alias+".web.telegram.org"};}
 }
}
