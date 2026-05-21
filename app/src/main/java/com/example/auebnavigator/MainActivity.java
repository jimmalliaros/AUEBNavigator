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

    // Κρατάει την τωρινή τοποθεσία του χρήστη (π.χ. "είσοδος", "πρώτος όροφος").
    // Το null σημαίνει ότι δεν ξέρουμε ακόμα.
    private String currentLocation = null;

    // Ένα flag που μας λέει αν η εφαρμογή περιμένει απάντηση για το πού βρίσκεται ο χρήστης.
    private boolean isWaitingForLocation = false;

    // Κρατάει τον προορισμό που ζήτησε ο χρήστης, για να τον θυμόμαστε αφού μας πει πού είναι.
    private String pendingDestination = null;

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

        // ✅ FIX Bug 3: Περνάμε τα touch events του κουμπιού στον GestureDetector
        // ώστε τα swipe που ξεκινούν πάνω στο κουμπί να λειτουργούν κανονικά
        btnMic.setOnTouchListener((v, event) -> {
            gestureDetector.onTouchEvent(event);
            // Επιτρέπουμε στο κουμπί να χειριστεί και αυτό το event (click)
            return false;
        });

        btnMic.setOnClickListener(v -> handleMicClick());

        pulseAnim = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.pulse);
        toneGen = new ToneGenerator(AudioManager.STREAM_MUSIC, 100);

        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("el", "GR"));

                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    // 🔥 Εφαρμογή της ταχύτητας και στην αρχική οθόνη
                    float speed = getSharedPreferences("AuebNavPrefs", MODE_PRIVATE).getFloat("tts_speed", 1.0f);
                    tts.setSpeechRate(speed);

                    speakText("To σύστημα πλοήγησης είναι έτοιμο!.");
                }
            }
        });
    }

    private void speakText(String text) {
        if (tts != null) {
            // Το QUEUE_FLUSH σημαίνει ότι αν μιλάει ήδη, το κόβει και λέει το καινούργιο αμέσως
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    // ✅ FIX Bug 2: Επιστρέφουμε το αποτέλεσμα του gestureDetector
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        boolean gestureResult = gestureDetector.onTouchEvent(event);
        return gestureResult || super.onTouchEvent(event);
    }

    // ✅ FIX Bug 5: Επανεκκίνηση recognizer όταν επιστρέφει η activity
    @Override
    protected void onResume() {
        super.onResume();
        if (speechRecognizer == null) {
            setupSpeechRecognizer();
        }
    }

    // ✅ FIX Bug 6: Σταματάμε το speech recognition όταν φεύγει η activity
    @Override
    protected void onStop() {
        super.onStop();
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
        }
        if (btnMic != null) {
            btnMic.clearAnimation();
        }
    }

    // --- ΛΟΓΙΚΗ ΜΙΚΡΟΦΩΝΟΥ ΚΑΙ ΑΔΕΙΩΝ ---

    private void handleMicClick() {
        if (vibrator != null && vibrator.hasVibrator()) {
            vibrator.vibrate(50);
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListeningNow();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_CODE);
        }
    }

    private void startListeningNow() {
        if (speechRecognizer != null && speechIntent != null) {
            speechRecognizer.cancel();
            speechRecognizer.startListening(speechIntent);
        } else {
            Toast.makeText(this, "Σφάλμα: Το μικρόφωνο δεν είναι έτοιμο.", Toast.LENGTH_SHORT).show();
            // Επανεκκίνηση recognizer αν δεν είναι έτοιμος
            setupSpeechRecognizer();
        }
    }

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
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
        }

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "el-GR");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                btnMic.startAnimation(pulseAnim);
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150);
                // Προαιρετικό: Ένα μικρό Toast για να ξέρεις ότι ξεκίνησε
                Toast.makeText(MainActivity.this, "Σε ακούω...", Toast.LENGTH_SHORT).show();
            }

            @Override public void onBeginningOfSpeech() { }
            @Override public void onRmsChanged(float rmsdB) { }
            @Override public void onBufferReceived(byte[] buffer) { }
            @Override public void onEndOfSpeech() { btnMic.clearAnimation(); }

            @Override
            public void onError(int error) {
                btnMic.clearAnimation();

                // 🔥 FIX: Αν το σφάλμα είναι Client (5), απλά το κάνουμε ignore και σταματάμε την εκτέλεση
                if (error == SpeechRecognizer.ERROR_CLIENT) {
                    Log.d("Speech", "Client error αγνοήθηκε λόγω αλλαγής activity.");
                    return;
                }

                // Για τα υπόλοιπα σφάλματα (π.χ. timeout) δείχνουμε το Toast
                String errorMsg = getSpeechErrorMessage(error);
                Toast.makeText(MainActivity.this, "Σφάλμα: " + errorMsg, Toast.LENGTH_SHORT).show();

                // Επανεκκίνηση για το επόμενο κλικ
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (!isFinishing()) {
                        setupSpeechRecognizer();
                    }
                }, 500);
            }

            // --- ΕΔΩ ΕΙΝΑΙ Η ΑΛΛΑΓΗ ΠΟΥ ΘΕΣ ---

            @Override
            public void onPartialResults(Bundle partialResults) {
                // Την αφήνουμε άδεια για να μην εμφανίζει τίποτα όσο μιλάς
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String spokenText = matches.get(0).toLowerCase();
                    Log.d("Speech", "Ο χρήστης είπε: " + spokenText);

                    // --- CHECK 1: Περιμένουμε τοποθεσία; ---
                    if (isWaitingForLocation) {
                        handleLocationResponse(spokenText);
                        return; // Σταματάμε εδώ, μην πάει παρακάτω
                    }

                    // --- CHECK 2: Κάμερα ---
                    boolean wantsCamera = spokenText.contains("κάμερα") || spokenText.contains("καμερα");
                    boolean wantsOpen = spokenText.contains("άνοιξε") || spokenText.contains("ανοιξε");

                    if (wantsCamera && wantsOpen) {
                        speakText("Ανοίγω την κάμερα.");
                        closeMicAndNavigate(new Intent(MainActivity.this, CameraActivity.class));
                        return;
                    }

                    // Μέσα στο onResults, στο σημείο // --- CHECK 3: Πλοήγηση ---
                    if (spokenText.contains("πήγαινε") || spokenText.contains("θέλω να πάω") || spokenText.contains("πού είναι")) {
                        String destination = null;

                        if (spokenText.contains("τ 101") || spokenText.contains("101")) destination = "T101";
                        else if (spokenText.contains("τ 102") || spokenText.contains("102")) destination = "T102";
                        else if (spokenText.contains("τ 103") || spokenText.contains("103")) destination = "T103";
                        else if (spokenText.contains("τουαλέτες") || spokenText.contains("τουαλετες")) destination = "Τουαλέτες";
                        else if (spokenText.contains("ασανσέρ") || spokenText.contains("ασανσερ")) destination = "Ασανσέρ";
                        else if (spokenText.contains("έξοδο κινδύνου") || spokenText.contains("εξοδο κινδυνου")) destination = "Έξοδος Κινδύνου";

                        if (destination != null) {
                            if (currentLocation == null) {
                                pendingDestination = destination;
                                isWaitingForLocation = true;
                                // Αλλάζουμε την ερώτηση για να ταιριάζει στον όροφο
                                speakText("Πολύ ωραία. Για να σε πάω στο " + destination + ", πες μου: Βρίσκεσαι στο κεφαλόσκαλο του πρώτου ορόφου;");
                            } else {
                                provideNavigationInstructions(currentLocation, destination);
                            }
                        } else {
                            speakText("Δεν αναγνώρισα αυτή την αίθουσα στον πρώτο όροφο. Δοκίμασε ξανά.");
                        }
                    }
                }
            }

            @Override public void onEvent(int eventType, Bundle params) { }
        });
    }

    // Επεξεργάζεται την απάντηση του χρήστη για το πού βρίσκεται
    // Μέσα στο MainActivity.java

    private void handleLocationResponse(String spokenText) {
        // Ελέγχουμε αν ο χρήστης επιβεβαίωσε ότι είναι στο κεφαλόσκαλο
        if (spokenText.contains("ναι") || spokenText.contains("κεφαλόσκαλο") || spokenText.contains("σκάλα")) {
            currentLocation = "Κεφαλόσκαλο"; // Αυτό πρέπει να είναι ολόιδιο με το όνομα στον AuebGraph
            isWaitingForLocation = false;
            speakText("Τέλεια. Ξεκινάμε την πλοήγηση για την αίθουσα " + pendingDestination + ".");

            Intent intent = new Intent(MainActivity.this, CameraActivity.class);
            intent.putExtra("START_LOCATION", currentLocation);
            intent.putExtra("DESTINATION", pendingDestination);

            pendingDestination = null;
            closeMicAndNavigate(intent);
        } else {
            speakText("Δεν κατάλαβα. Είσαι στο κεφαλόσκαλο του πρώτου ορόφου; Πες ναι ή όχι.");
        }
    }

    // Η "Καρδιά" του routing: Δίνει διαφορετικές οδηγίες ανάλογα με την αφετηρία
    private void provideNavigationInstructions(String start, String end) {
        if (start.equals("Κεντρική Είσοδος") && end.equals("Αμφιθέατρο Α")) {
            speakText("Από την κεντρική είσοδο: Προχώρα ευθεία, πέρνα τις κεντρικές σκάλες, και θα βρεις το Αμφιθέατρο Α στα δεξιά σου.");
        }
        else if (start.equals("Κεντρική Είσοδος") && end.equals("Γραμματεία")) {
            speakText("Από την κεντρική είσοδο: Πήγαινε στα αριστερά σου για να βρεις το ασανσέρ. Ανέβα στον τρίτο όροφο.");
        }
        else if (start.equals("Κεντρική Είσοδος") && end.equals("Κυλικείο")) {
            speakText("Από την κεντρική είσοδο: Προχώρα ευθεία μέχρι το τέλος του διαδρόμου. Το κυλικείο είναι ακριβώς μπροστά σου.");
        }
        // Εδώ μπορείς να προσθέσεις όσους συνδυασμούς θες!
    }
    // ✅ Helper: Μετατρέπει τον κωδικό σφάλματος σε ανθρώπινο μήνυμα
    private String getSpeechErrorMessage(int errorCode) {
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO: return "Σφάλμα ήχου";
            case SpeechRecognizer.ERROR_CLIENT: return "Σφάλμα client";
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "Δεν υπάρχουν άδειες (RECORD_AUDIO ή INTERNET)";
            case SpeechRecognizer.ERROR_NETWORK: return "Σφάλμα δικτύου - έλεγξε το internet";
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "Timeout δικτύου";
            case SpeechRecognizer.ERROR_NO_MATCH: return "Δεν βρέθηκε αντιστοιχία";
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY: return "Ο recognizer είναι απασχολημένος";
            case SpeechRecognizer.ERROR_SERVER: return "Σφάλμα server";
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "Δεν εντοπίστηκε ομιλία";
            default: return "Άγνωστο σφάλμα (" + errorCode + ")";
        }
    }

    // --- ΛΟΓΙΚΗ SWIPING ---

    private class SwipeListener extends GestureDetector.SimpleOnGestureListener {
        private static final int SWIPE_THRESHOLD = 100;
        private static final int SWIPE_VELOCITY_THRESHOLD = 100;

        @Override
        public boolean onFling(MotionEvent e1, @NonNull MotionEvent e2, float velocityX, float velocityY) {
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
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(30);
            }
            Intent intent = new Intent(MainActivity.this, CameraActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("NavigationError", "Δεν μπόρεσα να ανοίξω την κάμερα: " + e.getMessage());
            Toast.makeText(this, "Πρόβλημα στο άνοιγμα της κάμερας", Toast.LENGTH_SHORT).show();
        }
    }

    private void onSwipeLeft() {
        try {
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(30);
            }
            Intent intent = new Intent(MainActivity.this, SettingsActivity.class);
            startActivity(intent);
        } catch (Exception e) {
            Log.e("NavigationError", "Δεν μπόρεσα να ανοίξω τα settings: " + e.getMessage());
            Toast.makeText(this, "Πρόβλημα στο άνοιγμα των settings", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        if (toneGen != null) {
            toneGen.release();
        }
        // Καθαρισμός του TTS
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }

    // Κάνουμε "Hijack" τα πατήματα των φυσικών κουμπιών
    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        // Αν το κουμπί που πατήθηκε είναι το Volume Up
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP) {

            // Το event.getRepeatCount() == 0 εξασφαλίζει ότι θα πιάσει μόνο
            // το πρώτο πάτημα, και όχι τα συνεχόμενα αν το κρατάει πατημένο
            if (event.getRepeatCount() == 0) {
                Log.d("HardwareTrigger", "Πατήθηκε το Volume Up - Ενεργοποίηση μικροφώνου");

                // Προσομοιώνουμε το πάτημα του ψηφιακού κουμπιού
                handleMicClick();
            }

            // Επιστρέφουμε true για να πούμε στο Android:
            // "Το χειρίστηκα εγώ, μην δυναμώσεις την ένταση του ήχου"
            return true;
        }

        // Αν πατήθηκε οποιοδήποτε άλλο κουμπί (π.χ. back button),
        // αφήνουμε το Android να κάνει τη δουλειά του
        return super.onKeyDown(keyCode, event);
    }

    // Μέθοδος που κλείνει το μικρόφωνο και αλλάζει οθόνη με ασφάλεια
    private void closeMicAndNavigate(Intent intent) {
        if (speechRecognizer != null) {
            speechRecognizer.cancel();
        }
        // Δίνουμε 1.5 δευτερόλεπτο στο TTS να μιλήσει πριν αλλάξει η οθόνη
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> startActivity(intent), 1500);
    }
}
