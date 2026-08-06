package com.finditfun.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import com.finditfun.app.wifi.WifiAccessPoint;
import com.finditfun.app.wifi.WifiMath;
import java.util.ArrayList;
import java.util.List;

public final class WifiSpectrumView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<WifiAccessPoint> accessPoints = new ArrayList<>();
    private String connectedBssid;

    public WifiSpectrumView(Context context) {
        super(context);
    }

    public void setAccessPoints(List<WifiAccessPoint> accessPoints, String connectedBssid) {
        this.accessPoints = new ArrayList<>(accessPoints);
        this.connectedBssid = connectedBssid;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(10, 24, 35));
        float density = getResources().getDisplayMetrics().density;
        float labelWidth = 48f * density;
        float right = getWidth() - 14f * density;
        float laneHeight = getHeight() / 3f;
        String[] labels = {"2.4", "5", "6"};

        for (int lane = 0; lane < 3; lane++) {
            float centerY = laneHeight * lane + laneHeight / 2f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(1f);
            paint.setColor(Color.rgb(45, 70, 88));
            canvas.drawLine(labelWidth, centerY, right, centerY, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(12f * getResources().getDisplayMetrics().scaledDensity);
            paint.setColor(Color.rgb(145, 163, 178));
            canvas.drawText(labels[lane] + " GHz", 8f * density, centerY + 4f * density, paint);
        }

        int drawnLabels = 0;
        int limit = Math.min(40, accessPoints.size());
        for (int index = limit - 1; index >= 0; index--) {
            WifiAccessPoint item = accessPoints.get(index);
            int lane = laneFor(item.frequencyMhz);
            if (lane < 0) continue;
            float laneCenter = laneHeight * lane + laneHeight / 2f;
            float x = frequencyX(item.frequencyMhz, lane, labelWidth, right);
            float jitter = ((item.bssid == null ? item.ssid.hashCode() : item.bssid.hashCode())
                    & 0x7fffffff) % 31 - 15;
            float y = laneCenter + jitter / 15f * laneHeight * 0.28f;
            float fraction = (float) WifiMath.strengthFraction(item.rssi);
            float radius = (5f + 10f * fraction) * density;
            boolean connected = connectedBssid != null && connectedBssid.equals(item.bssid);
            int color = lane == 0 ? Color.rgb(155, 229, 100)
                    : lane == 1 ? Color.rgb(72, 217, 232)
                    : Color.rgb(183, 140, 255);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.argb(connected ? 255 : 145,
                    Color.red(color), Color.green(color), Color.blue(color)));
            canvas.drawCircle(x, y, radius, paint);
            if (connected) {
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth(3f * density);
                paint.setColor(Color.WHITE);
                canvas.drawCircle(x, y, radius + 3f * density, paint);
            }
            if ((connected || drawnLabels < 9) && item.rssi >= -82) {
                paint.setStyle(Paint.Style.FILL);
                paint.setTextSize(9f * getResources().getDisplayMetrics().scaledDensity);
                paint.setColor(Color.rgb(225, 234, 241));
                canvas.drawText(shortName(item.ssid), x + radius + 2f * density,
                        y - 2f * density, paint);
                drawnLabels++;
            }
        }
    }

    private static int laneFor(int frequencyMhz) {
        if (frequencyMhz >= 2_400 && frequencyMhz < 2_500) return 0;
        if (frequencyMhz >= 5_000 && frequencyMhz < 5_925) return 1;
        if (frequencyMhz >= 5_925 && frequencyMhz < 7_125) return 2;
        return -1;
    }

    private static float frequencyX(int frequencyMhz, int lane, float left, float right) {
        int min = lane == 0 ? 2_400 : lane == 1 ? 5_150 : 5_925;
        int max = lane == 0 ? 2_500 : lane == 1 ? 5_900 : 7_125;
        float fraction = Math.max(0f, Math.min(1f,
                (frequencyMhz - min) / (float) (max - min)));
        return left + fraction * (right - left);
    }

    private static String shortName(String value) {
        if (value == null || value.isEmpty()) return "Hidden";
        return value.length() <= 12 ? value : value.substring(0, 11) + "…";
    }
}
