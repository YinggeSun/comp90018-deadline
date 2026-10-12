package com.comp90018.deadline.sensor.light

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoLightControllerTest {
    @Test fun luxMappingIsBoundedAndMonotonic() {
        assertEquals(0.15f, brightnessForLux(0f), 0.001f)
        assertEquals(1f, brightnessForLux(1000f), 0.001f)
        assertTrue(brightnessForLux(10f) < brightnessForLux(100f))
        assertEquals(0.15f, brightnessForLux(-1f), 0.001f)
        assertEquals(0.15f, brightnessForLux(Float.NaN), 0.001f)
    }
}
