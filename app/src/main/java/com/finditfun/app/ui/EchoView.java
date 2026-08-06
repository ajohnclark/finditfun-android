package com.finditfun.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.view.View;
import com.finditfun.app.sound.AcousticAnalysis;

public final class EchoView extends View {
    private static final int CYAN = Color.rgb(72, 217, 232);
    private static final int LIME = Color.rgb(155, 229, 100);
    private static final int AMBER = Color.rgb(255, 202, 88);
    private static final long PING_ANIMATION_MILLIS = 1_100;

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path profilePath = new Path();
    private AcousticAnalysis.Result result;
    private long pingStartedAt;

    public EchoView(Context context) {
        super(context);
    }

    public void startPing() {
        pingStartedAt = SystemClock.uptimeMillis();
        invalidate();
    }

    public void setResult(AcousticAnalysis.Result result) {
        this.result = result;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(10, 24, 35));
        float density = getResources().getDisplayMetrics().density;
        float scaledDensity = getResources().getDisplayMetrics().scaledDensity;
        float centerX = getWidth() / 2f;
        float centerY = getHeight() * 0.38f;
        float maxRadius = Math.min(getWidth() * 0.42f, getHeight() * 0.31f);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(11f * scaledDensity);
        paint.setColor(Color.rgb(145, 163, 178));
        canvas.drawText("OMNIDIRECTIONAL ECHO DISTANCE · animation slowed",
                12f * density, 20f * density, paint);

        for (int meters = 2; meters <= 8; meters += 2) {
            float radius = maxRadius * meters / 8f;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(density);
            paint.setColor(Color.rgb(34, 62, 79));
            canvas.drawCircle(centerX, centerY, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(10f * scaledDensity);
            paint.setColor(Color.rgb(112, 137, 155));
            canvas.drawText(meters + "m", centerX + 3f * density,
                    centerY - radius + 12f * density, paint);
        }

        if (result != null) {
            for (AcousticAnalysis.EchoPeak peak : result.peaks) {
                float radius = maxRadius * peak.distanceMeters
                        / result.maxDistanceMeters;
                int color = peak.strength >= 0.67f ? LIME
                        : peak.strength >= 0.35f ? CYAN : AMBER;
                paint.setStyle(Paint.Style.STROKE);
                paint.setStrokeWidth((1.5f + peak.strength * 4f) * density);
                paint.setColor(withAlpha(color,
                        Math.round(95 + 150 * peak.strength)));
                canvas.drawCircle(centerX, centerY, radius, paint);
            }
        }

        long elapsed = SystemClock.uptimeMillis() - pingStartedAt;
        if (pingStartedAt > 0 && elapsed < PING_ANIMATION_MILLIS) {
            float progress = elapsed / (float) PING_ANIMATION_MILLIS;
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3f * density);
            paint.setColor(withAlpha(CYAN, Math.round(255 * (1f - progress))));
            canvas.drawCircle(centerX, centerY, maxRadius * progress, paint);
            postInvalidateOnAnimation();
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.WHITE);
        canvas.drawCircle(centerX, centerY, 6f * density, paint);
        paint.setColor(CYAN);
        canvas.drawCircle(centerX, centerY, 3f * density, paint);

        drawProfile(canvas, density, scaledDensity);
    }

    private void drawProfile(Canvas canvas, float density, float scaledDensity) {
        float left = 16f * density;
        float right = getWidth() - 16f * density;
        float top = getHeight() * 0.74f;
        float bottom = getHeight() - 23f * density;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(density);
        paint.setColor(Color.rgb(40, 69, 87));
        canvas.drawRect(left, top, right, bottom, paint);

        if (result != null && result.profile.length > 1) {
            profilePath.reset();
            for (int index = 0; index < result.profile.length; index++) {
                float x = left + index / (float) (result.profile.length - 1)
                        * (right - left);
                float y = bottom - result.profile[index] * (bottom - top);
                if (index == 0) profilePath.moveTo(x, y); else profilePath.lineTo(x, y);
            }
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(2f * density);
            paint.setColor(CYAN);
            canvas.drawPath(profilePath, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(10f * scaledDensity);
        paint.setColor(Color.rgb(145, 163, 178));
        canvas.drawText("0m", left, getHeight() - 7f * density, paint);
        canvas.drawText("echo strength by distance",
                getWidth() / 2f - 57f * density, getHeight() - 7f * density, paint);
        canvas.drawText("8m", right - 17f * density,
                getHeight() - 7f * density, paint);
    }

    private static int withAlpha(int color, int alpha) {
        return Color.argb(Math.max(0, Math.min(255, alpha)),
                Color.red(color), Color.green(color), Color.blue(color));
    }
}
