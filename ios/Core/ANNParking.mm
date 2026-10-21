#import "ANNParking.h"
#import "ANNAppearanceDispatcher.h"
#import "ANNAssert.h"
#import "UIViewController+LayoutProtocol.h"

// What a parked view had before it waited, and where it waits.
@interface ANNParkedView : NSObject
@property(nonatomic, weak, nullable) UIView *host;
@property(nonatomic) BOOL userInteractionEnabled;
@property(nonatomic) BOOL accessibilityElementsHidden;
@property(nonatomic) UIViewAutoresizing autoresizingMask;
@end

@implementation ANNParkedView
@end

// Every node that waits in a parking now, whichever parking it is. Weak; main queue.
static NSHashTable<UIViewController *> *ANNParkedNodes(void) {
    static NSHashTable<UIViewController *> *nodes;
    if (!nodes) {
        nodes = [NSHashTable weakObjectsHashTable];
    }
    return nodes;
}

@implementation ANNParking {
    // The parking holds its nodes: a node waits here until its waiter takes it out.
    NSMapTable<UIViewController *, ANNParkedView *> *_parked;
}

- (instancetype)init {
    if (self = [super init]) {
        _parked = [NSMapTable strongToStrongObjectsMapTable];
    }
    return self;
}

- (BOOL)park:(UIViewController *)node inView:(UIView *)host {
    ANNAssertMainQueue();
    if ([_parked objectForKey:node]) {
        return NO;
    }
    UIView *view = node.view;
    ANNParkedView *parked = [ANNParkedView new];
    parked.host = host;
    parked.userInteractionEnabled = view.userInteractionEnabled;
    parked.accessibilityElementsHidden = view.accessibilityElementsHidden;
    parked.autoresizingMask = view.autoresizingMask;
    [_parked setObject:parked forKey:node];
    [ANNParkedNodes() addObject:node];
    view.userInteractionEnabled = NO;
    view.accessibilityElementsHidden = YES;
    // It follows the host while it waits.
    view.autoresizingMask = UIViewAutoresizingFlexibleWidth | UIViewAutoresizingFlexibleHeight;
    view.frame = host.bounds;
    [ANNAppearanceDispatcher driveAppearanceOf:@[ node ]];
    [host insertSubview:view atIndex:0];
    return YES;
}

// UIKit reports a view controller without a parent whose view leaves a window as disappearing at once, but its
// `viewDidDisappear:` only after the next commit. A node that shows in the same turn (a root that becomes the window's
// root) would get that late call after its own appearance, and UIKit would keep it disappeared for good: a stack UIKit
// takes for disappeared gives the screens it pushes neither their appearance nor their safe area. The waiter that
// shows the node at once ends the departure here.
- (void)unpark:(UIViewController *)node endingDeparture:(BOOL)endingDeparture {
    ANNAssertMainQueue();
    ANNParkedView *parked = [_parked objectForKey:node];
    if (!parked) {
        return;
    }
    [_parked removeObjectForKey:node];
    [ANNParkedNodes() removeObject:node];
    UIView *view = node.viewIfLoaded;
    view.userInteractionEnabled = parked.userInteractionEnabled;
    view.accessibilityElementsHidden = parked.accessibilityElementsHidden;
    view.autoresizingMask = parked.autoresizingMask;
    if (endingDeparture) {
        [node beginAppearanceTransition:NO animated:NO];
    }
    // UIKit places the view itself once it shows it: only the parking's own place is left.
    if (view.superview == parked.host) {
        [view removeFromSuperview];
    }
    if (endingDeparture) {
        [node endAppearanceTransition];
    }
    [ANNAppearanceDispatcher stopDrivingAppearanceOf:@[ node ]];
}

+ (BOOL)isUnseen:(UIViewController *)node {
    ANNAssertMainQueue();
    NSHashTable<UIViewController *> *parked = ANNParkedNodes();
    if (parked.count == 0) {
        return NO;
    }
    for (UIViewController *parent = node; parent; parent = parent.layoutParent) {
        if ([parked containsObject:parent]) {
            return YES;
        }
    }
    return NO;
}

- (BOOL)isParked:(UIViewController *)node {
    return [_parked objectForKey:node] != nil;
}

- (NSArray<UIViewController *> *)parkedNodes {
    NSMutableArray<UIViewController *> *nodes = [NSMutableArray new];
    for (UIViewController *node in _parked.keyEnumerator) {
        [nodes addObject:node];
    }
    return nodes;
}

@end
