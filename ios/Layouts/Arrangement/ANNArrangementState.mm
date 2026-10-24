#import "ANNArrangementState.h"
#import "ANNArrangementViewController.h"

@implementation ANNArrangementState

+ (BOOL)canShowArrangements {
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
    if (@available(iOS 27.1, *)) {
        return ANNArrangementViewController.class != nil;
    }
#endif
    return NO;
}

@end
