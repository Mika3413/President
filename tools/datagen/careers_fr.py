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

# Atout et faiblesse durables : petit effet mensuel, crises atténuées (facteur < 1) ou aggravées (> 1).
PERKS = {
    "haut_fonctionnaire": ("Connaît l'État : les crises budgétaires sont mieux contenues", "Jugé technocrate par les classes populaires",
        [e(G + "low_income", -0.0015)], {"rating_downgrade": 0.7, "eu_deficit_procedure": 0.7, "pension_deficit": 0.8}),
    "avocat": ("Sait se défendre : les scandales font moins de dégâts", "Soupçonné de servir les puissants",
        [e(G + "middle_income", -0.001)], {"minister_scandal": 0.7, "president_scandal": 0.7, "minister_indicted": 0.75}),
    "entrepreneur": ("Les patrons le croient : fermetures et délocalisations mieux gérées", "Les grèves le visent davantage",
        [e("economy.businessConfidence", 0.002), e(G + "private_employees", -0.0015)], {"factory_closure": 0.75, "factory_relocation": 0.75, "bankruptcy": 0.8, "national_strike": 1.2}),
    "medecin": ("Crédible en santé : épidémies et hôpitaux débordés pèsent moins", "Peu à l'aise avec l'économie",
        [e(G + "seniors", 0.0015), e("economy.businessConfidence", -0.001)], {"epidemic": 0.7, "new_virus": 0.7, "pandemic_wave": 0.75, "hospital_overload": 0.75, "bird_flu": 0.8}),
    "militaire": ("L'autorité : attentats et menaces mieux encaissés, armée fidèle", "Les jeunes s'en méfient",
        [e("military.readiness", 0.002), e(G + "young", -0.0015)], {"terror_attack": 0.8, "drone_threat": 0.75, "embassy_attack": 0.8, "hybrid_sabotage": 0.8}),
    "enseignant": ("Parle aux enseignants : moins de grèves dans l'éducation", "Peu de crédit auprès des chefs d'entreprise",
        [e(G + "civil_servants", 0.0015), e("economy.businessConfidence", -0.001)], {"teachers_strike": 0.6, "student_protest": 0.75, "teacher_shortage": 0.8}),
    "syndicaliste": ("Les syndicats le respectent : grèves plus courtes", "Les marchés et les patrons s'inquiètent",
        [e(G + "private_employees", 0.0015), e("economy.businessConfidence", -0.002)], {"national_strike": 0.7, "rail_strike": 0.7, "port_strike": 0.7, "doctor_strike": 0.8}),
    "diplomate": ("Carnet d'adresses : incidents diplomatiques désamorcés", "Paraît loin des préoccupations des Français",
        [e(G + "rural", -0.0012)], {"diplomatic_incident": 0.65, "fishing_dispute": 0.7, "hostages_abroad": 0.8, "trade_tariffs": 0.8}),
    "agriculteur": ("La France rurale le suit : crises agricoles adoucies", "Les villes le trouvent dépassé",
        [e(G + "rural", 0.002), e(G + "urban", -0.0015)], {"farmers_protest": 0.65, "farmers_local": 0.7, "wine_hail": 0.75, "drought": 0.8, "wine_crisis": 0.75}),
    "journaliste": ("Maîtrise des médias : rumeurs et polémiques retombent vite", "Accusé de privilégier la communication",
        [e("opinion.national", 0.0008), e(G + "high_income", -0.001)], {"deepfake_president": 0.6, "social_media_harassment": 0.7, "foreign_interference": 0.8}),
    "elu_local": ("Homme de terrain : les colères locales s'apaisent plus vite", "Peu de poids à Bruxelles",
        [e(G + "middle_income", 0.0012), e("alliance.EU.NEGOTIATION_GOODWILL", -0.002)], {"city_demonstration": 0.75, "post_office_closure": 0.7, "medical_desert": 0.8}),
    "chercheur": ("Méthode et rigueur : crises techniques et sanitaires mieux traitées", "Peu de réseaux : la majorité est plus rétive",
        [e("government.parliamentSupport", -0.0015), e(G + "young", 0.001)], {"nuclear_incident": 0.75, "nuclear_plant_leak": 0.75, "ai_layoffs": 0.75, "space_failure": 0.7}),
}

TARGETS = re.compile(r"^(government\.parliamentSupport|opinion\.national|opinion\.group\.\w+|economy\.businessConfidence|quality\.\w+|military\.readiness|alliance\.(EU|NATO)\.[A-Z_]+)$")
TRAITS = {"aggressiveness", "pragmatism", "nationalism", "openness", "ego", "caution", "integrity", "charisma", "toughness", "militarism"}
groups = {g["id"] for g in json.load(open(os.path.join(ROOT, "countries", "FRA", "social_groups.json")))["groups"]}
for c in CAREERS:
    assert set(c[4]) <= TRAITS, c[0]
    for fx in c[5]:
        assert TARGETS.match(fx["target"]), fx
        if fx["target"].startswith(G): assert fx["target"][len(G):] in groups, fx
events = set()
for f in os.listdir(os.path.join(ROOT, "events")):
    if f.endswith(".json"):
        d = json.load(open(os.path.join(ROOT, "events", f)))
        for ev in (d.get("events", []) if isinstance(d, dict) else d):
            if isinstance(ev, dict) and "id" in ev: events.add(ev["id"])
for cid, (perk, flaw, monthly, factors) in PERKS.items():
    assert cid in {c[0] for c in CAREERS}, cid
    for fx in monthly: assert TARGETS.match(fx["target"]), fx
    for ev in factors: assert ev in events, (cid, ev)
out = {"_doc": "Parcours du président. Généré par tools/datagen/careers_fr.py.",
       "careers": [{"id": i, "label": l, "description": d, "skills": s, "traits": t, "effects": fx,
                    "perk": PERKS[i][0], "flaw": PERKS[i][1], "monthly": PERKS[i][2], "eventFactors": PERKS[i][3]} for i, l, d, s, t, fx in CAREERS]}
json.dump(out, open(os.path.join(ROOT, "countries", "FRA", "careers.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(CAREERS)} parcours")
