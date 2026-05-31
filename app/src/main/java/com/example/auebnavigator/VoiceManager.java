package com.example.auebnavigator;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.util.Log;
import java.util.Locale;

/**
VoiceManager class is responsible for setting up and managing the TextToSpeech (tts) engine
**/

public class VoiceManager {
    private static VoiceManager instance; //one and only instance of VoiceManager (singleton)
    private TextToSpeech tts; //TextToSpeech object
    private boolean isReady = false;

    public interface VoiceInitListener { //listener is not exactly used at the moment
        void onVoiceReady();
    }

    public static synchronized VoiceManager getInstance(Context context, VoiceInitListener listener) { //synchronized for only one thread to have access at a time
        if (instance == null) { //no VoiceManager instance exists, create one
            instance = new VoiceManager(context.getApplicationContext(), listener);
        } else {
            //VoiceManager instance already exists
            if (instance.isReady && listener != null) {
                listener.onVoiceReady();
            }
        }
        return instance;
    }

    private VoiceManager(Context context, VoiceInitListener listener) {
        try {
            tts = new TextToSpeech(context, status -> { //new tts object
                if (status == TextToSpeech.SUCCESS) { //tts was initialized successfully
                    int result = tts.setLanguage(new Locale("el", "GR"));
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) { //language is supported
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
        try {
            if (tts != null && isReady) {
                if (isPriority) { //text is a priority, speak it now
                    tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, null);
                } else { //text not a priority, add to queue
                    if (!tts.isSpeaking()) {
                        tts.speak(text, TextToSpeech.QUEUE_ADD, null, null);
                    }
                }
            }
        } catch (Exception e) {
            // if tts fails, show it at Logcat (debugging)
            Log.e("VoiceManager", "Speech error: " + e.getMessage());
        }
    }

    public void shutdown() { //close tts
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