"""Services secrets et groupes armés (France) -> assets/data/military/intel.json.

Opérations de la DGSE à l'étranger, chacune d'un type géré par le moteur :
  political (percer les intentions d'un dirigeant), military (connaître ses forces), economic
  (espionnage industriel), influence (campagne d'influence), opposition (financer l'opposition),
  sabotage, coup (soutenir un coup d'État), neutralize (neutraliser un chef d'un groupe armé).
Chaque opération : coût, durée, chance de base (modulée par la capacité des services), risque
d'être découverte en cas d'échec, réservée ou non aux pays hostiles.

Groupes armés et terroristes : force (0..1), hostilité envers la France, pays où ils opèrent,
parrain éventuel, événements dont ils font monter le risque (poids), actions possibles :
  neutralize (DGSE), strike (frappes aériennes), infiltrate (DGSI), negotiate, dissolve.
"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")

OPERATIONS = [
    # id, type, nom, description, coût (Md€), durée (jours), chance, risque d'être découvert, pays hostiles seulement
    ("spy_leader", "political", "Percer les intentions du dirigeant", "Sources humaines et interceptions : son caractère, ses projets, ce qu'il pense vraiment de la France.", 0.02, 30, 0.75, 0.15, False),
    ("spy_military", "military", "Cartographier ses forces armées", "Satellites, sources, interceptions : ses unités, leur état, ses plans.", 0.03, 45, 0.7, 0.15, False),
    ("spy_economy", "economic", "Espionnage industriel", "Récupérer secrets de fabrication et offres concurrentes pour nos champions.", 0.02, 60, 0.6, 0.3, False),
    ("influence", "influence", "Campagne d'influence", "Médias, réseaux sociaux, relais d'opinion : affaiblir le pouvoir en place.", 0.05, 90, 0.55, 0.3, True),
    ("fund_opposition", "opposition", "Financer l'opposition", "Argent, conseils, protection : préparer une alternance favorable à la France.", 0.1, 120, 0.5, 0.35, True),
    ("sabotage", "sabotage", "Sabotage", "Usines d'armement, dépôts, réseaux : ralentir sa machine de guerre.", 0.08, 45, 0.5, 0.4, True),
    ("coup", "coup", "Soutenir un coup d'État", "Des militaires prêts à renverser le pouvoir attendent un signal et des moyens.", 1.0, 90, 0.3, 0.7, True),
]

GROUPS = [
    # id, nom, type, description, où (texte), pays d'implantation simulés, force, hostilité, parrain, événements (poids), actions, intérieur
    ("ei", "État islamique", "jihadist", "Le califat est tombé mais l'organisation inspire et commandite toujours des attentats en Europe.",
     "Syrie, Irak, Sahel, Afghanistan", [], 0.45, 0.95, None, {"terror_attack": 1.0, "embassy_attack": 0.5, "hostages_abroad": 0.4}, ["neutralize", "strike", "infiltrate"], False),
    ("aqmi", "Al-Qaïda au Maghreb islamique", "jihadist", "Implantée au Sahel et en Afrique du Nord, spécialiste des enlèvements.",
     "Sahel, sud de l'Algérie", ["DZA"], 0.35, 0.85, None, {"hostages_abroad": 1.0, "embassy_attack": 0.5, "terror_attack": 0.3}, ["neutralize", "strike", "infiltrate"], False),
    ("jnim", "JNIM (Sahel)", "jihadist", "La coalition jihadiste qui gagne du terrain au Mali, au Burkina Faso et au Niger depuis le départ de Barkhane.",
     "Mali, Burkina Faso, Niger", [], 0.55, 0.8, None, {"hostages_abroad": 0.8, "embassy_attack": 0.6}, ["neutralize", "strike"], False),
    ("africa_corps", "Africa Corps (ex-Wagner)", "mercenary", "Les mercenaires russes protègent les juntes du Sahel et attisent le sentiment antifrançais.",
     "Mali, Centrafrique, Libye", [], 0.5, 0.6, "RUS", {"embassy_attack": 0.4, "hybrid_sabotage": 0.3}, ["neutralize", "infiltrate"], False),
    ("hezbollah", "Hezbollah", "militia", "Milice et parti libanais, armé par l'Iran ; menace les soldats de la FINUL et les intérêts français au Liban.",
     "Liban", [], 0.5, 0.4, None, {"hostages_abroad": 0.3, "embassy_attack": 0.3}, ["neutralize", "infiltrate", "negotiate"], False),
    ("pkk", "PKK", "separatist", "La guérilla kurde, combattue par la Turquie ; ses réseaux en Europe financent la lutte.",
     "Turquie, Irak", ["TUR"], 0.4, 0.15, None, {}, ["infiltrate", "negotiate"], False),
    ("flnc", "FLNC (Corse)", "separatist", "Le Front de libération nationale corse a repris les plasticages après des années de trêve.",
     "Corse", [], 0.25, 0.5, None, {"corsica_tensions": 1.0}, ["infiltrate", "negotiate"], True),
    ("ultra_right", "Groupuscules d'ultradroite", "extremist", "Petits groupes violents, parfois armés, qui préparent des actions contre des mosquées ou des élus.",
     "France", [], 0.25, 0.4, None, {"terror_attack": 0.3, "urban_riots": 0.2}, ["infiltrate", "dissolve"], True),
    ("ultra_left", "Ultragauche et black blocs", "extremist", "Casse dans les manifestations, sabotages de chantiers et de réseaux.",
     "France", [], 0.3, 0.35, None, {"urban_riots": 0.4, "hybrid_sabotage": 0.2}, ["infiltrate", "dissolve"], True),
    ("narco", "Narcotrafic (DZ Mafia et autres)", "criminal", "Les réseaux de la drogue tuent à Marseille, corrompent des fonctionnaires et défient l'État.",
     "Marseille, Grenoble, Nîmes, Antilles", ["MAR"], 0.5, 0.3, None, {"urban_riots": 0.3}, ["neutralize", "infiltrate"], True),
]

snapshot = json.load(open(os.path.join(ROOT, "world_snapshots", "WORLD_SNAPSHOT_2026_10.json")))
countries = {c.split("/")[1] for c in snapshot["countries"]}
events = set()
for f in os.listdir(os.path.join(ROOT, "events")):
    events |= {e["id"] for e in json.load(open(os.path.join(ROOT, "events", f))).get("events", [])}
KINDS = {"political", "military", "economic", "influence", "opposition", "sabotage", "coup"}
ACTIONS = {"neutralize", "strike", "infiltrate", "negotiate", "dissolve"}
for o in OPERATIONS:
    assert o[1] in KINDS, o[0]
for g in GROUPS:
    assert set(g[5]) <= countries and (g[8] is None or g[8] in countries) and set(g[9]) <= events and set(g[10]) <= ACTIONS, (g[0], set(g[9]) - events)

out = {
    "_doc": "Services secrets et groupes armés. Généré par tools/datagen/intel_fr.py.",
    "capacity": 0.55,
    "operations": [{"id": i, "kind": k, "label": l, "description": d, "costBillions": c, "days": days, "success": s, "exposure": e, "hostileOnly": h}
                   for i, k, l, d, c, days, s, e, h in OPERATIONS],
    "groups": [dict({"id": i, "label": l, "kind": k, "description": d, "where": w, "countries": cs, "strength": st, "hostility": ho, "events": ev, "actions": a, "domestic": dom},
                    **({"sponsor": sp} if sp else {})) for i, l, k, d, w, cs, st, ho, sp, ev, a, dom in GROUPS],
}
json.dump(out, open(os.path.join(ROOT, "military", "intel.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(OPERATIONS)} opérations, {len(GROUPS)} groupes armés")
