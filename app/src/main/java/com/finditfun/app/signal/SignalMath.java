package com.finditfun.app.signal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SignalMath {
    public enum Trend {
        WARMER,
        COLDER,
        STEADY,
        UNKNOWN
    }

    public static final long LIVE_WINDOW_MS = 3_500;
    private static final long TREND_SEGMENT_MS = 5_000;
    public static final long FRESH_WINDOW_MS = 8_000;

    private SignalMath() {}

    public static boolean isValidRssi(int rssi) {
        return rssi < 0 && rssi > -127;
    }

    public static Integer median(List<Integer> values) {
        if (values.isEmpty()) {
            return null;
        }
        ArrayList<Integer> sorted = new ArrayList<>(values);
        Collections.sort(sorted);
        return sorted.get(sorted.size() / 2);
    }

    public static int liveRssi(List<SignalSample> samples, long nowMillis) {
        if (samples.isEmpty()) {
            return -127;
        }
        ArrayList<Integer> recent = new ArrayList<>();
        long cutoff = nowMillis - LIVE_WINDOW_MS;
        for (SignalSample sample : samples) {
            if (sample.timeMillis >= cutoff) {
                recent.add(sample.rssi);
            }
        }
        Integer median = median(recent);
        return median != null ? median : samples.get(samples.size() - 1).rssi;
    }

    public static int weightedRecentRssi(List<SignalSample> samples, long nowMillis) {
        if (samples.isEmpty()) return -127;
        long cutoff = nowMillis - 6_000;
        double weightedTotal = 0;
        double weightTotal = 0;
        for (SignalSample sample : samples) {
            if (sample.timeMillis < cutoff) continue;
            double ageFraction = Math.min(1.0,
                    Math.max(0.0, (nowMillis - sample.timeMillis) / 6_000.0));
            double weight = 1.0 - 0.75 * ageFraction;
            weightedTotal += sample.rssi * weight;
            weightTotal += weight;
        }
        return weightTotal == 0
                ? samples.get(samples.size() - 1).rssi
                : (int) Math.round(weightedTotal / weightTotal);
    }

    public static Trend trend(List<SignalSample> samples, long nowMillis) {
        ArrayList<Integer> near = new ArrayList<>();
        ArrayList<Integer> prior = new ArrayList<>();
        long nearCutoff = nowMillis - TREND_SEGMENT_MS;
        long priorCutoff = nowMillis - 2 * TREND_SEGMENT_MS;

        for (SignalSample sample : samples) {
            if (sample.timeMillis >= nearCutoff) {
                near.add(sample.rssi);
            } else if (sample.timeMillis >= priorCutoff) {
                prior.add(sample.rssi);
            }
        }
        if (near.size() < 2 || prior.size() < 2) {
            return Trend.UNKNOWN;
        }
        int delta = median(near) - median(prior);
        if (delta >= 4) {
            return Trend.WARMER;
        }
        if (delta <= -4) {
            return Trend.COLDER;
        }
        return Trend.STEADY;
    }

    public static double fraction(int rssi) {
        int clamped = Math.max(-95, Math.min(-35, rssi));
        return (clamped + 95.0) / 60.0;
    }

    public static String proximity(int rssi) {
        if (rssi >= -48) return "VERY CLOSE";
        if (rssi >= -63) return "CLOSE";
        if (rssi >= -77) return "SAME ROOM";
        if (rssi >= -90) return "FAR / BLOCKED";
        return "VERY FAR";
    }

    public static long clickIntervalMillis(int rssi) {
        double strength = Math.max(0.0, Math.min(1.0, (rssi + 100.0) / 55.0));
        double eased = strength * strength;
        return Math.round(1_200.0 - 1_120.0 * eased);
    }
}
