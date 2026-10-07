#!/usr/bin/env python3
"""Génère assets/data/military/warfare.json : fortifications et bâtiments militaires,
terrains (montagnes, forêts, collines, déserts, villes), grands fleuves et forces/faiblesses
des types d'unités.

Les zones du théâtre sont des carrés d'un degré : le relief est décrit par de grandes ellipses
(massifs, forêts, déserts) et les fleuves par des lignes brisées ; une attaque qui franchit un
fleuve est pénalisée."""
import json, os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "assets", "data", "military")


def lvl(label, cost, days, **effects):
    return {"label": label, "costBillions": cost, "days": days, "effects": effects}


FORTIFICATIONS = [
    {"id": "line", "label": "Ligne de défense", "icon": "▦",
     "description": "Tranchées, obstacles antichars, fortins puis béton : les défenseurs de la zone tiennent bien mieux. L'artillerie ennemie la grignote.",
     "levels": [
         lvl("Tranchées et obstacles", 0.3, 45, defense=0.2),
         lvl("Fortins et champs de mines", 0.6, 90, defense=0.4),
         lvl("Ligne bétonnée", 1.2, 180, defense=0.65),
     ]},
    {"id": "air_defense", "label": "Défense sol-air", "icon": "◭",
     "description": "Batteries de missiles : abattent les avions ennemis et interceptent une partie des missiles de croisière dans un rayon de 200 km.",
     "levels": [
         lvl("Batterie courte portée (VL Mica)", 0.5, 60, airDefense=4, radiusKm=150, interception=0.25),
         lvl("Système longue portée (SAMP/T)", 1.5, 180, airDefense=9, radiusKm=250, interception=0.5),
     ]},
    {"id": "radar", "label": "Station radar", "icon": "◠",
     "description": "Alerte avancée : la défense sol-air de la région est plus efficace et nos services voient plus loin.",
     "levels": [lvl("Radar de veille longue portée", 0.25, 60, radar=0.3, radiusKm=400, intel=2)]},
    {"id": "airfield", "label": "Base aérienne avancée", "icon": "✈",
     "description": "Piste, hangars et dépôts de munitions : les escadres stationnées ici portent 40 % plus loin et se réarment plus vite.",
     "levels": [lvl("Base aérienne de campagne", 0.8, 120, airRange=0.4, rearm=1.0)]},
    {"id": "barracks", "label": "Caserne et centre d'entraînement", "icon": "⌂",
     "description": "Les unités de la zone récupèrent plus vite ; les nouvelles unités sortent mieux entraînées et plus tôt.",
     "levels": [
         lvl("Caserne", 0.4, 120, recovery=0.6, production=0.08, training=0.08),
         lvl("Camp d'entraînement interarmes", 0.8, 240, recovery=1.2, production=0.15, training=0.15),
     ]},
    {"id": "depot", "label": "Dépôt logistique", "icon": "▣",
     "description": "Munitions, carburant, pièces : point de départ du ravitaillement. Bâti près du front ou en zone conquise, il prolonge la portée de nos lignes.",
     "levels": [
         lvl("Dépôt de campagne", 0.3, 60, supply=1, resupply=0.3),
         lvl("Grand dépôt interarmées", 0.6, 120, supply=2, resupply=0.6),
     ]},
    {"id": "coastal", "label": "Batterie côtière", "icon": "⚓", "coastalOnly": True,
     "description": "Canons et missiles antinavires : un débarquement sur cette côte paie le prix fort, et les navires ennemis au large sont harcelés.",
     "levels": [
         lvl("Batterie d'artillerie côtière", 0.4, 90, amphibious=0.3, naval=0.03),
         lvl("Missiles antinavires Exocet", 0.9, 180, amphibious=0.6, naval=0.06),
     ]},
]

