#import "ANNSystemUIRules.h"
#import <React/RCTLog.h>

// The sides of one value of `layout.orientation`; `default` is the app's mask, which only the caller knows.
static UIInterfaceOrientationMask ANNSidesOfOrientation(NSString *value) {
    if ([value isEqualToString:@"all"] || [value isEqualToString:@"sensor"]) {
        return UIInterfaceOrientationMaskAll;
    }
    if ([value isEqualToString:@"landscape"] || [value isEqualToString:@"sensorLandscape"]) {
        return UIInterfaceOrientationMaskLandscape;
    }
    if ([value isEqualToString:@"portrait"]) {
        return UIInterfaceOrientationMaskPortrait;
    }
    if ([value isEqualToString:@"sensorPortrait"]) {
        return UIInterfaceOrientationMaskPortrait | UIInterfaceOrientationMaskPortraitUpsideDown;
    }
    if ([value isEqualToString:@"upsideDown"]) {
        return UIInterfaceOrientationMaskPortraitUpsideDown;
    }
    return 0;
}

UIInterfaceOrientationMask ANNOrientationMaskOf(NSArray<NSString *> *values,
                                                UIInterfaceOrientationMask appMask) {
    UIInterfaceOrientationMask mask = 0;
    for (id value in values) {
        if (![value isKindOfClass:NSString.class]) {
            continue;
        }
        mask |= [value isEqualToString:@"default"] ? appMask : ANNSidesOfOrientation(value);
    }
    mask &= appMask;
    return mask != 0 ? mask : appMask;
}

UIStatusBarStyle ANNStatusBarStyleOf(NSString *style) {
    if ([style isEqualToString:@"light"]) {
        return UIStatusBarStyleLightContent;
    }
    if ([style isEqualToString:@"dark"]) {
        return UIStatusBarStyleDarkContent;
    }
    return UIStatusBarStyleDefault;
}

BOOL ANNStatusBarHiddenOf(ANNStatusBarOptions *statusBar, BOOL navigationBarHidden) {
    if (statusBar.visible.hasValue && !statusBar.visible.isNull) {
        return ![statusBar.visible get];
    }
    return [statusBar.hideWithTopBar withDefault:NO] && navigationBarHidden;
}

UIRectEdge ANNDeferredEdgesOf(NSArray<NSString *> *values) {
    UIRectEdge edges = UIRectEdgeNone;
    for (id value in values) {
        if ([value isEqual:@"top"]) {
            edges |= UIRectEdgeTop;
        } else if ([value isEqual:@"bottom"]) {
            edges |= UIRectEdgeBottom;
        } else if ([value isEqual:@"left"]) {
            edges |= UIRectEdgeLeft;
        } else if ([value isEqual:@"right"]) {
            edges |= UIRectEdgeRight;
        }
    }
    return edges;
}

#if DEBUG

static void ANNExpectMask(const char *name, NSUInteger actual, NSUInteger expected) {
    if (actual != expected) {
        RCTLogError(@"[ApexNavigation] system UI rules self-check '%s': %lu, expected %lu", name,
                    (unsigned long)actual, (unsigned long)expected);
    }
}

