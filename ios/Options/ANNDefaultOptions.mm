#import "ANNDefaultOptions.h"
#import "ANNAssert.h"
#import "ANNOptionsCache.h"

static ANNNavigationOptions *ANNCurrentDefaults;

@implementation ANNDefaultOptions

+ (ANNNavigationOptions *)current {
    if (!ANNCurrentDefaults) {
        ANNCurrentDefaults = [[ANNNavigationOptions alloc] initWithDict:nil];
    }
    return ANNCurrentDefaults;
}

+ (void)setCurrent:(ANNNavigationOptions *)options {
    ANNAssertMainQueue();
    ANNCurrentDefaults = options;
    [ANNOptionsCache optionsChanged];
}

@end
