#import "ANNKeyCommands.h"

static NSString *ANNKeyInput(NSString *input) {
    NSDictionary<NSString *, NSString *> *special = @{
        @"escape" : UIKeyInputEscape,
        @"enter" : @"\r",
        @"tab" : @"\t",
        @"delete" : @"\b",
        @"up" : UIKeyInputUpArrow,
        @"down" : UIKeyInputDownArrow,
        @"left" : UIKeyInputLeftArrow,
        @"right" : UIKeyInputRightArrow,
    };
    return special[input] ?: input;
}

NSArray<UIKeyCommand *> *ANNKeyCommandsFromOptions(NSArray<NSDictionary *> *options, SEL action) {
    NSDictionary<NSString *, NSNumber *> *flags = @{
        @"command" : @(UIKeyModifierCommand),
        @"shift" : @(UIKeyModifierShift),
        @"option" : @(UIKeyModifierAlternate),
        @"alternate" : @(UIKeyModifierAlternate),
        @"control" : @(UIKeyModifierControl),
    };
    NSMutableArray<UIKeyCommand *> *commands = [NSMutableArray new];
    for (NSDictionary *option in options) {
        if (![option isKindOfClass:NSDictionary.class] ||
            ![option[@"id"] isKindOfClass:NSString.class] ||
            ![option[@"input"] isKindOfClass:NSString.class]) {
            continue;
        }
        UIKeyModifierFlags modifiers = 0;
        NSArray *names = [option[@"modifiers"] isKindOfClass:NSArray.class] ? option[@"modifiers"] : @[];
        for (id name in names) {
            modifiers |= [name isKindOfClass:NSString.class] ? flags[name].integerValue : 0;
        }
        NSString *title = [option[@"title"] isKindOfClass:NSString.class] ? option[@"title"] : @"";
        UIKeyCommand *command = [UIKeyCommand commandWithTitle:title
                                                         image:nil
                                                        action:action
                                                         input:ANNKeyInput(option[@"input"])
                                                 modifierFlags:modifiers
                                                  propertyList:option[@"id"]];
        // With a title the command shows in the list of shortcuts (holding ⌘ on iPad).
        command.discoverabilityTitle = title.length > 0 ? title : nil;
        [commands addObject:command];
    }
    return commands;
}

static __weak UIResponder *ANNFoundFirstResponder;

@interface UIResponder (ANNFirstResponder)
- (void)ann_reportFirstResponder:(id)sender;
@end

@implementation UIResponder (ANNFirstResponder)
- (void)ann_reportFirstResponder:(id)sender {
    ANNFoundFirstResponder = self;
}
@end

// An action without a target goes to the first responder (UIKit's documented way to find it).
BOOL ANNNoFirstResponder(void) {
    ANNFoundFirstResponder = nil;
    [UIApplication.sharedApplication sendAction:@selector(ann_reportFirstResponder:)
                                             to:nil
                                           from:nil
                                       forEvent:nil];
    UIResponder *responder = ANNFoundFirstResponder;
    return !responder || [responder isKindOfClass:UIWindow.class] ||
           [responder isKindOfClass:UIApplication.class] ||
           [responder conformsToProtocol:@protocol(UIApplicationDelegate)];
}
