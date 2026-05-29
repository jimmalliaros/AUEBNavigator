package com.example.auebnavigator;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Vibrator;
import android.util.Log;

public class StepDetector implements SensorEventListener {

    public interface StepListener {
        void onStepCounted(int currentSteps);
        void onTargetStepsReached();
    }

    private final StepListener listener;
    private final Vibrator vibrator;
    private int currentSteps = 0;
    private int targetSteps = 0;
    private boolean isNavigating = false;
    private long lastStepTime = 0;

    public StepDetector(Context context, StepListener listener) {
        this.listener = listener;
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE);
    }

    public void startNavigation(int targetSteps) {
        this.targetSteps = targetSteps;
        this.currentSteps = 0;
        this.isNavigating = true;
    }

    public void stopNavigation() {
        this.isNavigating = false;
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
                Log.d("Pedometer", "Βήμα: " + currentSteps + " / " + targetSteps);

                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(50);
                }

                if (listener != null) {
                    listener.onStepCounted(currentSteps);
                    if (currentSteps == targetSteps) {
                        isNavigating = false;
                        listener.onTargetStepsReached();
                    }
                }
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}
}