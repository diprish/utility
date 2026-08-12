package com.diprish.utilitymeter.ocr

/**
 * The target rectangle the user aligns the meter digits within, expressed as
 * fractions of the camera preview / captured image (0..1). The same values
 * drive the on-screen guide box and the crop applied before OCR, so what the
 * user frames is exactly what gets read.
 *
 * A wide, short band across the middle suits the horizontal digit row of most
 * meters.
 */
object CaptureReticle {
    const val LEFT = 0.08f
    const val TOP = 0.40f
    const val RIGHT = 0.92f
    const val BOTTOM = 0.58f
}
