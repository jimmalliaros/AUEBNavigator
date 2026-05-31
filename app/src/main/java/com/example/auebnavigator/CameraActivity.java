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

/**
 CameraActivity is the camera screen of the application
 */

public class CameraActivity extends AppCompatActivity implements
        StepDetector.StepListener,
        ObstacleAnalyzer.ObstacleListener,
        AuebTextRecognizer.OcrListener {

    private PreviewView viewFinder;  //shows the live camera feed
    private OcrFocusOverlayView ocrFocusOverlay;  //dark overlay with a clear focus window
    private ExecutorService cameraExecutor;  //background thread for camera analysis
    private SensorManager sensorManager; //access to device sensors
    private StepDetector stepDetector;  //step counter from the accelerometer
    private VoiceManager voiceManager;

    //Navigation using the side (volume) buttons
    private android.os.Vibrator vibrator;
    private long lastVolumeDownTime = 0;
    private final android.os.Handler volumeNavHandler = new android.os.Handler(android.os.Looper.getMainLooper());
    private Runnable volumeDownSingleTapRunnable;
    private static final int VOLUME_DOUBLE_PRESS_INTERVAL = 450; //ms tolerance between two presses

    //Swipe gesture navigation
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

        //Swipe gestures (same scheme as Main/Settings)
        // Left to right: go to Settings. Right to left: go to Main.
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
                        //Swipe left -> right: go to Settings
                        if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
                        startActivity(new Intent(CameraActivity.this, SettingsActivity.class));
                        finish();
                    } else {
                        //Swipe right -> left: go to Main
                        if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
                        startActivity(new Intent(CameraActivity.this, MainActivity.class));
                        finish();
                    }
                    return true;
                }
                return false;
            }
        });

        //Sensor setup (guarded with try-catch since not every device behaves the same)
        try {
            sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
            stepDetector = new StepDetector(this, this); //this Activity (the camera) is the StepListener
        } catch (Exception e) {
            Log.e("CameraActivity", "Sensor init failed: " + e.getMessage());
        }

        //Start the camera once we have permission,otherwise request it
        if (allPermissionsGranted()) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 10);
        }
    }

    // Initialisation of CameraX: binding the preview and an image-analysis use case to this lifecycle
    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Live preview shown in the viewFinder
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                //Image analysis use case (keep only the latest frame to stay real-time)
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                // ebind everything to the back camera for this Activity's lifecycle
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);

            } catch (Exception e) {
                Log.e("CameraX", "Binding failure: " + e.getMessage());
                if (voiceManager != null) {
                    voiceManager.speak("Συγγνώμη, η κάμερα δεν ανταποκρίνεται, δοκίμασε ξανά.", true);
                }
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // Feed the gesture detector from here so swipes are caught reliably over the full-screen camera preview
    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (gestureDetector != null) gestureDetector.onTouchEvent(event);
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, android.view.KeyEvent event) {
        //Up button pressed: return to main screen
        if (keyCode == android.view.KeyEvent.KEYCODE_VOLUME_UP && event.getRepeatCount() == 0) {
            if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
            startActivity(new Intent(CameraActivity.this, MainActivity.class));
            finish();
            return true;
        }
        //Down button pressed: 1 press-> Camera, 2 presses -> Settings
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

    //Single/double press of down button handling
    private void handleVolumeDownNavigation() {
        long now = System.currentTimeMillis();
        if (now - lastVolumeDownTime < VOLUME_DOUBLE_PRESS_INTERVAL) {
            //Double press: cancel the scheduled single-press action and go to settings screen
            if (volumeDownSingleTapRunnable != null) {
                volumeNavHandler.removeCallbacks(volumeDownSingleTapRunnable);
                volumeDownSingleTapRunnable = null;
            }
            lastVolumeDownTime = 0;
            if (vibrator != null && vibrator.hasVibrator()) vibrator.vibrate(30);
            startActivity(new Intent(CameraActivity.this, SettingsActivity.class));
            finish();
        } else {
            //Single press: we're already in Camera, so just wait in case a second press arrives
            lastVolumeDownTime = now;
            volumeDownSingleTapRunnable = () -> volumeDownSingleTapRunnable = null;
            volumeNavHandler.postDelayed(volumeDownSingleTapRunnable, VOLUME_DOUBLE_PRESS_INTERVAL);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        try {  //Start receiving accelerometer updates (for step detection) while visible
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
    protected void onPause() { //Stop accelerometer updates when the screen is not in the foreground (saves battery)
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

    //Interface methods (Placeholder for the code to be able to compile)
    @Override public void onStepCounted(int steps) {}
    @Override public void onTargetStepsReached() {}
    @Override public void onObstacleDetected(String label, float coverage) {}
    @Override public void onPathClear() {}
    @Override public void onTextRecognizedAndTranslated(String text) {}
}