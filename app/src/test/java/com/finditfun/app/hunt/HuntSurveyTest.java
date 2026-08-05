package com.finditfun.app.hunt;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class HuntSurveyTest {
    @Test
    public void headingsWrapIntoCompassRange() {
        assertEquals(350f, HuntSurvey.normalizeHeading(-10f), 0.001f);
        assertEquals(5f, HuntSurvey.normalizeHeading(365f), 0.001f);
    }

    @Test
    public void refusesAnArrowFromOneOrTwoHeadings() {
        HuntSurvey survey = new HuntSurvey();
        for (int i = 0; i < 40; i++) {
            survey.add(5, 0, 0, -42);
            survey.add(35, 0, 0, -85);
        }
        HuntSurvey.Snapshot snapshot = survey.snapshot();
        assertEquals(2, snapshot.coveredSectors);
        assertFalse(snapshot.hasExperimentalBearing());
        assertTrue(snapshot.readinessPercent < 20);
    }

    @Test
    public void completeDirectionalSweepFindsTheHotBearing() {
        HuntSurvey survey = new HuntSurvey();
        double truth = 90;
        for (int sector = 0; sector < HuntSurvey.SECTOR_COUNT; sector++) {
            double heading = (sector + 0.5) * 360.0 / HuntSurvey.SECTOR_COUNT;
            double alignment = Math.cos(Math.toRadians(heading - truth));
            int rssi = (int) Math.round(-60 - 12 * (1 - alignment) / 2);
            for (int sample = 0; sample < 8; sample++) {
                survey.add((float) heading, sector, sample, rssi);
            }
        }
        HuntSurvey.Snapshot snapshot = survey.snapshot();
        assertEquals(HuntSurvey.SECTOR_COUNT, snapshot.coveredSectors);
        assertTrue(snapshot.hasExperimentalBearing());
        assertTrue(snapshot.readinessPercent >= 55);
        assertTrue(circularDifference(snapshot.hotBearing, (float) truth) < 25);
    }

    @Test
    public void flatSignalDoesNotManufactureDirection() {
        HuntSurvey survey = new HuntSurvey();
        for (int sector = 0; sector < HuntSurvey.SECTOR_COUNT; sector++) {
            float heading = (sector + 0.5f) * 360f / HuntSurvey.SECTOR_COUNT;
            for (int sample = 0; sample < 8; sample++) survey.add(heading, 0, 0, -60);
        }
        HuntSurvey.Snapshot snapshot = survey.snapshot();
        assertEquals(0, snapshot.readinessPercent);
        assertFalse(snapshot.hasExperimentalBearing());
    }

    @Test
    public void oppositeHotSectorsStayBelowReadinessThreshold() {
        HuntSurvey survey = new HuntSurvey();
        for (int sector = 0; sector < HuntSurvey.SECTOR_COUNT; sector++) {
            float heading = (sector + 0.5f) * 360f / HuntSurvey.SECTOR_COUNT;
            int rssi = sector == 0 || sector == HuntSurvey.SECTOR_COUNT / 2 ? -45 : -75;
            for (int sample = 0; sample < 5; sample++) survey.add(heading, 0, 0, rssi);
        }
        HuntSurvey.Snapshot snapshot = survey.snapshot();
        assertTrue(snapshot.readinessPercent < 55);
        assertFalse(snapshot.hasExperimentalBearing());
    }

    @Test
    public void trailKeepsRelativePositionWithEachRealReading() {
        HuntSurvey survey = new HuntSurvey();
        survey.add(0, 1.5f, -2f, -65);
        HuntSurvey.Snapshot snapshot = survey.snapshot();
        assertEquals(1, snapshot.points.size());
        assertEquals(1.5f, snapshot.points.get(0).x, 0.001f);
        assertEquals(-2f, snapshot.points.get(0).y, 0.001f);
        assertEquals(-65, snapshot.points.get(0).rssi);
    }

    private static float circularDifference(float left, float right) {
        float difference = Math.abs(left - right) % 360f;
        return Math.min(difference, 360f - difference);
    }
}
