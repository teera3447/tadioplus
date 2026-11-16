package com.tadiotv.app;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import java.util.Locale;

/** หน้าปัดจูนคลื่นวิทยุ FM 88-108 MHz เข็มสีแดงเลื่อนไปที่คลื่นของสถานี */
public class RadioDialView extends View {

    private static final float MIN = 88f;
    private static final float MAX = 108f;

    private final Paint glass = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private float needle = 98f;
    private boolean hasStation = false;
    private ValueAnimator anim;

    public RadioDialView(Context c) {
        super(c);
        init();
    }

    public RadioDialView(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    public RadioDialView(Context c, AttributeSet a, int s) {
        super(c, a, s);
        init();
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private void init() {
        glass.setStyle(Paint.Style.FILL);
        glass.setColor(0xFF120C07);
        border.setStyle(Paint.Style.STROKE);
        border.setColor(0xFF5C3D26);
        border.setStrokeWidth(dp(2));
        tick.setColor(0xFFE8D9B5);
        tick.setStrokeWidth(dp(1.2f));
        label.setColor(0xFFE8D9B5);
        label.setTextSize(dp(11));
        label.setTextAlign(Paint.Align.CENTER);
        needlePaint.setStrokeCap(Paint.Cap.ROUND);
    }

    /** f <= 0 หรือนอกช่วง = สถานีนี้ไม่มีเลขคลื่น (เข็มหรี่ลง) */
    public void setFrequency(float f) {
        if (anim != null) {
            anim.cancel();
        }
        if (f < MIN - 0.6f || f > MAX) {
            hasStation = false;
            invalidate();
            return;
        }
        hasStation = true;
        float to = Math.max(MIN, Math.min(MAX, f));
        anim = ValueAnimator.ofFloat(needle, to);
        anim.setDuration(700);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator a) {
                needle = (Float) a.getAnimatedValue();
                invalidate();
            }
        });
        anim.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (anim != null) {
            anim.cancel();
        }
        super.onDetachedFromWindow();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        rect.set(dp(1), dp(1), w - dp(1), h - dp(1));
        canvas.drawRoundRect(rect, dp(10), dp(10), glass);
        canvas.drawRoundRect(rect, dp(10), dp(10), border);

        float x0 = dp(18);
        float x1 = w - dp(18);
        float baseY = h * 0.60f;
        canvas.drawLine(x0, baseY, x1, baseY, tick);

        int steps = (int) ((MAX - MIN) * 2);
        for (int i = 0; i <= steps; i++) {
            float f = MIN + i * 0.5f;
            float x = x0 + (f - MIN) / (MAX - MIN) * (x1 - x0);
            boolean major = i % 8 == 0;      // 88, 92, 96, ...
            boolean whole = i % 2 == 0;
            float len = major ? dp(16) : (whole ? dp(10) : dp(5));
            canvas.drawLine(x, baseY, x, baseY - len, tick);
            if (major) {
                canvas.drawText(String.format(Locale.US, "%d", (int) f), x, baseY + dp(17), label);
            }
        }

        float nx = x0 + (needle - MIN) / (MAX - MIN) * (x1 - x0);
        int alpha = hasStation ? 255 : 70;
        needlePaint.setColor(0xFFFF3B30);
        needlePaint.setAlpha(hasStation ? 70 : 20);
        needlePaint.setStrokeWidth(dp(7));
        canvas.drawLine(nx, dp(8), nx, h - dp(8), needlePaint);
        needlePaint.setColor(0xFFFF3B30);
        needlePaint.setAlpha(alpha);
        needlePaint.setStrokeWidth(dp(2.2f));
        canvas.drawLine(nx, dp(8), nx, h - dp(8), needlePaint);
    }
}
