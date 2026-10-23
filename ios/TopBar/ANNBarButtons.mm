#import "ANNBarButtons.h"
#import "ANNBarButtonComponentView.h"
#import "ANNIconButtonImage.h"
#import "ANNShowTextButton.h"
#import "ANNUIBarButtonItem.h"
#import "ANNUserChoice.h"
#import "UIViewController+LayoutProtocol.h"
#import <React/RCTLog.h>

// What a button is, by the first of its fields that gives a kind.
typedef NS_ENUM(NSInteger, ANNBarButtonKind) {
    ANNBarButtonKindNone,
    ANNBarButtonKindComponent,
    ANNBarButtonKindShowText,
    ANNBarButtonKindImageButton,
    ANNBarButtonKindSymbol,
    ANNBarButtonKindImage,
    ANNBarButtonKindText,
    ANNBarButtonKindSystemItem,
};

static const CGFloat ANNBarButtonFontSize = 17;

static NSArray<ANNButtonOptions *> *ANNButtonsOfSide(ANNNavigationOptions *options,
                                                     ANNBarButtonsSide side) {
    switch (side) {
        case ANNBarButtonsSideLeft:
            return options.topBar.leftButtons;
        case ANNBarButtonsSideRight:
            return options.topBar.rightButtons;
        case ANNBarButtonsSideCenter:
            return options.topBar.centerButtons;
        case ANNBarButtonsSideToolbar:
            return options.toolbar.buttons;
    }
    return nil;
}

// A new list in the own options (never the one in place: lists of copies share it).
static void ANNSetButtonsOfSide(ANNNavigationOptions *options, ANNBarButtonsSide side,
                                NSArray<ANNButtonOptions *> *buttons) {
    switch (side) {
        case ANNBarButtonsSideLeft:
            options.topBar.leftButtons = buttons;
            break;
        case ANNBarButtonsSideRight:
            options.topBar.rightButtons = buttons;
            break;
        case ANNBarButtonsSideCenter:
            options.topBar.centerButtons = buttons;
            break;
        case ANNBarButtonsSideToolbar:
            options.toolbar.buttons = buttons;
            break;
    }
}

static ANNButtonOptions *ANNButtonWithId(NSArray<ANNButtonOptions *> *buttons, NSString *buttonId) {
    for (ANNButtonOptions *button in buttons) {
        if ([[button.identifier withDefault:nil] isEqualToString:buttonId]) {
            return button;
        }
    }
    return nil;
}

// The button `buttonId` of `side` in `options` changed on a copy, in a new list; nothing when the side has no such
// button. The other buttons stay the objects they are: nothing changes them.
static void ANNChangeButton(ANNNavigationOptions *options,
                            ANNBarButtonsSide side,
                            NSString *buttonId,
                            void (^change)(ANNButtonOptions *changed)) {
    NSArray<ANNButtonOptions *> *list = ANNButtonsOfSide(options, side);
    if (!ANNButtonWithId(list, buttonId)) {
        return;
    }
    NSMutableArray<ANNButtonOptions *> *changedList = [NSMutableArray arrayWithCapacity:list.count];
    for (ANNButtonOptions *button in list) {
        if ([[button.identifier withDefault:nil] isEqualToString:buttonId]) {
            ANNButtonOptions *copy = [button copy];
            change(copy);
            [changedList addObject:copy];
        } else {
            [changedList addObject:button];
        }
    }
    ANNSetButtonsOfSide(options, side, changedList);
}

#pragma mark - The user's choices

static NSString *ANNToggleChoiceKey(ANNBarButtonsSide side, NSString *buttonId) {
    return [NSString stringWithFormat:@"buttons.%ld.selected.%@", (long)side, buttonId];
}

static NSString *ANNButtonMenuChoiceKey(ANNBarButtonsSide side, NSString *buttonId) {
    return [NSString stringWithFormat:@"buttons.%ld.menu.%@", (long)side, buttonId];
}

// The button `buttonId` of `side` in `options`, or nil.
static ANNButtonOptions *ANNButtonIn(ANNNavigationOptions *options, ANNBarButtonsSide side,
                                     NSString *buttonId) {
    return ANNButtonWithId(ANNButtonsOfSide(options, side), buttonId);
}

// A toggle the user flipped: `selected` of the button with its id, wherever its list comes from. It goes when the
// node's resolution after a merge has no such button, or when the merge gives the button a `selected` other than the
// app had (the own button's, else the button the node resolved to before the merge), by the rules of a menu's check.
static ANNUserChoice *ANNToggleChoice(ANNBarButtonsSide side, NSString *buttonId, BOOL selected) {
    return [ANNUserChoice choiceWithValue:@(selected)
        apply:^(ANNNavigationOptions *options, id value) {
          BOOL on = [(NSNumber *)value boolValue];
          ANNChangeButton(options, side, buttonId, ^(ANNButtonOptions *changed) {
            changed.selected = [Bool withValue:on];
          });
        }
        merged:^id(id value, ANNNavigationOptions *merge, ANNNavigationOptions *own,
                   ANNNavigationOptions *before, ANNNavigationOptions *after) {
          if (!ANNButtonIn(after, side, buttonId)) {
              return nil;
          }
          ANNButtonOptions *given = ANNButtonIn(merge, side, buttonId);
          if (!given.selected.hasValue) {
              return value;
          }
          ANNButtonOptions *was = ANNButtonIn(own, side, buttonId);
          if (!was.selected.hasValue) {
              was = ANNButtonIn(before, side, buttonId);
          }
          BOOL same = [given.selected withDefault:NO] == [was.selected withDefault:NO];
          return same ? value : nil;
        }];
}

// The items of the level (a menu's or a submenu's own list, without its submenus) that holds `itemId`, at any depth;
// nil when no level holds it.
static NSArray<ANNMenuItemOptions *> *ANNLevelOf(NSArray<ANNMenuItemOptions *> *items,
                                                 NSString *itemId) {
    NSMutableArray<ANNMenuItemOptions *> *level = [NSMutableArray new];
    BOOL holds = NO;
    for (ANNMenuItemOptions *item in items) {
        NSString *leafId = item.items.count ? nil : [item.identifier withDefault:nil];
        if (leafId) {
            [level addObject:item];
            holds = holds || [leafId isEqualToString:itemId];
        }
    }
    if (holds) {
        return level;
    }
    for (ANNMenuItemOptions *item in items) {
        if (item.items.count) {
            NSArray<ANNMenuItemOptions *> *found = ANNLevelOf(item.items, itemId);
            if (found) {
                return found;
            }
        }
    }
    return nil;
}

