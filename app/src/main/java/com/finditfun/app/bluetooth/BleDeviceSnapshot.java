package com.finditfun.app.bluetooth;

import com.finditfun.app.signal.SignalTracker;

public final class BleDeviceSnapshot {
    public final String key;
    public final String anonymousId;
    public final String displayName;
    public final String observedName;
    public final String kind;
    public final boolean connectable;
    public final boolean paired;
    public final String manufacturer;
    public final String services;
    public final String radio;
    public final Integer txPower;
    public final int advertisementBytes;
    public final int advertiseFlags;
    public final SignalTracker.Snapshot signal;

    BleDeviceSnapshot(String key, String anonymousId, String displayName, String observedName,
                      String kind, boolean connectable, boolean paired, String manufacturer, String services,
                      String radio, Integer txPower, int advertisementBytes, int advertiseFlags,
                      SignalTracker.Snapshot signal) {
        this.key = key;
        this.anonymousId = anonymousId;
        this.displayName = displayName;
        this.observedName = observedName;
        this.kind = kind;
        this.connectable = connectable;
        this.paired = paired;
        this.manufacturer = manufacturer;
        this.services = services;
        this.radio = radio;
        this.txPower = txPower;
        this.advertisementBytes = advertisementBytes;
        this.advertiseFlags = advertiseFlags;
        this.signal = signal;
    }
}
