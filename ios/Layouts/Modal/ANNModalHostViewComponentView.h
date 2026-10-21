#import <React/RCTModalHostViewComponentView.h>

/**
 * React Native's `<Modal>` under navigation: it is presented by the screen it is on, and when a
 * navigation modal covers that screen UIKit refuses to present it. This one is presented by the
 * view controller on top, as React Native lets a subclass do.
 */
@interface ANNModalHostViewComponentView : RCTModalHostViewComponentView

/** Makes `<Modal>` use this class; before the first `<Modal>` mounts. */
+ (void)registerForModalHostView;

@end
