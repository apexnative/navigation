#import "ANNElementFinder.h"
#import "ANNAppearanceDispatcher.h"
#import "Text.h"
#import "UIViewController+LayoutProtocol.h"
#import <React/RCTLog.h>
#import <React/RCTViewComponentView.h>

NSString *ANNNativeIDOf(Text *value) {
    NSString *text = [value withDefault:nil];
    return [text isKindOfClass:NSString.class] && text.length > 0 ? text : nil;
}

void ANNElementSkipped(NSString *element, NSString *reason) {
    RCTLogWarn(@"[ApexNavigation] the transition's element %@ is skipped, it shows and hides with "
               @"its screen while the rest plays: %@",
               element, reason);
}

BOOL ANNElementStep(NSString *element, NS_NOESCAPE dispatch_block_t work) {
    @try {
        work();
        return YES;
    } @catch (NSException *exception) {
        ANNElementSkipped(element, [NSString stringWithFormat:@"%@ (%@)", exception.reason ?: @"",
                                                              exception.name]);
        return NO;
    }
}

@implementation ANNElementFinder

+ (UIView *)searchRootOf:(UIViewController *)node {
    if (!node) {
        return nil;
    }
    UIViewController *screen = [ANNAppearanceDispatcher visibleScreenOf:node];
    return screen.reactView;
}

+ (UIView *)viewWithNativeID:(NSString *)nativeID inView:(UIView *)root {
    if (nativeID.length == 0 || !root) {
        return nil;
    }
    if ([root isKindOfClass:RCTViewComponentView.class] &&
        [((RCTViewComponentView *)root).nativeId isEqualToString:nativeID]) {
        return root;
    }
    for (UIView *subview in root.subviews) {
        UIView *found = [self viewWithNativeID:nativeID inView:subview];
        if (found) {
            return found;
        }
    }
    return nil;
}

+ (UIView *)viewWithNativeID:(NSString *)nativeID inNode:(UIViewController *)node {
    return [self viewWithNativeID:nativeID inView:[self searchRootOf:node]];
}

@end

@implementation ANNIsolatedElement {
    id<ANNFrameClient> _client;
    NSString *_name;
    BOOL _failed;
}

- (instancetype)initWithClient:(id<ANNFrameClient>)client name:(NSString *)name {
    if (self = [super init]) {
        _client = client;
        _name = [name copy];
    }
    return self;
}

#pragma mark - ANNFrameClient

- (void)applyAtTime:(NSTimeInterval)time {
    if (_failed) {
        return;
    }
    id<ANNFrameClient> client = _client;
    if (!ANNElementStep(_name, ^{
          [client applyAtTime:time];
        })) {
        _failed = YES;
        [self restore];
    }
}

- (NSTimeInterval)endTime {
    return _client.endTime;
}

- (void)restore {
    id<ANNFrameClient> client = _client;
    ANNElementStep(_name, ^{
      [client restore];
    });
}

@end