// The ids of the items in the level that holds `itemId`: the neighbors one pick unchecks.
static NSArray<NSString *> *ANNNeighborsOf(NSArray<ANNMenuItemOptions *> *items, NSString *itemId) {
    NSMutableArray<NSString *> *ids = [NSMutableArray new];
    for (ANNMenuItemOptions *item in ANNLevelOf(items, itemId)) {
        [ids addObject:[item.identifier withDefault:@""]];
    }
    return ids;
}

// The checked items of the level that holds `itemId` in `menu`: the field a merge gives that level. Nil when `menu`
// doesn't hold the item.
static NSSet<NSString *> *ANNCheckedInLevel(ANNMenuOptions *menu, NSString *itemId) {
    NSArray<ANNMenuItemOptions *> *level = menu ? ANNLevelOf(menu.items, itemId) : nil;
    if (!level) {
        return nil;
    }
    NSMutableSet<NSString *> *checked = [NSMutableSet new];
    for (ANNMenuItemOptions *item in level) {
        if ([item.checked withDefault:NO]) {
            [checked addObject:[item.identifier withDefault:@""]];
        }
    }
    return checked;
}

// A menu that has items, or nil: a merge without items gives no menu.
static ANNMenuOptions *ANNMenuWithItems(ANNMenuOptions *menu) {
    return menu.items ? menu : nil;
}

// The picks of single-selection levels in one menu, in the order made: the resolution plays them again on a copy of
// the menu it has, so a new menu from the app keeps the check where its items have the same ids. As on Android
// (`UserChoices.yieldTo`), at each merge into the node a pick goes when the node's resolution after it no longer has
// the item, or when the merge gives the menu with other checks in the pick's level than the app had there (the own
// options' menu, else the menu the node resolved to before the merge); the picks of the other levels stay.
typedef ANNMenuOptions *_Nullable (^ANNMenuOf)(ANNNavigationOptions *options);
typedef void (^ANNSetMenu)(ANNNavigationOptions *options, ANNMenuOptions *menu);

static ANNUserChoice *ANNMenuChoice(NSArray<NSString *> *picks,
                                    ANNMenuOf menuOf,
                                    ANNSetMenu setMenu) {
    return [ANNUserChoice choiceWithValue:picks
        apply:^(ANNNavigationOptions *options, id value) {
          ANNMenuOptions *menu = menuOf(options);
          if (menu.items.count == 0) {
              return;
          }
          ANNMenuOptions *copy = [ANNBarButtons deepCopyOfMenu:menu];
          for (NSString *itemId in (NSArray<NSString *> *)value) {
              [copy itemPicked:itemId];
          }
          setMenu(options, copy);
        }
        merged:^id(id value, ANNNavigationOptions *merge, ANNNavigationOptions *own,
                   ANNNavigationOptions *before, ANNNavigationOptions *after) {
          ANNMenuOptions *now = ANNMenuWithItems(menuOf(after));
          ANNMenuOptions *given = ANNMenuWithItems(menuOf(merge));
          ANNMenuOptions *was = ANNMenuWithItems(menuOf(own)) ?: ANNMenuWithItems(menuOf(before));
          NSArray<NSString *> *made = value;
          NSMutableArray<NSString *> *kept = [NSMutableArray new];
          for (NSString *itemId in made) {
              BOOL gone = !now || !ANNLevelOf(now.items, itemId);
              NSSet<NSString *> *givenChecks = given ? ANNCheckedInLevel(given, itemId) : nil;
              NSSet<NSString *> *wasChecks = ANNCheckedInLevel(was, itemId);
              BOOL same = givenChecks == wasChecks || [givenChecks isEqualToSet:wasChecks];
              BOOL changed = given && !same;
              if (!gone && !changed) {
                  [kept addObject:itemId];
              }
          }
          if (kept.count == 0) {
              return nil;
          }
          return kept.count == made.count ? value : kept;
        }];
}

static UIColor *ANNColorOf(Color *color) {
    if (!color.hasValue || color.isNoColor) {
        return nil;
    }
    return [color withDefault:UIColor.tintColor];
}

static Color *ANNSideColor(ANNTopBarOptions *topBar, ANNBarButtonsSide side) {
    switch (side) {
        case ANNBarButtonsSideLeft:
            return topBar.leftButtonColor;
        case ANNBarButtonsSideRight:
            return topBar.rightButtonColor;
        case ANNBarButtonsSideCenter:
            return topBar.centerButtonColor;
        case ANNBarButtonsSideToolbar:
            return nil;
    }
    return nil;
}

static Color *ANNSideDisabledColor(ANNTopBarOptions *topBar, ANNBarButtonsSide side) {
    switch (side) {
        case ANNBarButtonsSideLeft:
            return topBar.leftButtonDisabledColor;
        case ANNBarButtonsSideRight:
            return topBar.rightButtonDisabledColor;
        case ANNBarButtonsSideCenter:
            return topBar.centerButtonDisabledColor;
        case ANNBarButtonsSideToolbar:
            return nil;
    }
    return nil;
}

static Color *ANNSideBackgroundColor(ANNTopBarOptions *topBar, ANNBarButtonsSide side) {
    switch (side) {
        case ANNBarButtonsSideLeft:
            return topBar.leftButtonBackgroundColor;
        case ANNBarButtonsSideRight:
            return topBar.rightButtonBackgroundColor;
        default:
            return nil;
    }
}

static NSString *ANNSymbolOf(ANNButtonOptions *button) {
    NSString *symbol = [button.sfSymbol withDefault:nil];
    return symbol.length > 0 && [UIImage systemImageNamed:symbol] ? symbol : nil;
}

static UIImage *ANNImageOf(ANNButtonOptions *button) {
    return [button.icon withDefault:nil];
}

static NSString *ANNTextOf(ANNButtonOptions *button) {
    NSString *text = [button.text withDefault:nil];
    return text.length > 0 ? text : nil;
}

static BOOL ANNHasComponent(ANNButtonOptions *button) {
    return [button.component.name withDefault:nil].length > 0 &&
           [button.component.componentId withDefault:nil].length > 0;
}

// The image button's background: its own `iconBackground.color`, else (only for an image icon) the side's.
static UIColor *ANNIconBackgroundOf(ANNButtonOptions *button, Color *sideBackground) {
    UIColor *own = ANNColorOf(button.iconBackground.color);
    if (own) {
        return own;
    }
    return ANNImageOf(button) && !ANNSymbolOf(button) ? ANNColorOf(sideBackground) : nil;
}

static ANNBarButtonKind ANNKindOf(ANNButtonOptions *button, Color *sideBackground) {
    if (ANNHasComponent(button)) {
        return ANNBarButtonKindComponent;
    }
    UIImage *image = ANNImageOf(button);
    NSString *symbol = ANNSymbolOf(button);
    if ([button.showText withDefault:NO] && (image || symbol) && ANNTextOf(button)) {
        return ANNBarButtonKindShowText;
    }
    if (image && (ANNIconBackgroundOf(button, sideBackground) || button.iconInsets.hasValue)) {
        return ANNBarButtonKindImageButton;
    }
    if (symbol) {
        return ANNBarButtonKindSymbol;
    }
    if (image) {
        return ANNBarButtonKindImage;
    }
    if (ANNTextOf(button)) {
        return ANNBarButtonKindText;
    }
    if ([button.systemItem withDefault:nil].length > 0) {
        return ANNBarButtonKindSystemItem;
    }
    return ANNBarButtonKindNone;
}

