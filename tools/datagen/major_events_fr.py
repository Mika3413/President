#!/usr/bin/env python3
"""Grands événements -> assets/data/config/major_events.json.

- Compétitions sportives au calendrier réel (rugby 2027, Euro 2028, JO de Los Angeles 2028,
  JO d'hiver des Alpes françaises 2030, Coupe du monde 2030, rugby 2031, JO de Brisbane et
  Euro 2032) : les Bleus avancent tour par tour ; une victoire soulève le pays.
- Organisation des JO d'hiver 2030 : dossiers à trancher (budget, écologie, sécurité,
  transports) qui décident de la réussite des Jeux.
- Catastrophes mondiales : pandémie, krach, nuage de cendres, tempête solaire, crise
  alimentaire, séisme majeur ; leurs vagues frappent l'économie mondiale et la France, et la
  France peut envoyer de l'aide.
"""
import json, os

ROOT = os.path.join(os.path.dirname(__file__), "..", "..", "assets", "data", "config")
G = "opinion.group."

KO = ["Phase de groupes", "Huitième de finale", "Quart de finale", "Demi-finale", "Finale"]
RUGBY = ["Phase de poules", "Quart de finale", "Demi-finale", "Finale"]

TOURNAMENTS = [
    {"id": "rugby_wc_2027", "label": "Coupe du monde de rugby", "team": "le XV de France", "host": "en Australie", "start": "2027-10-01", "stages": RUGBY, "stageDays": [0, 14, 21, 28], "winChance": 0.62},
    {"id": "euro_2028", "label": "Euro de football", "team": "les Bleus", "host": "au Royaume-Uni et en Irlande", "start": "2028-06-09", "stages": KO, "stageDays": [0, 15, 20, 25, 30], "winChance": 0.66},
    {"id": "olympics_2028", "label": "Jeux olympiques de Los Angeles", "team": "l'équipe de France olympique", "host": "à Los Angeles", "start": "2028-07-14", "olympics": True, "durationDays": 16, "medals": 40},
    {"id": "olympics_winter_2030", "label": "Jeux olympiques d'hiver des Alpes françaises", "team": "l'équipe de France olympique", "host": "dans les Alpes françaises", "start": "2030-02-01", "olympics": True, "durationDays": 16, "medals": 16, "hosted": True},
    {"id": "football_wc_2030", "label": "Coupe du monde de football", "team": "les Bleus", "host": "en Espagne, au Portugal et au Maroc", "start": "2030-06-13", "stages": KO, "stageDays": [0, 16, 21, 26, 31], "winChance": 0.66},
    {"id": "rugby_wc_2031", "label": "Coupe du monde de rugby", "team": "le XV de France", "host": "aux États-Unis", "start": "2031-09-05", "stages": RUGBY, "stageDays": [0, 14, 21, 28], "winChance": 0.62},
    {"id": "euro_2032", "label": "Euro de football", "team": "les Bleus", "host": "en Italie et en Turquie", "start": "2032-06-11", "stages": KO, "stageDays": [0, 15, 20, 25, 30], "winChance": 0.66},
    {"id": "olympics_2032", "label": "Jeux olympiques de Brisbane", "team": "l'équipe de France olympique", "host": "à Brisbane", "start": "2032-07-23", "olympics": True, "durationDays": 16, "medals": 38},
]

def choice(text, effects, readiness=0.0):
    return {"text": text, "effects": effects, "readiness": readiness}

DOSSIERS = [
    {"id": "jo2030_budget", "event": "olympics_winter_2030", "label": "JO 2030 : les coûts dérapent", "from": "2027-03-01", "days": 60,
     "prompt": "Le comité d'organisation annonce un dépassement de 1,5 milliard d'euros sur les sites. La Cour des comptes s'inquiète.", "choices": [
        choice("Couvrir le dépassement : les Jeux doivent être une réussite.", {"budget.oneOff": 1.5, G + "high_income": -0.003}, 0.2),
        choice("Réduire la voilure : sites existants, moins de prestige.", {G + "middle_income": 0.003}, -0.1),
        choice("Appeler les mécènes et les régions à payer leur part.", {"budget.oneOff": 0.4, "economy.businessConfidence": 0.002}, 0.1),
     ]},
    {"id": "jo2030_ecology", "event": "olympics_winter_2030", "label": "JO 2030 : la montagne se mobilise", "from": "2028-02-01", "days": 60,
     "prompt": "Des collectifs dénoncent des Jeux sur des glaciers qui reculent : canons à neige, bétonisation, manifestations à Albertville.", "choices": [
        choice("Imposer des Jeux sobres : zéro site neuf, neige naturelle prioritaire.", {G + "young": 0.006, G + "rural": -0.002}, -0.05),
        choice("Maintenir le projet, avec des compensations écologiques.", {G + "rural": 0.002, G + "young": -0.003}, 0.1),
        choice("Ouvrir une grande consultation des habitants des vallées.", {"opinion.national": 0.003}, 0.0),
     ]},
    {"id": "jo2030_security", "event": "olympics_winter_2030", "label": "JO 2030 : le plan de sécurité", "from": "2029-03-01", "days": 60,
     "prompt": "Menace terroriste élevée, sites dispersés en montagne : le ministère de l'Intérieur présente trois options.", "choices": [
        choice("Déployer l'armée en appui (opération Sentinelle renforcée).", {"budget.oneOff": 0.4, G + "seniors": 0.003}, 0.2),
        choice("Confier l'essentiel à la sécurité privée, moins chère.", {"budget.oneOff": 0.1}, -0.1),
        choice("Coopération européenne : polices de toute l'Union mobilisées.", {"budget.oneOff": 0.2, "alliance.EU.NEGOTIATION_GOODWILL": 0.01}, 0.15),
     ]},
    {"id": "jo2030_transport", "event": "olympics_winter_2030", "label": "JO 2030 : desservir les vallées", "from": "2029-09-01", "days": 60,
     "prompt": "Routes saturées, trains insuffisants : comment amener deux millions de visiteurs dans les stations ?", "choices": [
        choice("Navettes ferroviaires et cars électriques gratuits, financés par l'État.", {"budget.oneOff": 0.6, G + "young": 0.003}, 0.2),
        choice("Limiter les spectateurs et miser sur la télévision.", {"economy.businessConfidence": -0.002}, -0.05),
        choice("Accélérer les chantiers routiers prévus.", {"budget.oneOff": 0.8, G + "rural": 0.003, G + "young": -0.002}, 0.1),
     ]},
]

