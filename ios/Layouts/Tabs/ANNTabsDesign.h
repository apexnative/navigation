#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// Which design UIKit draws the bars in: the Liquid Glass of iOS 26, or the design before it, which UIKit keeps when
/// the app's Info.plist has `UIDesignRequiresCompatibility = YES` (the compatibility mode): on iOS 26, and on iOS 27
/// only for an app built with an SDK before iOS 27. The key is read once per process. Every place of the core that
/// depends on the design asks here.
@interface ANNTabsDesign : NSObject

/// iOS 26 or later, and not the compatibility mode.
@property(class, nonatomic, readonly) BOOL isGlass;

/// iOS 26 or later with `UIDesignRequiresCompatibility = YES`, where UIKit honors it (not on iOS 27 and later for an
/// app built with the iOS 27 SDK): the bars look as before iOS 26.
@property(class, nonatomic, readonly) BOOL isCompatibilityMode;

/// iOS 18 and later on an iPad or a Mac in a regular width: the system's tab bar is on top (or in the sidebar), and
/// the bottom keeps nothing of it.
+ (BOOL)barIsOnTopIn:(UITraitCollection *)traits;

- (instancetype)init NS_UNAVAILABLE;
+ (instancetype)new NS_UNAVAILABLE;

@end

NS_ASSUME_NONNULL_END