TERRAINS = [
    {"id": "PLAINS", "label": "Plaine", "icon": "·", "defense": 1.0,
     "categories": {"ARMOR": 1.2, "INFANTRY": 1.0, "MOUNTAIN": 0.9},
     "hint": "Terrain ouvert : idéal pour les blindés."},
    {"id": "HILLS", "label": "Collines", "icon": "∩", "defense": 1.15,
     "categories": {"ARMOR": 0.95, "INFANTRY": 1.05, "MOUNTAIN": 1.15},
     "hint": "Relief modéré : léger avantage au défenseur."},
    {"id": "FOREST", "label": "Forêt", "icon": "♣", "defense": 1.25,
     "categories": {"ARMOR": 0.8, "INFANTRY": 1.15, "AIRBORNE": 1.1, "MOUNTAIN": 1.1},
     "hint": "Embuscades : l'infanterie y excelle, les chars s'y empêtrent."},
    {"id": "MOUNTAIN", "label": "Montagne", "icon": "▲", "defense": 1.5,
     "categories": {"ARMOR": 0.6, "INFANTRY": 1.0, "MOUNTAIN": 1.5, "ARTILLERY": 0.8},
     "hint": "Le défenseur a l'avantage ; seules les troupes de montagne s'y battent à leur aise."},
    {"id": "URBAN", "label": "Ville", "icon": "▥", "defense": 1.4,
     "categories": {"ARMOR": 0.7, "INFANTRY": 1.2, "MARINE": 1.1, "AIRBORNE": 1.1},
     "hint": "Combats de rue : lents, coûteux, favorables à l'infanterie."},
    {"id": "DESERT", "label": "Désert", "icon": "≈", "defense": 0.95,
     "categories": {"ARMOR": 1.15, "INFANTRY": 0.9},
     "hint": "Nulle part où se cacher ; la logistique souffre."},
]

# Grandes zones de relief (ellipses : centre lon/lat, demi-axes rx/ry en degrés). La première qui contient la zone l'emporte.
AREAS = [
    # Montagnes
    ("MOUNTAIN", 10.5, 46.4, 5.2, 1.4, "Alpes"),
    ("MOUNTAIN", 0.8, 42.7, 2.6, 0.55, "Pyrénées"),
    ("MOUNTAIN", 24.5, 48.3, 2.2, 1.0, "Carpates du Nord"),
    ("MOUNTAIN", 25.4, 46.0, 1.4, 1.3, "Carpates roumaines"),
    ("MOUNTAIN", 18.0, 43.6, 3.2, 1.4, "Alpes dinariques"),
    ("MOUNTAIN", 23.5, 42.3, 2.0, 0.8, "Balkans"),
    ("MOUNTAIN", 43.5, 42.7, 5.5, 1.2, "Caucase"),
    ("MOUNTAIN", 13.0, 64.0, 5.0, 4.5, "Alpes scandinaves"),
    ("MOUNTAIN", -4.5, 57.2, 1.6, 1.0, "Highlands"),
    ("MOUNTAIN", -4.0, 43.0, 2.5, 0.5, "Cordillère cantabrique"),
    ("MOUNTAIN", -3.3, 37.1, 1.5, 0.5, "Sierra Nevada"),
    ("MOUNTAIN", -3.0, 32.5, 5.5, 1.6, "Atlas"),
    ("MOUNTAIN", 49.0, 32.0, 5.5, 4.0, "Zagros"),
    ("MOUNTAIN", 52.0, 36.3, 3.5, 0.8, "Elbourz"),
    ("MOUNTAIN", 69.0, 35.0, 5.0, 2.5, "Hindou Kouch"),
    ("MOUNTAIN", 84.0, 29.5, 10.0, 2.5, "Himalaya"),
    ("MOUNTAIN", 89.0, 33.5, 9.0, 3.0, "Plateau tibétain"),
    ("MOUNTAIN", 39.5, 10.0, 3.0, 4.0, "Hauts plateaux éthiopiens"),
    ("MOUNTAIN", 44.5, 15.0, 1.5, 2.5, "Montagnes du Yémen"),
    ("MOUNTAIN", -112.0, 42.0, 6.0, 9.0, "Rocheuses"),
    ("MOUNTAIN", -70.0, -25.0, 3.0, 15.0, "Andes"),
    ("MOUNTAIN", 138.0, 36.0, 2.0, 1.5, "Alpes japonaises"),
    # Collines et moyennes montagnes
    ("HILLS", 3.0, 45.3, 1.4, 1.0, "Massif central"),
    ("HILLS", 6.6, 48.0, 0.7, 0.9, "Vosges"),
    ("HILLS", 6.0, 46.7, 0.8, 0.5, "Jura"),
    ("HILLS", 13.0, 42.5, 2.5, 2.2, "Apennins"),
    ("HILLS", -4.5, 40.3, 3.0, 2.0, "Meseta"),
    ("HILLS", 13.5, 50.0, 2.5, 0.8, "Monts de Bohême"),
    ("HILLS", 59.0, 58.0, 2.5, 9.0, "Oural"),
    ("HILLS", 34.5, 39.0, 6.5, 1.8, "Anatolie"),
    ("HILLS", 38.0, 33.5, 1.5, 1.5, "Plateau du Golan et Liban"),
    ("HILLS", 34.2, 44.7, 1.0, 0.4, "Montagnes de Crimée"),
    ("HILLS", -3.8, 52.5, 1.0, 0.8, "Pays de Galles"),
    # Forêts
    ("FOREST", 5.6, 50.0, 1.0, 0.5, "Ardennes"),
    ("FOREST", 8.2, 48.2, 0.5, 0.7, "Forêt-Noire"),
    ("FOREST", -0.8, 44.2, 0.8, 0.6, "Landes de Gascogne"),
    ("FOREST", 27.0, 52.0, 4.5, 1.3, "Polésie"),
    ("FOREST", 26.0, 63.5, 6.0, 3.5, "Forêts finlandaises"),
    ("FOREST", 16.5, 61.5, 4.0, 2.5, "Forêts suédoises"),
    ("FOREST", 34.0, 58.5, 6.0, 2.5, "Forêts de Russie du Nord-Ouest"),
    ("FOREST", 75.0, 60.0, 30.0, 5.0, "Taïga sibérienne"),
    ("FOREST", 24.0, 56.5, 3.0, 1.2, "Forêts baltes"),
    ("FOREST", 22.5, 52.8, 1.0, 0.6, "Forêt de Białowieża"),
    ("FOREST", 22.0, 2.0, 8.0, 5.0, "Forêt du Congo"),
    ("FOREST", -60.0, -5.0, 12.0, 8.0, "Amazonie"),
    ("FOREST", -78.0, 50.0, 18.0, 4.0, "Forêt boréale canadienne"),
    # Déserts
    ("DESERT", 10.0, 23.0, 21.0, 6.0, "Sahara"),
    ("DESERT", 46.0, 23.0, 9.0, 6.5, "Désert d'Arabie"),
    ("DESERT", 40.0, 33.0, 3.5, 2.2, "Désert de Syrie"),
    ("DESERT", 59.0, 39.5, 5.5, 2.5, "Karakoum"),
    ("DESERT", 105.0, 42.0, 9.0, 3.0, "Gobi"),
    ("DESERT", 132.0, -25.0, 10.0, 5.0, "Désert australien"),
    ("DESERT", 71.0, 27.0, 3.0, 2.0, "Thar"),
]

