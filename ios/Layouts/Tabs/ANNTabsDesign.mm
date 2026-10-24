#import "ANNTabsDesign.h"

@implementation ANNTabsDesign

// Read once: the Info.plist of a running app doesn't change. iOS 26 keeps the earlier design for the key; iOS 27 keeps
// it only for an app built with an SDK before iOS 27 and ignores it for one built with the iOS 27 SDK (an app linked
// against the iOS 26 SDK keeps the earlier design on iOS 27, one linked against the iOS 27 SDK gets the glass). The
// core is compiled with the app, so the SDK it is compiled with is the app's.
+ (BOOL)isCompatibilityMode {
    static BOOL compatibility;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      if (@available(iOS 26.0, *)) {
          id value = NSBundle.mainBundle.infoDictionary[@"UIDesignRequiresCompatibility"];
          compatibility = [value respondsToSelector:@selector(boolValue)] && [value boolValue];
      }
#if __IPHONE_OS_VERSION_MAX_ALLOWED >= 270000
      if (@available(iOS 27.0, *)) {
          compatibility = NO;
      }
#endif
    });
    return compatibility;
}

+ (BOOL)isGlass {
    if (@available(iOS 26.0, *)) {
        return !self.isCompatibilityMode;
    }
    return NO;
}

+ (BOOL)barIsOnTopIn:(UITraitCollection *)traits {
    if (@available(iOS 18.0, *)) {
        UIUserInterfaceIdiom idiom = traits.userInterfaceIdiom;
        BOOL wide = idiom == UIUserInterfaceIdiomPad || idiom == UIUserInterfaceIdiomMac;
        return wide && traits.horizontalSizeClass == UIUserInterfaceSizeClassRegular;
    }
    return NO;
}

@end
