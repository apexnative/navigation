#import "ANNSplitViewOptions.h"

@implementation ANNSplitViewOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        // Any string: the module maps UIKit's names and the earlier ones (`hidden`, `visible`, `overlay`), and gives
        // `automatic` to the rest, `auto` included (`splitView.displayMode`).
        [ANNOptionField stringField:@"displayMode" values:nil],
        [ANNOptionField stringField:@"primaryEdge" values:@[ @"leading", @"trailing" ]],
        [ANNOptionField numberField:@"width"],
        [ANNOptionField numberField:@"minWidth"],
        [ANNOptionField numberField:@"maxWidth"],
        [ANNOptionField stringField:@"primaryBackgroundStyle" values:@[ @"sidebar", @"none" ]],
        [ANNOptionField groupField:@"supplementary" class:ANNSplitViewColumnOptions.class],
        [ANNOptionField groupField:@"inspector" class:ANNSplitViewInspectorOptions.class],
        [ANNOptionField stringField:@"splitBehavior" values:@[ @"automatic", @"tile", @"overlay", @"displace" ]],
        [ANNOptionField stringField:@"displayModeButton" values:@[ @"automatic", @"never", @"always" ]],
        [ANNOptionField boolField:@"presentsWithGesture"],
        [ANNOptionField boolField:@"showsSecondaryOnlyButton"],
        [ANNOptionField stringField:@"compactTopColumn" values:@[ @"primary", @"secondary" ]],
        [ANNOptionField textField:@"testID"],
    ];
}

@end
