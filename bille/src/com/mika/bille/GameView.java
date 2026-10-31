package com.mika.bille;

import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Vue unique du jeu : boucle, capteurs, écrans et interface. */
public final class GameView extends SurfaceView implements SurfaceHolder.Callback, SensorEventListener, Runnable, World.Listener {

    static final int MENU = 0, LEVELS = 1, SETTINGS = 2, GAME = 3;
    static final int OV_NONE = 0, OV_PAUSE = 1, OV_WIN = 2;

    static final int B_PLAY = 1, B_LEVELS = 2, B_SETTINGS = 3, B_BACK = 4, B_PAUSE = 5, B_RESUME = 6, B_RESTART = 7,
            B_CALIBRATE = 8, B_MENU = 9, B_NEXT = 10, B_SOUND = 11, B_VIB = 12, B_AUTOCAL = 13, B_SENS = 14, B_LEVEL = 100;

    private static final String[] TIPS = {
            "Inclinez le téléphone pour guider la bille jusqu'au trou doré",
            "Évitez les trous noirs… ils sont plus gourmands qu'ils n'en ont l'air",
            "Les champignons roses repoussent la bille, la boue la ralentit",
            "La glace ne freine presque pas, les flèches accélèrent",
            "Les pics sortent en rythme : une lueur rouge les annonce",
            "Entrez dans un portail violet pour ressortir par l'autre",
            "Les blocs rayés bougent… et peuvent vous écraser",
            "Traversez le pont de glace sans tomber dans le vide",
            "Les lasers s'allument par intermittence : attendez le bon moment",
            "Ne touchez pas les lames des rotors",
            "Prenez votre temps dans le labyrinthe",
            "Tout ce que vous avez appris, réuni dans une seule épreuve"};
    private static final String[] SENS_NAMES = {"Douce", "Normale", "Vive"};
    private static final float[] SENS_MULT = {1.7f, 2.4f, 3.2f};

    private final Activity act;
    private final SharedPreferences prefs;
    private final Sfx sfx = new Sfx();
    private final Vibrator vib;
    private final SensorManager sensors;
    private final Sensor accel;
    private final Renderer rd = new Renderer();
    private final Random rnd = new Random();

    private Thread thread;
    private volatile boolean running;
    private volatile int surfW, surfH;
    private volatile boolean needLayout;
    private int W, H;

    private volatile int screen = MENU;
    private int overlay = OV_NONE;
    private float overlayT, screenT, fade;
    private World world;
    private int levelIdx;
    private boolean calibratePending;
    private float calMsgT;
    private float shake, redFlash;
    private int winStars;
    private boolean newRecord;

    // capteur (repère écran : x vers la droite, y vers le bas), en m/s²
    private volatile float gx, gy;
    private boolean hasSensor;
    private float calX, calY;
    private boolean sound, vibrate, autoCal;
    private int sens;

    // bille du menu
    private float mbX = -1, mbY, mbVX, mbVY;
    private final float[] mbRot = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    private final float[] mbTrailX = new float[14], mbTrailY = new float[14];
    private Bitmap menuBg;
    private float menuT;

    // interface
    private static final class Btn {
        final RectF r = new RectF();
        int id;
        String label;
        boolean primary, enabled = true;
    }

    private final ArrayList<Btn> btns = new ArrayList<>();
    private int btnCount;
    private int pressedId = -1;
    private final ConcurrentLinkedQueue<float[]> input = new ConcurrentLinkedQueue<>();

    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rf = new RectF();
    private final Path star = new Path();
    private final Typeface black = Typeface.create("sans-serif-black", Typeface.NORMAL);
    private final Typeface bold = Typeface.create("sans-serif", Typeface.BOLD);
    private final Typeface regular = Typeface.create("sans-serif", Typeface.NORMAL);
    private final Typeface condensed = Typeface.create("sans-serif-condensed", Typeface.BOLD);
    private final Shader starUiShader;

    public GameView(Activity a) {
        super(a);
        act = a;
        prefs = a.getSharedPreferences("bille", Context.MODE_PRIVATE);
        vib = (Vibrator) a.getSystemService(Context.VIBRATOR_SERVICE);
        sensors = (SensorManager) a.getSystemService(Context.SENSOR_SERVICE);
        accel = sensors == null ? null : sensors.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sound = prefs.getBoolean("sound", true);
        vibrate = prefs.getBoolean("vib", true);
        autoCal = prefs.getBoolean("autocal", true);
        sens = prefs.getInt("sens", 1);
        calX = prefs.getFloat("calX", 0);
        calY = prefs.getFloat("calY", 0);
        sfx.enabled = sound;
        getHolder().addCallback(this);
        setFocusable(true);
        for (int i = 0; i < 10; i++) {
            double ang = -Math.PI / 2 + i * Math.PI / 5;
            float r = i % 2 == 0 ? 1f : 0.46f;
            float x = (float) Math.cos(ang) * r, y = (float) Math.sin(ang) * r;
            if (i == 0) star.moveTo(x, y); else star.lineTo(x, y);
        }
        star.close();
        starUiShader = new LinearGradient(-0.6f, -1f, 0.6f, 1f, new int[]{0xFFFFF7C2, 0xFFFFD43B, 0xFFE8A000}, new float[]{0, 0.45f, 1}, Shader.TileMode.CLAMP);
        new Thread(new Runnable() {
            @Override
            public void run() {
                sfx.load(act);
            }
        }).start();
    }

    // =====================================================================
    // Cycle de vie
    // =====================================================================

    public void resume() {
        if (accel != null) {
            hasSensor = sensors.registerListener(this, accel, SensorManager.SENSOR_DELAY_GAME);
        }
    }

    public void pause() {
        if (sensors != null) sensors.unregisterListener(this);
        if (screen == GAME && overlay == OV_NONE) input.add(new float[]{-2, 0, 0});
    }

    public void destroy() {
        sfx.release();
    }

    /** Retour Android. Renvoie false si l'activité doit se fermer. */
    public boolean back() {
        if (screen == MENU) return false;
        input.add(new float[]{-1, 0, 0});
        return true;
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        running = true;
        thread = new Thread(this, "jeu");
        thread.start();
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        surfW = width;
        surfH = height;
        needLayout = true;
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        running = false;
        if (thread != null) {
            try {
                thread.join(1500);
            } catch (InterruptedException ignored) {
            }
        }
        thread = null;
    }