static UIBarButtonSystemItem ANNSystemItemOf(NSString *name) {
    static NSDictionary<NSString *, NSNumber *> *items;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      items = @{
          @"done" : @(UIBarButtonSystemItemDone),
          @"cancel" : @(UIBarButtonSystemItemCancel),
          @"edit" : @(UIBarButtonSystemItemEdit),
          @"save" : @(UIBarButtonSystemItemSave),
          @"add" : @(UIBarButtonSystemItemAdd),
          @"flexibleSpace" : @(UIBarButtonSystemItemFlexibleSpace),
          @"fixedSpace" : @(UIBarButtonSystemItemFixedSpace),
          @"compose" : @(UIBarButtonSystemItemCompose),
          @"reply" : @(UIBarButtonSystemItemReply),
          @"action" : @(UIBarButtonSystemItemAction),
          @"organize" : @(UIBarButtonSystemItemOrganize),
          @"bookmarks" : @(UIBarButtonSystemItemBookmarks),
          @"search" : @(UIBarButtonSystemItemSearch),
          @"refresh" : @(UIBarButtonSystemItemRefresh),
          @"stop" : @(UIBarButtonSystemItemStop),
          @"camera" : @(UIBarButtonSystemItemCamera),
          @"trash" : @(UIBarButtonSystemItemTrash),
          @"play" : @(UIBarButtonSystemItemPlay),
          @"pause" : @(UIBarButtonSystemItemPause),
          @"rewind" : @(UIBarButtonSystemItemRewind),
          @"fastForward" : @(UIBarButtonSystemItemFastForward),
          @"undo" : @(UIBarButtonSystemItemUndo),
          @"redo" : @(UIBarButtonSystemItemRedo),
          @"close" : @(UIBarButtonSystemItemClose),
      };
    });
    NSNumber *item = items[name];
    return item ? (UIBarButtonSystemItem)item.integerValue : UIBarButtonSystemItemDone;
}

// One built button: its item and the group it joins (iOS 26 capsules).
@interface ANNBuiltButton : NSObject
@property(nonatomic, strong) ANNUIBarButtonItem *item;
@property(nonatomic, strong) ANNButtonOptions *options;
@property(nonatomic, copy, nullable) NSString *group;
@property(nonatomic) BOOL pinned;
@end

@implementation ANNBuiltButton
@end

// What a press of an item or of its own view needs.
@interface ANNButtonPress : NSObject
@property(nonatomic, copy) NSString *buttonId;
@property(nonatomic) ANNBarButtonsSide side;
@property(nonatomic) BOOL toggle;
@property(nonatomic, weak, nullable) ANNUIBarButtonItem *item;
@end

@implementation ANNButtonPress
@end

@implementation ANNBarButtons {
    __weak UIViewController *_screen;
    ANNReactComponentRegistry *_registry;
    NSMutableDictionary<NSString *, ANNBarButtonComponentView *> *_slots;
    // The state of each toggle by its id: a button given again without `selected` keeps it.
    NSMutableDictionary<NSString *, NSNumber *> *_selected;
    // What a press of each sender (an item, a button of an item) means.
    NSMapTable<id, ANNButtonPress *> *_presses;
}

- (instancetype)initWithScreen:(UIViewController *)screen
                      registry:(ANNReactComponentRegistry *)registry {
    if (self = [super init]) {
        _screen = screen;
        _registry = registry;
        _slots = [NSMutableDictionary new];
        _selected = [NSMutableDictionary new];
        _presses = [NSMapTable weakToStrongObjectsMapTable];
    }
    return self;
}

#pragma mark - The sides

- (void)applySide:(ANNBarButtonsSide)side
          options:(ANNNavigationOptions *)options
         animated:(BOOL)animated {
    UIViewController *screen = _screen;
    if (!screen) {
        return;
    }
    UINavigationItem *navigationItem = screen.navigationItem;
    NSArray<ANNBuiltButton *> *built = [self buildSide:side options:options];
    if (side == ANNBarButtonsSideCenter) {
        if (@available(iOS 16.0, *)) {
            [self setCenter:built options:options item:navigationItem];
        }
        return;
    }
    NSMutableArray<ANNBuiltButton *> *placed = [built mutableCopy];
    if (side == ANNBarButtonsSideRight) {
        if (@available(iOS 16.0, *)) {
            [self setPinned:placed item:navigationItem];
        }
    }
    if (@available(iOS 26.0, *)) {
        if ([self hasGroupOfSeveral:placed]) {
            NSArray<UIBarButtonItemGroup *> *groups = [self groupsOf:placed];
            if (side == ANNBarButtonsSideLeft) {
                navigationItem.leadingItemGroups = groups;
            } else {
                navigationItem.trailingItemGroups = groups.reverseObjectEnumerator.allObjects;
            }
            return;
        }
    }
    NSMutableArray<UIBarButtonItem *> *items = [NSMutableArray new];
    for (ANNBuiltButton *button in placed) {
        if (@available(iOS 26.0, *)) {
            // Each button its own capsule: a zero-width space between neighbours, and the right ones never share the
            // capsule of "•••".
            if (items.count > 0) {
                [items addObject:[UIBarButtonItem fixedSpaceItemOfWidth:0]];
            }
            if (side == ANNBarButtonsSideRight) {
                button.item.sharesBackground = NO;
            }
        }
        [items addObject:button.item];
    }
    if (side == ANNBarButtonsSideLeft) {
        [navigationItem setLeftBarButtonItems:items animated:animated];
    } else {
        [navigationItem setRightBarButtonItems:items animated:animated];
    }
}

- (NSArray<ANNBuiltButton *> *)buildSide:(ANNBarButtonsSide)side
                                 options:(ANNNavigationOptions *)options {
    NSMutableArray<ANNBuiltButton *> *built = [NSMutableArray new];
    for (ANNButtonOptions *button in ANNButtonsOfSide(options, side) ?: @[]) {
        ANNUIBarButtonItem *item = [self itemFor:button side:side options:options];
        if (!item) {
            continue;
        }
        ANNBuiltButton *entry = [ANNBuiltButton new];
        entry.item = item;
        entry.options = button;
        NSString *group = [button.group withDefault:nil];
        entry.group = group.length > 0 ? group : nil;
        entry.pinned = side == ANNBarButtonsSideRight && [button.pinned withDefault:NO];
        [built addObject:entry];
    }
    return built;
}

