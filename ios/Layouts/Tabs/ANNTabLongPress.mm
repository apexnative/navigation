#import "ANNTabLongPress.h"
#import "ANNBottomTabsController.h"
#import "UITabBar+utils.h"

@implementation ANNTabLongPress {
    __weak ANNBottomTabsController *_tabs;
    UILongPressGestureRecognizer *_recognizer;
}

- (instancetype)initWithTabs:(ANNBottomTabsController *)tabs {
    if (self = [super init]) {
        _tabs = tabs;
    }
    return self;
}

- (void)attach {
    UITabBar *bar = _tabs.tabBar;
    if (!bar || _recognizer.view == bar) {
        return;
    }
    if (!_recognizer) {
        _recognizer = [[UILongPressGestureRecognizer alloc] initWithTarget:self
                                                                    action:@selector(longPressed:)];
        _recognizer.delegate = self;
    }
    // On iOS 26 the system's selection gesture takes the touch: the long press works along with it. Before, the long
    // press cancels the button's touch by itself, so the tab isn't selected.
    if (@available(iOS 26.0, *)) {
        _recognizer.cancelsTouchesInView = NO;
    }
    [bar addGestureRecognizer:_recognizer];
}

- (void)detach {
    [_recognizer.view removeGestureRecognizer:_recognizer];
}

#pragma mark - The gesture

- (void)longPressed:(UILongPressGestureRecognizer *)recognizer {
    ANNBottomTabsController *tabs = _tabs;
    if (!tabs) {
        return;
    }
    switch (recognizer.state) {
        case UIGestureRecognizerStateBegan: {
            NSUInteger index = [self tabIndexAt:[recognizer locationInView:tabs.tabBar]];
            if (index == NSNotFound) {
                return;
            }
            BOOL marksSelection = NO;
            if (@available(iOS 26.0, *)) {
                marksSelection = YES;
            }
            [tabs longPressTabAtIndex:index marksSelection:marksSelection];
            break;
        }
        case UIGestureRecognizerStateEnded:
        case UIGestureRecognizerStateCancelled:
        case UIGestureRecognizerStateFailed:
            [tabs longPressEnded];
            break;
        default:
            break;
    }
}

// Each tab's own button, by its item (never by UIKit's index, which counts More and another order); the buttons sit in
// their platter on iOS 26, so their frames go into the bar's coordinates.
- (NSUInteger)tabIndexAt:(CGPoint)point {
    ANNBottomTabsController *tabs = _tabs;
    UITabBar *bar = tabs.tabBar;
    NSArray<UIViewController *> *list = tabs.layoutTabs;
    for (NSUInteger index = 0; index < list.count; index++) {
        UIView *button = [bar viewOfItem:list[index].tabBarItem];
        if (!button || button.hidden || button.window != bar.window) {
            continue;
        }
        CGRect frame = [button convertRect:button.bounds toView:bar];
        if (CGRectContainsPoint(frame, point)) {
            return index;
        }
    }
    return NSNotFound;
}

#pragma mark - UIGestureRecognizerDelegate

- (BOOL)gestureRecognizer:(UIGestureRecognizer *)gestureRecognizer
    shouldRecognizeSimultaneouslyWithGestureRecognizer:(UIGestureRecognizer *)other {
    if (@available(iOS 26.0, *)) {
        return YES;
    }
    return NO;
}

@end
