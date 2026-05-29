package com.example.auebnavigator;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import java.util.List;

public class PathOverlayView extends View {
    private List<AuebGraph.Edge> currentPath;
    private final Paint paint;

    public PathOverlayView(Context context, AttributeSet attrs) {
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.CYAN); // Το χρώμα της γραμμής
        paint.setStrokeWidth(12f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);
    }

    public void updatePath(List<AuebGraph.Edge> path) {
        this.currentPath = path;
        invalidate(); // Ζητάει επανασχεδίαση
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (currentPath == null || currentPath.isEmpty()) return;

        Path path = new Path();
        // Ξεκινάμε από το κέντρο κάτω (εκεί που είναι ο χρήστης)
        path.moveTo(getWidth() / 2f, getHeight() * 0.9f);

        // Σχεδιάζουμε τη γραμμή προς τα σημεία
        for (int i = 0; i < currentPath.size(); i++) {
            float x = getWidth() / 2f + (i * 50); // Απλοποιημένη προβολή
            float y = getHeight() * 0.8f - (i * 150);
            path.lineTo(x, y);
            canvas.drawCircle(x, y, 15f, paint);
        }
        canvas.drawPath(path, paint);
    }
}