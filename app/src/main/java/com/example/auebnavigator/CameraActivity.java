package com.example.auebnavigator;

import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;
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
import android.os.Handler;
import android.os.Looper;
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

public class CameraActivity extends AppCompatActivity implements SensorEventListener {

    private Vibrator vibrator;
    private TextToSpeech tts;
    private boolean isTtsReady = false; //  ΦΛΑΓΚ ΑΣΦΑΛΕΙΑΣ: Για να ξέρουμε πότε είναι 100% έτοιμο το TTS

    private String startLocation;
    private String destination;

    private PreviewView viewFinder;
    private TextView tvObstacleInfo;

    // --- Μεταβλητές Custom Βηματομετρητή (Accelerometer) ---
    private SensorManager sensorManager;
    private Sensor accelerometerSensor;
    private int currentSteps = 0;
    private int targetSteps = 15;
    private boolean isNavigating = false;
    private long lastStepTime = 0;

    private static final int CAMERA_PERMISSION_CODE = 200;
    private ExecutorService cameraExecutor;
    private ObjectDetector objectDetector;
    private long lastSpokenTime = 0;

    private AuebGraph auebGraph;
    private List<AuebGraph.Edge> currentPath;
    private int currentEdgeIndex = 0;

    private TextRecognizer textRecognizer;
    private boolean isOcrMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_camera);

        // 1. ΑΡΧΙΚΟΠΟΙΗΣΗ GUI
        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        viewFinder = findViewById(R.id.viewFinder);
        tvObstacleInfo = findViewById(R.id.textView);

        // 2. ΑΡΧΙΚΟΠΟΙΗΣΗ ΕΡΓΑΛΕΙΩΝ ΚΑΙ ΓΡΑΦΟΥ
        cameraExecutor = Executors.newSingleThreadExecutor();
        auebGraph = new AuebGraph();

        ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build();
        objectDetector = ObjectDetection.getClient(options);
        textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        // 3. ΔΙΑΒΑΣΜΑ INTENT
        isOcrMode = getIntent().getBooleanExtra("ENABLE_OCR", false);
        Intent intent = getIntent();
        startLocation = intent.getStringExtra("START_LOCATION");
        destination = intent.getStringExtra("DESTINATION");

        if (startLocation == null) startLocation = "Άγνωστο";
        if (destination == null) destination = "Ελεύθερη Περιήγηση";

        // Αρχικοποίηση Αισθητήρα
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometerSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        // 4. ΕΛΕΓΧΟΣ ΑΔΕΙΩΝ & ΕΚΚΙΝΗΣΗ
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
        // 🔥 Αν το TTS δεν είναι ακόμα 100% έτοιμο, προσπερνάμε την ανάλυση των frames για να μην κρασάρει!
        if (!isTtsReady) {
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            if (isOcrMode) {
                textRecognizer.process(image)
                        .addOnSuccessListener(visionText -> {
                            String recognizedText = visionText.getText().trim();
                            if (!recognizedText.isEmpty()) {
                                long currentTime = System.currentTimeMillis();
                                if (currentTime - lastSpokenTime > 4000) {
                                    speakText(recognizedText, false);
                                    lastSpokenTime = currentTime;
                                }
                            }
                        })
                        .addOnFailureListener(e -> Log.e("MLKit", "Αποτυχία OCR", e))
                        .addOnCompleteListener(task -> imageProxy.close());
            } else {
                int imageWidth = mediaImage.getWidth();
                int imageHeight = mediaImage.getHeight();
                float totalImageArea = imageWidth * imageHeight;

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
                                    speakText("Προσοχή. Εμπόδιο. " + labelToSpeak, false);
                                    lastSpokenTime = currentTime;
                                }
                            } else {
                                runOnUiThread(() -> tvObstacleInfo.setText("Πορεία Καθαρή"));
                            }
                        })
                        .addOnFailureListener(e -> Log.e("MLKit", "Αποτυχία Εμποδίων", e))
                        .addOnCompleteListener(task -> imageProxy.close());
            }
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
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE && allPermissionsGranted()) {
            startCamera();
            setupTTS();
            if (sensorManager != null && accelerometerSensor != null) {
                sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_FASTEST);
            }
        }
    }

    private void setupTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("el", "GR"));
                if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                    float speed = getSharedPreferences("AuebNavPrefs", MODE_PRIVATE).getFloat("tts_speed", 1.0f);
                    tts.setSpeechRate(speed);

                    isTtsReady = true; // ✅ Το TTS είναι πλέον 100% ασφαλές για χρήση!

                    // 🔥 Δίνουμε ένα μικρό εικονικό delay 300ms για να προλάβει το Android OS να κάνει bind το engine
                    new Handler(Looper.getMainLooper()).postDelayed(this::startNavigationSequence, 300);
                }
            }
        });
    }

    private void startNavigationSequence() {
        // Αν ο χρήστης μπήκε απλά για ελεύθερη περιήγηση ή OCR, μην τρέχεις τον Α*!
        if (destination.equals("Ελεύθερη Περιήγηση") || isOcrMode) {
            if (isOcrMode) {
                speakText("Λειτουργία σάρωσης κειμένου ενεργή.", true);
            } else {
                speakText("Ελεύθερη περιήγηση ενεργή. Ανίχνευση εμποδίων στο προσκήνιο.", true);
            }
            isNavigating = false;
            return;
        }

        currentPath = auebGraph.findPathAStar(startLocation, destination);

        if (currentPath == null || currentPath.isEmpty()) {
            speakText("Συγγνώμη, δεν βρέθηκε διαδρομή από το σημείο " + startLocation + " προς το σημείο " + destination, true);
            isNavigating = false;
            return;
        }

        isNavigating = true;
        currentEdgeIndex = 0;

        AuebGraph.Edge firstEdge = currentPath.get(currentEdgeIndex);
        targetSteps = firstEdge.steps;
        currentSteps = 0;

        speakText("Η διαδρομή υπολογίστηκε. Ξεκινάμε. " + firstEdge.instruction, true);
        currentEdgeIndex++;
    }

    private void startNextLeg() {
        if (currentEdgeIndex >= currentPath.size()) {
            isNavigating = false;
            if (vibrator != null && vibrator.hasVibrator()) {
                vibrator.vibrate(1000);
            }
            speakText("Έφτασες στον τελικό προορισμό σου: " + destination, true);
            return;
        }

        String justReachedNode = currentPath.get(currentEdgeIndex - 1).targetNode;
        AuebGraph.Edge nextEdge = currentPath.get(currentEdgeIndex);
        targetSteps = nextEdge.steps;
        currentSteps = 0;

        speakText("Βρίσκεσαι στο σημείο " + justReachedNode + ". " + nextEdge.instruction, true);
        currentEdgeIndex++;
    }

    private void speakText(String text, boolean isPriority) {
        // 🔥 Απόλυτο null-check και ready-check για προστασία από crashes
        if (tts != null && isTtsReady) {
            try {
                if (isPriority) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
                } else {
                    if (!tts.isSpeaking()) {
                        tts.speak(text, TextToSpeech.QUEUE_ADD, null, null);
                    }
                }
            } catch (Exception e) {
                Log.e("TTS_Error", "Αποτυχία εκφώνησης: " + e.getMessage());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (sensorManager != null && accelerometerSensor != null) {
            sensorManager.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_FASTEST);
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
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && isNavigating) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            double magnitude = Math.sqrt(x * x + y * y + z * z);
            long currentTime = System.currentTimeMillis();

            if (magnitude > 12.2 && (currentTime - lastStepTime > 450)) {
                lastStepTime = currentTime;
                currentSteps++;
                Log.d("Pedometer", "Βήμα (Acc): " + currentSteps + " / " + targetSteps);

                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(50);
                }

                if (currentSteps == targetSteps) {
                    startNextLeg();
                } else if (currentSteps % 5 == 0) {
                    speakText("Ακόμα " + (targetSteps - currentSteps) + " βήματα.", false);
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

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