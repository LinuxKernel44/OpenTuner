package com.opentuner.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import com.google.android.material.color.MaterialColors;

/** Analog-style gauge showing the pitch deviation in cents (-50 .. +50). */
public class GaugeView extends View {
    private static final float RANGE_CENTS = 50f;
    private static final float SWEEP_DEG = 120f; // total sweep: -60° .. +60° from vertical
    public static final float IN_TUNE_CENTS = 5f;

    private final Paint arc = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tick = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hub = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF oval = new RectF();

    private final int colorTrack;
    private final int colorTick;
    private final int colorIdle;
    private final int colorGood;
    private final int colorBad;

    private float targetCents;
    private float shownCents;
    private boolean active;

    public GaugeView(Context context, AttributeSet attrs) {
        super(context, attrs);
        colorTrack = MaterialColors.getColor(this, com.google.android.material.R.attr.colorSurfaceVariant);
        colorTick = MaterialColors.getColor(this, com.google.android.material.R.attr.colorOnSurfaceVariant);
        colorIdle = colorTick;
        colorGood = ContextCompat.getColor(context, R.color.in_tune);
        colorBad = ContextCompat.getColor(context, R.color.out_of_tune);

        arc.setStyle(Paint.Style.STROKE);
        arc.setStrokeCap(Paint.Cap.ROUND);
        tick.setStyle(Paint.Style.STROKE);
        tick.setStrokeCap(Paint.Cap.ROUND);
        needle.setStyle(Paint.Style.STROKE);
        needle.setStrokeCap(Paint.Cap.ROUND);
        label.setTextAlign(Paint.Align.CENTER);
        label.setColor(colorTick);
    }

    /** Updates the displayed deviation. {@code active} false parks the needle in the centre, dimmed. */
    public void setReading(boolean active, float cents) {
        this.active = active;
        if (active) targetCents = Math.max(-RANGE_CENTS, Math.min(RANGE_CENTS, cents));
        else targetCents = 0f;
        postInvalidateOnAnimation();
    }

    public boolean isInTune() {
        return active && Math.abs(targetCents) <= IN_TUNE_CENTS;
    }

    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        int w = MeasureSpec.getSize(widthSpec);
        int h = (int) (w * 0.58f);
        setMeasuredDimension(w, resolveSize(h, heightSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float w = getWidth();
        float h = getHeight();
        float density = getResources().getDisplayMetrics().density;

        float cx = w / 2f;
        float radius = Math.min(w * 0.40f, h * 0.76f);
        float cy = h * 0.92f;
        float stroke = radius * 0.07f;

        // Ease the needle toward the target.
        shownCents += (targetCents - shownCents) * 0.25f;
        boolean settling = Math.abs(targetCents - shownCents) > 0.05f;

        oval.set(cx - radius, cy - radius, cx + radius, cy + radius);
        float start = -90f - SWEEP_DEG / 2f;

        arc.setStrokeWidth(stroke);
        arc.setColor(colorTrack);
        canvas.drawArc(oval, start, SWEEP_DEG, false, arc);

        // In-tune zone.
        float zone = SWEEP_DEG * (IN_TUNE_CENTS * 2f) / (RANGE_CENTS * 2f);
        arc.setColor(colorGood);
        arc.setAlpha(active && isInTune() ? 255 : 110);
        canvas.drawArc(oval, -90f - zone / 2f, zone, false, arc);

        // Ticks every 10 cents, with labels.
        label.setTextSize(radius * 0.09f);
        for (int c = -50; c <= 50; c += 10) {
            double ang = Math.toRadians(-90f + c / RANGE_CENTS * SWEEP_DEG / 2f);
            boolean major = c == 0 || Math.abs(c) == 50;
            float inner = radius - stroke * (major ? 2.4f : 1.9f);
            float outer = radius - stroke * 1.2f;
            tick.setColor(colorTick);
            tick.setStrokeWidth((major ? 2.2f : 1.4f) * density);
            canvas.drawLine(cx + (float) Math.cos(ang) * inner, cy + (float) Math.sin(ang) * inner,
                    cx + (float) Math.cos(ang) * outer, cy + (float) Math.sin(ang) * outer, tick);
            if (major) {
                float lr = radius + stroke * 0.5f + label.getTextSize() * 0.9f;
                String text = c == 0 ? "0" : (c > 0 ? "+" : "−") + Math.abs(c);
                canvas.drawText(text, cx + (float) Math.cos(ang) * lr,
                        cy + (float) Math.sin(ang) * lr + label.getTextSize() * 0.35f, label);
            }
        }

        // Needle.
        int color = !active ? colorIdle : (Math.abs(targetCents) <= IN_TUNE_CENTS ? colorGood : colorBad);
        double ang = Math.toRadians(-90f + shownCents / RANGE_CENTS * SWEEP_DEG / 2f);
        needle.setColor(color);
        needle.setAlpha(active ? 255 : 90);
        needle.setStrokeWidth(radius * 0.035f);
        float len = radius - stroke * 2.6f;
        canvas.drawLine(cx, cy, cx + (float) Math.cos(ang) * len, cy + (float) Math.sin(ang) * len, needle);
        hub.setColor(color);
        hub.setAlpha(active ? 255 : 90);
        canvas.drawCircle(cx, cy, radius * 0.06f, hub);

        if (settling) postInvalidateOnAnimation();
    }
}
