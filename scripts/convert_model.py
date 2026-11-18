"""Export the authorized checkpoint without INT8/INT4 quantization."""
import hashlib,json,os,pathlib,subprocess,sys,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
if len(sys.argv)!=2:raise SystemExit('Usage: python scripts/convert_model.py PATH_TO_ORIGINAL_CHECKPOINT.zip (legacy BF16 workflow)')
SOURCE=pathlib.Path(sys.argv[1]).expanduser().resolve()
MODEL=ROOT/'model-source'
ASSETS=ROOT/'app/src/main/assets/models'
MODEL.mkdir(exist_ok=True);ASSETS.mkdir(parents=True,exist_ok=True)
with zipfile.ZipFile(SOURCE) as z:
    for info in z.infolist():
        if info.filename not in ['config.json','generation_config.json','processor_config.json','tokenizer.json','tokenizer_config.json','chat_template.jinja','model.safetensors']:continue
        target=MODEL/info.filename
        if target.exists() and target.stat().st_size==info.file_size:continue
        print('Extracting '+info.filename,flush=True)
        z.extract(info,MODEL)
env=os.environ.copy();env['HF_HUB_OFFLINE']='1';env['TRANSFORMERS_OFFLINE']='1';env['PYTHONUTF8']='1'
converter=ROOT/'vendor/llama.cpp/convert_hf_to_gguf.py'
for name,kind,extra in [('model-bf16.gguf','bf16',[]),('vision-f16.gguf','f16',['--mmproj'])]:
    dest=ASSETS/name
    if not dest.exists():
        tmp=ASSETS/(name+'.partial')
        subprocess.run([sys.executable,str(converter),str(MODEL),'--outfile',str(tmp),'--outtype',kind,*extra],check=True,env=env)
        tmp.rename(dest)
    print(name+': '+str(dest.stat().st_size),flush=True)
manifest={'source_checkpoint':SOURCE.name,'language_precision':'BF16','vision_precision':'FP16','low_bit_quantization':False,'files':[]}
for name in ['model-bf16.gguf','vision-f16.gguf']:
    p=ASSETS/name
    with p.open('rb') as f:sha=hashlib.file_digest(f,'sha256').hexdigest()
    manifest['files'].append({'name':name,'size':p.stat().st_size,'sha256':sha})
(ASSETS/'manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
print(json.dumps(manifest,ensure_ascii=False,indent=2),flush=True)
