"""Conseil de sécurité de l'ONU -> assets/data/diplomacy/un.json.

Membres : 5 permanents (droit de veto) et 10 élus pour deux ans (composition réelle d'octobre
2026), renouvelés par moitié chaque 1er janvier à partir d'un vivier de candidats. Chaque pays
a un profil d'alignement (occident, russie, chine, non-alignés) qui fixe sa position de départ.

Modèles de résolution :
  - liés à une guerre en cours (agresseur, victime) : condamnation, cessez-le-feu, sanctions,
    opération de maintien de la paix, autorisation du recours à la force ;
  - thématiques (sans cible simulée) : Soudan, FINUL, Corée du Nord, Haïti, piraterie...
Position de chaque bloc (-1 contre, +1 pour), effets pour la France si le texte est adopté.
"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
G = "opinion.group."

PERMANENT = ["USA", "GBR", "FRA", "RUS", "CHN"]

# id (code), nom, pays simulé ?, profil (west, russia, china, nonaligned), fin de mandat
ELECTED = [
    ("DNK", "Danemark", None, (0.9, 0, 0, 0.1), 2026),
    ("GRC", "Grèce", "GRC", (0.8, 0.05, 0, 0.15), 2026),
    ("PAK", "Pakistan", None, (0.2, 0.05, 0.45, 0.3), 2026),
    ("PAN", "Panama", None, (0.6, 0, 0.05, 0.35), 2026),
    ("SOM", "Somalie", None, (0.3, 0, 0.1, 0.6), 2026),
    ("BHR", "Bahreïn", None, (0.5, 0.05, 0.1, 0.35), 2027),
    ("COL", "Colombie", None, (0.45, 0, 0.05, 0.5), 2027),
    ("COD", "RD Congo", None, (0.3, 0.05, 0.2, 0.45), 2027),
    ("LVA", "Lettonie", None, (0.95, 0, 0, 0.05), 2027),
    ("LBR", "Liberia", None, (0.55, 0, 0.1, 0.35), 2027),
]

# Vivier des futurs élus (dans l'ordre où ils entrent au Conseil).
CANDIDATES = [
    ("DEU", "Allemagne", "DEU", (0.95, 0, 0, 0.05)), ("IND", "Inde", "IND", (0.35, 0.2, 0, 0.45)), ("BRA", "Brésil", "BRA", (0.3, 0.1, 0.15, 0.45)),
    ("ZAF", "Afrique du Sud", None, (0.2, 0.2, 0.2, 0.4)), ("KAZ", "Kazakhstan", None, (0.1, 0.5, 0.25, 0.15)),
    ("JPN", "Japon", "JPN", (0.95, 0, 0, 0.05)), ("EGY", "Égypte", "EGY", (0.35, 0.15, 0.15, 0.35)), ("NOR", "Norvège", "NOR", (0.9, 0, 0, 0.1)),
    ("IDN", "Indonésie", None, (0.25, 0.05, 0.2, 0.5)), ("MEX", "Mexique", None, (0.45, 0, 0.05, 0.5)),
    ("POL", "Pologne", "POL", (0.95, 0, 0, 0.05)), ("MAR", "Maroc", "MAR", (0.6, 0, 0.05, 0.35)), ("CAN", "Canada", "CAN", (0.95, 0, 0, 0.05)),
    ("NGA", "Nigeria", None, (0.35, 0.05, 0.15, 0.45)), ("VNM", "Viêt Nam", None, (0.15, 0.15, 0.35, 0.35)),
    ("ITA", "Italie", "ITA", (0.9, 0, 0, 0.1)), ("DZA", "Algérie", "DZA", (0.15, 0.4, 0.15, 0.3)), ("TUR", "Turquie", "TUR", (0.5, 0.15, 0.05, 0.3)),
    ("ARG", "Argentine", None, (0.6, 0, 0.05, 0.35)), ("KEN", "Kenya", None, (0.45, 0, 0.15, 0.4)),
]

# Profils des membres permanents (pour leur position de départ).
P5_PROFILE = {"USA": (1, 0, 0, 0), "GBR": (1, 0, 0, 0), "FRA": (1, 0, 0, 0), "RUS": (0, 1, 0, 0), "CHN": (0, 0.2, 0.8, 0)}

# Modèles liés à une guerre : id, type, titre ({aggressor}, {victim}), description, positions (west, russia, china, nonaligned)
WAR_TEMPLATES = [
    ("condemn", "condemn", "Condamner l'agression {aggressorOf} contre {victimThe}", "Le Conseil exige le retrait immédiat des forces {aggressorOf}.", (0.8, -0.2, -0.1, 0.3)),
    ("ceasefire", "ceasefire", "Cessez-le-feu immédiat entre {aggressorThe} et {victimThe}", "Arrêt des combats, couloirs humanitaires, négociations sous l'égide de l'ONU.", (0.5, 0.2, 0.5, 0.8)),
    ("sanctions", "sanctions", "Sanctions et embargo sur les armes contre {aggressorThe}", "Gel des avoirs, interdiction de voyager, embargo sur les armes.", (0.7, -0.5, -0.4, 0.0)),
    ("peacekeeping", "peacekeeping", "Force de maintien de la paix entre {aggressorThe} et {victimThe}", "Des Casques bleus s'interposent le long de la ligne de front.", (0.4, -0.1, 0.2, 0.6)),
    ("force", "force", "Autoriser le recours à la force pour défendre {victimThe}", "Chapitre VII : les États membres peuvent employer « tous les moyens nécessaires ».", (0.5, -0.8, -0.7, -0.2)),
]

THEMATIC = [
    # id, titre, description, intérêt pour la France, positions, effets si adopté
    ("sudan", "Soudan : accès humanitaire et protection des civils", "Couloirs humanitaires au Darfour, enquête sur les massacres.",
     "Les ONG françaises pourront travailler ; geste humanitaire salué.", (0.8, 0.0, 0.1, 0.5), [{"target": G + "young", "amount": 0.003}]),
    ("unifil", "Liban : renouvellement de la FINUL", "Les Casques bleus restent au Sud-Liban, dont 700 soldats français.",
     "Nos soldats restent ; la France garde son rôle au Liban.", (0.7, 0.2, 0.3, 0.6), [{"target": "budget.oneOff", "amount": 0.1}]),
    ("dprk", "Corée du Nord : nouvelles sanctions après un essai de missile", "Durcissement des sanctions contre le programme balistique.",
     "Non-prolifération : une priorité française.", (0.9, -0.6, -0.5, 0.2), []),
    ("haiti", "Haïti : mission multinationale contre les gangs", "Une force internationale aide la police haïtienne.",
     "Haïti est francophone : la France y est attendue.", (0.7, 0.0, 0.0, 0.5), [{"target": "budget.oneOff", "amount": 0.05}]),
    ("piracy", "Golfe de Guinée : lutte contre la piraterie", "Coordination navale contre les pirates et les trafics.",
     "Protège nos navires et nos approvisionnements en pétrole.", (0.8, 0.3, 0.3, 0.6), [{"target": "sector.transport", "amount": 0.005}]),
    ("climate", "Climat et sécurité", "Le changement climatique reconnu comme menace pour la paix.",
     "Une victoire diplomatique pour la France, pays de l'accord de Paris.", (0.7, -0.5, -0.3, 0.6), [{"target": G + "young", "amount": 0.004}]),
    ("iran", "Iran : rétablissement des sanctions nucléaires", "Retour des sanctions de l'ONU après les violations de l'accord de Vienne.",
     "Non-prolifération ; mais nos entreprises perdent le marché iranien.", (0.8, -0.7, -0.5, 0.0), [{"target": "economy.businessConfidence", "amount": -0.002}]),
    ("journalists", "Protection des journalistes dans les conflits", "Les attaques contre la presse deviennent des crimes de guerre poursuivis.",
     "Défense de la liberté de la presse.", (0.8, -0.3, -0.4, 0.4), []),
    ("sahel", "Sahel : mission d'appui contre le terrorisme", "Soutien logistique et financier aux armées africaines contre les jihadistes.",
     "Prolonge l'effort français au Sahel sans troupes au sol.", (0.6, -0.4, 0.0, 0.3), [{"target": "budget.oneOff", "amount": 0.1}]),
    ("cyber", "Normes de comportement dans le cyberespace", "Interdiction des attaques contre les hôpitaux et les infrastructures civiles.",
     "Protège nos hôpitaux et nos réseaux.", (0.8, -0.5, -0.4, 0.5), []),
]

snapshot = json.load(open(os.path.join(ROOT, "world_snapshots", "WORLD_SNAPSHOT_2026_10.json")))
countries = {c.split("/")[1] for c in snapshot["countries"]}
assert all(c in countries for c in PERMANENT)
for e in ELECTED:
    assert e[2] is None or e[2] in countries, e
for c in CANDIDATES:
    assert c[2] is None or c[2] in countries, c


def profile(t):
    return {"west": t[0], "russia": t[1], "china": t[2], "nonaligned": t[3]}


out = {
    "_doc": "Conseil de sécurité de l'ONU. Généré par tools/datagen/un_fr.py.",
    "permanent": [{"id": c, "profile": profile(P5_PROFILE[c])} for c in PERMANENT],
    "elected": [dict({"id": i, "name": n, "profile": profile(p), "until": u}, **({"country": s} if s else {})) for i, n, s, p, u in ELECTED],
    "candidates": [dict({"id": i, "name": n, "profile": profile(p)}, **({"country": s} if s else {})) for i, n, s, p in CANDIDATES],
    "warTemplates": [{"id": i, "kind": k, "title": t, "description": d, "stances": profile(s)} for i, k, t, d, s in WAR_TEMPLATES],
    "thematic": [{"id": i, "kind": "thematic", "title": t, "description": d, "interest": it, "stances": profile(s), "effects": eff} for i, t, d, it, s, eff in THEMATIC],
}
json.dump(out, open(os.path.join(ROOT, "diplomacy", "un.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(PERMANENT)} permanents, {len(ELECTED)} élus, {len(CANDIDATES)} candidats, {len(WAR_TEMPLATES)} modèles de guerre, {len(THEMATIC)} thèmes")
