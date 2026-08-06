package com.finditfun.app.wifi;

public final class WifiConnectionSnapshot {
    public final String ssid;
    public final String bssid;
    public final int rssi;
    public final int frequencyMhz;
    public final int rxMbps;
    public final int txMbps;
    public final int wifiStandard;
    public final int mloLinkCount;

    public WifiConnectionSnapshot(String ssid, String bssid, int rssi,
                                  int frequencyMhz, int rxMbps, int txMbps,
                                  int wifiStandard, int mloLinkCount) {
        this.ssid = ssid;
        this.bssid = bssid;
        this.rssi = rssi;
        this.frequencyMhz = frequencyMhz;
        this.rxMbps = rxMbps;
        this.txMbps = txMbps;
        this.wifiStandard = wifiStandard;
        this.mloLinkCount = mloLinkCount;
    }
}
