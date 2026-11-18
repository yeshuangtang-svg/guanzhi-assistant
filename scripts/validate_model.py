import base64,io,json,os,pathlib,socket,subprocess,time,urllib.request
from PIL import Image,ImageDraw
ROOT=pathlib.Path(__file__).resolve().parents[1]
MODEL=ROOT/'app/src/main/assets/models';BUILD=ROOT/'build'
server=ROOT/'.tools/llama-windows/llama-server.exe'
sock=socket.socket();sock.bind(('127.0.0.1',0));port=sock.getsockname()[1];sock.close()
log=open(BUILD/'model-validation-server.log','w',encoding='utf-8')
args=[str(server),'-m',str(MODEL/'model-bf16.gguf'),'--mmproj',str(MODEL/'vision-f16.gguf'),'--host','127.0.0.1','--port',str(port),'-c','4096','-b','256','-ub','128','-t','4','-tb','4','-ngl','0','--parallel','1','--no-warmup','--no-mmproj-offload','--image-max-tokens','768','--image-min-tokens','64','--cache-ram','0','--offline']
p=subprocess.Popen(args,stdout=log,stderr=subprocess.STDOUT,creationflags=subprocess.CREATE_NO_WINDOW)
opener=urllib.request.build_opener(urllib.request.ProxyHandler({}))
base=f'http://127.0.0.1:{port}'
results=[]
try:
    deadline=time.monotonic()+180
    while time.monotonic()<deadline:
        if p.poll() is not None:raise RuntimeError('Validation server exited; see model-validation-server.log')
        try:
            with opener.open(base+'/health',timeout=2) as r:
                if r.status==200:break
        except Exception:pass
        time.sleep(.5)
    else:raise RuntimeError('Model load timed out')
    img=Image.new('RGB',(512,384),'white');draw=ImageDraw.Draw(img);draw.rectangle((45,110,205,270),fill=(225,35,35));draw.ellipse((300,110,460,270),fill=(25,70,220));img.save(BUILD/'vision-check.png')
    raw=io.BytesIO();img.save(raw,format='PNG');data='data:image/png;base64,'+base64.b64encode(raw.getvalue()).decode()
    cases=[('text','请用一句话回答：2加3等于几？',None),('vision_left','图片左边的图形是什么颜色？只回答颜色。',data),('vision_right','图片右边的图形是什么形状？只回答形状。',data)]
    for name,question,image in cases:
        content=question if image is None else [{'type':'image_url','image_url':{'url':image}},{'type':'text','text':question}]
        body={'messages':[{'role':'system','content':'你是一个中文看图助手。请根据图片和问题准确作答，区分图中可见事实与推断；看不清或信息不足时直接说明。回答简洁清晰。'},{'role':'user','content':content}],'max_tokens':96,'temperature':0,'stream':False,'chat_template_kwargs':{'enable_thinking':False}}
        start=time.monotonic();req=urllib.request.Request(base+'/v1/chat/completions',json.dumps(body).encode(),{'Content-Type':'application/json'})
        with opener.open(req,timeout=240) as r:answer=json.load(r)
        result={'case':name,'question':question,'reply':answer['choices'][0]['message'],'usage':answer.get('usage'),'elapsed_seconds':round(time.monotonic()-start,3)}
        results.append(result);print(json.dumps(result,ensure_ascii=False),flush=True)
    (BUILD/'model-validation.json').write_text(json.dumps({'runtime':'llama.cpp b12244 Windows CPU','android_device_tested':False,'results':results},ensure_ascii=False,indent=2),encoding='utf-8')
finally:
    p.terminate()
    try:p.wait(timeout=10)
    except subprocess.TimeoutExpired:p.kill();p.wait()
    log.close()