- (BOOL)hasGroupOfSeveral:(NSArray<ANNBuiltButton *> *)buttons {
    for (NSUInteger index = 1; index < buttons.count; index++) {
        NSString *group = buttons[index].group;
        if (group && [group isEqualToString:buttons[index - 1].group]) {
            return YES;
        }
    }
    return NO;
}

// Neighbours with one `group` share a capsule; every other button is a group of its own.
- (NSArray<NSArray<ANNBuiltButton *> *> *)runsOf:(NSArray<ANNBuiltButton *> *)buttons {
    NSMutableArray<NSArray<ANNBuiltButton *> *> *runs = [NSMutableArray new];
    NSMutableArray<ANNBuiltButton *> *run = nil;
    for (ANNBuiltButton *button in buttons) {
        if (run && button.group && [button.group isEqualToString:run.lastObject.group]) {
            [run addObject:button];
            continue;
        }
        run = [NSMutableArray arrayWithObject:button];
        [runs addObject:run];
    }
    return runs;
}

- (NSArray<UIBarButtonItemGroup *> *)groupsOf:(NSArray<ANNBuiltButton *> *)buttons
    API_AVAILABLE(ios(16.0)) {
    NSMutableArray<UIBarButtonItemGroup *> *groups = [NSMutableArray new];
    for (NSArray<ANNBuiltButton *> *run in [self runsOf:buttons]) {
        NSMutableArray<UIBarButtonItem *> *items = [NSMutableArray new];
        for (ANNBuiltButton *button in run) {
            [items addObject:button.item];
        }
        [groups addObject:[[UIBarButtonItemGroup alloc] initWithBarButtonItems:items
                                                            representativeItem:nil]];
    }
    return groups;
}

// Right `pinned` buttons stay at the edge behind "•••", several in one capsule.
- (void)setPinned:(NSMutableArray<ANNBuiltButton *> *)buttons
             item:(UINavigationItem *)navigationItem API_AVAILABLE(ios(16.0)) {
    NSMutableArray<UIBarButtonItem *> *pinned = [NSMutableArray new];
    for (ANNBuiltButton *button in [buttons copy]) {
        if (button.pinned) {
            [pinned addObject:button.item];
            [buttons removeObjectIdenticalTo:button];
        }
    }
    navigationItem.pinnedTrailingGroup =
        pinned.count > 0 ? [[UIBarButtonItemGroup alloc] initWithBarButtonItems:pinned
                                                             representativeItem:nil]
                         : nil;
}

// Each button, or neighbours with one `group`, a group of the middle; with `customizationId` the group can be moved or
// taken out by its `group` name (several buttons) or the button's `id`; a group without a name or with a name already
// taken is fixed.
- (void)setCenter:(NSArray<ANNBuiltButton *> *)buttons
          options:(ANNNavigationOptions *)options
             item:(UINavigationItem *)navigationItem API_AVAILABLE(ios(16.0)) {
    NSString *customizationId = [options.topBar.customizationId withDefault:nil];
    BOOL customizes = customizationId.length > 0;
    NSMutableSet<NSString *> *taken = [NSMutableSet new];
    NSMutableArray<UIBarButtonItemGroup *> *groups = [NSMutableArray new];
    for (NSArray<ANNBuiltButton *> *run in [self runsOf:buttons]) {
        NSMutableArray<UIBarButtonItem *> *items = [NSMutableArray new];
        for (ANNBuiltButton *button in run) {
            if (@available(iOS 26.0, *)) {
                if (run.count == 1) {
                    button.item.sharesBackground = NO;
                }
            }
            [items addObject:button.item];
        }
        ANNButtonOptions *first = run.firstObject.options;
        NSString *groupId =
            run.count > 1 ? run.firstObject.group : [first.identifier withDefault:nil];
        NSString *customization = [first.customization withDefault:@"movable"];
        UIBarButtonItemGroup *group = nil;
        if (!customizes) {
            group = [[UIBarButtonItemGroup alloc] initWithBarButtonItems:items
                                                      representativeItem:nil];
        } else if (groupId.length == 0 || [taken containsObject:groupId] ||
                   [customization isEqualToString:@"fixed"]) {
            group = [UIBarButtonItemGroup fixedGroupWithRepresentativeItem:nil items:items];
        } else if ([customization isEqualToString:@"optional"]) {
            [taken addObject:groupId];
            group = [UIBarButtonItemGroup
                optionalGroupWithCustomizationIdentifier:groupId
                                  inDefaultCustomization:[first.shownByDefault withDefault:YES]
                                      representativeItem:nil
                                                   items:items];
        } else {
            [taken addObject:groupId];
            group = [UIBarButtonItemGroup movableGroupWithCustomizationIdentifier:groupId
                                                               representativeItem:nil
                                                                            items:items];
        }
        [groups addObject:group];
    }
    navigationItem.customizationIdentifier = customizes ? customizationId : nil;
    navigationItem.centerItemGroups = groups;
}

#pragma mark - The overflow menu

- (void)applyOverflowMenu:(ANNNavigationOptions *)options {
    if (@available(iOS 16.0, *)) {
        UIViewController *screen = _screen;
        ANNMenuOptions *menu = options.topBar.overflowMenu;
        if (!screen || menu.items.count == 0) {
            screen.navigationItem.additionalOverflowItems = nil;
            return;
        }
        __weak ANNBarButtons *weakSelf = self;
        __weak UIViewController *weakScreen = screen;
        // Built from the screen's options at each open, so a check picked last time shows.
        screen.navigationItem.additionalOverflowItems =
            [UIDeferredMenuElement elementWithUncachedProvider:^(
                                       void (^completion)(NSArray<UIMenuElement *> *)) {
              ANNBarButtons *buttons = weakSelf;
              UIViewController *shown = weakScreen;
              ANNMenuOptions *current = [shown resolveOptionsWithDefault].topBar.overflowMenu;
              if (!buttons || current.items.count == 0) {
                  completion(@[]);
                  return;
              }
              UIMenu *built = [current menuWithPickHandler:^(NSString *itemId) {
                [weakSelf menuItemPicked:itemId
                    choiceKey:@"topBar.overflowMenu"
                    menuOf:^ANNMenuOptions *(ANNNavigationOptions *options) {
                      return options.topBar.overflowMenu;
                    }
                    setMenu:^(ANNNavigationOptions *options, ANNMenuOptions *copy) {
                      options.topBar.overflowMenu = copy;
                    }];
              }];
              UIMenu *section = [UIMenu menuWithTitle:built.title
                                                image:nil
                                           identifier:nil
                                              options:UIMenuOptionsDisplayInline
                                             children:built.children];
              completion(@[ section ]);
            }];
    }
}