void ANNSystemUIRulesSelfCheck(void) {
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      const UIInterfaceOrientationMask all = UIInterfaceOrientationMaskAll;
      const UIInterfaceOrientationMask phone = UIInterfaceOrientationMaskAllButUpsideDown;
      const UIInterfaceOrientationMask up = UIInterfaceOrientationMaskPortrait;
      const UIInterfaceOrientationMask down = UIInterfaceOrientationMaskPortraitUpsideDown;
      const UIInterfaceOrientationMask sides = UIInterfaceOrientationMaskLandscape;
      auto mask = [](NSArray<NSString *> *values, UIInterfaceOrientationMask app) {
          return ANNOrientationMaskOf(values, app);
      };
      // Each value alone, with an app that allows every side (the Android table's sides).
      ANNExpectMask("all", mask(@[ @"all" ], all), all);
      ANNExpectMask("sensor", mask(@[ @"sensor" ], all), all);
      ANNExpectMask("landscape", mask(@[ @"landscape" ], all), sides);
      ANNExpectMask("sensorLandscape", mask(@[ @"sensorLandscape" ], all), sides);
      ANNExpectMask("portrait", mask(@[ @"portrait" ], all), up);
      ANNExpectMask("sensorPortrait", mask(@[ @"sensorPortrait" ], all), up | down);
      ANNExpectMask("upsideDown", mask(@[ @"upsideDown" ], all), down);
      ANNExpectMask("default", mask(@[ @"default" ], phone), phone);
      // Unions.
      ANNExpectMask("portrait + landscape", mask(@[ @"portrait", @"landscape" ], all), phone);
      ANNExpectMask("upsideDown + landscape", mask(@[ @"upsideDown", @"landscape" ], all),
                    sides | down);
      ANNExpectMask("upsideDown + portrait", mask(@[ @"upsideDown", @"portrait" ], all), up | down);
      ANNExpectMask("default + landscape", mask(@[ @"default", @"landscape" ], up), up);
      // The app's mask: the meet, and the app's mask for nothing in common, no values, no option, unknown values.
      ANNExpectMask("sensorPortrait in a phone app", mask(@[ @"sensorPortrait" ], phone), up);
      ANNExpectMask("upsideDown in a phone app", mask(@[ @"upsideDown" ], phone), phone);
      ANNExpectMask("landscape in a portrait app", mask(@[ @"landscape" ], up), up);
      ANNExpectMask("[]", mask(@[], phone), phone);
      ANNExpectMask("no option", mask(nil, phone), phone);
      ANNExpectMask("['bogus']", mask(@[ @"bogus" ], phone), phone);
      // The edges.
      ANNExpectMask("edges top + left", ANNDeferredEdgesOf(@[ @"top", @"left" ]),
                    UIRectEdgeTop | UIRectEdgeLeft);
      ANNExpectMask("edges all", ANNDeferredEdgesOf(@[ @"top", @"bottom", @"left", @"right" ]),
                    UIRectEdgeAll);
      ANNExpectMask("edges none", ANNDeferredEdgesOf(nil), UIRectEdgeNone);
      ANNExpectMask("edges bogus", ANNDeferredEdgesOf(@[ @"bogus" ]), UIRectEdgeNone);
      // The style.
      ANNExpectMask("style light", ANNStatusBarStyleOf(@"light"), UIStatusBarStyleLightContent);
      ANNExpectMask("style dark", ANNStatusBarStyleOf(@"dark"), UIStatusBarStyleDarkContent);
      ANNExpectMask("style none", ANNStatusBarStyleOf(nil), UIStatusBarStyleDefault);
      ANNExpectMask("style other", ANNStatusBarStyleOf(@"black"), UIStatusBarStyleDefault);
      // The hidden rule: an explicit `visible` decides, else `hideWithTopBar` with the bar.
      ANNStatusBarOptions *hides =
          [[ANNStatusBarOptions alloc] initWithDict:@{@"hideWithTopBar" : @YES}];
      ANNStatusBarOptions *shown =
          [[ANNStatusBarOptions alloc] initWithDict:@{@"hideWithTopBar" : @YES, @"visible" : @YES}];
      ANNStatusBarOptions *hidden = [[ANNStatusBarOptions alloc] initWithDict:@{@"visible" : @NO}];
      ANNStatusBarOptions *none = [ANNStatusBarOptions new];
      ANNExpectMask("hideWithTopBar, bar hidden", ANNStatusBarHiddenOf(hides, YES), YES);
      ANNExpectMask("hideWithTopBar, bar shown", ANNStatusBarHiddenOf(hides, NO), NO);
      ANNExpectMask("visible over hideWithTopBar", ANNStatusBarHiddenOf(shown, YES), NO);
      ANNExpectMask("visible false", ANNStatusBarHiddenOf(hidden, NO), YES);
      ANNExpectMask("no option", ANNStatusBarHiddenOf(none, YES), NO);
    });
}

#endif
