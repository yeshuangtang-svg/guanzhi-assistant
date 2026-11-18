"""LAN-only, token-scoped APK delivery. No directory browsing or cloud upload."""
import re,argparse,html,http.server,io,ipaddress,json,os,pathlib,secrets,socket,subprocess,threading,time,urllib.request,webbrowser,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
(ROOT/'build').mkdir(parents=True,exist_ok=True)
import sys
sys.path.insert(0,str(ROOT/'.tools/update-deps'))
STATE=ROOT/'build/phone-update-runtime.json'
PRIVATE=[ipaddress.ip_network(x) for x in ('10.0.0.0/8','172.16.0.0/12','192.168.0.0/16','127.0.0.0/8')]
def private(address):
    try:return any(ipaddress.ip_address(address) in n for n in PRIVATE)
    except ValueError:return False
def latest(kind):
    if kind!='update':raise ValueError('Only weight-free update packages can be shared')
    pattern='Qwen-Vision-*-Update-arm64.apk'
    files=list((ROOT/'dist').glob(pattern))+list((ROOT/'dist').glob('Guanzhi-*-Update-arm64.apk'))
    def version(p):return tuple(int(x) for x in re.search(r'(\d+\.\d+\.\d+)',p.name).group(1).split('.'))
    result=max(files,key=version) if files else None
    if result:
        with zipfile.ZipFile(result) as archive:
            if any(n.endswith(('.gguf','.safetensors','.keystore')) for n in archive.namelist()) or json.loads(archive.read('assets/models/manifest.json')).get('weights_bundled',True):
                raise ValueError('Refusing to share a package containing model weights')
    return result
