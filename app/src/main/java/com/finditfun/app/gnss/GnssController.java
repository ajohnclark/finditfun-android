package com.finditfun.app.gnss;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.GnssStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class GnssController {
    public interface Listener {
        void onStatus(String status);
        void onSatellites(List<Satellite> satellites);
    }

    public static final class Satellite {
        public final String constellation;
        public final int svid;
        public final float cn0;
        public final float azimuth;
        public final float elevation;
        public final Float carrierMhz;
        public final boolean usedInFix;

        Satellite(String constellation, int svid, float cn0, float azimuth, float elevation,
                  Float carrierMhz, boolean usedInFix) {
            this.constellation = constellation;
            this.svid = svid;
            this.cn0 = cn0;
            this.azimuth = azimuth;
            this.elevation = elevation;
            this.carrierMhz = carrierMhz;
            this.usedInFix = usedInFix;
        }
    }

    private final Context context;
    private final Listener listener;
    private final LocationManager manager;
    private boolean running;

    public GnssController(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
        this.manager = context.getSystemService(LocationManager.class);
    }

    @SuppressLint("MissingPermission")
    public void start() {
        if (running) return;
        if (context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            listener.onStatus("Precise location permission is required for satellite data.");
            return;
        }
        if (manager == null || !manager.hasProvider(LocationManager.GPS_PROVIDER)) {
            listener.onStatus("GNSS hardware is unavailable.");
            return;
        }
        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            listener.onStatus("Location is off. Enable it to see satellites.");
            return;
        }
        manager.registerGnssStatusCallback(context.getMainExecutor(), callback);
        manager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1_000, 0,
                context.getMainExecutor(), locationListener);
        running = true;
        listener.onStatus("Listening to the sky… move near a window or outdoors.");
    }

    public void stop() {
        if (!running || manager == null) return;
        manager.unregisterGnssStatusCallback(callback);
        manager.removeUpdates(locationListener);
        running = false;
    }

    private final LocationListener locationListener = new LocationListener() {
        @Override
        public void onLocationChanged(Location location) {
            // Keeping a location request active makes GNSS status updates flow.
            // Coordinates are intentionally neither displayed nor stored.
        }
    };

    private final GnssStatus.Callback callback = new GnssStatus.Callback() {
        @Override
        public void onStarted() {
            listener.onStatus("GNSS receiver active — acquiring satellites…");
        }

        @Override
        public void onStopped() {
            listener.onStatus("GNSS receiver stopped.");
        }

        @Override
        public void onSatelliteStatusChanged(GnssStatus status) {
            ArrayList<Satellite> satellites = new ArrayList<>();
            for (int index = 0; index < status.getSatelliteCount(); index++) {
                Float carrier = status.hasCarrierFrequencyHz(index)
                        ? status.getCarrierFrequencyHz(index) / 1_000_000f : null;
                satellites.add(new Satellite(
                        constellationName(status.getConstellationType(index)),
                        status.getSvid(index),
                        status.getCn0DbHz(index),
                        status.getAzimuthDegrees(index),
                        status.getElevationDegrees(index),
                        carrier,
                        status.usedInFix(index)
                ));
            }
            satellites.sort(Comparator.comparingDouble((Satellite item) -> item.cn0).reversed());
            listener.onSatellites(satellites);
        }
    };

    private static String constellationName(int type) {
        return switch (type) {
            case GnssStatus.CONSTELLATION_GPS -> "GPS";
            case GnssStatus.CONSTELLATION_GLONASS -> "GLONASS";
            case GnssStatus.CONSTELLATION_GALILEO -> "Galileo";
            case GnssStatus.CONSTELLATION_BEIDOU -> "BeiDou";
            case GnssStatus.CONSTELLATION_QZSS -> "QZSS";
            case GnssStatus.CONSTELLATION_IRNSS -> "NavIC";
            case GnssStatus.CONSTELLATION_SBAS -> "SBAS";
            default -> "Other";
        };
    }
}
