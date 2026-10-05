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


def tax(category, id, label, description, unit, ref, lo, hi, step, curve, per, effects, legacy, reform=None, decimals=1):
    curve = sorted(curve)
    assert any(abs(v - ref) < 1e-9 and abs(r) < 1e-9 for v, r in curve), id
    assert curve[0][0] <= lo + 1e-9 and curve[-1][0] >= hi - 1e-9, id
    d = {"id": id, "category": category, "label": label, "description": description, "unit": unit, "decimals": decimals,
         "reference": ref, "min": lo, "max": hi, "step": step, "revenue": [list(c) for c in curve], "per": per,
         "effects": list(effects), "legacy": legacy}
    if reform: d["reform"] = reform
    TAXES.append(d)


CATEGORIES = [
    ("patrimoine", "Patrimoine et capital", "◆", "Fortune, revenus du capital, successions."),
    ("revenus", "Revenus et ménages", "♥", "Hauts revenus, familles, logement."),
    ("entreprises", "Entreprises", "€", "Impôts de production, crédits d'impôt, profits."),
    ("consommation", "Consommation", "▣", "TVA réduites, produits du quotidien."),
    ("comportement", "Santé et environnement", "♣", "Tabac, sucre, alcool, avion, logements vacants."),
]

# Patrimoine et capital
tax("patrimoine", "wealth", "Impôt sur la fortune financière (ISF)", "Taux annuel sur le patrimoine financier au-delà de 1,3 M€ (l'IFI sur l'immobilier reste).",
    "%", 0.0, 0.0, 3.0, 0.1, [(0, 0), (0.5, 2.5), (1.0, 4.0), (1.5, 4.6), (2.0, 4.5), (3.0, 3.6)], 1.0,
    [e(G + "high_income", -0.03), e(G + "low_income", 0.012), e("economy.businessConfidence", -0.015), e("economy.potentialGrowth", -0.0003, 1460)],
    [0.0, 1.0, 0.0], {"reform": "wealth_tax", "above": 0.3}, decimals=1)
tax("patrimoine", "real_estate_wealth", "Impôt sur la fortune immobilière (IFI)", "Taux marginal maximal sur le patrimoine immobilier au-delà de 1,3 M€ (0 % : IFI supprimé).",
    "%", 1.5, 0.0, 3.0, 0.1, [(0, -2.2), (0.75, -1.1), (1.5, 0), (2.25, 0.8), (3.0, 1.2)], 0.5,
    [e(G + "high_income", -0.008), e(G + "seniors", -0.004), e(G + "low_income", 0.004)], [1.5, 1.5, 0.0], decimals=1)
tax("patrimoine", "flat_tax", "Prélèvement forfaitaire sur le capital", "Taux unique sur les dividendes, intérêts et plus-values (flat tax).",
    "%", 30.0, 20.0, 50.0, 1.0, [(20, -4), (25, -2), (30, 0), (35, 2), (40, 3.2), (45, 3.6), (50, 3.4)], 5.0,
    [e(G + "high_income", -0.015), e("economy.businessConfidence", -0.008)], [30.0, 35.0, 42.0, 25.0], decimals=0)
tax("patrimoine", "inheritance", "Abattement sur les successions", "Part d'un héritage en ligne directe transmise sans impôt, par enfant.",
    "k€", 100.0, 50.0, 500.0, 10.0, [(50, 2.0), (100, 0), (200, -3.0), (300, -4.8), (500, -7.5)], 100.0,
    [e(G + "middle_income", 0.012), e(G + "seniors", 0.008), e(G + "low_income", -0.004)], [100.0, 250.0, 100.0], decimals=0)
tax("patrimoine", "inheritance_top", "Taux maximal sur les très gros héritages", "Taux marginal en ligne directe au-delà de 1,8 M€.",
    "%", 45.0, 30.0, 75.0, 1.0, [(30, -1.5), (45, 0), (55, 1.6), (60, 2.2), (70, 2.5), (75, 2.4)], 10.0,
    [e(G + "high_income", -0.012), e(G + "low_income", 0.005)], [45.0, 45.0, 60.0], decimals=0)
tax("patrimoine", "financial_transactions", "Taxe sur les transactions financières", "Taux sur les achats d'actions des grandes entreprises françaises.",
    "%", 0.3, 0.0, 1.0, 0.05, [(0, -1.7), (0.3, 0), (0.6, 1.3), (1.0, 1.8)], 0.3,
    [e("sector.banking", -0.015), e(G + "low_income", 0.003)], [0.3, 0.6, 0.0], decimals=2)
tax("patrimoine", "buybacks", "Taxe sur les rachats d'actions", "Taux sur les montants rachetés par les grandes entreprises.",
    "%", 0.0, 0.0, 5.0, 0.5, [(0, 0), (1, 0.6), (2, 1.0), (5, 1.5)], 1.0,
    [e("economy.businessConfidence", -0.004), e(G + "private_employees", 0.003)], [0.0, 1.0], decimals=1)

