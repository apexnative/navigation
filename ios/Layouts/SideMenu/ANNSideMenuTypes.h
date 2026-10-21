#import <Foundation/Foundation.h>

typedef NS_ENUM(NSInteger, ANNSideMenuSide) {
    ANNSideMenuSideNone,
    ANNSideMenuSideLeft,
    ANNSideMenuSideRight,
};

/** `sideMenu.<side>.openMode`: the center moves aside (`pushContent`) or the menu covers it. */
typedef NS_ENUM(NSInteger, ANNSideMenuOpenMode) {
    ANNSideMenuOpenModePushContent,
    ANNSideMenuOpenModeAboveContent,
};

/** `sideMenu.openGestureMode`: a pan from anywhere on the center, or from its edges only. */
typedef NS_ENUM(NSInteger, ANNSideMenuOpenGestureMode) {
    ANNSideMenuOpenGestureModeEntireScreen,
    ANNSideMenuOpenGestureModeBezel,
};
