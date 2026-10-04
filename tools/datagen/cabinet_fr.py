"""Vie du gouvernement (France) -> assets/data/countries/FRA/cabinet.json.

Les ministres ne sont pas des pions : chaque mois, ils peuvent proposer leurs propres projets
(initiatives), s'opposer entre eux sur un dossier (disputes), menacer de démissionner ou sortir
de la ligne du gouvernement. Chaque initiative et chaque dispute a ses effets.
"""
import json, os, re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
G = "opinion.group."


def e(target, amount, days=0, delay=0):
    d = {"target": target, "amount": amount}
    if days: d["days"] = days
    if delay: d["delayDays"] = delay
    return d


INITIATIVES = []


def init(ministry, id, label, description, cost, effects):
    INITIATIVES.append({"id": id, "ministry": ministry, "label": label, "description": description, "costBillions": cost, "effects": effects})


# --- Initiatives par ministère -------------------------------------------------------------------
init("economy", "simplification", "Choc de simplification pour les entreprises", "Formulaires supprimés, délais de paiement de l'État raccourcis.", 0.1,
     [e("economy.businessConfidence", 0.012), e(G + "self_employed", 0.012)])
init("economy", "made_in_france", "Label « Fabriqué en France » et commande publique", "Préférence aux produits français dans les achats de l'État.", 0.3,
     [e("economy.output", 0.0003, 365), e(G + "rural", 0.006), e("alliance.EU.DISAGREEMENT", -0.005)])
init("economy", "tax_fraud", "Grand plan contre la fraude fiscale", "Recrutement d'inspecteurs, croisement des données.", 0.15,
     [e("budget.oneOff", -1.2, 365, 180), e(G + "low_income", 0.006), e(G + "high_income", -0.006)])
init("interior", "police_stations", "Ouvrir 200 commissariats et brigades", "Présence policière dans les villes moyennes et les campagnes.", 0.6,
     [e("quality.security", 0.012, 0, 180), e(G + "seniors", 0.01), e(G + "rural", 0.008)])
init("interior", "cameras", "Vidéoprotection dans les transports", "Caméras dans les gares et les rames.", 0.25,
     [e("quality.security", 0.006), e(G + "young", -0.005), e(G + "seniors", 0.006)])
init("armed_forces", "reserve", "Doubler la réserve opérationnelle", "Plus de réservistes, mieux formés, mieux payés.", 0.4,
     [e("military.readiness", 0.02, 0, 120), e(G + "young", 0.004)])
init("armed_forces", "drones", "Programme national de drones", "Une filière française de drones militaires.", 0.8,
     [e("military.readiness", 0.015, 0, 365), e("economy.output", 0.0002, 365)])
init("foreign", "francophonie", "Relance de la Francophonie", "Bourses, lycées français, médias internationaux.", 0.2,
     [e("memory.MAR.NEGOTIATION_GOODWILL", 0.03), e("memory.DZA.NEGOTIATION_GOODWILL", 0.02), e("memory.EGY.NEGOTIATION_GOODWILL", 0.02)])
init("foreign", "eu_tour", "Tournée des capitales européennes", "Préparer les prochains Conseils européens.", 0.01,
     [e("alliance.EU.NEGOTIATION_GOODWILL", 0.012)])
init("health", "nurse_practice", "Infirmiers en pratique avancée", "Les infirmiers peuvent prescrire dans les déserts médicaux.", 0.1,
     [e("quality.health", 0.008, 0, 90), e(G + "rural", 0.01), e(G + "civil_servants", 0.004)])
init("health", "prevention", "Grand plan de prévention santé", "Tabac, alcool, sport, dépistages gratuits.", 0.3,
     [e("quality.health", 0.006, 0, 180), e(G + "seniors", 0.004)])
init("education", "small_classes", "Classes à 15 élèves en maternelle", "Dans les quartiers prioritaires puis partout.", 0.9,
     [e("quality.education", 0.012, 0, 365), e(G + "adults", 0.008), e(G + "civil_servants", 0.006)])
