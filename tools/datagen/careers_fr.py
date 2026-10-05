"""Parcours possibles du président (France) -> assets/data/countries/FRA/careers.json.

Chaque parcours modifie les compétences et le tempérament de départ, et a des effets au début
du mandat (réseaux, image auprès des groupes sociaux, relations).
"""
import json, os, re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
G = "opinion.group."


def e(target, amount):
    return {"target": target, "amount": amount}


CAREERS = [
    ("haut_fonctionnaire", "Haut fonctionnaire", "Grande école, inspection des finances, cabinets ministériels. Vous connaissez l'État par cœur.",
     {"competence": 0.1, "management": 0.12, "experience": 0.1}, {"pragmatism": 0.1, "charisma": -0.05},
     [e("government.parliamentSupport", 0.02), e(G + "low_income", -0.01), e(G + "high_income", 0.01)]),
    ("avocat", "Avocat", "Ténor du barreau : vous savez convaincre et défendre une cause.",
     {"competence": 0.05, "experience": 0.05}, {"charisma": 0.12, "integrity": 0.05},
     [e("quality.justice", 0.005), e("opinion.national", 0.005)]),
    ("entrepreneur", "Chef d'entreprise", "Vous avez créé et dirigé une entreprise. Les patrons vous font confiance, pas toujours les salariés.",
     {"management": 0.15}, {"pragmatism": 0.12, "caution": -0.05},
     [e("economy.businessConfidence", 0.03), e(G + "self_employed", 0.02), e(G + "low_income", -0.01)]),
    ("medecin", "Médecin", "Des années à l'hôpital : vous connaissez la santé et le terrain.",
     {"competence": 0.08}, {"integrity": 0.1, "caution": 0.05},
     [e("quality.health", 0.005), e(G + "seniors", 0.015)]),
    ("militaire", "Officier", "Saint-Cyr, opérations extérieures : l'autorité et le sens de l'État.",
     {"management": 0.08, "experience": 0.05}, {"toughness": 0.15, "militarism": 0.15},
     [e("military.readiness", 0.02), e(G + "seniors", 0.01), e(G + "young", -0.01)]),
    ("enseignant", "Professeur", "Vous avez enseigné en collège et lycée avant de vous engager.",
     {"competence": 0.05}, {"openness": 0.1, "integrity": 0.05},
     [e(G + "civil_servants", 0.02), e("quality.education", 0.004)]),
    ("syndicaliste", "Syndicaliste", "Vous avez mené des grèves et signé des accords : les salariés vous écoutent.",
     {"experience": 0.08}, {"toughness": 0.08, "charisma": 0.05},
     [e(G + "private_employees", 0.02), e(G + "low_income", 0.015), e("economy.businessConfidence", -0.02)]),
    ("diplomate", "Diplomate", "Ambassades et négociations internationales : le monde vous connaît.",
     {"competence": 0.06, "experience": 0.06}, {"openness": 0.1, "caution": 0.08},
     [e("alliance.EU.NEGOTIATION_GOODWILL", 0.02), e("alliance.NATO.NEGOTIATION_GOODWILL", 0.01)]),
    ("agriculteur", "Agriculteur", "Exploitant puis responsable syndical agricole : la France rurale est derrière vous.",
     {"management": 0.05}, {"nationalism": 0.05, "toughness": 0.05},
     [e(G + "rural", 0.03), e(G + "urban", -0.01), e("quality.agriculture", 0.005)]),
    ("journaliste", "Journaliste", "Plume et visage connus de la télévision : vous maîtrisez les médias.",
     {"experience": 0.03}, {"charisma": 0.15, "ego": 0.05},
     [e("opinion.national", 0.01), e(G + "young", 0.005)]),
    ("elu_local", "Élu local", "Maire puis président de région : vous avez fait vos preuves sur le terrain.",
     {"management": 0.1, "experience": 0.08}, {"pragmatism": 0.08},
     [e(G + "rural", 0.01), e(G + "middle_income", 0.01)]),
    ("chercheur", "Scientifique", "Chercheur reconnu, vous arrivez avec une méthode et peu de réseaux politiques.",
     {"competence": 0.15}, {"openness": 0.1, "charisma": -0.05},
     [e("government.parliamentSupport", -0.02), e(G + "high_income", 0.01), e(G + "young", 0.01)]),
]

TARGETS = re.compile(r"^(government\.parliamentSupport|opinion\.national|opinion\.group\.\w+|economy\.businessConfidence|quality\.\w+|military\.readiness|alliance\.(EU|NATO)\.[A-Z_]+)$")
TRAITS = {"aggressiveness", "pragmatism", "nationalism", "openness", "ego", "caution", "integrity", "charisma", "toughness", "militarism"}
groups = {g["id"] for g in json.load(open(os.path.join(ROOT, "countries", "FRA", "social_groups.json")))["groups"]}
for c in CAREERS:
    assert set(c[4]) <= TRAITS, c[0]
    for fx in c[5]:
        assert TARGETS.match(fx["target"]), fx
        if fx["target"].startswith(G): assert fx["target"][len(G):] in groups, fx
out = {"_doc": "Parcours du président. Généré par tools/datagen/careers_fr.py.",
       "careers": [{"id": i, "label": l, "description": d, "skills": s, "traits": t, "effects": fx} for i, l, d, s, t, fx in CAREERS]}
json.dump(out, open(os.path.join(ROOT, "countries", "FRA", "careers.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(CAREERS)} parcours")
