#import "QVEngine.h"
#include "EngineCore.hpp"
static NSString *str(const std::string& s){return [[NSString alloc] initWithBytes:s.data() length:s.size() encoding:NSUTF8StringEncoding] ?: @"无法读取引擎信息";}
static std::vector<std::string> values(NSArray<NSString *> *array){std::vector<std::string> out;for(NSString *s in array)out.emplace_back(s.UTF8String ?: "");return out;}
@implementation QVEngine
+ (NSString *)loadModel:(NSString *)language vision:(NSString *)vision gpu:(BOOL)gpu imageTokens:(NSInteger)tokens{return str(qwen::load(language.UTF8String,vision.UTF8String,gpu,(int)tokens));}
+ (NSString *)generateRoles:(NSArray<NSString *> *)roles texts:(NSArray<NSString *> *)texts images:(NSArray<NSString *> *)images web:(NSString *)web maxTokens:(NSInteger)tokens onToken:(void (^)(NSData *))callback{
    return str(qwen::generate(values(roles),values(texts),values(images),web.UTF8String,(int)tokens,[callback](const char *bytes,size_t size){@autoreleasepool{callback([NSData dataWithBytes:bytes length:size]);}}));
}
+ (void)cancel{qwen::cancel();}
+ (void)resetCancel{qwen::resetCancel();}
+ (void)unload{qwen::unload();}
+ (NSString *)backend{return str(qwen::backend());}
+ (NSString *)status{return str(qwen::status());}
+ (NSString *)stats{return str(qwen::stats());}
@end
