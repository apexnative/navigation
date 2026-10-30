#import "ANNSearchResultsViewController.h"
#import "ANNScreenEnvironment.h"
#import "ANNScreenSettling.h"

@implementation ANNSearchResultsViewController {
    NSString *_componentId;
    ANNEventEmitter *_eventEmitter;
}

- (instancetype)initWithReactView:(ANNReactView *)reactView
                      componentId:(NSString *)componentId
                     eventEmitter:(ANNEventEmitter *)eventEmitter {
    self = [super initWithNibName:nil bundle:nil];
    _reactView = reactView;
    _componentId = [componentId copy];
    _eventEmitter = eventEmitter;
    [NSNotificationCenter.defaultCenter addObserver:self
                                           selector:@selector(screenSettlingDidEnd:)
                                               name:ANNScreenSettlingDidEndNotification
                                             object:nil];
    return self;
}

- (void)viewDidLoad {
    [super viewDidLoad];
    self.view.backgroundColor = UIColor.systemBackgroundColor;
    _reactView.backgroundColor = UIColor.clearColor;
    [self.view addSubview:_reactView];
}

// The component covers the whole screen of results, so its background fills it: laid out inside the safe area, it left
// the system background under the bar and at the bottom. It keeps its content clear of them itself, as a list does
// with contentInsetAdjustmentBehavior, or with useSafeAreaInsets; its safe area view does not pad it.
- (void)viewDidLayoutSubviews {
    [super viewDidLayoutSubviews];
    _reactView.frame = self.view.bounds;
    [self updateInsets];
}

// Not while the geometry settles: the steps of a fold would reach JavaScript one by one, and its end sends them. The
// first values go at once: JavaScript reads them as it renders the results.
- (void)updateInsets {
    if (!_componentId) {
        return;
    }
    if (!ANNScreenSettling.isSettling || ![ANNScreenEnvironment insetsForScreenId:_componentId]) {
        [ANNScreenEnvironment setInsets:self.view.safeAreaInsets
                                padding:UIEdgeInsetsZero
                            forScreenId:_componentId
                           eventEmitter:_eventEmitter];
    }
}

// Only results that show, for the JavaScript they were made for, and while their record is kept: the search bar's
// removal removed it, and hidden results send theirs as they lay out again.
- (void)screenSettlingDidEnd:(NSNotification *)notification {
    if (_componentId && self.viewIfLoaded.window && _eventEmitter.isCurrent &&
        [ANNScreenEnvironment insetsForScreenId:_componentId]) {
        [self updateInsets];
    }
}

// The component gets the lifecycle events of a screen each time the results show and hide.
- (void)viewWillAppear:(BOOL)animated {
    [super viewWillAppear:animated];
    [_reactView componentWillAppear];
}

- (void)viewDidAppear:(BOOL)animated {
    [super viewDidAppear:animated];
    [_reactView componentDidAppear];
}

- (void)viewDidDisappear:(BOOL)animated {
    [super viewDidDisappear:animated];
    [_reactView componentDidDisappear];
}

@end
