"""Secteurs, grandes entreprises et Bourse (France) -> assets/data/countries/FRA/sectors.json.

Chaque secteur a un poids dans le PIB et l'emploi et réagit à des moteurs de l'économie
(moral des ménages, prix de l'énergie, taux, croissance mondiale, mesures de crise...).
Les entreprises sont fictives ; leur cours suit leur secteur. Certains événements frappent
directement des secteurs (attentat : tourisme ; droits de douane : luxe...).
Moteurs : consumer, business, energy, rates, world, security, defense, agriculture, restrictions, growth.
"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")

SECTORS = [
    # id, libellé, icône, part du PIB, part de l'emploi, sensibilités
    ("industry", "Industrie et automobile", "⚙", 0.10, 0.10, {"business": 0.5, "energy": -0.25, "world": 3.0, "growth": 2.0}),
    ("aerospace", "Aéronautique et défense", "✈", 0.03, 0.02, {"world": 4.0, "defense": 0.6, "business": 0.3}),
    ("luxury", "Luxe et cosmétiques", "◆", 0.03, 0.01, {"world": 5.0, "consumer": 0.2, "restrictions": -0.15}),
    ("energy", "Énergie", "⚡", 0.04, 0.01, {"energy": 0.3, "business": 0.2}),
    ("banking", "Banque et assurance", "€", 0.05, 0.03, {"rates": 2.0, "business": 0.6, "growth": 2.0}),
    ("agrifood", "Agriculture et agroalimentaire", "♣", 0.04, 0.05, {"agriculture": 0.8, "consumer": 0.2, "world": 1.0}),
    ("tourism", "Tourisme et restauration", "☀", 0.08, 0.07, {"consumer": 0.6, "security": 1.0, "restrictions": -0.6, "world": 2.0}),
    ("construction", "BTP et immobilier", "⌂", 0.06, 0.07, {"rates": -6.0, "consumer": 0.5, "business": 0.3}),
    ("tech", "Numérique et télécoms", "◎", 0.06, 0.04, {"business": 0.7, "world": 2.0, "growth": 1.5}),
    ("retail", "Commerce et distribution", "▣", 0.10, 0.12, {"consumer": 0.8, "restrictions": -0.25, "growth": 1.0}),
    ("transport", "Transports et logistique", "⇢", 0.05, 0.05, {"energy": -0.2, "business": 0.4, "restrictions": -0.3, "world": 1.5}),
    ("health", "Santé et pharmacie", "✚", 0.04, 0.04, {"business": 0.2, "restrictions": 0.05}),
]

COMPANIES = [
    # id, nom, secteur, capitalisation (Md€), salariés en France, sensibilité au secteur
    ("delmas", "Automobiles Delmas", "industry", 45, 60000, 1.4),
    ("acieries", "Aciéries de Lorraine", "industry", 12, 18000, 1.6),
    ("aerolis", "Aérolis", "aerospace", 120, 55000, 1.2),
    ("navalis", "Navalis Défense", "aerospace", 30, 25000, 0.9),
    ("verlaine", "Maison Verlaine", "luxury", 320, 40000, 1.1),
    ("belleroche", "Belleroche Cosmétiques", "luxury", 190, 15000, 0.9),
    ("energiefrance", "ÉnergieFrance", "energy", 70, 130000, 0.7),
    ("petrolia", "Pétrolia", "energy", 130, 35000, 1.0),
    ("hexagone", "Banque Hexagone", "banking", 75, 60000, 1.3),
    ("provinces", "Crédit des Provinces", "banking", 40, 70000, 1.1),
    ("lafayette", "Assurances Lafayette", "banking", 60, 30000, 0.9),
    ("laiteries", "Laiteries Réunies", "agrifood", 55, 45000, 0.6),
    ("riviera", "Hôtels Riviera", "tourism", 25, 40000, 1.5),
    ("batir", "Groupe Bâtir", "construction", 50, 90000, 1.2),
    ("numeris", "Numéris", "tech", 35, 20000, 1.6),
    ("telecomhexa", "Télécom Hexa", "tech", 40, 50000, 0.7),
    ("marcheplus", "Marché Plus", "retail", 30, 110000, 0.8),
    ("railroute", "Rail & Route", "transport", 20, 45000, 1.0),
    ("pharmaxis", "Pharmaxis", "health", 110, 25000, 0.6),
]

# Participations de l'État au début de la partie (part du capital).
STAKES = {"energiefrance": 1.0, "railroute": 1.0, "navalis": 0.62, "telecomhexa": 0.23, "aerolis": 0.11, "delmas": 0.06, "laiteries": 0.0}

# Effets directs d'événements sur les secteurs (choc d'activité, suit l'ampleur de l'événement).
EVENT_SHOCKS = {
    "terror_attack": {"tourism": -0.05, "retail": -0.01},
    "pandemic_wave": {"tourism": -0.12, "retail": -0.04, "transport": -0.05, "health": 0.03},
    "epidemic": {"tourism": -0.04, "health": 0.02},
    "new_virus": {"tourism": -0.02, "health": 0.03},
    "wine_crisis": {"agrifood": -0.05},
    "ski_no_snow": {"tourism": -0.03},
    "trade_tariffs": {"luxury": -0.05, "aerospace": -0.03, "agrifood": -0.03},
    "arms_contract": {"aerospace": 0.04},
    "nuclear_incident": {"energy": -0.04},
    "bank_run": {"banking": -0.15, "construction": -0.04},
    "bank_fragility": {"banking": -0.08},
    "bankruptcy": {"industry": -0.04},
    "factory_closure": {"industry": -0.01},
    "investment_announcement": {"industry": 0.03},
    "space_failure": {"aerospace": -0.03},
    "space_success": {"aerospace": 0.02},
    "ai_champion": {"tech": 0.06},
    "ai_exodus": {"tech": -0.06},
    "ai_layoffs": {"tech": 0.02},
    "tech_hq": {"tech": 0.03},
    "national_strike": {"transport": -0.03, "retail": -0.02},
    "rail_strike": {"transport": -0.04, "tourism": -0.01},
    "strike_spreads": {"transport": -0.05, "energy": -0.03},
    "port_strike": {"transport": -0.03, "agrifood": -0.01},
    "fuel_shortage": {"transport": -0.05, "retail": -0.01},
    "fuel_blockade": {"transport": -0.06},
    "gas_price_spike": {"industry": -0.04, "agrifood": -0.02},
    "blackout": {"industry": -0.03, "tech": -0.01},
    "heatwave": {"agrifood": -0.02, "tourism": 0.01},
    "drought": {"agrifood": -0.05, "energy": -0.01},
    "cyberattack": {"tech": -0.02, "banking": -0.01},
    "hybrid_cyber": {"tech": -0.03, "banking": -0.02},
    "urban_riots": {"retail": -0.02, "tourism": -0.02},
    "rating_downgrade": {"banking": -0.06, "construction": -0.02},
    "mercosur_deal": {"agrifood": -0.03, "industry": 0.01},
    "winter_olympics": {"tourism": 0.03, "construction": 0.02},
    "sports_victory": {"retail": 0.01},
    "refinery_accident": {"energy": -0.03},
    "storm": {"construction": 0.01, "agrifood": -0.01},
    "flood": {"construction": 0.01, "agrifood": -0.01},
    "cyclone": {"tourism": -0.02},
    "wildfire": {"tourism": -0.01},
    "bird_flu": {"agrifood": -0.04},
    "food_scandal": {"agrifood": -0.04, "retail": -0.01},
    "drug_shortage": {"health": -0.03},
    "farmers_protest": {"agrifood": -0.02, "transport": -0.01},
}

# Mesures de crise qui pèsent sur l'activité (moteur « restrictions », 1 = confinement strict).
RESTRICTIONS = {"lockdown": 1.0, "curfew": 0.35, "local_lockdown": 0.05, "local_curfew": 0.02, "health_borders": 0.4,
                "gathering_ban": 0.15, "fuel_rationing": 0.3, "load_shedding": 0.2, "masks": 0.03, "forest_ban": 0.02}

sector_ids = {s[0] for s in SECTORS}
events = set()
for f in os.listdir(os.path.join(ROOT, "events")):
    events |= {e["id"] for e in json.load(open(os.path.join(ROOT, "events", f))).get("events", [])}
measures = {m["id"] for m in json.load(open(os.path.join(ROOT, "countries", "FRA", "measures.json")))["measures"]}
assert all(c[2] in sector_ids for c in COMPANIES)
assert set(EVENT_SHOCKS) <= events, set(EVENT_SHOCKS) - events
assert all(set(v) <= sector_ids for v in EVENT_SHOCKS.values())
assert set(RESTRICTIONS) <= measures, set(RESTRICTIONS) - measures
assert abs(sum(s[3] for s in SECTORS) - 0.68) < 0.01  # le reste du PIB : administrations, services non marchands...

out = {
    "_doc": "Secteurs, entreprises (fictives) et Bourse. Généré par tools/datagen/sectors_fr.py.",
    "indexName": "Indice de Paris",
    "indexBase": 7800,
    "sectors": [{"id": i, "label": l, "icon": ic, "gdpShare": g, "jobShare": j, "drivers": d} for i, l, ic, g, j, d in SECTORS],
    "companies": [dict({"id": i, "name": n, "sector": s, "capBillions": c, "employees": e, "beta": b}, **({"stateStake": STAKES[i]} if i in STAKES else {})) for i, n, s, c, e, b in COMPANIES],
    "eventShocks": EVENT_SHOCKS,
    "restrictions": RESTRICTIONS,
}
json.dump(out, open(os.path.join(ROOT, "countries", "FRA", "sectors.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(SECTORS)} secteurs, {len(COMPANIES)} entreprises, {len(EVENT_SHOCKS)} chocs d'événements")
