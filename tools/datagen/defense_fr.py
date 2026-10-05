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
