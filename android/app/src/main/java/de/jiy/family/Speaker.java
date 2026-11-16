package de.jiy.family;

import android.content.Context;
import android.speech.tts.TextToSpeech;

import java.util.Locale;

/** Vorlesen auf Deutsch und Arabisch über die Sprachausgabe von Android. */
final class Speaker {

    private static TextToSpeech tts;
    private static boolean ready = false;

    private Speaker() {
    }

    static synchronized void init(Context c) {
        if (tts != null) return;
        tts = new TextToSpeech(c.getApplicationContext(), status -> ready = status == TextToSpeech.SUCCESS);
    }

    private static Locale loc(String lang) {
        return "ar".equals(lang) ? new Locale("ar") : Locale.GERMANY;
    }

    /** @param queue true = hinten anhängen (z. B. erst Deutsch, dann Arabisch), false = sofort sprechen */
    static synchronized boolean speak(String text, String lang, double rate, boolean queue) {
        if (tts == null || !ready || text == null) return false;
        int ok = tts.setLanguage(loc(lang));
        if (ok == TextToSpeech.LANG_MISSING_DATA || ok == TextToSpeech.LANG_NOT_SUPPORTED) return false;
        tts.setSpeechRate((float) (rate <= 0 ? 0.85 : rate));
        tts.speak(text, queue ? TextToSpeech.QUEUE_ADD : TextToSpeech.QUEUE_FLUSH, null, "jiy" + System.nanoTime());
        return true;
    }

    /** @return "ok", "missing" (Sprachpaket fehlt), "off" (noch nicht bereit) */
    static synchronized String check(String lang) {
        if (tts == null || !ready) return "off";
        int r = tts.isLanguageAvailable(loc(lang));
        return (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) ? "missing" : "ok";
    }

    static synchronized void stop() {
        if (tts != null) tts.stop();
    }
}
