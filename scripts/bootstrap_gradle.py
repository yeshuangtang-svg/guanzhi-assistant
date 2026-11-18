"""Install the pinned official Gradle distribution, verifying its published digest."""
import hashlib,pathlib,urllib.request,zipfile,shutil,os
root=pathlib.Path(__file__).resolve().parents[1];tools=root/'.tools';version='8.9'
def prepare():
    if (tools/f'gradle-{version}/bin/gradle.bat').exists():return
    tools.mkdir(exist_ok=True);url=f'https://services.gradle.org/distributions/gradle-{version}-bin.zip'
    expected=urllib.request.urlopen(url+'.sha256',timeout=60).read().decode().strip().split()[0]
    archive=tools/f'gradle-{version}-bin.zip'
    if not archive.exists():
        part=archive.with_suffix('.part')
        with urllib.request.urlopen(url,timeout=120) as response,part.open('wb') as out:shutil.copyfileobj(response,out,1024*1024)
        os.replace(part,archive)
    with archive.open('rb') as f:actual=hashlib.file_digest(f,'sha256').hexdigest()
    if actual!=expected:raise ValueError('Gradle distribution SHA256 mismatch')
    with zipfile.ZipFile(archive) as z:
        for name in z.namelist():
            dest=(tools/name).resolve()
            if not dest.is_relative_to(tools.resolve()):raise ValueError('Unsafe archive path')
        z.extractall(tools)
if __name__=='__main__':prepare()
