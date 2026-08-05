package com.finditfun.app.signal;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

public final class SignalTracker {
    private static final long HISTORY_MS = 5 * 60 * 1_000L;

    private final Deque<SignalSample> samples = new ArrayDeque<>();

    public synchronized void add(int rssi, long timeMillis) {
        if (!SignalMath.isValidRssi(rssi)) {
            return;
        }
        samples.addLast(new SignalSample(rssi, timeMillis));
        prune(timeMillis);
    }

    private void prune(long nowMillis) {
        long cutoff = nowMillis - HISTORY_MS;
        while (!samples.isEmpty() && samples.peekFirst().timeMillis < cutoff) {
            samples.removeFirst();
        }
    }

    public synchronized Snapshot snapshot(long nowMillis) {
        if (samples.isEmpty()) {
            return Snapshot.empty();
        }
        List<SignalSample> copy = new ArrayList<>(samples);
        SignalSample last = copy.get(copy.size() - 1);
        long ageMillis = Math.max(0, nowMillis - last.timeMillis);
        int peak = -127;
        for (SignalSample sample : copy) peak = Math.max(peak, sample.rssi);
        return new Snapshot(
                SignalMath.liveRssi(copy, nowMillis),
                SignalMath.weightedRecentRssi(copy, nowMillis),
                peak,
                ageMillis,
                ageMillis < SignalMath.FRESH_WINDOW_MS,
                SignalMath.trend(copy, nowMillis),
                copy.size()
        );
    }

    public static final class Snapshot {
        public final int liveRssi;
        public final int smoothedRssi;
        public final int peakRssi;
        public final long ageMillis;
        public final boolean fresh;
        public final SignalMath.Trend trend;
        public final int sampleCount;

        private Snapshot(int liveRssi, int smoothedRssi, int peakRssi, long ageMillis,
                         boolean fresh, SignalMath.Trend trend, int sampleCount) {
            this.liveRssi = liveRssi;
            this.smoothedRssi = smoothedRssi;
            this.peakRssi = peakRssi;
            this.ageMillis = ageMillis;
            this.fresh = fresh;
            this.trend = trend;
            this.sampleCount = sampleCount;
        }

        public static Snapshot empty() {
            return new Snapshot(-127, -127, -127, Long.MAX_VALUE, false,
                    SignalMath.Trend.UNKNOWN, 0);
        }
    }
}