# Revenus et ménages
tax("revenus", "high_incomes", "Contribution sur les hauts revenus", "Surtaxe au-delà de 250 000 € de revenus par an.",
    "%", 3.5, 0.0, 12.0, 0.5, [(0, -1.3), (3.5, 0), (7, 1.5), (10, 2.0), (12, 2.1)], 3.5,
    [e(G + "high_income", -0.02), e(G + "low_income", 0.006)], [3.5, 7.0, 0.0], decimals=1)
tax("revenus", "family_quotient", "Plafond du quotient familial", "Avantage fiscal maximal par demi-part d'enfant, par an.",
    "€", 1800.0, 1000.0, 3000.0, 50.0, [(1000, 2.5), (1800, 0), (2500, -1.5), (3000, -2.4)], 500.0,
    [e(G + "adults", 0.008), e(G + "middle_income", 0.006)], [1800.0, 1300.0, 2400.0], decimals=0)
tax("revenus", "housing_tax", "Taxe d'habitation sur les résidences principales", "Part de l'ancienne taxe rétablie au profit des communes (0 % : supprimée).",
    "%", 0.0, 0.0, 100.0, 5.0, [(0, 0), (50, 9.0), (100, 18.0)], 30.0,
    [e(G + "middle_income", -0.012), e(G + "low_income", -0.008), e("economy.consumerConfidence", -0.004)], [0.0, 0.0, 35.0], decimals=0)
tax("revenus", "tax_niches", "Rabot sur les niches fiscales", "Réduction de tous les avantages fiscaux (emploi à domicile, investissement locatif...).",
    "%", 0.0, 0.0, 50.0, 1.0, [(0, 0), (10, 8.0), (20, 15.0), (30, 20.0), (50, 27.0)], 10.0,
    [e(G + "middle_income", -0.012), e(G + "high_income", -0.012), e("sector.construction", -0.01)], [0.0, 10.0, 20.0], decimals=0)
tax("revenus", "tax_shield", "Bouclier fiscal", "Plafond des impôts en part des revenus (100 % : pas de bouclier).",
    "%", 100.0, 40.0, 100.0, 5.0, [(40, -1.4), (50, -0.8), (70, -0.3), (100, 0)], -10.0,
    [e(G + "high_income", 0.003), e(G + "low_income", -0.003)], [100.0, 50.0], decimals=0)
tax("revenus", "exit_tax", "Exit tax", "Taux sur les plus-values latentes des contribuables qui s'expatrient.",
    "%", 12.8, 0.0, 30.0, 0.5, [(0, -0.2), (12.8, 0), (20, 0.25), (30, 0.3)], 10.0,
    [e(G + "high_income", -0.005), e("economy.businessConfidence", -0.003)], [12.8, 25.0], decimals=1)

# Entreprises
tax("entreprises", "production_taxes", "Impôts de production", "Taxes sur la valeur ajoutée et le chiffre d'affaires des entreprises (Md€ par an).",
    "Md€", 75.0, 60.0, 90.0, 1.0, [(60, -15), (75, 0), (90, 14)], 5.0,
    [e("economy.businessConfidence", -0.02), e("sector.industry", -0.02)], [75.0, 70.0, 78.0], decimals=0)
tax("entreprises", "low_wage_relief", "Allègements de charges sur les bas salaires", "Réductions de cotisations patronales près du SMIC (Md€ par an).",
    "Md€", 80.0, 60.0, 100.0, 1.0, [(60, 20), (80, 0), (100, -20)], 5.0,
    [e("economy.businessConfidence", 0.01), e("economy.unemployment", -0.001)], [80.0, 75.0, 85.0], decimals=0)
tax("entreprises", "research_credit", "Crédit d'impôt recherche", "Aide fiscale à la recherche des entreprises (Md€ par an).",
    "Md€", 7.5, 2.0, 15.0, 0.5, [(2, 5.5), (7.5, 0), (15, -7.5)], 2.5,
    [e("sector.tech", 0.012), e("sector.health", 0.006), e("economy.potentialGrowth", 0.0002, 365)], [7.5, 5.5, 12.5], decimals=1)
tax("entreprises", "windfall", "Taxe sur les superprofits", "Taux sur les bénéfices exceptionnels (énergie, banques, transport maritime).",
    "%", 0.0, 0.0, 60.0, 5.0, [(0, 0), (25, 5.0), (50, 7.0), (60, 7.2)], 25.0,
    [e("sector.energy", -0.02), e("sector.banking", -0.015), e(G + "low_income", 0.01), e("economy.businessConfidence", -0.008)], [0.0, 25.0], decimals=0)
tax("entreprises", "digital_tax", "Taxe sur les services numériques", "Taux sur le chiffre d'affaires des grandes plateformes réalisé en France.",
    "%", 3.0, 0.0, 10.0, 0.5, [(0, -0.7), (3, 0), (6, 0.7), (10, 1.0)], 3.0,
    [e("sector.tech", -0.008), e("memory.USA.DISAGREEMENT", -0.02)], [3.0, 6.0, 0.0], decimals=1)

