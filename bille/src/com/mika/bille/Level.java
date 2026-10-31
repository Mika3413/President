package com.mika.bille;

import java.util.ArrayList;

/**
 * Description d'un niveau : grille 12 x 20 cases + objets animés.
 * Aucune dépendance Android (testable sur JVM).
 *
 * Légende de la grille :
 *  .  sol          #  mur            O  trou (piège)
 *  S  départ       G  arrivée        *  étoile
 *  B  champignon rebondissant        ~  glace           :  boue
 *  ^ v < >  accélérateurs            x  pics rétractables
 *  T t  paire de téléporteurs
 */
public final class Level {
    public static final int COLS = 12, ROWS = 20;

    public static final class Mover {
        public float x, y, w, h, dx, dy, period, phase;
        public float cx, cy, vx, vy; // état courant

        public void at(float t) {
            double a = 2 * Math.PI * (t / period + phase);
            float k = (float) ((1 - Math.cos(a)) * 0.5);
            float dk = (float) (Math.PI / period * Math.sin(a));
            cx = x + dx * k;
            cy = y + dy * k;
            vx = dx * dk;
            vy = dy * dk;
        }
    }

    public static final class Laser {
        public float x1, y1, x2, y2, period, on, phase;

        public float frac(float t) {
            float f = t / period + phase;
            return f - (float) Math.floor(f);
        }

        public boolean active(float t) {
            return frac(t) < on;
        }

        /** 0..1 : imminence de l'allumage (avertissement). */
        public float warning(float t) {
            float f = frac(t);
            if (f < on) return 0f;
            float remain = (1f - f) * period;
            return remain < 0.7f ? 1f - remain / 0.7f : 0f;
        }
    }

    public static final class Rotor {
        public float cx, cy, len, speed, phase;
        public int arms;

        public float angle(float t) {
            return phase + speed * t;
        }
    }

    public final String name;
    public final int world;
    public final char[][] g = new char[ROWS][COLS];
    public float sx, sy, gx, gy;
    public final ArrayList<float[]> walls = new ArrayList<>();   // x, y, w, h
    public final ArrayList<float[]> pits = new ArrayList<>();    // x, y, r
    public final ArrayList<float[]> bumpers = new ArrayList<>(); // x, y, r
    public final ArrayList<float[]> stars = new ArrayList<>();   // x, y
    public final ArrayList<int[]> spikes = new ArrayList<>();    // col, row
    public final ArrayList<int[]> boosts = new ArrayList<>();    // col, row, dir(0 ^,1 >,2 v,3 <)
    public float[] teleA, teleB;
    public final ArrayList<Mover> movers = new ArrayList<>();
    public final ArrayList<Laser> lasers = new ArrayList<>();
    public final ArrayList<Rotor> rotors = new ArrayList<>();
    public float spikePeriod = 2.6f;

    public Level(String name, int world, String... rows) {
        this.name = name;
        this.world = world;
        if (rows.length != ROWS) throw new IllegalArgumentException(name + " : " + rows.length + " lignes");
        int starts = 0, goals = 0;
        for (int r = 0; r < ROWS; r++) {
            if (rows[r].length() != COLS)
                throw new IllegalArgumentException(name + " ligne " + r + " : " + rows[r].length() + " colonnes");
            for (int c = 0; c < COLS; c++) {
                char ch = rows[r].charAt(c);
                g[r][c] = ch;
                float x = c + 0.5f, y = r + 0.5f;
                switch (ch) {
                    case 'S': sx = x; sy = y; starts++; break;
                    case 'G': gx = x; gy = y; goals++; break;
                    case 'O': pits.add(new float[]{x, y, 0.46f}); break;
                    case 'B': bumpers.add(new float[]{x, y, 0.36f}); break;
                    case '*': stars.add(new float[]{x, y}); break;
                    case 'x': spikes.add(new int[]{c, r}); break;
                    case '^': boosts.add(new int[]{c, r, 0}); break;
                    case '>': boosts.add(new int[]{c, r, 1}); break;
                    case 'v': boosts.add(new int[]{c, r, 2}); break;
                    case '<': boosts.add(new int[]{c, r, 3}); break;
                    case 'T': teleA = new float[]{x, y}; break;
                    case 't': teleB = new float[]{x, y}; break;
                    case '#': case '.': case '~': case ':': break;
                    default: throw new IllegalArgumentException(name + " : caractère inconnu '" + ch + "'");
                }
            }
        }
        if (starts != 1 || goals != 1) throw new IllegalArgumentException(name + " : départ/arrivée");
        if ((teleA == null) != (teleB == null)) throw new IllegalArgumentException(name + " : téléporteurs");
        mergeWalls();
    }

