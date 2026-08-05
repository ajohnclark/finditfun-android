package com.finditfun.app.signal;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

public final class SignalMathTest {
    @Test
    public void validatesOnlyRealNegativeRssiValues() {
        assertTrue(SignalMath.isValidRssi(-1));
        assertTrue(SignalMath.isValidRssi(-126));
        assertFalse(SignalMath.isValidRssi(0));
        assertFalse(SignalMath.isValidRssi(-127));
    }

    @Test
    public void medianResistsSingleReflectedSpike() {
        assertEquals(Integer.valueOf(-70), SignalMath.median(Arrays.asList(-72, -70, -20)));
    }

    @Test
    public void fractionIsClampedToUsefulRange() {
        assertEquals(0.0, SignalMath.fraction(-120), 0.0001);
        assertEquals(1.0, SignalMath.fraction(-10), 0.0001);
        assertEquals(35.0 / 60.0, SignalMath.fraction(-60), 0.0001);
    }

    @Test
    public void trendDetectsWarmerAndColderWindows() {
        long now = 20_000;
        List<SignalSample> warmer = Arrays.asList(
                new SignalSample(-82, 11_000),
                new SignalSample(-80, 14_000),
                new SignalSample(-65, 17_000),
                new SignalSample(-64, 19_000));
        assertEquals(SignalMath.Trend.WARMER, SignalMath.trend(warmer, now));

        List<SignalSample> colder = Arrays.asList(
                new SignalSample(-55, 11_000),
                new SignalSample(-57, 14_000),
                new SignalSample(-76, 17_000),
                new SignalSample(-78, 19_000));
        assertEquals(SignalMath.Trend.COLDER, SignalMath.trend(colder, now));
    }

    @Test
    public void clickCadenceAcceleratesAsSignalStrengthens() {
        assertTrue(SignalMath.clickIntervalMillis(-90) > SignalMath.clickIntervalMillis(-60));
        assertEquals(80, SignalMath.clickIntervalMillis(-40));
        assertEquals(1_200, SignalMath.clickIntervalMillis(-110));
    }

    @Test
    public void trackerMarksOldContactStale() {
        SignalTracker tracker = new SignalTracker();
        tracker.add(-60, 1_000);
        assertTrue(tracker.snapshot(5_000).fresh);
        assertFalse(tracker.snapshot(9_001).fresh);
        assertEquals(-60, tracker.snapshot(9_001).liveRssi);
    }
}
