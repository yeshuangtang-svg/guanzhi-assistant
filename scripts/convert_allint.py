"""Repack the user's ALLINT G64 integer codes into native GGUF Q4_0 / Q8_0.

No new calibration or integer rounding: split each G64 group into two G32 blocks.
GGUF block scales are FP16; report this adaptation and its error explicitly.
Small parameters use the archive's documented floating-point fallback.
"""
import hashlib,json,logging,math,os,pathlib,sys,types
ROOT=pathlib.Path(__file__).resolve().parents[1]
sys.path.insert(0,str(ROOT/'vendor/llama.cpp'))
sys.path.insert(0,str(ROOT/'vendor/llama.cpp/gguf-py'))
os.environ['HF_HUB_OFFLINE']='1';os.environ['TRANSFORMERS_OFFLINE']='1'
import numpy as np
import torch
from safetensors import safe_open
from conversion import get_model_class
from conversion.base import gguf,ModelBase

SOURCE=ROOT/'allint-source';OUT=ROOT/'app/src/main/assets/models'
MANIFEST=json.loads((SOURCE/'allint_manifest.json').read_text(encoding='utf-8'))
ENTRIES=MANIFEST['parameters']
HANDLE=safe_open(SOURCE/'model.safetensors',framework='pt')
REPORT=[]
torch.set_num_threads(4)
class FloatingFallback(Exception):pass

