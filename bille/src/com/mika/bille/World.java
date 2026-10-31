package com.mika.bille;

/** Physique de la bille et règles d'un niveau. Aucune dépendance Android. */
public final class World {

    public interface Listener {
        void onWallHit(float speed);
        void onStar(float x, float y);
        void onBumper(float x, float y);
        void onFall(float x, float y);
        void onDeath(float x, float y, int cause);
        void onTeleport(float x1, float y1, float x2, float y2);
        void onWin();
    }

    public static final int INTRO = 0, PLAY = 1, FALLING = 2, DYING = 3, WINNING = 4, WON = 5;
    public static final int DEATH_SPIKES = 1, DEATH_LASER = 2, DEATH_ROTOR = 3, DEATH_CRUSH = 4;
    public static final float R = 0.34f;
    static final float MAX_V = 15f;
    static final float FIRST_INTRO = 1.1f, RESPAWN_INTRO = 0.55f;

    public final Level lv;
    public Listener listener;

    public float t, time;
    public float x, y, vx, vy, speed;
    public int state = INTRO;
    public float stateT, introLen = FIRST_INTRO;
    public float scale = 0f;
    public float fx, fy;
    public boolean lavaDeath;
    public final boolean[] starTaken;
    public int stars, deaths;
    public final float[] bumperFlash;
    /** Matrice de rotation de la bille (roulement), lignes. */
    public final float[] rot = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    public float surfaceIce, surfaceMud;

    private int teleLock = -1;
    private float impact;

    public World(Level lv) {
        this.lv = lv;
        starTaken = new boolean[lv.stars.size()];
        bumperFlash = new float[lv.bumpers.size()];
        x = lv.sx;
        y = lv.sy;
        for (Level.Mover m : lv.movers) m.at(0);
    }

    public void update(float dt, float ax, float ay) {
        t += dt;
        for (int i = 0; i < bumperFlash.length; i++) bumperFlash[i] = Math.max(0, bumperFlash[i] - dt * 3.5f);
        for (Level.Mover m : lv.movers) m.at(t);
        stateT += dt;
        switch (state) {
            case INTRO: {
                float k = Math.min(1f, stateT / introLen);
                scale = k;
                if (stateT >= introLen) {
                    state = PLAY;
                    stateT = 0;
                    scale = 1f;
                }
                return;
            }
            case FALLING: {
                float k = Math.min(1f, stateT / 0.6f);
                float f = Math.min(1f, dt * 9f);
                x += (fx - x) * f;
                y += (fy - y) * f;
                scale = 1f - k * k;
                if (stateT > 0.85f) respawn();
                return;
            }
            case DYING:
                scale = 0f;
                if (stateT > 0.9f) respawn();
                return;
            case WINNING: {
                float k = Math.min(1f, stateT / 0.8f);
                float ang = stateT * 14f;
                float rad = (1f - k) * 0.25f;
                float tx = lv.gx + (float) Math.cos(ang) * rad, ty = lv.gy + (float) Math.sin(ang) * rad;
                float f = Math.min(1f, dt * 10f);
                x += (tx - x) * f;
                y += (ty - y) * f;
                scale = 1f - 0.75f * k;
                rollBy(vx * dt * (1 - k), vy * dt * (1 - k));
                if (stateT >= 0.9f) {
                    state = WON;
                    scale = 0f;
                    if (listener != null) listener.onWin();
                }
                return;
            }
            case WON:
                return;
            default:
                break;
        }

        time += dt;
        int n = (int) Math.ceil(dt / (1f / 300f));
        float h = dt / n;
        impact = 0;
        for (int i = 0; i < n && state == PLAY; i++) step(h, ax, ay);
        speed = (float) Math.sqrt(vx * vx + vy * vy);
        if (impact > 1.6f && listener != null) listener.onWallHit(impact);
    }

