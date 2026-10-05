"""Fiscalité détaillée (France) -> assets/data/countries/FRA/fiscal.json.

Au-delà des grands taux (TVA, impôt sur le revenu...), des dizaines de dispositifs précis :
IFI/ISF, flat tax, successions, niches, TVA réduites, taxes comportementales... Chaque option
indique la recette supplémentaire par an (Md€, par rapport à la situation actuelle, première
option) et ses effets à l'adoption. Les changements sont votés dans une loi de finances.
"""
import json, os, re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
G = "opinion.group."


def e(target, amount, days=0):
    d = {"target": target, "amount": amount}
    if days: d["days"] = days
    return d


def o(label, revenue=0.0, effects=(), description=""):
    d = {"label": label, "revenueBillions": revenue, "effects": list(effects)}
    if description: d["description"] = description
    return d


TAXES = []


def tax(category, id, label, description, options):
    TAXES.append({"id": id, "category": category, "label": label, "description": description, "options": options})


CATEGORIES = [
    ("patrimoine", "Patrimoine et capital", "◆", "Fortune, revenus du capital, successions."),
    ("revenus", "Revenus et ménages", "♥", "Hauts revenus, familles, logement."),
    ("entreprises", "Entreprises", "€", "Impôts de production, crédits d'impôt, profits."),
    ("consommation", "Consommation", "▣", "TVA réduites, produits du quotidien."),
    ("comportement", "Santé et environnement", "♣", "Tabac, sucre, alcool, avion, logements vacants."),
]

