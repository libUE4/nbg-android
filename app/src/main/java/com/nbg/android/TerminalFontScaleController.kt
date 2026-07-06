package com.nbg.android

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

class TerminalFontScaleController(
  initialFontSizeSp: Float = DefaultFontSizeSp,
) {
  var fontSizeSp by mutableStateOf(normalizeFontSize(initialFontSizeSp))
    private set

  fun applyZoom(scaleFactor: Float) {
    if (!scaleFactor.isFinite() || scaleFactor <= 0f) return
    fontSizeSp = (fontSizeSp * scaleFactor).coerceIn(MinFontSizeSp, MaxFontSizeSp)
  }

  fun increase() {
    fontSizeSp = (fontSizeSp + 1f).coerceAtMost(MaxFontSizeSp)
  }

  fun decrease() {
    fontSizeSp = (fontSizeSp - 1f).coerceAtLeast(MinFontSizeSp)
  }

  companion object {
    const val DefaultFontSizeSp = 10f
    const val MinFontSizeSp = 6f
    const val MaxFontSizeSp = 22f

    private fun normalizeFontSize(value: Float): Float =
      if (value.isFinite()) value.coerceIn(MinFontSizeSp, MaxFontSizeSp) else DefaultFontSizeSp
  }
}
