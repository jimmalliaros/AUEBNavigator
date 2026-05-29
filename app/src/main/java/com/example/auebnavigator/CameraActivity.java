package com.example.auebnavigator;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GestureDetectorCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity implements
        StepDetector.StepListener,
        ObstacleAnalyzer.ObstacleListener,
        AuebTextRecognizer.OcrListener {

    private PreviewView viewFinder;
    private OcrFocusOverlayView ocrFocusOverlay;
    private ExecutorService cameraExecutor;
    private SensorManager sensorManager;
    private StepDetector stepDetector;
    private VoiceManager voiceManager;

    // --- Πλοήγηση με τα πλαϊνά (φυσικά) κουμπιά έντασης ---
    private android.os.Vibrator vibrator;
    private long lastVolumeDownTime = 0;
    private final android.os.Handler volumeNavHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable volumeDownSingleTapRunnable;
    private static final int VOLUME_DOUBLE_PRESS_INTERVAL = 450; // ms ανοχής μεταξύ δύο πατημάτων

    // --- Πλοήγηση με σύρσιμο δαχτύλου (swipe) ---
    private GestureDetectorCompat gestureDetector;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        viewFinder = findViewById(R.id.viewFinder);
        ocrFocusOverlay = findViewById(R.id.ocrFocusOverlay);
        cameraExecutor = Executors.newSingleThreadExecutor();
        voiceManager = VoiceManager.getInstance(this, null);

        vibrator = (android.os.Vibrator) getSystemService(VIBRATOR_SERVICE);

        // --- ΛΟΓΙΚΗ GESTURES (ίδιο Swipe με Main/Settings) ---
        // Αριστερά προς δεξιά: Πάμε Ρυθμίσεις. Δεξιά προς αριστερά: Πάμε Αρχική.
        gestureDetector = new GestureDetectorCompat(this, new GestureDetector.SimpleOnGestureListener() {
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
                    if (diffX > 0) {
                        // Swipe Αριστερά προς δεξιά: Πάμε Ρυθμίσεις
                        if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
                        startActivity(new Intent(CameraActivity.this, SettingsActivity.class));
                        finish();
                    } else {
                        // Swipe Δεξιά προς αριστερά: Πάμε Αρχική
                        if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
                        startActivity(new Intent(CameraActivity.this, MainActivity.class));
                        finish();
                    }
                    return true;
                }
                return false;
            }
        });

        // Αρχικοποίηση αισθητήρων με try-catch
        try {
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            stepDetector = new StepDetector(this, this);
        } catch (Exception e) {
            Log.e("CameraActivity", "Sensor init failed: " + e.getMessage());
        }

        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 10);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                // Εδώ συνδέουμε τα analyzers μας
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);

            } catch (Exception e) {
                Log.e("CameraX", "Binding failure: " + e.getMessage());
                if (voiceManager != null) {
                    voiceManager.speak("Συγγνώμη, η κάμερα δεν αποκρίνεται, δοκίμασε ξανά.", true);
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // Τροφοδοτούμε τον gesture detector από εδώ, ώστε το swipe να πιάνεται αξιόπιστα
    // πάνω από την προεπισκόπηση της κάμερας (που γεμίζει όλη την οθόνη).
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (gestureDetector != null) gestureDetector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        // Πάνω κουμπί: επιστροφή στην αρχική οθόνη (μικρόφωνο)
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP && event.getRepeatCount() == 0) {
            if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
            startActivity(new Intent(CameraActivity.this, MainActivity.class));
            finish();
            return true;
        }
        // Κάτω κουμπί: 1 πάτημα -> (είμαστε ήδη στην Κάμερα), 2 πατήματα -> Ρυθμίσεις
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN && event.getRepeatCount() == 0) {
            handleVolumeDownNavigation();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    // Καταναλώνουμε και το onKeyUp των κουμπιών έντασης ώστε να μην εμφανίζεται το slider έντασης του συστήματος.
    @Override
    public boolean onKeyUp(int keyCode, android.view.KeyEvent event) {
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP || keyCode == android.view.KeyEvent.KEYCODE_VOLUME_DOWN) {
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    // Διαχείριση μονού/διπλού πατήματος του κάτω κουμπιού
    private void handleVolumeDownNavigation() {
        long now = System.currentTimeMillis();
        if (now - lastVolumeDownTime < VOLUME_DOUBLE_PRESS_INTERVAL) {
            // Διπλό πάτημα: ακυρώνουμε το προγραμματισμένο μονό και πάμε Ρυθμίσεις
            if (volumeDownSingleTapRunnable != null) {
                volumeNavHandler.removeCallbacks(volumeDownSingleTapRunnable);
                volumeDownSingleTapRunnable = null;
            }
            lastVolumeDownTime = 0;
            if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
            startActivity(new Intent(CameraActivity.this, SettingsActivity.class));
            finish();
        } else {
            // Μονό πάτημα: είμαστε ήδη στην Κάμερα, οπότε αναμένουμε μόνο για τυχόν δεύτερο πάτημα.
            lastVolumeDownTime = now;
            volumeDownSingleTapRunnable = () -> volumeDownSingleTapRunnable = null;
            volumeNavHandler.postDelayed(volumeDownSingleTapRunnable, VOLUME_DOUBLE_PRESS_INTERVAL);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {
            if (sensorManager != null && stepDetector != null) {
                Sensor accel = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                if (accel != null) {
                    sensorManager.registerListener(stepDetector, accel, SensorManager.SENSOR_DELAY_FASTEST);
                }
            }
        } catch (Exception e) {
            Log.e("CameraActivity", "Sensor register error: " + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(stepDetector);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        volumeNavHandler.removeCallbacksAndMessages(null);
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    // --- Interface Methods (Placeholder για να μην σκάει ο κώδικας) ---
    @Override public void onStepCounted(int steps) {}
    @Override public void onTargetStepsReached() {}
    @Override public void onObstacleDetected(String label, float coverage) {}
    @Override public void onPathClear() {}
    @Override public void onTextRecognizedAndTranslated(String text) {}
}