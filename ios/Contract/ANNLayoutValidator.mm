#import "ANNLayoutValidator.h"
#import "ANNAssert.h"
#import "ANNErrorTexts.h"
#import "ANNNodeRegistry.h"

@implementation ANNLayoutValidator

+ (NSString *)firstShownIdIn:(ANNLayoutNode *)tree allowed:(BOOL (^)(ANNLayoutNode *node))allowed {
    ANNAssertMainQueue();
    for (ANNLayoutNode *node in tree.allNodes) {
        if (node.isSideMenuSide || ![ANNNodeRegistry nodeForComponentId:node.nodeId]) {
            continue;
        }
        if (!allowed || !allowed(node)) {
            return node.nodeId;
        }
    }
    return nil;
}

+ (NSString *)checkStackChild:(ANNLayoutNode *)node stackId:(NSString *)stackId {
    if (node.isScreen || node.type == ANNLayoutTypeArrangement) {
        return nil;
    }
    return [ANNErrorTexts stackTakesScreens:stackId type:node.typeName];
}

@end
