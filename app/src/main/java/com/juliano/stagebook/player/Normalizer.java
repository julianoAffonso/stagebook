package com.juliano.stagebook.player;

import android.content.Context;
import android.content.SharedPreferences;

import java.io.File;
import java.util.Locale;

// Normalização por música: loudness (LUFS) do backing e pico do click, com cache.
final class Normalizer {
    static final float TARGET_LUFS = -16f;      // backing: volume percebido igual entre músicas
    static final float TARGET_CLICK_DB = -6f;   // click: pico igual entre músicas
    static final float CEILING_DB = -1f;        // backing nunca passa de -1 dBFS de pico
    static final float MAX_BOOST_DB = 15f, MAX_CUT_DB = -24f;

    static volatile int done, total;            // progresso da análise em lote

    static SharedPreferences cache(Context c) { return c.getSharedPreferences("norm", Context.MODE_PRIVATE); }

    static String key(File f) { return f.getName() + "|" + f.length() + "|" + f.lastModified(); }

    // Retorna {lufs, picoBacking, picoClick}, analisando se não estiver no cache.
    static float[] measure(Context c, File f) {
        String k = key(f);
        String v = cache(c).getString(k, null);
        if (v != null) {
            String[] p = v.split(";");
            try {
                return new float[]{Float.parseFloat(p[0]), Float.parseFloat(p[1]), Float.parseFloat(p[2])};
            } catch (RuntimeException e) {
                // cache corrompido: analisa de novo
            }
        }
        float[] m = Engine.analyze(f.getAbsolutePath());
        if (m != null) cache(c).edit().putString(k, m[0] + ";" + m[1] + ";" + m[2]).apply();
        return m;
    }

    static float db(float lin) { return (float) (20 * Math.log10(Math.max(lin, 1e-9))); }

    static float clamp(float x) { return Math.max(MAX_CUT_DB, Math.min(MAX_BOOST_DB, x)); }

    // Ganhos lineares {backing, click} para chegar nos alvos.
    static float[] gains(float[] m) {
        float gb = TARGET_LUFS - m[0];
        gb = Math.min(gb, CEILING_DB - db(m[1]));           // não deixa clipar
        gb = clamp(gb);
        float gc = m[2] > 1e-6 ? clamp(TARGET_CLICK_DB - db(m[2])) : 0f;
        return new float[]{(float) Math.pow(10, gb / 20), (float) Math.pow(10, gc / 20), gb, gc};
    }

    static String describe(float[] m, float[] g) {
        return String.format(Locale.US, "backing %.1f LUFS → %+.1f dB · click pico %.1f dB → %+.1f dB",
                m[0], g[2], db(m[2]), g[3]);
    }

    // Analisa a pasta inteira em segundo plano (o que já está no cache é instantâneo).
    static void analyzeAll(Context c, File dir) {
        File[] all = dir.listFiles((d, n) -> n.toLowerCase(Locale.ROOT).endsWith(".ogg"));
        if (all == null) return;
        total = all.length;
        done = 0;
        for (File f : all) {
            measure(c, f);
            done++;
        }
    }
}
