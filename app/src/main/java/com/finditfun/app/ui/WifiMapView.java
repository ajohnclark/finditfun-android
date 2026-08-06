package com.finditfun.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import com.finditfun.app.wifi.WifiMath;
import com.finditfun.app.wifi.WifiSurvey;
import java.util.ArrayList;

public final class WifiMapView extends View {
    private static final int CYAN = Color.rgb(72, 217, 232);
    private static final int LIME = Color.rgb(155, 229, 100);
    private static final int AMBER = Color.rgb(255, 202, 88);
    private static final int RED = Color.rgb(255, 102, 122);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private WifiSurvey.Snapshot survey = new WifiSurvey().snapshot();
    private float heading;
    private float currentX;
    private float currentY;

    public WifiMapView(Context context) {
        super(context);
    }

    public void setSurvey(WifiSurvey.Snapshot survey, float heading,
                          float currentX, float currentY) {
        this.survey = survey;
        this.heading = heading;
        this.currentX = currentX;
        this.currentY = currentY;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(10, 24, 35));
        float density = getResources().getDisplayMetrics().density;
        float left = 26f * density;
        float top = 36f * density;
        float right = getWidth() - 26f * density;
        float bottom = getHeight() - 32f * density;

        Bounds bounds = bounds();
        drawGrid(canvas, left, top, right, bottom);

        ArrayList<WifiSurvey.Sample> samples = new ArrayList<>(survey.samples);
        if (samples.size() > 1) {
            path.reset();
            for (int i = 0; i < samples.size(); i++) {
                WifiSurvey.Sample sample = samples.get(i);
                float x = mapX(sample.x, bounds, left, right);
                float y = mapY(sample.y, bounds, top, bottom);
                if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * density);
            paint.setColor(Color.rgb(65, 92, 110));
            canvas.drawPath(path, paint);
        }

        for (int i = 0; i < samples.size(); i++) {
            WifiSurvey.Sample sample = samples.get(i);
            float x = mapX(sample.x, bounds, left, right);
            float y = mapY(sample.y, bounds, top, bottom);
            float fraction = (float) WifiMath.strengthFraction(sample.rssi);
            float radius = (10f + 12f * fraction) * density;
            int color = colorForRssi(sample.rssi);
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(withAlpha(color, i == samples.size() - 1 ? 210 : 105));
            canvas.drawCircle(x, y, radius, paint);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(density);
            paint.setColor(withAlpha(color, 220));
            canvas.drawCircle(x, y, radius, paint);
        }

        float x = mapX(currentX, bounds, left, right);
        float y = mapY(currentY, bounds, top, bottom);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(x, y, 5f * density, paint);
        double radians = Math.toRadians(heading);
        float arrowX = x + (float) Math.sin(radians) * 24f * density;
        float arrowY = y - (float) Math.cos(radians) * 24f * density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f * density);
        paint.setColor(CYAN);
        canvas.drawLine(x, y, arrowX, arrowY, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(11f * getResources().getDisplayMetrics().scaledDensity);
        paint.setColor(Color.rgb(145, 163, 178));
        canvas.drawText("RELATIVE STEP MAP · blobs are measured samples",
                14f * density, 22f * density, paint);
        String count = samples.size() + (samples.size() == 1 ? " point" : " points");
        canvas.drawText(count, 14f * density, getHeight() - 10f * density, paint);
    }

    private Bounds bounds() {
        float minX = currentX;
        float maxX = currentX;
        float minY = currentY;
        float maxY = currentY;
        for (WifiSurvey.Sample sample : survey.samples) {
            minX = Math.min(minX, sample.x);
            maxX = Math.max(maxX, sample.x);
            minY = Math.min(minY, sample.y);
            maxY = Math.max(maxY, sample.y);
        }
        float centerX = (minX + maxX) / 2f;
        float centerY = (minY + maxY) / 2f;
        float extent = Math.max(3f, Math.max(maxX - minX, maxY - minY) / 2f + 1f);
        return new Bounds(centerX - extent, centerX + extent,
                centerY - extent, centerY + extent);
    }

    private void drawGrid(Canvas canvas, float left, float top, float right, float bottom) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        paint.setColor(Color.rgb(31, 55, 72));
        for (int i = 0; i <= 6; i++) {
            float fraction = i / 6f;
            float x = left + fraction * (right - left);
            float y = top + fraction * (bottom - top);
            canvas.drawLine(x, top, x, bottom, paint);
            canvas.drawLine(left, y, right, y, paint);
        }
        paint.setColor(Color.rgb(55, 82, 101));
        canvas.drawRect(left, top, right, bottom, paint);
    }

    private static float mapX(float value, Bounds bounds, float left, float right) {
        return left + (value - bounds.minX) / (bounds.maxX - bounds.minX) * (right - left);
    }

    private static float mapY(float value, Bounds bounds, float top, float bottom) {
        return bottom - (value - bounds.minY) / (bounds.maxY - bounds.minY) * (bottom - top);
    }

    private static int colorForRssi(int rssi) {
        if (rssi >= -55) return LIME;
        if (rssi >= -67) return CYAN;
        if (rssi >= -78) return AMBER;
        return RED;
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static final class Bounds {
        final float minX;
        final float maxX;
        final float minY;
        final float maxY;

        Bounds(float minX, float maxX, float minY, float maxY) {
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }
    }
}
