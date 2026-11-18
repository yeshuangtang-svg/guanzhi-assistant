"""Build the CPU-only arm64 engine with the installed NDK and CMake."""
from pathlib import Path
import subprocess
root=Path(__file__).resolve().parents[1]
cmake=root/'.tools/cmake-3.31.6-windows-x86_64/bin/cmake.exe'
build=root/'build/native-cpu'
subprocess.run([str(cmake),'-S',str(root/'app/src/main/cpp'),'-B',str(build),'-G','Ninja','-DCMAKE_BUILD_TYPE=Release','-DANDROID_ABI=arm64-v8a','-DANDROID_PLATFORM=android-28','-DANDROID_STL=c++_static','-DCMAKE_TOOLCHAIN_FILE='+str(root/'.tools/sdk/ndk/28.2.13676358/build/cmake/android.toolchain.cmake'),'-DCMAKE_MAKE_PROGRAM='+str(root/'.tools/ninja.exe')],check=True)
subprocess.run([str(cmake),'--build',str(build),'--target','qwenpocket','-j','6'],check=True)
