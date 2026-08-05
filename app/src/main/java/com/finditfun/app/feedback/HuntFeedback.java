package com.finditfun.app.feedback;

import android.content.Context;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.os.VibratorManager;
import com.finditfun.app.signal.SignalMath;

public final class HuntFeedback implements AutoCloseable {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final ToneGenerator tone = new ToneGenerator(AudioManager.STREAM_MUSIC, 55);
    private final Vibrator vibrator;
    private boolean soundEnabled = true;
    private boolean hapticsEnabled = true;
    private boolean running;
    private Integer rssi;
    private long lastHapticMillis;

    public HuntFeedback(Context context) {
        VibratorManager manager = context.getSystemService(VibratorManager.class);
        vibrator = manager == null ? null : manager.getDefaultVibrator();
    }

    public void start() {
        if (running) return;
        running = true;
        handler.post(pulse);
    }

    public void update(Integer freshRssi) {
        rssi = freshRssi;
    }

    public void setSoundEnabled(boolean enabled) {
        soundEnabled = enabled;
    }

    public boolean isSoundEnabled() {
        return soundEnabled;
    }

    public void setHapticsEnabled(boolean enabled) {
        hapticsEnabled = enabled;
    }

    public boolean isHapticsEnabled() {
        return hapticsEnabled;
    }

    private final Runnable pulse = new Runnable() {
        @Override
        public void run() {
            if (!running) return;
            Integer current = rssi;
            long delay = current == null ? 500 : SignalMath.clickIntervalMillis(current);
            if (current != null) {
                if (soundEnabled) {
                    tone.startTone(ToneGenerator.TONE_PROP_BEEP, 35);
                }
                long now = SystemClock.elapsedRealtime();
                if (hapticsEnabled && vibrator != null && vibrator.hasVibrator()
                        && now - lastHapticMillis >= Math.max(160, delay)) {
                    vibrator.vibrate(VibrationEffect.createOneShot(18, 80));
                    lastHapticMillis = now;
                }
            }
            handler.postDelayed(this, delay);
        }
    };

    @Override
    public void close() {
        running = false;
        rssi = null;
        handler.removeCallbacks(pulse);
        tone.release();
        if (vibrator != null) vibrator.cancel();
    }
}
