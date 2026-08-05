package com.finditfun.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import com.finditfun.app.hunt.HuntSurvey;

public final class HuntMapView extends View {
    public enum Mode { TRAIL, BEARING }

    private static final int BACKGROUND = Color.rgb(10, 24, 35);
    private static final int GRID = Color.rgb(55, 72, 88);
    private static final int INK = Color.rgb(244, 247, 251);
    private static final int MUTED = Color.rgb(145, 163, 178);
    private static final int CYAN = Color.rgb(72, 217, 232);
    private static final int RED = Color.rgb(255, 78, 90);
    private static final int AMBER = Color.rgb(255, 202, 88);
    private static final int BLUE = Color.rgb(52, 70, 230);

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final RectF circleBounds = new RectF();
    private Mode mode = Mode.BEARING;
    private HuntSurvey.Snapshot snapshot;
    private float heading;
    private float currentX;
    private float currentY;

    public HuntMapView(Context context) {
        super(context);
        setBackgroundColor(BACKGROUND);
        setContentDescription("Experimental Bluetooth signal survey");
    }

    public void setMode(Mode mode) {
        this.mode = mode;
        invalidate();
    }

    public Mode mode() {
        return mode;
    }

    public void setSurvey(HuntSurvey.Snapshot snapshot, float heading,
                          float currentX, float currentY) {
        this.snapshot = snapshot;
        this.heading = heading;
        this.currentX = currentX;
        this.currentY = currentY;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(BACKGROUND);
        if (mode == Mode.TRAIL) drawTrail(canvas); else drawBearing(canvas);
    }

    private void drawTrail(Canvas canvas) {
        float padding = dp(28);
        float top = dp(34);
        float bottom = getHeight() - dp(34);
        float left = padding;
        float right = getWidth() - padding;
        float minX = Math.min(0, currentX);
        float maxX = Math.max(0, currentX);
        float minY = Math.min(0, currentY);
        float maxY = Math.max(0, currentY);
        if (snapshot != null) {
            for (HuntSurvey.TrailPoint point : snapshot.points) {
                minX = Math.min(minX, point.x);
                maxX = Math.max(maxX, point.x);
                minY = Math.min(minY, point.y);
                maxY = Math.max(maxY, point.y);
            }
        }
        float spanX = Math.max(4f, maxX - minX);
        float spanY = Math.max(4f, maxY - minY);
        float scale = Math.min((right - left) / spanX, (bottom - top) / spanY);
        float centerDataX = (minX + maxX) / 2f;
        float centerDataY = (minY + maxY) / 2f;
        float centerX = (left + right) / 2f;
        float centerY = (top + bottom) / 2f;

        drawText(canvas, "TRAIL · colored by measured signal", dp(12), dp(20), MUTED,
                Paint.Align.LEFT);
        if (snapshot != null && snapshot.points.size() > 1) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(dp(2));
            paint.setColor(GRID);
            path.reset();
            boolean first = true;
            for (HuntSurvey.TrailPoint point : snapshot.points) {
                float x = centerX + (point.x - centerDataX) * scale;
                float y = centerY + (point.y - centerDataY) * scale;
                if (first) {
                    path.moveTo(x, y);
                    first = false;
                } else {
                    path.lineTo(x, y);
                }
            }
            canvas.drawPath(path, paint);
        }
        if (snapshot != null) {
            paint.setStyle(Paint.Style.FILL);
            for (HuntSurvey.TrailPoint point : snapshot.points) {
                float x = centerX + (point.x - centerDataX) * scale;
                float y = centerY + (point.y - centerDataY) * scale;
                paint.setColor(signalColor(point.rssi));
                canvas.drawCircle(x, y, dp(5), paint);
            }
        }