    /** Regroupe les cases de mur en rectangles (moins de collisions, rendu plus propre). */
    private void mergeWalls() {
        boolean[][] used = new boolean[ROWS][COLS];
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (g[r][c] != '#' || used[r][c]) continue;
                int c2 = c;
                while (c2 + 1 < COLS && g[r][c2 + 1] == '#' && !used[r][c2 + 1]) c2++;
                int r2 = r;
                outer:
                while (r2 + 1 < ROWS) {
                    for (int k = c; k <= c2; k++) if (g[r2 + 1][k] != '#' || used[r2 + 1][k]) break outer;
                    // n'étendre que si la rangée suivante n'est pas plus large (rectangles nets)
                    if ((c > 0 && g[r2 + 1][c - 1] == '#' && !used[r2 + 1][c - 1])
                            || (c2 + 1 < COLS && g[r2 + 1][c2 + 1] == '#' && !used[r2 + 1][c2 + 1])) break;
                    r2++;
                }
                for (int rr = r; rr <= r2; rr++) for (int k = c; k <= c2; k++) used[rr][k] = true;
                walls.add(new float[]{c, r, c2 - c + 1, r2 - r + 1});
            }
        }
    }

    public char cell(float x, float y) {
        int c = (int) Math.floor(x), r = (int) Math.floor(y);
        if (c < 0 || r < 0 || c >= COLS || r >= ROWS) return '#';
        return g[r][c];
    }

    // ---- objets animés (coordonnées en cases) ----

    public Level mover(float x, float y, float w, float h, float dx, float dy, float period, float phase) {
        Mover m = new Mover();
        m.x = x; m.y = y; m.w = w; m.h = h; m.dx = dx; m.dy = dy; m.period = period; m.phase = phase;
        m.at(0);
        movers.add(m);
        return this;
    }

    public Level laser(float x1, float y1, float x2, float y2, float period, float on, float phase) {
        Laser l = new Laser();
        l.x1 = x1; l.y1 = y1; l.x2 = x2; l.y2 = y2; l.period = period; l.on = on; l.phase = phase;
        lasers.add(l);
        return this;
    }

    public Level rotor(float cx, float cy, float len, float speed, int arms) {
        Rotor r = new Rotor();
        r.cx = cx; r.cy = cy; r.len = len; r.speed = speed; r.arms = arms; r.phase = 0.4f;
        rotors.add(r);
        return this;
    }

    public float spikePhase(int i) {
        int[] s = spikes.get(i);
        return s[0] * 0.09f + s[1] * 0.23f;
    }

    /** Hauteur des pics 0..1 au temps t. */
    public float spikeHeight(int i, float t) {
        float f = t / spikePeriod + spikePhase(i);
        f -= (float) Math.floor(f);
        if (f < 0.07f) return f / 0.07f;
        if (f < 0.45f) return 1f;
        if (f < 0.52f) return 1f - (f - 0.45f) / 0.07f;
        return 0f;
    }

    /** Avertissement 0..1 avant que les pics ne sortent. */
    public float spikeWarning(int i, float t) {
        float f = t / spikePeriod + spikePhase(i);
        f -= (float) Math.floor(f);
        if (f > 0.82f) return (f - 0.82f) / 0.18f;
        return 0f;
    }
}
