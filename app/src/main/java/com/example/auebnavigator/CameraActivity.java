package com.example.auebnavigator;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.Image;
import android.os.Bundle;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 🔥 FIX 1: Προσθέσαμε το implements SensorEventListener εδώ!
public class CameraActivity extends AppCompatActivity implements SensorEventListener {

    private Vibrator vibrator;
    private TextToSpeech tts;
    private String startLocation;
    private String destination;

    private PreviewView viewFinder;
    private TextView tvObstacleInfo;

    // --- Μεταβλητές Βηματομετρητή ---
    private SensorManager sensorManager;
    private Sensor stepDetectorSensor;
    private int currentSteps = 0;
    private int targetSteps = 15;
    private boolean isNavigating = false;

    private static final int CAMERA_PERMISSION_CODE = 200;
    private ExecutorService cameraExecutor;
    private ObjectDetector objectDetector;
    private long lastSpokenTime = 0;
    // --- Μεταβλητές Γράφου & Διαδρομής ---
    private AuebGraph auebGraph;

    private List<AuebGraph.Edge> currentPath; // Η λίστα με τις οδηγίες του Α*
    private int currentEdgeIndex = 0; // Σε ποιο "κομμάτι" της διαδρομής βρισκόμαστε


    //----------------------------------------

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // Αρχικοποίηση Sensor Manager
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            stepDetectorSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR);
            if (stepDetectorSensor == null) {
                Toast.makeText(this, "Το κινητό δεν έχει αισθητήρα βημάτων!", Toast.LENGTH_LONG).show();
            }
        }
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        viewFinder = findViewById(R.id.viewFinder);
        tvObstacleInfo = findViewById(R.id.textView);

        // Ο ΣΩΣΤΟΣ ΕΛΕΓΧΟΣ ΣΤΟ ΤΕΛΟΣ ΤΗΣ onCreate:
        if (allPermissionsGranted()) {
            startCamera();
            setupTTS();
        } else {
            // Αν λείπει έστω και μία άδεια, ζητάμε και τις δύο ταυτόχρονα!
            ActivityCompat.requestPermissions(this, new String[]{
                    Manifest.permission.CAMERA,
                    Manifest.permission.ACTIVITY_RECOGNITION
            }, CAMERA_PERMISSION_CODE);
        }

        ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build();

        objectDetector = ObjectDetection.getClient(options);
        cameraExecutor = Executors.newSingleThreadExecutor();
        // Αρχικοποιούμε τον Γράφο της ΑΣΟΕΕ
        auebGraph = new AuebGraph();

        Intent intent = getIntent();
        startLocation = intent.getStringExtra("START_LOCATION");
        destination = intent.getStringExtra("DESTINATION");

        if (startLocation == null) startLocation = "Άγνωστο";
        if (destination == null) destination = "Ελεύθερη Περιήγηση";

        if (allPermissionsGranted()) {
            startCamera();
            setupTTS();
        } else {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
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

                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();

                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Η αρχικοποίηση της κάμερας απέτυχε.", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @SuppressLint("UnsafeOptInUsageError")
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            int imageWidth = mediaImage.getWidth();
            int imageHeight = mediaImage.getHeight();
            float totalImageArea = imageWidth * imageHeight;

            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            objectDetector.process(image)
                    .addOnSuccessListener(detectedObjects -> {
                        boolean isCloseObstacleFound = false;
                        String labelToSpeak = "";

                        for (com.google.mlkit.vision.objects.DetectedObject obj : detectedObjects) {

                            android.graphics.Rect boundingBox = obj.getBoundingBox();
                            float objectArea = boundingBox.width() * boundingBox.height();
                            float coveragePercentage = (objectArea / totalImageArea) * 100;

                            if (coveragePercentage > 35.0f && !obj.getLabels().isEmpty()) {
                                isCloseObstacleFound = true;
                                String englishLabel = obj.getLabels().get(0).getText();
                                labelToSpeak = translateLabel(englishLabel);

                                String finalLabel = labelToSpeak;
                                runOnUiThread(() -> tvObstacleInfo.setText("Κοντινό Εμπόδιο: " + finalLabel + " (" + (int)coveragePercentage + "%)"));

                                break;
                            }
                        }

                        if (isCloseObstacleFound) {
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastSpokenTime > 4000) {
                                speakText("Προσοχή. Εμπόδιο στα δύο μέτρα. " + labelToSpeak);
                                lastSpokenTime = currentTime;
                            }
                        } else {
                            runOnUiThread(() -> tvObstacleInfo.setText("Πορεία Καθαρή"));
                        }
                    })
                    .addOnFailureListener(e -> Log.e("MLKit", "Αποτυχία", e))
                    .addOnCompleteListener(task -> {
                        imageProxy.close();
                    });
        }
    }

    private String translateLabel(String englishLabel) {
        switch (englishLabel) {
            case "Furniture": return "Έπιπλο";
            case "Plant": return "Φυτό";
            case "Place": return "Τοίχος ή Πόρτα";
            case "Fashion good": return "Άνθρωπος ή ρούχο";
            case "Food": return "Φαγητό";
            default: return "Άγνωστο αντικείμενο";
        }
    }

    private boolean allPermissionsGranted() {
        boolean cameraGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
        boolean activityGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION) == PackageManager.PERMISSION_GRANTED;
        return cameraGranted && activityGranted;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (allPermissionsGranted()) {
                startCamera();
                setupTTS();

                // Το πιο σημαντικό: Ενεργοποιούμε τον σένσορα ΑΦΟΥ μας δώσει την άδεια
                if (sensorManager != null && stepDetectorSensor != null) {
                    sensorManager.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_FASTEST);
                }
            } else {
                Toast.makeText(this, "Οι άδειες Κάμερας και Δραστηριότητας είναι απαραίτητες!", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void setupTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("el", "GR"));

                //  ΕΔΩ ΕΙΝΑΙ Η ΕΝΣΩΜΑΤΩΣΗ ΤΗΣ ΤΑΧΥΤΗΤΑΣ:
                float speed = getSharedPreferences("AuebNavPrefs", MODE_PRIVATE).getFloat("tts_speed", 1.0f);
                tts.setSpeechRate(speed);

                startNavigationSequence();
            }
        });
    }

    private void startNavigationSequence() {
        // Ζητάμε από τον Γράφο να βρει τη διαδρομή με τον Α*
        currentPath = auebGraph.findPathAStar(startLocation, destination);

        if (currentPath == null || currentPath.isEmpty()) {
            speakText("Συγγνώμη, δεν βρέθηκε διαδρομή από το " + startLocation + " προς το " + destination);
            return;
        }

        speakText("Η διαδρομή υπολογίστηκε. Ανίχνευση εμποδίων ενεργή. Ξεκινάμε.");
        isNavigating = true;
        currentEdgeIndex = 0;

        // Καλούμε την πρώτη οδηγία!
        startNextLeg();
    }

    // 🔥 ΝΕΑ HELPER ΜΕΘΟΔΟΣ: Τραβάει την επόμενη οδηγία από τη Λίστα
    private void startNextLeg() {
        // Αν φτάσαμε στο τέλος της λίστας των οδηγιών...
        if (currentEdgeIndex >= currentPath.size()) {
            isNavigating = false;

            // 🔥 Δόνηση για 1 δευτερόλεπτο για να καταλάβει ότι ΕΦΤΑΣΕ
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(1000);
            }

            speakText("Έφτασες στον τελικό προορισμό σου: " + destination);
            return;
        }

        // Διαβάζουμε την τωρινή Ακμή
        AuebGraph.Edge nextEdge = currentPath.get(currentEdgeIndex);

        targetSteps = nextEdge.steps; // Βάζουμε νέο στόχο βημάτων!
        currentSteps = 0; // Μηδενίζουμε τα παλιά βήματα

        speakText(nextEdge.instruction); // Του λέμε τι να κάνει (π.χ. "Στρίψε αριστερά")

        currentEdgeIndex++; // Πάμε στο επόμενο "κομμάτι" για την επόμενη φορά
    }

    // 🔥 FIX 2: Έκλεισα τη μέθοδο speakText κανονικά!
    private void speakText(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    // --- ΚΩΔΙΚΑΣ ΒΗΜΑΤΟΜΕΤΡΗΤΗ ---

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && stepDetectorSensor != null) {
            sensorManager.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_FASTEST);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_STEP_DETECTOR && isNavigating) {
            currentSteps++;
            Log.d("Pedometer", "Βήμα: " + currentSteps + " / " + targetSteps);

            // Αν ολοκληρώσαμε αυτό το "κομμάτι" της διαδρομής (ένα Edge)
            if (currentSteps == targetSteps) {
                // Φωνάζουμε την επόμενη οδηγία! (Αν δεν έχει άλλη, θα πει "έφτασες")
                startNextLeg();
            }
            else if (currentSteps % 5 == 0) {
                // Κάθε 5 βήματα υπενθύμιση
                speakText("Ακόμα " + (targetSteps - currentSteps) + " βήματα.");
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}