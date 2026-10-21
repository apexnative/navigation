package dev.apexnative.navigation.views.stack.topbar

import android.annotation.SuppressLint
import android.content.Context
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import androidx.annotation.ColorInt
import androidx.appcompat.widget.AppCompatTextView
import dev.apexnative.navigation.options.LargeTitleOptions
import dev.apexnative.navigation.options.parsers.TypefaceLoader
import dev.apexnative.navigation.utils.UiUtils

/**
 * The large title of the bar (`topBar.largeTitle`), under its row: the title big at the start, as Material 3's large
 * top app bar has it (headlineMedium, 16 dp from the sides and the bottom).
 */
@SuppressLint("ViewConstructor")
class LargeTitleView(context: Context) : AppCompatTextView(context) {
    // The font the view starts with: a screen's font builds on it, not on the font of the screen before.
    private val initialTypeface = typeface

    init {
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.BOTTOM or Gravity.START
        textAlignment = TEXT_ALIGNMENT_VIEW_START
        val margin = UiUtils.dpToPx(context, MARGIN).toInt()
        setPaddingRelative(margin, 0, margin, margin)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, DEFAULT_FONT_SIZE)
    }

    fun applyOptions(options: LargeTitleOptions, typefaceLoader: TypefaceLoader?, @ColorInt defaultColor: Int) {
        setTextSize(TypedValue.COMPLEX_UNIT_SP, options.fontSize.get(DEFAULT_FONT_SIZE.toDouble()).toFloat())
        setTextColor(options.color.get(defaultColor) ?: defaultColor)
        if (typefaceLoader != null) typeface = options.font.getTypeface(typefaceLoader, initialTypeface)
    }

    companion object {
        /** The room the large title adds to the bar: Material 3's large top app bar is 152 dp, its row 64. */
        const val HEIGHT = 88f
        private const val DEFAULT_FONT_SIZE = 28f
        private const val MARGIN = 16f
    }
}