tax("revenus", "tv_licence", "Contribution à l'audiovisuel public", "Ancienne redevance, supprimée en 2022 : l'audiovisuel public est financé par une part de TVA (0 € : supprimée).",
    "€/an", 0.0, 0.0, 200.0, 1.0, [(0, 0), (138, 3.2), (200, 4.4)], 50.0,
    [e(G + "low_income", -0.006), e(G + "seniors", -0.004), e(G + "middle_income", -0.003)], [0.0], decimals=0)

# Consommation
tax("consommation", "restaurant_vat", "TVA dans la restauration", "Taux réduit sur les repas au restaurant.",
    "%", 10.0, 5.5, 20.0, 0.5, [(5.5, -2.5), (10, 0), (20, 3.0)], 5.0,
    [e("sector.tourism", -0.02), e(G + "self_employed", -0.008)], [10.0, 5.5, 20.0], decimals=1)
tax("consommation", "essentials_vat", "TVA sur les produits de première nécessité", "Taux réduit sur l'alimentation de base.",
    "%", 5.5, 0.0, 10.0, 0.5, [(0, -7.0), (5.5, 0), (10, 5.0)], 5.5,
    [e(G + "low_income", -0.025), e(G + "middle_income", -0.012), e("economy.inflation", 0.004, 180)], [5.5, 0.0], decimals=1)
tax("consommation", "electricity_vat", "TVA sur l'électricité et le gaz", "Taux sur la consommation d'énergie des ménages.",
    "%", 20.0, 5.5, 20.0, 0.5, [(5.5, -6.0), (20, 0)], 14.5,
    [e(G + "low_income", -0.02), e(G + "rural", -0.012), e("economy.inflation", 0.003, 180)], [20.0, 5.5], decimals=1)

# Santé et environnement
tax("comportement", "tobacco", "Prix du paquet de cigarettes", "Les accises fixent le prix du paquet ; trop cher, la contrebande explose.",
    "€", 13.0, 10.0, 25.0, 0.5, [(10, -2.0), (13, 0), (16, 1.5), (20, 2.4), (25, 2.0)], 3.0,
    [e("quality.health", 0.004), e(G + "low_income", -0.008)], [13.0, 16.0], decimals=1)
tax("comportement", "sugar", "Taxe sur les boissons sucrées", "Montant par hectolitre pour les boissons les plus sucrées.",
    "€/hl", 7.0, 0.0, 40.0, 1.0, [(0, -0.3), (7, 0), (20, 0.6), (40, 0.9)], 10.0,
    [e("quality.health", 0.003), e("sector.agrifood", -0.008)], [7.0, 20.0], decimals=0)
tax("comportement", "alcohol", "Prix minimum par unité d'alcool", "Prix plancher d'une unité d'alcool (0 : pas de prix minimum).",
    "€", 0.0, 0.0, 1.0, 0.05, [(0, 0), (0.5, 0.8), (1.0, 1.1)], 0.5,
    [e("quality.health", 0.004), e(G + "rural", -0.008), e("sector.agrifood", -0.01)], [0.0, 0.5], decimals=2)
tax("comportement", "air_tickets", "Taxe sur les billets d'avion", "Montant par billet en classe économique sur les vols européens.",
    "€", 7.5, 0.0, 60.0, 0.5, [(0, -0.9), (7.5, 0), (22, 1.0), (40, 1.4), (60, 1.5)], 15.0,
    [e("quality.environment", 0.004), e("sector.tourism", -0.01), e("sector.transport", -0.01)], [7.5, 22.0], decimals=1)
tax("comportement", "vacant_homes", "Taxe sur les logements vacants", "Taux sur la valeur locative des logements vides depuis plus d'un an.",
    "%", 17.0, 0.0, 60.0, 1.0, [(0, -0.2), (17, 0), (34, 0.4), (60, 0.6)], 17.0,
    [e(G + "urban", 0.006), e(G + "high_income", -0.004)], [17.0, 34.0], decimals=0)
tax("comportement", "carbon_tax", "Taxe carbone", "Prix de la tonne de CO₂ dans les taxes sur les carburants et le chauffage (gelé depuis 2018).",
    "€/t", 44.6, 0.0, 250.0, 2.0, [(0, -9.0), (44.6, 0), (100, 9.0), (150, 16.0), (250, 25.0)], 20.0,
    [e(G + "rural", -0.025), e(G + "low_income", -0.012), e(G + "young", 0.004), e("quality.environment", 0.012, 1095), e("economy.inflation", 0.001, 365),
     e("chain.fuel_tax_protest", 0.08)], [44.6], {"reform": "carbon_tax", "above": 60.0}, decimals=0)

ids = [t["id"] for t in TAXES]
assert len(ids) == len(set(ids))
json.dump({"_doc": "Fiscalité détaillée. Généré par tools/datagen/fiscal_fr.py.",
           "categories": [{"id": i, "label": l, "icon": ic, "description": d} for i, l, ic, d in CATEGORIES], "taxes": TAXES},
          open(os.path.join(ROOT, "countries", "FRA", "fiscal.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(TAXES)} dispositifs fiscaux")
