package com.example.auebnavigator; // Βάλε το δικό σου package

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;

import java.util.ArrayList;

public class MainActivity extends AppCompatActivity {

    private FrameLayout btnMic;
    private Vibrator vibrator;
    private GestureDetectorCompat gestureDetector;

    // Μεταβλητές για το μικρόφωνο και τις άδειες
    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private static final int RECORD_AUDIO_PERMISSION_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        btnMic = findViewById(R.id.btn_mic);
        gestureDetector = new GestureDetectorCompat(this, new SwipeListener());

        // 1. Στήνουμε τα "αυτιά" της εφαρμογής
        setupSpeechRecognizer();

        // 2. Click Listener για το μικρόφωνο
        btnMic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                handleMicClick();
            }
        });
    }

    // Στέλνουμε τα αγγίγματα στον GestureDetector
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        this.gestureDetector.onTouchEvent(event);
        return super.onTouchEvent(event);
    }

    // --- ΛΟΓΙΚΗ ΜΙΚΡΟΦΩΝΟΥ ΚΑΙ ΑΔΕΙΩΝ ---

    private void handleMicClick() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(50);
        }

        // Ελέγχουμε αν έχουμε άδεια πριν ανοίξουμε μικρόφωνο
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListeningNow();
        } else {
            // Ζητάμε άδεια με popup
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_CODE);
        }
    }

    private void startListeningNow() {
        Toast.makeText(this, "Ακούω...", Toast.LENGTH_SHORT).show();
        if (speechRecognizer != null && speechIntent != null) {
            speechRecognizer.cancel();
            speechRecognizer.startListening(speechIntent);
        } else {
            Toast.makeText(this, "Σφάλμα: Το μικρόφωνο δεν είναι έτοιμο.", Toast.LENGTH_SHORT).show();
        }
    }

    // Διαχείριση της απάντησης του χρήστη στο popup της άδειας
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startListeningNow();
            } else {
                Toast.makeText(this, "Η πρόσβαση στο μικρόφωνο είναι απαραίτητη!", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "el-GR"); // Ελληνικά

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { Log.d("Speech", "Ready"); }
            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { Log.d("Speech", "Ended"); }
            @Override public void onError(int error) {
                Toast.makeText(MainActivity.this, "Σφάλμα: " + error, Toast.LENGTH_SHORT).show();
            }
            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    // Παίρνουμε το επικρατέστερο αποτέλεσμα και το κάνουμε μικρά γράμματα για σωστή σύγκριση
                    String spokenText = matches.get(0).toLowerCase();

                    Log.d("Speech", "Είπες: " + spokenText);

                    // Έλεγχος για λέξεις-κλειδιά που αφορούν την κάμερα
                    if (spokenText.contains("κάμερα") || spokenText.contains("camera") ||
                            spokenText.contains("φωτογραφία") || spokenText.contains("άνοιξε")) {

                        // Ηχητική επιβεβαίωση πριν την αλλαγή οθόνης (Πολύ σημαντικό για τυφλούς!)
                        Toast.makeText(MainActivity.this, "Ανοίγω την κάμερα...", Toast.LENGTH_SHORT).show();

                        // Καλούμε τη μέθοδο που ήδη φτιάξαμε για το Swipe Right
                        onSwipeRight();

                    } else {
                        // Αν δεν κατάλαβε, δώσε ένα feedback
                        Toast.makeText(MainActivity.this, "Δεν κατάλαβα την εντολή: " + spokenText, Toast.LENGTH_LONG).show();
                    }
                }
            }
            @Override public void onPartialResults(Bundle partialResults) { }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
    }

    // --- ΛΟΓΙΚΗ SWIPING ---

    private class SwipeListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            boolean result = false;
            try {
                float diffY = e2.getY() - e1.getY();
                float diffX = e2.getX() - e1.getX();

                if (Math.abs(diffX) > Math.abs(diffY)) {
                    if (Math.abs(diffX) > SWIPE_THRESHOLD && Math.abs(velocityX) > SWIPE_VELOCITY_THRESHOLD) {
                        if (diffX > 0) {
                            onSwipeRight();
                        } else {
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

    private void onSwipeRight() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(30);
        }
        // Σιγουρέψου ότι έχεις φτιάξει το CameraActivity, αλλιώς βγάλτο σε σχόλιο!
        Intent intent = new Intent(MainActivity.this, CameraActivity.class);
        startActivity(intent);
    }

    private void onSwipeLeft() {
        Toast.makeText(this, "Swipe Left", Toast.LENGTH_SHORT).show();
    }

    // Απελευθέρωση μνήμης!
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }
    }
}