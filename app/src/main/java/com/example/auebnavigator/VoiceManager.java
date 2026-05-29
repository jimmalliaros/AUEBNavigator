package com.example.auebnavigator;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import java.util.Locale;

public class VoiceManager {
    private static VoiceManager instance;
    private TextToSpeech tts;
    private boolean isReady = false;

    public interface VoiceInitListener {
        void onVoiceReady();
    }

    public static synchronized VoiceManager getInstance(Context context, VoiceInitListener listener) {
        if (instance == null) {
            instance = new VoiceManager(context.getApplicationContext(), listener);
        } else {
            // Αν το instance υπάρχει ήδη, τρέξε το callback για να μην "κολλάει" η ροή
            if (instance.isReady && listener != null) {
                listener.onVoiceReady();
            }
        }
        return instance;
    }

    private VoiceManager(Context context, VoiceInitListener listener) {
        try {
            tts = new TextToSpeech(context, status -> {
                if (status == TextToSpeech.SUCCESS) {
                    int result = tts.setLanguage(new Locale("el", "GR"));
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                        float speed = context.getSharedPreferences("AuebNavPrefs", Context.MODE_PRIVATE).getFloat("tts_speed", 1.0f);
                        tts.setSpeechRate(speed);
                        isReady = true;
                        if (listener != null) listener.onVoiceReady();
                    }
                }
            });
        } catch (Exception e) {
            Log.e("VoiceManager", "Critical TTS Init error: " + e.getMessage());
        }
    }

    public void speak(String text, boolean isPriority) {
        // 🔥 Robust Error Handling: Προστατεύουμε την εφαρμογή από TTS crashes
        try {
            if (tts != null && isReady) {
                if (isPriority) {
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
                } else {
                    if (!tts.isSpeaking()) {
                        tts.speak(text, TextToSpeech.QUEUE_ADD, null, null);
                    }
                }
            }
        } catch (Exception e) {
            // Αν το TTS engine αποτύχει, το καταγράφουμε στο Logcat και συνεχίζουμε
            Log.e("VoiceManager", "Speech error: " + e.getMessage());
        }
    }

    public void shutdown() {
        try {
            if (tts != null) {
                tts.stop();
                tts.shutdown();
            }
        } catch (Exception e) {
            Log.e("VoiceManager", "Shutdown error: " + e.getMessage());
        }
        instance = null;
    }
}