        float originX = centerX + (0 - centerDataX) * scale;
        float originY = centerY + (0 - centerDataY) * scale;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(2));
        paint.setColor(MUTED);
        canvas.drawCircle(originX, originY, dp(8), paint);

        float youX = centerX + (currentX - centerDataX) * scale;
        float youY = centerY + (currentY - centerDataY) * scale;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(INK);
        canvas.drawCircle(youX, youY, dp(8), paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(3));
        paint.setColor(MUTED);
        canvas.drawCircle(youX, youY, dp(13), paint);

        drawText(canvas, "○ origin     ● you     red strong · yellow mid · blue weak",
                dp(12), getHeight() - dp(10), MUTED, Paint.Align.LEFT);
    }

    private void drawBearing(Canvas canvas) {
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f + dp(5);
        float radius = Math.min(getWidth(), getHeight()) * 0.36f;
        circleBounds.set(cx - radius, cy - radius, cx + radius, cy + radius);

        float sectorWidth = 360f / HuntSurvey.SECTOR_COUNT;
        for (int sector = 0; sector < HuntSurvey.SECTOR_COUNT; sector++) {
            int rssi = snapshot == null ? -127 : snapshot.sectorMedians[sector];
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(rssi == -127 ? Color.rgb(14, 29, 40)
                    : withAlpha(signalColor(rssi), 125));
            canvas.drawArc(circleBounds, sector * sectorWidth - 90f, sectorWidth,
                    true, paint);
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(dp(1));
        paint.setColor(GRID);
        for (int ring = 1; ring <= 4; ring++) {
            canvas.drawCircle(cx, cy, radius * ring / 4f, paint);
        }
        for (int sector = 0; sector < HuntSurvey.SECTOR_COUNT; sector++) {
            double angle = Math.toRadians(sector * sectorWidth - 90);
            canvas.drawLine(cx, cy,
                    cx + (float) Math.cos(angle) * radius,
                    cy + (float) Math.sin(angle) * radius, paint);
        }

        drawText(canvas, "N", cx, cy - radius - dp(10), INK, Paint.Align.CENTER);
        drawText(canvas, "E", cx + radius + dp(13), cy + dp(4), INK, Paint.Align.CENTER);
        drawText(canvas, "S", cx, cy + radius + dp(22), INK, Paint.Align.CENTER);
        drawText(canvas, "W", cx - radius - dp(13), cy + dp(4), INK, Paint.Align.CENTER);

        drawDirectionLine(canvas, cx, cy, radius * 0.88f, heading, CYAN, dp(2));
        if (snapshot != null && snapshot.hasExperimentalBearing()) {
            drawArrow(canvas, cx, cy, radius * 0.72f, snapshot.hotBearing);
            drawText(canvas, "experimental hot sector",
                    cx, cy + radius + dp(43), AMBER, Paint.Align.CENTER);
        } else {
            drawText(canvas, "stand still · rotate slowly through every heading",
                    cx, cy + radius + dp(43), MUTED, Paint.Align.CENTER);
        }
        int covered = snapshot == null ? 0 : snapshot.coveredSectors;
        drawText(canvas, "COMPASS SCAN  " + covered + "/" + HuntSurvey.SECTOR_COUNT + " sectors",
                dp(12), dp(20), MUTED, Paint.Align.LEFT);
    }

    private void drawDirectionLine(Canvas canvas, float cx, float cy, float length,
                                   float bearing, int color, float width) {
        double radians = Math.toRadians(bearing - 90f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(width);
        paint.setColor(color);
        canvas.drawLine(cx, cy, cx + (float) Math.cos(radians) * length,
                cy + (float) Math.sin(radians) * length, paint);
    }

    private void drawArrow(Canvas canvas, float cx, float cy, float length, float bearing) {
        double radians = Math.toRadians(bearing - 90f);
        float tipX = cx + (float) Math.cos(radians) * length;
        float tipY = cy + (float) Math.sin(radians) * length;
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeWidth(dp(9));
        paint.setColor(RED);
        canvas.drawLine(cx, cy, tipX, tipY, paint);
        paint.setStrokeCap(Paint.Cap.BUTT);

        float wing = dp(18);
        double left = radians + Math.toRadians(145);
        double right = radians - Math.toRadians(145);
        path.reset();
        path.moveTo(tipX, tipY);
        path.lineTo(tipX + (float) Math.cos(left) * wing,
                tipY + (float) Math.sin(left) * wing);
        path.lineTo(tipX + (float) Math.cos(right) * wing,
                tipY + (float) Math.sin(right) * wing);
        path.close();
        paint.setStyle(Paint.Style.FILL);
        canvas.drawPath(path, paint);
    }

    private void drawText(Canvas canvas, String text, float x, float y, int color,
                          Paint.Align align) {
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        paint.setTextAlign(align);
        paint.setTextSize(dp(12));
        canvas.drawText(text, x, y, paint);
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private static int signalColor(int rssi) {
        if (rssi >= -58) return RED;
        if (rssi >= -75) return AMBER;
        return BLUE;
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
