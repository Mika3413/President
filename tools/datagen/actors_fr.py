"""Acteurs organisés de la société (France) -> assets/data/countries/FRA/actors.json.

Syndicats, patronat, cultes, lobbies et ONG : chacun a une influence, une satisfaction qui suit
vos lois, réformes et l'état du pays, des revendications que vous pouvez satisfaire, et une
capacité de mobilisation quand il est mécontent (grèves, blocages, manifestations).
Préférences : laws (loi -> {option: poids}), reforms (réforme adoptée -> poids),
conditions (variable de la simulation -> poids, écart au début de la partie).
"""
import json, os, re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
G = "opinion.group."


def e(target, amount, days=0):
    d = {"target": target, "amount": amount}
    if days: d["days"] = days
    return d


def demand(id, label, cost, effects, opposed=()):
    return {"id": id, "label": label, "costBillions": cost, "effects": effects, "opposed": list(opposed)}


ACTORS = []


def actor(id, label, kind, icon, influence, satisfaction, groups, description, laws=None, reforms=None, conditions=None, demands=(), mobilize=()):
    ACTORS.append({"id": id, "label": label, "kind": kind, "icon": icon, "influence": influence, "satisfaction": satisfaction,
                   "groups": groups, "description": description, "laws": laws or {}, "reforms": reforms or {},
                   "conditions": conditions or {}, "demands": list(demands), "mobilize": list(mobilize)})


KINDS = [("union", "Syndicats", "⚒"), ("employers", "Patronat", "€"), ("religion", "Cultes", "✚"), ("lobby", "Lobbies", "◆"), ("ngo", "Associations", "♣")]

# --- Syndicats ----------------------------------------------------------------------------------
actor("union_militant", "Grand syndicat contestataire", "union", "⚒", 0.75, 0.35, ["private_employees", "low_income"],
      "Premier syndicat historique, puissant dans l'industrie, l'énergie et les transports. Il sait bloquer le pays.",
      laws={"work_week": {"39h": -0.25, "32h": 0.15}, "minimum_wage": {"boost": 0.2, "regional": -0.3}, "strike_service": {"guaranteed": -0.25},
            "unemployment_rules": {"strict": -0.2, "generous": 0.1}, "right_to_protest": {"restricted": -0.25}},
      reforms={"pension_age_65": -0.3, "pension_age_62": 0.25, "labour_code": -0.2, "minimum_wage": 0.15, "unemployment_insurance": -0.15},
      conditions={"unemployment": -3.0, "purchasingPower": 2.0},
      demands=[demand("wages", "Hausse générale des salaires dans la fonction publique et les entreprises publiques", 4.0,
                      [e(G + "civil_servants", 0.02), e(G + "low_income", 0.01), e("economy.inflation", 0.002, 180)], ["employers_big"]),
               demand("energy_prices", "Blocage des prix de l'énergie", 3.0, [e(G + "low_income", 0.015), e("economy.inflation", -0.003, 180)], [])],
      mobilize=["national_strike", "rail_strike", "strike_spreads"])
actor("union_reformist", "Syndicat réformiste", "union", "⚒", 0.65, 0.5, ["private_employees", "middle_income"],
      "Premier syndicat par le nombre d'adhérents, partisan de la négociation.",
      laws={"work_week": {"39h": -0.2, "32h": 0.05}, "minimum_wage": {"boost": 0.1, "regional": -0.2}, "end_of_life": {"assisted": 0.05}},
      reforms={"pension_age_65": -0.2, "labour_code": -0.05, "unemployment_insurance": -0.1},
      conditions={"unemployment": -2.0},
      demands=[demand("training", "Grand plan de formation des salariés", 1.5, [e("economy.naturalUnemployment", -0.001, 365), e(G + "private_employees", 0.01)], []),
               demand("arduousness", "Prise en compte de la pénibilité pour la retraite", 1.0, [e(G + "private_employees", 0.012)], ["employers_big"])],
      mobilize=["national_strike"])
actor("union_public", "Syndicats de la fonction publique", "union", "⚒", 0.55, 0.4, ["civil_servants"],
      "Enseignants, soignants, agents : ils tiennent les services publics.",
      laws={"strike_service": {"guaranteed": -0.25}, "work_week": {"39h": -0.25}},
      reforms={"teachers_plan": 0.2, "hospital_plan": 0.2},
      conditions={"spending": 2.0},
      demands=[demand("index_point", "Dégel du point d'indice", 3.5, [e(G + "civil_servants", 0.03)], ["employers_big"]),
               demand("staff", "Recrutements dans l'école et l'hôpital", 2.5, [e("quality.education", 0.006, 0), e("quality.health", 0.006)], [])],
      mobilize=["teachers_strike", "doctor_strike"])
