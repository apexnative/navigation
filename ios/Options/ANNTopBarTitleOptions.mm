#import "ANNTopBarTitleOptions.h"

@implementation ANNTopBarTitleOptions

+ (NSArray<ANNOptionField *> *)optionFields {
    return @[
        [ANNOptionField textField:@"text"],
        [ANNOptionField fontField],
        [ANNOptionField colorField:@"color"],
        [ANNOptionField groupField:@"component" class:ANNTopBarTitleComponentOptions.class],
        [ANNOptionField groupField:@"menu" class:ANNMenuOptions.class],
        [ANNOptionField boolField:@"rename"],
        [ANNOptionField groupField:@"document" class:ANNTopBarTitleDocumentOptions.class],
        [ANNOptionField enumField:@"glass" values:@[ @"regular", @"clear", @"none" ]],
        [ANNOptionField doubleField:@"height"],
        [ANNOptionField doubleField:@"topMargin"],
        [ANNOptionField enumField:@"alignment" values:@[ @"center", @"fill" ]],
    ];
}

@end
