#include "EngineCore.hpp"
#include <iostream>
#include <stdexcept>
int main(int argc,char** argv){
    if(argc!=4)return 2;
    for(bool gpu:{false,true}){
        auto error=qwen::load(argv[1],argv[2],gpu,256);
        if(!error.empty()){std::cerr<<error;return 1;}
        for(int i=0;i<3;++i){
            qwen::resetCancel();std::string text;
            error=qwen::generate({"user"},{i==0?"What is 2 + 3? Answer with only the integer.":"What is the dominant color in this image? Answer with one color word."},{i==0?"":argv[3]},"",24,[&](const char* bytes,size_t count){text.append(bytes,count);});
            std::cout<<"gpu_requested="<<gpu<<" case="<<i<<" output="<<text<<" error="<<error<<" stats="<<qwen::stats()<<"\n";
            if(!error.empty()||(i==0&&text.find('5')==std::string::npos)||(i>0&&text.find("red")==std::string::npos&&text.find("红")==std::string::npos))return 3;
        }
        qwen::cancel();std::string text;
        error=qwen::generate({"user"},{"Hello"},{""},"",24,[&](const char* b,size_t n){text.append(b,n);});
        if(!error.empty()||!text.empty())return 4;
        qwen::unload();
    }
    std::cout<<"PASS: portable core CPU inference, image cache, cancellation and GPU-request fallback. Not an Apple Metal test.\n";
}
