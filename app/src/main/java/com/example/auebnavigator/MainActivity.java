package com.example.auebnavigator; // Βάλε το δικό σου package

import android.content.Intent;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat;

public class MainActivity extends AppCompatActivity {

    private FrameLayout btnMic;
    private Vibrator vibrator;

    // Προσθέτουμε τον "ανιχνευτή" χειρονομιών
    private GestureDetectorCompat gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        btnMic = findViewById(R.id.btn_mic);

        // Αρχικοποίηση του GestureDetector με την custom κλάση μας
        gestureDetector = new GestureDetectorCompat(this, new SwipeListener());

        // Click Listener για το μικρόφωνο
        btnMic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleMicClick();
            }
        });
    }

    // ΒΑΣΙΚΟ: Πρέπει να στέλνουμε όλα τα αγγίγματα της οθόνης στον GestureDetector
    // για να καταλάβει πότε γίνεται swipe.
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        this.gestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    private void handleMicClick() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(50);
        }
        Toast.makeText(this, "Ακούω...", Toast.LENGTH_SHORT).show();
        // TODO: Εδώ μπαίνει το SpeechRecognizer logic
    }

    // --- Η Λογική του Swiping ---
    private class SwipeListener extends GestureDetector.SimpleOnGestureListener {

        // Πόσα pixels πρέπει να διανύσει το δάχτυλο για να θεωρηθεί swipe
        private static final int SWIPE_THRESHOLD = 100;
        // Πόσο γρήγορα πρέπει να γίνει η κίνηση
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            boolean result = false;
            try {
                // Υπολογισμός της απόστασης (Τελικό σημείο e2 - Αρχικό σημείο e1)
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();

                // Ελέγχουμε αν η κίνηση ήταν κυρίως οριζόντια (άρα swipe δεξιά/αριστερά και όχι πάνω/κάτω)
                if (Math.abs(diffX) > Math.abs(diffY)) {
                    // Ελέγχουμε αν το swipe ήταν αρκετά μεγάλο και γρήγορο
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            // Θετικό diffX = Swipe Right (από αριστερά προς δεξιά)
                            onSwipeRight();
                        } else {
                            // Αρνητικό diffX = Swipe Left (από δεξιά προς αριστερά)
                            onSwipeLeft();
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

    // Τι συμβαίνει όταν κάνουμε Swipe Right
    private void onSwipeRight() {
        // 1. Βάζουμε πάλι Haptic Feedback για να νιώσει ο χρήστης ότι το gesture έπιασε
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(30);
        }

        // 2. Μετάβαση στην οθόνη της Κάμερας
        // Προσοχή: Πρέπει να έχεις φτιάξει ένα CameraActivity.java αλλιώς θα κοκκινίσει εδώ.
        Intent intent = new Intent(MainActivity.this, CameraActivity.class);
        startActivity(intent);
    }

    // Τι συμβαίνει όταν κάνουμε Swipe Left
    private void onSwipeLeft() {
        // Μπορείς να το αφήσεις κενό προς το παρόν ή να βάλεις π.χ. τις Ρυθμίσεις (SettingsActivity)
        Toast.makeText(this, "Swipe Left -> Ρυθμίσεις (coming soon)", Toast.LENGTH_SHORT).show();
    }
}