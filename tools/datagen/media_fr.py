"""Presse et sondages (France) -> assets/data/media/fr.json.

Journaux fictifs avec une ligne politique (leaning : -1 gauche, +1 droite) et une spécialité ;
instituts de sondage fictifs avec un léger biais. Les titres sont des gabarits par sujet et par
ton : « pro » (journal proche du président), « con » (journal critique), « neutral ».
Variables : {value}, {before}, {headline}, {decision}, {days}, {country}.
"""
import json
import os

PAPERS = [
    {"id": "quotidien", "name": "Le Quotidien national", "leaning": 0.0, "focus": "general", "color": "2f4a6b"},
    {"id": "tribune", "name": "La Tribune de France", "leaning": 0.7, "focus": "general", "color": "1f3d7a"},
    {"id": "liberte", "name": "Libre Parole", "leaning": -0.7, "focus": "social", "color": "a3262a"},
    {"id": "echos", "name": "L'Écho économique", "leaning": 0.3, "focus": "economy", "color": "b07a12"},
]

INSTITUTES = [
    {"id": "opinia", "name": "Institut Opinia", "bias": 0.01},
    {"id": "sondeo", "name": "Sondéo", "bias": -0.01},
    {"id": "panel", "name": "Panel France", "bias": 0.0},
]

T = {
    "approval_up": {
        "pro": ["Le président regagne la confiance : {value} d'opinions favorables", "Popularité : l'embellie se confirme ({value})"],
        "con": ["Popularité en hausse : un sursis pour l'Élysée ?", "{value} de bonnes opinions : l'éclaircie avant l'orage ?"],
        "neutral": ["Popularité du président : {before} → {value}", "Le chef de l'État remonte dans les sondages"],
    },
    "approval_down": {
        "pro": ["Popularité : un passage difficile pour l'exécutif", "Le président garde le cap malgré des sondages en baisse"],
        "con": ["Chute libre : le président tombe à {value}", "Désaveu : seulement {value} de Français satisfaits"],
        "neutral": ["Popularité du président : {before} → {value}", "Le chef de l'État recule dans les sondages"],
    },
    "unemployment_up": {
        "pro": ["Chômage : un contexte international difficile", "Emploi : le gouvernement promet une riposte"],
        "con": ["Le chômage repart à la hausse : {value}", "Emploi : l'échec du gouvernement"],
        "neutral": ["Chômage : {before} → {value}", "Le chômage remonte à {value}"],
    },
    "unemployment_down": {
        "pro": ["Le chômage recule à {value} : la politique du président paie", "Emploi : la France embauche"],
        "con": ["Chômage en baisse : l'arbre qui cache la précarité", "Emploi : une embellie fragile"],
        "neutral": ["Chômage : {before} → {value}", "Le chômage baisse à {value}"],
    },
    "growth_weak": {
        "pro": ["Croissance : l'économie résiste malgré tout", "Activité : le pire est passé, selon Bercy"],
        "con": ["L'économie française cale : {value} de croissance", "Croissance en berne : l'inquiétude monte"],
        "neutral": ["Croissance : {value} sur un an", "Activité ralentie : {value}"],
    },
    "growth_strong": {
        "pro": ["La croissance accélère : {value}", "L'économie française en pleine forme"],
        "con": ["Croissance : qui en profite vraiment ?", "{value} de croissance, mais les salaires ne suivent pas"],
        "neutral": ["Croissance : {value} sur un an", "L'activité progresse de {value}"],
    },
    "deficit_high": {
        "pro": ["Budget : le gouvernement promet de redresser les comptes", "Déficit : des économies en préparation"],
        "con": ["Déficit à {value} du PIB : la dérive des comptes publics", "Bruxelles s'alarme du déficit français"],
        "neutral": ["Déficit public : {value} du PIB", "Comptes publics : le déficit à {value}"],
    },
    "debt_high": {
        "pro": ["Dette : une trajectoire sous contrôle, assure Bercy", "Dette publique : les marchés restent confiants"],
        "con": ["Dette record : {value} du PIB", "La dette explose : jusqu'où ?"],
        "neutral": ["Dette publique : {value} du PIB", "La dette atteint {value} du PIB"],
    },
    "inflation_high": {
        "pro": ["Inflation : le gouvernement protège le pouvoir d'achat", "Prix : des mesures pour les ménages"],
        "con": ["Les prix flambent : {value} d'inflation", "Pouvoir d'achat : les Français étranglés"],
        "neutral": ["Inflation : {value} sur un an", "Hausse des prix : {value}"],
    },
    "war": {
        "pro": ["Guerre : la Nation unie derrière ses armées", "Le président en chef de guerre"],
        "con": ["Guerre : jusqu'où ira le président ?", "Le pays s'enlise dans la guerre"],
        "neutral": ["La France en guerre : le point sur les opérations", "Conflit armé : la situation au jour le jour"],
    },
    "election": {
        "pro": ["Présidentielle dans {days} jours : le président en campagne", "J-{days} : le sortant prépare sa réélection"],
        "con": ["Présidentielle : le sortant peut-il encore gagner ?", "J-{days} : l'alternance se prépare"],
        "neutral": ["Présidentielle : J-{days}", "Plus que {days} jours avant la présidentielle"],
    },
    "decision": {
        "pro": ["{decision} : le président passe à l'action", "{decision} : une décision saluée"],
        "con": ["{decision} : un coup de com' de plus ?", "{decision} : à quel prix ?"],
        "neutral": ["Le président annonce : {decision}", "{decision} : ce qui va changer"],
    },
    "law_passed": {
        "pro": ["Victoire au Parlement : {decision}", "{decision} : le texte est voté"],
        "con": ["{decision} : le texte passe, la colère reste", "Vote au Parlement : {decision}, au forceps"],
        "neutral": ["Le Parlement adopte : {decision}", "Texte adopté : {decision}"],
    },
    "law_rejected": {
        "pro": ["{decision} : un revers parlementaire", "Le Parlement rejette un texte du gouvernement"],
        "con": ["Camouflet pour le président : {decision} rejeté", "Le gouvernement désavoué par les députés"],
        "neutral": ["Le Parlement rejette : {decision}", "Texte rejeté : {decision}"],
    },
    "news": {
        "pro": ["{headline}", "{headline} : l'exécutif sur le pont"],
        "con": ["{headline} : le gouvernement dépassé ?", "{headline}"],
        "neutral": ["{headline}", "{headline}"],
    },
    "calm": {
        "pro": ["Le président fixe le cap des prochains mois", "Réformes : le gouvernement accélère"],
        "con": ["Le président à la peine pour imposer son agenda", "Immobilisme : l'opposition s'impatiente"],
        "neutral": ["La rentrée politique en questions", "Ce qui attend le gouvernement cette semaine"],
    },
}

out = {
    "_doc": "Presse et sondages. Généré par tools/datagen/media_fr.py.",
    "papers": PAPERS,
    "institutes": INSTITUTES,
    "templates": T,
}
path = os.path.join(os.path.dirname(__file__), "..", "..", "assets", "data", "media", "fr.json")
with open(path, "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=1)
    f.write("\n")
print("presse écrite :", os.path.normpath(path))
