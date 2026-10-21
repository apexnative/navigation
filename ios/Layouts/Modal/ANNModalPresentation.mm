#import "ANNModalPresentation.h"
#import "ANNAnimationRules.h"
#import "ANNModalSizePresenter.h"
#import "ANNPopoverPresenter.h"
#import "ANNSheetPresenter.h"
#import "ANNTransitionPlan.h"
#import "UIViewController+LayoutProtocol.h"

// UIKit's styles by the names of `modalPresentationStyle`.
static NSDictionary<NSString *, NSNumber *> *ANNPresentationStyles(void) {
    static NSDictionary<NSString *, NSNumber *> *styles;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      styles = @{
          @"pageSheet" : @(UIModalPresentationPageSheet),
          @"formSheet" : @(UIModalPresentationFormSheet),
          @"fullScreen" : @(UIModalPresentationFullScreen),
          @"overFullScreen" : @(UIModalPresentationOverFullScreen),
          @"overCurrentContext" : @(UIModalPresentationOverCurrentContext),
          @"currentContext" : @(UIModalPresentationCurrentContext),
          @"popover" : @(UIModalPresentationPopover),
      };
    });
    return styles;
}

// UIKit's transitions by the names of `modalTransitionStyle`.
static NSDictionary<NSString *, NSNumber *> *ANNTransitionStyles(void) {
    static NSDictionary<NSString *, NSNumber *> *styles;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      styles = @{
          @"coverVertical" : @(UIModalTransitionStyleCoverVertical),
          @"crossDissolve" : @(UIModalTransitionStyleCrossDissolve),
          @"flipHorizontal" : @(UIModalTransitionStyleFlipHorizontal),
          @"partialCurl" : @(UIModalTransitionStylePartialCurl),
      };
    });
    return styles;
}

@implementation ANNModalPresentation {
    // `animations.showModal.enter.waitForRender` of the show.
    Bool *_waitForRender;
}

- (instancetype)initWithOptions:(ANNNavigationOptions *)resolved animated:(NSNumber *)animated {
    if (self = [super init]) {
        NSString *styleName = [resolved.modalPresentationStyle withDefault:nil];
        if (styleName.length == 0) {
            _style = UIModalPresentationAutomatic;
        } else {
            NSNumber *style = ANNPresentationStyles()[styleName];
            _style = style ? (UIModalPresentationStyle)style.integerValue
                           : UIModalPresentationFullScreen;
        }
        NSString *transitionName = [resolved.modalTransitionStyle withDefault:nil];
        NSNumber *transition = transitionName ? ANNTransitionStyles()[transitionName] : nil;
        _transitionStyle = transition ? (UIModalTransitionStyle)transition.integerValue
                                      : UIModalTransitionStyleCoverVertical;
        ANNModalAnimationOptions *showModal = resolved.animations.showModal;
        if (animated) {
            _animated = animated.boolValue;
        } else {
            // `showModal.enter.enabled: false` turns the whole show's animation off.
            _animated = ![ANNAnimationRules sideIsDisabled:showModal.enter];
        }
        _isSheet = _style == UIModalPresentationPageSheet ||
                   _style == UIModalPresentationFormSheet ||
                   _style == UIModalPresentationAutomatic;
        // A custom show only off a sheet: a sheet keeps UIKit's sheet motion.
        if (_animated && !_isSheet && [ANNAnimationRules modalIsOwn:showModal]) {
            _showAnimation = showModal;
        }
        _waitForRender = showModal.enter.waitForRender;
    }
    return self;
}

- (ANNTransitionPlan *)showPlanOf:(UIViewController *)root
                            under:(UIViewController *)under
                        waitLimit:(NSTimeInterval)waitLimit {
    if (!_showAnimation) {
        return nil;
    }
    // The screen under a modal is in the transition only for a full-screen modal.
    _showPlan = [ANNTransitionPlan presentPlanWithAnimation:_showAnimation
                                              waitForRender:_waitForRender
                                                  waitLimit:waitLimit
                                          underInTransition:_style == UIModalPresentationFullScreen
                                                      under:under
                                                      modal:root];
    return _showPlan;
}

