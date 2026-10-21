#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Which design UIKit draws the bars in: the Liquid Glass of iOS 26, or the design before it, which iOS 26 keeps when
/// the app's Info.plist has `UIDesignRequiresCompatibility = YES` (the compatibility mode). The key is read once per
/// process. Every place of the core that depends on the design asks here (the module `UITabBar+utils` keeps its own
/// copy).
@interface ANNTabsDesign : NSObject

/// iOS 26 or later, and not the compatibility mode.
@property(class, nonatomic, readonly) BOOL isGlass;

/// iOS 26 or later with `UIDesignRequiresCompatibility = YES`: the bars look as before iOS 26.
@property(class, nonatomic, readonly) BOOL isCompatibilityMode;

/// iOS 18 and later on an iPad or a Mac in a regular width: the system's tab bar is on top (or in the sidebar), and
/// the bottom keeps nothing of it.
+ (BOOL)barIsOnTopIn:(UITraitCollection *)traits;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@end

NS_ASSUME_NONNULL_END
