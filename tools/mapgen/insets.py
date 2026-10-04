"""Placement des territoires d'outre-mer en médaillons dans l'Atlantique, à l'ouest de la France,
comme sur les cartes officielles. Partagé par build_map.py (tracés) et snapshot_fra_territory.py (villes).
Chaque territoire garde ses proportions réelles, à une échelle propre."""
import math

REFERENCE_LATITUDE = 46.5          # parallèle de référence de la projection du jeu
BOX_HALF_WIDTH = 0.7               # demi-largeur d'un médaillon (degrés de longitude affichés)
BOX_HALF_HEIGHT = 0.5              # demi-hauteur (degrés de latitude)
FIT = 0.85                         # taille maximale du territoire dans le médaillon (degrés « vrais »)

# code : (nom, emprise réelle (lon min, lon max, lat min, lat max), centre du médaillon (lon, lat))
INSETS = {
    "971": ("Guadeloupe", (-61.81, -61.002, 15.832, 16.514), (-10.0, 47.3)),
    "972": ("Martinique", (-61.229, -60.809, 14.389, 14.879), (-10.0, 46.2)),
    "973": ("Guyane", (-54.602, -51.619, 2.111, 5.748), (-10.0, 45.1)),
    "974": ("La Réunion", (55.217, 55.837, -21.389, -20.872), (-10.0, 44.0)),
    "976": ("Mayotte", (45.018, 45.3, -13.005, -12.637), (-10.0, 42.9)),
}


def transform(code):
    """Renvoie une fonction (lon, lat) réels -> (lon, lat) d'affichage dans le médaillon."""
    _, (x0, x1, y0, y1), (cx, cy) = INSETS[code]
    rcx, rcy = (x0 + x1) / 2, (y0 + y1) / 2
    k_real = math.cos(math.radians(rcy))
    k_ref = math.cos(math.radians(REFERENCE_LATITUDE))
    s = FIT / max((x1 - x0) * k_real, y1 - y0)
    return lambda lon, lat: (cx + (lon - rcx) * k_real * s / k_ref, cy + (lat - rcy) * s)


def box(code):
    _, _, (cx, cy) = INSETS[code]
    return [round(cx - BOX_HALF_WIDTH, 3), round(cy - BOX_HALF_HEIGHT, 3), round(cx + BOX_HALF_WIDTH, 3), round(cy + BOX_HALF_HEIGHT, 3)]