init("education", "uniform", "Expérimenter l'uniforme à l'école", "Volontariat des établissements.", 0.05,
     [e(G + "seniors", 0.01), e(G + "young", -0.008)])
init("labour", "apprenticeship", "Apprentissage pour tous", "Aide à l'embauche d'apprentis prolongée.", 0.8,
     [e("economy.naturalUnemployment", -0.0006, 0, 180), e(G + "young", 0.01)])
init("labour", "four_day_week", "Expérimenter la semaine de quatre jours", "Dans la fonction publique volontaire.", 0.05,
     [e(G + "civil_servants", 0.012), e("economy.businessConfidence", -0.004)])
init("justice", "fast_courts", "Tribunaux de proximité", "Juger plus vite les petits délits.", 0.3,
     [e("quality.justice", 0.012, 0, 180), e(G + "seniors", 0.006)])
init("justice", "prisons", "Construire 5 000 places de prison", "Pour mettre fin à la surpopulation.", 1.0,
     [e("quality.justice", 0.008, 0, 365), e(G + "seniors", 0.008), e(G + "young", -0.004)])
init("ecology", "renovation", "Rénovation thermique accélérée", "Les passoires énergétiques rénovées en priorité.", 1.0,
     [e("quality.environment", 0.012, 0, 180), e("economy.output", 0.0003, 365), e(G + "low_income", 0.006)])
init("ecology", "bike_plan", "Plan vélo national", "Pistes cyclables et aides à l'achat.", 0.25,
     [e("quality.environment", 0.005), e(G + "urban", 0.008), e(G + "rural", -0.004)])
init("transport", "night_trains", "Retour des trains de nuit", "Six lignes de nuit relancées.", 0.3,
     [e("quality.transport", 0.008, 0, 180), e("quality.environment", 0.003), e(G + "young", 0.006)])
init("transport", "rural_roads", "Plan routes rurales", "Réfection des routes départementales.", 0.6,
     [e("quality.transport", 0.008, 0, 120), e(G + "rural", 0.012)])
init("agriculture", "young_farmers", "Installer 20 000 jeunes agriculteurs", "Prêts bonifiés et accès au foncier.", 0.4,
     [e("quality.agriculture", 0.01, 0, 365), e(G + "rural", 0.012)])
init("agriculture", "local_canteens", "Produits locaux dans les cantines", "50 % de produits locaux dans la restauration collective.", 0.2,
     [e("quality.agriculture", 0.006), e(G + "rural", 0.006), e(G + "adults", 0.004)])

# --- Disputes entre ministres --------------------------------------------------------------------
DISPUTES = []


def dispute(id, a, b, subject, a_label, a_effects, b_label, b_effects):
    DISPUTES.append({"id": id, "a": a, "b": b, "subject": subject, "aLabel": a_label, "aEffects": a_effects, "bLabel": b_label, "bEffects": b_effects})


dispute("budget_cuts", "economy", "education", "les économies demandées à l'Éducation nationale",
        "Tenir les économies", [e("budget.oneOff", -0.8, 365), e(G + "civil_servants", -0.01)],
        "Préserver le budget de l'école", [e("quality.education", 0.004), e("economy.businessConfidence", -0.003)])
dispute("glyphosate", "agriculture", "ecology", "l'interdiction d'un pesticide",
        "Ne pas interdire : protéger les agriculteurs", [e(G + "rural", 0.01), e("quality.environment", -0.006)],
        "Interdire dans trois ans", [e("quality.environment", 0.008), e(G + "rural", -0.012), e(G + "young", 0.006)])
dispute("nuclear_vs_renewables", "economy", "ecology", "la part du nucléaire et des renouvelables",
        "Priorité au nucléaire", [e("economy.businessConfidence", 0.005), e(G + "young", -0.004)],
        "Priorité aux renouvelables", [e("quality.environment", 0.006), e("budget.oneOff", 0.3)])
dispute("immigration", "interior", "labour", "l'immigration de travail",
        "Durcir l'accueil", [e("demography.immigration", -0.05), e(G + "seniors", 0.008), e("economy.businessConfidence", -0.004)],
        "Ouvrir les métiers en tension", [e("demography.immigration", 0.05), e("economy.businessConfidence", 0.006), e(G + "seniors", -0.006)])
