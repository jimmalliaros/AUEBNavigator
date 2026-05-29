package com.example.auebnavigator;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
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
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity implements StepDetector.StepListener, ObstacleAnalyzer.ObstacleListener {

    private PreviewView viewFinder;
    private TextView tvObstacleInfo;
    private ExecutorService cameraExecutor;

    private SensorManager sensorManager;
    private StepDetector stepDetector;
    private VoiceManager voiceManager;

    private AuebGraph auebGraph;
    private List<AuebGraph.Edge> currentPath;
    private int currentEdgeIndex = 0;
    private String destination;
    private long lastSpokenTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        viewFinder = findViewById(R.id.viewFinder);
        tvObstacleInfo = findViewById(R.id.textView);
        cameraExecutor = Executors.newSingleThreadExecutor();
        auebGraph = new AuebGraph();

        // Intent Data
        String startLocation = getIntent().getStringExtra("START_LOCATION");
        destination = getIntent().getStringExtra("DESTINATION");

        // Αρχικοποίηση Components
        stepDetector = new StepDetector(this, this);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        voiceManager = VoiceManager.getInstance(this, () -> {
            // Μόλις η φωνή είναι έτοιμη, ξεκινάει ο Α*
            currentPath = auebGraph.findPathAStar(startLocation, destination);
            if (currentPath != null && !currentPath.isEmpty()) {
                AuebGraph.Edge firstEdge = currentPath.get(0);
                stepDetector.startNavigation(firstEdge.steps);
                voiceManager.speak("Η διαδρομή υπολογίστηκε. " + firstEdge.instruction, true);
                currentEdgeIndex = 1;
            }
        });

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, 200);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, new ObstacleAnalyzer(this));

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageAnalysis);
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Error static camera binding", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // --- INTERFACE IMPLEMENTATIONS ---

    @Override
    public void onStepCounted(int currentSteps) {
        if (currentSteps % 5 == 0) {
            voiceManager.speak("Ακόμα " + currentSteps + " βήματα.", false);
        }
    }

    @Override
    public void onTargetStepsReached() {
        if (currentEdgeIndex >= currentPath.size()) {
            voiceManager.speak("Έφτασες στον προορισμό σου: " + destination, true);
            return;
        }
        AuebGraph.Edge nextEdge = currentPath.get(currentEdgeIndex);
        stepDetector.startNavigation(nextEdge.steps);
        voiceManager.speak("Στάση: " + nextEdge.instruction, true);
        currentEdgeIndex++;
    }

    @Override
    public void onObstacleDetected(String label, float coveragePercentage) {
        runOnUiThread(() -> tvObstacleInfo.setText("Κοντινό Εμπόδιο: " + label));
        long currentTime = System.currentTimeMillis();
        if (currentTime - lastSpokenTime > 4000) {
            voiceManager.speak("Προσοχή εμπόδιο " + label, false);
            lastSpokenTime = currentTime;
        }
    }

    @Override
    public void onPathClear() {
        runOnUiThread(() -> tvObstacleInfo.setText("Πορεία Καθαρή"));
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER) != null) {
            sensorManager.registerListener(stepDetector, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) sensorManager.unregisterListener(stepDetector);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
    }
}