#pragma mark - The toolbar (`toolbar.buttons`)

- (NSArray<UIBarButtonItem *> *)toolbarItemsWithOptions:(ANNNavigationOptions *)options {
    NSMutableArray<UIBarButtonItem *> *items = [NSMutableArray new];
    // The kind of the previous element: an explicit space, an icon of a group, or another button.
    BOOL previousIsSpace = YES;
    NSString *previousIconGroup = nil;
    for (ANNButtonOptions *button in options.toolbar.buttons ?: @[]) {
        UIBarButtonItem *space = [self spaceFor:button];
        if (space) {
            [items addObject:space];
            previousIsSpace = YES;
            previousIconGroup = nil;
            continue;
        }
        if ([[button.systemItem withDefault:nil] isEqualToString:@"searchField"] &&
            [button.identifier withDefault:nil].length == 0) {
            continue;
        }
        ANNUIBarButtonItem *item = [self itemFor:button
                                            side:ANNBarButtonsSideToolbar
                                         options:options];
        if (!item) {
            continue;
        }
        ANNBarButtonKind kind = ANNKindOf(button, nil);
        BOOL icon = kind == ANNBarButtonKindSymbol || kind == ANNBarButtonKindImage ||
                    kind == ANNBarButtonKindImageButton || kind == ANNBarButtonKindShowText;
        NSString *group = icon ? [button.group withDefault:nil] : nil;
        if (@available(iOS 26.0, *)) {
            // Only icons of one group share a capsule (the toolbar has no groups): any other neighbours get a
            // zero-width space, unless a space of the options is there already.
            BOOL sameGroup = group.length > 0 && [group isEqualToString:previousIconGroup];
            if (!previousIsSpace && !sameGroup) {
                [items addObject:[UIBarButtonItem fixedSpaceItemOfWidth:0]];
            }
        }
        [items addObject:item];
        previousIsSpace = NO;
        previousIconGroup = group;
    }
    return items;
}

// An element without an id, text, icon and component is a space of the toolbar: flexible, a zero-width fixed one,
// or the search field (iOS 26+; skipped before).
- (nullable UIBarButtonItem *)spaceFor:(ANNButtonOptions *)button {
    if ([button.identifier withDefault:nil].length > 0 || ANNTextOf(button) || ANNImageOf(button) ||
        ANNSymbolOf(button) || ANNHasComponent(button)) {
        return nil;
    }
    NSString *systemItem = [button.systemItem withDefault:nil];
    if ([systemItem isEqualToString:@"flexibleSpace"]) {
        return [UIBarButtonItem flexibleSpaceItem];
    }
    if ([systemItem isEqualToString:@"fixedSpace"]) {
        return [UIBarButtonItem fixedSpaceItemOfWidth:0];
    }
    if ([systemItem isEqualToString:@"searchField"]) {
        if (@available(iOS 26.0, *)) {
            UIViewController *screen = _screen;
            ANNSearchBarOptions *searchBar = [screen resolveOptionsWithDefault].topBar.searchBar;
            if (screen && [searchBar.visible withDefault:NO]) {
                return screen.navigationItem.searchBarPlacementBarButtonItem;
            }
        }
        return nil;
    }
    return nil;
}

#pragma mark - One button

- (nullable ANNUIBarButtonItem *)itemFor:(ANNButtonOptions *)button
                                    side:(ANNBarButtonsSide)side
                                 options:(ANNNavigationOptions *)options {
    NSString *buttonId = [button.identifier withDefault:nil];
    if (buttonId.length == 0) {
        // A button the app can't tell apart is not placed.
        RCTLogError(@"[ApexNavigation] a bar button without an id is not placed: give every "
                    @"button an `id`");
        return nil;
    }
    ANNTopBarOptions *topBar = options.topBar;
    Color *sideBackground = ANNSideBackgroundColor(topBar, side);
    ANNBarButtonKind kind = ANNKindOf(button, sideBackground);
    if (kind == ANNBarButtonKindNone) {
        return nil;
    }
    UIColor *color = ANNColorOf(button.color) ?: ANNColorOf(ANNSideColor(topBar, side));
    UIColor *disabledColor =
        ANNColorOf(button.disabledColor) ?: ANNColorOf(ANNSideDisabledColor(topBar, side));
    BOOL enabled = [button.enabled withDefault:YES];
    ANNUIBarButtonItem *item = nil;
    switch (kind) {
        case ANNBarButtonKindComponent:
            item = [self componentItemFor:button];
            break;
        case ANNBarButtonKindShowText:
            item = [self showTextItemFor:button color:color disabledColor:disabledColor side:side];
            break;
        case ANNBarButtonKindImageButton:
            item = [self imageButtonItemFor:button
                                      color:color
                              disabledColor:disabledColor
                                 background:ANNIconBackgroundOf(button, sideBackground)
                                       side:side];
            break;
        case ANNBarButtonKindSymbol:
        case ANNBarButtonKindImage:
        case ANNBarButtonKindText:
        case ANNBarButtonKindSystemItem:
            item = [self plainItemFor:button
                                 kind:kind
                                color:color
                        disabledColor:disabledColor
                                 side:side];
            break;
        case ANNBarButtonKindNone:
            break;
    }
    if (!item) {
        return nil;
    }
    item.buttonId = buttonId;
    item.enabled = enabled;
    NSString *testID = [button.testID withDefault:nil];
    item.accessibilityIdentifier = testID.length > 0 ? testID : nil;
    NSString *label = [button.accessibilityLabel withDefault:nil];
    if (label.length > 0) {
        item.accessibilityLabel = label;
    }
    [self applyBadgeOf:button to:item side:side];
    [self applyLayoutOf:button kind:kind to:item];
    return item;
}

