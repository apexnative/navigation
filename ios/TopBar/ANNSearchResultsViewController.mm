#import "ANNSearchResultsViewController.h"
#import "ANNScreenInsets.h"

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
    if (_componentId) {
        [ANNScreenInsets setInsets:self.view.safeAreaInsets
                           padding:UIEdgeInsetsZero
                       forScreenId:_componentId
                      eventEmitter:_eventEmitter];
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
