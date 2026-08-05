package com.finditfun.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.SystemClock;
import android.view.View;
import com.finditfun.app.signal.SignalMath;

public final class SignalOrbView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int rssi = -100;
    private boolean fresh;

    public SignalOrbView(Context context) {
        super(context);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(3f * getResources().getDisplayMetrics().density);
    }

    public void setSignal(int rssi, boolean fresh) {
        this.rssi = rssi;
        this.fresh = fresh;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float cx = getWidth() / 2f;
        float cy = getHeight() / 2f;
        float maxRadius = Math.min(cx, cy) * 0.86f;
        double strength = fresh ? SignalMath.fraction(rssi) : 0.0;
        int baseColor = fresh ? colorForStrength(strength) : Color.rgb(80, 94, 108);

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(Color.rgb(10, 24, 35));
        canvas.drawCircle(cx, cy, maxRadius, paint);

        float phase = (SystemClock.elapsedRealtime() % 1_500L) / 1_500f;
        paint.setStyle(Paint.Style.STROKE);
        for (int ring = 1; ring <= 4; ring++) {
            float base = maxRadius * ring / 4f;
            float animated = Math.min(maxRadius, base + phase * maxRadius / 4f);
            int alpha = Math.max(25, 155 - ring * 23 - Math.round(phase * 40));
            paint.setColor((baseColor & 0x00FFFFFF) | (alpha << 24));
            canvas.drawCircle(cx, cy, animated, paint);
        }

        paint.setStyle(Paint.Style.FILL);
        paint.setColor(baseColor);
        canvas.drawCircle(cx, cy, (float) (18f + strength * 34f)
                * getResources().getDisplayMetrics().density, paint);
        postInvalidateDelayed(33);
    }

    private static int colorForStrength(double fraction) {
        if (fraction > 0.72) return Color.rgb(155, 229, 100);
        if (fraction > 0.42) return Color.rgb(72, 217, 232);
        if (fraction > 0.20) return Color.rgb(255, 202, 88);
        return Color.rgb(255, 102, 122);
    }
}
