#!/usr/bin/env python3
"""
Génère la grille des zones de théâtre militaire (assets/data/geo/zones.json) à partir des
géométries déjà produites par build_map.py. Une zone = un point de grille (1°) terrestre
(rattaché au pays qui le contient, et au département pour la France) ou maritime côtier.
Les voisinages (8 directions) servent au déplacement, au ravitaillement et aux fronts.
"""
import json, math, os

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
GEO = os.path.join(ROOT, "assets", "data", "geo")
LON_MIN, LON_MAX, LAT_MIN, LAT_MAX = -25, 60, 0, 71
STEP = 1.0
SEA_RANGE = 2  # cellules maritimes conservées jusqu'à cette distance des côtes

def load(name):
    with open(os.path.join(GEO, name), encoding="utf-8") as f:
        return json.load(f)["features"]

def rings(feature):
    out = []
    for r in feature["rings"]:
        pts = [(r[i], r[i + 1]) for i in range(0, len(r), 2)]
        xs = [p[0] for p in pts]; ys = [p[1] for p in pts]
        out.append((pts, (min(xs), min(ys), max(xs), max(ys))))
    return out

def inside(pts, x, y):
    c = False
    j = len(pts) - 1
    for i in range(len(pts)):
        xi, yi = pts[i]; xj, yj = pts[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi:
            c = not c
        j = i
    return c

def find(features, x, y):
    for fid, rs in features:
        for pts, (a, b, c, d) in rs:
            if a <= x <= c and b <= y <= d and inside(pts, x, y):
                return fid
    return None

def main():
    countries = [(f["id"], rings(f)) for f in load("world_countries.json")]
    depts = [(f["id"], rings(f)) for f in load("france_departments.json")]
    cells = {}
    nx = int((LON_MAX - LON_MIN) / STEP) + 1
    ny = int((LAT_MAX - LAT_MIN) / STEP) + 1
    for i in range(nx):
        for j in range(ny):
            lon = LON_MIN + i * STEP + STEP / 2
            lat = LAT_MIN + j * STEP + STEP / 2
            owner = find(countries, lon, lat)
            cells[(i, j)] = {"lon": round(lon, 2), "lat": round(lat, 2), "owner": owner}
    land = {k for k, v in cells.items() if v["owner"]}
    def dist_to_land(k):
        best = 99
        for (a, b) in land:
            d = max(abs(a - k[0]), abs(b - k[1]))
            if d < best: best = d
            if best <= 1: break
        return best
    keep = {}
    for k, v in cells.items():
        if v["owner"]:
            keep[k] = v
        else:
            near = any((k[0] + dx, k[1] + dy) in land for dx in range(-SEA_RANGE, SEA_RANGE + 1) for dy in range(-SEA_RANGE, SEA_RANGE + 1))
            if near: keep[k] = v
    zones = {}
    for (i, j), v in keep.items():
        zid = f"z{i}_{j}"
        z = {"id": zid, "lon": v["lon"], "lat": v["lat"], "owner": v["owner"] or "", "sea": v["owner"] is None}
        if v["owner"] == "FRA":
            d = find(depts, v["lon"], v["lat"])
            if d: z["department"] = d
        z["n"] = [f"z{i+dx}_{j+dy}" for dx in (-1, 0, 1) for dy in (-1, 0, 1)
                  if (dx or dy) and (i + dx, j + dy) in keep]
        zones[zid] = z
    # Pays trop petits pour la grille : une zone à leur point d'étiquette, reliée aux plus proches.
    present = {z["owner"] for z in zones.values()}
    for f in load("world_countries.json"):
        cid = f["id"]
        r = max(f["rings"], key=len)
        xs = r[0::2]; ys = r[1::2]
        cx = (min(xs) + max(xs)) / 2; cy = (min(ys) + max(ys)) / 2
        if cid in present or not (LON_MIN <= cx <= LON_MAX and LAT_MIN <= cy <= LAT_MAX):
            continue
        zid = "c_" + cid
        near = sorted((z for z in zones.values() if not z["sea"]), key=lambda z: (z["lon"] - cx) ** 2 + (z["lat"] - cy) ** 2)[:3]
        zones[zid] = {"id": zid, "lon": round(cx, 2), "lat": round(cy, 2), "owner": cid, "sea": False, "n": [z["id"] for z in near]}
        for z in near: z["n"].append(zid)
    for z in zones.values():
        z["coastal"] = (not z["sea"]) and any(zones[n]["sea"] for n in z["n"] if n in zones)
    out = {"source": "Grille générée par tools/mapgen/build_zones.py", "stepDegrees": STEP, "zones": list(zones.values())}
    with open(os.path.join(GEO, "zones.json"), "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, separators=(",", ":"))
    land_n = sum(1 for z in zones.values() if not z["sea"])
    print("zones", len(zones), "terrestres", land_n, "France", sum(1 for z in zones.values() if z["owner"] == "FRA"))

if __name__ == "__main__":
    main()
