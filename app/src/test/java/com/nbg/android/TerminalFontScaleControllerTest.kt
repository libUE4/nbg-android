package com.nbg.android

import org.junit.Assert.assertEquals
import org.junit.Test

class TerminalFontScaleControllerTest {
  @Test
  fun scalesFontSizeWithinTermuxLikeBounds() {
    val controller = TerminalFontScaleController(initialFontSizeSp = 10f)

    controller.applyZoom(1.25f)
    assertEquals(12.5f, controller.fontSizeSp, 0.001f)

    controller.applyZoom(0.01f)
    assertEquals(6f, controller.fontSizeSp, 0.001f)

    controller.applyZoom(100f)
    assertEquals(22f, controller.fontSizeSp, 0.001f)
  }

  @Test
  fun ignoresNonFiniteFontSizesAndZoomFactors() {
    val controller = TerminalFontScaleController(initialFontSizeSp = Float.NaN)

    assertEquals(TerminalFontScaleController.DefaultFontSizeSp, controller.fontSizeSp, 0.001f)

    controller.applyZoom(Float.POSITIVE_INFINITY)
    assertEquals(TerminalFontScaleController.DefaultFontSizeSp, controller.fontSizeSp, 0.001f)

    controller.applyZoom(Float.NaN)
    assertEquals(TerminalFontScaleController.DefaultFontSizeSp, controller.fontSizeSp, 0.001f)
  }
}