    // =====================================================================
    // Capteur
    // =====================================================================

    @Override
    public void onSensorChanged(SensorEvent e) {
        float x = e.values[0], y = e.values[1];
        int rot = Surface.ROTATION_0;
        try {
            WindowManager wm = act.getWindowManager();
            rot = wm.getDefaultDisplay().getRotation();
        } catch (Exception ignored) {
        }
        float sx, sy;
        switch (rot) {
            case Surface.ROTATION_90: sx = y; sy = x; break;
            case Surface.ROTATION_180: sx = x; sy = -y; break;
            case Surface.ROTATION_270: sx = -y; sy = -x; break;
            default: sx = -x; sy = y; break;
        }
        gx += (sx - gx) * 0.4f;
        gy += (sy - gy) * 0.4f;
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private float tiltX() {
        float v = gx - calX;
        return Math.abs(v) < 0.12f ? 0 : v;
    }

    private float tiltY() {
        float v = gy - calY;
        return Math.abs(v) < 0.12f ? 0 : v;
    }

    private void calibrate() {
        calX = gx;
        calY = gy;
        prefs.edit().putFloat("calX", calX).putFloat("calY", calY).apply();
        calMsgT = 1.8f;
    }

    // =====================================================================
    // Entrées tactiles
    // =====================================================================

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_MOVE || a == MotionEvent.ACTION_CANCEL) {
            input.add(new float[]{a, e.getX(), e.getY()});
        }
        return true;
    }

    private void processInput() {
        float[] ev;
        while ((ev = input.poll()) != null) {
            int a = (int) ev[0];
            if (a == -1) { onBack(); continue; }
            if (a == -2) { if (screen == GAME && overlay == OV_NONE && world != null && world.state != World.WON) openPause(); continue; }
            Btn b = hit(ev[1], ev[2]);
            if (a == MotionEvent.ACTION_DOWN) {
                pressedId = b == null ? -1 : b.id;
            } else if (a == MotionEvent.ACTION_MOVE) {
                if (b == null || b.id != pressedId) pressedId = -1;
            } else if (a == MotionEvent.ACTION_UP) {
                if (b != null && b.id == pressedId && b.enabled) click(b.id);
                pressedId = -1;
            } else {
                pressedId = -1;
            }
        }
    }

    private Btn hit(float x, float y) {
        for (int i = btnCount - 1; i >= 0; i--) {
            Btn b = btns.get(i);
            if (b.r.contains(x, y)) return b;
        }
        return null;
    }

    private void click(int id) {
        sfx.play(Sfx.CLICK, 0.6f);
        buzz(8, 60);
        if (id >= B_LEVEL) {
            startLevel(id - B_LEVEL);
            return;
        }
        switch (id) {
            case B_PLAY: startLevel(Math.min(unlocked() - 1, Levels.count() - 1)); break;
            case B_LEVELS: go(LEVELS); break;
            case B_SETTINGS: go(SETTINGS); break;
            case B_BACK: go(MENU); break;
            case B_PAUSE: openPause(); break;
            case B_RESUME: overlay = OV_NONE; break;
            case B_RESTART: startLevel(levelIdx); break;
            case B_CALIBRATE: calibrate(); break;
            case B_MENU: go(MENU); break;
            case B_NEXT:
                if (levelIdx + 1 < Levels.count()) startLevel(levelIdx + 1); else go(LEVELS);
                break;
            case B_SOUND:
                sound = !sound;
                sfx.enabled = sound;
                prefs.edit().putBoolean("sound", sound).apply();
                break;
            case B_VIB:
                vibrate = !vibrate;
                prefs.edit().putBoolean("vib", vibrate).apply();
                buzz(40, 200);
                break;
            case B_AUTOCAL:
                autoCal = !autoCal;
                prefs.edit().putBoolean("autocal", autoCal).apply();
                break;
            case B_SENS:
                sens = (sens + 1) % 3;
                prefs.edit().putInt("sens", sens).apply();
                break;
            default: break;
        }
    }

    private void onBack() {
        if (screen == GAME) {
            if (overlay == OV_NONE) openPause();
            else if (overlay == OV_PAUSE) overlay = OV_NONE;
            else go(LEVELS);
        } else {
            go(MENU);
        }
    }

    private void openPause() {
        overlay = OV_PAUSE;
        overlayT = 0;
    }

    private void go(int s) {
        screen = s;
        screenT = 0;
        fade = 1f;
        overlay = OV_NONE;
        if (s != GAME) world = null;
    }

    // =====================================================================
    // Progression
    // =====================================================================

    private int unlocked() {
        return Math.max(1, Math.min(Levels.count(), prefs.getInt("unlocked", 1)));
    }

    private int starsOf(int i) {
        return prefs.getInt("stars" + i, 0);
    }

    private float bestOf(int i) {
        return prefs.getFloat("best" + i, 0);
    }

    private int totalStars() {
        int s = 0;
        for (int i = 0; i < Levels.count(); i++) s += starsOf(i);
        return s;
    }

    private void startLevel(int i) {
        levelIdx = i;
        world = new World(Levels.get(i));
        world.listener = this;
        if (W > 0) rd.setup(world.lv, W, H, hudHeight(), W * 0.03f);
        rd.clearParticles();
        screen = GAME;
        overlay = OV_NONE;
        screenT = 0;
        fade = 1f;
        calibratePending = autoCal;
        shake = 0;
        redFlash = 0;
    }

    private float hudHeight() {
        return W * 0.2f;
    }

    // =====================================================================
    // Événements du monde
    // =====================================================================

    @Override
    public void onWallHit(float speed) {
        float v = Math.min(1f, (speed - 1.4f) / 9f);
        sfx.play(Sfx.BUMP, 0.25f + 0.75f * v, 0.85f + rnd.nextFloat() * 0.3f);
        if (speed > 4f) buzz(12, (int) (40 + 160 * v));
        if (speed > 6f) rd.spawn(world.x, world.y, 4, 0xFFFFFFFF, 2f, 0.3f, 0.03f, true);
    }

    @Override
    public void onStar(float x, float y) {
        sfx.play(Sfx.STAR, 0.9f);
        rd.burst(x, y, 0xFFFFD43B, 26, 5f);
        rd.spawn(x, y, 10, 0xFFFFFFFF, 3f, 0.6f, 0.04f, true);
        buzz(20, 120);
    }

    @Override
    public void onBumper(float x, float y) {
        sfx.play(Sfx.BUMPER, 0.8f, 0.9f + rnd.nextFloat() * 0.25f);
        rd.burst(x, y, 0xFFFF6FC8, 12, 4f);
        buzz(25, 180);
    }

    @Override
    public void onFall(float x, float y) {
        boolean lava = world.lv.world == 2;
        sfx.play(lava ? Sfx.LAVA : Sfx.FALL, 0.9f);
        if (lava) rd.burst(x, y, 0xFFFF7A1A, 30, 4f);
        shake = 0.25f;
        buzz(70, 200);
    }

    @Override
    public void onDeath(float x, float y, int cause) {
        sfx.play(Sfx.DEATH, 1f);
        rd.spawn(x, y, 36, 0xFF6FC8FF, 7f, 0.8f, 0.06f, true);
        rd.spawn(x, y, 14, 0xFFFFFFFF, 5f, 0.5f, 0.04f, false);
        rd.spawn(x, y, 12, 0xFFFFB347, 4f, 0.7f, 0.04f, false);
        if (cause == World.DEATH_LASER) rd.burst(x, y, 0xFFFF3050, 16, 3f);
        shake = 0.45f;
        redFlash = 1f;
        buzz(140, 255);
    }

    @Override
    public void onTeleport(float x1, float y1, float x2, float y2) {
        sfx.play(Sfx.TELEPORT, 0.9f);
        rd.burst(x1, y1, 0xFFB98CFF, 18, 4f);
        rd.burst(x2, y2, 0xFFE6DAFF, 18, 4f);
        buzz(30, 120);
    }

    @Override
    public void onWin() {
        sfx.play(Sfx.WIN, 1f);
        winStars = world.stars;
        int i = levelIdx;
        SharedPreferences.Editor ed = prefs.edit();
        if (winStars > starsOf(i)) ed.putInt("stars" + i, winStars);
        float best = bestOf(i);
        newRecord = best <= 0 || world.time < best;
        if (newRecord) ed.putFloat("best" + i, world.time);
        if (unlocked() < i + 2) ed.putInt("unlocked", Math.min(Levels.count(), i + 2));
        ed.apply();
        overlay = OV_WIN;
        overlayT = 0;
        int[] cols = {0xFFFFD43B, 0xFF7FE8FF, 0xFFFF6FC8, 0xFF7DFFB8, 0xFFFFFFFF};
        for (int k = 0; k < cols.length; k++) rd.spawn(world.lv.gx, world.lv.gy, 16, cols[k], 9f, 1.4f, 0.06f, true);
        buzz(60, 160);
    }

    private void buzz(int ms, int amp) {
        if (!vibrate || vib == null) return;
        try {
            if (Build.VERSION.SDK_INT >= 26) vib.vibrate(VibrationEffect.createOneShot(ms, Math.max(1, Math.min(255, amp))));
            else vib.vibrate(ms);
        } catch (Exception ignored) {
        }
    }

    // =====================================================================
    // Boucle
    // =====================================================================

    @Override
    public void run() {
        long last = System.nanoTime();
        while (running) {
            long now = System.nanoTime();
            float dt = Math.min(0.05f, (now - last) / 1e9f);
            last = now;
            if (needLayout) layout();
            if (W <= 0) {
                sleep(16);
                continue;
            }
            processInput();
            update(dt);
            Surface surface = getHolder().getSurface();
            Canvas c = null;
            boolean hw = false;
            try {
                if (Build.VERSION.SDK_INT >= 23) {
                    try {
                        c = surface.lockHardwareCanvas();
                        hw = true;
                    } catch (Exception e) {
                        c = null;
                    }
                }
                if (c == null) c = getHolder().lockCanvas();
                if (c == null) {
                    sleep(16);
                    continue;
                }
                draw(c, dt);
            } catch (Exception e) {
                // on continue : une image ratée ne doit pas arrêter le jeu
            } finally {
                if (c != null) {
                    try {
                        if (hw) surface.unlockCanvasAndPost(c);
                        else getHolder().unlockCanvasAndPost(c);
                    } catch (Exception ignored) {
                    }
                }
            }
            if (!hw) {
                long spent = (System.nanoTime() - now) / 1000000;
                if (spent < 15) sleep(15 - spent);
            }
        }
    }

    private void layout() {
        needLayout = false;
        W = surfW;
        H = surfH;
        if (W > 0 && H > 0) {
            if (menuBg != null) menuBg.recycle();
            menuBg = Renderer.background(W, H, Theme.of(0), 7);
            if (world != null) rd.setup(world.lv, W, H, hudHeight(), W * 0.03f);
            mbX = -1;
        }
    }

    // ---------- accès pour les captures d'écran de contrôle (tests) ----------

    void debugFrame(Canvas c, int w, int h, float dt) {
        if (w != W || h != H) {
            surfW = w;
            surfH = h;
            layout();
        }
        processInput();
        update(dt);
        draw(c, dt);
    }

    void debugTilt(float x, float y) {
        gx = x;
        gy = y;
        hasSensor = true;
    }

    void debugClick(int id) {
        click(id);
    }

    World debugWorld() {
        return world;
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {
        }
    }

    private void update(float dt) {
        screenT += dt;
        overlayT += dt;
        menuT += dt;
        fade = Math.max(0, fade - dt * 3f);
        shake = Math.max(0, shake - dt);
        redFlash = Math.max(0, redFlash - dt * 2.5f);
        calMsgT = Math.max(0, calMsgT - dt);
        if (screen == GAME && world != null) {
            if (overlay != OV_PAUSE) {
                float m = SENS_MULT[sens];
                world.update(dt, tiltX() * m, tiltY() * m);
                if (calibratePending && world.state == World.PLAY) {
                    calibratePending = false;
                    calibrate();
                }
            }
        } else {
            updateMenuBall(dt);
        }
    }

    private void updateMenuBall(float dt) {
        float r = W * 0.075f;
        if (mbX < 0) {
            mbX = W * 0.5f;
            mbY = H * 0.62f;
            mbVX = W * 0.3f;
            mbVY = -W * 0.2f;
        }
        float ax = tiltX() * W * 0.12f, ay = tiltY() * W * 0.12f;
        if (!hasSensor || (Math.abs(tiltX()) < 0.01f && Math.abs(tiltY()) < 0.01f)) {
            ax = (float) Math.sin(menuT * 0.7f) * W * 0.25f;
            ay = (float) Math.cos(menuT * 0.53f) * W * 0.25f;
        }
        mbVX += ax * dt;
        mbVY += ay * dt;
        mbVX *= 1 - 0.4f * dt;
        mbVY *= 1 - 0.4f * dt;
        float ox = mbX, oy = mbY;
        mbX += mbVX * dt;
        mbY += mbVY * dt;
        if (mbX < r) { mbX = r; mbVX = Math.abs(mbVX) * 0.6f; }
        if (mbX > W - r) { mbX = W - r; mbVX = -Math.abs(mbVX) * 0.6f; }
        if (mbY < r) { mbY = r; mbVY = Math.abs(mbVY) * 0.6f; }
        if (mbY > H - r) { mbY = H - r; mbVY = -Math.abs(mbVY) * 0.6f; }
        World.roll(mbRot, mbX - ox, mbY - oy, r);
        System.arraycopy(mbTrailX, 0, mbTrailX, 1, mbTrailX.length - 1);
        System.arraycopy(mbTrailY, 0, mbTrailY, 1, mbTrailY.length - 1);
        mbTrailX[0] = mbX;
        mbTrailY[0] = mbY;
    }

    // =====================================================================
    // Dessin
    // =====================================================================

    private void draw(Canvas c, float dt) {
        btnCount = 0;
        if (screen == GAME && world != null) drawGame(c, dt);
        else {
            c.drawBitmap(menuBg, 0, 0, null);
            drawMenuBall(c);
            if (screen == LEVELS) drawLevels(c);
            else if (screen == SETTINGS) drawSettings(c);
            else drawMenu(c);
        }
        if (calMsgT > 0) {
            float a = Math.min(1f, calMsgT * 2);
            pill(c, "Calibré : cette position est maintenant « à plat »", W / 2f, H * 0.9f, a);
        }
        if (fade > 0) {
            fp.setAlpha(255);
            fp.setShader(null);
            fp.setColor(Renderer.alpha(0xFF000000, (int) (255 * fade)));
            c.drawRect(0, 0, W, H, fp);
        }
    }

    private void drawMenuBall(Canvas c) {
        float r = W * 0.075f;
        rd.ensureSprites(r);
        for (int i = mbTrailX.length - 1; i > 0; i--) {
            float k = 1f - i / (float) mbTrailX.length;
            rd.glow(c, mbTrailX[i], mbTrailY[i], r * 1.5f * k, 0x6FC8FF, 0.12f * k);
        }
        rd.drawMarble(c, mbX, mbY, r, mbRot, 1f, true);
    }

    private void drawMenu(Canvas c) {
        float cx = W / 2f;
        float ty = H * 0.25f;
        // titre
        tp.setTypeface(black);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTextSize(W * 0.25f);
        tp.setLetterSpacing(0.04f);
        tp.setAlpha(255);
        tp.setShader(null);
        tp.setColor(0x90000000);
        c.drawText("BILLE", cx + W * 0.01f, ty + W * 0.018f, tp);
        float bounce = (float) Math.sin(menuT * 2) * W * 0.006f;
        tp.setAlpha(255);
        tp.setShader(new LinearGradient(0, ty - W * 0.2f, 0, ty, new int[]{0xFFFFF7D6, 0xFFFFD15C, 0xFFE8890C}, null, Shader.TileMode.CLAMP));
        c.drawText("BILLE", cx, ty + bounce, tp);
        tp.setAlpha(255);
        tp.setShader(null);
        tp.setTypeface(condensed);
        tp.setTextSize(W * 0.075f);
        tp.setLetterSpacing(0.5f);
        tp.setColor(0xFF7DFFB8);
        c.drawText("ODYSSÉE", cx + W * 0.018f, ty + W * 0.12f, tp);
        tp.setLetterSpacing(0f);
        tp.setTypeface(regular);
        tp.setTextSize(W * 0.04f);
        tp.setColor(0xB0FFFFFF);
        c.drawText("Inclinez. Roulez. Survivez.", cx, ty + W * 0.21f, tp);

        float bw = W * 0.62f, bh = W * 0.15f, gap = W * 0.04f;
        float y0 = H * 0.5f;
        int u = unlocked();
        boolean fresh = u == 1 && starsOf(0) == 0;
        addButton(c, B_PLAY, fresh ? "Jouer" : "Continuer · niveau " + Math.min(u, Levels.count()), cx - bw / 2, y0, bw, bh, true);
        addButton(c, B_LEVELS, "Niveaux", cx - bw / 2, y0 + bh + gap, bw, bh, false);
        addButton(c, B_SETTINGS, "Réglages", cx - bw / 2, y0 + 2 * (bh + gap), bw, bh, false);

        // total d'étoiles
        float sy = y0 + 3 * (bh + gap) + W * 0.06f;
        drawStarIcon(c, cx - W * 0.12f, sy - W * 0.015f, W * 0.035f, true, 1f);
        tp.setTypeface(bold);
        tp.setTextSize(W * 0.05f);
        tp.setColor(0xFFFFFFFF);
        tp.setTextAlign(Paint.Align.LEFT);
        c.drawText(totalStars() + " / " + Levels.count() * 3, cx - W * 0.07f, sy, tp);
        if (!hasSensor) {
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(W * 0.035f);
            tp.setColor(0xFFFF8A8A);
            c.drawText("Aucun accéléromètre détecté sur cet appareil", cx, H * 0.95f, tp);
        }
    }

    private void drawLevels(Canvas c) {
        float m = W * 0.06f;
        header(c, "Niveaux");
        int u = unlocked();
        float gap = W * 0.03f;
        float tile = (W - 2 * m - 3 * gap) / 4f;
        float y = W * 0.3f;
        float sectionH = tile + W * 0.13f;
        float avail = H - y - W * 0.05f;
        if (sectionH * 3 > avail) {
            tile = Math.max(W * 0.12f, tile * avail / (sectionH * 3));
            sectionH = tile + W * 0.13f;
        }
        float rowW = tile * 4 + gap * 3;
        float x0 = (W - rowW) / 2f;
        for (int wld = 0; wld < 3; wld++) {
            Theme th = Theme.of(wld);
            tp.setTypeface(condensed);
            tp.setTextAlign(Paint.Align.LEFT);
            tp.setTextSize(W * 0.045f);
            tp.setLetterSpacing(0.08f);
            tp.setColor(th.accent);
            c.drawText(Levels.WORLDS[wld].toUpperCase(Locale.FRANCE), x0, y + W * 0.05f, tp);
            tp.setLetterSpacing(0);
            float ty = y + W * 0.08f;
            for (int k = 0; k < 4; k++) {
                int i = wld * 4 + k;
                float tx = x0 + k * (tile + gap);
                boolean open = i < u;
                Btn b = addHit(B_LEVEL + i, tx, ty, tile, tile);
                b.enabled = open;
                boolean pressed = pressedId == b.id;
                float ins = pressed ? tile * 0.04f : 0;
                rf.set(tx + ins, ty + ins, tx + tile - ins, ty + tile - ins);
                fp.setAlpha(255);
                fp.setShader(null);
                fp.setColor(0x70000000);
                c.drawRoundRect(rf.left + tile * 0.03f, rf.top + tile * 0.06f, rf.right + tile * 0.03f, rf.bottom + tile * 0.06f, tile * 0.18f, tile * 0.18f, fp);
                if (open) {
                    fp.setAlpha(255);
                    fp.setShader(new LinearGradient(rf.left, rf.top, rf.right, rf.bottom, th.floorLight, th.floorDark, Shader.TileMode.CLAMP));
                } else {
                    fp.setAlpha(255);
                    fp.setShader(new LinearGradient(rf.left, rf.top, rf.right, rf.bottom, 0xFF2A2F38, 0xFF14171C, Shader.TileMode.CLAMP));
                }
                c.drawRoundRect(rf, tile * 0.18f, tile * 0.18f, fp);
                fp.setAlpha(255);
                fp.setShader(null);
                fp.setStyle(Paint.Style.STROKE);
                fp.setStrokeWidth(W * 0.005f);
                fp.setColor(open ? Renderer.alpha(th.accent, i == u - 1 ? 255 : 120) : 0x30FFFFFF);
                c.drawRoundRect(rf, tile * 0.18f, tile * 0.18f, fp);
                fp.setStyle(Paint.Style.FILL);
                if (open) {
                    tp.setTypeface(black);
                    tp.setTextAlign(Paint.Align.CENTER);
                    tp.setTextSize(tile * 0.42f);
                    tp.setColor(0xFFFFFFFF);
                    c.drawText(String.valueOf(i + 1), rf.centerX(), rf.top + tile * 0.55f, tp);
                    int st = starsOf(i);
                    for (int s = 0; s < 3; s++)
                        drawStarIcon(c, rf.centerX() + (s - 1) * tile * 0.24f, rf.top + tile * 0.78f, tile * 0.1f, s < st, 1f);
                } else {
                    drawLock(c, rf.centerX(), rf.centerY(), tile * 0.22f);
                }
            }
            y += sectionH;
        }
    }

    private void drawSettings(Canvas c) {
        header(c, "Réglages");
        float m = W * 0.08f;
        float bw = W - 2 * m, bh = W * 0.14f, gap = W * 0.035f;
        float y = W * 0.34f;
        addButton(c, B_SOUND, "Son : " + (sound ? "activé" : "coupé"), m, y, bw, bh, false);
        y += bh + gap;
        addButton(c, B_VIB, "Vibrations : " + (vibrate ? "activées" : "coupées"), m, y, bw, bh, false);
        y += bh + gap;
        addButton(c, B_SENS, "Sensibilité : " + SENS_NAMES[sens], m, y, bw, bh, false);
        y += bh + gap;
        addButton(c, B_AUTOCAL, "Calibration au départ : " + (autoCal ? "oui" : "non"), m, y, bw, bh, false);
        y += bh + gap;
        addButton(c, B_CALIBRATE, "Calibrer maintenant", m, y, bw, bh, true);
        y += bh + gap * 1.5f;
        tp.setTypeface(regular);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTextSize(W * 0.036f);
        tp.setColor(0xA0FFFFFF);
        String[] lines = {
                "La calibration enregistre la position actuelle",
                "du téléphone comme « à plat ». Avec la calibration",
                "au départ, chaque niveau démarre dans la position",
                "où vous tenez le téléphone."};
        for (String s : lines) {
            c.drawText(s, W / 2f, y + W * 0.04f, tp);
            y += W * 0.05f;
        }
        // niveau à bulle
        float bx = W / 2f, by = Math.min(H - W * 0.18f, y + W * 0.16f), br = W * 0.11f;
        fp.setAlpha(255);
        fp.setShader(null);
        fp.setColor(0x40000000);
        c.drawCircle(bx, by, br, fp);
        fp.setStyle(Paint.Style.STROKE);
        fp.setStrokeWidth(W * 0.006f);
        fp.setColor(0x80FFFFFF);
        c.drawCircle(bx, by, br, fp);
        c.drawCircle(bx, by, br * 0.25f, fp);
        fp.setStyle(Paint.Style.FILL);
        float dx = Math.max(-1, Math.min(1, tiltX() / 5f)), dy = Math.max(-1, Math.min(1, tiltY() / 5f));
        float l = (float) Math.sqrt(dx * dx + dy * dy);
        if (l > 1) { dx /= l; dy /= l; }
        rd.glow(c, bx + dx * br * 0.75f, by + dy * br * 0.75f, br * 0.5f, 0x7DFFB8, 0.9f);
    }

    private void header(Canvas c, String title) {
        float m = W * 0.06f;
        float s = W * 0.12f;
        Btn b = addHit(B_BACK, m * 0.6f, W * 0.08f, s, s);
        fp.setAlpha(255);
        fp.setShader(null);
        fp.setColor(pressedId == B_BACK ? 0x50FFFFFF : 0x28FFFFFF);
        c.drawCircle(b.r.centerX(), b.r.centerY(), s / 2, fp);
        tp.setTypeface(bold);
        tp.setStyle(Paint.Style.STROKE);
        tp.setStrokeWidth(W * 0.012f);
        tp.setStrokeCap(Paint.Cap.ROUND);
        tp.setColor(0xFFFFFFFF);
        float cx = b.r.centerX(), cy = b.r.centerY();
        c.drawLine(cx + s * 0.08f, cy - s * 0.18f, cx - s * 0.1f, cy, tp);
        c.drawLine(cx - s * 0.1f, cy, cx + s * 0.08f, cy + s * 0.18f, tp);
        tp.setStyle(Paint.Style.FILL);
        tp.setTypeface(black);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTextSize(W * 0.09f);
        tp.setColor(0xFFFFFFFF);
        c.drawText(title, W / 2f, W * 0.175f, tp);
    }

    private void drawGame(Canvas c, float dt) {
        World w = world;
        c.save();
        if (shake > 0) {
            float a = shake * W * 0.03f;
            c.translate((rnd.nextFloat() - 0.5f) * a, (rnd.nextFloat() - 0.5f) * a);
        }
        rd.draw(c, w, overlay == OV_PAUSE ? 0 : dt);
        c.restore();
        if (redFlash > 0) {
            fp.setAlpha(255);
            fp.setShader(new RadialGradient(W / 2f, H / 2f, H * 0.7f, new int[]{0x00FF0000, Renderer.alpha(0xFFFF2020, (int) (150 * redFlash))}, null, Shader.TileMode.CLAMP));
            c.drawRect(0, 0, W, H, fp);
            fp.setAlpha(255);
            fp.setShader(null);
        }
        drawHud(c, w);

        // carton d'introduction
        if (w.introLen == World.FIRST_INTRO && w.t < 2.6f && overlay == OV_NONE) {
            float a = w.t < 1.8f ? 1f : 1f - (w.t - 1.8f) / 0.8f;
            float cy = H * 0.45f;
            tp.setTypeface(condensed);
            tp.setTextAlign(Paint.Align.CENTER);
            tp.setTextSize(W * 0.05f);
            tp.setLetterSpacing(0.3f);
            tp.setColor(Renderer.alpha(rd.th.accent, (int) (255 * a)));
            tp.setShadowLayer(W * 0.02f, 0, W * 0.005f, Renderer.alpha(0xFF000000, (int) (200 * a)));
            c.drawText("NIVEAU " + (levelIdx + 1), W / 2f, cy - W * 0.1f, tp);
            tp.setLetterSpacing(0);
            tp.setTypeface(black);
            tp.setTextSize(W * 0.11f);
            tp.setColor(Renderer.alpha(0xFFFFFFFF, (int) (255 * a)));
            c.drawText(w.lv.name, W / 2f, cy + W * 0.02f, tp);
            tp.clearShadowLayer();
        }
        if (w.t < 7f && overlay == OV_NONE && w.t > 0.8f) {
            float a = Math.min(1f, (w.t - 0.8f) * 3) * Math.min(1f, (7f - w.t) * 2);
            pill(c, TIPS[Math.min(levelIdx, TIPS.length - 1)], W / 2f, Math.max(hudHeight() + W * 0.02f, (hudHeight() + rd.oy - rd.cs * 0.34f) / 2f), a);
        }

        if (overlay == OV_PAUSE) drawPause(c);
        else if (overlay == OV_WIN) drawWin(c, w);
    }

    private void drawHud(Canvas c, World w) {
        float hud = hudHeight();
        float m = W * 0.045f;
        float pr = hud * 0.24f;
        float pcx = m + pr, pcy = hud * 0.52f;
        if (overlay == OV_NONE) {
            Btn b = addHit(B_PAUSE, pcx - pr * 1.4f, pcy - pr * 1.4f, pr * 2.8f, pr * 2.8f);
            b.enabled = w.state != World.WON;
        }
        fp.setAlpha(255);
        fp.setShader(null);
        fp.setColor(pressedId == B_PAUSE ? 0x60FFFFFF : 0x30FFFFFF);
        c.drawCircle(pcx, pcy, pr, fp);
        fp.setColor(0xFFFFFFFF);
        c.drawRoundRect(pcx - pr * 0.36f, pcy - pr * 0.4f, pcx - pr * 0.1f, pcy + pr * 0.4f, pr * 0.08f, pr * 0.08f, fp);
        c.drawRoundRect(pcx + pr * 0.1f, pcy - pr * 0.4f, pcx + pr * 0.36f, pcy + pr * 0.4f, pr * 0.08f, pr * 0.08f, fp);

        float tx = pcx + pr + m * 0.6f;
        tp.setTextAlign(Paint.Align.LEFT);
        tp.setTypeface(condensed);
        tp.setTextSize(W * 0.034f);
        tp.setLetterSpacing(0.12f);
        tp.setColor(Renderer.alpha(rd.th.accent, 220));
        c.drawText(("Niveau " + (levelIdx + 1) + " · " + Levels.WORLDS[w.lv.world]).toUpperCase(Locale.FRANCE), tx, pcy - hud * 0.08f, tp);
        tp.setLetterSpacing(0);
        tp.setTypeface(black);
        tp.setTextSize(W * 0.058f);
        tp.setColor(0xFFFFFFFF);
        c.drawText(w.lv.name, tx, pcy + hud * 0.17f, tp);

        tp.setTextAlign(Paint.Align.RIGHT);
        tp.setTypeface(bold);
        tp.setTextSize(W * 0.06f);
        tp.setColor(0xFFFFFFFF);
        c.drawText(formatTime(w.time), W - m, pcy - hud * 0.02f, tp);
        float sr = W * 0.022f;
        for (int s = 0; s < 3; s++) {
            drawStarIcon(c, W - m - sr - (2 - s) * sr * 2.6f, pcy + hud * 0.17f, sr, s < w.stars, 1f);
        }
        if (w.deaths > 0) {
            tp.setTypeface(regular);
            tp.setTextSize(W * 0.032f);
            tp.setColor(0xB0FFB0B0);
            c.drawText("chutes " + w.deaths, W - m - sr * 8.6f, pcy + hud * 0.2f, tp);
        }
    }

    private void drawPause(Canvas c) {
        fp.setAlpha(255);
        fp.setShader(null);
        fp.setColor(0xA0000000);
        c.drawRect(0, 0, W, H, fp);
        float pw = W * 0.8f, ph = W * 1.02f;
        float px = (W - pw) / 2, py = (H - ph) / 2;
        panel(c, px, py, pw, ph);
        tp.setTypeface(black);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTextSize(W * 0.09f);
        tp.setColor(0xFFFFFFFF);
        c.drawText("Pause", W / 2f, py + W * 0.15f, tp);
        float bw = pw - W * 0.12f, bh = W * 0.13f, gap = W * 0.03f;
        float bx = px + W * 0.06f, by = py + W * 0.22f;
        addButton(c, B_RESUME, "Reprendre", bx, by, bw, bh, true);
        addButton(c, B_RESTART, "Recommencer", bx, by + (bh + gap), bw, bh, false);
        addButton(c, B_CALIBRATE, "Recalibrer (position actuelle)", bx, by + 2 * (bh + gap), bw, bh, false);
        addButton(c, B_MENU, "Menu principal", bx, by + 3 * (bh + gap), bw, bh, false);
        tp.setTypeface(regular);
        tp.setTextSize(W * 0.032f);
        tp.setColor(0x90FFFFFF);
        c.drawText("Sensibilité : " + SENS_NAMES[sens] + " · modifiable dans Réglages", W / 2f, py + ph - W * 0.05f, tp);
    }

    private void drawWin(Canvas c, World w) {
        float k = Math.min(1f, overlayT * 3f);
        fp.setAlpha(255);
        fp.setShader(null);
        fp.setColor(Renderer.alpha(0xFF000000, (int) (150 * k)));
        c.drawRect(0, 0, W, H, fp);
        float pw = W * 0.84f, ph = W * 1.05f;
        float px = (W - pw) / 2, py = (H - ph) / 2 + (1 - k) * W * 0.2f;
        panel(c, px, py, pw, ph);
        boolean last = levelIdx + 1 >= Levels.count();
        tp.setTypeface(black);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTextSize(W * 0.1f);
        tp.setAlpha(255);
        tp.setShader(new LinearGradient(0, py + W * 0.06f, 0, py + W * 0.16f, 0xFFFFF7D6, 0xFFFFC23A, Shader.TileMode.CLAMP));
        c.drawText(last ? "Victoire !" : "Bravo !", W / 2f, py + W * 0.16f, tp);
        tp.setAlpha(255);
        tp.setShader(null);
        // étoiles
        float sy = py + W * 0.33f;
        for (int s = 0; s < 3; s++) {
            float appear = Math.max(0, Math.min(1f, (overlayT - 0.35f - s * 0.25f) * 4f));
            float size = W * 0.075f * (s == 1 ? 1.25f : 1f);
            float pop = appear < 1 ? appear * 1.3f : 1f;
            float sx = W / 2f + (s - 1) * W * 0.2f;
            float yy = sy - (s == 1 ? W * 0.03f : 0);
            drawStarIcon(c, sx, yy, size, false, 1f);
            if (s < winStars && appear > 0) {
                if (appear < 1) rd.glow(c, sx, yy, size * 3f, 0xFFD43B, 1 - appear);
                drawStarIcon(c, sx, yy, size * pop, true, 1f);
            }
        }
        tp.setTypeface(bold);
        tp.setTextSize(W * 0.048f);
        tp.setColor(0xFFFFFFFF);
        c.drawText("Temps  " + formatTime(w.time) + (newRecord ? "  · record !" : ""), W / 2f, py + W * 0.5f, tp);
        tp.setTypeface(regular);
        tp.setTextSize(W * 0.038f);
        tp.setColor(0xB0FFFFFF);
        String sub = "Étoiles " + winStars + "/3   ·   Chutes " + w.deaths;
        if (!newRecord && bestOf(levelIdx) > 0) sub += "   ·   Record " + formatTime(bestOf(levelIdx));
        c.drawText(sub, W / 2f, py + W * 0.57f, tp);
        if (last) {
            tp.setColor(0xFF7DFFB8);
            c.drawText("Vous avez terminé tous les niveaux !", W / 2f, py + W * 0.63f, tp);
        }
        float bw = pw - W * 0.12f, bh = W * 0.13f, gap = W * 0.03f;
        float bx = px + W * 0.06f, by = py + W * 0.67f;
        if (overlayT > 0.4f) {
            addButton(c, B_NEXT, last ? "Voir les niveaux" : "Niveau suivant", bx, by, bw, bh, true);
            float hw = (bw - gap) / 2;
            addButton(c, B_RESTART, "Rejouer", bx, by + bh + gap, hw, bh, false);
            addButton(c, B_MENU, "Menu", bx + hw + gap, by + bh + gap, hw, bh, false);
        }
    }

    // ---------- éléments d'interface ----------

    private void panel(Canvas c, float x, float y, float w, float h) {
        float r = W * 0.06f;
        fp.setAlpha(255);
        fp.setShader(null);
        fp.setColor(0x80000000);
        c.drawRoundRect(x + W * 0.01f, y + W * 0.025f, x + w + W * 0.01f, y + h + W * 0.025f, r, r, fp);
        fp.setAlpha(255);
        fp.setShader(new LinearGradient(x, y, x, y + h, 0xF2222B3D, 0xF20C1018, Shader.TileMode.CLAMP));
        c.drawRoundRect(x, y, x + w, y + h, r, r, fp);
        fp.setAlpha(255);
        fp.setShader(null);
        fp.setStyle(Paint.Style.STROKE);
        fp.setStrokeWidth(W * 0.004f);
        fp.setColor(0x40FFFFFF);
        c.drawRoundRect(x, y, x + w, y + h, r, r, fp);
        fp.setStyle(Paint.Style.FILL);
    }

    private Btn addHit(int id, float x, float y, float w, float h) {
        Btn b;
        if (btnCount < btns.size()) b = btns.get(btnCount);
        else {
            b = new Btn();
            btns.add(b);
        }
        btnCount++;
        b.id = id;
        b.r.set(x, y, x + w, y + h);
        b.enabled = true;
        b.label = null;
        b.primary = false;
        return b;
    }

    private void addButton(Canvas c, int id, String label, float x, float y, float w, float h, boolean primary) {
        Btn b = addHit(id, x, y, w, h);
        b.label = label;
        b.primary = primary;
        boolean pressed = pressedId == id;
        float ins = pressed ? h * 0.05f : 0;
        rf.set(x + ins, y + ins, x + w - ins, y + h - ins);
        float r = h / 2;
        fp.setAlpha(255);
        fp.setShader(null);
        fp.setColor(0x60000000);
        c.drawRoundRect(rf.left, rf.top + h * 0.08f, rf.right, rf.bottom + h * 0.08f, r, r, fp);
        if (primary) {
            fp.setAlpha(255);
            fp.setShader(new LinearGradient(0, rf.top, 0, rf.bottom, pressed ? 0xFFFFE08A : 0xFFFFE699, pressed ? 0xFFE08A10 : 0xFFF2A21F, Shader.TileMode.CLAMP));
            c.drawRoundRect(rf, r, r, fp);
            fp.setAlpha(255);
            fp.setShader(null);
            fp.setColor(0x60FFFFFF);
            c.drawRoundRect(rf.left + h * 0.12f, rf.top + h * 0.06f, rf.right - h * 0.12f, rf.top + h * 0.38f, r, r, fp);
        } else {
            fp.setColor(pressed ? 0x50FFFFFF : 0x24FFFFFF);
            c.drawRoundRect(rf, r, r, fp);
            fp.setStyle(Paint.Style.STROKE);
            fp.setStrokeWidth(W * 0.004f);
            fp.setColor(0x60FFFFFF);
            c.drawRoundRect(rf, r, r, fp);
            fp.setStyle(Paint.Style.FILL);
        }
        tp.setTypeface(bold);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTextSize(Math.min(h * 0.4f, W * 0.052f));
        while (tp.measureText(label) > w * 0.88f && tp.getTextSize() > 8) tp.setTextSize(tp.getTextSize() * 0.93f);
        tp.setColor(primary ? 0xFF3A2200 : 0xFFFFFFFF);
        c.drawText(label, rf.centerX(), rf.centerY() + tp.getTextSize() * 0.35f, tp);
    }

    private void pill(Canvas c, String text, float cx, float cy, float a) {
        if (a <= 0) return;
        tp.setTypeface(bold);
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setTextSize(W * 0.036f);
        while (tp.measureText(text) > W * 0.84f && tp.getTextSize() > 8) tp.setTextSize(tp.getTextSize() * 0.95f);
        float tw = tp.measureText(text);
        float h = tp.getTextSize() * 2.2f;
        rf.set(cx - tw / 2 - h * 0.6f, cy - h / 2, cx + tw / 2 + h * 0.6f, cy + h / 2);
        fp.setAlpha(255);
        fp.setShader(null);
        fp.setColor(Renderer.alpha(0xE0101420, (int) (225 * a)));
        c.drawRoundRect(rf, h / 2, h / 2, fp);
        fp.setStyle(Paint.Style.STROKE);
        fp.setStrokeWidth(W * 0.003f);
        fp.setColor(Renderer.alpha(0xFFFFFFFF, (int) (70 * a)));
        c.drawRoundRect(rf, h / 2, h / 2, fp);
        fp.setStyle(Paint.Style.FILL);
        tp.setColor(Renderer.alpha(0xFFFFFFFF, (int) (255 * a)));
        c.drawText(text, cx, cy + tp.getTextSize() * 0.35f, tp);
    }

    private void drawStarIcon(Canvas c, float x, float y, float size, boolean filled, float a) {
        c.save();
        c.translate(x, y);
        c.scale(size, size);
        if (filled) {
            fp.setAlpha(255);
            fp.setShader(starUiShader);
            fp.setAlpha((int) (255 * a));
            c.drawPath(star, fp);
            fp.setAlpha(255);
            fp.setShader(null);
            fp.setStyle(Paint.Style.STROKE);
            fp.setStrokeWidth(0.1f);
            fp.setStrokeJoin(Paint.Join.ROUND);
            fp.setColor(0xFFB06A00);
            c.drawPath(star, fp);
            fp.setStyle(Paint.Style.FILL);
        } else {
            fp.setAlpha(255);
            fp.setShader(null);
            fp.setColor(0x40000000);
            c.drawPath(star, fp);
            fp.setStyle(Paint.Style.STROKE);
            fp.setStrokeWidth(0.1f);
            fp.setStrokeJoin(Paint.Join.ROUND);
            fp.setColor(0x60FFFFFF);
            c.drawPath(star, fp);
            fp.setStyle(Paint.Style.FILL);
        }
        c.restore();
    }

    private void drawLock(Canvas c, float x, float y, float s) {
        fp.setAlpha(255);
        fp.setShader(null);
        fp.setStyle(Paint.Style.STROKE);
        fp.setStrokeWidth(s * 0.22f);
        fp.setColor(0x80FFFFFF);
        rf.set(x - s * 0.5f, y - s * 1.1f, x + s * 0.5f, y - s * 0.1f);
        c.drawArc(rf, 180, 180, false, fp);
        c.drawLine(x - s * 0.5f, y - s * 0.6f, x - s * 0.5f, y - s * 0.1f, fp);
        c.drawLine(x + s * 0.5f, y - s * 0.6f, x + s * 0.5f, y - s * 0.1f, fp);
        fp.setStyle(Paint.Style.FILL);
        c.drawRoundRect(x - s * 0.8f, y - s * 0.2f, x + s * 0.8f, y + s * 0.9f, s * 0.2f, s * 0.2f, fp);
        fp.setColor(0xFF1A1D24);
        c.drawCircle(x, y + s * 0.3f, s * 0.15f, fp);
    }

    private static String formatTime(float t) {
        int tenth = (int) (t * 10);
        int s = tenth / 10, d = tenth % 10;
        return String.format(Locale.FRANCE, "%d:%02d.%d", s / 60, s % 60, d);
    }
}
