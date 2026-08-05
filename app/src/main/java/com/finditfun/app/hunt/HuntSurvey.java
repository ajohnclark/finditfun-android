package com.finditfun.app.hunt;

import com.finditfun.app.signal.SignalMath;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public final class HuntSurvey {
    public static final int SECTOR_COUNT = 12;
    private static final int MAX_POINTS = 500;
    private static final int MAX_SAMPLES_PER_SECTOR = 60;

    public static final class TrailPoint {
        public final float x;
        public final float y;
        public final int rssi;

        TrailPoint(float x, float y, int rssi) {
            this.x = x;
            this.y = y;
            this.rssi = rssi;
        }
    }

    public static final class Snapshot {
        public final List<TrailPoint> points;
        public final int[] sectorMedians;
        public final int[] sectorSamples;
        public final int totalSamples;
        public final int coveredSectors;
        public final int hotSector;
        public final float hotBearing;
        public final int readinessPercent;

        Snapshot(List<TrailPoint> points, int[] sectorMedians, int[] sectorSamples,
                 int totalSamples, int coveredSectors, int hotSector, float hotBearing,
                 int readinessPercent) {
            this.points = points;
            this.sectorMedians = sectorMedians;
            this.sectorSamples = sectorSamples;
            this.totalSamples = totalSamples;
            this.coveredSectors = coveredSectors;
            this.hotSector = hotSector;
            this.hotBearing = hotBearing;
            this.readinessPercent = readinessPercent;
        }

        public boolean hasExperimentalBearing() {
            return coveredSectors >= 8 && totalSamples >= 24 && hotSector >= 0
                    && readinessPercent >= 55;
        }
    }

    private final ArrayList<TrailPoint> points = new ArrayList<>();
    @SuppressWarnings("unchecked")
    private final ArrayList<Integer>[] sectors = new ArrayList[SECTOR_COUNT];
    private int totalSamples;

    public HuntSurvey() {
        for (int i = 0; i < sectors.length; i++) sectors[i] = new ArrayList<>();
    }

    public synchronized void add(float headingDegrees, float x, float y, int rssi) {
        if (!SignalMath.isValidRssi(rssi)) return;
        float normalized = normalizeHeading(headingDegrees);
        int sector = Math.min(SECTOR_COUNT - 1,
                (int) (normalized / (360f / SECTOR_COUNT)));
        ArrayList<Integer> bucket = sectors[sector];
        bucket.add(rssi);
        if (bucket.size() > MAX_SAMPLES_PER_SECTOR) bucket.remove(0);
        points.add(new TrailPoint(x, y, rssi));
        if (points.size() > MAX_POINTS) points.remove(0);
        totalSamples++;
    }

    public synchronized void reset() {
        points.clear();
        for (ArrayList<Integer> sector : sectors) sector.clear();
        totalSamples = 0;
    }

    public synchronized Snapshot snapshot() {
        int[] medians = new int[SECTOR_COUNT];
        int[] samples = new int[SECTOR_COUNT];
        Arrays.fill(medians, -127);
        int covered = 0;
        int hot = -1;
        int best = -127;
        ArrayList<Integer> filledMedians = new ArrayList<>();
        for (int i = 0; i < SECTOR_COUNT; i++) {
            samples[i] = sectors[i].size();
            Integer median = SignalMath.median(sectors[i]);
            if (median == null) continue;
            medians[i] = median;
            filledMedians.add(median);
            covered++;
            if (median > best) {
                best = median;
                hot = i;
            }
        }
        float bearing = hot < 0 ? 0 : (hot + 0.5f) * (360f / SECTOR_COUNT);
        int readiness = 0;
        if (covered >= 3) {
            Integer baselineValue = SignalMath.median(filledMedians);
            int baseline = baselineValue == null ? best : baselineValue;
            int runnerUp = -127;
            int wellSampled = 0;
            for (int i = 0; i < SECTOR_COUNT; i++) {
                if (samples[i] >= 3) wellSampled++;
                if (medians[i] == -127 || i == hot) continue;
                int gap = Math.abs(i - hot);
                int circularGap = Math.min(gap, SECTOR_COUNT - gap);
                if (circularGap > 1) runnerUp = Math.max(runnerUp, medians[i]);
            }
            if (runnerUp == -127) runnerUp = baseline;

            double coverageScore = clamp((covered - 4.0) / (SECTOR_COUNT - 4.0));
            double sampleScore = wellSampled / (double) SECTOR_COUNT;
            double sweepScore = 0.75 * coverageScore + 0.25 * sampleScore;
            double contrastScore = clamp((best - baseline - 1.0) / 6.0);
            double separationScore = clamp((best - runnerUp) / 3.0);
            double evidenceScore = 0.45 * contrastScore + 0.55 * separationScore;
            readiness = (int) Math.round(100.0 * Math.min(sweepScore, evidenceScore));
        }
        return new Snapshot(Collections.unmodifiableList(new ArrayList<>(points)), medians,
                samples, totalSamples, covered, hot, bearing, readiness);
    }

    private static double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    public static float normalizeHeading(float degrees) {
        float normalized = degrees % 360f;
        return normalized < 0 ? normalized + 360f : normalized;
    }
}
