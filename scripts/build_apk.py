"""Compatibility entry: build the latest CPU-only app."""
import pathlib,subprocess,sys
r=pathlib.Path(__file__).resolve().parents[1]
subprocess.run([sys.executable,str(r/'scripts/build_native.py')],check=True)
subprocess.run([sys.executable,str(r/'scripts/build_tools_apk.py')]+([] if '--update' in sys.argv else ['--full']),check=True)
