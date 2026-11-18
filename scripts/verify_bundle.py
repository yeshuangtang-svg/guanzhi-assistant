import hashlib,json,pathlib,sys,zipfile
ROOT=pathlib.Path(__file__).resolve().parents[1]
APK=pathlib.Path(sys.argv[1]) if len(sys.argv)>1 else ROOT/'dist/Guanzhi-1.5.2-Lite-W4W8-arm64.apk'
with zipfile.ZipFile(APK) as z:
    manifest=json.loads(z.read('assets/models/manifest.json'))
    bundled=manifest.get('weights_bundled',True)
    assert {n for n in z.namelist() if n.endswith('.gguf')}==({'assets/models/'+f['name'] for f in manifest['files']} if bundled else set()),'Unexpected bundled weights'
    results=[]
    for entry in manifest['files'] if bundled else []:
        path='assets/models/'+entry['name'];info=z.getinfo(path)
        with z.open(path) as f:sha=hashlib.file_digest(f,'sha256').hexdigest()
        assert sha==entry['sha256'] and info.file_size==entry['size'],path
        assert info.compress_type==zipfile.ZIP_STORED
        results.append({'file':path,'bytes':info.file_size,'sha256':sha,'verified':True})
    assert z.getinfo('classes.dex').file_size>0
    assert z.getinfo('lib/arm64-v8a/libqwenpocket.so').file_size>0
result={'apk':APK.name,'embedded_models':results,'weights_bundled':bundled,'required_models':manifest['files'],'contains_dex_and_arm64_library':True,'device_tested':False}
(ROOT/'build'/(APK.stem+'-verification.json')).write_text(json.dumps(result,indent=2),encoding='utf-8')
print(json.dumps(result,indent=2))
