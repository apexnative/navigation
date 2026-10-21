#import <UIKit/UIKit.h>

NS_ASSUME_NONNULL_BEGIN

/// What a screen of an arrangement tells it (ANNArrangementViewController, iOS 27.1). The protocol is there on every
/// SDK, so a screen needs no check of its own.
@protocol ANNArrangementScreenDelegate <NSObject>

/// The screen laid out, appeared, disappeared or its traits changed: the pair may lie differently now.
- (void)screenDidChangeLayout:(UIViewController *)screen;

/// The screen set its toolbar items: the stack shows the toolbar of its child, the arrangement, whose items are the
/// primary's.
- (void)screen:(UIViewController *)screen didSetToolbarItemsAnimated:(BOOL)animated;

@end

NS_ASSUME_NONNULL_END
