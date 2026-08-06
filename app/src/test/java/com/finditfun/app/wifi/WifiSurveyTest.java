package com.finditfun.app.wifi;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class WifiSurveyTest {
    @Test
    public void rejectsInvalidAndDuplicateSamples() {
        WifiSurvey survey = new WifiSurvey();
        assertFalse(survey.add(0, 0, 0, 0));
        assertTrue(survey.add(0, 0, -60, 1_000));
        assertFalse(survey.add(0, 0, -60, 1_200));
        assertEquals(1, survey.snapshot().samples.size());
    }

    @Test
    public void capturesMovementSignalChangeAndStationaryRefresh() {
        WifiSurvey survey = new WifiSurvey();
        assertTrue(survey.add(0, 0, -70, 1_000));
        assertTrue(survey.add(1, 0, -70, 1_100));
        assertTrue(survey.add(1, 0, -67, 1_200));
        assertTrue(survey.add(1, 0, -67, 2_700));
        assertEquals(4, survey.snapshot().samples.size());
    }

    @Test
    public void summarizesAndResetsSurvey() {
        WifiSurvey survey = new WifiSurvey();
        survey.add(0, 0, -80, 1_000);
        survey.add(1, 0, -60, 1_100);
        WifiSurvey.Snapshot snapshot = survey.snapshot();
        assertEquals(-60, snapshot.bestRssi);
        assertEquals(-70, snapshot.averageRssi);

        survey.reset();
        assertTrue(survey.snapshot().samples.isEmpty());
    }
}