def decode(spec):
    q,s=(HANDLE.get_tensor(k) for k in spec['keys'][:2])
    if spec['format']=='pack-quantized':
        rows,cols=spec['shape'];bits=spec['bits'];step=32//bits
        result=torch.empty((rows,cols),dtype=getattr(torch,spec['source_dtype']))
        shifts=torch.arange(step,dtype=torch.int32)*bits
        for start in range(0,rows,512):
            raw=(((q[start:start+512,:,None]>>shifts)&((1<<bits)-1))-(1<<(bits-1)))
            val=(raw.float().reshape(-1,cols//64,64)*s[start:start+512].float()[:,:,None]).reshape(-1,cols)
            result[start:start+512]=val.to(result.dtype)
    else:
        assert spec['format']=='allint-int8-g64' and spec['bits']==8 and spec['group_size']==64
        result=(q.float()*s.float()[:,None]).flatten()[:math.prod(spec['shape'])].reshape(spec['shape']).to(getattr(torch,spec['source_dtype']))
    assert torch.isfinite(result).all()
    return result

def repack(spec):
    assert spec['group_size']==64 and spec['symmetric'] and len(spec['shape'])==2
    rows,cols=spec['shape'];bits=spec['bits'];assert bits in (4,8) and cols%64==0
    packed=HANDLE.get_tensor(spec['keys'][0]).numpy().view(np.uint32)
    scales=HANDLE.get_tensor(spec['keys'][1]).float().numpy()
    assert scales.shape==(rows,cols//64) and np.isfinite(scales).all()
    scale16=scales.astype(np.float16)
    if not np.isfinite(scale16).all() or np.any((scales!=0)&(scale16==0)):
        raise FloatingFallback('FP16 block scale cannot represent all original scales; retain BF16 matrix')
    block_bytes=18 if bits==4 else 34
    output=np.empty((rows,cols//32,block_bytes),dtype=np.uint8)
    shifts=np.arange(32//bits,dtype=np.uint32)*bits
    max_abs=0.;sqerr=0.;sqref=0.;exact=0;count=0
    for start in range(0,rows,512):
        n=min(512,rows-start)
        codes=(((packed[start:start+n,:,None]>>shifts)&((1<<bits)-1)).astype(np.int16)-(1<<(bits-1))).astype(np.int8).reshape(n,cols)
        blocks=codes.reshape(n,cols//32,32)
        dst=output[start:start+n]
        d=np.repeat(scale16[start:start+n],2,axis=1)
        dst[:,:,:2]=np.ascontiguousarray(d[:,:,None]).view(np.uint8)
        if bits==8:
            dst[:,:,2:]=blocks.view(np.uint8)
            restored=dst[:,:,2:].view(np.int8)
        else:
            unsigned=(blocks.astype(np.int16)+8).astype(np.uint8)
            dst[:,:,2:]=unsigned[:,:,:16]|(unsigned[:,:,16:]<<4)
            restored=(np.concatenate([dst[:,:,2:]&15,dst[:,:,2:]>>4],axis=2).astype(np.int16)-8).astype(np.int8)
        assert np.array_equal(restored,blocks), 'Integer codes changed during repack'
        # Compare against the archive's BF16/FP32 compatibility loader, not original pre-quantization weights.
        original=(codes.astype(np.float32).reshape(n,cols//64,64)*scales[start:start+n,:,None]).reshape(n,cols)
        reference=torch.from_numpy(original).to(getattr(torch,spec['source_dtype'])).float().numpy()
        adapted=(restored.astype(np.float32)*d.astype(np.float32)[:,:,None]).reshape(n,cols)
        error=adapted-reference
        max_abs=max(max_abs,float(np.abs(error).max()));sqerr+=float(np.square(error,dtype=np.float64).sum());sqref+=float(np.square(reference,dtype=np.float64).sum())
        exact+=int(np.count_nonzero(adapted==reference));count+=reference.size
    relative_scale=np.abs(scale16.astype(np.float32)-scales)/np.maximum(np.abs(scales),1e-30)
    audit={'bits':bits,'integer_codes_preserved':True,'scale_relative_error_max':float(relative_scale.max()),
           'relative_l2_vs_compatibility':math.sqrt(sqerr/max(sqref,1e-30)),'max_abs_vs_compatibility':max_abs,'exact_values_fraction':exact/count}
    return output.reshape(rows,-1),audit

def convert(mmproj=False):
    base=get_model_class('Qwen3_5ForConditionalGeneration',mmproj=mmproj)
    class AllIntModel(base):
        model_arch=base.model_arch
        no_mtp=True
        def index_tensors(self,remote_hf_model_id=None):
            type(self)._original_block_count=self.hparams.get('text_config',self.hparams).get('num_hidden_layers',24)
            result={};self.source_specs={}
            for original,spec in ENTRIES.items():
                item=self.filter_tensors((original,lambda spec=spec:decode(spec)))
                self._source_tensor_names[original]=item[0] if item else None
                if item:
                    name,loader=item;result[name]=loader;self.source_specs[name]=(original,spec)
            return result
        def prepare_tensors(self):
            def floating(original,name,spec,bid,reason=None):
                value=decode(spec).float()
                for target,value in self.modify_tensors(value,name,bid):
                    force=self.tensor_force_quant(name,target,bid,value.ndim)
                    kind=gguf.GGMLQuantizationType.F32 if value.ndim<=1 or self._tensor_requires_f32(target,bid,value.ndim) else (gguf.GGMLQuantizationType.F16 if mmproj else gguf.GGMLQuantizationType.BF16)
                    if not isinstance(force,bool):kind=force
                    data=gguf.quants.quantize(value.numpy(),kind)
                    self.gguf_writer.add_tensor(target,data,raw_dtype=kind)
                    REPORT.append({'source':original,'target':target,'storage':kind.name,'fallback_from_allint':True,'matrix_fallback':reason})
            for name,(original,spec) in self.source_specs.items():
                bid=next((int(p) for p in name.split('.') if p.isdecimal()),None)
                if spec['format']=='pack-quantized':
                    # The pinned Qwen3.5 (equal K/V heads) and vision converters only rename these matrices.
                    # Reject any unexpected layout/numeric transform rather than silently assigning wrong scales.
                    probe=torch.empty(spec['shape'],device='meta',dtype=torch.float32)
                    mapped=list(self.modify_tensors(probe,name,bid))
                    assert len(mapped)==1 and mapped[0][1] is probe, f'Unsupported matrix transformation: {name}'
                    target=mapped[0][0]
                    assert not self._tensor_requires_f32(target,bid,2), f'Requires floating fallback: {name}'
                    try:data,audit=repack(spec)
                    except FloatingFallback as error:
                        floating(original,name,spec,bid,str(error));print('FLOATING_FALLBACK',original,flush=True);continue
                    kind=gguf.GGMLQuantizationType.Q4_0 if spec['bits']==4 else gguf.GGMLQuantizationType.Q8_0
                    self.gguf_writer.add_tensor(target,data,raw_dtype=kind)
                    REPORT.append({'source':original,'target':target,'storage':kind.name,**audit})
                    del data
                else:
                    floating(original,name,spec,bid)
                print(('vision' if mmproj else 'language'),original,flush=True)
    config=json.loads((SOURCE/'config.json').read_text(encoding='utf-8'));config.pop('quantization_config');config.pop('allint_config')
    name='vision-allint-q8.gguf' if mmproj else 'model-allint-w4w8.gguf'
    dest=OUT/name;temp=OUT/(name+'.partial')
    model=AllIntModel(SOURCE,gguf.LlamaFileType.MOSTLY_Q8_0,temp,hparams=config,eager=True,use_temp_file=True,model_name='Qwen3.5-0.8B-SFT2273-22.6-ALLINT-Mobile')
    model.write();temp.replace(dest)
    return dest

def main():
    logging.basicConfig(level=logging.INFO)
    checks=json.loads((SOURCE/'files_sha256.json').read_text(encoding='utf-8'))
    for name,expected in checks.items():
        with (SOURCE/name).open('rb') as f:assert hashlib.file_digest(f,'sha256').hexdigest()==expected,name
    files=[convert(False),convert(True)]
    native=[x for x in REPORT if x.get('integer_codes_preserved')]
    matrix_fallback=[x for x in REPORT if x.get('matrix_fallback')]
    assert len(native)+len(matrix_fallback)==238 and sum(x['bits']==4 for x in native)==96
    source=json.loads((ROOT/'build/allint-source-archive.json').read_text(encoding='utf-8'))
    report={'source_archive':source,'format':'GGUF Q4_0/Q8_0 mobile adaptation of ALLINT W4/W8 G64',
        'native_matrices':len(native),'matrix_fallbacks':matrix_fallback,'integer_codes_preserved_in_native_matrices':True,'group_mapping':'G64 split into two G32 blocks with the same scale',
        'scale_dtype':'FP16 (GGUF requirement)','not_bit_exact_to_bf16_compatibility':True,
        'scale_relative_error_max':max(x['scale_relative_error_max'] for x in native),
        'relative_l2_vs_compatibility_max':max(x['relative_l2_vs_compatibility'] for x in native),'tensors':REPORT,'files':[]}
    for p in files:
        with p.open('rb') as f:sha=hashlib.file_digest(f,'sha256').hexdigest()
        report['files'].append({'name':p.name,'size':p.stat().st_size,'sha256':sha})
    (ROOT/'build/allint-conversion-audit.json').write_text(json.dumps(report,ensure_ascii=False,indent=2),encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k!='tensors'},ensure_ascii=False,indent=2))
if __name__=='__main__':
    with torch.inference_mode():main()
