#import "ANNNavigationOptions.h"
#import "ANNReactComponentRegistry.h"
#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Where the buttons of a list go.
typedef NS_ENUM(NSInteger, ANNBarButtonsSide) {
    ANNBarButtonsSideLeft,
    ANNBarButtonsSideRight,
    ANNBarButtonsSideCenter,
    ANNBarButtonsSideToolbar,
};

/// The buttons of one screen's bar and toolbar (`topBar.leftButtons[].*`, the side options, `overflowMenu`,
/// `toolbar.buttons`): built from the resolved options, whole, at the screen's creation, at each appearance and at a
/// merge of a side. Each button is an `ANNUIBarButtonItem` of the first kind its fields give (React view → icon and
/// text → image button → SF Symbol → image → text → system item); a button without `id` is not placed. Side colors go
/// onto what is built, never into the options; what the user chose (a toggle, a menu's check) goes into the screen's
/// user layer. The React buttons' slots are kept by component id, so a button built again reuses its slot. Main queue.
@interface ANNBarButtons : NSObject

- (instancetype)initWithScreen:(UIViewController *)screen
                      registry:(ANNReactComponentRegistry *)registry;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

/// Sets `side` of the screen's item (left, right, center) from `options`; `animated` fades new buttons in (a side set
/// as groups changes at once).
- (void)applySide:(ANNBarButtonsSide)side
          options:(ANNNavigationOptions *)options
         animated:(BOOL)animated;

/// `topBar.overflowMenu` into the item's `additionalOverflowItems`, built from the screen's resolved options at
/// each open (iOS 16+).
- (void)applyOverflowMenu:(ANNNavigationOptions *)options;

/// The items of `toolbar.buttons`: buttons, spaces and the search field.
- (NSArray<UIBarButtonItem *> *)toolbarItemsWithOptions:(ANNNavigationOptions *)options;

/// The roots of the React buttons `options` name (with an `id`, on every side and the toolbar), made now when
/// missing, each in its slot.
- (NSArray<UIView<ANNComponentProtocol> *> *)componentButtonsOf:(ANNNavigationOptions *)options;

/// The slots of roots that went (`componentIds` are those still named).
- (void)keepSlotsOf:(NSSet<NSString *> *)componentIds;

/// The width the slot of a React button measured; 0 before it rendered.
- (CGFloat)measuredWidthOf:(NSString *)componentId;

/// A menu item `itemId` was picked: `ANN.NavigationButtonPressed` with its id, then, in a single-selection list, the
/// pick kept in the screen's user layer under `choiceKey`, one choice per menu. The resolution plays the picks again on
/// a copy of the menu it has. `menuOf` gives the menu in `options` (a button's, the title's, the overflow menu),
/// `setMenu` puts a changed copy there. A merge into the screen takes a pick away when the screen's resolution after it
/// has no such item, or when it gives the menu with other checks in the pick's level than the app had there, as on
/// Android. The two blocks capture no presenter: the node keeps them with the choice.
- (void)menuItemPicked:(NSString *)itemId
             choiceKey:(NSString *)choiceKey
                menuOf:(ANNMenuOptions *_Nullable (^)(ANNNavigationOptions *options))menuOf
               setMenu:(void (^)(ANNNavigationOptions *options, ANNMenuOptions *menu))setMenu;

/// A copy of `menu` that shares no item with it (a new `ANNMenuOptions` and a new `ANNMenuItemOptions` per item, at
/// any depth).
+ (ANNMenuOptions *)deepCopyOfMenu:(ANNMenuOptions *)menu;

@end

NS_ASSUME_NONNULL_END