- (ANNUIBarButtonItem *)plainItemFor:(ANNButtonOptions *)button
                                kind:(ANNBarButtonKind)kind
                               color:(UIColor *)color
                       disabledColor:(UIColor *)disabledColor
                                side:(ANNBarButtonsSide)side {
    ANNUIBarButtonItem *item = nil;
    NSString *text = ANNTextOf(button);
    switch (kind) {
        case ANNBarButtonKindSymbol: {
            NSString *symbol = ANNSymbolOf(button);
            UIImage *image = [UIImage systemImageNamed:symbol];
            // A symbol without a color is a template in the bar's color and dims when disabled; `disableIconTint` keeps
            // its own colors (multicolor).
            if ([button.disableIconTint withDefault:NO]) {
                UIImageSymbolConfiguration *multicolor =
                    [UIImageSymbolConfiguration configurationPreferringMulticolor];
                image = [[UIImage systemImageNamed:symbol withConfiguration:multicolor]
                    imageWithRenderingMode:UIImageRenderingModeAlwaysOriginal];
            }
            item = [[ANNUIBarButtonItem alloc] initWithImage:image
                                                       style:UIBarButtonItemStylePlain
                                                      target:nil
                                                      action:nil];
            break;
        }
        case ANNBarButtonKindImage: {
            // An image without a color as it is, with one tinted.
            UIImage *image = ANNImageOf(button);
            BOOL tinted = color && ![button.disableIconTint withDefault:NO];
            image = [image imageWithRenderingMode:tinted ? UIImageRenderingModeAlwaysTemplate
                                                         : UIImageRenderingModeAlwaysOriginal];
            item = [[ANNUIBarButtonItem alloc] initWithImage:image
                                                       style:UIBarButtonItemStylePlain
                                                      target:nil
                                                      action:nil];
            break;
        }
        case ANNBarButtonKindText:
            item = [[ANNUIBarButtonItem alloc] initWithTitle:text
                                                       style:UIBarButtonItemStylePlain
                                                      target:nil
                                                      action:nil];
            break;
        case ANNBarButtonKindSystemItem:
            // An unknown name is `done` (`close` is UIKit's close button).
            item = [[ANNUIBarButtonItem alloc]
                initWithBarButtonSystemItem:ANNSystemItemOf([button.systemItem withDefault:@""])
                                     target:nil
                                     action:nil];
            break;
        default:
            return nil;
    }
    // The icon button's text is its title: only the icon shows, the text in "•••", in the customization panel and for
    // VoiceOver.
    if (text && kind != ANNBarButtonKindText) {
        item.title = text;
    }
    BOOL prominent = [[button.style withDefault:nil] isEqualToString:@"prominent"];
    if (prominent) {
        if (@available(iOS 26.0, *)) {
            item.style = UIBarButtonItemStyleProminent;
        } else {
            item.style = UIBarButtonItemStyleDone;
        }
    }
    // The color: the tint, and for a plain button also the text's; `prominent` takes it only into the tint, and a font
    // only from the font options.
    if (color) {
        item.tintColor = color;
    }
    UIFont *font = [button.font fontWithPlaceFont:[UIFont systemFontOfSize:ANNBarButtonFontSize]];
    NSMutableDictionary<NSAttributedStringKey, id> *normal = [NSMutableDictionary new];
    NSMutableDictionary<NSAttributedStringKey, id> *disabled = [NSMutableDictionary new];
    if (font) {
        normal[NSFontAttributeName] = font;
        disabled[NSFontAttributeName] = font;
    }
    if (color && !prominent) {
        normal[NSForegroundColorAttributeName] = color;
    }
    if (disabledColor) {
        disabled[NSForegroundColorAttributeName] = disabledColor;
    }
    if (normal.count > 0) {
        [item setTitleTextAttributes:normal forState:UIControlStateNormal];
        [item setTitleTextAttributes:normal forState:UIControlStateHighlighted];
    }
    if (disabled.count > 0) {
        [item setTitleTextAttributes:disabled forState:UIControlStateDisabled];
    }
    if (disabledColor && ![button.enabled withDefault:YES] && kind != ANNBarButtonKindText) {
        item.tintColor = disabledColor;
    }
    [self wirePressOf:item button:button side:side];
    [self applyMenuOf:button to:item side:side];
    [self applyToggleOf:button to:item];
    return item;
}

// The image drawn on its background or with its insets, as the view of its own (no menu, toggle or style): two
// images, light and dark, the disabled one with the disabled color.
- (ANNUIBarButtonItem *)imageButtonItemFor:(ANNButtonOptions *)button
                                     color:(UIColor *)color
                             disabledColor:(UIColor *)disabledColor
                                background:(UIColor *)background
                                      side:(ANNBarButtonsSide)side {
    ANNIconBackgroundOptions *iconBackground = button.iconBackground;
    ANNIconInsetsOptions *insets = button.iconInsets;
    UIEdgeInsets padding =
        UIEdgeInsetsMake([insets.top withDefault:0], [insets.left withDefault:0],
                         [insets.bottom withDefault:0], [insets.right withDefault:0]);
    CGSize size =
        CGSizeMake([iconBackground.width withDefault:0], [iconBackground.height withDefault:0]);
    CGFloat radius = [iconBackground.cornerRadius withDefault:0];
    UIImage *icon = ANNImageOf(button);
    UIColor *tint = [button.disableIconTint withDefault:NO] ? nil : color;
    UIImage *normal = [ANNIconButtonImage imageWithIcon:icon
                                                   tint:tint
                                             background:background
                                           cornerRadius:radius
                                                   size:size
                                                 insets:padding];
    UIColor *disabledBackground = ANNColorOf(iconBackground.disabledColor) ?: background;
    UIImage *disabledImage = [ANNIconButtonImage imageWithIcon:icon
                                                          tint:disabledColor ?: tint
                                                    background:disabledBackground
                                                  cornerRadius:radius
                                                          size:size
                                                        insets:padding];
    UIButton *view = [UIButton buttonWithType:UIButtonTypeCustom];
    [view setImage:normal forState:UIControlStateNormal];
    [view setImage:disabledImage forState:UIControlStateDisabled];
    [view sizeToFit];
    view.enabled = [button.enabled withDefault:YES];
    NSString *testID = [button.testID withDefault:nil];
    view.accessibilityIdentifier = testID.length > 0 ? testID : nil;
    NSString *text = ANNTextOf(button);
    view.accessibilityLabel = [button.accessibilityLabel withDefault:nil] ?: text;
    ANNUIBarButtonItem *item = [[ANNUIBarButtonItem alloc] initWithCustomView:view];
    if (text) {
        item.title = text;
    }
    [view addTarget:self action:@selector(pressed:) forControlEvents:UIControlEventTouchUpInside];
    [self rememberPressOf:view button:button side:side item:item];
    return item;
}

- (ANNUIBarButtonItem *)showTextItemFor:(ANNButtonOptions *)button
                                  color:(UIColor *)color
                          disabledColor:(UIColor *)disabledColor
                                   side:(ANNBarButtonsSide)side {
    NSString *symbol = ANNSymbolOf(button);
    UIImage *image = symbol ? [UIImage systemImageNamed:symbol] : ANNImageOf(button);
    ANNShowTextButton *view = [ANNShowTextButton buttonWithOptions:button
                                                             image:image
                                                          isSymbol:symbol != nil
                                                             color:color
                                                     disabledColor:disabledColor];
    ANNUIBarButtonItem *item = [[ANNUIBarButtonItem alloc] initWithCustomView:view];
    NSString *text = ANNTextOf(button);
    item.title = text;
    item.image = image;
    [view addTarget:self action:@selector(pressed:) forControlEvents:UIControlEventTouchUpInside];
    [self rememberPressOf:view button:button side:side item:item];
    // In "•••" an item with its text and icon that presses.
    if (@available(iOS 16.0, *)) {
        __weak ANNBarButtons *weakSelf = self;
        __weak ANNShowTextButton *weakView = view;
        item.menuRepresentation = [UIAction actionWithTitle:text ?: @""
                                                      image:image
                                                 identifier:nil
                                                    handler:^(__kindof UIAction *action) {
                                                      [weakSelf pressed:weakView];
                                                    }];
    }
    return item;
}

