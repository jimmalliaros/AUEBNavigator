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

    // ΕΛΑΦΡΥΝΣΗ: Μετρητής για να αναλύουμε λιγότερα frames
    private int frameCount = 0;
    private static final int FRAME_SKIP_RATE = 4;

    public AuebTextRecognizer(OcrListener listener) {
        this.listener = listener;

        // 1. OCR (Latin)
        this.textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        // 2. Μεταφραστής (Αγγλικά -> Ελληνικά)
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.GREEK)
                .build();
        this.englishGreekTranslator = Translation.getClient(options);

        this.englishGreekTranslator.downloadModelIfNeeded()
                .addOnSuccessListener(unused -> isTranslatorReady = true)
                .addOnFailureListener(e -> Log.e("AuebOCR", "Αποτυχία Translator", e));
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        // 🔥 FRAME THROTTLING: Ανάλυση 1 στα κάθε 4 frames για εξοικονόμηση CPU
        frameCount++;
        if (frameCount % FRAME_SKIP_RATE != 0) {
            imageProxy.close();
            return;
        }

        if (!isTranslatorReady || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        Bitmap bitmap = imageProxy.toBitmap();
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();

        // Κόβουμε το 70% του πλάτους στο ΚΕΝΤΡΟ (ROI)
        int minDimension = Math.min(width, height);
        int cropSize = (int) (minDimension * 0.7);
        int cropX = (width - cropSize) / 2;
        int cropY = (height - cropSize) / 2;

        try {
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
                        bitmap.recycle();
                    });
        } catch (Exception e) {
            Log.e("AuebOCR", "Error: " + e.getMessage());
            imageProxy.close();
            bitmap.recycle();
        }
    }

    private void translateText(String englishText) {
        englishGreekTranslator.translate(englishText)
                .addOnSuccessListener(greekText -> {
                    // Log για να βλέπεις τι γίνεται πίσω από την κάμερα!
                    Log.d("AuebOCR", "Raw: " + englishText + " -> Translated: " + greekText);
                    if (listener != null) listener.onTextRecognizedAndTranslated(greekText);
                });
    }

    public void close() {
        textRecognizer.close();
        if (englishGreekTranslator != null) englishGreekTranslator.close();
    }
}