actor("farmers_union", "Syndicat agricole majoritaire", "union", "♣", 0.6, 0.4, ["rural"],
      "La voix des exploitants agricoles, capables de bloquer routes et préfectures.",
      laws={"hunting": {"sunday_ban": -0.2}},
      reforms={"carbon_tax": -0.2},
      conditions={"agriculture": 3.0},
      demands=[demand("simplify", "Moins de normes et de contrôles", 0.2, [e("quality.agriculture", 0.006), e("quality.environment", -0.004), e(G + "rural", 0.012)], ["ngo_environment"]),
               demand("prices", "Prix plancher garantis pour les produits agricoles", 1.0, [e(G + "rural", 0.015), e("economy.inflation", 0.001, 180)], ["retail_lobby"])],
      mobilize=["farmers_protest", "farmers_local"])
# --- Patronat -----------------------------------------------------------------------------------
actor("employers_big", "Patronat des grandes entreprises", "employers", "€", 0.8, 0.55, ["high_income", "self_employed"],
      "Les grandes entreprises et leurs fédérations : investissement, emploi, influence.",
      laws={"work_week": {"39h": 0.25, "32h": -0.35}, "minimum_wage": {"boost": -0.2, "regional": 0.15}, "sunday_work": {"free": 0.15},
            "strike_service": {"guaranteed": 0.15}, "unemployment_rules": {"strict": 0.15, "generous": -0.15}, "lobbying": {"strict": -0.1}},
      reforms={"pension_age_65": 0.2, "labour_code": 0.25, "wealth_tax": -0.3, "carbon_tax": -0.1},
      conditions={"businessConfidence": 2.0, "businessTaxes": -2.0},
      demands=[demand("production_taxes", "Baisse des impôts de production", 6.0, [e("economy.businessConfidence", 0.03), e("sector.industry", 0.02)], ["union_militant"]),
               demand("simplification", "Choc de simplification administrative", 0.2, [e("economy.businessConfidence", 0.015)], [])],
      mobilize=[])
actor("employers_small", "Patronat des PME et artisans", "employers", "€", 0.55, 0.45, ["self_employed"],
      "Commerçants, artisans, petites entreprises : le tissu économique local.",
      laws={"minimum_wage": {"boost": -0.25}, "sunday_work": {"free": 0.1}, "work_week": {"32h": -0.3}},
      reforms={"labour_code": 0.15},
      conditions={"consumerConfidence": 2.0},
      demands=[demand("charges", "Baisse des charges sur les bas salaires", 3.0, [e(G + "self_employed", 0.02), e("economy.unemployment", -0.002, 0)], [])],
      mobilize=[])
# --- Cultes -------------------------------------------------------------------------------------
actor("church", "Église catholique", "religion", "✚", 0.45, 0.55, ["seniors", "rural"],
      "Premier culte de France, attentif aux questions de société et de fin de vie.",
      laws={"end_of_life": {"assisted": -0.35}, "surrogacy": {"ethical": -0.3}, "cannabis": {"legal": -0.15}, "death_penalty": {"terrorism": -0.2}},
      demands=[demand("heritage", "Plan pour les églises et le patrimoine religieux", 0.3, [e(G + "seniors", 0.006), e(G + "rural", 0.004)], ["secular"])],
      mobilize=["city_demonstration"])
actor("muslim_council", "Culte musulman", "religion", "✚", 0.4, 0.45, ["urban", "young"],
      "Représentation du deuxième culte de France, sensible aux débats sur la laïcité.",
      laws={"secularism": {"public_space": -0.4, "relaxed": 0.2}, "nationality": {"abolished": -0.3, "restricted": -0.15},
            "family_reunification": {"suspended": -0.25}, "facial_recognition": {"general": -0.1}},
      demands=[demand("imams", "Formation des imams en France", 0.05, [e("quality.security", 0.003)], [])],
      mobilize=["city_demonstration", "urban_riots"])
actor("jewish_council", "Communautés juives", "religion", "✚", 0.35, 0.55, ["urban"],
      "Institutions représentatives des Français de confession juive, attentives à la sécurité.",
      laws={"secularism": {"public_space": -0.15}, "mass_surveillance": {"extended": 0.05}},
      conditions={"security": 3.0},
      demands=[demand("protection", "Protection renforcée des lieux de culte et des écoles", 0.15, [e("quality.security", 0.003)], [])],
      mobilize=[])
