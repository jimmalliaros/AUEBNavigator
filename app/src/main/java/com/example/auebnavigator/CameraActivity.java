package com.example.auebnavigator;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.Image;
import android.os.Bundle;
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

import java.util.Locale;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CameraActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private String startLocation;
    private String destination;

    private PreviewView viewFinder;
    private TextView tvObstacleInfo; // Το TextView που έφτιαξες κάτω-κάτω

    private static final int CAMERA_PERMISSION_CODE = 200;

    // Το Thread που θα τρέχει βαριές δουλειές (Image Analysis) στο παρασκήνιο
    private ExecutorService cameraExecutor;

    // Ο ανιχνευτής αντικειμένων του ML Kit
    private ObjectDetector objectDetector;

    // Μεταβλητή για να μην μας "σπαμάρει" το TTS συνέχεια για το ίδιο εμπόδιο
    private long lastSpokenTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera);

        viewFinder = findViewById(R.id.viewFinder);
        tvObstacleInfo = findViewById(R.id.textView);

        // 1. Ρυθμίζουμε τον Ανιχνευτή (ML Kit Options)
        // Το SINGLE_IMAGE_MODE είναι πιο ελαφρύ για το κινητό, το STREAM_MODE είναι για 100% real-time tracking.
        ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification() // Μας επιστρέφει "τι" είναι (π.χ. Furniture, Fashion good)
                .build();

        objectDetector = ObjectDetection.getClient(options);

        cameraExecutor = Executors.newSingleThreadExecutor();

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

                // Η προβολή της κάμερας στην οθόνη
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                // 🔥 ΕΔΩ ΜΠΑΙΝΕΙ ΤΟ MACHINE LEARNING (Image Analysis)
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // Αν αργήσει το ML, πέτα τα παλιά καρέ
                        .build();

                imageAnalysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;
                cameraProvider.unbindAll();

                // Συνδέουμε Preview ΚΑΙ Analysis
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageAnalysis);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Η αρχικοποίηση της κάμερας απέτυχε.", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    // Η μέθοδος που τρέχει δεκάδες φορές το δευτερόλεπτο και ταΐζει το Νευρωνικό Δίκτυο
    @SuppressLint("UnsafeOptInUsageError")
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            // Παίρνουμε τις διαστάσεις της ολόκληρης εικόνας (Width & Height)
            int imageWidth = mediaImage.getWidth();
            int imageHeight = mediaImage.getHeight();
            // Υπολογίζουμε το συνολικό Εμβαδόν (Area) του καρέ σε pixels
            float totalImageArea = imageWidth * imageHeight;

            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            objectDetector.process(image)
                    .addOnSuccessListener(detectedObjects -> {
                        boolean isCloseObstacleFound = false;
                        String labelToSpeak = "";

                        for (com.google.mlkit.vision.objects.DetectedObject obj : detectedObjects) {

                            // 1. Παίρνουμε το Bounding Box (το "κουτί" που περικλείει το αντικείμενο)
                            android.graphics.Rect boundingBox = obj.getBoundingBox();

                            // 2. Υπολογίζουμε το Εμβαδόν (Area) του Αντικειμένου
                            float objectArea = boundingBox.width() * boundingBox.height();

                            // 3. Βρίσκουμε το ποσοστό κάλυψης (Πόσο % της οθόνης πιάνει το αντικείμενο;)
                            float coveragePercentage = (objectArea / totalImageArea) * 100;

                            // 4. Η "ΕΞΥΠΝΗ" ΛΟΓΙΚΗ: Ειδοποιούμε ΜΟΝΟ αν το αντικείμενο είναι "μεγάλο" (ΚΟΝΤΑ μας)
                            // π.χ. Αν πιάνει πάνω από το 35% της οθόνης (Μπορείς να παίξεις με αυτό το νούμερο)
                            if (coveragePercentage > 35.0f && !obj.getLabels().isEmpty()) {
                                isCloseObstacleFound = true;
                                String englishLabel = obj.getLabels().get(0).getText();
                                labelToSpeak = translateLabel(englishLabel);

                                // (Προαιρετικό) Για Debug: Να βλέπεις στην οθόνη το ποσοστό
                                String finalLabel = labelToSpeak;
                                runOnUiThread(() -> tvObstacleInfo.setText("Κοντινό Εμπόδιο: " + finalLabel + " (" + (int)coveragePercentage + "%)"));

                                break; // Βρήκαμε ένα κοντινό εμπόδιο, σταματάμε να ψάχνουμε τα άλλα στο ίδιο καρέ
                            }
                        }

                        // Αν βρέθηκε ΚΟΝΤΙΝΟ εμπόδιο και πέρασε το cooldown time (π.χ. 4 δευτερόλεπτα τώρα)
                        if (isCloseObstacleFound) {
                            long currentTime = System.currentTimeMillis();
                            if (currentTime - lastSpokenTime > 4000) { // Αύξησα το cooldown στα 4 δευτερόλεπτα
                                speakText("Προσοχή. Εμπόδιο στα δύο μέτρα. " + labelToSpeak);
                                lastSpokenTime = currentTime;
                            }
                        } else {
                            // Αν δεν υπάρχει κοντινό εμπόδιο, καθαρίζουμε το UI (δεν μιλάει)
                            runOnUiThread(() -> tvObstacleInfo.setText("Πορεία Καθαρή"));
                        }
                    })
                    .addOnFailureListener(e -> Log.e("MLKit", "Αποτυχία", e))
                    .addOnCompleteListener(task -> {
                        imageProxy.close();
                    });
        }
    }

    // Helper μέθοδος: Το ML Kit της Google επιστρέφει Αγγλικές κατηγορίες
    // (π.χ. Furniture, Plant, Place). Τα κάνουμε "Ελληνοποίηση".
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

    // --- ΚΩΔΙΚΑΣ PERMISSIONS & TTS ---

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    private void setupTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(new Locale("el", "GR"));
                startNavigationSequence();
            }
        });
    }

    private void startNavigationSequence() {
        if (startLocation.equals("Κεντρική Είσοδος") && destination.equals("Αμφιθέατρο Α")) {
            speakText("Προχώρα ευθεία. Ανίχνευση εμποδίων ενεργή.");
        } else {
            speakText("Ανίχνευση εμποδίων ενεργή. Προορισμός: " + destination);
        }
    }

    private void speakText(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown(); // Κλείνουμε το background thread
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}