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

/**
 ObstacleAnalyzer class for detecting obstacles in the image
 */
public class ObstacleAnalyzer implements ImageAnalysis.Analyzer {

    public interface ObstacleListener {
        void onObstacleDetected(String label, float coveragePercentage);  //Called when an object covers a large part of the frame (a likely obstacle)
        void onPathClear();  //Called when no significant object is detected in the frame
    }

    private final ObjectDetector objectDetector; //ML Kit on-device object detector
    private final ObstacleListener listener;  //listener for receiving the detection results

    // Frame throttling: analyze only 1 out of every FRAME_SKIP_RATE frames
    // The camera produces many frames per second; analysing each one would beheavy on CPU and battery, so we deliberately drop the in-between frames
    private int frameCount = 0;
    private static final int FRAME_SKIP_RATE = 5;

    public ObstacleAnalyzer(ObstacleListener listener) {
        this.listener = listener;
        ObjectDetectorOptions options = new ObjectDetectorOptions.Builder()
                .setDetectorMode(ObjectDetectorOptions.STREAM_MODE) //STREAM_MODE: optimised for low-latency detection on a continuous camera feed
                .enableMultipleObjects() // ML kit method for tracking more than one object per frame
                .enableClassification() //ML kit method for identifying and classifying objects in the image
                .build();
        this.objectDetector = ObjectDetection.getClient(options);
    }

    @SuppressLint("UnsafeOptInUsageError")
    @Override
    public void analyze(@NonNull ImageProxy imageProxy) {
        //Frame throttling
        frameCount++;
        if (frameCount % FRAME_SKIP_RATE != 0) {
            imageProxy.close();
            return;
        }

        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            //fromMediaImage is the most efficient way to wrap a CameraX frame for ML Kit.The rotation degrees ensure the image is analysed in the correct orientation
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            //Use the frame's own dimensions to compute the total area it covers
            int imageWidth = image.getWidth();
            int imageHeight = image.getHeight();
            float totalImageArea = imageWidth * imageHeight;

            //Detection runs asynchronously, results arrive via the listeners below
            objectDetector.process(image)
                    .addOnSuccessListener(detectedObjects -> {
                        boolean found = false;
                        for (com.google.mlkit.vision.objects.DetectedObject obj : detectedObjects) {
                            //How much of the frame this object's bounding box covers
                            Rect boundingBox = obj.getBoundingBox();
                            float objectArea = boundingBox.width() * boundingBox.height();
                            float coveragePercentage = (objectArea / totalImageArea) * 100;

                            //if the appearing object covers more than 35% of the screen, it is considered an obstacle close to the user
                            if (coveragePercentage > 35.0f && !obj.getLabels().isEmpty()) {
                                found = true;
                                String englishLabel = obj.getLabels().get(0).getText();
                                if (listener != null) {
                                    listener.onObstacleDetected(translateLabel(englishLabel), coveragePercentage);
                                }
                                break;  //report the first qualifying obstacle, then stop
                            }
                        }
                        if (!found && listener != null) { //No object passed the threshold,the path is clear
                            listener.onPathClear();
                        }
                    })
                    //Close the frame when detection finishes - CameraX will not deliver the next frame until the current ImageProxy is closed.
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
        }
    }

    private String translateLabel(String englishLabel) {
        switch (englishLabel) { //labels ML kit understands translated to greek-ML kit cannot understand unfortunately stairs or elevators
            case "Furniture": return "Έπιπλο";
            case "Plant": return "Φυτό";
            case "Place": return "Τοίχος ή Πόρτα";
            case "Fashion good": return "Άνθρωπος ή ρούχο";
            case "Food": return "Φαγητό";
            default: return "Εμπόδιο";
        }
    }
}