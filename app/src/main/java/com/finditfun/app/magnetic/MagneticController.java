package com.finditfun.app.magnetic;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public final class MagneticController implements SensorEventListener {
    public interface Listener {
        void onStatus(String status);
        void onReading(float x, float y, float z, float magnitude);
    }

    private final SensorManager manager;
    private final Sensor sensor;
    private final Listener listener;
    private boolean running;

    public MagneticController(Context context, Listener listener) {
        manager = context.getSystemService(SensorManager.class);
        sensor = manager == null ? null : manager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD);
        this.listener = listener;
    }

    public void start() {
        if (running) return;
        if (manager == null || sensor == null) {
            listener.onStatus("No magnetic-field sensor was found.");
            return;
        }
        running = manager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME);
        listener.onStatus(running ? "Live magnetic field — values are microtesla (µT)."
                : "Could not start the magnetic-field sensor.");
    }

    public void stop() {
        if (manager != null) manager.unregisterListener(this);
        running = false;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];
        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        listener.onReading(x, y, z, magnitude);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            listener.onStatus("Sensor wants calibration — move the phone in a figure eight.");
        }
    }
}
