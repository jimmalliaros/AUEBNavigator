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

public class SettingsActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private SharedPreferences prefs;
    private GestureDetectorCompat gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        prefs = getSharedPreferences("AuebNavPrefs", MODE_PRIVATE);

        // Αρχικοποίηση TTS για να μπορεί να μας μιλάει!
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("el", "GR"));
                // Εφαρμόζουμε την ταχύτητα που έχει ήδη αποθηκευμένη ο χρήστης
                float speed = prefs.getFloat("tts_speed", 1.0f);
                tts.setSpeechRate(speed);
            }
        });

        // Πιάνουμε τα Layouts (τα κουμπιά που έφτιαξες στο XML)
        LinearLayout btnVoiceSpeed = findViewById(R.id.btn_voice_speed);
        LinearLayout btnUserGuide = findViewById(R.id.btn_user_guide);
        LinearLayout btnFeedback = findViewById(R.id.btn_feedback);

        // --- 1. ΚΟΥΜΠΙ: ΤΑΧΥΤΗΤΑ ΦΩΝΗΣ ---
        btnVoiceSpeed.setOnClickListener(v -> showSpeedDialog());

        // --- 2. ΚΟΥΜΠΙ: ΟΔΗΓΟΣ ΧΡΗΣΗΣ ---
        btnUserGuide.setOnClickListener(v -> {
            // Αυτό είναι το κείμενο που θα διαβάσει η φωνή
            String guideText = "Καλώς ήρθες στον οδηγό χρήσης! " +
                    "Για να αλλάξεις οθόνη, κάνε απλά swipe αριστερά ή δεξιά με το δάχτυλό σου. " +
                    "Στην κεντρική οθόνη, πάτα το μικρόφωνο και πες 'Πήγαινε με στο Ταφ 101' για να ξεκινήσει η πλοήγηση. " +
                    "Εναλλακτικά, πες 'Σκάναρε με κάμερα' για να σου διαβάσω οποιοδήποτε κείμενο βλέπει η κάμερα του κινητού σου!";

            speakText(guideText);
            Toast.makeText(this, "Ακούστε τον οδηγό...", Toast.LENGTH_SHORT).show();
        });

        // --- 3. ΚΟΥΜΠΙ: FEEDBACK ---
        btnFeedback.setOnClickListener(v -> {
            Toast.makeText(this, "Η λειτουργία ανατροφοδότησης θα προστεθεί σύντομα!", Toast.LENGTH_SHORT).show();
        });

        // --- ΚΑΤΩ ΜΠΑΡΑ ΠΛΟΗΓΗΣΗΣ ---
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, MainActivity.class));
            finish();
        });

        findViewById(R.id.nav_camera).setOnClickListener(v -> {
            startActivity(new Intent(SettingsActivity.this, CameraActivity.class));
            finish();
        });

        // --- ΛΟΓΙΚΗ GESTURES (Το κλασικό Swipe που φτιάξαμε) ---
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
                        // Swipe Αριστερά: Πάμε Main
                        startActivity(new Intent(SettingsActivity.this, MainActivity.class));
                        finish();
                    } else {
                        // Swipe Δεξιά: Πάμε Camera
                        startActivity(new Intent(SettingsActivity.this, CameraActivity.class));
                        finish();
                    }
                    return true;
                }
                return false;
            }
        });
    }

    // 🔥 Η μέθοδος που πετάει το παραθυράκι με τις 3 ταχύτητες!
    private void showSpeedDialog() {
        String[] options = {"Αργό (0.5x)", "Μέτριο (1.0x)", "Γρήγορο (1.5x)"};

        // Διαβάζουμε ποια ταχύτητα έχει τώρα για να τσεκάρουμε την αντίστοιχη επιλογή
        float currentSpeed = prefs.getFloat("tts_speed", 1.0f);
        int checkedItem = 1; // Default είναι το "Μέτριο"
        if (currentSpeed == 0.5f) checkedItem = 0;
        else if (currentSpeed == 1.5f) checkedItem = 2;

        // Φτιάχνουμε το Popup (Dialog)
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Ταχύτητα Φωνής");

        builder.setSingleChoiceItems(options, checkedItem, (dialog, which) -> {
            float newSpeed = 1.0f;
            if (which == 0) newSpeed = 0.5f;
            else if (which == 1) newSpeed = 1.0f;
            else if (which == 2) newSpeed = 1.5f;

            // 1. Αποθηκεύουμε την επιλογή του χρήστη
            prefs.edit().putFloat("tts_speed", newSpeed).apply();

            // 2. Ενημερώνουμε τη φωνή LIVE και κάνουμε τεστ
            if (tts != null) {
                tts.setSpeechRate(newSpeed);
                speakText("Η ταχύτητα της φωνής άλλαξε.");
            }

            // 3. Κλείνουμε το παραθυράκι αυτόματα
            dialog.dismiss();
        });

        builder.setNegativeButton("Ακύρωση", null); // Κουμπί για κλείσιμο
        builder.show();
    }

    // Helper μέθοδος για να μιλάει η εφαρμογή εύκολα
    private void speakText(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return gestureDetector.onTouchEvent(event) || super.onTouchEvent(event);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}