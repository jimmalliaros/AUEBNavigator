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

/**
   AuebTextRecognizer is responsible for recognizing text from the live camera (OCR) and translating it from English to Greek
 **/

public class AuebTextRecognizer implements ImageAnalysis.Analyzer {

    public interface OcrListener { //Callback for delivering the recognised and translated Greek text
        void onTextRecognizedAndTranslated(String greekText);
    }

    private final TextRecognizer textRecognizer; //ML Kit Latin-script text recognizer
    private final Translator englishGreekTranslator;  //ML Kit English to  Greek translator
    private final OcrListener listener; //listener that receives the final text in Greek
    private boolean isTranslatorReady = false;

    private int frameCount = 0; // Frame throttling: process only 1 out of every FRAME_SKIP_RATE frames (saves CPU/battery)
    private static final int FRAME_SKIP_RATE = 4;

    public AuebTextRecognizer(OcrListener listener) {
        this.listener = listener;

        // On-device text recogniser with default (Latin script) options
        this.textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        //Establish an English to Greek translator.
        TranslatorOptions options = new TranslatorOptions.Builder()
                .setSourceLanguage(TranslateLanguage.ENGLISH)
                .setTargetLanguage(TranslateLanguage.GREEK)
                .build();
        this.englishGreekTranslator = Translation.getClient(options);


        //The translation model must be downloaded once before use. Flag readiness when it finishes and until then, analyze() skips translation to avoid errors
        this.englishGreekTranslator.downloadModelIfNeeded()
                .addOnSuccessListener(unused -> isTranslatorReady = true)
                .addOnFailureListener(e -> Log.e("AuebOCR", "Translator download failed: " + e.getMessage()));
    }

    //getImage()/toBitmap() are opt-in CameraX APIs-this suppresses the lint warning
    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        //Frame throttling: drop the in-between frames (still must close the proxy)
        frameCount++;
        if (frameCount % FRAME_SKIP_RATE != 0) {
            imageProxy.close();
            return;
        }


        //Skip until the translation model is ready or if there's no image to read
        if (!isTranslatorReady || imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        //Wrap the whole processing block in try-catch so one bad frame can't crash the app
        try {
            Bitmap bitmap = imageProxy.toBitmap();
            int width = bitmap.getWidth();
            int height = bitmap.getHeight();

            //Crop a centred square (70% of the smaller side) so OCR focuses on the middle of the frame (matching the on-screen focus window)
            int minDimension = Math.min(width, height);
            int cropSize = (int) (minDimension * 0.7);
            int cropX = (width - cropSize) / 2;
            int cropY = (height - cropSize) / 2;

            Bitmap croppedBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropSize, cropSize);
            InputImage image = InputImage.fromBitmap(croppedBitmap, 0);

            //Recognise text (asynchronous)
            textRecognizer.process(image)
                    .addOnSuccessListener(visionText -> {
                        String englishText = visionText.getText().trim();
                        //Ignore noise: only translate when there's something meaningful
                        if (englishText.length() > 2) {
                            translateText(englishText);
                        }
                    })
                    .addOnFailureListener(e -> Log.w("AuebOCR", "OCR processing failed: " + e.getMessage()))
                    .addOnCompleteListener(task -> {
                        //Free the frame and the bitmaps so the camera keeps flowing
                        imageProxy.close();
                        croppedBitmap.recycle();
                        bitmap.recycle();
                    });
        } catch (Exception e) {
            Log.e("AuebOCR", "Critical frame analysis error: " + e.getMessage());
            imageProxy.close();  //always close the proxy so the camera doesn't stall
        }
    }

    private void translateText(String englishText) {  //Translation of recognised English text to Greek and notification of listener
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

    public void close() {  //Releases the ML Kit resources. Call when the analyzer is no longer needed
        try {
            textRecognizer.close();
            if (englishGreekTranslator != null) englishGreekTranslator.close();
        } catch (Exception e) {
            Log.e("AuebOCR", "Error closing resources: " + e.getMessage());
        }
    }
}