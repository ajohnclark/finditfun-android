package com.finditfun.app.bluetooth;

import com.finditfun.app.signal.SignalTracker;

final class BleDeviceRecord {
    private final String key;
    private final String anonymousId;
    private final SignalTracker signal = new SignalTracker();
    private String name;
    private String pairedName;
    private String kind;
    private boolean connectable;
    private boolean paired;
    private String manufacturer = "Not advertised";
    private String services = "None advertised";
    private String radio = "BLE";
    private Integer txPower;
    private int advertisementBytes;
    private int advertiseFlags = -1;

    BleDeviceRecord(String key, String kind) {
        this.key = key;
        this.kind = kind;
        this.anonymousId = String.format("%04X", key.hashCode() & 0xFFFF);
    }

    synchronized void update(String observedName, String observedKind, boolean observedConnectable,
                             String observedManufacturer, String observedServices,
                             String observedRadio, Integer observedTxPower,
                             int observedAdvertisementBytes, int observedAdvertiseFlags,
                             boolean observedPaired, int rssi, long nowMillis) {
        if (observedName != null && !observedName.trim().isEmpty()) {
            name = observedName.trim();
        }
        if (observedKind != null && !observedKind.trim().isEmpty()) {
            kind = observedKind;
        }
        connectable = connectable || observedConnectable;
        paired = paired || observedPaired;
        manufacturer = observedManufacturer;
        services = observedServices;
        radio = observedRadio;
        txPower = observedTxPower;
        advertisementBytes = observedAdvertisementBytes;
        advertiseFlags = observedAdvertiseFlags;
        signal.add(rssi, nowMillis);
    }

    synchronized void updatePaired(String pairedName, String pairedKind, String pairedRadio) {
        if (pairedName != null && !pairedName.trim().isEmpty()) this.pairedName = pairedName.trim();
        if (pairedKind != null && !pairedKind.trim().isEmpty()) kind = pairedKind;
        radio = pairedRadio;
        paired = true;
    }

    synchronized BleDeviceSnapshot snapshot(long nowMillis) {
        String label = pairedName != null ? pairedName
                : name != null ? name : kind + " #" + anonymousId;
        return new BleDeviceSnapshot(key, anonymousId, label, name, kind, connectable, paired,
                manufacturer, services, radio, txPower, advertisementBytes, advertiseFlags,
                signal.snapshot(nowMillis));
    }
}
