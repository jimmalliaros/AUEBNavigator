package com.example.auebnavigator;

import android.content.Intent;
import android.os.Bundle;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.GestureDetectorCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class MainActivity extends AppCompatActivity {

    private GestureDetectorCompat gestureDetectorCompat;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });
        gestureDetectorCompat = new GestureDetectorCompat(this, new MyGestureListener());

    }

    private class MyGestureListener extends GestureDetector.SimpleOnGestureListener{

        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override


        public boolean onDoubleTapEvent(@NonNull MotionEvent e) {

            Intent intent = new Intent(MainActivity.this, SpeakingScreen.class);
            startActivity(intent);
            return true;
        }

        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            boolean result = false;
            try {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();

                // Determine if swipe is Horizontal or Vertical
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    // Horizontal swipe
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            Intent intent = new Intent(MainActivity.this, SpeakingScreen.class);
                            //System.out.println("Swipe Right");
                        } else {
                            System.out.println("Swipe Left");
                        }
                        result = true;
                    }
                } else {
                    // Vertical swipe
                    if (Math.abs(diffY) > SWIPE_THRESHOLD && Math.abs(velocityY) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffY > 0) {
                            System.out.println("Swipe Down");
                        } else {
                            System.out.println("Swipe Up");
                        }
                        result = true;
                    }
                }
            } catch (Exception exception) {
                exception.printStackTrace();
            }
            return result;
        }





    }

    public boolean onTouchEvent(MotionEvent event){
        gestureDetectorCompat.onTouchEvent(event);


        return super.onTouchEvent(event);
    }


}