dispute("defense_budget", "armed_forces", "economy", "la hausse du budget des armées",
        "Accorder la hausse", [e("military.readiness", 0.01, 0, 120), e("budget.oneOff", 1.0, 365)],
        "Reporter la hausse", [e("military.readiness", -0.005), e("alliance.NATO.DISAGREEMENT", -0.005)])
dispute("prisons_vs_alternatives", "justice", "interior", "la réponse à la surpopulation carcérale",
        "Peines alternatives", [e("quality.justice", 0.005), e(G + "seniors", -0.006)],
        "Plus de places de prison", [e("budget.oneOff", 0.5, 365), e(G + "seniors", 0.006)])
dispute("hospital_vs_savings", "health", "economy", "le budget des hôpitaux",
        "Rallonge pour les hôpitaux", [e("budget.oneOff", 0.8), e("quality.health", 0.006), e(G + "civil_servants", 0.006)],
        "Maîtriser les dépenses", [e("budget.oneOff", -0.3), e("quality.health", -0.004)])
dispute("motorway_vs_rail", "transport", "ecology", "un projet d'autoroute contesté",
        "Construire l'autoroute", [e("quality.transport", 0.004), e(G + "rural", 0.006), e("quality.environment", -0.006)],
        "Investir dans le train à la place", [e("quality.environment", 0.004), e(G + "rural", -0.004), e("budget.oneOff", 0.2)])
dispute("sanctions_trade", "foreign", "economy", "des sanctions qui pénalisent nos exportateurs",
        "Maintenir les sanctions", [e("alliance.EU.NEGOTIATION_GOODWILL", 0.006), e("economy.businessConfidence", -0.004)],
        "Demander des exemptions", [e("economy.businessConfidence", 0.004), e("alliance.EU.DISAGREEMENT", -0.006)])
dispute("pension_age", "labour", "economy", "l'âge de départ à la retraite",
        "Ne pas y toucher", [e(G + "private_employees", 0.008), e("economy.businessConfidence", -0.004)],
        "Le relever progressivement", [e("budget.oneOff", -1.0, 365), e(G + "private_employees", -0.012), e(G + "seniors", -0.006)])

TARGETS = re.compile(r"^(budget\.oneOff|economy\.(output|consumerConfidence|businessConfidence|naturalUnemployment)|opinion\.group\.\w+"
                     r"|quality\.(health|environment|security|transport|education|agriculture|social|justice)|military\.readiness"
                     r"|alliance\.(EU|NATO)\.[A-Z_]+|memory\.[A-Z]{3}\.[A-Z_]+|demography\.immigration)$")
gov = json.load(open(os.path.join(ROOT, "countries", "FRA", "government.json")))
ministries = {m["id"] for m in gov["ministries"]}
groups = {g["id"] for g in json.load(open(os.path.join(ROOT, "countries", "FRA", "social_groups.json")))["groups"]}
for i in INITIATIVES:
    assert i["ministry"] in ministries, i["id"]
    for fx in i["effects"]:
        assert TARGETS.match(fx["target"]), fx
for d in DISPUTES:
    assert d["a"] in ministries and d["b"] in ministries, d["id"]
    for fx in d["aEffects"] + d["bEffects"]:
        assert TARGETS.match(fx["target"]), fx
for fx in [f for i in INITIATIVES for f in i["effects"]] + [f for d in DISPUTES for f in d["aEffects"] + d["bEffects"]]:
    if fx["target"].startswith(G):
        assert fx["target"][len(G):] in groups, fx
assert {i["ministry"] for i in INITIATIVES} >= ministries - {"pm"}, ministries - {i["ministry"] for i in INITIATIVES}

out = {"_doc": "Initiatives et disputes des ministres. Généré par tools/datagen/cabinet_fr.py.", "initiatives": INITIATIVES, "disputes": DISPUTES}
json.dump(out, open(os.path.join(ROOT, "countries", "FRA", "cabinet.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(INITIATIVES)} initiatives, {len(DISPUTES)} disputes")
