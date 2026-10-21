#import "ANNNodePresenter.h"

NS_ASSUME_NONNULL_BEGIN

/// The presenter of a split view (`splitView.*`): it turns `splitView.*` into the calls of the split view module's
/// category (`UISplitViewController+ANNOptions`).
///
/// When: at creation every field, `displayMode` and `inspector.visible` too, without animation; at every appearance
/// of a screen inside (`descendantWillAppear:`) and at the repaint of new default options, every field but
/// `displayMode` and `inspector.visible` (the user's choice lives until a merge); at a merge into the split view, or
/// one that came up from a screen inside, only the fields the merge names, `inspector.visible` animated. The values
/// come from the split view's resolved options with the defaults. A screen's merge that names `splitView` goes into the
/// split view's own options too, and the split view's parents get merges without `splitView`. No `testID` on iOS: UIKit
/// gives a column-style split view no button to put it on.
@interface ANNSplitViewPresenter : ANNNodePresenter
@end

NS_ASSUME_NONNULL_END
