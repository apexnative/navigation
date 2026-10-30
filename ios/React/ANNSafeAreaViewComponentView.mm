#import "ANNSafeAreaViewComponentView.h"
#import "ANNScreenEnvironment.h"
#import "ANNScreenReadiness.h"
#import "../cpp/react/renderer/components/ApexNavigationSpec/ANNSafeAreaViewComponentDescriptor.h"
#import <React/RCTConversions.h>

using namespace facebook::react;

@interface ANNSafeAreaViewComponentView () <ANNScreenPaddingListener>
@end

@implementation ANNSafeAreaViewComponentView {
    ANNSafeAreaViewShadowNode::ConcreteState::Shared _state;
    NSString *_screenId;
    BOOL _readySent;
    // The screen heard that its content rendered.
    BOOL _contentRenderedSent;
}

+ (ComponentDescriptorProvider)componentDescriptorProvider {
    return concreteComponentDescriptorProvider<ANNSafeAreaViewComponentDescriptor>();
}

- (instancetype)initWithFrame:(CGRect)frame {
    if (self = [super initWithFrame:frame]) {
        static const auto defaultProps = std::make_shared<const ANNSafeAreaViewProps>();
        _props = defaultProps;
    }
    return self;
}

- (void)updateProps:(const Props::Shared &)props oldProps:(const Props::Shared &)oldProps {
    const auto &newProps = static_cast<const ANNSafeAreaViewProps &>(*props);
    [self listenToScreenId:RCTNSStringFromStringNilIfEmpty(newProps.screenId)];
    // The screen's readiness comes from this view, never from its root's children.
    if (_screenId != nil) {
        [ANNScreenReadiness screenHasSafeAreaView:_screenId];
    }
    _contentRendered = newProps.contentRendered;
    [super updateProps:props oldProps:oldProps];
    [self applyPadding];
    if (_contentRendered) {
        [self sendContentRendered];
    }
}

// Without the flag (a wrapper that doesn't give it), the first children mounted tell that the content rendered.
- (void)mountChildComponentView:(UIView<RCTComponentViewProtocol> *)childComponentView
                          index:(NSInteger)index {
    [super mountChildComponentView:childComponentView index:index];
    [self sendContentRendered];
}

// The screen's container waits for it: once, by the screen's id.
- (void)sendContentRendered {
    if (_contentRenderedSent || _screenId == nil) {
        return;
    }
    _contentRenderedSent = YES;
    [ANNScreenReadiness screenContentDidRender:_screenId];
}

- (void)updateState:(const State::Shared &)state oldState:(const State::Shared &)oldState {
    _state = std::static_pointer_cast<const ANNSafeAreaViewShadowNode::ConcreteState>(state);
    [self applyPadding];
}

// The view hears the padding changes of its screen only. It listens before it applies the padding it has, at each
// props, so a change between the two can't be missed.
- (void)listenToScreenId:(nullable NSString *)screenId {
    if ((screenId == nil && _screenId == nil) || [screenId isEqualToString:_screenId]) {
        return;
    }
    if (_screenId != nil) {
        [ANNScreenEnvironment removePaddingListener:self forScreenId:_screenId];
    }
    _screenId = screenId;
    if (screenId != nil) {
        [ANNScreenEnvironment addPaddingListener:self forScreenId:screenId];
    }
}

#pragma mark - ANNScreenPaddingListener

- (void)screenPaddingDidChange {
    [self applyPadding];
}

// The padding of the screen goes to the state, which lays the view out with it.
- (void)applyPadding {
    if (_state == nullptr || _screenId == nil) {
        return;
    }
    UIEdgeInsets padding = [ANNScreenEnvironment paddingForScreenId:_screenId];
    EdgeInsets insets{.left = (Float)padding.left,
                      .top = (Float)padding.top,
                      .right = (Float)padding.right,
                      .bottom = (Float)padding.bottom};
    if (insets != _state->getData().padding) {
        _state->updateState(ANNSafeAreaViewState{insets});
    }
}

- (void)updateEventEmitter:(const EventEmitter::Shared &)eventEmitter {
    [super updateEventEmitter:eventEmitter];
    [self sendReadyIfLaidOut];
}

- (void)didMoveToSuperview {
    [super didMoveToSuperview];
    [self sendReadyIfLaidOut];
}

- (void)didMoveToWindow {
    [super didMoveToWindow];
    [self sendReadyIfLaidOut];
}

- (void)layoutSubviews {
    [super layoutSubviews];
    [self sendReadyIfLaidOut];
}

- (void)safeAreaInsetsDidChange {
    [super safeAreaInsetsDidChange];
    [self sendReadyIfLaidOut];
}

// The screen renders its content once this view is laid out in its place with a size, as
// react-native-safe-area-context does: a list that is there before the first layout of the top bar
// makes UIKit start the large title collapsed. A tab that isn't shown has its root outside the
// window: it renders its content when it shows.
- (void)sendReadyIfLaidOut {
    if (_readySent || _eventEmitter == nullptr || self.superview == nil) {
        return;
    }
    if (self.waitsForReadiness && (self.window == nil || CGRectIsEmpty(self.window.bounds) ||
                                   CGSizeEqualToSize(self.frame.size, CGSizeZero))) {
        return;
    }
    _readySent = YES;
    std::static_pointer_cast<const ANNSafeAreaViewEventEmitter>(_eventEmitter)->onReady({});
    if (_screenId != nil) {
        [ANNScreenReadiness screenDidLayOut:_screenId];
    }
}

// The core answers by the id: a screen waits for its layout; a component of a bar (title, button, background) or the
// item of a tab renders at once, as it did before; it shows with the bar.
- (BOOL)waitsForReadiness {
    return _screenId == nil || [ANNScreenReadiness screenWaitsForReadiness:_screenId];
}

- (void)prepareForRecycle {
    [super prepareForRecycle];
    _state.reset();
    // A recycled view gets its next screen's id with its next props.
    [self listenToScreenId:nil];
    _readySent = NO;
    _contentRenderedSent = NO;
    _contentRendered = NO;
}

@end