# Grands fleuves (lon, lat) : les franchir en attaquant coûte cher.
RIVERS = {
    "Rhin": [(9.2, 47.65), (7.6, 47.6), (7.75, 48.6), (8.45, 49.0), (8.27, 50.0), (7.6, 50.36), (6.95, 50.94), (6.77, 51.23), (6.1, 51.85), (5.2, 51.9), (4.1, 51.95)],
    "Danube": [(8.5, 48.0), (10.0, 48.4), (12.1, 49.0), (14.3, 48.3), (16.4, 48.2), (17.1, 48.15), (18.9, 47.8), (19.05, 47.5), (18.9, 46.0), (19.0, 45.2), (20.5, 44.8), (22.5, 44.6), (22.9, 43.8), (25.0, 43.7), (26.1, 44.0), (27.9, 44.1), (28.2, 45.4), (29.6, 45.2)],
    "Dniepr": [(32.0, 54.8), (30.3, 53.9), (30.6, 52.0), (30.5, 50.45), (32.0, 49.4), (35.0, 48.45), (35.1, 47.8), (34.0, 47.3), (33.4, 46.8), (32.6, 46.6), (31.9, 46.6)],
    "Vistule": [(19.0, 49.6), (19.9, 50.05), (21.6, 50.6), (21.0, 52.23), (19.0, 52.9), (18.0, 53.1), (18.8, 54.0), (18.95, 54.35)],
    "Oder": [(18.3, 49.9), (17.0, 51.1), (15.5, 51.9), (14.6, 52.5), (14.3, 53.4), (14.5, 53.9)],
    "Elbe": [(15.9, 50.4), (14.4, 50.5), (13.7, 51.05), (12.4, 51.9), (11.6, 52.1), (10.0, 53.55), (8.9, 53.85)],
    "Loire": [(4.0, 45.0), (4.0, 46.5), (3.1, 47.0), (2.4, 47.4), (1.9, 47.9), (0.7, 47.4), (-0.55, 47.47), (-1.55, 47.2), (-2.2, 47.27)],
    "Seine": [(4.6, 47.8), (4.1, 48.3), (3.0, 48.4), (2.35, 48.85), (1.5, 49.1), (1.1, 49.44), (0.1, 49.45)],
    "Rhône": [(6.15, 46.2), (5.5, 45.9), (4.85, 45.76), (4.85, 44.9), (4.8, 43.95), (4.6, 43.65), (4.75, 43.35)],
    "Pô": [(7.6, 45.05), (9.0, 45.1), (10.0, 45.05), (11.0, 45.0), (12.3, 44.95)],
    "Volga": [(35.9, 56.85), (37.5, 57.3), (39.9, 57.6), (44.0, 56.3), (49.1, 55.8), (48.4, 54.3), (50.1, 53.2), (46.0, 51.5), (44.5, 48.7), (47.0, 46.9), (48.0, 46.35)],
    "Don": [(38.3, 53.8), (39.2, 51.7), (40.5, 49.8), (42.0, 48.5), (43.0, 48.8), (40.7, 47.4), (39.7, 47.25)],
    "Dniestr": [(23.2, 49.5), (25.0, 48.9), (26.6, 48.5), (27.8, 48.1), (29.0, 47.1), (30.2, 46.4)],
    "Daugava": [(32.0, 56.2), (30.3, 55.5), (28.0, 55.9), (26.5, 55.9), (24.0, 56.95)],
    "Niémen": [(26.0, 53.8), (24.0, 54.0), (23.9, 54.9), (22.0, 55.07), (21.2, 55.3)],
    "Nil": [(32.9, 24.1), (32.6, 25.7), (31.2, 27.2), (31.2, 30.05), (31.0, 31.5)],
    "Euphrate": [(38.3, 37.0), (38.5, 36.0), (40.1, 35.3), (41.4, 34.4), (43.3, 33.0), (44.4, 32.0), (46.3, 31.0), (47.4, 30.6)],
    "Tigre": [(40.2, 37.9), (42.5, 37.0), (43.1, 36.3), (43.9, 34.6), (44.4, 33.3), (45.8, 32.5), (47.4, 31.0)],
}

