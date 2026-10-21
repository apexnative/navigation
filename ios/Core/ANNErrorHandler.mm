#import "ANNErrorHandler.h"
#import <React/RCTLog.h>

@implementation ANNErrorHandler

+ (void)reject:(RCTPromiseRejectBlock)reject text:(NSString *)text {
    reject(nil, text, nil);
}

+ (void)logFailureOf:(NSString *)method text:(NSString *)text {
    RCTLogError(@"[ApexNavigation] %@: %@", method, text);
}

@end
