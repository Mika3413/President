#!/usr/bin/env python3
"""Contours des pays jouables autres que la France, à partir de Natural Earth (domaine public).

Usage : python3 -I tools/datagen/playable_geo.py <ne_10m_admin_1_states_provinces.geojson> <ne_10m_populated_places_simple.geojson>

Produit :
- assets/data/geo/<ISO3>_departments.json et <ISO3>_regions.json (même format que la France) ;
- tools/datagen/playable_geo_cache.json : subdivisions (code, nom, région, surface, centre) et
  villes, pour que playable_fr.py puisse régénérer les données sans rien télécharger.
"""
import json, math, os, sys

HERE = os.path.dirname(os.path.abspath(__file__))
GEO = os.path.join(HERE, "..", "..", "assets", "data", "geo")
COUNTRIES = ["DEU", "GBR", "ITA", "ESP", "USA"]
TOLERANCE = 0.02
MIN_RING_AREA = 0.004

DEU_REGIONS = {"SH": "N", "HH": "N", "NI": "N", "HB": "N", "MV": "N", "NW": "W", "RP": "W", "SL": "W", "HE": "W", "BW": "S", "BY": "S", "BE": "E", "BB": "E", "SN": "E", "ST": "E", "TH": "E"}
DEU_REGION_NAMES = {"N": "Allemagne du Nord", "W": "Allemagne de l'Ouest", "S": "Allemagne du Sud", "E": "Allemagne de l'Est"}
USA_DIVISIONS = {
    "NE": ("Nouvelle-Angleterre", ["CT", "ME", "MA", "NH", "RI", "VT"]),
    "MA": ("Centre-Atlantique", ["NJ", "NY", "PA"]),
    "ENC": ("Grands Lacs", ["IL", "IN", "MI", "OH", "WI"]),
    "WNC": ("Grandes Plaines", ["IA", "KS", "MN", "MO", "NE", "ND", "SD"]),
    "SA": ("Atlantique Sud", ["DE", "DC", "FL", "GA", "MD", "NC", "SC", "VA", "WV"]),
    "ESC": ("Sud-Est intérieur", ["AL", "KY", "MS", "TN"]),
    "WSC": ("Sud-Ouest", ["AR", "LA", "OK", "TX"]),
    "MTN": ("Montagnes Rocheuses", ["AZ", "CO", "ID", "MT", "NV", "NM", "UT", "WY"]),
    "PAC": ("Pacifique", ["AK", "CA", "HI", "OR", "WA"]),
}
GBR_REGIONS = {
    "North East": ("NE", "Nord-Est de l'Angleterre"), "North West": ("NW", "Nord-Ouest de l'Angleterre"),
    "Yorkshire and the Humber": ("YH", "Yorkshire"), "East Midlands": ("EM", "Midlands de l'Est"), "West Midlands": ("WM", "Midlands de l'Ouest"),
    "East": ("EE", "Est de l'Angleterre"), "Eastern": ("EE", "Est de l'Angleterre"), "Greater London": ("LDN", "Grand Londres"),
    "South East": ("SE", "Sud-Est de l'Angleterre"), "South West": ("SW", "Sud-Ouest de l'Angleterre"),
}
GBR_NATIONS = {"Scotland": ("SCT", "Écosse"), "Wales": ("WLS", "Pays de Galles"), "Northern Ireland": ("NIR", "Irlande du Nord")}
ITA_REGIONS = {
    "Abruzzo": "Abruzzes", "Apulia": "Pouilles", "Basilicata": "Basilicate", "Calabria": "Calabre", "Campania": "Campanie", "Emilia-Romagna": "Émilie-Romagne",
    "Friuli-Venezia Giulia": "Frioul-Vénétie Julienne", "Lazio": "Latium", "Liguria": "Ligurie", "Lombardia": "Lombardie", "Marche": "Marches", "Molise": "Molise",
    "Piemonte": "Piémont", "Sardegna": "Sardaigne", "Sicily": "Sicile", "Toscana": "Toscane", "Trentino-Alto Adige": "Trentin-Haut-Adige", "Umbria": "Ombrie",
    "Valle d'Aosta": "Vallée d'Aoste", "Veneto": "Vénétie",
}
ESP_REGIONS = {
    "Andalucía": "Andalousie", "Aragón": "Aragon", "Asturias": "Asturies", "Canary Is.": "Canaries", "Cantabria": "Cantabrie", "Castilla y León": "Castille-et-León",
    "Castilla-La Mancha": "Castille-La Manche", "Cataluña": "Catalogne", "Ceuta": "Ceuta", "Extremadura": "Estrémadure", "Foral de Navarra": "Navarre", "Galicia": "Galice",
    "Islas Baleares": "Baléares", "La Rioja": "La Rioja", "Madrid": "Communauté de Madrid", "Melilla": "Melilla", "Murcia": "Région de Murcie", "País Vasco": "Pays basque",
    "Valenciana": "Communauté valencienne",
}


