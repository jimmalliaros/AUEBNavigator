package com.example.auebnavigator;

import android.content.Context;
import android.speech.tts.TextToSpeech;
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
        }
        return instance;
    }

    private VoiceManager(Context context, VoiceInitListener listener) {
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
    }

    public void speak(String text, boolean isPriority) {
        if (tts != null && isReady) {
            if (isPriority) {
                tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
            } else {
                if (!tts.isSpeaking()) {
                    tts.speak(text, TextToSpeech.QUEUE_ADD, null, null);
                }
            }
        }
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        instance = null;
    }
}