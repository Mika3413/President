"""Union européenne : textes de la Commission votés au Conseil -> assets/data/diplomacy/eu.json.

Chaque texte a sa règle de vote (majorité qualifiée : 55 % des États et 65 % de la population ;
ou unanimité), ses effets sur la France s'il est adopté, et la position de départ de chaque
État membre simulé (-1 contre ... +1 pour) ; « others » résume les 15 États non simulés.
"""
import json, os, re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
G = "opinion.group."


def e(target, amount, days=0, delay=0):
    d = {"target": target, "amount": amount}
    if days: d["days"] = days
    if delay: d["delayDays"] = delay
    return d


TEXTS = []


def text(id, title, description, rule, effects, stances, others, interest):
    TEXTS.append({"id": id, "title": title, "description": description, "rule": rule, "effects": effects,
                  "stances": stances, "others": others, "interest": interest})


text("ai_rules", "Règlement européen sur l'intelligence artificielle", "Obligations de transparence et interdiction de certains usages de l'IA.", "QMV",
     [e("sector.tech", -0.02), e("quality.security", 0.004), e(G + "adults", 0.004)],
     {"DEU": 0.4, "ITA": 0.2, "ESP": 0.4, "NLD": 0.5, "BEL": 0.5, "PRT": 0.3, "AUT": 0.5, "POL": 0.0, "SWE": -0.1, "GRC": 0.3, "ROU": 0.2}, 0.3,
     "Protège les citoyens ; freine nos jeunes pousses.")
text("combustion_delay", "Report de la fin des moteurs thermiques", "Les voitures thermiques neuves autorisées cinq ans de plus.", "QMV",
     [e("sector.industry", 0.03), e("quality.environment", -0.006), e(G + "rural", 0.008), e(G + "young", -0.006)],
     {"DEU": 0.6, "ITA": 0.6, "ESP": -0.1, "NLD": -0.6, "BEL": -0.2, "PRT": -0.2, "AUT": 0.2, "POL": 0.7, "SWE": -0.6, "GRC": 0.3, "ROU": 0.6}, 0.2,
     "Soulage l'automobile ; recul climatique.")
text("defense_fund", "Fonds européen de défense de 100 Md€", "Achats communs d'armement fabriqué en Europe.", "QMV",
     [e("sector.aerospace", 0.05), e("military.readiness", 0.01, 0, 120), e("budget.oneOff", 1.0, 365)],
     {"DEU": 0.2, "ITA": 0.4, "ESP": 0.2, "NLD": -0.3, "BEL": 0.2, "PRT": 0.1, "AUT": -0.6, "POL": 0.8, "SWE": 0.4, "GRC": 0.6, "ROU": 0.7}, 0.2,
     "Bon pour notre industrie de défense ; contribution au budget.")
text("digital_tax", "Taxe européenne sur les géants du numérique", "Une taxe sur le chiffre d'affaires des grandes plateformes.", "UNANIMITY",
     [e("budget.oneOff", -0.8, 365), e("sector.tech", -0.01), e("memory.USA.DISAGREEMENT", -0.03)],
     {"DEU": 0.1, "ITA": 0.6, "ESP": 0.6, "NLD": -0.5, "BEL": 0.4, "PRT": 0.5, "AUT": 0.6, "POL": 0.2, "SWE": -0.3, "GRC": 0.5, "ROU": 0.3}, -0.2,
     "Recettes nouvelles ; colère de Washington.")
text("asylum_quotas", "Répartition obligatoire des demandeurs d'asile", "Chaque État accueille un quota ou paie une contribution.", "QMV",
     [e("demography.immigration", 0.03), e(G + "seniors", -0.01), e(G + "urban", 0.004)],
     {"DEU": 0.5, "ITA": 0.8, "ESP": 0.7, "NLD": -0.2, "BEL": 0.3, "PRT": 0.5, "AUT": -0.6, "POL": -0.9, "SWE": 0.0, "GRC": 0.9, "ROU": -0.4}, -0.2,
     "Solidarité avec l'Italie et la Grèce ; sujet explosif en France.")
text("cap_reform", "Réforme de la politique agricole commune", "Moins d'aides à l'hectare, plus d'aides écologiques.", "QMV",
     [e("sector.agrifood", -0.02), e(G + "rural", -0.015), e("quality.environment", 0.006)],
     {"DEU": 0.3, "ITA": -0.3, "ESP": -0.4, "NLD": 0.6, "BEL": 0.2, "PRT": -0.3, "AUT": 0.3, "POL": -0.6, "SWE": 0.7, "GRC": -0.5, "ROU": -0.6}, 0.0,
     "Nos agriculteurs y perdent ; l'environnement y gagne.")