tax("patrimoine", "wealth", "Impôt sur la fortune", "L'IFI ne taxe que le patrimoine immobilier au-delà de 1,3 M€.", [
    o("IFI (immobilier seulement)", 0.0),
    o("Rétablir l'ISF (tout le patrimoine)", 4.0, [e(G + "high_income", -0.03), e(G + "low_income", 0.012), e("economy.businessConfidence", -0.015)], "Symbole fort ; certains contribuables partent."),
    o("Supprimer l'IFI", -2.0, [e(G + "high_income", 0.015), e(G + "low_income", -0.012), e("economy.businessConfidence", 0.005)]),
])
tax("patrimoine", "flat_tax", "Prélèvement forfaitaire sur le capital", "Les revenus du capital sont taxés à 30 % (flat tax).", [
    o("30 % (flat tax)", 0.0),
    o("35 %", 2.0, [e(G + "high_income", -0.015), e("economy.businessConfidence", -0.008)]),
    o("Retour au barème progressif", 4.0, [e(G + "high_income", -0.025), e(G + "low_income", 0.008), e("economy.businessConfidence", -0.015)]),
    o("25 %", -2.0, [e(G + "high_income", 0.012), e("economy.businessConfidence", 0.008)]),
])
tax("patrimoine", "inheritance", "Droits de succession", "Jusqu'à 45 % en ligne directe au-delà de 1,8 M€.", [
    o("Barème actuel", 0.0),
    o("Allégés pour les classes moyennes", -4.0, [e(G + "middle_income", 0.015), e(G + "seniors", 0.01)]),
    o("Alourdis sur les très gros héritages", 3.0, [e(G + "high_income", -0.015), e(G + "low_income", 0.006)]),
])
tax("patrimoine", "financial_transactions", "Taxe sur les transactions financières", "0,3 % sur les achats d'actions des grandes entreprises.", [
    o("0,3 %", 0.0),
    o("0,6 % et élargie aux opérations intrajournalières", 1.5, [e("sector.banking", -0.02), e(G + "low_income", 0.004)]),
    o("Supprimée", -1.7, [e("sector.banking", 0.01)]),
])
tax("patrimoine", "buybacks", "Taxe sur les rachats d'actions", "Aucune taxe spécifique.", [
    o("Aucune", 0.0),
    o("1 % des rachats", 0.6, [e("economy.businessConfidence", -0.004), e(G + "private_employees", 0.004)]),
])
tax("revenus", "high_incomes", "Contribution sur les hauts revenus", "3 à 4 % au-delà de 250 000 € de revenus.", [
    o("3 à 4 %", 0.0),
    o("Doublée (6 à 8 %)", 1.5, [e(G + "high_income", -0.02), e(G + "low_income", 0.006)]),
    o("Supprimée", -1.3, [e(G + "high_income", 0.012)]),
])
tax("revenus", "family_quotient", "Quotient familial", "L'avantage par enfant est plafonné à environ 1 800 € par an.", [
    o("Plafond actuel", 0.0),
    o("Plafond abaissé", 1.5, [e(G + "middle_income", -0.012), e(G + "adults", -0.008)]),
    o("Plafond relevé", -1.0, [e(G + "adults", 0.01), e(G + "middle_income", 0.006)]),
])
tax("revenus", "housing_tax", "Taxe d'habitation", "Supprimée sur les résidences principales, maintenue sur les secondaires.", [
    o("Résidences secondaires seulement", 0.0),
    o("Surtaxe sur les résidences secondaires en zone tendue", 0.8, [e(G + "high_income", -0.006), e(G + "urban", 0.004)]),
    o("Rétablie sur les résidences principales (communes)", 6.0, [e(G + "middle_income", -0.025), e(G + "low_income", -0.015)]),
])
tax("revenus", "tax_niches", "Niches fiscales", "Environ 90 Md€ d'avantages fiscaux (emploi à domicile, investissement locatif...).", [
    o("Inchangées", 0.0),
    o("Rabot de 10 % sur toutes les niches", 8.0, [e(G + "middle_income", -0.012), e(G + "high_income", -0.012), e("sector.construction", -0.01)]),
    o("Suppression des niches les plus coûteuses", 15.0, [e(G + "high_income", -0.025), e(G + "middle_income", -0.015), e("sector.construction", -0.02), e("economy.businessConfidence", -0.01)]),
])
tax("revenus", "tax_shield", "Bouclier fiscal", "Aucun plafonnement des impôts en fonction des revenus.", [
    o("Aucun", 0.0),
    o("Impôts plafonnés à 50 % des revenus", -0.8, [e(G + "high_income", 0.012), e(G + "low_income", -0.012)]),
])
tax("revenus", "exit_tax", "Exit tax", "Taxe allégée sur les plus-values latentes en cas de départ à l'étranger.", [
    o("Allégée", 0.0),
    o("Renforcée", 0.3, [e(G + "high_income", -0.006), e("economy.businessConfidence", -0.004)]),
])
tax("entreprises", "production_taxes", "Impôts de production", "Taxes sur la valeur ajoutée et le chiffre d'affaires des entreprises.", [
    o("Niveau actuel", 0.0),
    o("Baisse de 5 Md€", -5.0, [e("economy.businessConfidence", 0.02), e("sector.industry", 0.02)]),
    o("Hausse de 3 Md€", 3.0, [e("economy.businessConfidence", -0.015), e("sector.industry", -0.015)]),
])
tax("entreprises", "low_wage_relief", "Allègements de charges sur les bas salaires", "Environ 80 Md€ de réductions de cotisations.", [
    o("Niveau actuel", 0.0),
    o("Recentrés sur le SMIC", 5.0, [e("economy.businessConfidence", -0.01), e("economy.unemployment", 0.001)]),
    o("Renforcés", -5.0, [e("economy.businessConfidence", 0.01), e("economy.unemployment", -0.001)]),
])
tax("entreprises", "research_credit", "Crédit d'impôt recherche", "7 Md€ par an pour la recherche des entreprises.", [
    o("Actuel", 0.0),
    o("Recentré sur les PME", 2.0, [e("sector.tech", -0.01), e(G + "self_employed", 0.004)]),
    o("Doublé", -5.0, [e("sector.tech", 0.03), e("sector.health", 0.02), e("economy.potentialGrowth", 0.0005, 365)]),
])
tax("entreprises", "windfall", "Taxe sur les superprofits", "Pas de taxe sur les bénéfices exceptionnels.", [
    o("Aucune", 0.0),
    o("Temporaire (énergie, banques, transport maritime)", 5.0, [e("sector.energy", -0.02), e("sector.banking", -0.015), e(G + "low_income", 0.01), e("economy.businessConfidence", -0.008)]),
])
tax("entreprises", "digital_tax", "Taxe sur les services numériques", "3 % du chiffre d'affaires des grandes plateformes réalisé en France.", [
    o("3 %", 0.0),
    o("6 %", 0.7, [e("sector.tech", -0.01), e("memory.USA.DISAGREEMENT", -0.02)]),
    o("Supprimée", -0.7, [e("memory.USA.NEGOTIATION_GOODWILL", 0.01)]),
])
tax("consommation", "restaurant_vat", "TVA dans la restauration", "Taux réduit de 10 %.", [
    o("10 %", 0.0),
    o("5,5 %", -2.5, [e("sector.tourism", 0.02), e(G + "self_employed", 0.008)]),
    o("20 %", 3.0, [e("sector.tourism", -0.03), e(G + "self_employed", -0.012)]),
])
tax("consommation", "essentials_vat", "TVA sur les produits de première nécessité", "Taux réduit de 5,5 % sur l'alimentation et l'énergie.", [
    o("5,5 %", 0.0),
    o("0 % sur les produits de base", -7.0, [e(G + "low_income", 0.025), e(G + "middle_income", 0.012), e("economy.inflation", -0.004, 180)]),
])
tax("consommation", "electricity_vat", "TVA sur l'électricité et le gaz", "20 % sur la consommation.", [
    o("20 %", 0.0),
    o("5,5 %", -6.0, [e(G + "low_income", 0.02), e(G + "rural", 0.012), e("economy.inflation", -0.003, 180)]),
])
tax("comportement", "tobacco", "Fiscalité du tabac", "Un paquet coûte environ 13 €.", [
    o("Paquet à 13 €", 0.0),
    o("Paquet à 16 €", 1.5, [e("quality.health", 0.004, 0), e(G + "low_income", -0.008)]),
])
tax("comportement", "sugar", "Taxe sur les boissons sucrées", "Taxe modulée selon la teneur en sucre.", [
    o("Actuelle", 0.0),
    o("Renforcée et élargie aux produits transformés", 0.6, [e("quality.health", 0.004), e("sector.agrifood", -0.01)]),
])
tax("comportement", "alcohol", "Fiscalité de l'alcool", "Accises sur les alcools forts, très faibles sur le vin.", [
    o("Actuelle", 0.0),
    o("Prix minimum par unité d'alcool", 0.8, [e("quality.health", 0.004), e(G + "rural", -0.008), e("sector.agrifood", -0.01)]),
])
tax("comportement", "air_tickets", "Taxe sur les billets d'avion", "Quelques euros par billet.", [
    o("Actuelle", 0.0),
    o("Triplée", 1.0, [e("quality.environment", 0.004), e("sector.tourism", -0.01), e("sector.transport", -0.01)]),
])
tax("comportement", "vacant_homes", "Taxe sur les logements vacants", "Dans les zones tendues seulement.", [
    o("Zones tendues", 0.0),
    o("Généralisée et doublée", 0.4, [e(G + "urban", 0.006), e(G + "high_income", -0.004)]),
])

TARGETS = re.compile(r"^(economy\.\w+|opinion\.group\.\w+|quality\.\w+|sector\.\w+|memory\.[A-Z]{3}\.[A-Z_]+)$")
groups = {g["id"] for g in json.load(open(os.path.join(ROOT, "countries", "FRA", "social_groups.json")))["groups"]}
sectors = {s["id"] for s in json.load(open(os.path.join(ROOT, "countries", "FRA", "sectors.json")))["sectors"]}
for t in TAXES:
    assert t["options"][0]["revenueBillions"] == 0.0, t["id"]
    for op in t["options"]:
        for fx in op["effects"]:
            assert TARGETS.match(fx["target"]), fx
            if fx["target"].startswith(G): assert fx["target"][len(G):] in groups, fx
            if fx["target"].startswith("sector."): assert fx["target"][7:] in sectors, fx
out = {"_doc": "Fiscalité détaillée. Généré par tools/datagen/fiscal_fr.py.",
       "categories": [{"id": i, "label": l, "icon": ic, "description": d} for i, l, ic, d in CATEGORIES], "taxes": TAXES}
json.dump(out, open(os.path.join(ROOT, "countries", "FRA", "fiscal.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(TAXES)} dispositifs fiscaux")
