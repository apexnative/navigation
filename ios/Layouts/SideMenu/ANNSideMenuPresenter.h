#import "ANNNodePresenter.h"

NS_ASSUME_NONNULL_BEGIN

/// The presenter of a side menu: it turns `sideMenu.*` into the calls of the side menu module
/// (`ANNSideMenuViewController`), with the module's background and the `testID`s of its parts.
///
/// When: at creation, and at every appearance of a screen inside (`descendantWillAppear:`), every field with its
/// default (`enabled` true, `width` 280, `openMode` `aboveContent`, `shouldStretchDrawer` true, `animationVelocity`
/// 840, `glass` false, `openGestureMode` `bezel`, no `animationType`); at a merge into the side menu or into a node
/// inside it, the fields the merge names. The values always come from the side menu's resolution at that moment (its
/// own options under the current part's chain, the center's `sideMenu` over an open menu, the defaults under), read
/// without the cache, so that a merge off the visible chain changes nothing until its screen appears. `visible` with
/// `animate` acts only from a merge, last, and from the layout at the first show: resolved options never open or close
/// a menu.
@interface ANNSideMenuPresenter : ANNNodePresenter

/// The layout's `sideMenu.left.visible` / `sideMenu.right.visible`, read before the node's own options drop them: a
/// menu with `true` opens without animation at the first appearance of a screen inside.
- (void)setVisibilityAtFirstShow:(ANNSideMenuOptions *)sideMenu;

/// A node inside the side menu merged `mergeOptions` (the override of `ANNNodePresenter`'s hook): the fields the merge
/// names, then its `visible`.
- (void)mergeChildOptions:(ANNNavigationOptions *)mergeOptions;

@end

NS_ASSUME_NONNULL_END
