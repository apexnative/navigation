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

/// The screen went: the hosted surface stops as soon as it runs, on the main queue. A surface that runs already stops
/// at once; one that doesn't run yet stops at its change to running (`surface:didChangeStage:`). Until then this view
/// and its surface are kept, so the core gives them up on the main queue. A surface that hasn't started after 5 s is
/// let go, and stops if it ever starts later. Once; main queue.
- (void)stopSurfaceWhenRunning;

@end

NS_ASSUME_NONNULL_END
