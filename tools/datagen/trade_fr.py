"""Matières premières, exportations et institutions internationales -> assets/data/diplomacy/trade.json.

Matières premières : prix mondiaux (cours de départ, volatilité, retour à la moyenne), poids dans
le prix de l'énergie et l'inflation françaises, secteurs touchés, grands producteurs (une guerre ou
des sanctions contre eux font flamber les cours), chocs d'événements, contrats possibles.
Exportations : produits phares que la France peut vendre, pays clients, valeur et secteur.
Les ventes d'armes sont ajoutées ensuite par defense_fr.py : lancer ce script après celui-ci.
"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")

COMMODITIES = [
    # id, nom, unité, cours, volatilité mensuelle, poids énergie, poids inflation, secteurs (+ si la hausse les aide), producteurs
    ("oil", "Pétrole (Brent)", "$/baril", 72, 0.07, 0.35, 0.02, {"transport": -0.25, "energy": 0.1, "tourism": -0.1, "industry": -0.05}, {"SAU": 0.35, "RUS": 0.25, "USA": 0.25, "NOR": 0.1, "CAN": 0.05}),
    ("gas", "Gaz naturel (TTF)", "€/MWh", 35, 0.12, 0.25, 0.015, {"industry": -0.25, "agrifood": -0.08, "energy": 0.05}, {"NOR": 0.35, "USA": 0.3, "RUS": 0.15, "DZA": 0.15}),
    ("uranium", "Uranium", "$/livre", 80, 0.05, 0.08, 0.0, {"energy": -0.1}, {"CAN": 0.35, "RUS": 0.25, "USA": 0.1}),
    ("wheat", "Blé", "€/tonne", 220, 0.06, 0.0, 0.01, {"agrifood": 0.15}, {"RUS": 0.25, "USA": 0.2, "UKR": 0.15, "CAN": 0.15, "IND": 0.1}),
    ("copper", "Cuivre", "$/tonne", 9500, 0.05, 0.0, 0.003, {"industry": -0.08, "construction": -0.06, "tech": -0.04}, {"CHN": 0.3, "USA": 0.1, "RUS": 0.1}),
    ("lithium", "Lithium", "$/tonne", 12000, 0.09, 0.0, 0.0, {"industry": -0.08, "tech": -0.04}, {"CHN": 0.6, "BRA": 0.1}),
    ("gold", "Or", "$/once", 2600, 0.04, 0.0, 0.0, {"banking": 0.03, "luxury": -0.03}, {"CHN": 0.2, "RUS": 0.15, "USA": 0.1, "CAN": 0.1}),
]

# Stocks stratégiques (jours de consommation au départ) et coût d'un mois de stock au cours de référence (Md€).
RESERVES = {"oil": (90, 2.4), "gas": (60, 2.5)}

EVENT_SHOCKS = {
    "gas_price_spike": {"gas": 0.5},
    "fuel_shortage": {"oil": 0.15},
    "fuel_blockade": {"oil": 0.08},
    "drought": {"wheat": 0.2},
    "drought_abroad": {"wheat": 0.15},
    "cold_wave": {"gas": 0.2},
    "refinery_accident": {"oil": 0.05},
    "nuclear_incident": {"uranium": 0.05},
    "trade_tariffs": {"copper": 0.05},
}

# Contrats d'approvisionnement à long terme : pays, matière, rabais, durée (années), relation minimale.
CONTRACTS = [
    ("NOR", "gas", 0.10, 3, 0.45), ("USA", "gas", 0.05, 3, 0.45), ("DZA", "gas", 0.12, 3, 0.4),
    ("SAU", "oil", 0.06, 3, 0.4), ("NOR", "oil", 0.04, 3, 0.45), ("CAN", "uranium", 0.10, 5, 0.45),
    ("BRA", "lithium", 0.10, 3, 0.4), ("CAN", "copper", 0.05, 3, 0.45),
]

# Produits que la France exporte : id, nom, secteur, valeur d'un contrat (Md€), délai (jours), pays intéressés.
PRODUCTS = [
    ("airliners", "Avions de ligne", "aerospace", 12.0, 45, ["USA", "CHN", "IND", "SAU", "TUR", "JPN", "EGY", "BRA"]),
    ("nuclear_plant", "Centrales nucléaires", "energy", 20.0, 120, ["IND", "POL", "CZE", "SAU", "EGY", "GBR", "NLD", "SWE"]),
    ("high_speed_rail", "Trains à grande vitesse", "industry", 6.0, 90, ["USA", "CAN", "IND", "MAR", "EGY", "SAU", "BRA"]),
    ("satellites", "Satellites et lanceurs", "aerospace", 2.0, 60, ["IND", "BRA", "SAU", "EGY", "JPN", "CAN"]),
    ("wheat", "Céréales", "agrifood", 1.5, 30, ["DZA", "MAR", "EGY", "SAU", "TUN"]),
    ("wine", "Vins et spiritueux", "agrifood", 1.0, 30, ["USA", "CHN", "GBR", "JPN", "CAN", "BRA"]),
    ("luxury", "Luxe et cosmétiques", "luxury", 2.5, 30, ["CHN", "USA", "JPN", "SAU", "IND", "GBR"]),
    ("pharma", "Médicaments et vaccins", "health", 1.5, 45, ["EGY", "MAR", "TUN", "DZA", "BRA", "IND"]),
    ("water_services", "Eau et services urbains", "construction", 1.0, 60, ["SAU", "MAR", "EGY", "IND", "CHN"]),
]

snapshot = json.load(open(os.path.join(ROOT, "world_snapshots", "WORLD_SNAPSHOT_2026_10.json")))
countries = {c.split("/")[1] for c in snapshot["countries"]}
sectors = {s["id"] for s in json.load(open(os.path.join(ROOT, "countries", "FRA", "sectors.json")))["sectors"]}
events = set()
for f in os.listdir(os.path.join(ROOT, "events")):
    events |= {x["id"] for x in json.load(open(os.path.join(ROOT, "events", f))).get("events", [])}
ids = {c[0] for c in COMMODITIES}
for c in COMMODITIES:
    assert set(c[7]) <= sectors and set(c[8]) <= countries, c[0]
assert set(EVENT_SHOCKS) <= events and all(set(v) <= ids for v in EVENT_SHOCKS.values())
for c in CONTRACTS:
    assert c[0] in countries and c[1] in ids, c
PRODUCTS = [(i, n, s, v, d, [x for x in cl if x in countries]) for i, n, s, v, d, cl in PRODUCTS]
for p in PRODUCTS:
    assert p[2] in sectors and p[5], p[0]

out = {
    "_doc": "Matières premières, exportations, institutions. Généré par tools/datagen/trade_fr.py.",
    "commodities": [{"id": i, "label": l, "unit": u, "basePrice": p, "volatility": v, "energyWeight": ew, "inflationWeight": iw, "sectors": s, "producers": pr,
                     **({"reserveDays": RESERVES[i][0], "refillCostBillions": RESERVES[i][1]} if i in RESERVES else {})}
                    for i, l, u, p, v, ew, iw, s, pr in COMMODITIES],
    "eventShocks": EVENT_SHOCKS,
    "contracts": [{"country": c, "commodity": m, "discount": d, "years": y, "minRelation": r} for c, m, d, y, r in CONTRACTS],
    "products": [{"id": i, "label": n, "sector": s, "valueBillions": v, "delayDays": d, "clients": cl} for i, n, s, v, d, cl in PRODUCTS],
}
json.dump(out, open(os.path.join(ROOT, "diplomacy", "trade.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(COMMODITIES)} matières premières, {len(CONTRACTS)} contrats, {len(PRODUCTS)} produits")
