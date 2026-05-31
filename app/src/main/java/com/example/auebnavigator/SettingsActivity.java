package com.example.auebnavigator;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.MotionEvent;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.GestureDetectorCompat;

import java.util.Locale;

/**
 SettingsActivity is the settings screen of the application
 */

public class SettingsActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private SharedPreferences prefs; //stores the chosen voice speed
    private GestureDetectorCompat gestureDetector; //swipe navigation


    //Navigation using the side (volume) buttons
    private long lastVolumeDownTime = 0;
    private final android.os.Handler volumeNavHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable volumeDownSingleTapRunnable;
    private static final int VOLUME_DOUBLE_PRESS_INTERVAL = 450; //ms tolerance between two presses

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("AuebNavPrefs", MODE_PRIVATE);

        //Initialisation of TTS in Greek
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("el", "GR"));
                // appliance of the speed the user already saved
                float speed = prefs.getFloat("tts_speed", 1.0f);
                tts.setSpeechRate(speed);
            }
        });

        // button layouts
        LinearLayout btnVoiceSpeed = findViewById(R.id.btn_voice_speed);
        LinearLayout btnUserGuide = findViewById(R.id.btn_user_guide);
        LinearLayout btnFeedback = findViewById(R.id.btn_feedback);

        //Voice speed button
        btnVoiceSpeed.setOnClickListener(v -> showSpeedDialog());

        //User guide button
        btnUserGuide.setOnClickListener(v -> {
            String guideText = "Καλώς ήρθες στον οδηγό χρήσης! " +
                    "Για να αλλάξεις οθόνη, κάνε απλά swipe αριστερά ή δεξιά με το δάχτυλό σου ή χρησιμοποίησε τα πλαϊνά κουμπιά του τηλεφώνου σου. Με πάτημα του πάνω κουμπιού μία φορά κατευθύνεσαι στην κεντρική οθόνη του μικροφώνου, με πάτημα του κάτω μία φορά ανοίγεις τη κάμερα ενώ με πάτημα του κάτω δύο φορές πηγαίνεις στις ρυθμίσεις. " +
                    "Στην κεντρική οθόνη, άνοιξε το μικρόφωνο και πες τη διαδρομή στην οποία θες να πλοηγηθείς, όπως 'Πήγαινε με στο Ταφ 101' για να ξεκινήσει η πλοήγηση. " +
                    "Εναλλακτικά, πες 'Σκάναρε με κάμερα' για να σου διαβάσω οποιοδήποτε κείμενο βλέπει η κάμερα του κινητού σου!";

            speakText(guideText);
            Toast.makeText(this, "Ακούστε τον οδηγό...", Toast.LENGTH_SHORT).show();
        });

        // feedback button-not implemented yet
        btnFeedback.setOnClickListener(v -> {
            Toast.makeText(this, "Η λειτουργία ανατροφοδότησης θα προστεθεί σύντομα!", Toast.LENGTH_SHORT).show();
        });

        //Bottom navigation bar
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, MainActivity.class)); //go to main activity screen
            finish();
        });

        findViewById(R.id.nav_camera).setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, CameraActivity.class)); //go to camera screen
            finish();
        });

        //Swipe gesture navigation (same pattern as the other screens)
        gestureDetector = new GestureDetectorCompat(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                if (e1 == null || e2 == null) return false;
                float diffX = e2.getX() - e1.getX();
                float diffY = e2.getY() - e1.getY();

                if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 120 && Math.abs(velocityX) > 120) {
                    if (diffX < 0) {
                        // Swipe left to right: Go to Main screen
                        startActivity(new Intent(SettingsActivity.this, CameraActivity.class));
                        finish();
                    } else {
                        // Swipe right to left: Go to Camera screen
                        startActivity(new Intent(SettingsActivity.this, MainActivity.class));
                        finish();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    //Speed dialog for changing the TTS speed
    private void showSpeedDialog() {
        String[] options = {"Αργό (0.5x)", "Μέτριο (1.0x)", "Γρήγορο (1.5x)"};

        //Read the current speed to check its box
        float currentSpeed = prefs.getFloat("tts_speed", 1.0f);
        int checkedItem = 1; // Default spead is "Μέτριο" (1.0χ)
        if (currentSpeed == 0.5f) checkedItem = 0;
        else if (currentSpeed == 1.5f) checkedItem = 2;

        //Build the popup
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ταχύτητα Φωνής");

        builder.setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
            float newSpeed = 1.0f;
            if (which == 0) newSpeed = 0.5f;
            else if (which == 1) newSpeed = 1.0f;
            else if (which == 2) newSpeed = 1.5f;

            //Save the new speed
            prefs.edit().putFloat("tts_speed", newSpeed).apply();

            //Apply the change live and speak a sample so the user is reassured
            if (tts != null) {
                tts.setSpeechRate(newSpeed);
                speakText("Η ταχύτητα της φωνής άλλαξε.");
            }

            //Close the dialog automatically
            dialog.dismiss();
        });

        builder.setNegativeButton("Ακύρωση", null); //button for closing without changing the voice speed
        builder.show();
    }

    private void speakText(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        //Up button pressed: Navigate to main screen
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP && event.getRepeatCount() == 0) {
            startActivity(new Intent(SettingsActivity.this, MainActivity.class));
            finish();
            return true;
        }
        // Down button pressed: 1 press-> Camera, 2 presses -> Settings
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN && event.getRepeatCount() == 0) {
            handleVolumeDownNavigation();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    //Also consume the key-up of the volume buttons so the system volume slider doesn't appear
    @Override
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    // Single/double press of down button handling
    private void handleVolumeDownNavigation() {
        long now = System.currentTimeMillis();
        if (now - lastVolumeDownTime < VOLUME_DOUBLE_PRESS_INTERVAL) {
            //Double press: cancel the scheduled single-press action. We're already in Settings, so stay
            if (volumeDownSingleTapRunnable != null) {
                volumeNavHandler.removeCallbacks(volumeDownSingleTapRunnable);
                volumeDownSingleTapRunnable = null;
            }
            lastVolumeDownTime = 0;
        } else {
            //Single press: wait briefly for a possible second press, otherwise go to Camera
            lastVolumeDownTime = now;
            volumeDownSingleTapRunnable = () -> {
                startActivity(new Intent(SettingsActivity.this, CameraActivity.class));
                finish();
                volumeDownSingleTapRunnable = null;
            };
            volumeNavHandler.postDelayed(volumeDownSingleTapRunnable, VOLUME_DOUBLE_PRESS_INTERVAL);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        volumeNavHandler.removeCallbacksAndMessages(null);
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}