#import "ANNLayoutTreeOptions.h"
#import "ANNAssert.h"
#import "ANNErrorTexts.h"
#import "ANNOptionsReader.h"

@implementation ANNLayoutTreeOptions

+ (NSDictionary<NSString *, ANNNavigationOptions *> *)optionsOfTree:(ANNLayoutNode *)tree
                                                           rejection:(NSString **)rejection {
    return [self optionsOfTrees:@[ tree ] rejection:rejection];
}

+ (NSDictionary<NSString *, ANNNavigationOptions *> *)optionsOfTrees:(NSArray<ANNLayoutNode *> *)trees
                                                            rejection:(NSString **)rejection {
    ANNAssertMainQueue();
    *rejection = nil;
    NSMutableDictionary<NSString *, ANNNavigationOptions *> *optionsById = [NSMutableDictionary new];
    for (ANNLayoutNode *tree in trees) {
        for (ANNLayoutNode *node in tree.allNodes) {
            NSString *unreadablePath = nil;
            ANNNavigationOptions *options = [ANNOptionsReader optionsFrom:node.options unreadablePath:&unreadablePath];
            if (unreadablePath) {
                *rejection = [ANNErrorTexts unreadableOption:unreadablePath componentId:node.nodeId];
                return nil;
            }
            optionsById[node.nodeId] = options;
        }
    }
    return optionsById;
}

@end
