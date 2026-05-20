package com.example.auebnavigator;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.view.MotionEvent;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;

public class SettingsActivity extends AppCompatActivity {

    private SeekBar seekBarSpeed;
    private TextView tvSpeedValue;
    private TextToSpeech tts;
    private SharedPreferences prefs;

    private androidx.core.view.GestureDetectorCompat gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        seekBarSpeed = findViewById(R.id.seekBarSpeed);
        tvSpeedValue = findViewById(R.id.tvSpeedValue);
        prefs = getSharedPreferences("AuebNavPrefs", MODE_PRIVATE);

        // Ανάγνωση υπάρχουσας ταχύτητας (default: 1.0f)
        float currentSpeed = prefs.getFloat("tts_speed", 1.0f);
        // Μετατροπή float σε τιμή SeekBar (0.5x έως 3.5x)
        seekBarSpeed.setProgress((int)((currentSpeed - 0.5f) * 100));
        tvSpeedValue.setText("Ταχύτητα: " + currentSpeed + "x");

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("el", "GR"));
            }
        });

        seekBarSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float calculatedSpeed = 0.5f + (progress / 100f);
                tvSpeedValue.setText("Ταχύτητα: " + String.format(Locale.US, "%.1f", calculatedSpeed) + "x");
                // Αποθήκευση live στα SharedPreferences
                prefs.edit().putFloat("tts_speed", calculatedSpeed).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Μόλις ο χρήστης αφήσει τη μπάρα, παίζει ένα δοκιμαστικό ηχητικό δείγμα
                float speed = prefs.getFloat("tts_speed", 1.0f);
                if (tts != null) {
                    tts.setSpeechRate(speed);
                    tts.speak("Δοκιμή ταχύτητας", TextToSpeech.QUEUE_FLUSH, null, null);
                }
            }
        });

        // Κλικ στα εικονίδια της κάτω μπάρας
        findViewById(R.id.nav_home).setOnClickListener(v -> {
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        findViewById(R.id.nav_camera).setOnClickListener(v -> {
            startActivity(new Intent(this, CameraActivity.class));
            finish();
        });

        gestureDetector = new androidx.core.view.GestureDetectorCompat(this, new android.view.GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
                float diffX = e2.getX() - e1.getX();
                if (Math.abs(diffX) > 120 && Math.abs(velocityX) > 120) {
                    if (diffX < 0) {
                        // Swipe Αριστερά: Πάμε στο Main Activity
                        startActivity(new Intent(SettingsActivity.this, MainActivity.class));
                        finish();
                    } else {
                        // Swipe Δεξιά: Πάμε στην Κάμερα (Κυκλική πλοήγηση)
                        startActivity(new Intent(SettingsActivity.this, CameraActivity.class));
                        finish();
                    }
                    return true;
                }
                return false;
            }
        });
    } // <-- ΕΔΩ κλείνει σωστά η onCreate()!

    // 🔥 Η onTouchEvent βγήκε ΕΞΩ από την onCreate() και είναι αυτόνομη
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