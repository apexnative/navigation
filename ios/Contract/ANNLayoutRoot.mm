#import "ANNLayoutRoot.h"

@implementation ANNLayoutRoot

- (instancetype)initWithRoot:(ANNLayoutNode *)root
                      modals:(NSArray<ANNLayoutNode *> *)modals
                    overlays:(NSArray<ANNLayoutNode *> *)overlays {
    if (self = [super init]) {
        _root = root;
        _modals = [modals copy];
        _overlays = [overlays copy];
    }
    return self;
}

- (NSArray<ANNLayoutNode *> *)trees {
    NSArray<ANNLayoutNode *> *trees = [@[ _root ] arrayByAddingObjectsFromArray:_modals];
    return [trees arrayByAddingObjectsFromArray:_overlays];
}

@end
