package dev.apexnative.navigation.utils

import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityNodeProvider
import androidx.annotation.RequiresApi
import androidx.core.view.AccessibilityDelegateCompat
import androidx.core.view.ViewCompat
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat

/**
 * The `testID` of a view the core draws: the view's tag and the `resource-id` of its accessibility node
 * (`viewIdResourceName`), which UiAutomator, Appium and Maestro read. A view without one has none: a view used again
 * loses the one it had. Main thread only.
 */
internal object TestIds {
    fun apply(view: View, testId: String?) {
        val id = testId?.ifEmpty { null }
        view.tag = id
        ViewCompat.setAccessibilityDelegate(view, id?.let(::IdDelegate))
    }

    /**
     * As [apply], for a view with an accessibility delegate of its own (Material's sheet drag handle: its clicks and
     * the sheet's state): the delegate stays and the id comes over it. Android 10+ gives the view's delegate
     * (`View.getAccessibilityDelegate`); before that only the tag is set, and the delegate isn't replaced.
     */
    fun applyOver(view: View, testId: String?) {
        val id = testId?.ifEmpty { null }
        view.tag = id
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val current = view.accessibilityDelegate
        val own = (current as? OverDelegate)?.own ?: current
        view.accessibilityDelegate = if (id == null) own else OverDelegate(id, own)
    }

    private class IdDelegate(private val id: String) : AccessibilityDelegateCompat() {
        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfoCompat) {
            super.onInitializeAccessibilityNodeInfo(host, info)
            info.viewIdResourceName = id
        }
    }

    // The view's own delegate answers everything; the node gets the id after it.
    @RequiresApi(Build.VERSION_CODES.Q)
    private class OverDelegate(private val id: String, val own: View.AccessibilityDelegate?) :
        View.AccessibilityDelegate() {
        private val inner = own ?: View.AccessibilityDelegate()

        override fun onInitializeAccessibilityNodeInfo(host: View, info: AccessibilityNodeInfo) {
            inner.onInitializeAccessibilityNodeInfo(host, info)
            info.viewIdResourceName = id
        }

        override fun sendAccessibilityEvent(host: View, eventType: Int) = inner.sendAccessibilityEvent(host, eventType)

        override fun performAccessibilityAction(host: View, action: Int, args: Bundle?): Boolean =
            inner.performAccessibilityAction(host, action, args)

        override fun sendAccessibilityEventUnchecked(host: View, event: AccessibilityEvent) =
            inner.sendAccessibilityEventUnchecked(host, event)

        override fun dispatchPopulateAccessibilityEvent(host: View, event: AccessibilityEvent): Boolean =
            inner.dispatchPopulateAccessibilityEvent(host, event)

        override fun onPopulateAccessibilityEvent(host: View, event: AccessibilityEvent) =
            inner.onPopulateAccessibilityEvent(host, event)

        override fun onInitializeAccessibilityEvent(host: View, event: AccessibilityEvent) =
            inner.onInitializeAccessibilityEvent(host, event)

        override fun addExtraDataToAccessibilityNodeInfo(
            host: View,
            info: AccessibilityNodeInfo,
            extraDataKey: String,
            arguments: Bundle?,
        ) = inner.addExtraDataToAccessibilityNodeInfo(host, info, extraDataKey, arguments)

        override fun onRequestSendAccessibilityEvent(host: ViewGroup, child: View, event: AccessibilityEvent): Boolean =
            inner.onRequestSendAccessibilityEvent(host, child, event)

        override fun getAccessibilityNodeProvider(host: View): AccessibilityNodeProvider? =
            inner.getAccessibilityNodeProvider(host)
    }
}
