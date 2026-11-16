package com.tadiotv.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import java.util.Arrays;

/** Music visualizer แบบแท่งไฟ LED (สีเหลืองอำพัน -> ส้ม -> แดง) พร้อมขีดค้างค่าสูงสุด */
public class SpectrumView extends View {

    private static final int BARS = SpectrumAnalyzer.BARS;
    private static final int SEGMENTS = 16;
    private static final int OFF = 0xFF2A1F12;
    private static final int PEAK = 0xFFFFE9B0;

    private final float[] target = new float[BARS];
    private final float[] shown = new float[BARS];
    private final float[] peak = new float[BARS];
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private SpectrumAnalyzer analyzer;
    private boolean active = false;

    public SpectrumView(Context c) {
        super(c);
    }

    public SpectrumView(Context c, AttributeSet a) {
        super(c, a);
    }

    public SpectrumView(Context c, AttributeSet a, int s) {
        super(c, a, s);
    }

    public void setAnalyzer(SpectrumAnalyzer analyzer) {
        this.analyzer = analyzer;
    }

    /** true = กำลังมีเสียงออกลำโพง */
    public void setActive(boolean active) {
        this.active = active;
        invalidate();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private static int colorFor(int segment) {
        float f = segment / (float) (SEGMENTS - 1);
        if (f < 0.55f) {
            return 0xFFFFB300; // เหลืองอำพัน
        }
        if (f < 0.82f) {
            return 0xFFFF7A00; // ส้ม
        }
        return 0xFFFF3B30;     // แดง
    }

    @Override
    protected void onDraw(Canvas canvas) {
        boolean live = active && analyzer != null && analyzer.isFresh();
        if (live) {
            analyzer.copyBars(target);
        } else {
            Arrays.fill(target, 0f);
        }

        float w = getWidth();
        float h = getHeight();
        float gap = dp(3);
        float barW = (w - gap * (BARS - 1)) / BARS;
        float segGap = dp(2);
        float segH = (h - segGap * (SEGMENTS - 1)) / SEGMENTS;
        boolean alive = false;

        for (int i = 0; i < BARS; i++) {
            float t = target[i];
            if (t > shown[i]) {
                shown[i] += (t - shown[i]) * 0.65f;   // ขึ้นเร็ว
            } else {
                shown[i] *= 0.90f;                    // ลงนุ่ม
            }
            if (shown[i] > peak[i]) {
                peak[i] = shown[i];
            } else {
                peak[i] = Math.max(0f, peak[i] - 0.015f);
            }
            if (shown[i] > 0.01f || peak[i] > 0.01f) {
                alive = true;
            }

            int lit = Math.round(shown[i] * SEGMENTS);
            int pk = Math.round(peak[i] * SEGMENTS) - 1;
            float x = i * (barW + gap);
            for (int s = 0; s < SEGMENTS; s++) {
                float bottom = h - s * (segH + segGap);
                int color;
                if (s < lit) {
                    color = colorFor(s);
                } else if (s == pk && pk >= lit) {
                    color = PEAK;
                } else {
                    color = OFF;
                }
                paint.setColor(color);
                rect.set(x, bottom - segH, x + barW, bottom);
                canvas.drawRect(rect, paint);
            }
        }

        if (alive || active) {
            postInvalidateOnAnimation();
        }
    }
}
