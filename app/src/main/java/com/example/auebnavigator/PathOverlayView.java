package com.example.auebnavigator;

import android.content.Context;
import android.graphics.*;
import android.util.AttributeSet;
import android.view.View;
import java.util.List;

/**
  PathOverlayView class for showing the path between two points
 **/

public class PathOverlayView extends View {
    private List<AuebGraph.Edge> currentPath;
    private final Paint paint;

    public PathOverlayView(Context context, AttributeSet attrs) { //design of the line between the two points
        super(context, attrs);
        paint = new Paint();
        paint.setColor(Color.CYAN);
        paint.setStrokeWidth(12f);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setAntiAlias(true);
    }

    public void updatePath(List<AuebGraph.Edge> path) {
        this.currentPath = path;
        invalidate(); //path redesign
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (currentPath == null || currentPath.isEmpty()) return;

        Path path = new Path();
        // Start from the center (where the user is located)
        path.moveTo(getWidth() / 2f, getHeight() * 0.9f);

        // Draw the line to the other point
        for (int i = 0; i < currentPath.size(); i++) {
            float x = getWidth() / 2f + (i * 50);
            float y = getHeight() * 0.8f - (i * 150);
            path.lineTo(x, y);
            canvas.drawCircle(x, y, 15f, paint);
        }
        canvas.drawPath(path, paint);
    }
}