actor("protestants", "Fédération protestante", "religion", "✚", 0.25, 0.55, ["adults"],
      "Les Églises protestantes, engagées sur l'accueil des réfugiés.",
      laws={"family_reunification": {"suspended": -0.25, "strict": -0.1}, "end_of_life": {"assisted": -0.1}},
      demands=[demand("refugees", "Accueil digne des réfugiés", 0.4, [e("demography.immigration", 0.02), e(G + "urban", 0.004)], [])],
      mobilize=[])
actor("secular", "Mouvement laïque", "religion", "⚖", 0.35, 0.5, ["civil_servants", "urban"],
      "Associations de défense de la laïcité et de l'école publique.",
      laws={"secularism": {"public_space": 0.15, "relaxed": -0.3}, "end_of_life": {"assisted": 0.2}, "surrogacy": {"ethical": 0.05}},
      demands=[demand("school", "Moyens pour l'école publique", 1.0, [e("quality.education", 0.006)], ["church"])],
      mobilize=[])
# --- Lobbies ------------------------------------------------------------------------------------
actor("oil_lobby", "Industrie pétrolière", "lobby", "⛁", 0.5, 0.5, ["high_income"],
      "Raffineurs et distributeurs de carburant.",
      reforms={"carbon_tax": -0.3, "renewables_plan": -0.1},
      conditions={"energy": 1.0},
      demands=[demand("fuel_tax", "Gel de la taxe sur les carburants", 2.0, [e(G + "rural", 0.012), e("quality.environment", -0.004)], ["ngo_environment"])],
      mobilize=["fuel_blockade"])
actor("pharma_lobby", "Industrie pharmaceutique", "lobby", "✚", 0.45, 0.5, ["high_income"],
      "Laboratoires et fabricants de médicaments.",
      demands=[demand("prices", "Revalorisation du prix des médicaments", 1.0, [e("sector.health", 0.02), e("budget.oneOff", 0.5)], ["union_public"])],
      mobilize=[])
actor("agrifood_lobby", "Industrie agroalimentaire", "lobby", "♣", 0.45, 0.5, ["rural"],
      "Grands groupes de l'alimentation et de la boisson.",
      laws={"sunday_work": {"free": 0.05}},
      demands=[demand("labels", "Assouplir l'étiquetage nutritionnel", 0.0, [e("sector.agrifood", 0.01), e("quality.health", -0.003)], ["ngo_consumers"])],
      mobilize=[])
actor("tech_lobby", "Géants du numérique", "lobby", "◎", 0.55, 0.5, ["young", "high_income"],
      "Plateformes et grandes entreprises du numérique.",
      laws={"social_media_minors": {"under15": -0.3}, "internet_control": {"blocking": -0.2, "filtered": -0.5}, "disinformation": {"permanent": -0.2}},
      demands=[demand("data", "Assouplir les règles sur les données", 0.0, [e("sector.tech", 0.02)], ["ngo_consumers"])],
      mobilize=[])
actor("nuclear_lobby", "Filière nucléaire", "lobby", "☢", 0.5, 0.55, ["civil_servants"],
      "Exploitant, constructeurs et sous-traitants des centrales.",
      reforms={"nuclear_program": 0.3, "renewables_plan": -0.05},
      demands=[demand("epr", "Commande de nouveaux réacteurs", 4.0, [e("sector.energy", 0.03), e("sector.industry", 0.01)], ["ngo_environment"])],
      mobilize=[])
actor("defense_lobby", "Industrie de défense", "lobby", "⚔", 0.5, 0.55, ["high_income"],
      "Constructeurs d'avions, de navires et de blindés.",
      reforms={"defense_programming": 0.3},
      demands=[demand("orders", "Commandes pluriannuelles garanties", 3.0, [e("sector.aerospace", 0.03), e("military.readiness", 0.01)], [])],
      mobilize=[])
actor("banking_lobby", "Banques et assurances", "lobby", "€", 0.55, 0.55, ["high_income"],
      "Les grandes banques et compagnies d'assurance.",
      reforms={"wealth_tax": -0.2},
      conditions={"businessConfidence": 1.0},
      demands=[demand("rules", "Assouplir les règles prudentielles", 0.0, [e("sector.banking", 0.02), e("economy.businessConfidence", 0.005)], ["ngo_consumers"])],
      mobilize=[])
actor("retail_lobby", "Grande distribution", "lobby", "▣", 0.45, 0.5, ["middle_income"],
      "Hypermarchés et enseignes nationales.",
      laws={"sunday_work": {"free": 0.25}},
      demands=[demand("negotiations", "Liberté dans les négociations avec les producteurs", 0.0, [e("sector.retail", 0.015), e(G + "rural", -0.008)], ["farmers_union"])],
      mobilize=[])
