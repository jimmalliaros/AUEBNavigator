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

/**
 A custom View that dims the entire screen with a dark scrim, except for a clear square "focus window" in the centre, which is the area the OCR engine can scan
 */

public class OcrFocusOverlayView extends View {
    private Paint scrimPaint;
    private Paint eraserPaint;
    private Paint borderPaint;
    private RectF focusRect;
    private final float rectSizePercentage = 0.7f; //size of the focus window where the apps reads the text

    public OcrFocusOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);

        //Dark scrim: a solid fill of semi-transparent black.
        scrimPaint = new Paint();
        scrimPaint.setColor(Color.parseColor("#99000000")); // 60% black
        scrimPaint.setStyle(Paint.Style.FILL);

        // Eraser: removes whatever is already on the canvas, leaving the focus window fully transparent. AntiAlias smooths the cut-out edges.
        eraserPaint = new Paint();
        eraserPaint.setAntiAlias(true);
        eraserPaint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.CLEAR));

        //Red outline drawn around the focus window.
        borderPaint = new Paint();
        borderPaint.setColor(Color.RED);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(8f);

        setLayerType(LAYER_TYPE_SOFTWARE, null);
    }

    //onSizeChanged is called when Android knows the size of the View screen
    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        float size = w * rectSizePercentage; //square side = 70% of the view width
        float left = (w - size) / 2f; //equal left/right margin -> horizontally centred
        float top = (h - size) / 2f;//equal top/bottom margin -> vertically centred
        focusRect = new RectF(left, top, left + size, top + size);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (focusRect == null) return;

        canvas.drawRect(0, 0, getWidth(), getHeight(), scrimPaint);//fill the whole view with the dark scrim
        canvas.drawRect(focusRect, eraserPaint);//erase the focus rectangle so the camera preview shows through
        canvas.drawRect(focusRect, borderPaint);//draw the red border around that window
    }
}