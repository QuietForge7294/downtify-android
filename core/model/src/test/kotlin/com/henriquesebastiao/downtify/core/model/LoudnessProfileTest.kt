package com.henriquesebastiao.downtify.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LoudnessProfileTest {
    private fun ready(integrated: Double, peak: Double, gain: Double) =
        LoudnessProfile("ready", 1, integrated, peak, gain)

    @Test
    fun amplificationAndPeaksAreCappedEvenWhenTheServerRequestsMoreGain() {
        assertEquals(12.0, ready(-40.0, -20.0, 30.0).safeGainDb(), 0.001)
        assertEquals(4.0, ready(-40.0, -5.0, 12.0).safeGainDb(), 0.001)
        assertEquals(-10.0, ready(-8.0, -0.5, -10.0).safeGainDb(), 0.001)
    }

    @Test
    fun missingUnsupportedAndNonFiniteMeasurementsKeepOriginalVolume() {
        assertEquals(0.0, LoudnessProfile().safeGainDb(), 0.0)
        assertEquals(0.0, ready(-30.0, -20.0, 12.0).copy(algorithmVersion = 2).safeGainDb(), 0.0)
        assertEquals(0.0, ready(Double.NaN, -20.0, 12.0).safeGainDb(), 0.0)
        assertEquals(0.0, ready(-30.0, -20.0, -90.0).safeGainDb(), 0.0)
        assertEquals(0.0, LoudnessProfile("silent", 1).safeGainDb(), 0.0)
    }

    @Test
    fun onlyAMeasurementOfAKnownFileCanBeBoundToAnOfflineCopy() {
        assertTrue(ready(-20.0, -6.0, 2.0).copy(fingerprint = "f").bindable)
        assertFalse(ready(-20.0, -6.0, 2.0).bindable)
        assertFalse(LoudnessProfile("unanalysed", 1, fingerprint = "f").bindable)
    }
}
