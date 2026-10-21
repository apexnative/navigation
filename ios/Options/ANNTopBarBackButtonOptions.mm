#import "ANNTopBarBackButtonOptions.h"

@implementation ANNTopBarBackButtonOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField textField:@"accessibilityLabel"],
        [[ANNOptionField textField:@"id"] withProperty:@"identifier"],
        [ANNOptionField imageField:@"icon"],
        [ANNOptionField textField:@"sfSymbol"],
        [ANNOptionField boolField:@"visible"],
        [ANNOptionField textField:@"title"],
        [ANNOptionField boolField:@"showTitle"],
        [ANNOptionField colorField:@"color"],
        [ANNOptionField fontField],
        [ANNOptionField groupField:@"iconBackground" class:ANNIconBackgroundOptions.class],
        [ANNOptionField textField:@"testID"],
        [ANNOptionField boolField:@"enableMenu"],
        [ANNOptionField enumField:@"displayMode" values:@[ @"default", @"generic", @"minimal" ]],
        [ANNOptionField boolField:@"hideSharedBackground"],
        [ANNOptionField boolField:@"popStackOnPress"],
    ];
}

// The id of the event a held back sends is read from `id`, and from the key `identifier` when `id` isn't there.
- (instancetype)initWithDict:(NSDictionary *)dict {
    if (self = [super initWithDict:dict]) {
        if (!self.identifier.hasValue && [dict isKindOfClass:NSDictionary.class]) {
            self.identifier = [TextParser parse:dict key:@"identifier"];
        }
    }
    return self;
}

@end
