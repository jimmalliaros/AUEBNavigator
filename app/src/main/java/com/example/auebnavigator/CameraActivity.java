package com.example.auebnavigator;
import android.os.Bundle;

import android.content.Intent;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import java.util.Locale;


public class CameraActivity extends AppCompatActivity {

    private TextToSpeech tts;
    private String startLocation;
    private String destination;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.camera);
        // 1. Διαβάζουμε τα δεδομένα που μας έστειλε η MainActivity
        Intent intent = getIntent();
        startLocation = intent.getStringExtra("START_LOCATION");
        destination = intent.getStringExtra("DESTINATION");

        // Αν ήρθε εδώ "κατά λάθος" (χωρίς προορισμό), του το λέμε
        if (startLocation == null || destination == null) {
            startLocation = "Άγνωστο";
            destination = "Λειτουργία Ελεύθερης Περιήγησης";
        }

        Log.d("NavigationMode", "Ξεκίνησε πλοήγηση: Από " + startLocation + " -> Προς " + destination);

        // 2. Αρχικοποιούμε το Text-To-Speech για την CameraActivity
        setupTTS();

        // (Εδώ λογικά έχεις ήδη κώδικα που ανοίγει τον φακό της κάμερας)
    }

    private void setupTTS() {
        tts = new TextToSpeech(this, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(new Locale("el", "GR"));
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    Log.e("TTS", "Ελληνικά δεν υποστηρίζονται.");
                } else {
                    // Μόλις φορτώσει η μηχανή ομιλίας, του δίνουμε την πρώτη εντολή!
                    startNavigationSequence();
                }
            }
        });
    }

    // 3. Η λογική των πρώτων οδηγιών (Εδώ θα μπει μετά ο Γράφος)
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

    // Η Helper μέθοδος που μιλάει
    private void speakText(String text) {
        if (tts != null) {
            tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Πάντα καθαρίζουμε τη μνήμη όταν κλείνει η οθόνη
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
    }
}
