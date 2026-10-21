#import "ANNLayoutOptions.h"

@implementation ANNLayoutOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    NSArray<NSString *> *edges = @[ @"top", @"right", @"bottom", @"left" ];
    NSArray<NSString *> *orientations = @[
        @"all", @"default", @"portrait", @"landscape", @"upsideDown", @"sensor", @"sensorLandscape",
        @"sensorPortrait"
    ];
    return @[
        [ANNOptionField stringArrayField:@"safeAreaEdges" values:edges],
        [ANNOptionField colorField:@"backgroundColor"],
        [ANNOptionField colorField:@"componentBackgroundColor"],
        [ANNOptionField stringArrayField:@"orientation" values:orientations],
        [ANNOptionField boolField:@"verticalBars"],
        [ANNOptionField boolField:@"drawBehindVerticalBar"],
        [ANNOptionField boolField:@"autoHideHomeIndicator"],
        [ANNOptionField stringArrayField:@"deferSystemGestures" values:edges],
        [ANNOptionField boolField:@"adjustResize"],
        [ANNOptionField enumField:@"direction" values:@[ @"ltr", @"rtl", @"locale" ]],
    ];
}

@end
