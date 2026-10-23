#import <Foundation/Foundation.h>

NS_ASSUME_NONNULL_BEGIN

/// The navigation state JavaScript reads with `navigation.getState()`: `ANN.NavigationStateChanged { screen, tab }`
/// (EVT-NavigationStateChanged).
///
/// What: the visible layout of the navigation's window — the top modal of the library, else the root; overlays and
/// presentations that are not layout (alerts, the share sheet, a search controller, React Native's `<Modal>`) don't
/// count — then down the current children to the end. `screen` is that node when it is a screen, its name without the
/// `slot:` prefix (a native screen: its registration name); `tab` is the selected index of the nearest tabs the
/// screen is in (with tabs inside tabs the inner ones), `null` out of tabs. When: only when it differs from the last
/// one sent. Main queue.
@interface ANNNavigationState : NSObject

/// One update in the next pass of the main loop, however many are asked in this one: after a `DidAppear` or a
/// `DidDisappear` that went, after `ANN.BottomTabSelected`.
+ (void)setNeedsUpdate;

/// The coalesced updates (`setNeedsUpdate`) wait while a command holds them: a `setRoot` that shows its layout's
/// modals and overlays (one state, before its answer), a user's close whose end waits for UIKit. Each hold is released
/// once; the last release asks for the update that waited. `updateNow` is never held.
+ (void)holdUpdates;
+ (void)releaseUpdates;

/// The state now, sent when it changed. A command's answer asks for it once (`-[ANNCommandAnswer reportStateNow]`, at
/// the end of its transition or before its promise); the end of a user's transition and a tab chosen from code ask
/// `updateNow`. A walk that throws logs one line, `[ApexNavigation] <commandName>: could not report the navigation
/// state: <reason>` (as on Android; `transition` without a command), and sends nothing, so its caller always goes on.
+ (void)updateNowFor:(nullable NSString *)commandName;

/// `updateNowFor:` without a command.
+ (void)updateNow;

/// Forgets the last state sent: the next one goes even if it is the same (a new bundle, `AppLaunched`).
+ (void)reset;

@end

NS_ASSUME_NONNULL_END
