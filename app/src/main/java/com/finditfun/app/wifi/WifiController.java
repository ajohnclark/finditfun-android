package com.finditfun.app.wifi;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.wifi.ScanResult;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Build;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class WifiController {
    public interface Listener {
        void onStatus(String status);
        void onConnection(WifiConnectionSnapshot connection);
        void onAccessPoints(List<WifiAccessPoint> accessPoints);
    }

    private final Context context;
    private final Listener listener;
    private final WifiManager wifiManager;
    private final ConnectivityManager connectivityManager;
    private boolean receiverRegistered;
    private boolean running;

    public WifiController(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
        wifiManager = this.context.getSystemService(WifiManager.class);
        connectivityManager = this.context.getSystemService(ConnectivityManager.class);
    }

    public void start() {
        if (running) return;
        if (wifiManager == null) {
            listener.onStatus("Wi-Fi hardware is unavailable.");
            return;
        }
        if (!hasPermissions()) {
            listener.onStatus("Precise location and Nearby Wi-Fi permission are required.");
            return;
        }
        if (!wifiManager.isWifiEnabled()) {
            listener.onStatus("Wi-Fi is off. Turn it on, then return here.");
            return;
        }
        if (!receiverRegistered) {
            IntentFilter filter = new IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION);
            context.registerReceiver(scanReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            receiverRegistered = true;
        }
        running = true;
        listener.onStatus("Mapping the connected network and listening for scan results…");
        refreshConnection();
        readScanResults();
        requestScan();
    }

    public void stop() {
        if (receiverRegistered) {
            try {
                context.unregisterReceiver(scanReceiver);
            } catch (IllegalArgumentException ignored) {
                // The process may already have unregistered the receiver.
            }
        }
        receiverRegistered = false;
        running = false;
    }

    public boolean isRunning() {
        return running;
    }

    @SuppressLint("MissingPermission")
    public void refreshConnection() {
        if (!running || !hasPermissions()) return;
        WifiInfo info;
        try {
            info = activeWifiInfo();
        } catch (SecurityException error) {
            listener.onConnection(null);
            listener.onStatus("Android blocked access to the active Wi-Fi connection.");
            return;
        }
        if (info == null || !WifiMath.isValidRssi(info.getRssi())) {
            listener.onConnection(null);
            return;
        }
        int mloLinks = 0;
        if (Build.VERSION.SDK_INT >= 34 && info.getAssociatedMloLinks() != null) {
            mloLinks = info.getAssociatedMloLinks().size();
        }
        listener.onConnection(new WifiConnectionSnapshot(
                cleanSsid(info.getSSID()),
                info.getBSSID(),
                info.getRssi(),
                info.getFrequency(),
                info.getRxLinkSpeedMbps(),
                info.getTxLinkSpeedMbps(),
                info.getWifiStandard(),
                mloLinks
        ));
    }

    @SuppressLint("MissingPermission")
    public void requestScan() {
        if (!running || !hasPermissions() || wifiManager == null) return;
        boolean started;
        try {
            started = wifiManager.startScan();
        } catch (SecurityException error) {
            listener.onStatus("Wi-Fi scan permission was removed.");
            return;
        }
        if (started) {
            listener.onStatus("Wi-Fi scan requested — Android may throttle repeated scans.");
        } else {
            listener.onStatus("Using cached Wi-Fi results — Android throttled this scan.");
            readScanResults();
        }
    }

    private boolean hasPermissions() {
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) return false;
        return Build.VERSION.SDK_INT < 33
                || context.checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES)
                == PackageManager.PERMISSION_GRANTED;
    }

    @SuppressLint({"MissingPermission", "Deprecated"})
    private WifiInfo activeWifiInfo() {
        if (connectivityManager != null) {
            Network active = connectivityManager.getActiveNetwork();
            NetworkCapabilities capabilities = active == null ? null
                    : connectivityManager.getNetworkCapabilities(active);
            if (capabilities != null
                    && capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                    && capabilities.getTransportInfo() instanceof WifiInfo info) {
                return info;
            }
        }
        return wifiManager == null ? null : wifiManager.getConnectionInfo();
    }

    @SuppressLint("MissingPermission")
    private void readScanResults() {
        if (!running || !hasPermissions() || wifiManager == null) return;
        List<ScanResult> results;
        try {
            results = wifiManager.getScanResults();
        } catch (SecurityException error) {
            listener.onStatus("Wi-Fi scan results are unavailable without permission.");
            return;
        }
        ArrayList<WifiAccessPoint> accessPoints = new ArrayList<>();
        for (ScanResult result : results) {
            if (!WifiMath.isValidRssi(result.level)) continue;
            boolean newerRtt = Build.VERSION.SDK_INT >= 35
                    && result.is80211azNtbResponder();
            accessPoints.add(new WifiAccessPoint(
                    scanSsid(result),
                    result.BSSID,
                    result.level,
                    result.frequency,
                    WifiMath.channelForFrequency(result.frequency),
                    result.channelWidth,
                    result.getWifiStandard(),
                    result.is80211mcResponder() || newerRtt
            ));
        }
        accessPoints.sort(Comparator.comparingInt((WifiAccessPoint item) -> item.rssi)
                .reversed());
        listener.onAccessPoints(accessPoints);
        listener.onStatus(accessPoints.size() + " access points visible · connected network updates live");
    }

    private final BroadcastReceiver scanReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            readScanResults();
            refreshConnection();
        }
    };

    private static String scanSsid(ScanResult result) {
        String value;
        if (Build.VERSION.SDK_INT >= 33) {
            value = result.getWifiSsid() == null ? null : result.getWifiSsid().toString();
        } else {
            //noinspection deprecation
            value = result.SSID;
        }
        return value == null || value.trim().isEmpty() ? "Hidden network" : value;
    }

    private static String cleanSsid(String value) {
        if (value == null || value.equals(WifiManager.UNKNOWN_SSID)) return "Connected Wi-Fi";
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }
}
