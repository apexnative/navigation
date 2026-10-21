#import "ANNTopBarOptions.h"

@implementation ANNTopBarOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField boolField:@"visible"],
        [ANNOptionField boolField:@"animate"],
        [ANNOptionField boolField:@"hideOnScroll"],
        [ANNOptionField boolField:@"hideOnTap"],
        [ANNOptionField boolField:@"hideOnKeyboard"],
        [ANNOptionField colorField:@"leftButtonColor"],
        [ANNOptionField colorField:@"rightButtonColor"],
        [ANNOptionField colorField:@"leftButtonBackgroundColor"],
        [ANNOptionField colorField:@"rightButtonBackgroundColor"],
        [ANNOptionField colorField:@"leftButtonDisabledColor"],
        [ANNOptionField colorField:@"rightButtonDisabledColor"],
        [ANNOptionField colorField:@"centerButtonColor"],
        [ANNOptionField colorField:@"centerButtonDisabledColor"],
        [ANNOptionField boolField:@"drawBehind"],
        [ANNOptionField textField:@"testID"],
        [ANNOptionField groupField:@"title" class:ANNTopBarTitleOptions.class],
        [ANNOptionField groupField:@"subtitle" class:ANNTopBarSubtitleOptions.class],
        [ANNOptionField groupField:@"backButton" class:ANNTopBarBackButtonOptions.class],
        [ANNOptionField groupArrayField:@"leftButtons" class:ANNButtonOptions.class],
        [ANNOptionField groupArrayField:@"rightButtons" class:ANNButtonOptions.class],
        [ANNOptionField groupArrayField:@"centerButtons" class:ANNButtonOptions.class],
        [ANNOptionField textField:@"customizationId"],
        [ANNOptionField groupField:@"overflowMenu" class:ANNMenuOptions.class],
        [ANNOptionField enumField:@"verticalBarKeeps" values:@[ @"tabs", @"buttons" ]],
        [ANNOptionField groupField:@"background" class:ANNTopBarBackgroundOptions.class],
        [ANNOptionField groupField:@"scrollEdgeAppearance"
                             class:ANNTopBarScrollEdgeAppearanceOptions.class],
        [ANNOptionField enumField:@"barStyle" values:@[ @"default", @"black" ]],
        [ANNOptionField enumField:@"style" values:@[ @"navigator", @"browser", @"editor" ]],
        [ANNOptionField boolField:@"noBorder"],
        [ANNOptionField groupField:@"searchBar" class:ANNSearchBarOptions.class],
        [ANNOptionField groupField:@"largeTitle" class:ANNTopBarLargeTitleOptions.class],
        [ANNOptionField doubleField:@"height"],
        [ANNOptionField colorField:@"borderColor"],
        [ANNOptionField doubleField:@"borderHeight"],
        [ANNOptionField doubleField:@"elevation"],
        [ANNOptionField doubleField:@"topMargin"],
        [ANNOptionField boolField:@"animateLeftButtons"],
        [ANNOptionField boolField:@"animateRightButtons"],
    ];
}

@end
