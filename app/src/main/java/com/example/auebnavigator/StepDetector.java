package com.example.auebnavigator;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.os.Vibrator;
import android.util.Log;

/**
  StepDetector class is responsible for detecting steps using the accelerometer sensor
 **/

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
        this.vibrator = (Vibrator) context.getSystemService(Context.VIBRATOR_SERVICE); //install vibrator so that the phone vibrates when the step is "counted"
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
    public void onSensorChanged(SensorEvent event) { //sensor changes many times per second, we want to detect which of these records are real time steps of the user
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER && isNavigating) {
            // x,y,z parameters are the three axies that define the position of the device (the phone of the user) in the world
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            double magnitude = Math.sqrt(x * x + y * y + z * z); //magnitude of the acceleration vector, gravity has a 9.8 magnitude, we want the position of the device to not play any role in the counting of the step so we "neutralize" it
            long currentTime = System.currentTimeMillis();

            //checks to see if the record is a real time step
            if (magnitude > 12.2 && (currentTime - lastStepTime > 450)) { //magnitude > 12.2 means that the record possibly is a real time step, currentTime - lastStepTime > 450 is used to not count the same step two times
                lastStepTime = currentTime;
                currentSteps++;
                Log.d("Pedometer", "Βήμα: " + currentSteps + " / " + targetSteps);

                if (vibrator != null && vibrator.hasVibrator()) {
                    vibrator.vibrate(50); //vibrate-step is counted
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
    public void onAccuracyChanged(Sensor sensor, int accuracy) {} //do nothing if the accuracy of the sensor changes
}