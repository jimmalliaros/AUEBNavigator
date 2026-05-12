package com.example.auebnavigator;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

public class CameraActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private String startLocation;
    private String destination;
    private PreviewView viewFinder;

    private static final int CAMERA_PERMISSION_CODE = 200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera);

        viewFinder = findViewById(R.id.viewFinder);

        // 1. Διαβάζουμε τα δεδομένα πλοήγησης (από το MainActivity)
        Intent intent = getIntent();
        startLocation = intent.getStringExtra("START_LOCATION");
        destination = intent.getStringExtra("DESTINATION");

        if (startLocation == null || destination == null) {
            startLocation = "Άγνωστο";
            destination = "Λειτουργία Ελεύθερης Περιήγησης";
        }
        Log.d("NavigationMode", "Ξεκίνησε πλοήγηση: Από " + startLocation + " -> Προς " + destination);

        // 2. Ελέγχουμε την άδεια για την κάμερα.
        // Αν την έχουμε, ανοίγουμε κάμερα ΚΑΙ ξεκινάμε το TTS
        if (allPermissionsGranted()) {
            startCamera();
            setupTTS();
        } else {
            // Ζητάμε την άδεια από τον χρήστη
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_CODE);
        }
    }

    // --- ΚΩΔΙΚΑΣ CAMERAX ---

    private void startCamera() {
        // Ο Provider διαχειρίζεται τη σύνδεση της κάμερας με το lifecycle της οθόνης
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                // Συνδέουμε το Lifecycle
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Φτιάχνουμε το "Παράθυρο" (Preview) που θα δείχνει την εικόνα
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(viewFinder.getSurfaceProvider());

                // Επιλέγουμε την Πίσω Κάμερα (Back Camera)
                CameraSelector cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA;

                // Αποσυνδέουμε προηγούμενα use cases (αν υπήρχαν) πριν συνδέσουμε τα νέα
                cameraProvider.unbindAll();

                // Συνδέουμε την κάμερα με την οθόνη!
                cameraProvider.bindToLifecycle(this, cameraSelector, preview);

            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraX", "Η αρχικοποίηση της κάμερας απέτυχε.", e);
            }
        }, ContextCompat.getMainExecutor(this)); // Τρέχει στο Main Thread
    }

    // --- ΚΩΔΙΚΑΣ PERMISSIONS ---

    private boolean allPermissionsGranted() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_CODE) {
            if (allPermissionsGranted()) {
                startCamera();
                setupTTS();
            } else {
                Toast.makeText(this, "Η άδεια της κάμερας είναι απαραίτητη για την αναγνώριση εμποδίων.", Toast.LENGTH_SHORT).show();
                finish(); // Αν δεν δώσει άδεια, κλείνουμε την οθόνη (αφού μιλάμε για accessibility app, πρέπει να τον ειδοποιήσεις ίσως και φωνητικά)
            }
        }
    }

    // --- ΚΩΔΙΚΑΣ TEXT-TO-SPEECH (TTS) & NAVIGATION LOGIC ---

    private void setupTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("el", "GR"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Ελληνικά δεν υποστηρίζονται.");
                } else {
                    startNavigationSequence();
                }
            }
        });
    }

    private void startNavigationSequence() {
        if (startLocation.equals("Κεντρική Είσοδος") && destination.equals("Αμφιθέατρο Α")) {
            speakText("Η κάμερα ενεργοποιήθηκε για εντοπισμό εμποδίων. Προχώρα ευθεία για 15 βήματα.");
        }
        else if (startLocation.equals("Κεντρική Είσοδος") && destination.equals("Γραμματεία")) {
            speakText("Η κάμερα ενεργοποιήθηκε. Προχώρα αριστερά προς το ασανσέρ.");
        }
        else {
            speakText("Η κάμερα ενεργοποιήθηκε. " + destination);
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
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}