#import "ANNSplitViewInspectorOptions.h"

@implementation ANNSplitViewInspectorOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return [[super optionFields] arrayByAddingObject:[ANNOptionField boolField:@"visible"]];
}

@end
