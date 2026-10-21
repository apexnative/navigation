package dev.apexnative.navigation.react.events

/**
 * `componentType` of the appearance events: a screen, or a React component in the options of a bar or a tab. JavaScript
 * gives the app only those of [Component] as screen events.
 */
enum class ComponentType(val jsName: String) {
    Component("Component"),
    TopBarTitle("TopBarTitle"),
    TopBarButton("TopBarButton"),
    TopBarBackground("TopBarBackground"),
    BottomTabItem("BottomTabItem"),
}
