#import "ANNOptions.h"

// An item of a button menu: an action with an id, or a submenu when it has items of its own.
@interface ANNMenuItemOptions : ANNOptions

@property(nonatomic, strong) Text *identifier;
@property(nonatomic, strong) Text *title;
@property(nonatomic, strong) Text *sfSymbol;
@property(nonatomic, strong) Image *icon;
@property(nonatomic, strong) Bool *destructive;
@property(nonatomic, strong) Bool *disabled;
@property(nonatomic, strong) Bool *checked;
// A submenu: its items, and whether one of them is checked at a time.
@property(nonatomic, strong) NSArray<ANNMenuItemOptions *> *items;
@property(nonatomic, strong) Bool *singleSelection;
@property(nonatomic, strong) Text *testID;

@end

// The menu of a top bar or toolbar button.
@interface ANNMenuOptions : ANNOptions

@property(nonatomic, strong) Text *title;
@property(nonatomic, strong) Bool *singleSelection;
@property(nonatomic, strong) NSArray<ANNMenuItemOptions *> *items;
@property(nonatomic, strong) Text *testID;

- (BOOL)hasValue;

// The item with this id was picked: in a single selection menu it gets the check and its neighbors lose it.
- (void)itemPicked:(NSString *)itemId;

// The UIMenu of these options: an action for each item, a submenu for an item with items. A picked action calls onPick
// with the id of its item.
- (UIMenu *)menuWithPickHandler:(void (^)(NSString *itemId))onPick;

@end
