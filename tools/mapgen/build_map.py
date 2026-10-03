#!/usr/bin/env python3
"""
Génère les fichiers de géométrie compacts utilisés par le jeu à partir de données ouvertes.

Sources (voir ATTRIBUTIONS.md) :
  - france-geojson (G. David) : tracés IGN Admin Express COG, Licence Ouverte Etalab 2.0
  - Natural Earth 1:50m admin 0 : domaine public

Sortie : assets/data/geo/*.json au format
  { "source": "...", "features": [ { "id": "...", "name": "...", "parent": "...",
      "rings": [[lon,lat,lon,lat,...], ...] } ] }
Les coordonnées restent en longitude/latitude : la projection est faite par le moteur de rendu.

Utilisation : python3 tools/mapgen/build_map.py [--cache DIR]
Aucune dépendance hors bibliothèque standard.
"""
import argparse
import json
import math
import os
import urllib.request

FRANCE_BASE = "https://raw.githubusercontent.com/gregoiredavid/france-geojson/master/"
NE_URL = ("https://raw.githubusercontent.com/nvkelso/natural-earth-vector/master/"
          "geojson/ne_50m_admin_0_countries.geojson")

# Tolérances de simplification Douglas-Peucker (en degrés)
FRANCE_TOLERANCE = 0.004
WORLD_TOLERANCE = 0.04
# Anneaux plus petits que cette surface (deg²) ignorés
FRANCE_MIN_AREA = 0.0004
WORLD_MIN_AREA = 0.02
FRANCE_DECIMALS = 3
WORLD_DECIMALS = 2

# Département -> région (codes INSEE 2016)
DEPT_TO_REGION = {}
REGION_DEPTS = {
    "11": "75 77 78 91 92 93 94 95",
    "24": "18 28 36 37 41 45",
    "27": "21 25 39 58 70 71 89 90",
    "28": "14 27 50 61 76",
    "32": "02 59 60 62 80",
    "44": "08 10 51 52 54 55 57 67 68 88",
    "52": "44 49 53 72 85",
    "53": "22 29 35 56",
    "75": "16 17 19 23 24 33 40 47 64 79 86 87",
    "76": "09 11 12 30 31 32 34 46 48 65 66 81 82",
    "84": "01 03 07 15 26 38 42 43 63 69 73 74",
    "93": "04 05 06 13 83 84",
    "94": "2A 2B",
}
for region, depts in REGION_DEPTS.items():
    for d in depts.split():
        DEPT_TO_REGION[d] = region


def fetch(url, cache_dir):
    name = os.path.join(cache_dir, url.rsplit("/", 1)[-1])
    if not os.path.exists(name):
        print("Téléchargement", url)
        urllib.request.urlretrieve(url, name)
    with open(name, encoding="utf-8") as f:
        return json.load(f)


def perpendicular_distance(p, a, b):
    if a == b:
        return math.dist(p, a)
    (x, y), (x1, y1), (x2, y2) = p, a, b
    num = abs((y2 - y1) * x - (x2 - x1) * y + x2 * y1 - y2 * x1)
    return num / math.dist(a, b)


def douglas_peucker(points, tolerance):
    if len(points) < 3:
        return points
    keep = [False] * len(points)
    keep[0] = keep[-1] = True
    stack = [(0, len(points) - 1)]
    while stack:
        start, end = stack.pop()
        max_d, index = 0.0, -1
        for i in range(start + 1, end):
            d = perpendicular_distance(points[i], points[start], points[end])
            if d > max_d:
                max_d, index = d, i
        if max_d > tolerance and index > 0:
            keep[index] = True
            stack.append((start, index))
            stack.append((index, end))
    return [p for p, k in zip(points, keep) if k]


def ring_area(points):
    area = 0.0
    for i in range(len(points)):
        x1, y1 = points[i]
        x2, y2 = points[(i + 1) % len(points)]
        area += x1 * y2 - x2 * y1
    return abs(area) / 2.0


def outer_rings(geometry):
    """Ne garde que les anneaux extérieurs (les trous sont négligeables à cette échelle)."""
    if geometry["type"] == "Polygon":
        return [geometry["coordinates"][0]]
    if geometry["type"] == "MultiPolygon":
        return [poly[0] for poly in geometry["coordinates"]]
    return []


def encode_rings(geometry, tolerance, min_area, decimals):
    rings = []
    for ring in outer_rings(geometry):
        pts = [tuple(p[:2]) for p in ring]
        if len(pts) > 1 and pts[0] == pts[-1]:
            pts = pts[:-1]
        if ring_area(pts) < min_area:
            continue
        simplified = douglas_peucker(pts + [pts[0]], tolerance)[:-1]
        if len(simplified) < 3:
            continue
        flat = []
        for x, y in simplified:
            flat.extend([round(x, decimals), round(y, decimals)])
        rings.append(flat)
    return rings


def write(path, source, features):
    with open(path, "w", encoding="utf-8") as f:
        json.dump({"source": source, "features": features}, f, ensure_ascii=False,
                  separators=(",", ":"))
    print("Écrit", path, os.path.getsize(path) // 1024, "Ko")


def build_france(cache, out):
    regions = fetch(FRANCE_BASE + "regions-version-simplifiee.geojson", cache)
    depts = fetch(FRANCE_BASE + "departements-version-simplifiee.geojson", cache)
    src = "france-geojson (IGN Admin Express COG, Licence Ouverte Etalab)"
    write(os.path.join(out, "france_regions.json"), src, [
        {"id": f["properties"]["code"], "name": f["properties"]["nom"], "parent": "FRA",
         "rings": encode_rings(f["geometry"], FRANCE_TOLERANCE, FRANCE_MIN_AREA, FRANCE_DECIMALS)}
        for f in regions["features"]])
    write(os.path.join(out, "france_departments.json"), src, [
        {"id": f["properties"]["code"], "name": f["properties"]["nom"],
         "parent": DEPT_TO_REGION[f["properties"]["code"]],
         "rings": encode_rings(f["geometry"], FRANCE_TOLERANCE, FRANCE_MIN_AREA, FRANCE_DECIMALS)}
        for f in depts["features"]])


def build_world(cache, out):
    world = fetch(NE_URL, cache)
    features = []
    for f in world["features"]:
        p = f["properties"]
        iso = p.get("ISO_A3")
        if not iso or iso == "-99":
            iso = p.get("ADM0_A3")
        rings = encode_rings(f["geometry"], WORLD_TOLERANCE, WORLD_MIN_AREA, WORLD_DECIMALS)
        if rings:
            features.append({"id": iso, "name": p.get("NAME_FR") or p["NAME"], "parent": "",
                             "rings": rings})
    write(os.path.join(out, "world_countries.json"), "Natural Earth 1:50m (domaine public)", features)


def main():
    root = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
    parser = argparse.ArgumentParser()
    parser.add_argument("--cache", default=os.path.join(root, "build", "mapgen-cache"))
    args = parser.parse_args()
    os.makedirs(args.cache, exist_ok=True)
    out = os.path.join(root, "assets", "data", "geo")
    os.makedirs(out, exist_ok=True)
    build_france(args.cache, out)
    build_world(args.cache, out)


if __name__ == "__main__":
    main()
