#import "ANNReactViewCreator.h"
#import "ANNAssert.h"
#import "ANNReactView.h"
#import "ANNSlotContainers.h"
#import "ANNSlotScreenView.h"
#import "ApexNavigation+Internal.h"

@implementation ANNReactViewCreator {
    ANNEventEmitter *_eventEmitter;
}

- (instancetype)initWithEventEmitter:(ANNEventEmitter *)eventEmitter {
    if (self = [super init]) {
        _eventEmitter = eventEmitter;
    }
    return self;
}

- (UIView<ANNComponentProtocol> *)createComponentView:(NSString *)name
                                          componentId:(NSString *)componentId
                                        componentType:(ANNComponentType)componentType
                                  reactViewReadyBlock:
                                      (ANNReactViewReadyCompletionBlock)reactViewReadyBlock {
    ANNAssertMainQueue();
    RCTHost *host = [ApexNavigation getHost];
    // A screen of the one React tree: the slot screen view starts the tree's host with it.
    if (host && componentType == ANNComponentTypeComponent && [name hasPrefix:ANNSlotPrefix]) {
        return [[ANNSlotScreenView alloc] initWithComponentId:componentId
                                                         name:name
                                                         host:host
                                                 eventEmitter:_eventEmitter
                                          reactViewReadyBlock:reactViewReadyBlock];
    }
    return [[ANNReactView alloc] initWithHost:host
                                  componentId:componentId
                                         name:name
                                componentType:componentType
                                 eventEmitter:_eventEmitter
                          reactViewReadyBlock:reactViewReadyBlock];
}

@end
