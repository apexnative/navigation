#import <Foundation/Foundation.h>

/// What a React view of the navigation shows: a screen, or a component of the options (bar title, button, background).
/// The lifecycle events name it by this type (`componentType`).
typedef NS_ENUM(NSInteger, ANNComponentType) {
    /// A screen, `Component` in the events.
    ANNComponentTypeComponent,
    /// The same as a screen, by the name the views of screens use.
    ANNReactComponentTypeScreen = ANNComponentTypeComponent,
    ANNComponentTypeTopBarTitle,
    ANNComponentTypeTopBarButton,
    ANNComponentTypeTopBarBackground,
    /// A cell of the tabs' custom row (`bottomTab.component`): it renders at once and sends no appearance events on
    /// iOS.
    ANNComponentTypeBottomTabItem,
    /// The tabs' bottom accessory (iOS 26, `bottomTabs.bottomAccessory.component`): it waits for its layout like a
    /// screen and sends no appearance events.
    ANNComponentTypeBottomTabsAccessory,
};

/// The type as the views of screens name it.
typedef ANNComponentType ANNReactComponentType;
