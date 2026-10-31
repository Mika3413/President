package com.mika.bille;

import android.graphics.Bitmap;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.CornerPathEffect;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;

import java.util.HashMap;
import java.util.Random;

/** Dessin du plateau, des pièges et de la bille. */
final class Renderer {
    // ---------- sprites partagés ----------
    private Bitmap glow, ballShadow, ballBody, ballOverlay, spikeSprite;
    private int spriteSize;
    private final HashMap<Integer, ColorFilter> filters = new HashMap<>();
    private final Paint sp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint p2 = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Rect dst = new Rect();
    private final Path path = new Path();
    private final Path clip = new Path();
    private final Path starPath = new Path();
    private final Path chevron = new Path();
    private Shader starShader, bumperShader, hubShader, bladeShader, emitterShader;

    // ---------- plateau courant ----------
    Level lv;
    Theme th;
    Bitmap board;
    float cs, ox, oy;
    private int bw, bh;
    private Shader wallTopShader;
    private float[][] motes;

    // ---------- particules (coordonnées en cases) ----------
    private static final int MAXP = 500;
    private final float[] px = new float[MAXP], py = new float[MAXP], pvx = new float[MAXP], pvy = new float[MAXP];
    private final float[] plife = new float[MAXP], pmax = new float[MAXP], psize = new float[MAXP];
    private final int[] pcol = new int[MAXP];
    private final boolean[] pglow = new boolean[MAXP];
    private int pcount;
    private final Random rnd = new Random();

    // traînée
    private final float[] trailX = new float[16], trailY = new float[16];
    private int trailN;

    // motif de la bille (points sur la sphère, repère de la bille)
    private static final float[][] BAND = new float[40][3];
    private static final float[][] DOTS = new float[10][3];
    static {
        for (int i = 0; i < BAND.length; i++) {
            double a = 2 * Math.PI * i / BAND.length;
            BAND[i][0] = (float) (0.22 * Math.sin(3 * a));
            BAND[i][1] = (float) Math.cos(a);
            BAND[i][2] = (float) Math.sin(a);
            float l = (float) Math.sqrt(BAND[i][0] * BAND[i][0] + BAND[i][1] * BAND[i][1] + BAND[i][2] * BAND[i][2]);
            BAND[i][0] /= l; BAND[i][1] /= l; BAND[i][2] /= l;
        }
        double gold = Math.PI * (3 - Math.sqrt(5));
        for (int i = 0; i < DOTS.length; i++) {
            float y = 1 - (i / (float) (DOTS.length - 1)) * 2;
            float r = (float) Math.sqrt(1 - y * y);
            double th = gold * i;
            DOTS[i][0] = y;
            DOTS[i][1] = (float) (Math.cos(th) * r);
            DOTS[i][2] = (float) (Math.sin(th) * r);
        }
    }

    Renderer() {
        // étoile unitaire
        for (int i = 0; i < 10; i++) {
            double a = -Math.PI / 2 + i * Math.PI / 5;
            float r = i % 2 == 0 ? 1f : 0.46f;
            float x = (float) Math.cos(a) * r, y = (float) Math.sin(a) * r;
            if (i == 0) starPath.moveTo(x, y); else starPath.lineTo(x, y);
        }
        starPath.close();
        starShader = new LinearGradient(-0.6f, -1f, 0.6f, 1f, new int[]{0xFFFFF7C2, 0xFFFFD43B, 0xFFE8A000}, new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP);
        bumperShader = new RadialGradient(-0.35f, -0.4f, 1.5f, new int[]{0xFFFFD6F5, 0xFFFF4FB8, 0xFFA0127A, 0xFF3A0630}, new float[]{0, 0.3f, 0.75f, 1}, Shader.TileMode.CLAMP);
        hubShader = new RadialGradient(-0.3f, -0.35f, 1.4f, new int[]{0xFFFFFFFF, 0xFFB8C0C8, 0xFF4A525C}, new float[]{0, 0.4f, 1}, Shader.TileMode.CLAMP);
        bladeShader = new LinearGradient(0, -1, 0, 1, new int[]{0xFFF4F7FA, 0xFF9AA4AE, 0xFF4A525C}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP);
        emitterShader = new RadialGradient(-0.3f, -0.35f, 1.3f, new int[]{0xFFE6E9EE, 0xFF7C848E, 0xFF2A2F36}, new float[]{0, 0.5f, 1}, Shader.TileMode.CLAMP);
        chevron.moveTo(-0.28f, 0.12f);
        chevron.lineTo(0f, -0.14f);
        chevron.lineTo(0.28f, 0.12f);
    }

    // =====================================================================
    // Sprites
    // =====================================================================

