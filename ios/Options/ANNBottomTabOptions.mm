#import "ANNBottomTabOptions.h"

@implementation ANNBottomTabOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField groupField:@"dotIndicator" class:ANNBottomTabDotIndicatorOptions.class],
        [ANNOptionField groupField:@"component" class:ANNComponentOptions.class],
        [ANNOptionField textField:@"text"],
        [ANNOptionField textField:@"badge"],
        [ANNOptionField colorField:@"badgeColor"],
        [ANNOptionField boolField:@"animateBadge"],
        [ANNOptionField textField:@"testID"],
        [ANNOptionField imageField:@"icon"],
        [ANNOptionField textField:@"sfSymbol"],
        [ANNOptionField colorField:@"iconColor"],
        [ANNOptionField doubleField:@"iconWidth"],
        [ANNOptionField doubleField:@"iconHeight"],
        [ANNOptionField colorField:@"textColor"],
        [ANNOptionField colorField:@"selectedIconColor"],
        [ANNOptionField colorField:@"selectedTextColor"],
        [ANNOptionField fontField],
        [ANNOptionField doubleField:@"selectedFontSize"],
        [ANNOptionField groupField:@"iconInsets" class:ANNIconInsetsOptions.class],
        [ANNOptionField imageField:@"selectedIcon"],
        [ANNOptionField textField:@"sfSelectedSymbol"],
        [ANNOptionField boolField:@"disableIconTint"],
        [ANNOptionField boolField:@"disableSelectedIconTint"],
        [ANNOptionField boolField:@"selectTabOnPress"],
        [ANNOptionField boolField:@"popToRootOnPress"],
        [ANNOptionField boolField:@"popToRootOnBlur"],
        [ANNOptionField enumField:@"role"
                           values:@[
                               @"search", @"bookmarks", @"contacts", @"downloads", @"favorites",
                               @"featured", @"history", @"more", @"mostRecent", @"mostViewed",
                               @"recents", @"topRated"
                           ]],
        [ANNOptionField enumField:@"customization"
                           values:@[
                               @"fixed", @"movable", @"optional", @"pinned", @"sidebarOnly"
                           ]],
        [ANNOptionField boolField:@"shownByDefault"],
        [ANNOptionField boolField:@"allowsHiding"],
    ];
}

@end
