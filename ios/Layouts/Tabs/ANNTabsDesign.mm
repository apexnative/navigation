#import "ANNTabsDesign.h"

@implementation ANNTabsDesign

// Read once: the Info.plist of a running app doesn't change.
+ (BOOL)isCompatibilityMode {
    static BOOL compatibility;
    static dispatch_once_t once;
    dispatch_once(&once, ^{
      if (@available(iOS 26.0, *)) {
          id value = NSBundle.mainBundle.infoDictionary[@"UIDesignRequiresCompatibility"];
          compatibility = [value respondsToSelector:@selector(boolValue)] && [value boolValue];
      }
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
