package com.finditfun.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.View;
import com.finditfun.app.gnss.GnssController;
import java.util.ArrayList;
import java.util.List;

public final class SkyPlotView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private List<GnssController.Satellite> satellites = new ArrayList<>();

    public SkyPlotView(Context context) {
        super(context);
    }

    public void setSatellites(List<GnssController.Satellite> satellites) {
        this.satellites = new ArrayList<>(satellites);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float radius = Math.min(cx, cy) * 0.84f;

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(10, 24, 35));
        canvas.drawCircle(cx, cy, radius, paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(2f);
        paint.setColor(Color.rgb(55, 82, 101));
        canvas.drawCircle(cx, cy, radius, paint);
        canvas.drawCircle(cx, cy, radius * 2f / 3f, paint);
        canvas.drawCircle(cx, cy, radius / 3f, paint);
        canvas.drawLine(cx - radius, cy, cx + radius, cy, paint);
        canvas.drawLine(cx, cy - radius, cx, cy + radius, paint);

        paint.setStyle(Paint.Style.FILL);
        paint.setTextSize(12f * getResources().getDisplayMetrics().scaledDensity);
        paint.setColor(Color.rgb(145, 163, 178));
        canvas.drawText("N", cx - 5, cy - radius - 8, paint);
        canvas.drawText("E", cx + radius + 8, cy + 5, paint);

        for (GnssController.Satellite satellite : satellites) {
            double azimuth = Math.toRadians(satellite.azimuth);
            float distance = radius * (90f - Math.max(0, satellite.elevation)) / 90f;
            float x = cx + (float) Math.sin(azimuth) * distance;
            float y = cy - (float) Math.cos(azimuth) * distance;
            float dotRadius = 6f + Math.min(10f, satellite.cn0 / 5f);
            paint.setColor(satellite.usedInFix
                    ? Color.rgb(155, 229, 100)
                    : Color.rgb(72, 217, 232));
            canvas.drawCircle(x, y, dotRadius, paint);
            paint.setTextSize(9f * getResources().getDisplayMetrics().scaledDensity);
            paint.setColor(Color.rgb(244, 247, 251));
            canvas.drawText(Integer.toString(satellite.svid), x + dotRadius + 2, y + 3, paint);
        }
    }
}
