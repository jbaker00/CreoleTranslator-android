package com.creole.translator.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AgeBandTest {
    @Test fun under13() = assertEquals(AgeBand.UNDER_13, AgeBand.of(0, 12))
    @Test fun teen13to15() = assertEquals(AgeBand.TEEN, AgeBand.of(13, 15))
    @Test fun teen16to17() = assertEquals(AgeBand.TEEN, AgeBand.of(16, 17))
    @Test fun adultOpenEnded() = assertEquals(AgeBand.ADULT, AgeBand.of(18, null))
    @Test fun nothingShared() = assertEquals(AgeBand.UNKNOWN, AgeBand.of(null, null))
    @Test fun lowerOnlyUnder18IsUnknown() = assertEquals(AgeBand.UNKNOWN, AgeBand.of(13, null))

    @Test fun minorFlag() {
        assertTrue(AgeBand.UNDER_13.isMinor)
        assertTrue(AgeBand.TEEN.isMinor)
        assertFalse(AgeBand.ADULT.isMinor)
        assertFalse(AgeBand.UNKNOWN.isMinor)
    }
}
