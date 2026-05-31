package com.example.auebnavigator;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.FrameLayout;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;

import java.util.ArrayList;
import java.util.Locale;

/**
 MainActivity is the home screen of the application
 */

public class MainActivity extends AppCompatActivity {

    private String currentLocation = null;
    private boolean isWaitingForLocation = false;
    private String pendingDestination = null;
    private boolean isProcessingCommand = false;

    //VAD (Voice Activity Detection) variables
    private static final float NOISE_THRESHOLD_DB = 5.0f; //above this loudness = "user is speaking"
    private static final long SILENCE_TIMEOUT_MS = 1500;  //stop listening after this much silence
    private long lastSpeechTime = 0;  //timestamp of the last detected speech

    private TextToSpeech tts; //speaks feedback to the user
    private ToneGenerator toneGen;  //short beep when listening starts
    private android.view.animation.Animation pulseAnim; //pulsing animation on the mic button
    private FrameLayout btnMic;
    private Vibrator vibrator;
    private GestureDetectorCompat gestureDetector;  //detector for swipe gestures

    private SpeechRecognizer speechRecognizer;  //Android speech-to-text engine
    private Intent speechIntent; //configuration for the recognizer
    private static final int RECORD_AUDIO_PERMISSION_CODE = 100;

    //Navigation using the side buttons of the phone (volume up/down)
    private long lastVolumeDownTime = 0;
    private final android.os.Handler volumeNavHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable volumeDownSingleTapRunnable;
    private static final int VOLUME_DOUBLE_PRESS_INTERVAL = 450; //ms window to wait for a second press

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        btnMic = findViewById(R.id.btn_mic);
        gestureDetector = new GestureDetectorCompat(this, new SwipeListener());

        setupSpeechRecognizer();