CATASTROPHES = [
    {"id": "global_pandemic", "label": "Pandémie mondiale", "chancePerYear": 0.025, "aid": False, "phases": [
        {"day": 0, "headline": "Un nouveau virus se répand sur tous les continents", "worldOutput": -0.008, "franceEvent": "new_virus"},
        {"day": 35, "headline": "L'OMS déclare une urgence de santé publique mondiale", "worldOutput": -0.02, "effects": {"economy.consumerConfidence": -0.04}, "franceEvent": "pandemic_wave"},
        {"day": 150, "headline": "La pandémie reflue : vaccins et immunité font leur œuvre", "worldOutput": 0.01, "effects": {"economy.consumerConfidence": 0.03}},
    ]},
    {"id": "financial_crash", "label": "Krach financier mondial", "chancePerYear": 0.025, "aid": False, "phases": [
        {"day": 0, "headline": "Krach à Wall Street : les places mondiales s'effondrent", "worldOutput": -0.02, "effects": {"economy.businessConfidence": -0.06, "economy.consumerConfidence": -0.03}},
        {"day": 45, "headline": "Les banques centrales inondent les marchés de liquidités", "worldOutput": 0.008, "effects": {"economy.businessConfidence": 0.03}},
    ]},
    {"id": "ash_cloud", "label": "Éruption volcanique en Islande", "chancePerYear": 0.03, "aid": False, "phases": [
        {"day": 0, "headline": "Éruption en Islande : un nuage de cendres paralyse le ciel européen", "worldOutput": -0.002, "effects": {"sector.tourism": -0.03, "sector.aerospace": -0.02}},
        {"day": 12, "headline": "Le trafic aérien reprend au-dessus de l'Europe", "worldOutput": 0.001, "effects": {"sector.tourism": 0.015}},
    ]},
    {"id": "solar_storm", "label": "Tempête solaire géante", "chancePerYear": 0.015, "aid": False, "phases": [
        {"day": 0, "headline": "Tempête solaire géante : satellites et réseaux électriques touchés", "worldOutput": -0.004, "effects": {"economy.businessConfidence": -0.02, "sector.energy": -0.03}},
    ]},
    {"id": "food_crisis", "label": "Crise alimentaire mondiale", "chancePerYear": 0.025, "aid": True, "country": "EGY", "phases": [
        {"day": 0, "headline": "Sécheresses et guerres : les prix du blé s'envolent, la faim menace", "worldOutput": -0.004, "effects": {"economy.inflation": 0.004, G + "low_income": -0.004}},
        {"day": 90, "headline": "Les récoltes de l'hémisphère sud détendent les marchés", "worldOutput": 0.002},
    ]},
    {"id": "megaquake", "label": "Séisme majeur", "chancePerYear": 0.04, "aid": True, "countries": ["JPN", "TUR", "IND", "CHN", "ITA", "GRC", "MAR", "DZA", "USA"], "phases": [
        {"day": 0, "headline": "Séisme dévastateur {in} : des milliers de victimes", "worldOutput": -0.001, "countryOutput": -0.05},
        {"day": 20, "headline": "{The} commence à reconstruire après le séisme", "countryOutput": 0.01},
    ]},
]

AID = {"prompt": "{The} est frappé(e) par la catastrophe et appelle la communauté internationale à l'aide.", "choices": [
    {"text": "Aide massive : sécurité civile, hôpital de campagne, 500 millions d'euros.", "effects": {"budget.oneOff": 0.5, "memory.{c}.CRISIS_SOLIDARITY": 0.15, "opinion.national": 0.003}},
    {"text": "Aide ciblée : sauveteurs et matériel médical.", "effects": {"budget.oneOff": 0.1, "memory.{c}.CRISIS_SOLIDARITY": 0.06}},
    {"text": "Un message de condoléances, sans plus.", "effects": {"memory.{c}.CRISIS_SOLIDARITY": -0.02}},
]}

data = {"_doc": "Généré par tools/datagen/major_events_fr.py", "tournaments": TOURNAMENTS, "dossiers": DOSSIERS, "catastrophes": CATASTROPHES, "aid": AID,
        "readinessStart": 0.5, "cooldownDays": 365}
with open(os.path.join(ROOT, "major_events.json"), "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=1)
print("major_events.json :", len(TOURNAMENTS), "compétitions,", len(DOSSIERS), "dossiers,", len(CATASTROPHES), "catastrophes")
