#import "ANNMenuOptions.h"

static NSArray<ANNMenuItemOptions *> *ANNParseMenuItems(id items) {
    if (![items isKindOfClass:NSArray.class]) {
        return nil;
    }
    NSMutableArray<ANNMenuItemOptions *> *parsed = [NSMutableArray new];
    for (id item in items) {
        if ([item isKindOfClass:NSDictionary.class]) {
            [parsed addObject:[[ANNMenuItemOptions alloc] initWithDict:item]];
        }
    }
    return parsed;
}

// Checks the picked item where it is in a single selection list; YES when the item is in `items`, at any depth.
static BOOL ANNMenuItemPicked(NSArray<ANNMenuItemOptions *> *items, BOOL singleSelection, NSString *itemId) {
    for (ANNMenuItemOptions *item in items) {
        if (item.items.count) {
            if (ANNMenuItemPicked(item.items, [item.singleSelection withDefault:NO], itemId)) {
                return YES;
            }
        } else if ([[item.identifier withDefault:nil] isEqualToString:itemId]) {
            if (singleSelection) {
                for (ANNMenuItemOptions *neighbor in items) {
                    if (!neighbor.items.count) {
                        neighbor.checked = [Bool withValue:neighbor == item];
                    }
                }
            }
            return YES;
        }
    }
    return NO;
}

static UIMenu *ANNMenu(NSString *title, UIImage *image, BOOL singleSelection, NSArray<ANNMenuItemOptions *> *items,
                       void (^onPick)(NSString *itemId)) {
    NSMutableArray<UIMenuElement *> *children = [NSMutableArray new];
    for (ANNMenuItemOptions *item in items) {
        UIImage *itemImage = item.sfSymbol.hasValue ? [UIImage systemImageNamed:item.sfSymbol.get]
                                                    : [item.icon withDefault:nil];
        if (item.items.count) {
            UIMenu *submenu = ANNMenu([item.title withDefault:@""], itemImage,
                                      [item.singleSelection withDefault:NO], item.items, onPick);
            if ([submenu respondsToSelector:@selector(setAccessibilityIdentifier:)]) {
                submenu.accessibilityIdentifier = [item.testID withDefault:nil];
            }
            [children addObject:submenu];
            continue;
        }
        NSString *itemId = [item.identifier withDefault:nil];
        UIAction *action = [UIAction actionWithTitle:[item.title withDefault:@""]
                                               image:itemImage
                                          identifier:nil
                                             handler:^(__kindof UIAction *_Nonnull picked) {
                                               if (itemId) {
                                                   onPick(itemId);
                                               }
                                             }];
        UIMenuElementAttributes attributes = 0;
        if ([item.destructive withDefault:NO]) {
            attributes |= UIMenuElementAttributesDestructive;
        }
        if ([item.disabled withDefault:NO]) {
            attributes |= UIMenuElementAttributesDisabled;
        }
        action.attributes = attributes;
        action.state = [item.checked withDefault:NO] ? UIMenuElementStateOn : UIMenuElementStateOff;
        if ([action respondsToSelector:@selector(setAccessibilityIdentifier:)]) {
            action.accessibilityIdentifier = [item.testID withDefault:nil];
        }
        [children addObject:action];
    }
    return [UIMenu menuWithTitle:title
                           image:image
                      identifier:nil
                         options:singleSelection ? UIMenuOptionsSingleSelection : 0
                        children:children];
}

@implementation ANNMenuItemOptions

- (instancetype)initWithDict:(NSDictionary *)dict {
    self = [super initWithDict:dict];
    self.identifier = [TextParser parse:dict key:@"id"];
    self.title = [TextParser parse:dict key:@"title"];
    self.sfSymbol = [TextParser parse:dict key:@"sfSymbol"];
    self.icon = [ImageParser parse:dict key:@"icon"];
    self.destructive = [BoolParser parse:dict key:@"destructive"];
    self.disabled = [BoolParser parse:dict key:@"disabled"];
    self.checked = [BoolParser parse:dict key:@"checked"];
    self.items = ANNParseMenuItems(dict[@"items"]);
    self.singleSelection = [BoolParser parse:dict key:@"singleSelection"];
    self.testID = [TextParser parse:dict key:@"testID"];
    return self;
}

@end

@implementation ANNMenuOptions

- (instancetype)initWithDict:(NSDictionary *)dict {
    self = [super initWithDict:dict];
    self.title = [TextParser parse:dict key:@"title"];
    self.singleSelection = [BoolParser parse:dict key:@"singleSelection"];
    self.items = ANNParseMenuItems(dict[@"items"]);
    self.testID = [TextParser parse:dict key:@"testID"];
    return self;
}

// A menu from merged options replaces the items of the menu as a whole; its title and selection mode merge.
- (void)mergeOptions:(ANNMenuOptions *)options {
    if (options.title.hasValue)
        self.title = options.title;
    if (options.singleSelection.hasValue)
        self.singleSelection = options.singleSelection;
    if (options.items)
        self.items = options.items;
    if (options.testID.hasValue)
        self.testID = options.testID;
}

- (BOOL)hasValue {
    return self.items != nil;
}

- (void)itemPicked:(NSString *)itemId {
    ANNMenuItemPicked(self.items, [self.singleSelection withDefault:NO], itemId);
}

- (UIMenu *)menuWithPickHandler:(void (^)(NSString *itemId))onPick {
    return ANNMenu([self.title withDefault:@""], nil, [self.singleSelection withDefault:NO], self.items, onPick);
}

@end