    private void step(float h, float ax, float ay) {
        char c = lv.cell(x, y);
        float fric = 0.85f;
        surfaceIce = c == '~' ? 1 : 0;
        surfaceMud = c == ':' ? 1 : 0;
        if (c == '~') { fric = 0.06f; }
        else if (c == ':') { fric = 5.5f; ax *= 0.55f; ay *= 0.55f; }
        else if (c == '^') ay -= 34f;
        else if (c == 'v') ay += 34f;
        else if (c == '<') ax -= 34f;
        else if (c == '>') ax += 34f;

        // attraction des trous
        for (float[] p : lv.pits) {
            float dx = p[0] - x, dy = p[1] - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < p[2] + R * 0.55f && d > 1e-4f) {
                ax += dx / d * 10f;
                ay += dy / d * 10f;
            }
        }
        // attraction et freinage près de l'arrivée
        {
            float dx = lv.gx - x, dy = lv.gy - y;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (d < 1.0f && d > 1e-4f) {
                float k = 1f - d;
                ax += dx / d * 9f * k;
                ay += dy / d * 9f * k;
                fric += 2.5f * k;
            }
        }

        vx += ax * h;
        vy += ay * h;
        float damp = Math.max(0f, 1f - fric * h);
        vx *= damp;
        vy *= damp;
        float s2 = vx * vx + vy * vy;
        if (s2 > MAX_V * MAX_V) {
            float s = (float) Math.sqrt(s2);
            vx *= MAX_V / s;
            vy *= MAX_V / s;
        }
        float px = x, py = y;
        x += vx * h;
        y += vy * h;

        // collisions
        for (Level.Mover m : lv.movers) collideRect(m.cx, m.cy, m.w, m.h, m.vx, m.vy, 0.35f);
        for (float[] w : lv.walls) collideRect(w[0], w[1], w[2], w[3], 0, 0, 0.45f);
        border();
        for (int i = 0; i < lv.bumpers.size(); i++) {
            float[] b = lv.bumpers.get(i);
            float dx = x - b[0], dy = y - b[1];
            float rr = R + b[2];
            float d2 = dx * dx + dy * dy;
            if (d2 < rr * rr && d2 > 1e-8f) {
                float d = (float) Math.sqrt(d2);
                float nx = dx / d, ny = dy / d;
                x = b[0] + nx * rr;
                y = b[1] + ny * rr;
                float vn = vx * nx + vy * ny;
                if (vn < 0) {
                    vx -= 2f * vn * nx;
                    vy -= 2f * vn * ny;
                }
                vx += nx * 6.5f;
                vy += ny * 6.5f;
                if (bumperFlash[i] < 0.6f && listener != null) listener.onBumper(b[0], b[1]);
                bumperFlash[i] = 1f;
            }
        }
        for (Level.Rotor r : lv.rotors) {
            float dx = x - r.cx, dy = y - r.cy;
            float rr = R + 0.3f;
            float d2 = dx * dx + dy * dy;
            if (d2 < rr * rr && d2 > 1e-8f) {
                float d = (float) Math.sqrt(d2);
                float nx = dx / d, ny = dy / d;
                x = r.cx + nx * rr;
                y = r.cy + ny * rr;
                float vn = vx * nx + vy * ny;
                if (vn < 0) {
                    vx -= 1.5f * vn * nx;
                    vy -= 1.5f * vn * ny;
                }
            }
        }

        rollBy(x - px, y - py);