text("fiscal_rules", "Assouplissement des règles budgétaires", "Plus de temps pour réduire les déficits, investissements exclus du calcul.", "QMV",
     [e("economy.output", 0.0006, 365), e("economy.businessConfidence", -0.003), e("sector.banking", -0.01)],
     {"DEU": -0.4, "ITA": 0.9, "ESP": 0.7, "NLD": -0.9, "BEL": 0.3, "PRT": 0.6, "AUT": -0.6, "POL": 0.4, "SWE": -0.7, "GRC": 0.8, "ROU": 0.5}, 0.1,
     "De l'air pour notre budget ; les « frugaux » s'y opposent.")
text("power_market", "Réforme du marché de l'électricité", "Des prix fondés sur les coûts réels de production, nucléaire compris.", "QMV",
     [e("sector.energy", 0.02), e(G + "low_income", 0.008), e("sector.industry", 0.02)],
     {"DEU": -0.3, "ITA": 0.4, "ESP": 0.6, "NLD": -0.3, "BEL": 0.3, "PRT": 0.5, "AUT": -0.4, "POL": 0.3, "SWE": 0.1, "GRC": 0.5, "ROU": 0.4}, 0.2,
     "Valorise notre parc nucléaire ; Berlin hésite.")
text("carbon_border", "Taxe carbone aux frontières élargie", "Les importations polluantes paient le prix du carbone.", "QMV",
     [e("sector.industry", 0.015), e("quality.environment", 0.005), e("memory.CHN.DISAGREEMENT", -0.03), e("memory.IND.DISAGREEMENT", -0.02)],
     {"DEU": 0.3, "ITA": 0.2, "ESP": 0.4, "NLD": 0.5, "BEL": 0.5, "PRT": 0.3, "AUT": 0.5, "POL": -0.6, "SWE": 0.7, "GRC": 0.0, "ROU": -0.3}, 0.0,
     "Protège notre industrie et le climat ; Pékin menace de répliquer.")
text("ukraine_accession", "Ouverture des négociations d'adhésion de l'Ukraine", "Un long processus qui commence.", "UNANIMITY",
     [e("memory.UKR.NEGOTIATION_GOODWILL", 0.06), e("memory.RUS.DISAGREEMENT", -0.04), e(G + "rural", -0.01)],
     {"DEU": 0.5, "ITA": 0.4, "ESP": 0.5, "NLD": 0.2, "BEL": 0.4, "PRT": 0.5, "AUT": 0.0, "POL": 0.9, "SWE": 0.8, "GRC": 0.2, "ROU": 0.7}, 0.0,
     "Geste historique ; inquiétude des agriculteurs.")
text("russia_sanctions", "Nouveau paquet de sanctions contre la Russie", "Gel d'avoirs et interdictions d'exportation.", "UNANIMITY",
     [e("memory.RUS.DISAGREEMENT", -0.05), e("memory.UKR.CRISIS_SOLIDARITY", 0.03), e("sector.energy", -0.01), e("economy.inflation", 0.0005, 180)],
     {"DEU": 0.5, "ITA": 0.3, "ESP": 0.5, "NLD": 0.7, "BEL": 0.6, "PRT": 0.6, "AUT": 0.0, "POL": 1.0, "SWE": 0.9, "GRC": 0.1, "ROU": 0.8}, -0.1,
     "Fermeté face à Moscou ; un coût pour nos entreprises.")
text("mercosur_ratification", "Ratification de l'accord avec le Mercosur", "Libre-échange avec l'Amérique du Sud.", "QMV",
     [e("sector.agrifood", -0.04), e("sector.industry", 0.02), e("sector.luxury", 0.01), e(G + "rural", -0.025)],
     {"DEU": 0.8, "ITA": 0.2, "ESP": 0.8, "NLD": 0.6, "BEL": -0.2, "PRT": 0.8, "AUT": -0.6, "POL": -0.4, "SWE": 0.7, "GRC": 0.0, "ROU": 0.0}, 0.3,
     "Bon pour l'industrie ; nos éleveurs vent debout.")
text("common_debt", "Nouvel emprunt commun pour l'industrie verte", "L'Union emprunte pour financer batteries, hydrogène et puces.", "UNANIMITY",
     [e("sector.industry", 0.02), e("economy.output", 0.0008, 365), e("economy.businessConfidence", 0.004)],
     {"DEU": -0.4, "ITA": 0.9, "ESP": 0.8, "NLD": -0.9, "BEL": 0.4, "PRT": 0.7, "AUT": -0.7, "POL": 0.5, "SWE": -0.6, "GRC": 0.9, "ROU": 0.6}, 0.1,
     "Investissements massifs ; les « frugaux » mettent leur veto en balance.")