# Bonus d'un type d'unité attaquant contre le type dominant en face.
MATCHUPS = [
    {"attacker": "ARMOR", "defender": "INFANTRY", "factor": 1.2, "label": "les blindés enfoncent l'infanterie"},
    {"attacker": "ARMOR", "defender": "AIRBORNE", "factor": 1.25, "label": "les parachutistes manquent d'armes lourdes"},
    {"attacker": "INFANTRY", "defender": "ARMOR", "factor": 0.85, "label": "l'infanterie peine face aux chars"},
    {"attacker": "ARTILLERY", "defender": "INFANTRY", "factor": 1.2, "label": "l'artillerie écrase les positions"},
    {"attacker": "MOUNTAIN", "defender": "MOUNTAIN", "factor": 1.0, "label": ""},
    {"attacker": "SUPPORT", "defender": "ARMOR", "factor": 0.6, "label": "des unités de soutien face aux chars"},
]

CATEGORIES = {
    "ARMOR": "Blindés", "INFANTRY": "Infanterie", "MOUNTAIN": "Troupes de montagne", "AIRBORNE": "Parachutistes",
    "MARINE": "Infanterie de marine", "ARTILLERY": "Artillerie", "AIR_DEFENSE": "Défense sol-air mobile",
    "SUPPORT": "Soutien", "AIR": "Aviation", "NAVAL": "Marine", "STRATEGIC": "Dissuasion",
}

data = {
    "_doc": "Généré par tools/datagen/warfare_fr.py",
    "fortifications": FORTIFICATIONS,
    "terrains": TERRAINS,
    "areas": [{"terrain": t, "lon": lon, "lat": lat, "rx": rx, "ry": ry, "name": n} for t, lon, lat, rx, ry, n in AREAS],
    "rivers": [{"name": n, "points": [[p[0], p[1]] for p in pts]} for n, pts in RIVERS.items()],
    "matchups": MATCHUPS,
    "categories": CATEGORIES,
    "urbanMinMillions": 1.0,
    "urbanRadiusKm": 70,
    "riverCrossing": 0.75,
    "amphibiousPenalty": 0.8,
    "artilleryVsFortification": 0.5,
}

with open(os.path.join(ROOT, "warfare.json"), "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=1)
print("warfare.json :", len(FORTIFICATIONS), "fortifications,", len(AREAS), "zones de relief,", len(RIVERS), "fleuves")
