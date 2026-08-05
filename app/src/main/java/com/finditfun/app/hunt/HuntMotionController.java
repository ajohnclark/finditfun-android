package com.finditfun.app.hunt;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;

public final class HuntMotionController implements SensorEventListener {
    public interface Listener {
        void onMotion(float headingDegrees, float xSteps, float ySteps, int steps,
                      String status);
    }

    private static final float STEP_LENGTH = 1f;

    private final Context context;
    private final SensorManager manager;
    private final Sensor rotationSensor;
    private final boolean absoluteHeading;
    private final Sensor stepSensor;
    private final Listener listener;
    private final float[] rotationMatrix = new float[9];
    private final float[] orientation = new float[3];

    private float headingDegrees;
    private float xSteps;
    private float ySteps;
    private int steps;
    private boolean running;
    private boolean stepsActive;
    private String status = "Orientation starting…";

    public HuntMotionController(Context context, Listener listener) {
        this.context = context;
        manager = context.getSystemService(SensorManager.class);
        Sensor absolute = manager == null ? null
                : manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR);
        if (absolute == null && manager != null) {
            absolute = manager.getDefaultSensor(Sensor.TYPE_GEOMAGNETIC_ROTATION_VECTOR);
        }
        Sensor relative = manager == null ? null
                : manager.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        rotationSensor = absolute != null ? absolute : relative;
        absoluteHeading = absolute != null;
        stepSensor = manager == null ? null
                : manager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
        this.listener = listener;
    }

    public void start() {
        if (running || manager == null) return;
        if (rotationSensor == null) {
            status = "No orientation sensor available";
            notifyListener();
            return;
        }
        manager.registerListener(this, rotationSensor, SensorManager.SENSOR_DELAY_GAME);
        stepsActive = stepSensor != null
                && context.checkSelfPermission(Manifest.permission.ACTIVITY_RECOGNITION)
                == PackageManager.PERMISSION_GRANTED
                && manager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_NORMAL);
        String headingKind = absoluteHeading ? "compass heading" : "relative gyro heading";
        status = stepsActive ? headingKind + " + steps active"
                : headingKind + " · allow Physical activity for trail steps";
        running = true;
        notifyListener();
    }

    public void stop() {
        if (manager != null) manager.unregisterListener(this);
        running = false;
        stepsActive = false;
    }

    public void reset() {
        xSteps = 0;
        ySteps = 0;
        steps = 0;
        notifyListener();
    }

    public float headingDegrees() {
        return headingDegrees;
    }

    public float xSteps() {
        return xSteps;
    }

    public float ySteps() {
        return ySteps;
    }

    public int steps() {
        return steps;
    }

    public boolean stepsActive() {
        return stepsActive;
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor == rotationSensor) {
            SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
            SensorManager.getOrientation(rotationMatrix, orientation);
            headingDegrees = (float) ((Math.toDegrees(orientation[0]) + 360.0) % 360.0);
            notifyListener();
        } else if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR) {
            double radians = Math.toRadians(headingDegrees);
            xSteps += (float) Math.sin(radians) * STEP_LENGTH;
            ySteps -= (float) Math.cos(radians) * STEP_LENGTH;
            steps++;
            notifyListener();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        if (sensor == rotationSensor
                && accuracy == SensorManager.SENSOR_STATUS_UNRELIABLE) {
            status = "Compass uncertain · move the phone in a figure eight";
            notifyListener();
        }
    }

    private void notifyListener() {
        listener.onMotion(headingDegrees, xSteps, ySteps, steps, status);
    }
}
