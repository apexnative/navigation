#import <React/RCTSurfaceHostingView.h>

NS_ASSUME_NONNULL_BEGIN

/// The view that hosts the React surface of a screen or component the core makes (`ANNReactView`). React Native takes
/// the traits of any hosting view as the app's color scheme (`RCTAppearance`); this view passes a trait change on only
/// when its interface style is its window's, so the app's theme follows the window alone. Without a window it passes
/// every change on.
@interface ANNSurfaceHostingView : RCTSurfaceHostingView

/// Called at each mount of the hosted surface: after a change of the surface's size, the first one lays the surface
/// out again, so a render that was on its way during the change doesn't keep the old size's layout.
- (void)surfaceDidMount;

@end

NS_ASSUME_NONNULL_END
