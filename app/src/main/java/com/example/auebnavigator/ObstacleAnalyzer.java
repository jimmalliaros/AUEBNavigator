package com.example.auebnavigator;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.media.Image;
import androidx.annotation.NonNull;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.objects.ObjectDetection;
import com.google.mlkit.vision.objects.ObjectDetector;
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions;

public class ObstacleAnalyzer implements ImageAnalysis.Analyzer {

    public interface ObstacleListener {
        void onObstacleDetected(String label, float coveragePercentage);
        void onPathClear();
    }

    private final ObjectDetector objectDetector;
    private final ObstacleListener listener;

    public ObstacleAnalyzer(ObstacleListener listener) {
        this.listener = listener;
        ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE)
                .enableMultipleObjects()
                .enableClassification()
                .build();
        this.objectDetector = ObjectDetection.getClient(options);
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            int imageWidth = mediaImage.getWidth();
            int imageHeight = mediaImage.getHeight();
            float totalImageArea = imageWidth * imageHeight;

            objectDetector.process(image)
                    .addOnSuccessListener(detectedObjects -> {
                        boolean found = false;
                        for (com.google.mlkit.vision.objects.DetectedObject obj : detectedObjects) {
                            Rect boundingBox = obj.getBoundingBox();
                            float objectArea = boundingBox.width() * boundingBox.height();
                            float coveragePercentage = (objectArea / totalImageArea) * 100;

                            if (coveragePercentage > 35.0f && !obj.getLabels().isEmpty()) {
                                found = true;
                                String englishLabel = obj.getLabels().get(0).getText();
                                if (listener != null) {
                                    listener.onObstacleDetected(translateLabel(englishLabel), coveragePercentage);
                                }
                                break;
                            }
                        }
                        if (!found && listener != null) {
                            listener.onPathClear();
                        }
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
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
}