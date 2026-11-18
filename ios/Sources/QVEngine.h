#import <Foundation/Foundation.h>
NS_ASSUME_NONNULL_BEGIN
@interface QVEngine : NSObject
+ (NSString *)loadModel:(NSString *)language vision:(NSString *)vision gpu:(BOOL)gpu imageTokens:(NSInteger)tokens NS_SWIFT_NAME(load(language:vision:gpu:imageTokens:));
+ (NSString *)generateRoles:(NSArray<NSString *> *)roles texts:(NSArray<NSString *> *)texts images:(NSArray<NSString *> *)images web:(NSString *)web maxTokens:(NSInteger)tokens onToken:(void (^)(NSData *))callback NS_SWIFT_NAME(generate(roles:texts:images:web:maxTokens:onToken:));
+ (void)cancel;
+ (void)resetCancel;
+ (void)unload;
+ (NSString *)backend;
+ (NSString *)status;
+ (NSString *)stats;
@end
NS_ASSUME_NONNULL_END