        //Touch handling on the mic button: distinguish a real tap from a swipe
        btnMic.setOnTouchListener(new View.OnTouchListener() {
            private float startX, startY;
            private boolean isSwipe = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                gestureDetector.onTouchEvent(event); //let swipes still be detected

                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = event.getX();
                        startY = event.getY();
                        isSwipe = false;
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        //If the finger moved far enough, treat the gesture as a swipe, not a tap
                        if (Math.abs(event.getX() - startX) > 50 || Math.abs(event.getY() - startY) > 50) {
                            isSwipe = true;
                        }
                        break;

                    case MotionEvent.ACTION_UP:
                        //Only a genuine tap (no swipe) opens the microphone
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

        //Initialise text-to-speech (Greek) and announce that the app is ready
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("el", "GR"));
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    float speed = getSharedPreferences("AuebNavPrefs", MODE_PRIVATE).getFloat("tts_speed", 1.0f);
                    tts.setSpeechRate(speed);
                    speakText("To σύστημα πλοήγησης είναι έτοιμο!");
                }
            }
        });


        // Bottom-bar buttons: go to Camera / Settings (with a short vibration)
        findViewById(R.id.nav_camera).setOnClickListener(v -> {
            if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
            startActivity(new Intent(MainActivity.this, CameraActivity.class));
        });

        findViewById(R.id.nav_settings).setOnClickListener(v -> {
            if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        });
    }

    //speakText is a method where tts speaks the given text, interrupting anything currently being spoken
    private void speakText(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

   //Handling of a mic tap: vibrate, then start listening
    private void handleMicClick() {
        if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(50);
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startListeningNow();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.RECORD_AUDIO}, RECORD_AUDIO_PERMISSION_CODE);
        }
    }


    //startListeningNow cancels any previous session and starts a fresh listening session
    private void startListeningNow() {
        isProcessingCommand = false;
        if (speechRecognizer != null && speechIntent != null) {
            speechRecognizer.cancel();
            speechRecognizer.startListening(speechIntent);
        } else {
            setupSpeechRecognizer();
        }
    }

    // (Re)creation of the speech recognizer and wiring up all of its callbacks
    private void setupSpeechRecognizer() {
        if (speechRecognizer != null) speechRecognizer.destroy();

        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        speechIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        speechIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "el-GR");

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override
            public void onReadyForSpeech(Bundle params) {
                btnMic.startAnimation(pulseAnim); //visual "I'm listening" cue
                toneGen.startTone(ToneGenerator.TONE_PROP_BEEP, 150);  //audible cue (a beep)
                lastSpeechTime = System.currentTimeMillis(); //reset the silence timer
            }

            @Override
            public void onBeginningOfSpeech() {}

            @Override
            public void onRmsChanged(float rmsdB) {
                //Live microphone loudness: used for our custom silence detection (VAD)
                Log.d("AudioLevel", "Τρέχον dB: " + rmsdB);

                if (rmsdB > NOISE_THRESHOLD_DB) {
                    lastSpeechTime = System.currentTimeMillis(); //user is talking, keep listening
                } else {
                    //After enough continuous silence, stop the mic automatically
                    if (System.currentTimeMillis() - lastSpeechTime > SILENCE_TIMEOUT_MS) {
                        if (speechRecognizer != null && !isProcessingCommand) {
                            speechRecognizer.stopListening();
                        }
                    }
                }
            }

            @Override
            public void onBufferReceived(byte[] buffer) {}

            @Override
            public void onEndOfSpeech() {
                btnMic.clearAnimation();
            }

            @Override
            public void onError(int error) {
                btnMic.clearAnimation();
                if (error == SpeechRecognizer.ERROR_CLIENT) return;
                //Recover from other errors by rebuilding the recognizer after a short delay
                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    if (!isFinishing()) setupSpeechRecognizer();
                }, 500);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                // Act on partial results so commands feel snappy (no need to wait for the final result)
                if (isProcessingCommand) return;
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    if (checkAndExecuteCommand(matches.get(0).toLowerCase())) {
                        isProcessingCommand = true; //lock so we don't run it again on the final result
                        speechRecognizer.cancel();
                        btnMic.clearAnimation();
                    }
                }
            }

            @Override
            public void onResults(Bundle results) {
                //Final result: only used if a partial result didn't already trigger a command
                if (isProcessingCommand) return;
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    checkAndExecuteCommand(matches.get(0).toLowerCase());
                }
            }

            @Override
            public void onEvent(int eventType, Bundle params) {}
        });
    }

    private boolean checkAndExecuteCommand(String spokenText) {
        //Already have a destination,waiting for the starting point (2nd step)
        if (isWaitingForLocation) {
            if (spokenText.contains("είσοδο") || spokenText.contains("εισοδο") || spokenText.contains("ισόγειο")) {
                currentLocation = "Κεντρική Είσοδος";
                isWaitingForLocation = false;
                speakText("Τέλεια! Ξεκινάμε την πλοήγηση από την είσοδο με προορισμό " + pendingDestination + ".");

                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                intent.putExtra("START_LOCATION", currentLocation);
                intent.putExtra("DESTINATION", pendingDestination);
                pendingDestination = null;
                closeMicAndNavigate(intent);
                return true;
            } else if (spokenText.contains("ναι") || spokenText.contains("κεφαλόσκαλο") || spokenText.contains("σκάλα")) {
                currentLocation = "Κεφαλόσκαλο";
                isWaitingForLocation = false;
                speakText("Τέλεια! Ξεκινάμε την πλοήγηση από το κεφαλόσκαλο με προορισμό " + pendingDestination + ".");

                Intent intent = new Intent(MainActivity.this, CameraActivity.class);
                intent.putExtra("START_LOCATION", currentLocation);
                intent.putExtra("DESTINATION", pendingDestination);
                pendingDestination = null;
                closeMicAndNavigate(intent);
                return true;
            } else if (spokenText.contains("όχι") || spokenText.contains("οχι") || spokenText.contains("άκυρο")) {  //User aborted: reset the state machine.
                speakText("Εντάξει, η πλοήγηση ακυρώθηκε.");
                isWaitingForLocation = false;
                pendingDestination = null;
                return true;
            }
            return false;
        }

        //Commands for OCR scanning
        if (spokenText.contains("σάρωση με κάμερα") || spokenText.contains("σαρωση με καμερα") ||
                spokenText.contains("σκάναρε") || spokenText.contains("σκαναρε")) {
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

        //Parse the destination from the phrase (1st step)
        if (spokenText.contains("πήγαινε") || spokenText.contains("θέλω να πάω") || spokenText.contains("πού είναι")) {
            String destination = null;
            if (spokenText.contains("τ 101") || spokenText.contains("101")) destination = "T101";
            else if (spokenText.contains("τ 102") || spokenText.contains("102")) destination = "T102";
            else if (spokenText.contains("τ 103") || spokenText.contains("103")) destination = "T103";
            else if (spokenText.contains("τουαλέτες") || spokenText.contains("τουαλετες")) {
                destination = (spokenText.contains("ισόγειο") || spokenText.contains("ισογείου")) ? "Τουαλέτες Ισόγειο" : "Τουαλέτες";
            }
            else if (spokenText.contains("ασανσέρ") || spokenText.contains("ασανσερ")) {
                destination = (spokenText.contains("ισόγειο") || spokenText.contains("ισογείου")) ? "Ασανσέρ Ισόγειο" : "Ασανσέρ";
            }
            else if (spokenText.contains("έξοδο κινδύνου") || spokenText.contains("εξοδο κινδυνου")) destination = "Έξοδος Κινδύνου";

            if (destination != null) { //Got a destination: now ask for the starting point and switch to step 2
                pendingDestination = destination;
                isWaitingForLocation = true;
                speakText("Πολύ ωραία. Για να σε πάω στο " + destination + ", πες μου: Βρίσκεσαι στην είσοδο του κτηρίου ή κάπου αλλού;");
                return true;
            }
        }
        return false;
    }

    private void closeMicAndNavigate(Intent intent) {
        if (speechRecognizer != null) speechRecognizer.cancel();
        new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> startActivity(intent), 1500);
    }


    // Detection of left/right swipes to move between screens
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
                    // Swipe left to right : Go to Camera
                    if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
                    startActivity(new Intent(MainActivity.this, CameraActivity.class));
                } else {
                    // Swipe right to left: Go to Settings
                    if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
                    startActivity(new Intent(MainActivity.this, SettingsActivity.class));
                }
                return true;
            }
            return false;
        }
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        //Up button pressed: open microphone
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP && event.getRepeatCount() == 0) {
            handleMicClick();
            return true;
        }
        //Down button pressed: 1 press -> Camera, 2 presses -> Settings
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN && event.getRepeatCount() == 0) {
            handleVolumeDownNavigation();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    //Also consume the key-up of the volume buttons so the system volume slider doesn't pop up
    @Override
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    //Handling fo single/double down button press
    private void handleVolumeDownNavigation() {
        long now = System.currentTimeMillis();
        if (now - lastVolumeDownTime < VOLUME_DOUBLE_PRESS_INTERVAL) {
            // Double press of down button: cancel navigation to camera screen and go to settings instead
            if (volumeDownSingleTapRunnable != null) {
                volumeNavHandler.removeCallbacks(volumeDownSingleTapRunnable);
                volumeDownSingleTapRunnable = null;
            }
            lastVolumeDownTime = 0;
            if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
            startActivity(new Intent(MainActivity.this, SettingsActivity.class));
        } else {
            //One press of down button: wait a bit for a second press to see if the user wants to go to the settings tab, otherwise open Camera
            lastVolumeDownTime = now;
            volumeDownSingleTapRunnable = () -> {
                if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
                startActivity(new Intent(MainActivity.this, CameraActivity.class));
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
    protected void onResume() {
        super.onResume();
        if (speechRecognizer == null) setupSpeechRecognizer();
    }

    @Override
    protected void onStop() { //Stop listening when the screen is no longer in the foreground
        super.onStop();
        if (speechRecognizer != null) {
            speechRecognizer.stopListening();
            speechRecognizer.cancel();
        }
        if (btnMic != null) btnMic.clearAnimation();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        //If the user just granted the mic permission, start listening right away
        if (requestCode == RECORD_AUDIO_PERMISSION_CODE && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startListeningNow();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        volumeNavHandler.removeCallbacksAndMessages(null);
        if (speechRecognizer != null) speechRecognizer.destroy();
        if (toneGen != null) toneGen.release();
        if (tts != null) { tts.stop(); tts.shutdown(); }
    }
}