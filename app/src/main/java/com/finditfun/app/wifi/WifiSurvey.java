package com.finditfun.app.wifi;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class WifiSurvey {
    private static final int MAX_SAMPLES = 800;
    private static final long MAX_STATIONARY_INTERVAL_MS = 1_500;
    private static final float MIN_POSITION_CHANGE = 0.18f;

    public static final class Sample {
        public final float x;
        public final float y;
        public final int rssi;
        public final long timestampMillis;

        public Sample(float x, float y, int rssi, long timestampMillis) {
            this.x = x;
            this.y = y;
            this.rssi = rssi;
            this.timestampMillis = timestampMillis;
        }
    }

    public static final class Snapshot {
        public final List<Sample> samples;
        public final int bestRssi;
        public final int averageRssi;

        Snapshot(List<Sample> samples, int bestRssi, int averageRssi) {
            this.samples = Collections.unmodifiableList(samples);
            this.bestRssi = bestRssi;
            this.averageRssi = averageRssi;
        }
    }

    private final ArrayList<Sample> samples = new ArrayList<>();

    public boolean add(float x, float y, int rssi, long timestampMillis) {
        if (!WifiMath.isValidRssi(rssi)) return false;
        if (!samples.isEmpty()) {
            Sample previous = samples.get(samples.size() - 1);
            float dx = x - previous.x;
            float dy = y - previous.y;
            boolean moved = Math.sqrt(dx * dx + dy * dy) >= MIN_POSITION_CHANGE;
            boolean changed = Math.abs(rssi - previous.rssi) >= 2;
            boolean aged = timestampMillis - previous.timestampMillis
                    >= MAX_STATIONARY_INTERVAL_MS;
            if (!moved && !changed && !aged) return false;
        }
        samples.add(new Sample(x, y, rssi, timestampMillis));
        while (samples.size() > MAX_SAMPLES) samples.remove(0);
        return true;
    }

    public Snapshot snapshot() {
        if (samples.isEmpty()) {
            return new Snapshot(new ArrayList<>(), Integer.MIN_VALUE, 0);
        }
        int best = Integer.MIN_VALUE;
        long total = 0;
        for (Sample sample : samples) {
            best = Math.max(best, sample.rssi);
            total += sample.rssi;
        }
        return new Snapshot(new ArrayList<>(samples), best,
                Math.round(total / (float) samples.size()));
    }

    public void reset() {
        samples.clear();
    }
}
