package com.example.auebnavigator;

import android.speech.tts.TextToSpeech;
import java.util.Locale;
import android.annotation.SuppressLint;
import android.media.AudioManager;
import android.media.ToneGenerator;
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

    private static final float NOISE_THRESHOLD_DB = 5.0f; // Το κατώφλι των decibel. Παίξε με αυτό!
    private long lastSpeechTime = 0;
    private static final long SILENCE_TIMEOUT_MS = 1500; // 1.5 δευτερόλεπτο ησυχίας για να "κόψει"
    private String currentLocation = null;
    private boolean isWaitingForLocation = false;
    private String pendingDestination = null;
    private boolean isProcessingCommand = false; // Προστασία για να μην εκτελείται 2 φορές η ίδια εντολή

    private TextToSpeech tts;
    private ToneGenerator toneGen;
    private android.view.animation.Animation pulseAnim;
    private FrameLayout btnMic;
    private Vibrator vibrator;
    private GestureDetectorCompat gestureDetector;

    private SpeechRecognizer speechRecognizer;
    private Intent speechIntent;
    private static final int RECORD_AUDIO_PERMISSION_CODE = 100;

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        btnMic = findViewById(R.id.btn_mic);
        gestureDetector = new GestureDetectorCompat(this, new SwipeListener());

        setupSpeechRecognizer();

        btnMic.setOnTouchListener((v, event) -> {
            if (gestureDetector.onTouchEvent(event)) {
                return true;
            }
            return false;
        });

        //  Ο ΕΞΥΠΝΟΣ ΜΗΧΑΝΙΣΜΟΣ ΠΟΥ ΞΕΧΩΡΙΖΕΙ ΤΟ CLICK ΑΠΟ ΤΟ SWIPE
        btnMic.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private boolean isSwipe = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                // Πρώτα δίνουμε το άγγιγμα στον ανιχνευτή για να δει αν είναι Swipe
                gestureDetector.onTouchEvent(event);

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        // Μόλις ακουμπήσει το δάχτυλο, κρατάμε τις συντεταγμένες
                        startX = event.getX();
                        startY = event.getY();
                        isSwipe = false;
                        return true; // Κρατάμε το άγγιγμα "ζωντανό"

                    case MotionEvent.ACTION_MOVE:
                        // Αν το δάχτυλο κουνηθεί πάνω από 50 pixels, το θεωρούμε Swipe!
                        if (Math.abs(event.getX() - startX) > 50 || Math.abs(event.getY() - startY) > 50) {
                            isSwipe = true;
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        // Όταν σηκωθεί το δάχτυλο, αν ΔΕΝ ήταν swipe, τρέχουμε το μικρόφωνο (Απλό Click)
                        if (!isSwipe) {
                            handleMicClick();
                        }
                        break;
                }
                return true;
            }
        });

        pulseAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse);
        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("el", "GR"));
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    float speed = getSharedPreferences("AuebNavPrefs", MODE_PRIVATE).getFloat("tts_speed", 1.0f);
                    tts.setSpeechRate(speed);
                    speakText("To σύστημα πλοήγησης είναι έτοιμο!.");
                }
            }
        });

        // Κλικ στα εικονίδια της κάτω μπάρας
        findViewById(R.id.nav_camera).setOnClickListener(v -> {
            if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
            startActivity(new Intent(MainActivity.this, CameraActivity.class));
        });

        findViewById(R.id.nav_settings).setOnClickListener(v -> {
            if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });
    }

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
    protected void onResume() {
        super.onResume();
        if (speechRecognizer == null) setupSpeechRecognizer();
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
        }
        if (btnMic != null) btnMic.clearAnimation();
    }

    private void handleMicClick() {
        if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(50);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListeningNow();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_CODE);
        }
    }

    private void startListeningNow() {
        isProcessingCommand = false;
        if (speechRecognizer != null && speechIntent != null) {
            speechRecognizer.cancel();
            speechRecognizer.startListening(speechIntent);
        } else {
            setupSpeechRecognizer();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListeningNow();
        }
    }

    private void setupSpeechRecognizer() {
        if (speechRecognizer != null) speechRecognizer.destroy();

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "el-GR");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                btnMic.startAnimation(pulseAnim);
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
                lastSpeechTime = System.currentTimeMillis(); // Αρχικοποιούμε τον χρόνο
            }
            @Override public void onBeginningOfSpeech() { }
            @Override
            public void onRmsChanged(float rmsdB) {
                // Εκτυπώνουμε το επίπεδο θορύβου στο Logcat για να το μελετήσουμε!
                Log.d("AudioLevel", "Τρέχον dB: " + rmsdB);

                // Αν ο ήχος είναι πιο δυνατός από το όριό μας, ανανεώνουμε το χρονόμετρο
                if (rmsdB > NOISE_THRESHOLD_DB) {
                    lastSpeechTime = System.currentTimeMillis();
                } else {
                    // Αν έχει πέσει ησυχία για πάνω από τον χρόνο που ορίσαμε
                    if (System.currentTimeMillis() - lastSpeechTime > SILENCE_TIMEOUT_MS) {
                        if (speechRecognizer != null && !isProcessingCommand) {
                            // Κλείνουμε το μικρόφωνο
                            speechRecognizer.stopListening();
                        }
                    }
                }
            }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { btnMic.clearAnimation(); }
            @Override
            public void onError(int error) {
                btnMic.clearAnimation();
                if (error == SpeechRecognizer.ERROR_CLIENT) return;
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (!isFinishing()) setupSpeechRecognizer();
                }, 500);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                if (isProcessingCommand) return;
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    if (checkAndExecuteCommand(matches.get(0).toLowerCase())) {
                        isProcessingCommand = true;
                        speechRecognizer.cancel();
                        btnMic.clearAnimation();
                    }
                }
            }

            @Override
            public void onResults(Bundle results) {
                if (isProcessingCommand) return;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    checkAndExecuteCommand(matches.get(0).toLowerCase());
                }
            }
            @Override public void onEvent(int eventType, Bundle params) { }
        });
    }

    //  Η καρδιά του συστήματος. Επιστρέφει true αν βρήκε εντολή.
    private boolean checkAndExecuteCommand(String spokenText) {
        if (isWaitingForLocation) {
            // Περίπτωση 1: Ο χρήστης είναι στο Ισόγειο (Κεντρική Είσοδος)
            if (spokenText.contains("είσοδο") || spokenText.contains("εισοδο") || spokenText.contains("ισόγειο")) {
                currentLocation = "Κεντρική Είσοδος"; // Το νέο node που βάλαμε στον 3D γράφο!
                isWaitingForLocation = false;
                speakText("Τέλεια. Ξεκινάμε την πλοήγηση από την είσοδο για την αίθουσα " + pendingDestination + ".");

                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                intent.putExtra("START_LOCATION", currentLocation);
                intent.putExtra("DESTINATION", pendingDestination);
                pendingDestination = null;
                closeMicAndNavigate(intent);
                return true;
            }
            // Περίπτωση 2: Ο χρήστης είναι στον 1ο Όροφο (Κεφαλόσκαλο)
            else if (spokenText.contains("ναι") || spokenText.contains("κεφαλόσκαλο") || spokenText.contains("σκάλα")) {
                currentLocation = "Κεφαλόσκαλο";
                isWaitingForLocation = false;
                speakText("Τέλεια. Ξεκινάμε την πλοήγηση από το κεφαλόσκαλο για την αίθουσα " + pendingDestination + ".");

                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                intent.putExtra("START_LOCATION", currentLocation);
                intent.putExtra("DESTINATION", pendingDestination);
                pendingDestination = null;
                closeMicAndNavigate(intent);
                return true;
            }
            // Περίπτωση 3: Ακύρωση
            else if (spokenText.contains("όχι") || spokenText.contains("οχι") || spokenText.contains("άκυρο")) {
                speakText("Εντάξει, η πλοήγηση ακυρώθηκε.");
                isWaitingForLocation = false;
                pendingDestination = null;
                return true;
            }
            return false;
        }

        if (spokenText.contains("σκάναρε") || spokenText.contains("σκαναρε")) {
            speakText("Λειτουργία σάρωσης κειμένου ενεργή. Σήκωσε την κάμερα.");
            Intent intent = new Intent(MainActivity.this, CameraActivity.class);
            intent.putExtra("ENABLE_OCR", true);
            closeMicAndNavigate(intent);
            return true;
        }

        if ((spokenText.contains("κάμερα") || spokenText.contains("καμερα")) && (spokenText.contains("άνοιξε") || spokenText.contains("ανοιξε"))) {
            speakText("Ανοίγω την κάμερα.");
            closeMicAndNavigate(new Intent(MainActivity.this, CameraActivity.class));
            return true;
        }

        if (spokenText.contains("πήγαινε") || spokenText.contains("θέλω να πάω") || spokenText.contains("πού είναι")) {
            String destination = null;
            if (spokenText.contains("τ 101") || spokenText.contains("101")) destination = "T101";
            else if (spokenText.contains("τ 102") || spokenText.contains("102")) destination = "T102";
            else if (spokenText.contains("τ 103") || spokenText.contains("103")) destination = "T103";

                //  ΕΞΥΠΝΟ MAPPING ΓΙΑ ΤΟΥΑΛΕΤΕΣ
            else if (spokenText.contains("τουαλέτες") || spokenText.contains("τουαλετες")) {
                if (spokenText.contains("ισόγειο") || spokenText.contains("ισογείου")) {
                    destination = "Τουαλέτες Ισόγειο"; // Node ισογείου
                } else {
                    destination = "Τουαλέτες"; // Node 1ου ορόφου
                }
            }

            //  ΕΞΥΠΝΟ MAPPING ΓΙΑ ΑΣΑΝΣΕΡ
            else if (spokenText.contains("ασανσέρ") || spokenText.contains("ασανσερ")) {
                if (spokenText.contains("ισόγειο") || spokenText.contains("ισογείου")) {
                    destination = "Ασανσέρ Ισόγειο"; // Node ισογείου
                } else {
                    destination = "Ασανσέρ"; // Node 1ου ορόφου
                }
            }

            else if (spokenText.contains("έξοδο κινδύνου") || spokenText.contains("εξοδο κινδυνου")) destination = "Έξοδος Κινδύνου";

            if (destination != null) {
                pendingDestination = destination;
                isWaitingForLocation = true;
                speakText("Πολύ ωραία. Για να σε πάω στο " + destination + ", πες μου: Βρίσκεσαι στην είσοδο του κτηρίου ή κάπου αλλού;");
                return true;
            }
        }
        return false;
    }

    private class SwipeListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onDown(MotionEvent e) { return true; }

        @Override
        public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
            if (e1 == null || e2 == null) return false;
            float diffX = e2.getX() - e1.getX();
            float diffY = e2.getY() - e1.getY();

            if (Math.abs(diffX) > Math.abs(diffY) && Math.abs(diffX) > 100 && Math.abs(velocityX) > 100) {
                if (diffX > 0) {
                    if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                } else {
                    if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
                    startActivity(new Intent(MainActivity.this, CameraActivity.class));
                }
                return true;
            }
            return false;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (toneGen != null) toneGen.release();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP && event.getRepeatCount() == 0) {
            handleMicClick();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    private void closeMicAndNavigate(Intent intent) {
        if (speechRecognizer != null) speechRecognizer.cancel();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> startActivity(intent), 1500);
    }
}