- (ANNTransitionPlan *)dismissPlanOf:(UIViewController *)root
                           animation:(ANNModalAnimationOptions *)animation
                               under:(UIViewController *)under {
    if (_isSheet) {
        return nil;
    }
    BOOL popover = _style == UIModalPresentationPopover;
    ANNTransitionPlan *plan =
        [ANNTransitionPlan dismissPlanWithAnimation:animation
                                  underInTransition:_style == UIModalPresentationFullScreen
                                              modal:root
                                              under:under];
    if (plan) {
        // A popover with nothing to play fades out over the length.
        plan.fadesWhenNothingPlays = popover;
        return plan;
    }
    // Shown with its own animation and closed without: a full screen closes as UIKit does it, a popover fades over the
    // show's length; the show's `exit` (for the screen under) never plays on it.
    if (popover && _showPlan && _showPlan.length > 0) {
        return [ANNTransitionPlan fadeOutPlanOfModal:root under:under length:_showPlan.length];
    }
    return nil;
}

// UIKit takes `partialCurl` only for a full-screen modal over a controller that fills the screen, and a controller
// shown with `partialCurl` presents nothing more: the command rejects before it asks UIKit.
- (NSString *)problemPresentingFrom:(UIViewController *)presenting
                         windowRoot:(UIViewController *)windowRoot {
    if (presenting.presentingViewController &&
        presenting.modalTransitionStyle == UIModalTransitionStylePartialCurl) {
        return @"partialCurl needs a full screen modal";
    }
    if (_transitionStyle != UIModalTransitionStylePartialCurl) {
        return nil;
    }
    BOOL presentingFills = presenting == windowRoot ||
                           presenting.modalPresentationStyle == UIModalPresentationFullScreen;
    if (_style != UIModalPresentationFullScreen || !presentingFills) {
        return @"partialCurl needs a full screen modal";
    }
    return nil;
}

- (void)prepare:(UIViewController *)root
             options:(ANNNavigationOptions *)resolved
            delegate:(id<UIAdaptivePresentationControllerDelegate>)delegate
    presentingLayout:(UIViewController *)presentingLayout {
    root.modalPresentationStyle = _style;
    root.modalTransitionStyle = _transitionStyle;
    // The presentation controller is made for the style just set; the delegate hears the user's closes, the tries
    // on a held modal and the detents the finger chose.
    root.presentationController.delegate = delegate;
    ANNModalOptions *modal = resolved.modal;
    BOOL popover = _style == UIModalPresentationPopover;
    if (popover && ![modal.popover.adaptive withDefault:YES]) {
        [ANNPopoverPresenter keepPopoverOf:root delegate:delegate];
    }
    if (_isSheet) {
        [ANNSheetPresenter applyOptions:modal.sheet toModal:root];
    }
    [ANNModalSizePresenter applyOptions:modal toModal:root];
    if (popover) {
        [ANNPopoverPresenter anchorPopover:root.popoverPresentationController
                                   options:modal.popover
                                presenting:presentingLayout];
    }
    [ANNModalPresentation applyHoldTo:root];
    [ANNModalPresentation applyTestIDTo:root sheet:_isSheet];
}

+ (void)applyHoldTo:(UIViewController *)root {
    BOOL held = ![[root resolveOptionsWithDefault].modal.swipeToDismiss withDefault:YES];
    if (root.modalInPresentation != held) {
        root.modalInPresentation = held;
    }
}

+ (void)applyTestIDTo:(UIViewController *)root sheet:(BOOL)isSheet {
    if (!root.isViewLoaded) {
        return;
    }
    ANNModalOptions *modal = [root resolveOptionsWithDefault].modal;
    NSString *testID = isSheet ? [modal.sheet.testID withDefault:nil] : nil;
    if (testID.length == 0) {
        testID = [modal.testID withDefault:nil];
    }
    NSString *identifier = testID.length > 0 ? testID : nil;
    if (![root.view.accessibilityIdentifier ?: @"" isEqualToString:identifier ?: @""]) {
        root.view.accessibilityIdentifier = identifier;
    }
}

@end