    void ensureSprites(float ballRadiusPx) {
        int size = Math.max(32, (int) (ballRadiusPx * 2.6f));
        if (glow == null) {
            glow = Bitmap.createBitmap(128, 128, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(glow);
            Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
            g.setAlpha(255);
            g.setShader(new RadialGradient(64, 64, 64, new int[]{0xFFFFFFFF, 0x99FFFFFF, 0x30FFFFFF, 0x00FFFFFF}, new float[]{0, 0.18f, 0.5f, 1}, Shader.TileMode.CLAMP));
            c.drawCircle(64, 64, 64, g);
        }
        if (ballBody != null && spriteSize == size) return;
        spriteSize = size;
        float r = size / 2f;
        // ombre
        ballShadow = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(ballShadow);
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setAlpha(255);
        g.setShader(new RadialGradient(r, r, r, new int[]{0xB0000000, 0x70000000, 0x00000000}, new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(r, r, r, g);
        // corps : verre bleu nuit
        ballBody = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        c = new Canvas(ballBody);
        g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setAlpha(255);
        g.setShader(new RadialGradient(r * 0.72f, r * 0.68f, r * 1.25f,
                new int[]{0xFF8FD3FF, 0xFF2C7BE0, 0xFF143C94, 0xFF07153D}, new float[]{0, 0.3f, 0.72f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(r, r, r - 0.5f, g);
        // reflet interne (caustique) en bas à droite
        g.setAlpha(255);
        g.setShader(new RadialGradient(r * 1.32f, r * 1.38f, r * 0.55f, new int[]{0x7080E8FF, 0x0080E8FF}, null, Shader.TileMode.CLAMP));
        c.drawCircle(r, r, r - 0.5f, g);
        // surcouche : ombrage + reflets
        ballOverlay = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        c = new Canvas(ballOverlay);
        g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setAlpha(255);
        g.setShader(new RadialGradient(r * 0.75f, r * 0.72f, r * 1.3f, new int[]{0x00000000, 0x00000000, 0x55000820, 0xC0000510}, new float[]{0, 0.45f, 0.78f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(r, r, r - 0.5f, g);
        // liseré lumineux
        g.setAlpha(255);
        g.setShader(null);
        g.setStyle(Paint.Style.STROKE);
        g.setStrokeWidth(r * 0.06f);
        g.setColor(0x6699DDFF);
        RectF o = new RectF(r * 0.08f, r * 0.08f, size - r * 0.08f, size - r * 0.08f);
        c.drawArc(o, 10, 80, false, g);
        g.setStyle(Paint.Style.FILL);
        // reflet spéculaire principal
        g.setAlpha(255);
        g.setShader(new RadialGradient(r * 0.62f, r * 0.55f, r * 0.42f, new int[]{0xF0FFFFFF, 0x60FFFFFF, 0x00FFFFFF}, new float[]{0, 0.4f, 1}, Shader.TileMode.CLAMP));
        c.save();
        c.scale(1f, 0.75f, r * 0.62f, r * 0.55f);
        c.drawCircle(r * 0.62f, r * 0.55f, r * 0.42f, g);
        c.restore();
        g.setAlpha(255);
        g.setShader(null);
        g.setColor(0xFFFFFFFF);
        c.drawCircle(r * 0.56f, r * 0.5f, r * 0.08f, g);
        g.setColor(0x80FFFFFF);
        c.drawCircle(r * 1.38f, r * 1.42f, r * 0.05f, g);
    }

    private ColorFilter filter(int color) {
        ColorFilter f = filters.get(color);
        if (f == null) {
            f = new PorterDuffColorFilter(color | 0xFF000000, PorterDuff.Mode.SRC_IN);
            filters.put(color, f);
        }
        return f;
    }

    /** Halo lumineux centré en (x, y) pixels. */
    void glow(Canvas c, float x, float y, float radius, int color, float alpha) {
        if (alpha <= 0.01f) return;
        sp.setColorFilter(filter(color & 0xFFFFFF));
        sp.setAlpha((int) (Math.min(1f, alpha) * 255));
        rf.set(x - radius, y - radius, x + radius, y + radius);
        c.drawBitmap(glow, null, rf, sp);
        sp.setColorFilter(null);
        sp.setAlpha(255);
    }

    /** Bille en pixels, avec motif qui roule selon la matrice rot. */
    void drawMarble(Canvas c, float x, float y, float r, float[] rot, float alpha, boolean shadow) {
        if (r < 0.5f) return;
        int a = (int) (alpha * 255);
        float s = spriteSize / 2.6f; // rayon de référence des sprites
        float k = r / s;
        if (shadow) {
            sp.setAlpha(a);
            float sr = spriteSize / 2f * k * 1.05f;
            float sx = x + r * 0.28f, sy = y + r * 0.4f;
            rf.set(sx - sr, sy - sr * 0.85f, sx + sr, sy + sr * 0.85f);
            c.drawBitmap(ballShadow, null, rf, sp);
        }
        rf.set(x - r, y - r, x + r, y + r);
        sp.setAlpha(a);
        c.drawBitmap(ballBody, null, rf, sp);
        // motif roulant
        c.save();
        clip.reset();
        clip.addCircle(x, y, r * 0.97f, Path.Direction.CW);
        c.clipPath(clip);
        float[] m = rot;
        for (int i = 0; i < BAND.length; i++) {
            float[] q = BAND[i];
            float wx = m[0] * q[0] + m[1] * q[1] + m[2] * q[2];
            float wy = m[3] * q[0] + m[4] * q[1] + m[5] * q[2];
            float wz = m[6] * q[0] + m[7] * q[1] + m[8] * q[2];
            if (wz < -0.1f) continue;
            float lum = 0.45f + 0.55f * Math.max(0, wz);
            p.setColor(mix(0xFF7A2E00, 0xFFFFB347, lum));
            p.setAlpha((int) (a * Math.min(1f, (wz + 0.1f) * 4f)));
            c.drawCircle(x + wx * r * 0.95f, y + wy * r * 0.95f, r * (0.08f + 0.1f * Math.max(0, wz)), p);
        }
        for (int i = 0; i < DOTS.length; i++) {
            float[] q = DOTS[i];
            float wx = m[0] * q[0] + m[1] * q[1] + m[2] * q[2];
            float wy = m[3] * q[0] + m[4] * q[1] + m[5] * q[2];
            float wz = m[6] * q[0] + m[7] * q[1] + m[8] * q[2];
            if (wz < 0.05f) continue;
            p.setColor(0xFFE8F6FF);
            p.setAlpha((int) (a * Math.min(1f, wz * 3f) * 0.85f));
            c.drawCircle(x + wx * r * 0.95f, y + wy * r * 0.95f, r * 0.07f * (0.4f + 0.6f * wz), p);
        }
        c.restore();
        p.setAlpha(255);
        c.drawBitmap(ballOverlay, null, rf, sp);
        sp.setAlpha(255);
    }

    static int mix(int c1, int c2, float k) {
        k = Math.max(0, Math.min(1, k));
        int a1 = c1 >>> 24, r1 = (c1 >> 16) & 255, g1 = (c1 >> 8) & 255, b1 = c1 & 255;
        int a2 = c2 >>> 24, r2 = (c2 >> 16) & 255, g2 = (c2 >> 8) & 255, b2 = c2 & 255;
        return ((int) (a1 + (a2 - a1) * k) << 24) | ((int) (r1 + (r2 - r1) * k) << 16)
                | ((int) (g1 + (g2 - g1) * k) << 8) | (int) (b1 + (b2 - b1) * k);
    }

    static int alpha(int color, int a) {
        return (color & 0xFFFFFF) | (Math.max(0, Math.min(255, a)) << 24);
    }

    // =====================================================================
    // Arrière-plan seul (menus)
    // =====================================================================

    static Bitmap background(int w, int h, Theme th, long seed) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        paintBackground(c, w, h, th, new Random(seed));
        return b;
    }

    private static void paintBackground(Canvas c, int w, int h, Theme th, Random r) {
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setAlpha(255);
        g.setShader(new LinearGradient(0, 0, w * 0.3f, h, th.bgTop, th.bgBottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, g);
        for (int i = 0; i < 22; i++) {
            float x = r.nextFloat() * w, y = r.nextFloat() * h, rad = w * (0.04f + r.nextFloat() * 0.16f);
            int a = 18 + r.nextInt(30);
            g.setAlpha(255);
            g.setShader(new RadialGradient(x, y, rad, new int[]{alpha(th.bokeh, a), alpha(th.bokeh, a / 2), alpha(th.bokeh, 0)}, new float[]{0, 0.7f, 1}, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rad, g);
        }
        g.setAlpha(255);
        g.setShader(new RadialGradient(w / 2f, h * 0.45f, h * 0.75f, new int[]{0x00000000, 0x00000000, 0xA0000000}, new float[]{0, 0.55f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, g);
    }

    // =====================================================================
    // Plateau pré-rendu
    // =====================================================================

    void setup(Level level, int w, int h, float top, float bottom) {
        lv = level;
        th = Theme.of(level.world);
        float m = w * 0.035f;
        cs = Math.min((w - 2 * m) / (Level.COLS + 0.7f), (h - top - bottom - 2 * m) / (Level.ROWS + 0.7f));
        ox = (w - Level.COLS * cs) / 2f;
        oy = top + m + (h - top - bottom - 2 * m - Level.ROWS * cs) / 2f;
        bw = w;
        bh = h;
        ensureSprites(World.R * cs);
        Random r = new Random(level.name.hashCode());
        motes = new float[26][4];
        for (float[] mo : motes) {
            mo[0] = r.nextFloat() * Level.COLS;
            mo[1] = r.nextFloat() * Level.ROWS;
            mo[2] = r.nextFloat() * 6.28f;
            mo[3] = 0.5f + r.nextFloat();
        }
        if (board != null) board.recycle();
        board = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        bake(new Canvas(board), r);
        pcount = 0;
        trailN = 0;
    }

    float X(float x) { return ox + x * cs; }
    float Y(float y) { return oy + y * cs; }

    private void bake(Canvas c, Random r) {
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        paintBackground(c, bw, bh, th, r);
        RectF bd = new RectF(ox, oy, ox + Level.COLS * cs, oy + Level.ROWS * cs);
        float fr = cs * 0.34f;
        RectF frame = new RectF(bd.left - fr, bd.top - fr, bd.right + fr, bd.bottom + fr);

        // ombre et cadre
        g.setColor(0xC0000000);
        g.setMaskFilter(new BlurMaskFilter(cs * 0.6f, BlurMaskFilter.Blur.NORMAL));
        c.drawRoundRect(frame.left + cs * 0.12f, frame.top + cs * 0.35f, frame.right + cs * 0.12f, frame.bottom + cs * 0.35f, cs * 0.5f, cs * 0.5f, g);
        g.setMaskFilter(null);
        g.setAlpha(255);
        g.setShader(new LinearGradient(frame.left, frame.top, frame.right, frame.bottom,
                new int[]{th.frameLight, mix(th.frameLight, th.frameDark, 0.5f), th.frameDark}, null, Shader.TileMode.CLAMP));
        c.drawRoundRect(frame, cs * 0.45f, cs * 0.45f, g);
        g.setAlpha(255);
        g.setShader(null);
        // veinage / brossage du cadre
        c.save();
        c.clipRect(frame);
        g.setStyle(Paint.Style.STROKE);
        g.setStrokeWidth(Math.max(1f, cs * 0.015f));
        for (int i = 0; i < 160; i++) {
            float y = frame.top + r.nextFloat() * frame.height();
            float x = frame.left + r.nextFloat() * frame.width();
            g.setColor(alpha(r.nextBoolean() ? 0xFFFFFF : 0x000000, 10 + r.nextInt(18)));
            if (r.nextBoolean()) c.drawLine(x - cs * 2, y, x + cs * 2, y + cs * 0.05f, g);
            else c.drawLine(x, y - cs * 2, x + cs * 0.05f, y + cs * 2, g);
        }
        c.restore();
        g.setColor(0x50FFFFFF);
        g.setStrokeWidth(cs * 0.05f);
        RectF in = new RectF(frame);
        in.inset(cs * 0.04f, cs * 0.04f);
        c.drawRoundRect(in, cs * 0.42f, cs * 0.42f, g);
        g.setStyle(Paint.Style.FILL);

        // sol
        c.save();
        c.clipRect(bd);
        g.setAlpha(255);
        g.setShader(new RadialGradient(bd.left + bd.width() * 0.3f, bd.top + bd.height() * 0.25f, bd.height() * 0.95f,
                th.floorLight, th.floorDark, Shader.TileMode.CLAMP));
        c.drawRect(bd, g);
        g.setAlpha(255);
        g.setShader(null);
        for (int i = 0; i < 3500; i++) {
            g.setColor(alpha(th.floorSpeck, 6 + r.nextInt(26)));
            c.drawCircle(bd.left + r.nextFloat() * bd.width(), bd.top + r.nextFloat() * bd.height(), cs * (0.006f + r.nextFloat() * 0.022f), g);
        }
        if (th.frost) {
            g.setStyle(Paint.Style.STROKE);
            for (int i = 0; i < 40; i++) {
                float x = bd.left + r.nextFloat() * bd.width(), y = bd.top + r.nextFloat() * bd.height();
                g.setStrokeWidth(cs * (0.1f + r.nextFloat() * 0.5f));
                g.setColor(alpha(0xFFFFFF, 5 + r.nextInt(8)));
                c.drawLine(x, y, x + cs * 3, y - cs * 3, g);
            }
            g.setStyle(Paint.Style.FILL);
        }
        if (th.cracks) drawCracks(c, bd, r);
        // quadrillage discret
        g.setColor(0x0EFFFFFF);
        g.setStrokeWidth(1f);
        for (int i = 1; i < Level.COLS; i++) c.drawLine(X(i), bd.top, X(i), bd.bottom, g);
        for (int i = 1; i < Level.ROWS; i++) c.drawLine(bd.left, Y(i), bd.right, Y(i), g);

        // zones
        for (int row = 0; row < Level.ROWS; row++) {
            for (int col = 0; col < Level.COLS; col++) {
                char ch = lv.g[row][col];
                if (ch == '~') drawIce(c, col, row, r);
                else if (ch == ':') drawMud(c, col, row, r);
            }
        }
        for (int[] b : lv.boosts) {
            rf.set(X(b[0]) + cs * 0.06f, Y(b[1]) + cs * 0.06f, X(b[0] + 1) - cs * 0.06f, Y(b[1] + 1) - cs * 0.06f);
            g.setColor(0xC0101018);
            c.drawRoundRect(rf, cs * 0.14f, cs * 0.14f, g);
            g.setStyle(Paint.Style.STROKE);
            g.setStrokeWidth(cs * 0.035f);
            g.setColor(alpha(th.accent, 140));
            c.drawRoundRect(rf, cs * 0.14f, cs * 0.14f, g);
            g.setStyle(Paint.Style.FILL);
        }
        for (int[] s : lv.spikes) {
            rf.set(X(s[0]) + cs * 0.05f, Y(s[1]) + cs * 0.05f, X(s[0] + 1) - cs * 0.05f, Y(s[1] + 1) - cs * 0.05f);
            g.setAlpha(255);
            g.setShader(new LinearGradient(rf.left, rf.top, rf.right, rf.bottom, 0xFF9AA3AD, 0xFF3C434C, Shader.TileMode.CLAMP));
            c.drawRoundRect(rf, cs * 0.1f, cs * 0.1f, g);
            g.setAlpha(255);
            g.setShader(null);
            g.setColor(0xFF15181C);
            for (int k = 0; k < 4; k++) {
                float hx = X(s[0]) + cs * (k % 2 == 0 ? 0.3f : 0.7f), hy = Y(s[1]) + cs * (k < 2 ? 0.3f : 0.7f);
                c.drawCircle(hx, hy, cs * 0.09f, g);
            }
            g.setColor(0x40FFFFFF);
            g.setStyle(Paint.Style.STROKE);
            g.setStrokeWidth(cs * 0.025f);
            c.drawRoundRect(rf, cs * 0.1f, cs * 0.1f, g);
            g.setStyle(Paint.Style.FILL);
        }
        if (lv.teleA != null) {
            drawTeleBase(c, lv.teleA, g);
            drawTeleBase(c, lv.teleB, g);
        }
        // départ
        g.setStyle(Paint.Style.STROKE);
        g.setStrokeWidth(cs * 0.04f);
        g.setColor(0x40FFFFFF);
        c.drawCircle(X(lv.sx), Y(lv.sy), cs * 0.42f, g);
        g.setColor(0x20FFFFFF);
        c.drawCircle(X(lv.sx), Y(lv.sy), cs * 0.3f, g);
        g.setStyle(Paint.Style.FILL);

        // trous
        for (float[] pit : lv.pits) drawPit(c, pit[0], pit[1], pit[2], g);
        // arrivée
        drawGoalBase(c, g);

        // ombre intérieure du cadre
        g.setStyle(Paint.Style.STROKE);
        g.setColor(0xB0000000);
        g.setStrokeWidth(cs * 0.3f);
        g.setMaskFilter(new BlurMaskFilter(cs * 0.22f, BlurMaskFilter.Blur.NORMAL));
        c.drawRect(bd.left - cs * 0.05f, bd.top - cs * 0.08f, bd.right + cs * 0.02f, bd.bottom + cs * 0.02f, g);
        g.setMaskFilter(null);
        g.setStyle(Paint.Style.FILL);
        // vignettage
        g.setAlpha(255);
        g.setShader(new RadialGradient(bd.centerX(), bd.centerY(), bd.height() * 0.7f, new int[]{0x00000000, 0x00000000, 0x70000000}, new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
        c.drawRect(bd, g);
        g.setAlpha(255);
        g.setShader(null);
        c.restore();

        // murs
        drawWalls(c, r);

        wallTopShader = new LinearGradient(bd.left, bd.top, bd.right, bd.bottom, th.wallTopLight, th.wallTopDark, Shader.TileMode.CLAMP);
    }

    private void drawCracks(Canvas c, RectF bd, Random r) {
        Path crack = new Path();
        for (int i = 0; i < 16; i++) {
            float x = bd.left + r.nextFloat() * bd.width(), y = bd.top + r.nextFloat() * bd.height();
            crack.moveTo(x, y);
            float a = r.nextFloat() * 6.28f;
            int n = 4 + r.nextInt(6);
            for (int k = 0; k < n; k++) {
                a += (r.nextFloat() - 0.5f) * 1.4f;
                x += (float) Math.cos(a) * cs * (0.3f + r.nextFloat() * 0.6f);
                y += (float) Math.sin(a) * cs * (0.3f + r.nextFloat() * 0.6f);
                crack.lineTo(x, y);
            }
        }
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setStyle(Paint.Style.STROKE);
        g.setStrokeJoin(Paint.Join.ROUND);
        g.setStrokeCap(Paint.Cap.ROUND);
        g.setColor(0x90FF4A10);
        g.setStrokeWidth(cs * 0.16f);
        g.setMaskFilter(new BlurMaskFilter(cs * 0.18f, BlurMaskFilter.Blur.NORMAL));
        c.drawPath(crack, g);
        g.setMaskFilter(null);
        g.setColor(0xFF1A0A05);
        g.setStrokeWidth(cs * 0.06f);
        c.drawPath(crack, g);
        g.setColor(0xFFFF8A2A);
        g.setStrokeWidth(cs * 0.022f);
        c.drawPath(crack, g);
    }

    private void drawIce(Canvas c, int col, int row, Random r) {
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        float x = X(col), y = Y(row);
        g.setAlpha(255);
        g.setShader(new LinearGradient(x, y, x + cs, y + cs, 0xA0D8F4FF, 0x8070B8E8, Shader.TileMode.CLAMP));
        c.drawRect(x, y, x + cs, y + cs, g);
        g.setAlpha(255);
        g.setShader(null);
        g.setStyle(Paint.Style.STROKE);
        g.setStrokeWidth(Math.max(1f, cs * 0.012f));
        for (int i = 0; i < 3; i++) {
            g.setColor(alpha(0xFFFFFF, 60 + r.nextInt(80)));
            float sx = x + r.nextFloat() * cs, sy = y + r.nextFloat() * cs;
            c.drawLine(sx, sy, sx + (r.nextFloat() - 0.5f) * cs * 0.6f, sy + (r.nextFloat() - 0.5f) * cs * 0.6f, g);
        }
        g.setStyle(Paint.Style.FILL);
        g.setColor(0x30FFFFFF);
        c.drawRect(x, y, x + cs, y + cs * 0.06f, g);
    }

    private void drawMud(Canvas c, int col, int row, Random r) {
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        float x = X(col), y = Y(row);
        g.setColor(0xE0563A22);
        c.drawRect(x, y, x + cs, y + cs, g);
        for (int i = 0; i < 9; i++) {
            g.setColor(r.nextBoolean() ? 0x60301E10 : 0x407A5A3A);
            c.drawCircle(x + r.nextFloat() * cs, y + r.nextFloat() * cs, cs * (0.05f + r.nextFloat() * 0.12f), g);
        }
        g.setColor(0x30FFE0B0);
        c.drawCircle(x + r.nextFloat() * cs, y + r.nextFloat() * cs, cs * 0.04f, g);
    }

    private void drawTeleBase(Canvas c, float[] t, Paint g) {
        float x = X(t[0]), y = Y(t[1]), rr = cs * 0.46f;
        g.setAlpha(255);
        g.setShader(new RadialGradient(x, y, rr, new int[]{0xFF0A0418, 0xFF3A1A7A, 0xFF8A5CFF}, new float[]{0, 0.7f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rr, g);
        g.setAlpha(255);
        g.setShader(null);
        g.setStyle(Paint.Style.STROKE);
        g.setStrokeWidth(cs * 0.05f);
        g.setColor(0xFFCDB8FF);
        c.drawCircle(x, y, rr, g);
        g.setStyle(Paint.Style.FILL);
    }

    private void drawPit(Canvas c, float cx, float cy, float pr, Paint g) {
        float x = X(cx), y = Y(cy), rr = pr * cs;
        if (th.lava) {
            g.setColor(0xFF120806);
            c.drawCircle(x, y, rr * 1.12f, g);
            g.setAlpha(255);
            g.setShader(new RadialGradient(x - rr * 0.15f, y - rr * 0.15f, rr * 1.05f,
                    new int[]{0xFFFFF4A8, 0xFFFFB02E, 0xFFE0480E, 0xFF5A1204}, new float[]{0, 0.35f, 0.75f, 1}, Shader.TileMode.CLAMP));
            c.drawCircle(x, y, rr, g);
            g.setAlpha(255);
            g.setShader(null);
            g.setStyle(Paint.Style.STROKE);
            g.setStrokeWidth(cs * 0.06f);
            g.setColor(0xFF2A100A);
            c.drawCircle(x, y, rr, g);
            g.setStyle(Paint.Style.FILL);
            return;
        }
        // lèvre
        g.setAlpha(255);
        g.setShader(new RadialGradient(x, y, rr * 1.25f, new int[]{0x00000000, 0x00000000, 0x60000000, 0x00000000}, new float[]{0, 0.7f, 0.82f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rr * 1.25f, g);
        // profondeur : la paroi du fond (bas-droite) reçoit la lumière
        g.setAlpha(255);
        g.setShader(new RadialGradient(x - rr * 0.25f, y - rr * 0.3f, rr * 1.3f,
                new int[]{0xFF000000, 0xFF020303, mix(0xFF000000, th.floorDark, 0.9f)}, new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, rr, g);
        g.setAlpha(255);
        g.setShader(null);
        g.setStyle(Paint.Style.STROKE);
        g.setStrokeWidth(cs * 0.035f);
        g.setColor(alpha(th.pitRim, 90));
        rf.set(x - rr, y - rr, x + rr, y + rr);
        c.drawArc(rf, 0, 180, false, g);
        g.setColor(0x90000000);
        c.drawArc(rf, 180, 180, false, g);
        g.setStyle(Paint.Style.FILL);
    }

    private void drawGoalBase(Canvas c, Paint g) {
        float x = X(lv.gx), y = Y(lv.gy);
        float ro = cs * 0.62f, ri = cs * 0.46f;
        g.setAlpha(255);
        g.setShader(new RadialGradient(x, y, cs * 1.4f, new int[]{alpha(th.accent, 90), alpha(th.accent, 0)}, null, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, cs * 1.4f, g);
        g.setAlpha(255);
        g.setShader(null);
        g.setColor(0x90000000);
        c.drawCircle(x + cs * 0.06f, y + cs * 0.1f, ro, g);
        g.setAlpha(255);
        g.setShader(new SweepGradient(x, y, new int[]{th.accent, 0xFFFFFFFF, mix(th.accent, 0xFF000000, 0.5f), 0xFFFFFFFF, th.accent, mix(th.accent, 0xFF000000, 0.4f), th.accent}, null));
        c.drawCircle(x, y, ro, g);
        g.setAlpha(255);
        g.setShader(new RadialGradient(x - ri * 0.25f, y - ri * 0.3f, ri * 1.3f, new int[]{0xFF000000, 0xFF000000, 0xFF101418}, new float[]{0, 0.6f, 1}, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, ri, g);
        g.setAlpha(255);
        g.setShader(null);
        g.setStyle(Paint.Style.STROKE);
        g.setStrokeWidth(cs * 0.03f);
        g.setColor(0x80000000);
        c.drawCircle(x, y, ri, g);
        g.setColor(0x60FFFFFF);
        c.drawCircle(x, y, ro, g);
        g.setStyle(Paint.Style.FILL);
    }

    private void drawWalls(Canvas c, Random r) {
        if (lv.walls.isEmpty()) return;
        Path all = new Path();
        Path tmp = new Path();
        for (float[] w : lv.walls) {
            tmp.reset();
            tmp.addRect(X(w[0]), Y(w[1]), X(w[0] + w[2]), Y(w[1] + w[3]), Path.Direction.CW);
            all.op(tmp, Path.Op.UNION);
        }
        CornerPathEffect corner = new CornerPathEffect(cs * 0.14f);
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        g.setPathEffect(corner);
        float hgt = cs * 0.12f;
        // ombre portée
        g.setColor(0xA0000000);
        g.setMaskFilter(new BlurMaskFilter(cs * 0.2f, BlurMaskFilter.Blur.NORMAL));
        c.save();
        c.translate(cs * 0.16f, cs * 0.26f);
        c.drawPath(all, g);
        c.restore();
        g.setMaskFilter(null);
        // flanc
        c.save();
        c.translate(0, hgt);
        g.setColor(th.wallSide);
        c.drawPath(all, g);
        c.restore();
        // dessus
        RectF bd = new RectF(ox, oy, ox + Level.COLS * cs, oy + Level.ROWS * cs);
        g.setAlpha(255);
        g.setShader(new LinearGradient(bd.left, bd.top, bd.right, bd.bottom, th.wallTopLight, th.wallTopDark, Shader.TileMode.CLAMP));
        c.drawPath(all, g);
        g.setAlpha(255);
        g.setShader(null);
        // texture
        c.save();
        c.clipPath(all);
        Paint d = new Paint(Paint.ANTI_ALIAS_FLAG);
        RectF bounds = new RectF();
        all.computeBounds(bounds, true);
        int n = (int) (bounds.width() * bounds.height() / (cs * cs) * 40);
        for (int i = 0; i < n; i++) {
            d.setColor(alpha(th.wallSpeck, 12 + r.nextInt(30)));
            c.drawCircle(bounds.left + r.nextFloat() * bounds.width(), bounds.top + r.nextFloat() * bounds.height(), cs * (0.008f + r.nextFloat() * 0.02f), d);
        }
        // veines (marbre / cristal)
        d.setStyle(Paint.Style.STROKE);
        d.setStrokeWidth(Math.max(1f, cs * 0.018f));
        for (int i = 0; i < (int) (n / 25f); i++) {
            float x = bounds.left + r.nextFloat() * bounds.width(), y = bounds.top + r.nextFloat() * bounds.height();
            d.setColor(alpha(th.wallSpeck, 25 + r.nextInt(30)));
            Path v = new Path();
            v.moveTo(x, y);
            v.quadTo(x + (r.nextFloat() - 0.5f) * cs, y + (r.nextFloat() - 0.5f) * cs, x + (r.nextFloat() - 0.5f) * cs * 2, y + (r.nextFloat() - 0.5f) * cs * 2);
            c.drawPath(v, d);
        }
        // reflet de l'arête haut-gauche
        Paint hl = new Paint(Paint.ANTI_ALIAS_FLAG);
        hl.setPathEffect(corner);
        hl.setStyle(Paint.Style.STROKE);
        hl.setStrokeWidth(cs * 0.08f);
        hl.setColor(0x70FFFFFF);
        c.translate(cs * 0.035f, cs * 0.035f);
        c.drawPath(all, hl);
        c.restore();
        // arête
        hl.setStrokeWidth(Math.max(1.5f, cs * 0.03f));
        hl.setColor(th.wallEdge);
        if (th.lava) {
            hl.setColor(0x90FF5A1A);
            hl.setStrokeWidth(cs * 0.14f);
            hl.setMaskFilter(new BlurMaskFilter(cs * 0.12f, BlurMaskFilter.Blur.NORMAL));
            c.drawPath(all, hl);
            hl.setMaskFilter(null);
            hl.setStrokeWidth(Math.max(1.5f, cs * 0.035f));
            hl.setColor(0xFFFF8A3A);
        }
        c.drawPath(all, hl);
    }

    // =====================================================================
    // Dessin d'une image du jeu
    // =====================================================================

    void draw(Canvas c, World w, float dt) {
        c.drawBitmap(board, 0, 0, null);
        float t = w.t;
        Level l = lv;

        drawGoalFx(c, t);
        if (th.lava) {
            for (int i = 0; i < l.pits.size(); i++) {
                float[] pit = l.pits.get(i);
                float x = X(pit[0]), y = Y(pit[1]);
                float pulse = 0.5f + 0.5f * (float) Math.sin(t * 2.2f + i * 1.7f);
                glow(c, x, y, cs * (0.9f + 0.2f * pulse), 0xFF6A1A, 0.35f + 0.25f * pulse);
                for (int k = 0; k < 2; k++) {
                    float ph = (t * 0.7f + i * 0.37f + k * 0.5f) % 1f;
                    float bx = x + (float) Math.cos(i * 3 + k * 2.1f) * pit[2] * cs * 0.45f;
                    float by = y + (float) Math.sin(i * 5 + k * 1.3f) * pit[2] * cs * 0.45f;
                    p.setColor(alpha(0xFFFFE08A, (int) (200 * (1 - ph))));
                    p.setStyle(Paint.Style.STROKE);
                    p.setStrokeWidth(cs * 0.025f);
                    c.drawCircle(bx, by, cs * 0.04f + ph * cs * 0.1f, p);
                    p.setStyle(Paint.Style.FILL);
                }
            }
        }
        drawBoosts(c, t);
        drawTeleFx(c, t);
        drawSpikes(c, t);
        drawStars(c, w, t);
        drawBumpers(c, w);
        drawMovers(c);
        drawRotors(c, t);
        drawLasers(c, t);
        drawMotes(c, t);

        // traînée et bille
        float bx = X(w.x), by = Y(w.y);
        if (w.state == World.PLAY && w.speed > 3f) {
            pushTrail(bx, by);
        } else if (trailN > 0) {
            trailN--;
        }
        for (int i = 0; i < trailN; i++) {
            float k = 1f - i / (float) trailX.length;
            glow(c, trailX[i], trailY[i], World.R * cs * 1.4f * k, 0x6FC8FF, 0.22f * k * Math.min(1f, w.speed / 10f));
        }
        if (w.state == World.PLAY || w.state == World.INTRO || w.state == World.FALLING || w.state == World.WINNING) {
            float s = w.scale;
            float r = World.R * cs;
            if (w.state == World.INTRO) {
                // la bille tombe du ciel : grande et transparente puis se pose
                float k = s;
                float drop = (1 - k) * (1 - k);
                c.save();
                drawMarbleShadowOnly(c, bx, by, r, k);
                drawMarble(c, bx, by - drop * cs * 1.5f, r * (1 + drop * 0.6f), w.rot, Math.min(1f, k * 1.6f), false);
                c.restore();
            } else if (w.state == World.FALLING && w.lavaDeath) {
                drawMarble(c, bx, by, r * Math.max(0.05f, s), w.rot, s, false);
                glow(c, bx, by, cs * 0.8f, 0xFF8A2A, (1 - s) * 0.8f);
            } else {
                drawMarble(c, bx, by, r * Math.max(0.05f, s), w.rot, Math.min(1f, 0.3f + s), s > 0.9f);
            }
            if (w.surfaceMud > 0 && w.speed > 0.6f && rnd.nextFloat() < 0.3f)
                spawn(w.x, w.y, 1, 0xFF6A4A2A, 0.6f, 0.5f, 0.06f, false);
            if (w.surfaceIce > 0 && w.speed > 4f && rnd.nextFloat() < 0.25f)
                spawn(w.x, w.y, 1, 0xFFE8F8FF, 0.8f, 0.4f, 0.04f, true);
        }
        updateDrawParticles(c, dt);
    }

    private void drawMarbleShadowOnly(Canvas c, float x, float y, float r, float k) {
        sp.setAlpha((int) (255 * k));
        float sr = r * 1.35f * (0.6f + 0.4f * k);
        rf.set(x + r * 0.28f - sr, y + r * 0.4f - sr * 0.85f, x + r * 0.28f + sr, y + r * 0.4f + sr * 0.85f);
        c.drawBitmap(ballShadow, null, rf, sp);
        sp.setAlpha(255);
    }

    private void pushTrail(float x, float y) {
        System.arraycopy(trailX, 0, trailX, 1, trailX.length - 1);
        System.arraycopy(trailY, 0, trailY, 1, trailY.length - 1);
        trailX[0] = x;
        trailY[0] = y;
        if (trailN < trailX.length) trailN++;
    }

    private void drawGoalFx(Canvas c, float t) {
        float x = X(lv.gx), y = Y(lv.gy);
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * 3f);
        glow(c, x, y, cs * (1.1f + 0.25f * pulse), th.accent, 0.35f + 0.25f * pulse);
        // rayons tournants
        c.save();
        c.translate(x, y);
        c.rotate(t * 25f);
        p.setColor(alpha(th.accent, 26));
        for (int i = 0; i < 6; i++) {
            path.reset();
            path.moveTo(0, 0);
            path.lineTo(cs * 1.6f, -cs * 0.12f);
            path.lineTo(cs * 1.6f, cs * 0.12f);
            path.close();
            c.drawPath(path, p);
            c.rotate(60);
        }
        c.restore();
        // étincelles aspirées
        for (int i = 0; i < 8; i++) {
            float ph = (t * 0.6f + i / 8f) % 1f;
            float a = i * 0.785f + ph * 4f;
            float rad = cs * (1.0f - 0.6f * ph);
            float sx = x + (float) Math.cos(a) * rad, sy = y + (float) Math.sin(a) * rad;
            p.setColor(alpha(0xFFFFFFFF, (int) (220 * Math.sin(ph * Math.PI))));
            c.drawCircle(sx, sy, cs * 0.035f, p);
        }
        glow(c, x, y, cs * 0.35f, 0xFFFFFF, 0.15f + 0.1f * pulse);
    }

    private void drawBoosts(Canvas c, float t) {
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(cs * 0.07f);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeJoin(Paint.Join.ROUND);
        for (int[] b : lv.boosts) {
            float x = X(b[0] + 0.5f), y = Y(b[1] + 0.5f);
            c.save();
            c.clipRect(X(b[0]) + cs * 0.08f, Y(b[1]) + cs * 0.08f, X(b[0] + 1) - cs * 0.08f, Y(b[1] + 1) - cs * 0.08f);
            c.translate(x, y);
            c.rotate(b[2] * 90);
            c.scale(cs, cs);
            p.setStrokeWidth(0.07f);
            for (int k = 0; k < 3; k++) {
                float ph = (t * 1.6f + k / 3f) % 1f;
                float off = 0.5f - ph;
                float a = (float) Math.sin(ph * Math.PI);
                p.setColor(alpha(th.accent, (int) (255 * a)));
                c.save();
                c.translate(0, off);
                c.drawPath(chevron, p);
                c.restore();
            }
            c.restore();
        }
        p.setStyle(Paint.Style.FILL);
        p.setStrokeCap(Paint.Cap.BUTT);
    }

    private void drawTeleFx(Canvas c, float t) {
        if (lv.teleA == null) return;
        float[][] tp = {lv.teleA, lv.teleB};
        for (int j = 0; j < 2; j++) {
            float x = X(tp[j][0]), y = Y(tp[j][1]);
            glow(c, x, y, cs * 0.9f, 0xB98CFF, 0.45f + 0.15f * (float) Math.sin(t * 4 + j));
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(cs * 0.05f);
            p.setStrokeCap(Paint.Cap.ROUND);
            rf.set(x - cs * 0.34f, y - cs * 0.34f, x + cs * 0.34f, y + cs * 0.34f);
            float rot = (j == 0 ? 1 : -1) * t * 200f;
            p.setColor(0xFFE6DAFF);
            for (int k = 0; k < 3; k++) c.drawArc(rf, rot + k * 120, 70, false, p);
            rf.inset(cs * 0.1f, cs * 0.1f);
            p.setColor(0xFFB98CFF);
            for (int k = 0; k < 3; k++) c.drawArc(rf, -rot * 1.4f + k * 120 + 40, 60, false, p);
            p.setStyle(Paint.Style.FILL);
            p.setStrokeCap(Paint.Cap.BUTT);
            for (int k = 0; k < 6; k++) {
                float ph = (t * 0.8f + k / 6f) % 1f;
                float a = k * 1.047f + ph * 5f * (j == 0 ? 1 : -1);
                float rad = cs * 0.42f * (1 - ph);
                p.setColor(alpha(0xFFFFFFFF, (int) (230 * (1 - ph))));
                c.drawCircle(x + (float) Math.cos(a) * rad, y + (float) Math.sin(a) * rad, cs * 0.03f, p);
            }
        }
    }

    private void ensureSpikeSprite() {
        int size = Math.max(16, (int) cs);
        if (spikeSprite != null && spikeSprite.getWidth() == size) return;
        spikeSprite = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(spikeSprite);
        Paint g = new Paint(Paint.ANTI_ALIAS_FLAG);
        float s = size;
        for (int k = 0; k < 4; k++) {
            float cx = s * (k % 2 == 0 ? 0.3f : 0.7f), cy = s * (k < 2 ? 0.3f : 0.7f), h = s * 0.19f;
            float ax = cx - s * 0.03f, ay = cy - s * 0.04f; // pointe légèrement décalée (perspective)
            g.setColor(0x60000000);
            c.drawCircle(cx + s * 0.05f, cy + s * 0.07f, h, g);
            int[] cols = {0xFFF2F6FA, 0xFFC4CCD4, 0xFF6C7682, 0xFF4A525C};
            float[][] faces = {
                    {cx - h, cy - h, cx + h, cy - h},
                    {cx - h, cy - h, cx - h, cy + h},
                    {cx + h, cy - h, cx + h, cy + h},
                    {cx - h, cy + h, cx + h, cy + h}};
            for (int f = 0; f < 4; f++) {
                Path tri = new Path();
                tri.moveTo(ax, ay);
                tri.lineTo(faces[f][0], faces[f][1]);
                tri.lineTo(faces[f][2], faces[f][3]);
                tri.close();
                g.setColor(cols[f]);
                c.drawPath(tri, g);
            }
        }
    }

    private void drawSpikes(Canvas c, float t) {
        if (lv.spikes.isEmpty()) return;
        ensureSpikeSprite();
        for (int i = 0; i < lv.spikes.size(); i++) {
            int[] s = lv.spikes.get(i);
            float h = lv.spikeHeight(i, t);
            float warn = lv.spikeWarning(i, t);
            float x = X(s[0] + 0.5f), y = Y(s[1] + 0.5f);
            if (warn > 0) glow(c, x, y, cs * 0.7f, 0xFF3030, warn * 0.6f * (0.6f + 0.4f * (float) Math.sin(t * 30)));
            if (h > 0.01f) {
                float half = cs * 0.5f * (0.55f + 0.45f * h);
                sp.setAlpha((int) (255 * Math.min(1f, h * 2)));
                rf.set(x - half, y - half, x + half, y + half);
                c.drawBitmap(spikeSprite, null, rf, sp);
                sp.setAlpha(255);
            }
        }
    }

    private void drawStars(Canvas c, World w, float t) {
        for (int i = 0; i < lv.stars.size(); i++) {
            if (w.starTaken[i]) continue;
            float[] s = lv.stars.get(i);
            float x = X(s[0]), y = Y(s[1]);
            float bob = (float) Math.sin(t * 3 + i) * cs * 0.05f;
            glow(c, x, y + bob, cs * 0.75f, 0xFFD43B, 0.55f);
            // ombre
            c.save();
            c.translate(x + cs * 0.08f, y + cs * 0.14f);
            c.scale(cs * 0.3f, cs * 0.3f);
            p.setColor(0x50000000);
            c.drawPath(starPath, p);
            c.restore();
            c.save();
            c.translate(x, y + bob - cs * 0.04f);
            c.rotate((float) Math.sin(t * 2 + i) * 12f);
            float sc = cs * 0.32f;
            c.scale(sc * (0.85f + 0.15f * (float) Math.cos(t * 2.4f + i)), sc);
            p.setAlpha(255);
            p.setShader(starShader);
            c.drawPath(starPath, p);
            p.setAlpha(255);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.08f);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(0xFFB06A00);
            c.drawPath(starPath, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xB0FFFFFF);
            c.drawCircle(-0.18f, -0.3f, 0.12f, p);
            c.restore();
        }
    }

    private void drawBumpers(Canvas c, World w) {
        for (int i = 0; i < lv.bumpers.size(); i++) {
            float[] b = lv.bumpers.get(i);
            float x = X(b[0]), y = Y(b[1]);
            float f = w.bumperFlash[i];
            float r = b[2] * cs * (1 + 0.18f * f);
            p.setColor(0x70000000);
            c.drawCircle(x + cs * 0.1f, y + cs * 0.16f, r * 1.05f, p);
            glow(c, x, y, r * 2.4f, 0xFF4FB8, 0.25f + 0.6f * f);
            p.setColor(0xFF2A0624);
            c.drawCircle(x, y, r * 1.12f, p);
            c.save();
            c.translate(x, y);
            c.scale(r, r);
            p.setAlpha(255);
            p.setShader(bumperShader);
            c.drawCircle(0, 0, 1, p);
            p.setAlpha(255);
            p.setShader(null);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(0.12f);
            p.setColor(mix(0xFFFFC2EC, 0xFFFFFFFF, f));
            c.drawCircle(0, 0, 0.72f, p);
            p.setStyle(Paint.Style.FILL);
            p.setColor(0xD0FFFFFF);
            c.drawCircle(-0.35f, -0.4f, 0.16f, p);
            c.restore();
        }
    }

    private void drawMovers(Canvas c) {
        for (Level.Mover m : lv.movers) {
            float l = X(m.cx), tp = Y(m.cy), rr = X(m.cx + m.w), b = Y(m.cy + m.h);
            float rad = cs * 0.14f;
            p.setColor(0x50000000);
            c.drawRoundRect(l + cs * 0.12f, tp + cs * 0.22f, rr + cs * 0.2f, b + cs * 0.3f, rad, rad, p);
            p.setColor(0x40000000);
            c.drawRoundRect(l + cs * 0.06f, tp + cs * 0.12f, rr + cs * 0.12f, b + cs * 0.18f, rad, rad, p);
            p.setColor(th.wallSide);
            c.drawRoundRect(l, tp + cs * 0.12f, rr, b + cs * 0.12f, rad, rad, p);
            p.setAlpha(255);
            p.setShader(wallTopShader);
            c.drawRoundRect(l, tp, rr, b, rad, rad, p);
            p.setAlpha(255);
            p.setShader(null);
            // bandes de danger
            c.save();
            rf.set(l + cs * 0.12f, tp + cs * 0.12f, rr - cs * 0.12f, b - cs * 0.12f);
            c.clipRect(rf);
            p.setColor(0x3A000000);
            c.drawRect(rf, p);
            p.setColor(alpha(th.accent, 170));
            p.setStrokeWidth(cs * 0.09f);
            p.setStyle(Paint.Style.STROKE);
            for (float s = rf.left - rf.height(); s < rf.right; s += cs * 0.3f)
                c.drawLine(s, rf.bottom, s + rf.height(), rf.top, p);
            c.restore();
            p.setStrokeWidth(cs * 0.03f);
            p.setColor(0x80FFFFFF);
            c.drawRoundRect(l + cs * 0.02f, tp + cs * 0.02f, rr - cs * 0.02f, b - cs * 0.02f, rad, rad, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private void drawRotors(Canvas c, float t) {
        for (Level.Rotor ro : lv.rotors) {
            float x = X(ro.cx), y = Y(ro.cy);
            float a = (float) Math.toDegrees(ro.angle(t));
            float len = ro.len * cs;
            // traînée de mouvement
            p.setColor(0x22FF5050);
            rf.set(x - len, y - len, x + len, y + len);
            for (int k = 0; k < ro.arms; k++) {
                float ak = a + k * 360f / ro.arms;
                float sweep = 30f * Math.signum(ro.speed);
                c.drawArc(rf, ak - sweep, sweep, true, p);
            }
            // ombre des lames
            c.save();
            c.translate(x + cs * 0.12f, y + cs * 0.2f);
            c.rotate(a);
            p.setColor(0x60000000);
            for (int k = 0; k < ro.arms; k++) {
                c.drawRoundRect(0, -cs * 0.1f, len, cs * 0.1f, cs * 0.1f, cs * 0.1f, p);
                c.rotate(360f / ro.arms);
            }
            c.restore();
            // lames
            c.save();
            c.translate(x, y);
            c.rotate(a);
            for (int k = 0; k < ro.arms; k++) {
                c.save();
                c.scale(1, cs * 0.1f);
                p.setAlpha(255);
                p.setShader(bladeShader);
                c.drawRoundRect(0, -1, len, 1, cs * 0.1f, 1, p);
                p.setAlpha(255);
                p.setShader(null);
                c.restore();
                p.setColor(0xFFFF3B3B);
                c.drawRect(cs * 0.3f, cs * 0.06f, len - cs * 0.1f, cs * 0.1f, p);
                glow(c, len - cs * 0.05f, 0, cs * 0.25f, 0xFF4040, 0.6f);
                c.rotate(360f / ro.arms);
            }
            c.restore();
            // moyeu
            c.save();
            c.translate(x, y);
            c.scale(cs * 0.3f, cs * 0.3f);
            p.setAlpha(255);
            p.setShader(hubShader);
            c.drawCircle(0, 0, 1, p);
            p.setAlpha(255);
            p.setShader(null);
            p.setColor(0xFF30353C);
            c.drawCircle(0, 0, 0.35f, p);
            c.restore();
        }
    }

    private void drawLasers(Canvas c, float t) {
        for (Level.Laser l : lv.lasers) {
            float x1 = X(l.x1), y1 = Y(l.y1), x2 = X(l.x2), y2 = Y(l.y2);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStyle(Paint.Style.STROKE);
            if (l.active(t)) {
                float flick = 0.85f + 0.15f * (float) Math.sin(t * 70);
                p.setColor(0x40FF2040);
                p.setStrokeWidth(cs * 0.42f * flick);
                c.drawLine(x1, y1, x2, y2, p);
                p.setColor(0x90FF3050);
                p.setStrokeWidth(cs * 0.18f * flick);
                c.drawLine(x1, y1, x2, y2, p);
                p.setColor(0xFFFFE0E8);
                p.setStrokeWidth(cs * 0.06f);
                c.drawLine(x1, y1, x2, y2, p);
            } else {
                float wv = l.warning(t);
                if (wv > 0 && ((int) (t * 18)) % 2 == 0) {
                    p.setColor(alpha(0xFFFF3050, (int) (60 + 120 * wv)));
                    p.setStrokeWidth(cs * 0.03f);
                    c.drawLine(x1, y1, x2, y2, p);
                }
            }
            p.setStyle(Paint.Style.FILL);
            p.setStrokeCap(Paint.Cap.BUTT);
            boolean on = l.active(t);
            drawEmitter(c, x1, y1, on);
            drawEmitter(c, x2, y2, on);
        }
    }

    private void drawEmitter(Canvas c, float x, float y, boolean on) {
        p.setColor(0x60000000);
        c.drawCircle(x + cs * 0.06f, y + cs * 0.1f, cs * 0.2f, p);
        c.save();
        c.translate(x, y);
        c.scale(cs * 0.2f, cs * 0.2f);
        p.setAlpha(255);
        p.setShader(emitterShader);
        c.drawCircle(0, 0, 1, p);
        p.setAlpha(255);
        p.setShader(null);
        p.setColor(on ? 0xFFFF4060 : 0xFF501018);
        c.drawCircle(0, 0, 0.45f, p);
        c.restore();
        if (on) glow(c, x, y, cs * 0.5f, 0xFF3050, 0.8f);
    }

    private void drawMotes(Canvas c, float t) {
        if (motes == null) return;
        int col = lv.world == 2 ? 0xFF9A3D : (lv.world == 1 ? 0xE8F6FF : 0xC8FF7D);
        for (int i = 0; i < motes.length; i++) {
            float[] m = motes[i];
            float x, y;
            if (lv.world == 2) { // braises qui montent
                y = m[1] - t * 0.6f * m[3];
                y = ((y % Level.ROWS) + Level.ROWS) % Level.ROWS;
                x = m[0] + (float) Math.sin(t * 1.3f + m[2]) * 0.4f;
            } else if (lv.world == 1) { // flocons qui descendent
                y = (m[1] + t * 0.45f * m[3]) % Level.ROWS;
                x = m[0] + (float) Math.sin(t * 0.9f + m[2]) * 0.6f;
            } else { // lucioles
                x = m[0] + (float) Math.sin(t * 0.4f * m[3] + m[2]) * 1.2f;
                y = m[1] + (float) Math.cos(t * 0.33f * m[3] + m[2] * 1.3f) * 1.2f;
            }
            float tw = 0.5f + 0.5f * (float) Math.sin(t * 2.5f * m[3] + m[2] * 3);
            glow(c, X(x), Y(y), cs * 0.22f * (0.6f + 0.4f * tw), col, 0.25f + 0.45f * tw);
        }
    }

    // =====================================================================
    // Particules
    // =====================================================================

    void spawn(float x, float y, int n, int color, float speed, float life, float size, boolean glowing) {
        for (int i = 0; i < n; i++) {
            if (pcount >= MAXP) return;
            int k = pcount++;
            double a = rnd.nextFloat() * Math.PI * 2;
            float s = speed * (0.3f + rnd.nextFloat());
            px[k] = x; py[k] = y;
            pvx[k] = (float) Math.cos(a) * s;
            pvy[k] = (float) Math.sin(a) * s;
            pmax[k] = plife[k] = life * (0.6f + rnd.nextFloat() * 0.8f);
            psize[k] = size * (0.6f + rnd.nextFloat() * 0.8f);
            pcol[k] = color;
            pglow[k] = glowing;
        }
    }

    void burst(float x, float y, int color, int n, float speed) {
        spawn(x, y, n, color, speed, 0.9f, 0.07f, true);
    }

    private void updateDrawParticles(Canvas c, float dt) {
        for (int i = 0; i < pcount; ) {
            plife[i] -= dt;
            if (plife[i] <= 0) {
                int last = --pcount;
                px[i] = px[last]; py[i] = py[last]; pvx[i] = pvx[last]; pvy[i] = pvy[last];
                plife[i] = plife[last]; pmax[i] = pmax[last]; psize[i] = psize[last]; pcol[i] = pcol[last]; pglow[i] = pglow[last];
                continue;
            }
            float damp = Math.max(0, 1 - 2.2f * dt);
            pvx[i] *= damp;
            pvy[i] *= damp;
            px[i] += pvx[i] * dt;
            py[i] += pvy[i] * dt;
            float k = plife[i] / pmax[i];
            float x = X(px[i]), y = Y(py[i]);
            if (pglow[i]) glow(c, x, y, psize[i] * cs * 4f * k, pcol[i], k * 0.7f);
            p.setColor(alpha(pcol[i], (int) (255 * Math.min(1f, k * 1.5f))));
            c.drawCircle(x, y, psize[i] * cs * (0.4f + 0.6f * k), p);
            i++;
        }
    }

    void clearParticles() {
        pcount = 0;
        trailN = 0;
    }
}
