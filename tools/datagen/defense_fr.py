"""Armement, bases à l'étranger et dissuasion (France) -> assets/data/military/defense.json.

Catalogue : équipements que la France peut commander. Certains livrent une unité (escadre de
Rafale, groupe de frégates...), d'autres renforcent une capacité :
  airDefense (défense sol-air : moins de dégâts des frappes ennemies), strike (frappes dans la
  profondeur), cyber (moins d'attaques hybrides réussies), drones, space (renseignement),
  naval (contrôle des mers), ammo (production de munitions).
Bases : implantations à l'étranger, ouvertes ou fermées au début, coût annuel, pays sur lesquels
elles donnent de l'influence, pays qu'elles inquiètent, risque que l'hôte demande le départ.
"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")

CATEGORIES = [("air", "Air et espace", "✈"), ("land", "Terre", "⚔"), ("sea", "Mer", "⚓"), ("missiles", "Missiles et défense aérienne", "◎"), ("cyber", "Cyber, drones, renseignement", "⌘")]

EQUIPMENT = [
    # id, catégorie, nom, description, coût (Md€), délai (jours), unité livrée, capacités, fournisseur étranger
    ("rafale", "air", "Rafale F4 (escadre de 20 appareils)", "L'avion de combat omnirôle français, dernier standard.", 4.0, 900, "FIGHTER_WING", {"strike": 0.04}, None),
    ("a400m", "air", "A400M Atlas (escadre de transport)", "Projeter troupes et matériel loin et vite.", 2.0, 720, "TRANSPORT_WING", {}, None),
    ("mrtt", "air", "A330 MRTT Phénix (ravitailleurs)", "Ravitaillement en vol et transport stratégique.", 2.5, 900, "TANKER_WING", {}, None),
    ("satellites", "air", "Satellites militaires (CSO, Syracuse)", "Images et communications sécurisées partout dans le monde.", 1.5, 720, None, {"space": 0.2, "strike": 0.03}, None),
    ("leclerc", "land", "Chars Leclerc rénovés (brigade blindée)", "Une brigade lourde de plus.", 3.5, 720, "ARMORED_BRIGADE", {}, None),
    ("griffon", "land", "Griffon et Serval (brigade mécanisée)", "Les nouveaux blindés du programme Scorpion.", 2.5, 540, "MECHANIZED_BRIGADE", {}, None),
    ("caesar", "land", "Canons CAESAR (60 pièces)", "Artillerie de 155 mm sur camion, précise et mobile.", 0.5, 360, None, {"strike": 0.04}, None),
    ("ammo_plant", "land", "Usine d'obus de 155 mm et de poudre", "Produire en France les munitions d'une guerre longue.", 0.6, 540, None, {"ammo": 0.3}, None),
    ("fdi", "sea", "Frégates de défense et d'intervention (groupe)", "Frégates de premier rang, lutte anti-sous-marine et anti-aérienne.", 3.0, 1080, "SURFACE_GROUP", {"naval": 0.05}, None),
    ("suffren", "sea", "Sous-marin nucléaire d'attaque Suffren", "Chasse aux sous-marins, missiles de croisière navals.", 1.5, 1440, None, {"naval": 0.15, "strike": 0.03}, None),
    ("pang", "sea", "Porte-avions de nouvelle génération", "Successeur du Charles-de-Gaulle : 30 ans de puissance aéronavale.", 10.0, 3600, "CARRIER_GROUP", {"naval": 0.2}, None),
    ("sampt", "missiles", "Défense sol-air SAMP/T NG (4 batteries)", "Intercepte avions, missiles de croisière et balistiques.", 1.2, 540, None, {"airDefense": 0.15}, None),
    ("mistral", "missiles", "Missiles Mistral 3 (1 000)", "Défense antiaérienne de courte portée.", 0.4, 270, None, {"airDefense": 0.05}, None),
    ("scalp", "missiles", "Missiles de croisière SCALP (200)", "Frapper loin dans la profondeur ennemie.", 0.6, 360, None, {"strike": 0.12}, None),
    ("patriot", "missiles", "Batteries Patriot (achat américain)", "Livraison plus rapide, mais dépendance aux États-Unis.", 1.0, 270, None, {"airDefense": 0.12}, "USA"),
    ("drones", "cyber", "Drones Aarok et munitions rôdeuses", "Une filière française de drones de combat.", 0.8, 365, None, {"drones": 0.15, "strike": 0.04}, None),
    ("reaper", "cyber", "Drones MQ-9 Reaper (achat américain)", "Éprouvés et vite livrés ; les États-Unis gardent la main sur les logiciels.", 0.6, 180, None, {"drones": 0.12}, "USA"),
    ("cyber_command", "cyber", "Commandement cyber : 1 000 cyber-combattants", "Défendre réseaux, hôpitaux et centrales ; riposter.", 0.5, 365, None, {"cyber": 0.15}, None),
    # Air
    ("mirage_2000d", "air", "Mirage 2000D rénovés (48 appareils)", "Moins cher qu'un Rafale neuf : l'appui au sol modernisé pour dix ans.", 1.2, 360, None, {"strike": 0.03}, None),
    ("tigre", "air", "Hélicoptères Tigre Mk III (24)", "L'hélicoptère de combat : chasse aux chars, appui des troupes.", 1.8, 720, None, {"strike": 0.04, "drones": 0.02}, None),
    ("eurodrone", "air", "Eurodrone (programme européen)", "Drone de surveillance armé coproduit avec l'Allemagne, l'Italie et l'Espagne.", 1.5, 1440, None, {"drones": 0.12, "space": 0.05}, None),
    ("avsimar", "air", "Avions de patrouille maritime Albatros", "Traquer les sous-marins et surveiller les approches maritimes.", 1.0, 900, None, {"naval": 0.06, "space": 0.03}, None),
    ("e2d", "air", "E-2D Advanced Hawkeye (achat américain)", "Radar volant du porte-avions : voir loin, coordonner la défense aérienne.", 1.4, 540, None, {"airDefense": 0.06, "space": 0.05}, "USA"),
    ("scaf", "air", "SCAF : avion de combat du futur (tranche de développement)", "Le chasseur des années 2040 avec l'Allemagne et l'Espagne. Très long, très cher, décisif.", 6.0, 3600, None, {"strike": 0.1, "airDefense": 0.05}, None),
    # Terre
    ("caesar_regiment", "land", "Régiment d'artillerie CAESAR Mk II", "Une unité d'artillerie de plus : la meilleure arme contre les lignes fortifiées.", 1.2, 360, "ARTILLERY_REGIMENT", {}, None),
    ("vl_mica_regiment", "land", "Régiment de défense sol-air VL MICA", "Défense antiaérienne mobile qui accompagne les brigades.", 1.5, 420, "AIR_DEFENSE_REGIMENT", {"airDefense": 0.03}, None),
    ("foudre", "land", "Lance-roquettes unitaires Foudre (26)", "Frappes de précision à 150 km : le successeur français du LRU.", 0.8, 540, None, {"strike": 0.06}, None),
    ("mountain_gear", "land", "Brigade de montagne (équipement complet)", "Chasseurs alpins : maîtres des combats en altitude.", 1.6, 480, "MOUNTAIN_BRIGADE", {}, None),
    ("marines", "land", "Brigade d'infanterie de marine", "Troupes de débarquement et d'intervention rapide.", 1.8, 480, "MARINE_BRIGADE", {}, None),
    ("larinae", "land", "Munitions rôdeuses Larinae et Colibri", "Drones kamikazes au niveau des sections.", 0.3, 270, None, {"drones": 0.06}, None),
    # Mer
    ("fremm", "sea", "Frégates FREMM supplémentaires (groupe)", "Frégates éprouvées, livrables plus tôt que les FDI.", 2.6, 900, "SURFACE_GROUP", {"naval": 0.04}, None),
    ("po", "sea", "Patrouilleurs océaniques (10)", "Surveiller la deuxième zone économique exclusive du monde.", 0.9, 720, None, {"naval": 0.03}, None),
    ("slamf", "sea", "Chasseurs de mines SLAM-F", "Drones sous-marins pour dégager ports et détroits.", 0.6, 720, None, {"naval": 0.04}, None),
    # Missiles et défense aérienne
    ("aster_b1nt", "missiles", "Missiles Aster 30 B1NT (antibalistiques)", "Intercepter les missiles balistiques et hypersoniques.", 1.0, 540, None, {"airDefense": 0.1}, None),
    ("exocet_b4", "missiles", "Missiles antinavires Exocet Block 4", "Tenir les flottes ennemies à distance.", 0.5, 360, None, {"naval": 0.05}, None),
    ("hypersonic", "missiles", "Planeur hypersonique (démonstrateur V-MaX)", "Un missile que personne ne sait intercepter : un saut technologique.", 1.5, 1440, None, {"strike": 0.08}, None),
    ("iris_t", "missiles", "IRIS-T SLM (achat allemand)", "Défense sol-air moyenne portée, vite livrée ; renforce le pilier européen.", 0.9, 240, None, {"airDefense": 0.08}, "DEU"),
    ("nasams", "missiles", "NASAMS (achat norvégien)", "Système éprouvé en Ukraine, intégré à l'OTAN.", 0.8, 300, None, {"airDefense": 0.07}, "NOR"),
    # Cyber, drones, renseignement
    ("early_warning", "cyber", "Satellites d'alerte avancée (Odin's Eye)", "Détecter les tirs de missiles dès leur départ.", 1.2, 1080, None, {"space": 0.15, "airDefense": 0.03}, None),
    ("ew", "cyber", "Guerre électronique (brouilleurs, leurres)", "Aveugler drones et radars ennemis.", 0.4, 360, None, {"drones": 0.05, "cyber": 0.05}, None),
]

START_CAPABILITIES = {"airDefense": 0.3, "strike": 0.35, "cyber": 0.35, "drones": 0.2, "space": 0.4, "naval": 0.45, "ammo": 0.2}
CAPABILITY_LABELS = {"airDefense": "Défense aérienne", "strike": "Frappe dans la profondeur", "cyber": "Cyberdéfense", "drones": "Drones",
                     "space": "Espace et renseignement", "naval": "Puissance navale", "ammo": "Production de munitions"}

BASES = [
    # id, nom, hôte (pays simulé ou ""), nom de l'hôte, coût annuel, ouverte au départ, risque d'éviction (par mois), influence, inquiète, coût d'ouverture
    ("djibouti", "Base de Djibouti (1 500 militaires)", "", "Djibouti", 0.3, True, 0.002, ["EGY", "SAU"], [], 0.5),
    ("abu_dhabi", "Base interarmées d'Abou Dabi", "", "Émirats arabes unis", 0.2, True, 0.0, ["SAU", "EGY"], [], 0.4),
    ("jordan", "Base aérienne projetée en Jordanie", "", "Jordanie", 0.15, True, 0.001, ["EGY", "SAU", "TUR"], [], 0.3),
    ("gabon", "Camp de Gaulle à Libreville (partagé)", "", "Gabon", 0.05, True, 0.004, ["MAR"], [], 0.2),
    ("romania", "Mission Aigle en Roumanie (Cincu)", "ROU", "Roumanie", 0.3, True, 0.0, ["ROU", "POL", "UKR"], ["RUS"], 0.4),
    ("estonia", "Mission Lynx en Estonie (Tapa)", "", "Estonie", 0.1, True, 0.0, ["POL", "NOR", "SWE"], ["RUS"], 0.2),
    ("cote_ivoire", "Port-Bouët à Abidjan", "", "Côte d'Ivoire", 0.1, False, 0.004, ["MAR"], [], 0.3),
    ("senegal", "Camp de Ouakam à Dakar", "", "Sénégal", 0.1, False, 0.006, ["MAR"], [], 0.3),
    ("chad", "Base de N'Djamena", "", "Tchad", 0.15, False, 0.008, ["EGY"], [], 0.3),
    ("crete", "Base navale de Souda (Crète)", "GRC", "Grèce", 0.15, False, 0.0, ["GRC"], ["TUR"], 0.4),
    ("india", "Point d'appui naval à Bombay", "IND", "Inde", 0.1, False, 0.0, ["IND"], ["CHN"], 0.3),
    ("poland", "Brigade française en Pologne", "POL", "Pologne", 0.4, False, 0.0, ["POL", "UKR", "DEU"], ["RUS", "BLR"], 0.6),
]

# Ventes d'armes (ajoutées aux produits d'exportation du commerce) : id, nom, secteur, valeur, délai, clients, clients sensibles
ARMS = [
    ("arms_rafale", "Rafale (24 appareils)", "aerospace", 8.0, 120, ["IND", "EGY", "SAU", "GRC", "BRA", "MAR", "ROU", "POL"], ["EGY", "SAU"]),
    ("arms_submarines", "Sous-marins Scorpène", "aerospace", 4.0, 150, ["IND", "BRA", "POL", "NLD", "MAR", "EGY"], ["EGY"]),
    ("arms_frigates", "Frégates de défense et d'intervention", "aerospace", 3.0, 120, ["GRC", "SAU", "EGY", "MAR", "NOR", "ROU"], ["SAU", "EGY"]),
    ("arms_caesar", "Canons CAESAR", "aerospace", 0.5, 60, ["UKR", "BEL", "ROU", "MAR", "SAU", "IND"], ["SAU"]),
    ("arms_sampt", "Défense sol-air SAMP/T", "aerospace", 1.5, 90, ["UKR", "ROU", "POL", "SAU", "EGY", "GRC"], ["SAU", "EGY"]),
    ("arms_helicopters", "Hélicoptères H225M Caracal", "aerospace", 1.2, 90, ["BRA", "IND", "SAU", "EGY", "MAR", "NLD"], ["SAU", "EGY"]),
    ("arms_mirage", "Mirage 2000-5 d'occasion", "aerospace", 1.0, 60, ["UKR", "GRC", "IND", "EGY", "BRA"], ["EGY"]),
    ("arms_griffon", "Blindés Griffon et Serval", "aerospace", 1.4, 90, ["BEL", "ROU", "MAR", "SAU", "EGY", "UKR"], ["SAU", "EGY"]),
    ("arms_tigre", "Hélicoptères de combat Tigre", "aerospace", 1.5, 120, ["ESP", "SAU", "IND", "BRA", "MAR"], ["SAU"]),
    ("arms_aster", "Missiles Aster 30", "aerospace", 0.8, 60, ["ITA", "GBR", "SAU", "EGY", "UKR", "GRC"], ["SAU", "EGY"]),
    ("arms_exocet", "Missiles antinavires Exocet", "aerospace", 0.4, 60, ["BRA", "IND", "MAR", "EGY", "GRC", "TUR"], ["EGY", "TUR"]),
    ("arms_gowind", "Corvettes Gowind", "aerospace", 1.6, 120, ["EGY", "ROU", "MAR", "SAU", "BRA"], ["EGY", "SAU"]),
    ("arms_satellites", "Satellites d'observation", "aerospace", 0.9, 120, ["MAR", "BRA", "IND", "EGY", "POL", "SAU"], ["EGY", "SAU"]),
    ("arms_shells", "Obus de 155 mm (contrat pluriannuel)", "aerospace", 0.6, 45, ["UKR", "POL", "ROU", "BEL", "NLD", "IND"], []),
    ("arms_drones", "Drones Aarok", "aerospace", 0.5, 90, ["UKR", "IND", "MAR", "BRA", "ROU"], []),
    ("arms_po", "Patrouilleurs hauturiers", "aerospace", 0.5, 90, ["MAR", "EGY", "BRA", "GRC", "TUN", "DZA"], ["EGY", "DZA"]),
]

snapshot = json.load(open(os.path.join(ROOT, "world_snapshots", "WORLD_SNAPSHOT_2026_10.json")))
countries = {c.split("/")[1] for c in snapshot["countries"]}
units = {t["id"] for t in json.load(open(os.path.join(ROOT, "military", "unit_types.json")))["types"]}
cats = {c[0] for c in CATEGORIES}
for e in EQUIPMENT:
    assert e[1] in cats and (e[6] is None or e[6] in units) and set(e[7]) <= set(START_CAPABILITIES) and (e[8] is None or e[8] in countries), e[0]
for b in BASES:
    assert (b[2] == "" or b[2] in countries) and set(b[7]) <= countries and set(b[8]) <= countries, b[0]

out = {
    "_doc": "Armement, bases et dissuasion. Généré par tools/datagen/defense_fr.py.",
    "categories": [{"id": i, "label": l, "icon": ic} for i, l, ic in CATEGORIES],
    "equipment": [dict({"id": i, "category": c, "label": l, "description": d, "costBillions": cost, "days": days, "capabilities": caps},
                       **({"unitType": u} if u else {}), **({"supplier": s} if s else {})) for i, c, l, d, cost, days, u, caps, s in EQUIPMENT],
    "capabilities": START_CAPABILITIES,
    "capabilityLabels": CAPABILITY_LABELS,
    "bases": [dict({"id": i, "label": l, "hostName": hn, "annualCostBillions": cost, "open": op, "evictionRisk": r, "influence": inf, "worries": w, "openCostBillions": oc},
                   **({"host": h} if h else {})) for i, l, h, hn, cost, op, r, inf, w, oc in BASES],
    "warheads": 290,
    "credibility": 0.7,
}
json.dump(out, open(os.path.join(ROOT, "military", "defense.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)

# Les ventes d'armes rejoignent les produits d'exportation.
trade_path = os.path.join(ROOT, "diplomacy", "trade.json")
trade = json.load(open(trade_path))
trade["products"] = [p for p in trade["products"] if not p.get("arms")] + [
    {"id": i, "label": l, "sector": s, "valueBillions": v, "delayDays": d, "clients": [c for c in cl if c in countries], "arms": True, "sensitive": sens}
    for i, l, s, v, d, cl, sens in ARMS]
json.dump(trade, open(trade_path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(EQUIPMENT)} équipements, {len(BASES)} bases, {len(ARMS)} ventes d'armes")
