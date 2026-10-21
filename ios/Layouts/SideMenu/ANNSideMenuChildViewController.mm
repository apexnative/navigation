#import "ANNSideMenuChildViewController.h"
#import "ANNShownScreens.h"

@implementation ANNSideMenuChildViewController

// The child is the wrapper's UIKit child from the start, so that it finds the side menu through the wrapper before
// any view loads; its view comes in with the wrapper's.
- (instancetype)initWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                           creator:(id<ANNComponentViewCreator>)creator
                           options:(ANNNavigationOptions *)options
                    defaultOptions:(ANNNavigationOptions *)defaultOptions
                         presenter:(ANNNodePresenter *)presenter
                      eventEmitter:(ANNEventEmitter *)eventEmitter
                             child:(UIViewController *)child
                              type:(ANNSideMenuChildType)type {
    self = [super initWithNibName:nil bundle:nil];
    _child = child;
    _type = type;
    [self.nodeHelper setUpWithLayoutInfo:layoutInfo
                                 creator:creator
                                 options:options
                          defaultOptions:defaultOptions
                               presenter:presenter
                            eventEmitter:eventEmitter];
    [self addChildViewController:child];
    return self;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    UIView *view = _child.view;
    view.frame = self.view.bounds;
    view.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    [self.view addSubview:view];
    [_child didMoveToParentViewController:self];
    self.view.accessibilityIdentifier = _testID;
}

- (void)setTestID:(NSString *)testID {
    _testID = [testID copy];
    self.viewIfLoaded.accessibilityIdentifier = _testID;
}

#pragma mark - Appearance

// A menu shows or hides at the end of its motion, also with the side menu: a modal's hold follows the screen the
// modal shows now, and the system UI follows the open menu or the center. Asking again changes nothing when nothing
// changed. The side menu asks UIKit for its own controller itself; the owner adds the orientation it doesn't ask, and
// the overlays.
- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    if (_type != ANNSideMenuChildTypeCenter) {
        [ANNShownScreens changedIn:self];
    }
}

- (void)viewDidDisappear:(BOOL)animated {
    [super viewDidDisappear:animated];
    if (_type != ANNSideMenuChildTypeCenter) {
        [ANNShownScreens changedIn:self];
    }
}

#pragma mark - ANNLayoutProtocol

- (UIViewController *)getCurrentChild {
    return _child;
}

- (NSArray<UIViewController *> *)layoutChildren {
    return @[ _child ];
}

// The center through the common render of its side menu, a menu through the side menu's own.
- (void)render {
    [_child render];
}

// A merge inside the part: the side menu takes its part, then the merge goes on to the side menu's parent without
// `sideMenu`, so that only the nearest side menu acts on it.
- (void)mergeChildOptions:(ANNNavigationOptions *)options child:(UIViewController *)child {
    UIViewController *sideMenu = self.layoutParent;
    if (!sideMenu) {
        return;
    }
    ANNNodePresenter *presenter = sideMenu.presenter;
    [presenter mergeChildOptions:options];
    [sideMenu.layoutParent mergeChildOptions:presenter ? [presenter optionsForParent:options] : options
                                       child:sideMenu];
}

- (UIViewController *)systemUIChild {
    return _child;
}

#pragma mark - UIViewController

// The side menu answers the status bar itself from its resolved options; the rest is the screen's.
- (UIViewController *)childViewControllerForHomeIndicatorAutoHidden {
    return _child;
}

- (UIViewController *)childViewControllerForScreenEdgesDeferringSystemGestures {
    return _child;
}

#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270100
- (UIViewController *)childViewControllerForPreferredVerticalBarBehavior API_AVAILABLE(ios(27.1)) {
    return _child;
}
#endif

@end