// A React button: the item's custom view is the root's slot, kept by the component id. Its presses are its own: no
// event, no menu, toggle or style.
- (nullable ANNUIBarButtonItem *)componentItemFor:(ANNButtonOptions *)button {
    ANNBarButtonComponentView *slot = [self slotFor:button];
    if (!slot) {
        return nil;
    }
    [slot removeFromSuperview];
    ANNUIBarButtonItem *item = [[ANNUIBarButtonItem alloc] initWithCustomView:slot];
    item.componentId = slot.content.componentId;
    NSString *testID = [button.testID withDefault:nil];
    slot.accessibilityIdentifier = testID.length > 0 ? testID : nil;
    return item;
}

- (nullable ANNBarButtonComponentView *)slotFor:(ANNButtonOptions *)button {
    UIView<ANNComponentProtocol> *content = [_registry componentView:button.component
                                                                type:ANNComponentTypeTopBarButton];
    if (!content) {
        return nil;
    }
    NSString *componentId = content.componentId;
    ANNBarButtonComponentView *slot = _slots[componentId];
    if (!slot || slot.content != content) {
        slot = [[ANNBarButtonComponentView alloc] initWithContent:content];
        _slots[componentId] = slot;
    }
    return slot;
}

#pragma mark - The parts of a button

// iOS 26: a number, a short text, or `true` for the dot; `0`, `''`, `false` and `null` none. Not in the toolbar.
- (void)applyBadgeOf:(ANNButtonOptions *)button
                  to:(UIBarButtonItem *)item
                side:(ANNBarButtonsSide)side {
    if (@available(iOS 26.0, *)) {
        if (side == ANNBarButtonsSideToolbar) {
            return;
        }
        id value = [button.badge get];
        UIBarButtonItemBadge *badge = nil;
        if (ANNJSONIsBoolean(value)) {
            badge = [value boolValue] ? [UIBarButtonItemBadge indicatorBadge] : nil;
        } else if (ANNJSONIsNumber(value)) {
            NSInteger count = [value integerValue];
            badge = count > 0 ? [UIBarButtonItemBadge badgeWithCount:(NSUInteger)count] : nil;
        } else if ([value isKindOfClass:NSString.class] && [value length] > 0) {
            badge = [UIBarButtonItemBadge badgeWithString:value];
        }
        UIColor *badgeColor = ANNColorOf(button.badgeColor);
        UIColor *badgeTextColor = ANNColorOf(button.badgeTextColor);
        if (badge && badgeColor) {
            badge.backgroundColor = badgeColor;
        }
        if (badge && badgeTextColor) {
            badge.foregroundColor = badgeTextColor;
        }
        item.badge = badge;
    }
}

// The capsule (iOS 26), and the places of iOS 27 (`priority`) and 27.1 (`vertical`).
- (void)applyLayoutOf:(ANNButtonOptions *)button
                 kind:(ANNBarButtonKind)kind
                   to:(UIBarButtonItem *)item {
    if (@available(iOS 26.0, *)) {
        BOOL ownBackground =
            kind == ANNBarButtonKindComponent || kind == ANNBarButtonKindImageButton;
        item.hidesSharedBackground = [button.hideSharedBackground withDefault:ownBackground];
    }
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270000
    if (@available(iOS 27.0, *)) {
        NSString *priority = [button.priority withDefault:nil];
        if ([priority isEqualToString:@"high"]) {
            item.visibilityPriority = UIBarButtonItemVisibilityPriorityHigh;
        } else if ([priority isEqualToString:@"low"]) {
            item.visibilityPriority = UIBarButtonItemVisibilityPriorityLow;
        } else {
            item.visibilityPriority = UIBarButtonItemVisibilityPriorityStandard;
        }
    }
#endif
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
    if (@available(iOS 27.1, *)) {
        if (button.vertical.hasValue) {
            item.axisBehavior = button.vertical.get ? UIBarButtonItemAxisBehaviorVerticalPreferred
                                                    : UIBarButtonItemAxisBehaviorHorizontalOnly;
        } else {
            item.axisBehavior = UIBarButtonItemAxisBehaviorAutomatic;
        }
    }
#endif
}

// A menu: without `menuOnLongPress` the button has no action of its own and UIKit opens the menu on a tap; with it a
// tap is the button's action and a long press the menu.
- (void)applyMenuOf:(ANNButtonOptions *)button
                 to:(ANNUIBarButtonItem *)item
               side:(ANNBarButtonsSide)side {
    ANNMenuOptions *menu = button.menu;
    if (menu.items.count == 0) {
        return;
    }
    // The item gets its id only after it is built: the id comes from the button.
    NSString *buttonId = [button.identifier withDefault:@""];
    __weak ANNBarButtons *weakSelf = self;
    item.menu = [menu menuWithPickHandler:^(NSString *itemId) {
      [weakSelf menuItemPicked:itemId
          choiceKey:ANNButtonMenuChoiceKey(side, buttonId)
          menuOf:^ANNMenuOptions *(ANNNavigationOptions *options) {
            return ANNButtonWithId(ANNButtonsOfSide(options, side), buttonId).menu;
          }
          setMenu:^(ANNNavigationOptions *options, ANNMenuOptions *copy) {
            ANNChangeButton(options, side, buttonId, ^(ANNButtonOptions *changed) {
              changed.menu = copy;
            });
          }];
    }];
    if (![button.menuOnLongPress withDefault:NO]) {
        item.target = nil;
        item.action = nil;
    }
}

// `selected` shows "on"; a toggle given again without it keeps the state of the old button with its id. The item is
// still being built and has no id yet: the id comes from the button, and a button without one keeps no state.
- (void)applyToggleOf:(ANNButtonOptions *)button to:(ANNUIBarButtonItem *)item {
    NSString *buttonId = [button.identifier withDefault:nil];
    BOOL toggle = [button.toggle withDefault:NO] && buttonId.length > 0;
    BOOL selected = NO;
    if (button.selected.hasValue) {
        selected = button.selected.get;
    } else if (toggle && _selected[buttonId]) {
        selected = _selected[buttonId].boolValue;
    }
    if (toggle) {
        _selected[buttonId] = @(selected);
    }
    item.selected = selected;
}

- (void)wirePressOf:(ANNUIBarButtonItem *)item
             button:(ANNButtonOptions *)button
               side:(ANNBarButtonsSide)side {
    item.target = self;
    item.action = @selector(pressed:);
    [self rememberPressOf:item button:button side:side item:item];
}

