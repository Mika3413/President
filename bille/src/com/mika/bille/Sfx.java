package com.mika.bille;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Random;

/** Effets sonores synthétisés au lancement (aucun fichier audio embarqué). */
final class Sfx {
    static final int BUMP = 0, STAR = 1, FALL = 2, DEATH = 3, BUMPER = 4, TELEPORT = 5, WIN = 6, CLICK = 7, LAVA = 8;
    private static final int RATE = 22050;

    private SoundPool pool;
    private final int[] ids = new int[9];
    boolean enabled = true;

    void load(Context ctx) {
        AudioAttributes aa = new AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_GAME)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build();
        pool = new SoundPool.Builder().setMaxStreams(8).setAudioAttributes(aa).build();
        File dir = new File(ctx.getCacheDir(), "sfx2");
        dir.mkdirs();
        for (int i = 0; i < ids.length; i++) {
            File f = new File(dir, "s" + i + ".wav");
            try {
                if (!f.exists() || f.length() < 100) writeWav(f, synth(i));
                ids[i] = pool.load(f.getAbsolutePath(), 1);
            } catch (Exception e) {
                ids[i] = 0;
            }
        }
    }

    void play(int id, float vol) {
        play(id, vol, 1f);
    }

    void play(int id, float vol, float rate) {
        if (!enabled || pool == null || ids[id] == 0) return;
        vol = Math.max(0f, Math.min(1f, vol));
        pool.play(ids[id], vol, vol, 1, 0, Math.max(0.5f, Math.min(2f, rate)));
    }

    void release() {
        if (pool != null) pool.release();
        pool = null;
    }

    private static float[] synth(int id) {
        Random rnd = new Random(id * 31 + 7);
        switch (id) {
            case BUMP: {
                float[] s = new float[(int) (RATE * 0.12)];
                for (int i = 0; i < s.length; i++) {
                    float t = i / (float) RATE;
                    float env = (float) Math.exp(-t * 45);
                    s[i] = env * (0.8f * (float) Math.sin(2 * Math.PI * 140 * t) + 0.35f * (rnd.nextFloat() * 2 - 1) * (float) Math.exp(-t * 120));
                }
                return s;
            }
            case STAR: {
                float[] s = new float[(int) (RATE * 0.55)];
                float[] notes = {1318.5f, 1975.5f, 2637f};
                for (int i = 0; i < s.length; i++) {
                    float t = i / (float) RATE;
                    float v = 0;
                    for (int k = 0; k < notes.length; k++) {
                        float tk = t - k * 0.06f;
                        if (tk < 0) continue;
                        v += (float) (Math.exp(-tk * 9) * (Math.sin(2 * Math.PI * notes[k] * tk) + 0.3 * Math.sin(4 * Math.PI * notes[k] * tk)));
                    }
                    s[i] = v * 0.33f;
                }
                return s;
            }
            case FALL: {
                float[] s = new float[(int) (RATE * 0.8)];
                double ph = 0;
                for (int i = 0; i < s.length; i++) {
                    float t = i / (float) RATE;
                    float f = 700 * (float) Math.exp(-t * 3.2) + 60;
                    ph += 2 * Math.PI * f / RATE;
                    float env = Math.min(1f, t * 40) * (float) Math.exp(-t * 2.5);
                    s[i] = env * 0.6f * (float) (Math.sin(ph) + 0.25 * Math.sin(3 * ph));
                }
                return s;
            }
            case LAVA: {
                float[] s = new float[(int) (RATE * 0.9)];
                float lp = 0;
                for (int i = 0; i < s.length; i++) {
                    float t = i / (float) RATE;
                    float n = rnd.nextFloat() * 2 - 1;
                    lp += (n - lp) * 0.08f;
                    float env = Math.min(1f, t * 30) * (float) Math.exp(-t * 3);
                    float bubble = (float) Math.sin(2 * Math.PI * (90 + 40 * Math.sin(t * 30)) * t);
                    s[i] = env * (1.6f * lp + 0.35f * bubble);
                }
                return s;
            }
            case DEATH: {
                float[] s = new float[(int) (RATE * 0.45)];
                float lp = 0;
                for (int i = 0; i < s.length; i++) {
                    float t = i / (float) RATE;
                    float n = rnd.nextFloat() * 2 - 1;
                    lp += (n - lp) * 0.35f;
                    float env = (float) Math.exp(-t * 9);
                    s[i] = env * (0.7f * lp + 0.4f * (float) Math.sin(2 * Math.PI * (220 - 300 * t) * t));
                }
                return s;
            }
            case BUMPER: {
                float[] s = new float[(int) (RATE * 0.3)];
                double ph = 0;
                for (int i = 0; i < s.length; i++) {
                    float t = i / (float) RATE;
                    float f = 260 + 420 * (float) Math.exp(-t * 18);
                    ph += 2 * Math.PI * f / RATE;
                    s[i] = (float) Math.exp(-t * 11) * 0.6f * (float) Math.sin(ph);
                }
                return s;
            }
            case TELEPORT: {
                float[] s = new float[(int) (RATE * 0.5)];
                double ph = 0;
                for (int i = 0; i < s.length; i++) {
                    float t = i / (float) RATE;
                    float f = 300 + 1800 * t;
                    ph += 2 * Math.PI * f / RATE;
                    float trem = 0.6f + 0.4f * (float) Math.sin(2 * Math.PI * 28 * t);
                    s[i] = (float) Math.sin(Math.PI * t / 0.5) * trem * 0.45f * (float) Math.sin(ph);
                }
                return s;
            }
            case WIN: {
                float[] s = new float[(int) (RATE * 1.4)];
                float[] notes = {523.3f, 659.3f, 784f, 1046.5f, 1318.5f};
                for (int i = 0; i < s.length; i++) {
                    float t = i / (float) RATE;
                    float v = 0;
                    for (int k = 0; k < notes.length; k++) {
                        float tk = t - k * 0.09f;
                        if (tk < 0) continue;
                        float env = (float) Math.exp(-tk * (k == notes.length - 1 ? 2.2 : 5));
                        v += env * (float) (Math.sin(2 * Math.PI * notes[k] * tk) + 0.2 * Math.sin(4 * Math.PI * notes[k] * tk));
                    }
                    s[i] = v * 0.25f;
                }
                return s;
            }
            default: { // CLICK
                float[] s = new float[(int) (RATE * 0.05)];
                for (int i = 0; i < s.length; i++) {
                    float t = i / (float) RATE;
                    s[i] = (float) Math.exp(-t * 90) * 0.5f * (float) Math.sin(2 * Math.PI * 1200 * t);
                }
                return s;
            }
        }
    }

    private static void writeWav(File f, float[] s) throws Exception {
        int n = s.length;
        byte[] b = new byte[44 + n * 2];
        int dataLen = n * 2;
        put(b, 0, "RIFF");
        le32(b, 4, 36 + dataLen);
        put(b, 8, "WAVE");
        put(b, 12, "fmt ");
        le32(b, 16, 16);
        le16(b, 20, 1);
        le16(b, 22, 1);
        le32(b, 24, RATE);
        le32(b, 28, RATE * 2);
        le16(b, 32, 2);
        le16(b, 34, 16);
        put(b, 36, "data");
        le32(b, 40, dataLen);
        for (int i = 0; i < n; i++) {
            float fade = i > n - 64 ? (n - i) / 64f : 1f;
            int v = (int) (Math.max(-1f, Math.min(1f, s[i] * fade)) * 32000);
            le16(b, 44 + i * 2, v);
        }
        FileOutputStream out = new FileOutputStream(f);
        try {
            out.write(b);
        } finally {
            out.close();
        }
    }

    private static void put(byte[] b, int o, String s) {
        for (int i = 0; i < 4; i++) b[o + i] = (byte) s.charAt(i);
    }

    private static void le32(byte[] b, int o, int v) {
        b[o] = (byte) v; b[o + 1] = (byte) (v >> 8); b[o + 2] = (byte) (v >> 16); b[o + 3] = (byte) (v >> 24);
    }

    private static void le16(byte[] b, int o, int v) {
        b[o] = (byte) v; b[o + 1] = (byte) (v >> 8);
    }
}
