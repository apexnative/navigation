#import "ANNLayoutInfo.h"

@implementation ANNLayoutInfo

- (instancetype)initWithComponentId:(NSString *)componentId name:(NSString *)name {
    if (self = [super init]) {
        _componentId = [componentId copy];
        _name = [name copy];
    }
    return self;
}

@end
