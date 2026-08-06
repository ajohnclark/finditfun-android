package com.finditfun.app.wifi;

public final class WifiAccessPoint {
    public final String ssid;
    public final String bssid;
    public final int rssi;
    public final int frequencyMhz;
    public final int channel;
    public final int channelWidth;
    public final int wifiStandard;
    public final boolean rttCapable;

    public WifiAccessPoint(String ssid, String bssid, int rssi, int frequencyMhz,
                           int channel, int channelWidth, int wifiStandard,
                           boolean rttCapable) {
        this.ssid = ssid;
        this.bssid = bssid;
        this.rssi = rssi;
        this.frequencyMhz = frequencyMhz;
        this.channel = channel;
        this.channelWidth = channelWidth;
        this.wifiStandard = wifiStandard;
        this.rttCapable = rttCapable;
    }
}