class Handler(http.server.BaseHTTPRequestHandler):
    protocol_version='HTTP/1.1'
    def log_message(self,*args):pass
    def send(self,status,data,mime='text/plain; charset=utf-8',extra=None):
        self.send_response(status);self.send_header('Content-Type',mime);self.send_header('Content-Length',str(len(data)));self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff')
        for key,value in (extra or {}).items():self.send_header(key,value)
        self.end_headers()
        if self.command!='HEAD':self.wfile.write(data)
    def do_HEAD(self):self.do_GET()
    def do_GET(self):
        if not private(self.client_address[0]):return self.send(403,b'LAN only')
        prefix='/'+self.server.token+'/'
        if not self.path.startswith(prefix):return self.send(404,b'Not found')
        path=self.path[len(prefix):]
        if path=='health':return self.send(200,b'QwenVision-update')
        if path=='qr.png':return self.send(200,self.server.qr,'image/png')
        if path=='update.apk':
            file=latest('update')
            if file is None:return self.send(404,b'Package not ready')
            self.send_response(200);self.send_header('Content-Type','application/vnd.android.package-archive');self.send_header('Content-Length',str(file.stat().st_size));self.send_header('Content-Disposition','attachment; filename="'+file.name+'"');self.send_header('Cache-Control','no-store');self.end_headers()
            if self.command=='HEAD':return
            try:
                with file.open('rb') as stream:
                    while chunk:=stream.read(256*1024):self.wfile.write(chunk)
            except (BrokenPipeError,ConnectionResetError):pass
            return
        if path:return self.send(404,b'Not found')
        links=[]
        for kind,title in [('update','日常升级 · 不含千问权重')]:
            p=latest(kind)
            if p:
                digest=(ROOT/'dist'/(p.name+'.sha256')).read_text().split()[0] if (ROOT/'dist'/(p.name+'.sha256')).exists() else '待校验'
                links.append('<section><h2>'+title+'</h2><p>'+html.escape(p.name)+'<br>'+str(round(p.stat().st_size/1e6,2))+' MB</p><a href="'+('update.apk' if kind=='update' else 'full.apk')+'">下载 APK</a><details><summary>SHA-256 校验值</summary><code>'+digest+'</code></details></section>')
        page='''<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>观知助手 · 手机更新</title><style>body{font:16px/1.65 system-ui;background:#f7f5fc;color:#272138;margin:0;padding:24px;max-width:640px;margin:auto}h1{font-size:27px}h2{font-size:18px}section{background:white;border-radius:20px;padding:20px;margin:20px 0;overflow-wrap:anywhere}a{display:block;background:#6952cf;color:white;text-align:center;text-decoration:none;border-radius:12px;padding:12px}img{display:block;width:220px;max-width:80%;margin:20px auto}small,details{font-size:12px;color:#756d86}code{word-break:break-all}summary{margin-top:15px;cursor:pointer}</style><h1>观知助手 · 手机更新</h1><p>电脑与手机连接同一 Wi-Fi，扫码后直接下载。文件从这台电脑传到手机，无需上传网盘。</p><img src="qr.png" alt="手机扫码下载"><section><b>已装过并成功运行模型？选日常升级。</b><p>直接覆盖安装，保留模型、历史和设置。不要先卸载，也不要清除应用数据。首次安装请另取带模型的完整版，并等待模型加载成功；本页面不提供模型包。</p></section>'''+''.join(links)+'''<small>下载后点击 APK，在安卓安装界面确认更新。打不开此页面时，检查两台设备是否同一网络、访客 Wi-Fi 隔离以及 Windows 防火墙提示。此服务只开放本应用安装包，2 小时后自动关闭。</small></html>'''
        self.send(200,page.encode('utf-8'),'text/html; charset=utf-8',{'Content-Security-Policy':"default-src 'none'; img-src 'self'; style-src 'unsafe-inline'; base-uri 'none'; frame-ancestors 'none'"})
    def do_POST(self):
        if self.client_address[0]=='127.0.0.1' and self.path=='/'+self.server.token+'/stop':
            self.send(200,b'Stopping');threading.Thread(target=self.server.shutdown,daemon=True).start()
        else:self.send(403,b'Forbidden')
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--ip');parser.add_argument('--stop',action='store_true');parser.add_argument('--launch',action='store_true');args=parser.parse_args()
    if STATE.exists():
        old=json.loads(STATE.read_text(encoding='utf-8'))
        try:
            url='http://127.0.0.1:'+str(old['port'])+'/'+old['token']+'/'
            if args.stop:urllib.request.urlopen(urllib.request.Request(url+'stop',data=b'',method='POST'),timeout=2);print('Stopped');return
            if urllib.request.urlopen(url+'health',timeout=2).read()==b'QwenVision-update':
                print(old['url'],flush=True)
                if args.launch:webbrowser.open(old['url'])
                return
        except (OSError,ValueError):pass
    if args.stop:return
    addresses=[x[4][0] for x in socket.getaddrinfo(socket.gethostname(),None,socket.AF_INET) if private(x[4][0]) and not x[4][0].startswith('127.')]
    address=args.ip or (addresses[0] if addresses else None)
    if not args.ip and os.name=='nt':
        try:
            raw=subprocess.check_output(['powershell.exe','-NoProfile','-Command','Get-NetIPAddress -AddressFamily IPv4 | Select-Object IPAddress,InterfaceAlias | ConvertTo-Json -Compress'],creationflags=subprocess.CREATE_NO_WINDOW)
            rows=json.loads(raw.decode('utf-8',errors='replace'));rows=rows if isinstance(rows,list) else [rows]
            rows=[r for r in rows if private(r['IPAddress']) and not any(x.lower() in r['InterfaceAlias'].lower() for x in ['VMware','vEthernet','Virtual','Meta','Loopback']) and not r['IPAddress'].startswith('127.')]
            rows.sort(key=lambda r:0 if any(x in r['InterfaceAlias'].lower() for x in ['wlan','wi-fi','wireless']) else 1)
            if rows:address=rows[0]['IPAddress']
        except (OSError,ValueError):pass
    if not address or not private(address):raise SystemExit('No private LAN IPv4; pass --ip with the computer Wi-Fi IPv4 address')
    if latest('update') is None:raise SystemExit('Build an Update APK first')
    if args.launch:
        with (ROOT/'build/phone-update.log').open('wb') as log,(ROOT/'build/phone-update-error.log').open('wb') as err:
            subprocess.Popen([sys.executable,str(pathlib.Path(__file__).resolve()),'--ip',address],cwd=ROOT,stdout=log,stderr=err,creationflags=subprocess.CREATE_NO_WINDOW if os.name=='nt' else 0)
        for _ in range(40):
            time.sleep(.2)
            try:
                state=json.loads(STATE.read_text(encoding='utf-8'));health='http://127.0.0.1:'+str(state['port'])+'/'+state['token']+'/health'
                if urllib.request.urlopen(health,timeout=1).read()==b'QwenVision-update':webbrowser.open(state['url']);print(state['url']);return
            except (OSError,ValueError):pass
        raise SystemExit('Server did not start; check build/phone-update-error.log')
    server=http.server.ThreadingHTTPServer(('0.0.0.0',0),Handler);server.daemon_threads=True;server.token=secrets.token_urlsafe(24)
    url='http://'+address+':'+str(server.server_port)+'/'+server.token+'/'
    import qrcode
    buffer=io.BytesIO();qrcode.make(url).save(buffer,format='PNG');server.qr=buffer.getvalue()
    (ROOT/'build/phone-update-qr.png').write_bytes(server.qr)
    info={'pid':os.getpid(),'port':server.server_port,'token':server.token,'url':url,'ip':address,'expires_at':time.time()+7200}
    STATE.write_text(json.dumps(info,indent=2),encoding='utf-8');print(url,flush=True)
    timer=threading.Timer(7200,server.shutdown);timer.daemon=True;timer.start()
    try:server.serve_forever()
    finally:server.server_close()
if __name__=='__main__':main()