text("pesticide_renewal", "Renouvellement d'un pesticide controversé", "Autorisation prolongée de dix ans.", "QMV",
     [e("sector.agrifood", 0.02), e("quality.environment", -0.006), e(G + "rural", 0.008), e(G + "young", -0.008)],
     {"DEU": -0.2, "ITA": 0.2, "ESP": 0.5, "NLD": 0.4, "BEL": -0.3, "PRT": 0.4, "AUT": -0.8, "POL": 0.6, "SWE": 0.2, "GRC": 0.4, "ROU": 0.6}, 0.3,
     "Les agriculteurs le demandent ; les écologistes manifestent.")
text("schengen_controls", "Contrôles prolongés aux frontières intérieures", "Les États peuvent contrôler leurs frontières deux ans de plus.", "QMV",
     [e("quality.security", 0.004), e("sector.transport", -0.01), e("sector.tourism", -0.005), e(G + "seniors", 0.006)],
     {"DEU": 0.5, "ITA": 0.2, "ESP": -0.2, "NLD": 0.3, "BEL": -0.1, "PRT": -0.4, "AUT": 0.7, "POL": 0.3, "SWE": 0.4, "GRC": -0.3, "ROU": -0.6}, -0.1,
     "Sécurité ; frein au commerce et au tourisme.")
text("minimum_wages", "Directive renforcée sur les salaires minimaux", "Des salaires minimaux décents dans toute l'Union.", "QMV",
     [e(G + "low_income", 0.006), e("economy.businessConfidence", -0.002), e("sector.industry", 0.005)],
     {"DEU": 0.4, "ITA": 0.3, "ESP": 0.7, "NLD": -0.4, "BEL": 0.5, "PRT": 0.6, "AUT": -0.2, "POL": -0.2, "SWE": -0.8, "GRC": 0.5, "ROU": 0.2}, 0.0,
     "Moins de dumping social chez nos voisins.")
text("nuclear_taxonomy", "Le nucléaire reconnu comme énergie durable", "Le nucléaire accède aux financements verts européens.", "QMV",
     [e("sector.energy", 0.03), e("economy.businessConfidence", 0.003), e(G + "young", -0.003)],
     {"DEU": -0.6, "ITA": 0.2, "ESP": -0.4, "NLD": 0.4, "BEL": 0.2, "PRT": -0.5, "AUT": -0.9, "POL": 0.8, "SWE": 0.5, "GRC": 0.0, "ROU": 0.7}, 0.2,
     "Victoire pour notre filière nucléaire ; Berlin et Vienne contre.")
text("space_program", "Programme spatial européen renforcé", "Lanceurs, satellites et constellation de communication souveraine.", "QMV",
     [e("sector.aerospace", 0.03), e("budget.oneOff", 0.4, 365), e("sector.tech", 0.01)],
     {"DEU": 0.3, "ITA": 0.6, "ESP": 0.4, "NLD": -0.2, "BEL": 0.5, "PRT": 0.1, "AUT": -0.1, "POL": 0.2, "SWE": 0.3, "GRC": 0.0, "ROU": 0.1}, 0.1,
     "Bon pour Ariane et notre industrie spatiale.")

snapshot = json.load(open(os.path.join(ROOT, "world_snapshots", "WORLD_SNAPSHOT_2026_10.json")))
members = next(a for a in snapshot["alliances"] if a["id"] == "EU")["members"]
others_members = [m for m in members if m != "FRA"]
TARGETS = re.compile(r"^(budget\.oneOff|economy\.(output|businessConfidence|inflation)|opinion\.group\.\w+|sector\.\w+"
                     r"|quality\.(environment|security)|military\.readiness|memory\.[A-Z]{3}\.[A-Z_]+|demography\.immigration)$")
sectors = {s["id"] for s in json.load(open(os.path.join(ROOT, "countries", "FRA", "sectors.json")))["sectors"]}
for t in TEXTS:
    assert set(t["stances"]) == set(others_members), (t["id"], set(others_members) ^ set(t["stances"]))
    assert t["rule"] in ("QMV", "UNANIMITY")
    for fx in t["effects"]:
        assert TARGETS.match(fx["target"]), fx
        if fx["target"].startswith("sector."):
            assert fx["target"][7:] in sectors, fx

out = {"_doc": "Textes européens. Généré par tools/datagen/eu_fr.py.", "totalStates": 27, "totalPopulation": 449000000,
       "proposalIntervalDays": 50, "voteDelayDays": 30, "texts": TEXTS}
json.dump(out, open(os.path.join(ROOT, "diplomacy", "eu.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(TEXTS)} textes européens")
