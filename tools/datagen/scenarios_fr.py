"""Scénarios de départ -> assets/data/config/scenarios.json.

Un scénario part de la situation réelle et y ajoute une crise : effets immédiats, événements
lancés dès les premiers jours, guerres déclarées, soutien parlementaire imposé.
"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
G = "opinion.group."


def e(target, amount, days=0):
    d = {"target": target, "amount": amount}
    if days: d["days"] = days
    return d


def ev(id, scope=None, day=1):
    d = {"event": id, "day": day}
    if scope: d["scope"] = scope
    return d


SCENARIOS = [
    {"id": "standard", "label": "La France d'octobre 2026", "icon": "★",
     "description": "La situation réelle : croissance molle, dette élevée, Assemblée fragmentée. À vous de jouer."},
    {"id": "financial_crisis", "label": "Krach financier", "icon": "€",
     "description": "Une grande banque vacille, les marchés s'effondrent, le chômage va grimper. Sauver le système sans ruiner l'État.",
     "effects": [e("economy.businessConfidence", -0.12), e("economy.consumerConfidence", -0.08), e("economy.output", -0.01, 120),
                 e("sector.banking", -0.25), e("sector.construction", -0.1), e("opinion.national", -0.03)],
     "events": [ev("bank_fragility", day=2), ev("bankruptcy", day=10), ev("rating_downgrade", day=25)]},
    {"id": "pandemic", "label": "Pandémie", "icon": "⚕",
     "description": "Un nouveau virus se propage, les hôpitaux se remplissent. Confiner, vacciner, protéger l'économie : tout à la fois.",
     "effects": [e("quality.health", -0.05, 90), e("economy.consumerConfidence", -0.05), e("sector.tourism", -0.1)],
     "events": [ev("new_virus", day=1), ev("pandemic_wave", day=12), ev("drug_shortage", day=30)]},
    {"id": "war_europe", "label": "Guerre aux portes de l'Union", "icon": "⚔",
     "description": "La Russie attaque la Pologne. L'OTAN est sollicitée, l'énergie flambe, les réfugiés affluent. Jusqu'où engager la France ?",
     "effects": [e("economy.businessConfidence", -0.06), e("economy.inflation", 0.01, 180), e("sector.aerospace", 0.08), e("sector.energy", -0.05)],
     "wars": [{"attacker": "RUS", "defender": "POL"}],
     "events": [ev("gas_price_spike", day=5), ev("refugee_crisis", day=15)]},
    {"id": "hung_parliament", "label": "Majorité introuvable", "icon": "⌂",
     "description": "Aucune majorité à l'Assemblée, des frondeurs dans votre camp, une motion de censure dans l'air. Gouverner sans majorité.",
     "parliamentSupport": 0.32,
     "effects": [e("opinion.national", -0.04)],
     "events": [ev("majority_rebels", day=3), ev("group_split", day=40)]},
    {"id": "energy_winter", "label": "Hiver de pénuries", "icon": "⚡",
     "description": "Le gaz manque, des réacteurs sont à l'arrêt, le froid arrive. Délestages, sobriété, prix : l'hiver sera long.",
     "effects": [e("economy.inflation", 0.015, 180), e("economy.consumerConfidence", -0.05), e("sector.industry", -0.08)],
     "events": [ev("gas_price_spike", day=2), ev("cold_wave", day=20), ev("blackout", day=35)]},
]

events = set()
for f in os.listdir(os.path.join(ROOT, "events")):
    events |= {x["id"] for x in json.load(open(os.path.join(ROOT, "events", f))).get("events", [])}
for s in SCENARIOS:
    for x in s.get("events", []):
        assert x["event"] in events, x
json.dump({"_doc": "Scénarios de départ. Généré par tools/datagen/scenarios_fr.py.", "scenarios": SCENARIOS},
          open(os.path.join(ROOT, "config", "scenarios.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(SCENARIOS)} scénarios")
