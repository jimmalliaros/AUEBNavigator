package com.example.auebnavigator;

import android.Manifest;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        viewFinder = findViewById(R.id.viewFinder);
        ocrFocusOverlay = findViewById(R.id.ocrFocusOverlay);
        cameraExecutor = Executors.newSingleThreadExecutor();
        voiceManager = VoiceManager.getInstance(this, null);

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