        // étoiles
        for (int i = 0; i < starTaken.length; i++) {
            if (starTaken[i]) continue;
            float[] s = lv.stars.get(i);
            if (dist2(s[0], s[1]) < (R + 0.3f) * (R + 0.3f)) {
                starTaken[i] = true;
                stars++;
                if (listener != null) listener.onStar(s[0], s[1]);
            }
        }
        // téléporteurs
        if (lv.teleA != null) {
            if (teleLock == -1) {
                if (dist2(lv.teleA[0], lv.teleA[1]) < 0.33f * 0.33f) teleport(lv.teleA, lv.teleB, 1);
                else if (dist2(lv.teleB[0], lv.teleB[1]) < 0.33f * 0.33f) teleport(lv.teleB, lv.teleA, 0);
            } else {
                float[] p = teleLock == 1 ? lv.teleB : lv.teleA;
                if (dist2(p[0], p[1]) > 0.8f * 0.8f) teleLock = -1;
            }
        }
        // arrivée
        float sp2 = vx * vx + vy * vy;
        if (dist2(lv.gx, lv.gy) < 0.4f * 0.4f && sp2 < 9.5f * 9.5f) {
            state = WINNING;
            stateT = 0;
            return;
        }
        // trous
        for (float[] p : lv.pits) {
            if (dist2(p[0], p[1]) < (p[2] * 0.78f) * (p[2] * 0.78f)) {
                state = FALLING;
                stateT = 0;
                fx = p[0];
                fy = p[1];
                deaths++;
                lavaDeath = lv.world == 2;
                if (listener != null) listener.onFall(p[0], p[1]);
                return;
            }
        }
        // pics
        for (int i = 0; i < lv.spikes.size(); i++) {
            int[] s = lv.spikes.get(i);
            if (lv.spikeHeight(i, t) < 0.55f) continue;
            float nx = clamp(x, s[0] + 0.12f, s[0] + 0.88f), ny = clamp(y, s[1] + 0.12f, s[1] + 0.88f);
            float dx = x - nx, dy = y - ny;
            if (dx * dx + dy * dy < (R * 0.6f) * (R * 0.6f)) { die(DEATH_SPIKES); return; }
        }
        // lasers
        for (Level.Laser l : lv.lasers) {
            if (!l.active(t)) continue;
            if (segDist2(l.x1, l.y1, l.x2, l.y2) < (R * 0.7f) * (R * 0.7f)) { die(DEATH_LASER); return; }
        }
        // lames des rotors
        for (Level.Rotor r : lv.rotors) {
            float a = r.angle(t);
            for (int k = 0; k < r.arms; k++) {
                float ak = a + k * (float) (2 * Math.PI / r.arms);
                float tx = r.cx + (float) Math.cos(ak) * r.len, ty = r.cy + (float) Math.sin(ak) * r.len;
                if (segDist2(r.cx, r.cy, tx, ty) < (R + 0.08f) * (R + 0.08f)) { die(DEATH_ROTOR); return; }
            }
        }
        // écrasement par un mur mobile
        for (Level.Mover m : lv.movers) {
            float nx = clamp(x, m.cx, m.cx + m.w), ny = clamp(y, m.cy, m.cy + m.h);
            float dx = x - nx, dy = y - ny;
            float d = (float) Math.sqrt(dx * dx + dy * dy);
            if (R - d > 0.2f) { die(DEATH_CRUSH); return; }
        }
    }

    private void teleport(float[] from, float[] to, int lock) {
        x = to[0];
        y = to[1];
        teleLock = lock;
        if (listener != null) listener.onTeleport(from[0], from[1], to[0], to[1]);
    }

    private void die(int cause) {
        state = DYING;
        stateT = 0;
        deaths++;
        if (listener != null) listener.onDeath(x, y, cause);
    }

    private void respawn() {
        x = lv.sx;
        y = lv.sy;
        vx = vy = 0;
        speed = 0;
        teleLock = -1;
        state = INTRO;
        introLen = RESPAWN_INTRO;
        stateT = 0;
        scale = 0;
    }

    private void border() {
        float e = 0.45f;
        if (x < R) { x = R; if (vx < 0) { impact = Math.max(impact, -vx); vx = -vx * e; } }
        if (x > Level.COLS - R) { x = Level.COLS - R; if (vx > 0) { impact = Math.max(impact, vx); vx = -vx * e; } }
        if (y < R) { y = R; if (vy < 0) { impact = Math.max(impact, -vy); vy = -vy * e; } }
        if (y > Level.ROWS - R) { y = Level.ROWS - R; if (vy > 0) { impact = Math.max(impact, vy); vy = -vy * e; } }
    }

    private void collideRect(float rx, float ry, float rw, float rh, float mvx, float mvy, float e) {
        if (x + R < rx || x - R > rx + rw || y + R < ry || y - R > ry + rh) return;
        float nx = clamp(x, rx, rx + rw), ny = clamp(y, ry, ry + rh);
        float dx = x - nx, dy = y - ny;
        float d2 = dx * dx + dy * dy;
        if (d2 >= R * R) return;
        float ux, uy, pen;
        if (d2 < 1e-10f) {
            // centre à l'intérieur : sortie par le côté le plus proche
            float l = x - rx, r = rx + rw - x, t = y - ry, b = ry + rh - y;
            float m = Math.min(Math.min(l, r), Math.min(t, b));
            if (m == l) { ux = -1; uy = 0; pen = l + R; }
            else if (m == r) { ux = 1; uy = 0; pen = r + R; }
            else if (m == t) { ux = 0; uy = -1; pen = t + R; }
            else { ux = 0; uy = 1; pen = b + R; }
        } else {
            float d = (float) Math.sqrt(d2);
            ux = dx / d;
            uy = dy / d;
            pen = R - d;
        }
        x += ux * pen;
        y += uy * pen;
        float rvx = vx - mvx, rvy = vy - mvy;
        float vn = rvx * ux + rvy * uy;
        if (vn < 0) {
            impact = Math.max(impact, -vn);
            rvx -= (1 + e) * vn * ux;
            rvy -= (1 + e) * vn * uy;
            // léger frottement tangentiel
            float tx = -uy, ty = ux;
            float vt = rvx * tx + rvy * ty;
            rvx -= vt * 0.04f * tx;
            rvy -= vt * 0.04f * ty;
            vx = rvx + mvx;
            vy = rvy + mvy;
        }
    }

    /** Fait rouler la bille d'un déplacement (dx, dy) en cases. */
    public void rollBy(float dx, float dy) {
        roll(rot, dx, dy, R);
    }

    /** Rotation de roulement (formule de Rodrigues) appliquée à la matrice rot. */
    public static void roll(float[] r, float dx, float dy, float radius) {
        float d = (float) Math.sqrt(dx * dx + dy * dy);
        if (d < 1e-6f) return;
        float axx = -dy / d, ayy = dx / d; // axe horizontal perpendiculaire au déplacement
        float ang = d / radius;
        float c = (float) Math.cos(ang), s = (float) Math.sin(ang), oc = 1 - c;
        float m00 = c + axx * axx * oc, m01 = axx * ayy * oc, m02 = ayy * s;
        float m10 = ayy * axx * oc, m11 = c + ayy * ayy * oc, m12 = -axx * s;
        float m20 = -ayy * s, m21 = axx * s, m22 = c;
        for (int j = 0; j < 3; j++) {
            float a = r[j], b = r[3 + j], e = r[6 + j];
            r[j] = m00 * a + m01 * b + m02 * e;
            r[3 + j] = m10 * a + m11 * b + m12 * e;
            r[6 + j] = m20 * a + m21 * b + m22 * e;
        }
        orthonormalize(r);
    }

    private static void orthonormalize(float[] r) {
        float l = (float) Math.sqrt(r[0] * r[0] + r[1] * r[1] + r[2] * r[2]);
        r[0] /= l; r[1] /= l; r[2] /= l;
        float d = r[0] * r[3] + r[1] * r[4] + r[2] * r[5];
        r[3] -= d * r[0]; r[4] -= d * r[1]; r[5] -= d * r[2];
        l = (float) Math.sqrt(r[3] * r[3] + r[4] * r[4] + r[5] * r[5]);
        r[3] /= l; r[4] /= l; r[5] /= l;
        r[6] = r[1] * r[5] - r[2] * r[4];
        r[7] = r[2] * r[3] - r[0] * r[5];
        r[8] = r[0] * r[4] - r[1] * r[3];
    }

    private float dist2(float px, float py) {
        float dx = x - px, dy = y - py;
        return dx * dx + dy * dy;
    }

    private float segDist2(float x1, float y1, float x2, float y2) {
        float dx = x2 - x1, dy = y2 - y1;
        float l2 = dx * dx + dy * dy;
        float k = l2 < 1e-8f ? 0 : clamp(((x - x1) * dx + (y - y1) * dy) / l2, 0, 1);
        float px = x1 + dx * k - x, py = y1 + dy * k - y;
        return px * px + py * py;
    }

    static float clamp(float v, float a, float b) {
        return v < a ? a : (v > b ? b : v);
    }

    /** Étoiles obtenues (0..3) pour un niveau terminé. */
    public int rating() {
        return stars;
    }
}
