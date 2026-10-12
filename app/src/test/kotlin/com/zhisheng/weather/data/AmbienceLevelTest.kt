package com.zhisheng.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AmbienceLevelTest {

    @Test
    fun intenseLevelIsTheDefaultAndExplicitLevelsRemainDistinct() {
        assertEquals(AmbienceLevel.INTENSE, AmbienceLevel.from(null))
        assertEquals(AmbienceLevel.OFF, AmbienceLevel.from("off"))
        assertEquals(AmbienceLevel.SUBTLE, AmbienceLevel.from("subtle"))
        assertEquals(AmbienceLevel.VIVID, AmbienceLevel.from("vivid"))
        assertTrue(AmbienceLevel.VIVID.factor >= AmbienceLevel.SUBTLE.factor * 3f)
        assertTrue(AmbienceLevel.INTENSE.factor > AmbienceLevel.VIVID.factor)
        assertTrue(AmbienceLevel.INTENSE.motionScale > AmbienceLevel.VIVID.motionScale)
        assertTrue(AmbienceLevel.INTENSE.vivid)
    }
}
