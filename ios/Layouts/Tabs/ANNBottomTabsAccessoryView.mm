#import "ANNBottomTabsAccessoryView.h"

@implementation ANNBottomTabsAccessoryView {
    ANNEventEmitter *_eventEmitter;
    NSString *_placement;
}

- (instancetype)initWithReactView:(ANNReactView *)reactView
                     eventEmitter:(ANNEventEmitter *)eventEmitter {
    self = [super initWithFrame:CGRectZero];
    _reactView = reactView;
    _eventEmitter = eventEmitter;
    [self addSubview:reactView];
    if (@available(iOS 26.0, *)) {
        [self registerForTraitChanges:@[ UITraitTabAccessoryEnvironment.class ]
                           withAction:@selector(tabAccessoryEnvironmentDidChange)];
    }
    return self;
}

// UIKit resizes the accessory inside its animation as the tab bar minimizes and expands. React
// Native lays out and mounts its views right away there, so their frames animated with it and
// text drawn for the new size stretched: the component takes the new size outside the animation.
- (void)layoutSubviews {
    [super layoutSubviews];
    if (CGRectEqualToRect(_reactView.frame, self.bounds)) {
        return;
    }
    [UIView performWithoutAnimation:^{
      self->_reactView.frame = self.bounds;
      [self->_reactView layoutIfNeeded];
    }];
}

// UIKit sets the environment when the accessory is shown and changes it as the tab bar minimizes
// and expands.
- (void)tabAccessoryEnvironmentDidChange API_AVAILABLE(ios(26.0)) {
    NSString *placement = nil;
    switch (self.traitCollection.tabAccessoryEnvironment) {
    case UITabAccessoryEnvironmentRegular:
        placement = @"regular";
        break;
    case UITabAccessoryEnvironmentInline:
        placement = @"inline";
        break;
    default:
        break;
    }
    if (!placement || [placement isEqualToString:_placement]) {
        return;
    }
    _placement = placement;
    [_eventEmitter sendBottomAccessoryPlacementChanged:_reactView.componentId placement:placement];
}

@end
