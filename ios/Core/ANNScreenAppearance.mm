#import "ANNScreenAppearance.h"
#import "ANNAppearanceDispatcher.h"
#import "ANNScreenAppearance+Internal.h"
#import "ANNReactView.h"
#import "ApexNavigation+Internal.h"

@implementation ANNScreenAppearance {
    // Whether the record was made with content to wait for: a content that went away waits for nothing.
    BOOL _waitsForContent;
}

- (instancetype)initWithComponentId:(NSString *)componentId
                      componentName:(NSString *)componentName
                      componentType:(ANNComponentType)componentType
                            content:(id<ANNComponentProtocol>)content
                       eventEmitter:(ANNEventEmitter *)eventEmitter {
    if (self = [super init]) {
        _componentId = [componentId copy] ?: @"";
        _componentName = [componentName copy] ?: @"";
        _componentType = componentType;
        _content = content;
        _waitsForContent = content != nil;
        _eventEmitter = eventEmitter ?: ApexNavigation.eventEmitter;
        _phase = ANNAppearancePhaseHidden;
    }
    return self;
}

- (BOOL)isReady {
    if (_readyForGood) {
        return YES;
    }
    id<ANNComponentProtocol> content = _content;
    if (!_waitsForContent || !content || content.isReady) {
        _readyForGood = YES;
    }
    return _readyForGood;
}

- (void)markReady {
    [ANNAppearanceDispatcher appearanceBecameReady:self];
}

@end