def slug(s):
    import unicodedata
    s = unicodedata.normalize("NFKD", s).encode("ascii", "ignore").decode().lower()
    return "".join(c if c.isalnum() else "_" for c in s).strip("_")


def ring_area(r):
    a = 0.0
    for i in range(len(r)):
        x1, y1 = r[i]; x2, y2 = r[(i + 1) % len(r)]
        a += x1 * y2 - x2 * y1
    return abs(a) / 2


def dp(points, tol):
    if len(points) < 4: return points
    # Anneau fermé : on le coupe au point le plus éloigné du départ, puis on simplifie les deux moitiés.
    if points[0] == points[-1]:
        far = max(range(len(points)), key=lambda i: (points[i][0] - points[0][0]) ** 2 + (points[i][1] - points[0][1]) ** 2)
        if 0 < far < len(points) - 1:
            return dp_open(points[:far + 1], tol)[:-1] + dp_open(points[far:], tol)
    return dp_open(points, tol)


def dp_open(points, tol):
    if len(points) < 3: return points
    stack = [(0, len(points) - 1)]; keep = {0, len(points) - 1}
    while stack:
        s, e = stack.pop()
        x1, y1 = points[s]; x2, y2 = points[e]
        dx, dy = x2 - x1, y2 - y1; norm = math.hypot(dx, dy) or 1e-12
        best, idx = 0.0, None
        for i in range(s + 1, e):
            x, y = points[i]
            d = abs(dy * x - dx * y + x2 * y1 - y2 * x1) / norm
            if d > best: best, idx = d, i
        if idx is not None and best > tol:
            keep.add(idx); stack += [(s, idx), (idx, e)]
    return [points[i] for i in sorted(keep)]


def polygons(geom):
    if geom["type"] == "Polygon": return [geom["coordinates"]]
    return geom["coordinates"]


def point_in(x, y, ring):
    inside = False
    j = len(ring) - 1
    for i in range(len(ring)):
        xi, yi = ring[i]; xj, yj = ring[j]
        if (yi > y) != (yj > y) and x < (xj - xi) * (y - yi) / (yj - yi + 1e-12) + xi: inside = not inside
        j = i
    return inside


