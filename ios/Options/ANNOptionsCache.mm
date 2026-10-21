#import "ANNOptionsCache.h"
#import "ANNAssert.h"

static NSUInteger ANNOptionsGeneration;

@implementation ANNOptionsCache

+ (NSUInteger)generation {
    return ANNOptionsGeneration;
}

+ (void)optionsChanged {
    ANNAssertMainQueue();
    ANNOptionsGeneration++;
}

@end
