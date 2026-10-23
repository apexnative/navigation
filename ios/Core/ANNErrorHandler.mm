#import "ANNErrorHandler.h"
#import "ANNErrorTexts.h"
#import <React/RCTLog.h>

@implementation ANNErrorHandler

// Calling a nil block ends the app (`EXC_BAD_ACCESS` at 0x10, its invoke field): JavaScript always passes both blocks,
// so a nil one comes from a caller in native code, and the answer goes to the log.
+ (void)reject:(RCTPromiseRejectBlock)reject of:(NSString *)method text:(NSString *)text {
    if (!reject) {
        [self logFailureOf:method text:text];
        return;
    }
    reject(nil, text, nil);
}

+ (void)resolve:(RCTPromiseResolveBlock)resolve of:(NSString *)method value:(id)value {
    if (!resolve) {
        [self logFailureOf:method
                      text:[ANNErrorTexts couldNot:@"resolve its promise" reason:@"the command came without one"]];
        return;
    }
    resolve(value);
}

// A warning, not an error: a failed command without a promise (a `mergeOptions` the screen can't take) is the app's
// to notice, not a red box over it; Android logs it the same way, without a dialog.
+ (void)logFailureOf:(NSString *)method text:(NSString *)text {
    RCTLogWarn(@"[ApexNavigation] %@: %@", method, text);
}

@end
