import com.mika.bille.Level;
import com.mika.bille.Levels;
import com.mika.bille.World;

import java.util.ArrayDeque;
import java.util.Random;

/** Vérifie les niveaux sur JVM : format, accessibilité des étoiles et de l'arrivée, robustesse de la physique. */
public class LevelCheck {
    public static void main(String[] a) {
        int errors = 0;
        for (int i = 0; i < Levels.count(); i++) {
            Level lv = Levels.get(i);
            boolean[][] seen = new boolean[Level.ROWS][Level.COLS];
            ArrayDeque<int[]> q = new ArrayDeque<>();
            q.add(new int[]{(int) lv.sx, (int) lv.sy});
            seen[(int) lv.sy][(int) lv.sx] = true;
            while (!q.isEmpty()) {
                int[] p = q.poll();
                int[][] next = {{p[0] + 1, p[1]}, {p[0] - 1, p[1]}, {p[0], p[1] + 1}, {p[0], p[1] - 1}};
                char here = lv.g[p[1]][p[0]];
                if (here == 'T' || here == 't') {
                    float[] o = here == 'T' ? lv.teleB : lv.teleA;
                    next = new int[][]{next[0], next[1], next[2], next[3], {(int) o[0], (int) o[1]}};
                }
                for (int[] n : next) {
                    if (n[0] < 0 || n[1] < 0 || n[0] >= Level.COLS || n[1] >= Level.ROWS) continue;
                    char c = lv.g[n[1]][n[0]];
                    if (c == '#' || c == 'O' || seen[n[1]][n[0]]) continue;
                    seen[n[1]][n[0]] = true;
                    q.add(n);
                }
            }
            if (!seen[(int) lv.gy][(int) lv.gx]) { System.out.println("ERREUR " + lv.name + " : arrivée inaccessible"); errors++; }
            for (float[] s : lv.stars)
                if (!seen[(int) s[1]][(int) s[0]]) { System.out.println("ERREUR " + lv.name + " : étoile inaccessible " + s[0] + "," + s[1]); errors++; }
            if (lv.stars.size() != 3) { System.out.println("ERREUR " + lv.name + " : " + lv.stars.size() + " étoiles"); errors++; }

            // simulation aléatoire : pas de NaN, pas de sortie du plateau, pas de traversée de mur
            Random rnd = new Random(i);
            World w = new World(lv);
            float ax = 0, ay = 0;
            int falls = 0;
            for (int f = 0; f < 60 * 300; f++) {
                if (f % 45 == 0) { ax = (rnd.nextFloat() - 0.5f) * 30; ay = (rnd.nextFloat() - 0.5f) * 30; }
                w.update(1 / 60f, ax, ay);
                if (Float.isNaN(w.x) || Float.isNaN(w.y) || Float.isNaN(w.rot[0])) { System.out.println("ERREUR NaN " + lv.name); errors++; break; }
                if (w.x < 0 || w.y < 0 || w.x > Level.COLS || w.y > Level.ROWS) { System.out.println("ERREUR hors plateau " + lv.name); errors++; break; }
                if (w.state == World.PLAY && lv.cell(w.x, w.y) == '#') { System.out.println("ERREUR dans un mur " + lv.name + " " + w.x + "," + w.y); errors++; break; }
                if (w.state == World.WON) break;
            }
            System.out.printf("%-20s murs=%d trous=%d morts=%d étoiles=%d état=%d temps=%.1f%n",
                    lv.name, lv.walls.size(), lv.pits.size(), w.deaths, w.stars, w.state, w.time);
        }
        System.out.println(errors == 0 ? "OK" : errors + " erreur(s)");
        if (errors > 0) System.exit(1);
    }
}
