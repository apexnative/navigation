package dev.apexnative.navigation.views.externalcomponent

import android.annotation.SuppressLint
import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import dev.apexnative.navigation.views.component.Renderable

/**
 * The view of a native screen (`ExternalComponentViewController`): the container the app's view fills. It takes the
 * window insets itself and passes nothing on to the app's view; it keeps the room for the bars as its margins and what
 * the system covers as its padding, as a React screen does. It is rendered once it has the app's view.
 */
@SuppressLint("ViewConstructor")
class ExternalComponentLayout(context: Context) : FrameLayout(context), Renderable {
    /** Runs as the view joins a window. */
    internal var onAttached: (() -> Unit)? = null

    override val isRendered: Boolean
        get() = childCount > 0

    /** Puts the app's [view] in, filling the container. */
    fun setContent(view: View) {
        (view.parent as? ViewGroup)?.removeView(view)
        addView(contentOf(view), LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        onAttached?.invoke()
    }

    internal companion object {
        /**
         * What goes into the container for the app's [view]: the view itself when it is a `ViewGroup`, else a
         * `FrameLayout` it fills (a lone `TextView` failed as the container measured it).
         */
        fun contentOf(view: View): ViewGroup = view as? ViewGroup
            ?: FrameLayout(view.context).apply {
                addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
            }
    }
}
