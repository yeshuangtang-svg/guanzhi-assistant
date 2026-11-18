#pragma once
#include <functional>
#include <string>
#include <vector>
namespace qwen {
using TokenCallback=std::function<void(const char*,size_t)>;
std::string load(const std::string&,const std::string&,bool,int);
void cancel();void resetCancel();void unload();
std::string backend();std::string status();std::string stats();
std::string generate(const std::vector<std::string>&,const std::vector<std::string>&,const std::vector<std::string>&,const std::string&,int,const TokenCallback&);
}
