"""Exercise HTTP routing without opening any network listener."""
import io,json,pathlib,types
import phone_update
class Socket:
    def __init__(self,request):self.input=io.BytesIO(request);self.output=bytearray()
    def makefile(self,*args):return self.input
    def sendall(self,data):self.output.extend(data)
def request(path,peer='127.0.0.1',method='GET'):
    s=Socket((method+' '+path+' HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n').encode())
    phone_update.Handler(s,(peer,123),types.SimpleNamespace(token='test',qr=b'png'))
    header,body=bytes(s.output).split(b'\r\n\r\n',1)
    return int(header.split()[1]),body
checks=[]
for path,status in [('/',404),('/test/health',200),('/test/full.apk',404),('/test/../app/src/main/assets/models/model-allint-w4w8.gguf',404),('/test/qr.png',200),('/test/',200)]:
    assert request(path)[0]==status,path;checks.append(path)
assert request('/test/update.apk','8.8.8.8')[0]==403
status,payload=request('/test/update.apk');assert status==200 and payload==phone_update.latest('update').read_bytes()
status,body=request('/test/update.apk',method='HEAD');assert status==200 and not body
result={'checks':len(checks)+3,'network_listener_started':False,'only_weight_free_update_apk':True,'full_model_route_blocked':True,'unauthenticated_path_blocked':True,'download_bytes_match_apk':True}
(phone_update.ROOT/'build/phone-update-validation.json').write_text(json.dumps(result,indent=2),encoding='utf-8');print(json.dumps(result))
