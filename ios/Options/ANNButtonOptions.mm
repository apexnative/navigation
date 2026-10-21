#import "ANNButtonOptions.h"

@implementation ANNButtonOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    // `SystemItemIcon`, and the search field of the toolbar (`OptionsToolbarSearchField`).
    NSArray<NSString *> *systemItems = @[
        @"done", @"cancel", @"edit", @"save", @"add", @"flexibleSpace", @"fixedSpace", @"compose",
        @"reply", @"action", @"organize", @"bookmarks", @"search", @"refresh", @"stop", @"camera",
        @"trash", @"play", @"pause", @"rewind", @"fastForward", @"undo", @"redo", @"close",
        @"searchField",
    ];
    return @[
        [[ANNOptionField textField:@"id"] withProperty:@"identifier"],
        [ANNOptionField boolField:@"allCaps"],
        [ANNOptionField imageField:@"icon"],
        [ANNOptionField textField:@"sfSymbol"],
        [ANNOptionField groupField:@"iconInsets" class:ANNIconInsetsOptions.class],
        [ANNOptionField groupField:@"component" class:ANNButtonComponentOptions.class],
        [ANNOptionField enumField:@"systemItem" values:systemItems],
        [ANNOptionField textField:@"text"],
        [ANNOptionField textField:@"accessibilityLabel"],
        [ANNOptionField fontField],
        [ANNOptionField boolField:@"enabled"],
        [ANNOptionField boolField:@"disableIconTint"],
        [ANNOptionField colorField:@"color"],
        [ANNOptionField colorField:@"disabledColor"],
        [ANNOptionField groupField:@"iconBackground" class:ANNIconBackgroundOptions.class],
        [ANNOptionField enumField:@"style" values:@[ @"plain", @"prominent" ]],
        [ANNOptionField textField:@"testID"],
        [ANNOptionField boolField:@"hideSharedBackground"],
        [ANNOptionField boolField:@"showText"],
        [ANNOptionField textField:@"group"],
        [ANNOptionField paramField:@"badge"
                             check:^BOOL(id value) {
                               return ANNJSONIsNumber(value) || ANNJSONIsBoolean(value) ||
                                      [value isKindOfClass:NSString.class] || value == NSNull.null;
                             }],
        [ANNOptionField colorField:@"badgeColor"],
        [ANNOptionField colorField:@"badgeTextColor"],
        [ANNOptionField boolField:@"toggle"],
        [ANNOptionField boolField:@"selected"],
        [ANNOptionField groupField:@"menu" class:ANNMenuOptions.class],
        [ANNOptionField boolField:@"menuOnLongPress"],
        [ANNOptionField enumField:@"customization" values:@[ @"fixed", @"movable", @"optional" ]],
        [ANNOptionField boolField:@"shownByDefault"],
        [ANNOptionField boolField:@"pinned"],
        [ANNOptionField enumField:@"priority" values:@[ @"high", @"standard", @"low" ]],
        [ANNOptionField boolField:@"vertical"],
    ];
}

@end
