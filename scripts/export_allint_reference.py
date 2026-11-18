"""Create unbundled 16-bit reference files from the quantized checkpoint for QA."""
import json,pathlib,shutil,subprocess,sys,os
sys.path.insert(0,str(pathlib.Path(__file__).resolve().parent))
from convert_allint import ROOT,SOURCE,ENTRIES,decode,torch
from safetensors.torch import save_file
folder=ROOT/'build/allint-reference-hf';folder.mkdir(exist_ok=True)
out=ROOT/'build/allint-reference';out.mkdir(exist_ok=True)
config=json.loads((SOURCE/'config.json').read_text(encoding='utf-8'));config.pop('quantization_config');config.pop('allint_config')
(folder/'config.json').write_text(json.dumps(config,ensure_ascii=False),encoding='utf-8')
for name in ['generation_config.json','processor_config.json','tokenizer.json','tokenizer_config.json','chat_template.jinja']:
    shutil.copyfile(SOURCE/name,folder/name)
weight=folder/'model.safetensors'
if not weight.exists():
    with torch.inference_mode():
        values={name:decode(spec).contiguous() for name,spec in ENTRIES.items()}
        save_file(values,weight,metadata={'format':'pt'});del values
    print('Decoded ALLINT reference saved',flush=True)
env=os.environ.copy();env['HF_HUB_OFFLINE']='1';env['TRANSFORMERS_OFFLINE']='1'
for name,dtype,args in [('model-bf16.gguf','bf16',['--no-mtp']),('vision-f16.gguf','f16',['--mmproj'])]:
    destination=out/name
    if destination.exists():continue
    temp=out/(name+'.partial')
    subprocess.run([sys.executable,str(ROOT/'vendor/llama.cpp/convert_hf_to_gguf.py'),str(folder),'--outtype',dtype,'--outfile',str(temp),*args],check=True,env=env)
    temp.replace(destination)
print('Reference files are QA-only and will not be included in APK.',flush=True)
