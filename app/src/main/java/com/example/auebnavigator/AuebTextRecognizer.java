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

    private int frameCount = 0;
    private static final int FRAME_SKIP_RATE = 4;

    public AuebTextRecognizer(OcrListener listener) {
        this.listener = listener;

        this.textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.GREEK)
                .build();
        this.englishGreekTranslator = Translation.getClient(options);

        this.englishGreekTranslator.downloadModelIfNeeded()
                .addOnSuccessListener(unused -> isTranslatorReady = true)
                .addOnFailureListener(e -> Log.e("AuebOCR", "Translator download failed: " + e.getMessage()));
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        frameCount++;
        if (frameCount % FRAME_SKIP_RATE != 0) {
            imageProxy.close();
            return;
        }

        if (!isTranslatorReady || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        // 🔥 Robust Error Handling: Try-Catch σε όλο το block επεξεργασίας
        try {
            Bitmap bitmap = imageProxy.toBitmap();
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            int minDimension = Math.min(width, height);
            int cropSize = (int) (minDimension * 0.7);
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
                    .addOnFailureListener(e -> Log.w("AuebOCR", "OCR processing failed: " + e.getMessage()))
                    .addOnCompleteListener(task -> {
                        imageProxy.close();
                        croppedBitmap.recycle();
                        bitmap.recycle();
                    });
        } catch (Exception e) {
            Log.e("AuebOCR", "Critical frame analysis error: " + e.getMessage());
            imageProxy.close(); // Πάντα κλείνουμε το proxy για να μην κολλήσει η κάμερα
        }
    }

    private void translateText(String englishText) {
        try {
            englishGreekTranslator.translate(englishText)
                    .addOnSuccessListener(greekText -> {
                        Log.d("AuebOCR", "Raw: " + englishText + " -> Translated: " + greekText);
                        if (listener != null) listener.onTextRecognizedAndTranslated(greekText);
                    })
                    .addOnFailureListener(e -> Log.w("AuebOCR", "Translation failed: " + e.getMessage()));
        } catch (Exception e) {
            Log.e("AuebOCR", "Translation logic error: " + e.getMessage());
        }
    }

    public void close() {
        try {
            textRecognizer.close();
            if (englishGreekTranslator != null) englishGreekTranslator.close();
        } catch (Exception e) {
            Log.e("AuebOCR", "Error closing resources: " + e.getMessage());
        }
    }
}