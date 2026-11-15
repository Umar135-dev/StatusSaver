package tussaver

import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding

/** Keeps content clear of the status bar and navigation bar (edge-to-edge screens). */
fun View.applySystemBarsPadding() {
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
        insets
    }
}

/** Adds the status bar height on top of the padding the view already has. */
fun View.applyTopInsetPadding() {
    val basePadding = paddingTop
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val top = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
        view.updatePadding(top = basePadding + top)
        insets
    }
}

/** Adds the navigation bar height on top of the padding the view already has. */
/**
 * Adds the height of the tappable navigation area (the three buttons, if the phone has them).
 * With gesture navigation this is zero, so the bar sits close to the bottom edge.
 */
fun View.applyBottomInsetPadding() {
    val basePadding = paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(this) { view, insets ->
        val bottom = insets.getInsets(WindowInsetsCompat.Type.tappableElement()).bottom
        view.updatePadding(bottom = basePadding + bottom)
        insets
    }
}