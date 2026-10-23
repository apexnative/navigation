#import "ANNNavigationOptions.h"
#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// The one rule of where a screen sits against the bars (in one place) and the one check of the large title: pure
/// functions of a screen's resolved options (its own over those of its stack, tabs and side menu, then the defaults).
/// Every place that decides by these asks here.

/// `topBar.largeTitle.displayMode` as UIKit gets it: `displayMode` decides over `visible` (`true` is `always`, `false`
/// and unset are `never`); `inline` before iOS 17 is `always`. One of `always`, `never`, `automatic`, `inline`.
FOUNDATION_EXPORT NSString *ANNLargeTitleDisplayMode(ANNNavigationOptions *options);

/// A large title can show: its display mode isn't `never`.
FOUNDATION_EXPORT BOOL ANNLargeTitleCanShow(ANNNavigationOptions *options);

/// The screen goes under the top bar when any of these holds: `topBar.drawBehind`, `topBar.background.translucent`,
/// `topBar.visible: false`, a large title can show, `topBar.hideOnScroll`, `topBar.searchBar.hideOnScroll`. An explicit
/// `drawBehind: false` keeps none of the others out.
FOUNDATION_EXPORT BOOL ANNScreenIsUnderTopBar(ANNNavigationOptions *options);

/// The screen goes under the tab bar when any of these holds: `bottomTabs.drawBehind` (YES by default with the glass bar
/// of iOS 26, NO before and in the compatibility mode), `bottomTabs.translucent`, `bottomTabs.visible: false`.
FOUNDATION_EXPORT BOOL ANNScreenIsUnderTabBar(ANNNavigationOptions *options);

/// Whether `options` (a merge) sets any key the rule reads: the screen applies the rule again then.
FOUNDATION_EXPORT BOOL ANNOptionsChangeScreenBars(ANNNavigationOptions *options);

NS_ASSUME_NONNULL_END
