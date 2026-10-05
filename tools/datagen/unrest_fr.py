"""Mouvements de contestation (France) -> assets/data/config/unrest.json.

Un mouvement naît d'une réforme ou d'une loi contestée, d'un événement (grève, jacquerie, émeutes)
ou de la colère accumulée (opinion, inflation, chômage). Il grossit ou s'essouffle chaque jour,
se radicalise sous la répression, et peut passer par cinq phases :
  manifestations -> grèves et blocages -> émeutes -> insurrection -> révolution (fin de partie).
Chaque cause : groupes sociaux et acteurs mobilisés, slogan, déclencheurs, foule de départ,
concession possible (ses effets), secteurs touchés par les blocages.
"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
G = "opinion.group."


def e(target, amount, days=0):
    d = {"target": target, "amount": amount}
    if days: d["days"] = days
    return d


CAUSES = [
    # id, nom, slogan, réformes, lois, événements, groupes, acteurs, foule de départ (milliers), concession (nom, effets), secteurs bloqués
    ("pensions", "Contre la réforme des retraites", "« 64 ans, c'est non ! »", ["pension_age_65"], [], [],
     ["private_employees", "civil_servants", "low_income"], ["union_militant", "union_reformist", "union_public"], 300,
     ("Suspendre la réforme jusqu'à la présidentielle", [e("budget.oneOff", 4.0, 365), e("economy.businessConfidence", -0.01), e(G + "seniors", 0.01), e(G + "private_employees", 0.02)]),
     {"transport": -0.04, "energy": -0.02}),
    ("cost_of_living", "La vie chère", "« On ne peut plus remplir le frigo ! »", [], [], ["fuel_tax_protest", "fuel_protest_hardens", "fuel_shortage"],
     ["low_income", "rural", "self_employed"], ["ngo_consumers"], 120,
     ("Chèque inflation et gel des taxes sur le carburant", [e("budget.oneOff", 6.0), e(G + "low_income", 0.02), e(G + "rural", 0.015)]),
     {"retail": -0.03, "transport": -0.03}),
    ("farmers", "La colère paysanne", "« Pas de pays sans paysans »", [], [], ["farmers_protest", "mercosur_deal"],
     ["rural", "self_employed"], ["farmers_union"], 60,
     ("Plan d'urgence agricole et pause des normes", [e("budget.oneOff", 2.0), e("quality.environment", -0.01), e(G + "rural", 0.02)]),
     {"agrifood": -0.03, "transport": -0.02}),
    ("students", "La jeunesse dans la rue", "« Ni chair à canon, ni chair à patron »", [], [], ["student_protest"],
     ["young"], ["union_militant"], 80,
     ("Revalorisation des bourses et repas à 1 €", [e("budget.oneOff", 1.5), e(G + "young", 0.02)]),
     {}),
    ("labour", "Contre la loi Travail", "« Loi Travail, non merci ! »", ["labour_code", "unemployment_insurance"], ["work_week", "strike_service", "sunday_work", "unemployment_rules"], [],
     ["private_employees", "young", "low_income"], ["union_militant", "union_public"], 200,
     ("Retirer les mesures les plus contestées", [e("economy.businessConfidence", -0.008), e(G + "private_employees", 0.015)]),
     {"transport": -0.03}),
    ("ecology", "La marche pour le climat", "« Il n'y a pas de planète B »", ["nuclear_program"], ["fracking"], ["cop_climate", "heatwave"],
     ["young", "urban"], ["ngo_environment"], 100,
     ("Loi climat renforcée et fin des forages", [e("budget.oneOff", 2.0), e("economy.businessConfidence", -0.005), e(G + "young", 0.02)]),
     {}),
    ("police", "Justice pour les victimes de violences policières", "« Pas de justice, pas de paix »", [], ["police_cameras", "right_to_protest", "facial_recognition", "mass_surveillance"], ["urban_riots"],
     ["young", "urban"], ["ngo_rights"], 50,
     ("Réforme de l'IGPN et caméras-piétons obligatoires", [e("quality.security", -0.005), e(G + "young", 0.02), e(G + "urban", 0.01)]),
     {"retail": -0.02}),
    ("society", "La manif pour tous", "« Touche pas à nos enfants »", [], ["end_of_life", "surrogacy", "cannabis"], [],
     ["seniors", "rural"], ["church"], 150,
     ("Retirer le texte de l'ordre du jour", [e(G + "seniors", 0.015), e(G + "young", -0.01)]),
     {}),
    ("immigration", "Contre la loi immigration", "« Solidarité avec les sans-papiers »", ["immigration_strict"], ["nationality", "family_reunification"], [],
     ["young", "urban"], ["ngo_rights", "muslim_council"], 80,
     ("Retirer les articles les plus durs", [e(G + "urban", 0.01), e(G + "seniors", -0.01)]),
     {}),
    ("public_services", "Sauvons nos services publics", "« L'hôpital, pas la charité »", [], [], ["hospital_strike", "teachers_strike", "post_office_closure"],
     ["civil_servants", "rural"], ["union_public"], 100,
     ("Plan d'urgence pour l'hôpital et l'école", [e("budget.oneOff", 5.0), e("quality.health", 0.01), e(G + "civil_servants", 0.02)]),
     {"health": -0.02}),
    ("anger", "La colère sociale", "« Démission ! Démission ! »", [], [], ["national_strike", "protest"],
     ["low_income", "middle_income", "rural"], ["union_militant"], 150,
     ("Grand débat national et mesures pour le pouvoir d'achat", [e("budget.oneOff", 8.0), e(G + "low_income", 0.02), e(G + "middle_income", 0.01)]),
     {"retail": -0.03, "transport": -0.03, "tourism": -0.03}),
    ("war", "Non à la guerre", "« Pas en notre nom »", [], [], [],
     ["young", "urban"], ["ngo_rights"], 100,
     ("Ouvrir des négociations de paix", [e(G + "young", 0.02)]),
     {}),
]

PHASES = [
    # id, nom, seuil de manifestants (milliers), seuil de radicalité
    ("MARCHES", "Manifestations", 0, 0.0),
    ("BLOCKADES", "Grèves et blocages", 400, 0.0),
    ("RIOTS", "Émeutes", 150, 0.5),
    ("INSURRECTION", "Insurrection", 800, 0.75),
]

snapshot_groups = json.load(open(os.path.join(ROOT, "countries", "FRA", "social_groups.json")))
groups = set()
def walk(x):
    if isinstance(x, dict):
        if "id" in x: groups.add(x["id"])
        for v in x.values(): walk(v)
    elif isinstance(x, list):
        for v in x: walk(v)
walk(snapshot_groups)
reforms = {r["id"] for r in json.load(open(os.path.join(ROOT, "countries", "FRA", "reforms.json")))["reforms"]}
laws = {l["id"] for l in json.load(open(os.path.join(ROOT, "countries", "FRA", "laws.json")))["laws"]}
actors = {a["id"] for a in json.load(open(os.path.join(ROOT, "countries", "FRA", "actors.json")))["actors"]}
sectors = {s["id"] for s in json.load(open(os.path.join(ROOT, "countries", "FRA", "sectors.json")))["sectors"]}
events = set()
for f in os.listdir(os.path.join(ROOT, "events")):
    events |= {x["id"] for x in json.load(open(os.path.join(ROOT, "events", f))).get("events", [])}

causes = []
for cid, label, slogan, rf, lw, ev, gr, ac, crowd, (clabel, ceff), blocks in CAUSES:
    assert set(rf) <= reforms, (cid, set(rf) - reforms)
    assert set(lw) <= laws, (cid, set(lw) - laws)
    assert set(gr) <= groups, (cid, set(gr) - groups)
    assert set(ac) <= actors, (cid, set(ac) - actors)
    assert set(blocks) <= sectors, cid
    ev = [x for x in ev if x in events]
    causes.append({"id": cid, "label": label, "slogan": slogan, "reforms": rf, "laws": lw, "events": ev, "groups": gr, "actors": ac,
                   "crowdThousands": crowd, "concession": {"label": clabel, "effects": ceff}, "blockades": blocks})

out = {
    "_doc": "Mouvements de contestation, phases, armée. Généré par tools/datagen/unrest_fr.py.",
    "causes": causes,
    "phases": [{"id": i, "label": l, "crowdThousands": c, "radicalization": r} for i, l, c, r in PHASES],
    "armyLoyalty": 0.8,
}
json.dump(out, open(os.path.join(ROOT, "config", "unrest.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(causes)} causes de contestation")
