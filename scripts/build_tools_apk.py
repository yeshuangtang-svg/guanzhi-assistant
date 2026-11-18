"""Gradle packages the local model app and public-web reader; signing preserves existing app identity."""
import pathlib,os,json,shutil,subprocess,sys,hashlib,zipfile
from bootstrap_gradle import prepare
ROOT=pathlib.Path(__file__).resolve().parents[1];BUILD=ROOT/'build/gradle-input';TOOLS=ROOT/'.tools'
java_home=os.environ.get('QWEN_JAVA_HOME') or os.environ.get('JAVA_HOME')
if not java_home:raise RuntimeError('Set QWEN_JAVA_HOME or JAVA_HOME to a JDK 21 installation')
JAVA=pathlib.Path(java_home)
keystore=pathlib.Path(os.environ.get('QWEN_KEYSTORE',str(TOOLS/'qwenpocket-release.keystore'))).expanduser().resolve()
alias=os.environ.get('QWEN_KEY_ALIAS','qwenpocket')
if not keystore.is_file():raise RuntimeError('Create your signing key or set QWEN_KEYSTORE; see README.md')
if not os.environ.get('QWEN_STORE_PASSWORD'):raise RuntimeError('Set QWEN_STORE_PASSWORD; never commit signing credentials')
sign_env=os.environ.copy()
sign_env.setdefault('QWEN_KEY_PASSWORD',sign_env['QWEN_STORE_PASSWORD'])
DIST=pathlib.Path(os.environ.get('QWEN_OUTPUT_DIR',str(ROOT/'dist'))).expanduser().resolve()
DIST.mkdir(parents=True,exist_ok=True)
prepare()
native=ROOT/'build/native-cpu/libqwenpocket.so'
sources=[ROOT/'app/src/main/cpp/native.cpp',ROOT/'app/src/main/cpp/CMakeLists.txt',ROOT/'engine-version.json']+list((ROOT/'app/src/main/cpp').glob('*.h'))
if not native.exists() or any(p.stat().st_mtime>native.stat().st_mtime for p in sources):
    raise RuntimeError('Rebuild the native engine before packaging; see README.md')
full='--full' in sys.argv;assets=BUILD/'assets';assets.mkdir(parents=True,exist_ok=True)
source=ROOT/'app/src/main/assets'
legacy_notice=assets/'licenses/MLKit-NOTICE.txt'
if legacy_notice.exists():
    assert legacy_notice.resolve().is_relative_to(BUILD.resolve());legacy_notice.unlink()
for p in source.rglob('*'):
    if not p.is_file() or p.suffix=='.gguf' or p.name=='MLKit-NOTICE.txt':continue
    dest=assets/p.relative_to(source);dest.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(p,dest)
manifest=json.loads((source/'models/manifest.json').read_text(encoding='utf-8'))
manifest['models']=[m for m in manifest['models'] if m['id']=='allint22'];wanted={manifest['models'][0][k] for k in ('language_file','vision_file')}
manifest['files']=[f for f in manifest['files'] if f['name'] in wanted];manifest['weights_bundled']=full
for name in wanted:
    target=assets/'models'/name
    if full:shutil.copyfile(source/'models'/name,target)
    elif target.exists():
        assert target.resolve().parent==(BUILD/'assets/models').resolve();target.unlink()
(assets/'models/manifest.json').write_text(json.dumps(manifest,ensure_ascii=False,indent=2),encoding='utf-8')
variant=BUILD/'java/cn/shiguang/qwenpocket/BuildVariant.java';variant.parent.mkdir(parents=True,exist_ok=True)
variant.write_text('package cn.shiguang.qwenpocket; final class BuildVariant { static final boolean LITE=true; static final String VERSION="1.5.2"; }',encoding='utf-8')
android=(ROOT/'app/src/main/AndroidManifest.xml').read_text(encoding='utf-8').replace(' package="cn.shiguang.qwenpocket"','')
(BUILD/'AndroidManifest.xml').write_text(android,encoding='utf-8')
lib=BUILD/'jniLibs/arm64-v8a/libqwenpocket.so';lib.parent.mkdir(parents=True,exist_ok=True);shutil.copyfile(ROOT/'build/native-cpu/libqwenpocket.so',lib)
# Keep full symbols in build/native-cpu for crash analysis, strip only the staged APK copy.
strip=TOOLS/'sdk/ndk/28.2.13676358/toolchains/llvm/prebuilt/windows-x86_64/bin/llvm-strip.exe'
subprocess.run([str(strip),'--strip-unneeded',str(lib)],check=True)
env=os.environ.copy();env['JAVA_HOME']=str(JAVA);env['ANDROID_HOME']=str(TOOLS/'sdk');env['GRADLE_USER_HOME']=str(TOOLS/'gradle-cache');env['PATH']=str(JAVA/'bin')+os.pathsep+env.get('PATH','')
subprocess.run([str(TOOLS/'gradle-8.9/bin/gradle.bat'),'--no-daemon',':app:assembleRelease'],cwd=ROOT,env=env,check=True)
bt=TOOLS/'sdk/build-tools/35.0.0';unsigned=ROOT/'app/build/outputs/apk/release/app-release-unsigned.apk';aligned=ROOT/'build/tools-aligned.apk'
subprocess.run([str(bt/'zipalign.exe'),'-f','-P','16','4',os.path.relpath(unsigned,ROOT),os.path.relpath(aligned,ROOT)],cwd=ROOT,check=True)
apk=DIST/('Guanzhi-1.5.2-Lite-W4W8-arm64.apk' if full else 'Guanzhi-1.5.2-Update-arm64.apk')
subprocess.run([str(JAVA/'bin/java.exe'),'-Xmx2g','-jar',str(bt/'lib/apksigner.jar'),'sign','--ks',str(keystore),'--ks-pass','env:QWEN_STORE_PASSWORD','--key-pass','env:QWEN_KEY_PASSWORD','--ks-key-alias',alias,'--v1-signing-enabled','false','--v2-signing-enabled','true','--v3-signing-enabled','true','--v4-signing-enabled','false','--out',str(apk),str(aligned)],check=True,env=sign_env)
subprocess.run([str(JAVA/'bin/java.exe'),'-Xmx2g','-jar',str(bt/'lib/apksigner.jar'),'verify','--verbose',str(apk)],check=True)
with apk.open('rb') as f:sha=hashlib.file_digest(f,'sha256').hexdigest()
apk.with_suffix(apk.suffix+'.sha256').write_text(sha+'  '+apk.name+'\n',encoding='ascii')
print(json.dumps({'apk':str(apk),'bytes':apk.stat().st_size,'sha256':sha},ensure_ascii=False))