def main(admin_path, places_path):
    admin = json.load(open(admin_path))
    places = json.load(open(places_path))
    cache = {}
    for iso in COUNTRIES:
        feats = [f for f in admin["features"] if f["properties"].get("adm0_a3") == iso]
        deps, regions = [], {}
        out_deps = []
        for f in feats:
            p = f["properties"]
            iso2 = (p.get("iso_3166_2") or "").split("-")[-1] or p.get("postal") or slug(p["name"])[:4]
            code = iso2.upper()
            name = p.get("name_fr") or p.get("name")
            if iso == "DEU":
                rcode = DEU_REGIONS.get(code, "W"); rname = DEU_REGION_NAMES[rcode]
            elif iso == "USA":
                rcode, rname = next(((k, v[0]) for k, v in USA_DIVISIONS.items() if code in v[1]), ("PAC", "Pacifique"))
            elif iso == "GBR":
                nation = p.get("geonunit") or ""
                if nation in GBR_NATIONS: rcode, rname = GBR_NATIONS[nation]
                else: rcode, rname = GBR_REGIONS.get(p.get("region") or "", ("SE", "Sud-Est de l'Angleterre"))
            elif iso == "ITA":
                rname = ITA_REGIONS.get(p.get("region") or "", p.get("region") or "Italie"); rcode = slug(rname)[:6].upper()
            else:
                rname = ESP_REGIONS.get(p.get("region") or "", p.get("region") or "Espagne"); rcode = slug(rname)[:6].upper()
            rings, area, cx, cy = [], 0.0, 0.0, 0.0
            for poly in polygons(f["geometry"]):
                outer = poly[0]
                a = ring_area(outer)
                if a < MIN_RING_AREA and rings: continue
                # Au plus 900 sommets par contour (limite du rendu des polygones).
                tol = TOLERANCE * (2.5 if iso == "USA" else 1.0)
                simple = dp([(round(x, 3), round(y, 3)) for x, y in outer], tol)
                while len(simple) > 900:
                    tol *= 1.6
                    simple = dp([(round(x, 3), round(y, 3)) for x, y in outer], tol)
                if len(simple) < 4: continue
                rings.append(simple)
                area += a
                cx += sum(x for x, _ in outer) / len(outer) * a; cy += sum(y for _, y in outer) / len(outer) * a
            if not rings: continue
            cx /= area or 1; cy /= area or 1
            # Codes uniques.
            while any(d["code"] == code for d in deps): code += "X"
            deps.append({"code": code, "name": name, "region": rcode, "area": round(area, 4), "lon": round(cx, 3), "lat": round(cy, 3)})
            regions[rcode] = rname
            out_deps.append({"id": code, "name": name, "parent": rcode, "rings": [[c for pt in r for c in pt] for r in rings], "_raw": rings})
        # Villes : agglomérations de Natural Earth, rattachées à leur subdivision.
        cities = []
        for pl in places["features"]:
            p = {k.lower(): v for k, v in pl["properties"].items()}
            if p.get("adm0_a3") != iso and p.get("sov_a3") != iso: continue
            x, y = pl["geometry"]["coordinates"][:2]
            pop = p.get("pop_max") or 0
            if pop < 30000: continue
            dep = next((d["id"] for d in out_deps if any(point_in(x, y, r) for r in d["_raw"])), None)
            if dep is None:
                # Point sur un estuaire ou une côte simplifiée : la subdivision la plus proche.
                near = min(deps, key=lambda d: (d["lon"] - x) ** 2 + (d["lat"] - y) ** 2)
                if (near["lon"] - x) ** 2 + (near["lat"] - y) ** 2 > 0.8: continue
                dep = near["code"]
            cities.append({"name": p.get("name_fr") or p.get("name"), "lon": round(x, 3), "lat": round(y, 3), "population": int(pop), "department": dep,
                           "capital": p.get("adm0cap") == 1 or p.get("featurecla") == "Admin-0 capital"})
        cities.sort(key=lambda c: -c["population"])
        cache[iso] = {"departments": deps, "regions": regions, "cities": cities[:260]}
        geo_deps = {"source": "Natural Earth 1:10m (domaine public), simplifié", "features": [{k: v for k, v in d.items() if k != "_raw"} for d in out_deps]}
        geo_regs = {"source": "Natural Earth 1:10m (domaine public), regroupé", "features": [
            {"id": rc, "name": rn, "rings": [ring for d in out_deps if d["parent"] == rc for ring in d["rings"]]} for rc, rn in regions.items()]}
        json.dump(geo_deps, open(os.path.join(GEO, f"{iso}_departments.json"), "w"), ensure_ascii=False, separators=(",", ":"))
        json.dump(geo_regs, open(os.path.join(GEO, f"{iso}_regions.json"), "w"), ensure_ascii=False, separators=(",", ":"))
        print(iso, len(deps), "subdivisions,", len(regions), "régions,", len(cache[iso]["cities"]), "villes")
    json.dump(cache, open(os.path.join(HERE, "playable_geo_cache.json"), "w"), ensure_ascii=False, indent=0)


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
