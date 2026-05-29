package com.example.auebnavigator;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

public class OcrFocusOverlayView extends View {
    private Paint scrimPaint;
    private Paint eraserPaint;
    private Paint borderPaint;
    private RectF focusRect;

    // Πιάνει το 70% του πλάτους (ιδανικό για ταμπέλες)
    private final float rectSizePercentage = 0.7f;

    public OcrFocusOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        scrimPaint = new Paint();
        scrimPaint.setColor(Color.parseColor("#99000000")); // 60% Μαύρο
        scrimPaint.setStyle(Paint.Style.FILL);

        eraserPaint = new Paint();
        eraserPaint.setAntiAlias(true);
        eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        borderPaint = new Paint();
        borderPaint.setColor(Color.RED);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(8f);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    // Το Android καλεί αυτή τη μέθοδο ΜΟΛΙΣ ξέρει το μέγεθος της οθόνης
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float size = w * rectSizePercentage;
        float left = (w - size) / 2f;
        float top = (h - size) / 2f;
        focusRect = new RectF(left, top, left + size, top + size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (focusRect == null) return;

        canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);
        canvas.drawRect(focusRect, eraserPaint);
        canvas.drawRect(focusRect, borderPaint);
    }
}