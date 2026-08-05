package com.finditfun.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

public final class MagneticGraphView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path path = new Path();
    private final ArrayList<Float> values = new ArrayList<>();

    public MagneticGraphView(Context context) {
        super(context);
    }

    public void addReading(float value) {
        values.add(value);
        while (values.size() > 160) values.remove(0);
        invalidate();
    }

    public List<Float> values() {
        return new ArrayList<>(values);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        canvas.drawColor(Color.rgb(10, 24, 35));
        if (values.size() < 2) return;

        float max = 100f;
        for (float value : values) max = Math.max(max, value);
        path.reset();
        for (int i = 0; i < values.size(); i++) {
            float x = i * getWidth() / (float) Math.max(1, values.size() - 1);
            float y = getHeight() - Math.min(getHeight(), values.get(i) / max * getHeight());
            if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
        }
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(4f * getResources().getDisplayMetrics().density);
        paint.setColor(Color.rgb(255, 202, 88));
        canvas.drawPath(path, paint);
    }
}