- (void)rememberPressOf:(id)sender
                 button:(ANNButtonOptions *)button
                   side:(ANNBarButtonsSide)side
                   item:(ANNUIBarButtonItem *)item {
    ANNButtonPress *press = [ANNButtonPress new];
    press.buttonId = [button.identifier withDefault:@""];
    press.side = side;
    press.toggle = [button.toggle withDefault:NO] && sender == item;
    press.item = item;
    [_presses setObject:press forKey:sender];
}

#pragma mark - Presses

- (void)pressed:(id)sender {
    ANNButtonPress *press = [_presses objectForKey:sender];
    UIViewController *screen = _screen;
    NSString *componentId = screen.layoutInfo.componentId;
    if (!press || !screen || componentId.length == 0) {
        return;
    }
    if (!press.toggle) {
        [screen.eventEmitter sendOnNavigationButtonPressed:componentId buttonId:press.buttonId];
        return;
    }
    // A toggle flips, the user layer keeps the new state, then the event tells it.
    ANNUIBarButtonItem *item = press.item;
    BOOL selected = !item.selected;
    item.selected = selected;
    _selected[press.buttonId] = @(selected);
    NSString *buttonId = press.buttonId;
    ANNBarButtonsSide side = press.side;
    [screen setUserChoice:ANNToggleChoice(side, buttonId, selected)
                   forKey:ANNToggleChoiceKey(side, buttonId)];
    [screen.eventEmitter sendOnNavigationButtonPressed:componentId
                                              buttonId:buttonId
                                              selected:@(selected)];
}

+ (nullable ANNButtonOptions *)buttonWithId:(NSString *)buttonId
                                        in:(NSArray<ANNButtonOptions *> *)buttons {
    return ANNButtonWithId(buttons, buttonId);
}

#pragma mark - Menus

- (void)menuItemPicked:(NSString *)itemId
             choiceKey:(NSString *)choiceKey
                menuOf:(ANNMenuOptions *_Nullable (^)(ANNNavigationOptions *options))menuOf
               setMenu:(void (^)(ANNNavigationOptions *options, ANNMenuOptions *menu))setMenu {
    UIViewController *screen = _screen;
    NSString *componentId = screen.layoutInfo.componentId;
    if (!screen || componentId.length == 0) {
        return;
    }
    [screen.eventEmitter sendOnNavigationButtonPressed:componentId buttonId:itemId];
    ANNMenuOptions *menu = menuOf([screen resolveOptionsWithDefault]);
    if (!menu || ![ANNBarButtons menu:menu hasSingleSelectionOf:itemId]) {
        return;
    }
    // The new pick goes last; the earlier picks of its own list go, so a pick the app takes away later doesn't bring
    // back one the user replaced.
    NSArray<NSString *> *made = [screen userChoiceForKey:choiceKey].value ?: @[];
    NSArray<NSString *> *neighbors = ANNNeighborsOf(menu.items, itemId);
    NSMutableArray<NSString *> *picks = [NSMutableArray new];
    for (NSString *pick in made) {
        if (![neighbors containsObject:pick]) {
            [picks addObject:pick];
        }
    }
    [picks addObject:itemId];
    [screen setUserChoice:ANNMenuChoice(picks, menuOf, setMenu) forKey:choiceKey];
}

+ (BOOL)menu:(ANNMenuOptions *)menu hasSingleSelectionOf:(NSString *)itemId {
    return [self items:menu.items
        singleSelection:[menu.singleSelection withDefault:NO]
                contain:itemId];
}

+ (BOOL)items:(NSArray<ANNMenuItemOptions *> *)items
    singleSelection:(BOOL)singleSelection
            contain:(NSString *)itemId {
    for (ANNMenuItemOptions *item in items) {
        if (item.items.count > 0) {
            if ([self items:item.items
                    singleSelection:[item.singleSelection withDefault:NO]
                            contain:itemId]) {
                return YES;
            }
        } else if (singleSelection && [[item.identifier withDefault:nil] isEqualToString:itemId]) {
            return YES;
        }
    }
    return NO;
}

+ (ANNMenuOptions *)deepCopyOfMenu:(ANNMenuOptions *)menu {
    ANNMenuOptions *copy = [[ANNMenuOptions alloc] initWithDict:nil];
    copy.title = menu.title;
    copy.singleSelection = menu.singleSelection;
    copy.testID = menu.testID;
    copy.items = [self deepCopyOfItems:menu.items];
    return copy;
}

+ (NSArray<ANNMenuItemOptions *> *)deepCopyOfItems:(NSArray<ANNMenuItemOptions *> *)items {
    if (!items) {
        return nil;
    }
    NSMutableArray<ANNMenuItemOptions *> *copies = [NSMutableArray arrayWithCapacity:items.count];
    for (ANNMenuItemOptions *item in items) {
        ANNMenuItemOptions *copy = [[ANNMenuItemOptions alloc] initWithDict:nil];
        copy.identifier = item.identifier;
        copy.title = item.title;
        copy.sfSymbol = item.sfSymbol;
        copy.icon = item.icon;
        copy.destructive = item.destructive;
        copy.disabled = item.disabled;
        copy.checked = item.checked;
        copy.singleSelection = item.singleSelection;
        copy.testID = item.testID;
        copy.items = [self deepCopyOfItems:item.items];
        [copies addObject:copy];
    }
    return copies;
}

#pragma mark - React buttons

- (NSArray<UIView<ANNComponentProtocol> *> *)componentButtonsOf:(ANNNavigationOptions *)options {
    NSMutableArray<UIView<ANNComponentProtocol> *> *roots = [NSMutableArray new];
    ANNBarButtonsSide sides[] = {ANNBarButtonsSideLeft, ANNBarButtonsSideRight,
                                 ANNBarButtonsSideCenter, ANNBarButtonsSideToolbar};
    for (ANNBarButtonsSide side : sides) {
        if (side == ANNBarButtonsSideCenter) {
            if (@available(iOS 16.0, *)) {
            } else {
                continue;
            }
        }
        for (ANNButtonOptions *button in ANNButtonsOfSide(options, side) ?: @[]) {
            if ([button.identifier withDefault:nil].length == 0 || !ANNHasComponent(button)) {
                continue;
            }
            ANNBarButtonComponentView *slot = [self slotFor:button];
            if (slot) {
                [roots addObject:slot.content];
            }
        }
    }
    return roots;
}

- (void)keepSlotsOf:(NSSet<NSString *> *)componentIds {
    for (NSString *componentId in _slots.allKeys) {
        if (![componentIds containsObject:componentId]) {
            [_slots removeObjectForKey:componentId];
        }
    }
}

- (CGFloat)measuredWidthOf:(NSString *)componentId {
    return _slots[componentId].measuredWidth;
}

@end
