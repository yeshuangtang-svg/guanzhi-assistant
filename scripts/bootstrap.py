import concurrent.futures, hashlib, json, os, pathlib, shutil, urllib.request, zipfile, xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parents[1]
TOOLS = ROOT / '.tools'
TOOLS.mkdir(exist_ok=True)
COMMIT = '45c8ea837489be1304500dd1d298f2a671c985d4'

def download(url, dest):
    dest = pathlib.Path(dest)
    if dest.exists():
        return dest
    print('Downloading ' + url, flush=True)
    with urllib.request.urlopen(url, timeout=120) as response, open(str(dest)+'.part','wb') as out:
        shutil.copyfileobj(response, out, 1024*1024)
    os.replace(str(dest)+'.part', dest)
    print('Downloaded '+dest.name+': '+str(dest.stat().st_size),flush=True)
    return dest

def extract(url, archive_name, marker, target):
    if marker.exists():
        return
    p=download(url, TOOLS/archive_name)
    with zipfile.ZipFile(p) as z: z.extractall('\\\\?\\'+str(pathlib.Path(target).resolve()))

def sdk():
    data=urllib.request.urlopen('https://dl.google.com/android/repository/repository2-3.xml',timeout=120).read()
    (TOOLS/'repository.xml').write_bytes(data)
    root=ET.fromstring(data)
    for pkgid, dest in [('platforms;android-35', 'sdk/platforms/android-35'),('build-tools;35.0.0','sdk/build-tools/35.0.0'),('ndk;28.2.13676358','sdk/ndk/28.2.13676358'),('platform-tools','sdk/platform-tools')]:
        dest=TOOLS/dest
        if dest.exists(): continue
        pkg=next(x for x in root if x.tag.endswith('remotePackage') and x.attrib.get('path')==pkgid)
        archives=pkg.find('archives')
        archive=next(a for a in archives if a.findtext('host-os') in (None,'windows'))
        url=archive.findtext('complete/url')
        checksum=archive.find('complete/checksum')
        p=download('https://dl.google.com/android/repository/'+url,TOOLS/pathlib.Path(url).name)
        if checksum is not None and checksum.text:
            actual=hashlib.file_digest(open(p,'rb'),checksum.attrib.get('type','sha1')).hexdigest()
            if actual != checksum.text: raise ValueError('SDK checksum mismatch: '+str(p))
        dest.mkdir(parents=True,exist_ok=True)
        with zipfile.ZipFile(p) as z:
            for info in z.infolist():
                parts=pathlib.PurePosixPath(info.filename).parts[1:]
                if not parts or info.is_dir():continue
                if '..' in parts:raise ValueError('Unsafe SDK path')
                target=pathlib.Path('\\\\?\\'+str(dest.joinpath(*parts).resolve()))
                target.parent.mkdir(parents=True,exist_ok=True)
                with z.open(info) as src,open(target,'wb') as out:shutil.copyfileobj(src,out)
        print('Installed '+pkgid,flush=True)

def source():
    vendor=ROOT/'vendor';vendor.mkdir(exist_ok=True)
    marker=vendor/'llama.cpp'
    if not marker.exists():
        extract('https://github.com/ggml-org/llama.cpp/archive/'+COMMIT+'.zip','llama-source.zip',marker,vendor)
        (vendor/('llama.cpp-'+COMMIT)).rename(marker)
    (ROOT/'engine-version.json').write_text(json.dumps({'engine':'llama.cpp','commit':COMMIT},indent=2))

def cmake():
    extract('https://github.com/Kitware/CMake/releases/download/v3.31.6/cmake-3.31.6-windows-x86_64.zip','cmake.zip',TOOLS/'cmake-3.31.6-windows-x86_64',TOOLS)
    extract('https://github.com/ninja-build/ninja/releases/download/v1.12.1/ninja-win.zip','ninja.zip',TOOLS/'ninja.exe',TOOLS)

with concurrent.futures.ThreadPoolExecutor(max_workers=3) as pool:
    for f in [pool.submit(sdk),pool.submit(source),pool.submit(cmake)]:f.result()
print('Bootstrap complete',flush=True)
