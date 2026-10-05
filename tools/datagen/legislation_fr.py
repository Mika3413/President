"""Législation unifiée (France) -> assets/data/countries/FRA/legislation.json.

Trois choses :
  1. parameters : réglages chiffrés qui ne sont ni un impôt du budget ni une loi du catalogue
     (âge de la retraite, coup de pouce au SMIC, montant du RSA, vitesse sur les routes). Chacun a
     sa voie (BUDGET : loi de finances ; LAW : loi ordinaire ; DECREE : décret immédiat), ses
     effets pour une hausse ou une baisse de « per » unités, ses liens avec les réformes du
     catalogue (considérées comme adoptées au-delà d'un seuil).
  2. incidence : qui paie chaque grand impôt et qui profite de chaque budget (pour l'aperçu).
  3. builder : le constructeur de mesures « Action + Cible + Valeur + Conditions ».
     Cibles chiffrées (effectifs, revenus, mobilité, groupes concernés, service public lié...)
     et actions (taxer, réduire l'impôt, verser une prime, taxer le chiffre d'affaires,
     subventionner, plafonner les prix, interdire, obliger, recruter, augmenter les salaires).
     Les formules (effet Laffer, départs, prix, qualité des services) sont dans le moteur.
"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
G = "opinion.group."


def e(target, amount, days=0):
    d = {"target": target, "amount": amount}
    if days: d["days"] = days
    return d


reforms = {r["id"]: r for r in json.load(open(os.path.join(ROOT, "countries", "FRA", "reforms.json")))["reforms"]}


def scaled(effects, k):
    return [dict(x, amount=round(x["amount"] * k, 6)) for x in effects]


# --- 1. Réglages chiffrés ------------------------------------------------------------------------
P65 = reforms["pension_age_65"]
P62 = reforms["pension_age_62"]
MW = reforms["minimum_wage"]
PARAMETERS = [
    {"id": "pension_age", "channel": "LAW", "domain": "travail", "label": "Âge légal de départ à la retraite",
     "description": "L'âge à partir duquel on peut partir à la retraite. Chaque année de plus fait économiser des milliards, mais fâche les salariés.",
     "unit": "ans", "format": "years", "reference": 64, "min": 60, "max": 67, "step": 0.25, "per": 1,
     # +1 an : les effets de la réforme « 65 ans » ; −1 an : la moitié de la réforme « 62 ans » (deux ans plus tôt).
     "up": P65["immediateEffects"] + P65["longTermEffects"], "down": scaled(P62["immediateEffects"] + P62["longTermEffects"], 0.5),
     "difficultyUp": 0.08, "difficultyDown": 0.025, "appealUp": -0.25, "appealDown": 0.2,
     "links": [{"reform": "pension_age_65", "above": 64.0}, {"reform": "pension_age_62", "below": 64.0}]},
    {"id": "smic_boost", "channel": "DECREE", "domain": "travail", "label": "Coup de pouce au SMIC",
     "description": "Hausse du salaire minimum au-delà de l'inflation, cumulée depuis le début du mandat. Un SMIC ne se baisse pas.",
     "unit": "%", "format": "number", "decimals": 1, "reference": 0, "min": 0, "max": 25, "step": 0.5, "per": 5, "noDecrease": True,
     "up": MW["immediateEffects"] + MW["longTermEffects"], "down": [],
     "decreeLimit": 10, "links": [{"reform": "minimum_wage", "above": 0.0}]},
    {"id": "rsa_amount", "channel": "BUDGET", "domain": "solidarite", "label": "Montant du RSA (personne seule)",
     "description": "Revenu de solidarité active versé à 1,9 million de foyers. Une partie du budget « Solidarité ».",
     "unit": "€/mois", "format": "number", "decimals": 0, "reference": 646, "min": 0, "max": 3000, "step": 5, "per": 50,
     "costPerUnit": 1.9e6 * 12 / 1e9, "spendingItem": "solidarity",
     "up": [e(G + "low_income", 0.008), e(G + "inactive", 0.012), e(G + "self_employed", -0.003), e("economy.naturalUnemployment", 0.0003, 365)],
     "down": [e(G + "low_income", -0.008), e(G + "inactive", -0.012), e(G + "self_employed", 0.002)]},
    {"id": "apprentice_aid", "channel": "BUDGET", "domain": "solidarite", "label": "Aide à l'embauche d'un apprenti",
     "description": "Versée à l'employeur pour chaque apprenti la première année. 850 000 apprentis aujourd'hui.",
     "unit": "€", "format": "number", "decimals": 0, "reference": 5000, "min": 0, "max": 15000, "step": 500, "per": 1000,
     "costPerUnit": 850_000 / 1e9,
     "up": [e(G + "young", 0.004), e(G + "self_employed", 0.002), e("economy.unemployment", -0.0006, 365), e("economy.potentialGrowth", 0.0001, 730)],
     "down": [e(G + "young", -0.004), e(G + "self_employed", -0.002), e("economy.unemployment", 0.0006, 365), e("economy.potentialGrowth", -0.0001, 730)]},
    {"id": "pension_indexation", "channel": "BUDGET", "domain": "solidarite", "label": "Revalorisation des pensions (au-delà de l'inflation)",
     "description": "Hausse ou gel des retraites par rapport aux prix, cumulée depuis le début du mandat. Un point coûte environ 4 Md€ par an.",
     "unit": "%", "format": "number", "decimals": 1, "reference": 0, "min": -15, "max": 20, "step": 0.5, "per": 1,
     "costPerUnit": 4.1, "spendingItem": "pensions",
     "up": [e(G + "retirees", 0.007), e(G + "seniors", 0.005), e(G + "young", -0.001)],
     "down": [e(G + "retirees", -0.009), e(G + "seniors", -0.007)]},
    {"id": "housing_aid", "channel": "BUDGET", "domain": "solidarite", "label": "Aides au logement (APL)",
     "description": "Montant des APL par rapport à aujourd'hui (100 %). 16 Md€ pour 5,8 millions de foyers ; une hausse se retrouve en partie dans les loyers.",
     "unit": "%", "format": "number", "decimals": 0, "reference": 100, "min": 0, "max": 250, "step": 5, "per": 10,
     "costPerUnit": 0.16, "spendingItem": "solidarity",
     "up": [e(G + "young", 0.004), e(G + "low_income", 0.004), e(G + "urban", 0.002), e("economy.inflation", 0.0002, 365), e("sector.construction", 0.002)],
     "down": [e(G + "young", -0.005), e(G + "low_income", -0.005), e(G + "urban", -0.002)]},
    {"id": "family_allowance", "channel": "BUDGET", "domain": "solidarite", "label": "Allocations familiales",
     "description": "Montant des allocations familiales par rapport à aujourd'hui (100 %). 13 Md€ par an.",
     "unit": "%", "format": "number", "decimals": 0, "reference": 100, "min": 0, "max": 250, "step": 5, "per": 10,
     "costPerUnit": 0.13, "spendingItem": "solidarity",
     "up": [e(G + "adults", 0.004), e(G + "middle_income", 0.002)],
     "down": [e(G + "adults", -0.005), e(G + "middle_income", -0.003)]},
    {"id": "activity_bonus", "channel": "BUDGET", "domain": "solidarite", "label": "Prime d'activité",
     "description": "Complément de revenu des travailleurs modestes, par rapport à aujourd'hui (100 %). 10 Md€ par an ; elle rend le travail plus payant que le RSA.",
     "unit": "%", "format": "number", "decimals": 0, "reference": 100, "min": 0, "max": 250, "step": 5, "per": 10,
     "costPerUnit": 0.1, "spendingItem": "solidarity",
     "up": [e(G + "low_income", 0.004), e(G + "private_employees", 0.002), e("economy.unemployment", -0.0003, 365)],
     "down": [e(G + "low_income", -0.005), e(G + "private_employees", -0.002), e("economy.unemployment", 0.0003, 365)]},
    {"id": "civil_service_index", "channel": "BUDGET", "domain": "solidarite", "label": "Point d'indice des fonctionnaires",
     "description": "Hausse ou gel des salaires de 5,7 millions d'agents publics, cumulée depuis le début du mandat. Un point coûte environ 2,2 Md€ par an.",
     "unit": "%", "format": "number", "decimals": 1, "reference": 0, "min": -10, "max": 30, "step": 0.5, "per": 1,
     "costPerUnit": 2.2,
     "up": [e(G + "civil_servants", 0.008), e("quality.education", 0.002, 365), e("quality.health", 0.002, 365), e("economy.inflation", 0.0002, 365)],
     "down": [e(G + "civil_servants", -0.012), e("quality.education", -0.003, 365), e("quality.health", -0.003, 365)]},
    {"id": "speed_limit", "channel": "DECREE", "domain": "securite", "label": "Vitesse maximale sur les routes secondaires",
     "description": "Limitation sur les routes à double sens sans séparateur. Plus vite : moins de colère rurale, plus de morts.",
     "unit": "km/h", "format": "number", "decimals": 0, "reference": 80, "min": 70, "max": 100, "step": 10, "per": 10,
     "up": [e(G + "rural", 0.012), e(G + "urban", -0.002), e("quality.security", -0.006), e("quality.environment", -0.003)],
     "down": [e(G + "rural", -0.012), e("quality.security", 0.006), e("quality.environment", 0.003)], "decreeLimit": 10},
]
for p in PARAMETERS:
    for l in p.get("links", []):
        assert l["reform"] in reforms, l

# --- 2. Incidence : qui paie, qui profite ---------------------------------------------------------
INCIDENCE = {
    "tax:social_contributions": {"private_employees": 0.45, "self_employed": 0.3, "civil_servants": 0.15},
    "tax:vat": {"low_income": 0.4, "middle_income": 0.35, "retirees": 0.15},
    "tax:csg": {"private_employees": 0.35, "retirees": 0.35, "civil_servants": 0.2},
    "tax:income_tax": {"high_income": 0.55, "middle_income": 0.35},
    "tax:corporate_tax": {"self_employed": 0.4, "high_income": 0.3},
    "tax:property_taxes": {"middle_income": 0.35, "high_income": 0.3, "seniors": 0.25},
    "tax:energy_taxes": {"rural": 0.45, "low_income": 0.3},
    "spend:pensions": {"retirees": 0.7, "seniors": 0.6},
    "spend:health": {"seniors": 0.4, "adults": 0.25, "low_income": 0.2},
    "spend:education": {"young": 0.5, "adults": 0.3, "civil_servants": 0.3},
    "spend:defense": {"civil_servants": 0.1},
    "spend:police": {"urban": 0.3, "seniors": 0.3},
    "spend:justice": {"urban": 0.2},
    "spend:solidarity": {"low_income": 0.6, "inactive": 0.4},
    "spend:unemployment_benefits": {"inactive": 0.6, "low_income": 0.3},
    "spend:transport": {"rural": 0.35, "urban": 0.35},
    "spend:ecology": {"young": 0.4},
    "spend:agriculture": {"rural": 0.6},
    "spend:local_authorities": {"rural": 0.4, "civil_servants": 0.2},
    "spend:state_operations": {"civil_servants": 0.6},
}

# --- 3. Constructeur de mesures ------------------------------------------------------------------
# Cibles : id, catégorie, libellé (« les médecins libéraux »), nom court, effectif, revenu moyen (€/an)
# ou assiette (Md€), mobilité (0..1), groupes {groupe: poids}, acteurs {acteur: poids}, et options.
TARGETS = []


def target(id, category, label, short, count=0.0, income=0.0, base=0.0, baseLabel="", mobility=0.2, groups=None, actors=None, **kw):
    d = {"id": id, "category": category, "label": label, "short": short, "count": count, "income": income, "base": base,
         "baseLabel": baseLabel, "mobility": mobility, "groups": groups or {}, "actors": actors or {}}
    d.update(kw)
    TARGETS.append(d)


# Professions
target("doctors", "profession", "les médecins libéraux", "Médecins libéraux", count=120_000, income=120_000, mobility=0.3,
       groups={"high_income": 0.25, "self_employed": 0.35, "seniors": 0.15}, quality="health", qualityWeight=0.6, rural=0.12, idf=0.22, sigma=0.5,
       obligation={"label": "exercer en zone sous-dotée", "unit": "ans", "max": 5, "step": 1, "risk": 0.15})
target("hospital_doctors", "profession", "les médecins hospitaliers", "Médecins hospitaliers", count=110_000, income=85_000, mobility=0.25,
       groups={"civil_servants": 0.3, "high_income": 0.15}, quality="health", qualityWeight=0.6, public=True, salary=130_000, rural=0.1)
target("nurses", "profession", "les infirmiers", "Infirmiers", count=640_000, income=34_000, mobility=0.12,
       groups={"civil_servants": 0.3, "middle_income": 0.15}, quality="health", qualityWeight=0.5, public=True, salary=52_000, rural=0.15)
target("pharmacists", "profession", "les pharmaciens d'officine", "Pharmaciens", count=55_000, income=85_000, mobility=0.15,
       groups={"self_employed": 0.2, "high_income": 0.1}, quality="health", qualityWeight=0.15, rural=0.2,
       obligation={"label": "maintenir une officine en zone rurale", "unit": "ans", "max": 5, "step": 1, "risk": 0.2})
target("teachers", "profession", "les enseignants", "Enseignants", count=870_000, income=36_000, mobility=0.08,
       groups={"civil_servants": 0.5, "middle_income": 0.15}, actors={"union_public": 0.5}, quality="education", qualityWeight=0.7, public=True, salary=56_000,
       event="national_strike", rural=0.2)
target("police", "profession", "les policiers et gendarmes", "Policiers et gendarmes", count=250_000, income=34_000, mobility=0.05,
       groups={"civil_servants": 0.3, "seniors": 0.1}, quality="security", qualityWeight=0.7, public=True, salary=55_000, rural=0.3)
target("judges", "profession", "les magistrats", "Magistrats", count=9_000, income=70_000, mobility=0.05,
       groups={"civil_servants": 0.05}, quality="justice", qualityWeight=0.8, public=True, salary=105_000)
target("court_clerks", "profession", "les greffiers", "Greffiers", count=11_000, income=30_000, mobility=0.05,
       groups={"civil_servants": 0.05}, quality="justice", qualityWeight=0.4, public=True, salary=48_000)
target("soldiers", "profession", "les militaires", "Militaires", count=205_000, income=30_000, mobility=0.05,
       groups={"civil_servants": 0.15}, quality="defense", qualityWeight=0.7, public=True, salary=50_000, rural=0.3)
target("prison_guards", "profession", "les surveillants pénitentiaires", "Surveillants de prison", count=28_000, income=28_000, mobility=0.05,
       groups={"civil_servants": 0.05}, quality="justice", qualityWeight=0.3, public=True, salary=45_000)
target("caregivers", "profession", "les aides-soignants", "Aides-soignants", count=400_000, income=25_000, mobility=0.1,
       groups={"civil_servants": 0.2, "low_income": 0.2}, quality="health", qualityWeight=0.35, public=True, salary=40_000)
target("researchers", "profession", "les chercheurs publics", "Chercheurs", count=110_000, income=45_000, mobility=0.35,
       groups={"civil_servants": 0.1, "young": 0.05}, quality="education", qualityWeight=0.25, public=True, salary=70_000, sector="tech")
target("farmers", "profession", "les agriculteurs", "Agriculteurs", count=390_000, income=25_000, mobility=0.05,
       groups={"rural": 0.6, "self_employed": 0.4}, actors={"farmers_union": 1.0}, quality="agriculture", qualityWeight=0.4, sector="agrifood",
       event="farmers_protest", rural=0.85)
target("lawyers", "profession", "les avocats", "Avocats", count=77_000, income=80_000, mobility=0.15, groups={"high_income": 0.1, "self_employed": 0.1},
       quality="justice", qualityWeight=0.1, idf=0.45, sigma=0.8)
target("notaries", "profession", "les notaires", "Notaires", count=18_000, income=230_000, mobility=0.1, groups={"high_income": 0.05}, sigma=0.6)
target("taxi_vtc", "profession", "les chauffeurs de taxi et de VTC", "Taxis et VTC", count=120_000, income=24_000, mobility=0.05,
       groups={"self_employed": 0.15, "urban": 0.1, "low_income": 0.1}, sector="transport", idf=0.6, event="fuel_blockade")
target("truckers", "profession", "les routiers", "Routiers", count=400_000, income=28_000, mobility=0.05,
       groups={"private_employees": 0.1, "rural": 0.15}, sector="transport", event="fuel_blockade", rural=0.4)
target("artisans", "profession", "les artisans et commerçants", "Artisans et commerçants", count=3_000_000, income=32_000, mobility=0.08,
       groups={"self_employed": 0.8, "rural": 0.2}, actors={"employers_small": 0.8}, sector="retail", rural=0.3, sigma=0.8)
target("athletes", "profession", "les sportifs professionnels", "Sportifs professionnels", count=12_000, income=250_000, mobility=0.6,
       groups={"young": 0.05}, sigma=1.2)
target("executives", "profession", "les cadres", "Cadres", count=5_400_000, income=62_000, mobility=0.08,
       groups={"high_income": 0.5, "private_employees": 0.4, "urban": 0.2}, idf=0.38, sigma=0.5)
target("civil_servants", "profession", "les fonctionnaires", "Fonctionnaires", count=5_700_000, income=31_000, mobility=0.03,
       groups={"civil_servants": 1.0}, actors={"union_public": 1.0}, salary=47_000, event="national_strike")
# Groupes de la population
target("top1", "group", "les 1 % les plus riches", "1 % les plus riches", count=400_000, income=330_000, mobility=0.45,
       groups={"high_income": 0.6}, actors={"employers_big": 0.4}, sigma=0.9, idf=0.5, richTax=True)
target("top10", "group", "les 10 % les plus aisés", "10 % les plus aisés", count=4_000_000, income=110_000, mobility=0.15,
       groups={"high_income": 1.0, "urban": 0.2}, sigma=0.6, idf=0.35, richTax=True)
target("middle_class", "group", "les classes moyennes", "Classes moyennes", count=12_000_000, income=38_000, mobility=0.03,
       groups={"middle_income": 1.0, "adults": 0.3}, sigma=0.3)
target("modest", "group", "les ménages modestes", "Ménages modestes", count=9_000_000, income=17_000, mobility=0.01,
       groups={"low_income": 1.0}, sigma=0.35)
target("retirees", "group", "les retraités", "Retraités", count=17_000_000, income=22_000, mobility=0.02,
       groups={"retirees": 1.0, "seniors": 0.9}, sigma=0.55, rural=0.3)
target("students", "group", "les étudiants", "Étudiants", count=3_000_000, income=9_000, mobility=0.02,
       groups={"young": 0.7}, sigma=0.6, idf=0.3)
target("young_workers", "group", "les jeunes actifs de moins de 30 ans", "Jeunes actifs", count=5_000_000, income=24_000, mobility=0.05,
       groups={"young": 0.8, "private_employees": 0.2}, sigma=0.4)
target("families", "group", "les familles avec enfants", "Familles avec enfants", count=8_000_000, income=45_000, mobility=0.02,
       groups={"adults": 0.7, "middle_income": 0.3}, sigma=0.5)
target("landlords", "group", "les propriétaires bailleurs", "Propriétaires bailleurs", count=3_500_000, income=13_000, mobility=0.1,
       groups={"high_income": 0.3, "seniors": 0.3}, sigma=0.9, sector="construction")
target("tenants", "group", "les locataires", "Locataires", count=11_000_000, income=26_000, mobility=0.01,
       groups={"low_income": 0.4, "young": 0.4, "urban": 0.4}, sigma=0.5, idf=0.25)
target("unemployed", "group", "les chômeurs", "Chômeurs", count=2_300_000, income=14_000, mobility=0.0,
       groups={"inactive": 0.8, "low_income": 0.4}, sigma=0.5)
target("rural_people", "group", "les habitants des zones rurales", "Ruraux", count=11_000_000, income=25_000, mobility=0.02,
       groups={"rural": 1.0}, sigma=0.5, rural=1.0)
# Entreprises (taxe ou aide sur une assiette en Md€)
target("big_companies", "company", "les grandes entreprises", "Grandes entreprises", base=120, baseLabel="bénéfices", mobility=0.25,
       groups={"high_income": 0.2}, actors={"employers_big": 1.0}, sector="industry", priceWeight=0.2)
target("smes", "company", "les PME", "PME", base=60, baseLabel="bénéfices", mobility=0.05,
       groups={"self_employed": 0.5}, actors={"employers_small": 1.0}, sector="retail", priceWeight=0.3)
target("banks", "company", "les banques", "Banques", base=35, baseLabel="bénéfices", mobility=0.15,
       groups={"high_income": 0.1}, actors={"banking_lobby": 1.0}, sector="banking", priceWeight=0.05)
target("energy_firms", "company", "les énergéticiens", "Énergéticiens", base=25, baseLabel="bénéfices", mobility=0.1,
       actors={"oil_lobby": 1.0}, sector="energy", priceWeight=0.4)
target("tech_giants", "company", "les géants du numérique", "Géants du numérique", base=30, baseLabel="chiffre d'affaires en France", mobility=0.2,
       actors={"tech_lobby": 1.0}, sector="tech", priceWeight=0.05, foreign="USA")
target("supermarkets", "company", "la grande distribution", "Grande distribution", base=12, baseLabel="marges", mobility=0.05,
       actors={"retail_lobby": 1.0}, sector="retail", priceWeight=0.6)
target("pharma", "company", "les laboratoires pharmaceutiques", "Laboratoires pharmaceutiques", base=30, baseLabel="chiffre d'affaires", mobility=0.2,
       actors={"pharma_lobby": 1.0}, sector="health", priceWeight=0.1)
target("airlines", "company", "les compagnies aériennes", "Compagnies aériennes", base=25, baseLabel="chiffre d'affaires", mobility=0.15,
       sector="transport", priceWeight=0.05, environment=0.004)
target("shipping", "company", "les armateurs", "Armateurs", base=10, baseLabel="bénéfices", mobility=0.4, sector="transport", priceWeight=0.05)
target("platforms", "company", "les plateformes de livraison et de VTC", "Plateformes (VTC, livraison)", base=5, baseLabel="chiffre d'affaires", mobility=0.2,
       sector="transport", priceWeight=0.02, foreign="USA")
# Produits (taxe sur les ventes, plafonnement des prix)
target("fuel", "product", "les carburants", "Carburants", base=60, baseLabel="ventes", mobility=0.1, groups={"rural": 0.6, "low_income": 0.3},
       sector="transport", priceWeight=0.04, environment=0.01, event="fuel_tax_protest", normalIncrease=3.0, capSector="energy",
       capWinners={"rural": 1.0, "low_income": 0.6}, capLosers={})
target("junk_food", "product", "les aliments ultra-transformés", "Aliments ultra-transformés", base=50, baseLabel="ventes", mobility=0.05,
       groups={"low_income": 0.3}, sector="agrifood", priceWeight=0.05, health=0.006)
target("plastic", "product", "les emballages plastiques", "Emballages plastiques", base=10, baseLabel="ventes", mobility=0.1,
       sector="industry", priceWeight=0.01, environment=0.008)
target("suv", "product", "les voitures lourdes (SUV)", "Voitures lourdes", base=20, baseLabel="ventes", mobility=0.15,
       groups={"high_income": 0.2, "rural": 0.2}, sector="industry", priceWeight=0.01, environment=0.006)
target("private_jets", "product", "les vols en jet privé", "Jets privés", base=1.0, baseLabel="ventes", mobility=0.3,
       groups={"high_income": 0.05}, priceWeight=0.0, environment=0.002, symbolic=0.004)
target("luxury", "product", "les produits de luxe", "Produits de luxe", base=40, baseLabel="ventes en France", mobility=0.3,
       sector="luxury", priceWeight=0.0, symbolic=0.003)
target("betting", "product", "les paris sportifs en ligne", "Paris en ligne", base=3, baseLabel="mises nettes", mobility=0.2,
       groups={"young": 0.1}, priceWeight=0.0, health=0.002)
target("rents", "product", "les loyers", "Loyers", base=90, baseLabel="loyers", mobility=0.0, groups={"tenants_dummy": 0}, priceWeight=0.07,
       normalIncrease=3.5, capSector="construction", capWinners={"young": 0.8, "urban": 0.8, "low_income": 0.6}, capLosers={"high_income": 0.5, "seniors": 0.4})
target("electricity", "product", "l'électricité", "Électricité", base=45, baseLabel="ventes aux ménages", mobility=0.0, groups={"low_income": 0.3, "rural": 0.3},
       priceWeight=0.03, normalIncrease=5.0, capSector="energy", capWinners={"low_income": 0.8, "rural": 0.6, "seniors": 0.4}, capLosers={}, capCost=0.9)
target("food", "product", "les produits alimentaires de base", "Alimentation de base", base=150, baseLabel="ventes", mobility=0.0, priceWeight=0.15,
       normalIncrease=3.0, capSector="agrifood", capWinners={"low_income": 1.0, "middle_income": 0.5}, capLosers={"rural": 0.5, "self_employed": 0.2})
target("medicines", "product", "les médicaments", "Médicaments", base=30, baseLabel="ventes", mobility=0.0, priceWeight=0.02,
       normalIncrease=1.0, capSector="health", capWinners={"seniors": 0.4}, capLosers={})
# Pratiques que l'on peut interdire
target("single_plastic", "practice", "le plastique à usage unique", "Plastique à usage unique", sector="industry",
       ban={"label": "Interdire", "revenueLoss": 0.0, "effects": [e("quality.environment", 0.01), e("sector.industry", -0.008), e(G + "young", 0.006)], "liberty": 0, "risk": 0.0})
target("short_flights", "practice", "les vols intérieurs quand le train fait moins de 4 h", "Vols courts", sector="transport",
       ban={"label": "Interdire", "revenueLoss": 0.2, "effects": [e("quality.environment", 0.008), e("sector.transport", -0.01), e(G + "young", 0.006), e(G + "high_income", -0.004)], "liberty": -0.5, "risk": 0.05})
target("glyphosate", "practice", "le glyphosate", "Glyphosate", sector="agrifood", event="farmers_protest",
       ban={"label": "Interdire", "revenueLoss": 0.0, "effects": [e("quality.environment", 0.012), e("quality.agriculture", -0.015), e("sector.agrifood", -0.015), e(G + "rural", -0.012), e(G + "young", 0.008)], "liberty": 0, "risk": 0.05})
target("fast_fashion_ads", "practice", "la publicité pour la mode ultra-éphémère", "Pub pour la mode éphémère", sector="retail",
       ban={"label": "Interdire", "revenueLoss": 0.0, "effects": [e("quality.environment", 0.004), e("sector.retail", 0.004), e(G + "young", -0.003)], "liberty": -0.5, "risk": 0.1})
target("phones_school", "practice", "le téléphone portable au collège et au lycée", "Téléphone à l'école",
       ban={"label": "Interdire", "revenueLoss": 0.0, "effects": [e("quality.education", 0.008), e(G + "adults", 0.01), e(G + "young", -0.006)], "liberty": -0.5, "risk": 0.0})
target("short_rentals", "practice", "les locations touristiques de courte durée en zone tendue", "Locations de type Airbnb", sector="tourism",
       ban={"label": "Interdire", "revenueLoss": 0.1, "effects": [e("sector.tourism", -0.012), e(G + "urban", 0.008), e(G + "young", 0.006), e(G + "high_income", -0.006)], "liberty": -1, "risk": 0.25})
target("cage_farming", "practice", "l'élevage de poules en cage", "Poules en cage", sector="agrifood", event="farmers_protest",
       ban={"label": "Interdire", "revenueLoss": 0.0, "effects": [e("sector.agrifood", -0.006), e(G + "young", 0.006), e(G + "rural", -0.004), e("economy.inflation", 0.0005)], "liberty": 0, "risk": 0.0})
target("cash_large", "practice", "les paiements en espèces au-delà de 500 €", "Paiements en espèces", sector="retail",
       ban={"label": "Interdire", "revenueLoss": -1.0, "effects": [e(G + "seniors", -0.006), e(G + "self_employed", -0.004), e("quality.justice", 0.004)], "liberty": -1.5, "risk": 0.1})

ACTIONS = [
    # id, verbe, modèle, catégories, unité, min, max, pas, défaut, voie, conditions, condition requise sur la cible, phrase
    ("surtax", "Taxer", "SURTAX", ["profession", "group"], "%", 0.5, 90, 0.5, 5, "BUDGET", ["threshold", "zone", "exempt", "duration", "phaseIn"], None,
     "surtaxe de {value} sur les revenus"),
    ("tax_cut", "Baisser l'impôt de", "TAX_CUT", ["profession", "group"], "%", 1, 100, 1, 10, "BUDGET", ["threshold", "zone", "duration", "phaseIn"], None,
     "baisse de {value} de leur impôt sur le revenu"),
    ("bonus", "Verser une prime à", "BONUS", ["profession", "group"], "€/mois", 10, 3000, 10, 100, "BUDGET", ["below", "zone", "duration"], None,
     "prime de {value}"),
    ("turnover_tax", "Taxer", "TURNOVER_TAX", ["company", "product"], "%", 0.5, 90, 0.5, 5, "BUDGET", ["duration", "phaseIn"], None,
     "taxe de {value} sur les {baseLabel}"),
    ("subsidy", "Subventionner", "SUBSIDY", ["company", "product", "profession"], "Md€/an", 0.1, 50, 0.1, 1, "BUDGET", ["duration"], None,
     "aide de {value}"),
    ("price_cap", "Plafonner la hausse des prix de", "PRICE_CAP", ["product"], "%/an", -5, 10, 0.5, 1, "LAW", ["duration", "zone"], "normalIncrease",
     "hausse limitée à {value}"),
    ("ban", "Interdire", "BAN", ["practice"], "", 0, 1, 1, 1, "LAW", ["phaseIn"], "ban", ""),
    ("oblige", "Obliger", "OBLIGATION", ["profession"], "ans", 1, 5, 1, 2, "LAW", ["phaseIn"], "obligation", "{obligation} pendant {value}"),
    ("recruit", "Recruter", "RECRUIT", ["profession"], "postes", 500, 300_000, 500, 5000, "BUDGET", ["zone", "phaseIn"], "public",
     "{value} de plus"),
    ("pay_raise", "Augmenter les salaires de", "PAY_RAISE", ["profession"], "%", 1, 100, 1, 5, "BUDGET", ["phaseIn"], "public",
     "hausse de {value} des salaires"),
    ("job_cuts", "Supprimer", "JOB_CUTS", ["profession"], "postes", 500, 300_000, 500, 5000, "BUDGET", ["zone", "phaseIn"], "public",
     "{value} en moins"),
]

# Libellé des boutons de l'étape « Que voulez-vous faire ? ».
BUTTONS = {"surtax": "Taxer des revenus", "tax_cut": "Baisser l'impôt", "bonus": "Verser une prime", "turnover_tax": "Taxer des entreprises ou des produits",
           "subsidy": "Subventionner", "price_cap": "Plafonner des prix", "ban": "Interdire", "oblige": "Obliger", "recruit": "Recruter",
           "pay_raise": "Augmenter des salaires", "job_cuts": "Supprimer des postes"}

THRESHOLDS = [0, 30_000, 50_000, 80_000, 120_000, 180_000, 250_000, 500_000]
BELOW = [0, 15_000, 20_000, 30_000, 50_000]
ZONES = [("national", "Toute la France"), ("idf", "Île-de-France seulement"), ("province", "Hors Île-de-France"),
         ("rural", "Zones rurales seulement"), ("urban", "Grandes villes seulement")]
DURATIONS = [0, 1, 2, 3, 5]

# Validation
social = json.load(open(os.path.join(ROOT, "countries", "FRA", "social_groups.json")))
groups = {g["id"] for g in social["groups"]}
actors = {a["id"] for a in json.load(open(os.path.join(ROOT, "countries", "FRA", "actors.json")))["actors"]}
sectors = {s["id"] for s in json.load(open(os.path.join(ROOT, "countries", "FRA", "sectors.json")))["sectors"]}
gov = json.load(open(os.path.join(ROOT, "countries", "FRA", "government.json")))
qualities = set(gov["initialServiceQuality"])
events = set()
for f in os.listdir(os.path.join(ROOT, "events")):
    events |= {x["id"] for x in json.load(open(os.path.join(ROOT, "events", f))).get("events", [])}
for t in TARGETS:
    t["groups"] = {g: w for g, w in t["groups"].items() if w}
    assert set(t["groups"]) <= groups, (t["id"], set(t["groups"]) - groups)
    for k in ("capWinners", "capLosers"):
        assert set(t.get(k, {})) <= groups, (t["id"], k)
    assert set(t["actors"]) <= actors, (t["id"], set(t["actors"]) - actors)
    assert t.get("sector") is None or t["sector"] in sectors, t["id"]
    assert t.get("capSector") is None or t["capSector"] in sectors, t["id"]
    assert t.get("quality") is None or t["quality"] in qualities, t["id"]
    assert t.get("event") is None or t["event"] in events, (t["id"], t.get("event"))
for p in PARAMETERS:
    for fx in p.get("up", []) + p.get("down", []):
        if fx["target"].startswith(G): assert fx["target"][len(G):] in groups, fx
    assert p.get("spendingItem") is None or p["spendingItem"] in {"solidarity", "pensions"}

out = {
    "_doc": "Législation unifiée : réglages chiffrés, incidence, constructeur de mesures. Généré par tools/datagen/legislation_fr.py.",
    "parameters": PARAMETERS,
    "incidence": INCIDENCE,
    "domains": [
        {"id": "budget_tax", "label": "Impôts", "icon": "€"}, {"id": "budget_spending", "label": "Dépenses", "icon": "▣"},
        {"id": "fiscal", "label": "Fiscalité fine", "icon": "◆"}, {"id": "measures", "label": "Mesures sur mesure", "icon": "✎"},
        {"id": "societe", "label": "Société", "icon": "♥"}, {"id": "justice", "label": "Justice et sécurité", "icon": "⚖"},
        {"id": "travail", "label": "Travail et social", "icon": "⚒"}, {"id": "libertes", "label": "Libertés et médias", "icon": "▤"},
        {"id": "institutions", "label": "Constitution et institutions", "icon": "⌂"}, {"id": "reformes", "label": "Grandes réformes", "icon": "★"},
        {"id": "solidarite", "label": "Solidarité", "icon": "♥"}, {"id": "securite", "label": "Sécurité routière", "icon": "⚠"},
    ],
    "builder": {
        "targets": TARGETS,
        "actions": [{"id": i, "verb": v, "button": BUTTONS[i], "model": m, "categories": c, "unit": u, "min": lo, "max": hi, "step": st, "default": d, "channel": ch,
                     "conditions": cond, **({"requires": req} if req else {}), "phrase": ph}
                    for i, v, m, c, u, lo, hi, st, d, ch, cond, req, ph in ACTIONS],
        "categories": [{"id": "profession", "label": "Professions"}, {"id": "group", "label": "Groupes de la population"},
                       {"id": "company", "label": "Entreprises"}, {"id": "product", "label": "Produits et prix"}, {"id": "practice", "label": "Pratiques"}],
        "thresholds": THRESHOLDS, "below": BELOW, "zones": [{"id": i, "label": l} for i, l in ZONES], "durations": DURATIONS,
    },
}
json.dump(out, open(os.path.join(ROOT, "countries", "FRA", "legislation.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)

# Le pays déclare son fichier de législation.
cpath = os.path.join(ROOT, "countries", "FRA", "country.json")
country = json.load(open(cpath), object_pairs_hook=__import__("collections").OrderedDict)
if "legislation" not in country:
    items = list(country.items())
    i = [k for k, _ in items].index("fiscal") + 1
    items.insert(i, ("legislation", "countries/FRA/legislation.json"))
    country = __import__("collections").OrderedDict(items)
    json.dump(country, open(cpath, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(PARAMETERS)} réglages, {len(TARGETS)} cibles, {len(ACTIONS)} actions")
