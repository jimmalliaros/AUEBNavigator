package com.example.auebnavigator;

import android.annotation.SuppressLint;
import android.graphics.Bitmap;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import com.google.mlkit.nl.translate.TranslateLanguage;
import com.google.mlkit.nl.translate.Translation;
import com.google.mlkit.nl.translate.Translator;
import com.google.mlkit.nl.translate.TranslatorOptions;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

public class AuebTextRecognizer implements ImageAnalysis.Analyzer {

    public interface OcrListener {
        void onTextRecognizedAndTranslated(String greekText);
    }

    private final TextRecognizer textRecognizer;
    private final Translator englishGreekTranslator;
    private final OcrListener listener;
    private boolean isTranslatorReady = false;

    public AuebTextRecognizer(OcrListener listener) {
        this.listener = listener;

        // 1. Το γρήγορο Offline Λατινικό OCR (Επειδή το Ελληνικό δεν υποστηρίζεται offline)
        this.textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        // 2. Το Offline Μοντέλο Μετάφρασης
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.GREEK)
                .build();
        this.englishGreekTranslator = Translation.getClient(options);

        // Κατεβάζει τα Ελληνικά (~30MB) την πρώτη φορά
        this.englishGreekTranslator.downloadModelIfNeeded()
                .addOnSuccessListener(unused -> isTranslatorReady = true);
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        if (!isTranslatorReady) {
            imageProxy.close();
            return;
        }

        Bitmap bitmap = imageProxy.toBitmap();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Κόβουμε το 70% του πλάτους στο ΚΕΝΤΡΟ της εικόνας (όπως και το UI Overlay)
        int cropSize = (int) (width * 0.7);
        int cropX = (width - cropSize) / 2;
        int cropY = (height - cropSize) / 2;

        Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropSize, cropSize);
        InputImage image = InputImage.fromBitmap(croppedBitmap, 0);

        textRecognizer.process(image)
                .addOnSuccessListener(visionText -> {
                    String englishText = visionText.getText().trim();
                    if (englishText.length() > 2) {
                        translateText(englishText);
                    }
                })
                .addOnCompleteListener(task -> {
                    imageProxy.close();
                    croppedBitmap.recycle();
                });
    }

    private void translateText(String englishText) {
        englishGreekTranslator.translate(englishText)
                .addOnSuccessListener(greekText -> {

                    // 🎯 ΕΔΩ ΕΙΝΑΙ ΤΟ SOS LOG: Τυπώνει τι είδε στα αγγλικά και τι έβγαλε στα ελληνικά!
                    Log.d("AuebOCR", "Raw: " + englishText + " -> Translated: " + greekText);

                    if (listener != null) listener.onTextRecognizedAndTranslated(greekText);
                });
    }

    public void close() {
        textRecognizer.close();
        if (englishGreekTranslator != null) englishGreekTranslator.close();
    }
}