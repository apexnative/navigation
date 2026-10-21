#import "ANNFontOptions.h"
#import <React/RCTFont.h>

// The weights React Native's font takes, by the names the options allow; UIKit's names go to their numbers.
static NSDictionary<NSString *, NSString *> *ANNFontWeights(void) {
    static NSDictionary<NSString *, NSString *> *weights;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      weights = @{
          @"normal" : @"normal",
          @"bold" : @"bold",
          @"100" : @"100",
          @"200" : @"200",
          @"300" : @"300",
          @"400" : @"400",
          @"500" : @"500",
          @"600" : @"600",
          @"700" : @"700",
          @"800" : @"800",
          @"900" : @"900",
          @"ultralight" : @"100",
          @"thin" : @"200",
          @"light" : @"300",
          @"regular" : @"400",
          @"medium" : @"500",
          @"semibold" : @"600",
          @"heavy" : @"800",
          @"black" : @"900",
      };
    });
    return weights;
}

@implementation ANNFontOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField textField:@"fontFamily"],
        [ANNOptionField doubleField:@"fontSize"],
        [ANNOptionField textField:@"fontWeight"],
        [ANNOptionField enumField:@"fontStyle" values:@[ @"normal", @"italic" ]],
    ];
}

- (UIFont *)fontWithPlaceFont:(UIFont *)placeFont {
    NSString *family = [self.fontFamily withDefault:nil];
    NSString *weightName = [self.fontWeight withDefault:nil];
    if (!family && !self.fontSize.hasValue && !weightName && !self.fontStyle.hasValue) {
        return nil;
    }
    CGFloat size = [self.fontSize withDefault:placeFont.pointSize > 0 ? placeFont.pointSize : 17];
    UIFont *base = placeFont ?: [UIFont systemFontOfSize:size];
    // An unknown weight draws the normal one.
    NSString *weight = weightName ? (ANNFontWeights()[weightName] ?: @"normal") : nil;
    NSString *style = [self.fontStyle withDefault:nil];
    return [RCTFont updateFont:base
                    withFamily:family
                          size:@(size)
                        weight:weight
                         style:style
                       variant:nil
               scaleMultiplier:1];
}

@end
