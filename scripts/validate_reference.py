import os
os.environ['HF_HUB_OFFLINE']='1';os.environ['TRANSFORMERS_OFFLINE']='1'
import json,pathlib,time,torch
from transformers import AutoProcessor,Qwen3_5ForConditionalGeneration
from PIL import Image
ROOT=pathlib.Path(__file__).resolve().parents[1]
torch.set_num_threads(4)
processor=AutoProcessor.from_pretrained(ROOT/'model-source',local_files_only=True)
model=Qwen3_5ForConditionalGeneration.from_pretrained(ROOT/'model-source',dtype=torch.bfloat16,local_files_only=True,attn_implementation='eager').eval()
image=Image.open(ROOT/'build/vision-check.png')
results=[]
for question in ['图片左边的图形是什么颜色？只回答颜色。','图片右边的图形是什么形状？只回答形状。']:
    messages=[{'role':'system','content':'你是一个中文看图助手。请根据图片和问题准确作答，区分图中可见事实与推断；看不清或信息不足时直接说明。回答简洁清晰。'},{'role':'user','content':[{'type':'image','image':image},{'type':'text','text':question}]}]
    inputs=processor.apply_chat_template(messages,add_generation_prompt=True,tokenize=True,return_dict=True,return_tensors='pt',enable_thinking=False)
    start=time.monotonic()
    with torch.inference_mode():output=model.generate(**inputs,max_new_tokens=32,do_sample=False,use_cache=True)
    answer=processor.batch_decode(output[:,inputs['input_ids'].shape[1]:],skip_special_tokens=True)[0]
    result={'question':question,'reply':answer,'elapsed_seconds':round(time.monotonic()-start,3)};results.append(result);print(json.dumps(result,ensure_ascii=False),flush=True)
(ROOT/'build/reference-validation.json').write_text(json.dumps({'runtime':'Transformers 5.8.0, source BF16, CPU','results':results},ensure_ascii=False,indent=2),encoding='utf-8')
