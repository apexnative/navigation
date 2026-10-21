package dev.apexnative.navigation.views.component

/** A screen's view that knows whether its content is drawn as it is now: what a transition into it waits for. */
interface Renderable {
    val isRendered: Boolean
}
