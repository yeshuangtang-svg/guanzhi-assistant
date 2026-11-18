#include "../app/src/main/cpp/repetition_guard.h"
#include <cassert>
#include <iostream>
int main(){
    RepetitionGuard loop;bool hit=false;for(int i=0;i<36;i++)hit=loop.push(i%12,(i+1)*3);assert(hit&&loop.keep_bytes==36);
    RepetitionGuard twice;for(int i=0;i<24;i++)assert(!twice.push(i%12,i+1));
    RepetitionGuard varied;for(int i=0;i<1024;i++)assert(!varied.push(i,i+1));
    RepetitionGuard symbol;for(int i=0;i<31;i++)assert(!symbol.push(9,i+1));assert(symbol.push(9,32)&&symbol.keep_bytes==1);
    RepetitionGuard table;for(int i=0;i<200;i++)assert(!table.push(i%4,i+1));
    RepetitionGuard prefix;for(int i=0;i<7;i++)assert(!prefix.push(100+i,i+1));for(int i=0;i<36;i++)hit=prefix.push(i%12,i+8);assert(hit&&prefix.keep_bytes==19);
    std::cout<<"PASS: exact cycles, duplicate pair allowed, unique sequence, stuck token, low-diversity table and nonrepeating prefix\n";
}
