package com.finditfun.app.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WifiMathTest {
    @Test
    public void validatesRealRssiValues() {
        assertTrue(WifiMath.isValidRssi(-1));
        assertTrue(WifiMath.isValidRssi(-126));
        assertFalse(WifiMath.isValidRssi(0));
        assertFalse(WifiMath.isValidRssi(-127));
    }

    @Test
    public void strengthFractionClampsAndInterpolates() {
        assertEquals(0.0, WifiMath.strengthFraction(-120), 0.0001);
        assertEquals(1.0, WifiMath.strengthFraction(-20), 0.0001);
        assertEquals(0.5, WifiMath.strengthFraction(-70), 0.0001);
    }

    @Test
    public void labelsUsefulWifiBands() {
        assertEquals("2.4 GHz", WifiMath.bandLabel(2_437));
        assertEquals("5 GHz", WifiMath.bandLabel(5_180));
        assertEquals("6 GHz", WifiMath.bandLabel(5_955));
        assertEquals("60 GHz", WifiMath.bandLabel(60_480));
    }

    @Test
    public void calculatesCommonChannels() {
        assertEquals(1, WifiMath.channelForFrequency(2_412));
        assertEquals(14, WifiMath.channelForFrequency(2_484));
        assertEquals(36, WifiMath.channelForFrequency(5_180));
        assertEquals(1, WifiMath.channelForFrequency(5_955));
        assertEquals(-1, WifiMath.channelForFrequency(900));
    }
}
