#import "ANNExternalViewController.h"
#import "ANNAppearanceDispatcher.h"
#import "ANNComponentPresenter.h"
#import "ANNTopBarPresenter.h"

@implementation ANNExternalViewController {
    ANNScreenAppearance *_ownAppearance;
}

// A native screen has no React content: its own record waits for nothing, and its name in the events is its
// registration name. It reports through the node's emitter, the JavaScript it was made for.
- (instancetype)initWithLayoutInfo:(ANNLayoutInfo *)layoutInfo
                           creator:(id<ANNComponentViewCreator>)creator
                           options:(ANNNavigationOptions *)options
                    defaultOptions:(ANNNavigationOptions *)defaultOptions
                         presenter:(ANNNodePresenter *)presenter
                      eventEmitter:(ANNEventEmitter *)eventEmitter
              childViewControllers:(NSArray<UIViewController *> *)childViewControllers {
    self = [super initWithLayoutInfo:layoutInfo
                             creator:creator
                             options:options
                      defaultOptions:defaultOptions
                           presenter:presenter
                        eventEmitter:eventEmitter
                childViewControllers:childViewControllers];
    if (self) {
        _ownAppearance = [[ANNScreenAppearance alloc] initWithComponentId:layoutInfo.componentId
                                                            componentName:layoutInfo.name ?: @""
                                                            componentType:ANNComponentTypeComponent
                                                                  content:nil
                                                             eventEmitter:self.eventEmitter];
    }
    return self;
}

- (void)embedNativeViewController:(UIViewController *)nativeViewController
                          context:(ANNNativeScreenContext *)context {
    if (_nativeViewController) {
        return;
    }
    _nativeScreenContext = context;
    _nativeViewController = nativeViewController;
    [self addChildViewController:nativeViewController];
    if (self.isViewLoaded) {
        [self addNativeView];
    }
    [nativeViewController didMoveToParentViewController:self];
}

// The app's view fills the screen and follows its size: rotation, a split view, and a `UIHostingController`, whose
// view has no size before its first layout.
- (void)addNativeView {
    UIView *nativeView = _nativeViewController.view;
    if (nativeView.superview == self.view) {
        return;
    }
    nativeView.frame = self.view.bounds;
    nativeView.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    [self.view addSubview:nativeView];
    // `statusBar.blur` stays over the app's view, as over a React screen's content.
    if ([self.presenter isKindOfClass:ANNComponentPresenter.class]) {
        [(ANNComponentPresenter *)self.presenter keepStatusBarBlurOnTop];
    }
}

- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    UIView *nativeView = _nativeViewController.view;
    if (nativeView.superview == self.view && !CGRectEqualToRect(nativeView.frame, self.view.bounds)) {
        nativeView.frame = self.view.bounds;
    }
}

// The bar shows the app's view controller's item: the options of the bar go into it.
- (UINavigationItem *)navigationItem {
    return _nativeViewController.navigationItem ?: [super navigationItem];
}

- (ANNScreenAppearance *)appearance {
    return _ownAppearance;
}

// The bar's moments as a React screen's (the search, the large title, the bar components; correction 11).
- (void)screenWillAppear {
    [self.presenter applyOptions:[self resolveOptionsWithDefault]];
    [ANNAppearanceDispatcher willAppear:_ownAppearance];
    [[ANNTopBarPresenter presenterOfScreen:self] screenWillAppear];
}

- (void)screenDidAppear {
    [ANNAppearanceDispatcher didAppear:_ownAppearance];
    [[ANNTopBarPresenter presenterOfScreen:self] screenDidAppear];
}

- (void)screenDidDisappear {
    [ANNAppearanceDispatcher didDisappear:_ownAppearance];
    [[ANNTopBarPresenter presenterOfScreen:self] screenDidDisappear];
}

// No React content to make: the app's view goes in with the view, and the screen is ready at once.
- (void)render {
    if (self.nodeHelper.destroyed) {
        return;
    }
    UIView *view = self.view;
    if (_nativeViewController && view) {
        [self addNativeView];
    }
    [self readyForPresentation];
}

// The screen's destroy, then JavaScript lets go of its hook and functions, once however the screen went.
- (void)destroy {
    [super destroy];
    [ANNAppearanceDispatcher nativeScreenReleased:self];
}

@end
