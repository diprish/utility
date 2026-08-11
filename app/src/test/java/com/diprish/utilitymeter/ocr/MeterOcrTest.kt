package com.diprish.utilitymeter.ocr

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for the pure number-extraction heuristic in [MeterOcr]. These run
 * on the JVM without a device — the ML Kit call itself is not exercised here.
 */
class MeterOcrTest {

    @Test
    fun extractsPlainInteger() {
        assertTrue(MeterOcr.extractCandidates("012345").contains(12345.0))
    }

    @Test
    fun handlesDecimalWithDot() {
        assertTrue(MeterOcr.extractCandidates("reading 1234.5 kWh").contains(1234.5))
    }

    @Test
    fun handlesDecimalWithComma() {
        assertTrue(MeterOcr.extractCandidates("1234,5").contains(1234.5))
    }

    @Test
    fun stripsSpacesInsideNumbers() {
        // OCR frequently splits digit groups with spaces.
        assertTrue(MeterOcr.extractCandidates("4 321").contains(4321.0))
    }

    @Test
    fun ignoresNonNumericText() {
        assertTrue(MeterOcr.extractCandidates("kWh SN").isEmpty())
    }

    @Test
    fun emptyInputYieldsNoCandidates() {
        assertEquals(emptyList<Double>(), MeterOcr.extractCandidates(""))
    }
}