actor("hunters", "Chasseurs", "lobby", "♣", 0.35, 0.55, ["rural"],
      "Un million de pratiquants, très présents dans les campagnes.",
      laws={"hunting": {"sunday_ban": -0.4}, "firearms": {"relaxed": 0.1}},
      demands=[demand("species", "Élargir la liste des espèces chassables", 0.0, [e(G + "rural", 0.006), e("quality.environment", -0.003)], ["ngo_environment"])],
      mobilize=[])
# --- Associations -------------------------------------------------------------------------------
actor("ngo_environment", "Associations écologistes", "ngo", "♣", 0.5, 0.35, ["young", "urban"],
      "Grandes ONG environnementales, influentes chez les jeunes.",
      reforms={"carbon_tax": 0.25, "renewables_plan": 0.3, "nuclear_program": -0.15},
      conditions={"environment": 3.0},
      demands=[demand("climate", "Loi climat ambitieuse", 2.0, [e("quality.environment", 0.012), e(G + "young", 0.012), e("economy.businessConfidence", -0.01)], ["oil_lobby", "farmers_union"])],
      mobilize=["city_demonstration"])
actor("ngo_rights", "Défense des droits de l'homme", "ngo", "⚖", 0.4, 0.5, ["urban", "young"],
      "Associations de défense des libertés publiques.",
      laws={"mass_surveillance": {"extended": -0.3, "restricted": 0.2}, "facial_recognition": {"general": -0.4, "events": -0.1},
            "death_penalty": {"terrorism": -0.5}, "right_to_protest": {"restricted": -0.3}, "internet_control": {"filtered": -0.4, "blocking": -0.15},
            "press_sources": {"strong": 0.2, "weakened": -0.3}, "police_cameras": {"mandatory": 0.15}},
      demands=[demand("prisons", "Fin de la surpopulation carcérale", 0.8, [e("quality.justice", 0.006)], [])],
      mobilize=["city_demonstration"])
actor("ngo_consumers", "Associations de consommateurs", "ngo", "▣", 0.35, 0.5, ["middle_income", "low_income"],
      "Défense des consommateurs et des usagers.",
      conditions={"purchasingPower": 2.0},
      demands=[demand("prices", "Encadrement des prix des produits de première nécessité", 0.5, [e(G + "low_income", 0.012), e("sector.retail", -0.01)], ["retail_lobby"])],
      mobilize=[])

# --- Vérifications ------------------------------------------------------------------------------
laws = {l["id"]: [o["id"] for o in l["options"]] for l in json.load(open(os.path.join(ROOT, "countries", "FRA", "laws.json")))["laws"]}
reforms = {r["id"] for r in json.load(open(os.path.join(ROOT, "countries", "FRA", "reforms.json")))["reforms"]}
groups = {g["id"] for g in json.load(open(os.path.join(ROOT, "countries", "FRA", "social_groups.json")))["groups"]}
events = set()
for f in os.listdir(os.path.join(ROOT, "events")):
    events |= {x["id"] for x in json.load(open(os.path.join(ROOT, "events", f))).get("events", [])}
CONDITIONS = {"unemployment", "purchasingPower", "spending", "agriculture", "businessConfidence", "businessTaxes", "consumerConfidence", "security", "energy", "environment"}
TARGETS = re.compile(r"^(budget\.oneOff|economy\.\w+|opinion\.group\.\w+|quality\.\w+|sector\.\w+|military\.readiness|demography\.immigration)$")
ids = {a["id"] for a in ACTORS}
for a in ACTORS:
    assert set(a["groups"]) <= groups, a["id"]
    for l, opts in a["laws"].items():
        assert l in laws and set(opts) <= set(laws[l]), (a["id"], l)
    assert set(a["reforms"]) <= reforms, (a["id"], set(a["reforms"]) - reforms)
    assert set(a["conditions"]) <= CONDITIONS, a["id"]
    assert set(a["mobilize"]) <= events, a["id"]
    for d in a["demands"]:
        assert set(d["opposed"]) <= ids, (a["id"], d["opposed"])
        for fx in d["effects"]:
            assert TARGETS.match(fx["target"]), fx
            if fx["target"].startswith(G): assert fx["target"][len(G):] in groups, fx

out = {"_doc": "Acteurs organisés de la société. Généré par tools/datagen/actors_fr.py.",
       "kinds": [{"id": i, "label": l, "icon": ic, "description": ""} for i, l, ic in KINDS], "actors": ACTORS}
json.dump(out, open(os.path.join(ROOT, "countries", "FRA", "actors.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(ACTORS)} acteurs")
