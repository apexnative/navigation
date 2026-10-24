package dev.apexnative.navigation.viewcontrollers.modal

import androidx.activity.BackEventCompat

/**
 * A modal's sheet as back sees it ([ModalSheet]): whether a release of back would hide it, and the preview of that
 * hide during the back gesture (Android 14+). [ModalCoordinator.backTargetSheet] gives the one back would dismiss now.
 * Main thread only.
 */
internal interface SheetBack {
    /**
     * Back's release would hide the sheet, as a drag down does: it stands at a detent from which it goes down (its
     * smallest, while it lets the user close it), laid out, and it isn't closing already. False at a larger detent:
     * the Material behavior would only collapse it there.
     */
    val hidesOnBack: Boolean

    /**
     * Starts the preview of back on the sheet with the gesture's first [event]: the sheet shrinks a little toward the
     * bottom with the progress, the scrim following it. None when [hidesOnBack] doesn't hold or a preview runs.
     */
    fun startBackPreview(event: BackEventCompat): Gesture?

    /** A back gesture over the sheet ([startBackPreview]). */
    interface Gesture {
        /** The sheet the gesture previews. */
        val sheet: SheetBack

        /** The gesture moved. */
        fun progress(event: BackEventCompat)

        /** Cancelled, or back went elsewhere: the sheet grows back to where it stood, the scrim with it. */
        fun cancel()

        /**
         * Released: the sheet slides down from where the preview left it and goes as one the user dragged down (its
         * events, its dismissal). False when the preview stopped since it started (a command closed or moved the
         * sheet): back goes the usual way.
         */
        fun commit(): Boolean
    }
}
