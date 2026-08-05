package com.finditfun.app.signal;

public final class SignalSample {
    public final int rssi;
    public final long timeMillis;

    public SignalSample(int rssi, long timeMillis) {
        this.rssi = rssi;
        this.timeMillis = timeMillis;
    }
}
