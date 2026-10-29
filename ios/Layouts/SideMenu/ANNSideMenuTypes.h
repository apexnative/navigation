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

/** `sideMenu.menusAttachMode`: when the screens of the menus are made, with the side menu, after its first
 * appearance, or each as it first starts to open. */
typedef NS_ENUM(NSInteger, ANNSideMenuAttachMode) {
    ANNSideMenuAttachModeTogether,
    ANNSideMenuAttachModeAfterCenter,
    ANNSideMenuAttachModeOnOpen,
};

/** `sideMenu.openGestureMode`: a pan from anywhere on the center, or from its edges only. */
typedef NS_ENUM(NSInteger, ANNSideMenuOpenGestureMode) {
    ANNSideMenuOpenGestureModeEntireScreen,
    ANNSideMenuOpenGestureModeBezel,
};
