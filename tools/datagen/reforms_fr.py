"""Génère les réformes et promesses de la France (assets/data/countries/FRA/reforms.json, promises.json)."""
import json, os
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data", "countries", "FRA"))

def E(t, a, days=0, delay=0):
    d = {"target": t, "amount": a}
    if days: d["days"] = days
    if delay: d["delayDays"] = delay
    return d

def R(id, title, cat, desc, diff, imm, lt, summary, excl=()):
    return {"id": id, "title": title, "category": cat, "description": desc, "difficulty": diff,
            "immediateEffects": imm, "longTermEffects": lt, "exclusiveWith": list(excl), "summary": summary}

O = "opinion.group."
reforms = [
 R("pension_age_65", "Report de l'âge légal de départ à 65 ans", "Retraites",
   "Relève progressivement l'âge légal de départ. Réduit les dépenses de retraite et augmente la population active, au prix d'une forte contestation.", 0.08,
   [E(O+"private_employees", -0.06), E(O+"seniors", -0.02), E(O+"young", -0.02), E(O+"civil_servants", -0.04), E(O+"self_employed", 0.02)],
   [E("spending.pensions", -0.05, 1460), E("economy.potentialGrowth", 0.0015, 1460), E("economy.naturalUnemployment", 0.001, 730)],
   "Retraites −5 % sur 4 ans, croissance potentielle accrue ; très impopulaire chez les actifs.", ["pension_age_62"]),
 R("pension_age_62", "Retour à la retraite à 62 ans", "Retraites",
   "Abaisse l'âge légal de départ. Mesure populaire, coûteuse pour les finances publiques et la population active.", 0.05,
   [E(O+"private_employees", 0.05), E(O+"seniors", 0.02), E(O+"self_employed", -0.03), E(O+"high_income", -0.02)],
   [E("spending.pensions", 0.05, 730), E("economy.potentialGrowth", -0.0015, 1460)],
   "Retraites +5 %, croissance potentielle réduite.", ["pension_age_65"]),
 R("unemployment_insurance", "Durcissement de l'assurance chômage", "Travail",
   "Réduit la durée et le montant des indemnités pour inciter au retour à l'emploi.", 0.04,
   [E(O+"inactive", -0.06), E(O+"low_income", -0.03), E(O+"self_employed", 0.02)],
   [E("spending.unemployment_benefits", -0.12, 365), E("economy.naturalUnemployment", -0.004, 1095)],
   "Indemnités −12 %, chômage structurel −0,4 point en 3 ans."),
 R("labour_code", "Assouplissement du code du travail", "Travail",
   "Facilite les embauches et les licenciements, développe la négociation d'entreprise.", 0.05,
   [E(O+"private_employees", -0.04), E(O+"self_employed", 0.05), E("economy.businessConfidence", 0.03)],
   [E("economy.naturalUnemployment", -0.004, 1095), E("economy.potentialGrowth", 0.001, 1095)],
   "Chômage structurel −0,4 point, confiance des entreprises accrue."),
 R("minimum_wage", "Hausse du salaire minimum de 5 %", "Travail",
   "Augmente le SMIC au-delà de l'inflation.", 0.0,
   [E(O+"low_income", 0.05), E(O+"private_employees", 0.02), E(O+"self_employed", -0.04), E("economy.businessConfidence", -0.02)],
   [E("economy.inflation", 0.002, 180), E("economy.naturalUnemployment", 0.002, 730), E("spending.solidarity", 0.01, 180)],
   "Pouvoir d'achat des plus modestes ; un peu d'inflation et de chômage."),
 R("police_recruitment", "Recrutement de 10 000 policiers et gendarmes", "Sécurité",
   "Plan pluriannuel de recrutement et d'équipement des forces de l'ordre.", 0.0,
   [E(O+"seniors", 0.02), E(O+"rural", 0.01)],
   [E("spending.police", 0.08, 365), E("quality.security", 0.04, 730)],
   "Sécurité intérieure +, dépense +8 % sur la police."),
 R("prison_plan", "Plan de 15 000 places de prison", "Justice",
   "Construction de nouveaux établissements pénitentiaires.", 0.0,
   [E(O+"seniors", 0.01)],
   [E("budget.oneOff", 5.0, 1460), E("quality.justice", 0.06, 1460), E("quality.security", 0.01, 1460)],
   "5 Md€ sur 4 ans ; justice et sécurité améliorées."),
 R("hospital_plan", "Grand plan pour l'hôpital", "Santé",
   "Revalorisations, recrutements et rénovation des hôpitaux.", 0.0,
   [E(O+"civil_servants", 0.03), E(O+"seniors", 0.02)],
   [E("spending.health", 0.04, 365), E("quality.health", 0.06, 1095)],
   "Santé +4 % de budget, qualité des soins accrue en 3 ans."),
 R("teachers_plan", "Revalorisation des enseignants", "Éducation",
   "Hausse des salaires et du recrutement dans l'Éducation nationale.", 0.0,
   [E(O+"civil_servants", 0.04), E(O+"young", 0.01)],
   [E("spending.education", 0.05, 365), E("quality.education", 0.05, 1095)],
   "Éducation +5 % de budget, qualité accrue."),
 R("housing_plan", "Plan de construction de logements", "Logement",
   "Soutien massif à la construction neuve et à la rénovation.", 0.0,
   [E(O+"young", 0.03), E(O+"low_income", 0.02), E(O+"urban", 0.02)],
   [E("budget.oneOff", 12.0, 1095), E("economy.output", 0.003, 1095), E("economy.unemployment", -0.002, 1095)],
   "12 Md€ sur 3 ans : activité et emploi dans le BTP."),
 R("immigration_strict", "Durcissement de la politique migratoire", "Immigration",
   "Restreint l'immigration et accélère les éloignements.", 0.04,
   [E(O+"seniors", 0.03), E(O+"rural", 0.03), E(O+"urban", -0.02), E(O+"young", -0.03)],
   [E("demography.immigration", -0.4, 365), E("economy.potentialGrowth", -0.0008, 1460)],
   "Immigration −40 % ; population active moins dynamique.", ["immigration_work"]),
 R("immigration_work", "Immigration de travail ciblée", "Immigration",
   "Facilite le recrutement de travailleurs étrangers dans les métiers en tension.", 0.03,
   [E(O+"self_employed", 0.03), E(O+"seniors", -0.02), E(O+"rural", -0.02)],
   [E("demography.immigration", 0.25, 365), E("economy.potentialGrowth", 0.0008, 1460)],
   "Immigration +25 %, croissance potentielle accrue.", ["immigration_strict"]),
 R("france_travail", "Création de France Travail", "Travail",
   "Pôle emploi, missions locales et Cap emploi fusionnent en un guichet unique ; chaque allocataire du RSA est inscrit et accompagné.", 0.02,
   [E(O+"inactive", -0.01), E(O+"civil_servants", -0.01), E(O+"self_employed", 0.01), E("budget.oneOff", 1.0, 365)],
   [E("economy.naturalUnemployment", -0.002, 1095), E("spending.unemployment_benefits", -0.02, 730)],
   "Un guichet unique pour l'emploi : chômage structurel −0,2 point à terme."),
 R("nuclear_program", "Programme de six nouveaux réacteurs EPR2", "Énergie",
   "Construction de six réacteurs sur des sites existants. Long, coûteux, structurant.", 0.02,
   [E(O+"rural", 0.01), E("economy.businessConfidence", 0.01)],
   [E("budget.oneOff", 60.0, 4380), E("energy.capacity.new_nuclear", 9900, 0, 4380), E("economy.output", 0.002, 4380)],
   "60 Md€ sur 12 ans ; +9,9 GW de production pilotable à terme."),
 R("renewables_plan", "Accélération des énergies renouvelables", "Énergie",
   "Simplifie les procédures et finance l'éolien en mer et le solaire.", 0.0,
   [E(O+"young", 0.02), E(O+"urban", 0.02), E(O+"rural", -0.01)],
   [E("budget.oneOff", 15.0, 1825), E("energy.capacity.new_renewables", 18000, 1825), E("quality.environment", 0.05, 1825)],
   "15 Md€ sur 5 ans ; +18 GW éolien et solaire, environnement amélioré."),
 R("carbon_tax", "Hausse de la taxe carbone", "Écologie",
   "Renchérit progressivement les énergies fossiles.", 0.05,
   [E(O+"rural", -0.06), E(O+"low_income", -0.03), E(O+"young", 0.01)],
   [E("revenue.energy_taxes", 20, 365), E("quality.environment", 0.04, 1095), E("economy.inflation", 0.002, 365)],
   "Recettes +, environnement + ; colère des ruraux."),
 R("wealth_tax", "Rétablissement de l'impôt sur la fortune", "Fiscalité",
   "Réintroduit un impôt sur l'ensemble du patrimoine des plus aisés.", 0.04,
   [E(O+"high_income", -0.07), E(O+"low_income", 0.03), E(O+"middle_income", 0.01), E("economy.businessConfidence", -0.02)],
   [E("revenue.property_taxes", 5, 180), E("economy.potentialGrowth", -0.0005, 1460)],
   "Recettes +, signal de justice fiscale ; capitaux plus frileux."),
 R("defense_programming", "Loi de programmation militaire renforcée", "Défense",
   "Hausse de 25 % du budget des armées sur cinq ans.", 0.0,
   [E(O+"seniors", 0.01)],
   [E("spending.defense", 0.25, 1825), E("military.readiness", 0.08, 1825), E("economy.output", 0.001, 1825)],
   "Armées +25 % sur 5 ans : disponibilité et stocks."),
 R("decentralization", "Nouvelle étape de décentralisation", "Territoires",
   "Transfère des compétences et des ressources aux régions et départements.", 0.03,
   [E(O+"rural", 0.02)],
   [E("spending.local_authorities", 0.04, 365), E("quality.transport", 0.02, 1095)],
   "Collectivités +4 % ; services de proximité améliorés."),
]
json.dump({"reforms": reforms, "voteDelayDays": 30}, open("reforms.json", "w"), ensure_ascii=False, indent=1)

P = [
 {"id": "unemployment_7", "label": "Ramener le chômage sous 7 %", "kind": "BELOW", "variable": "economy.unemployment", "threshold": 0.07, "groups": ["private_employees", "inactive", "young"]},
 {"id": "deficit_3", "label": "Ramener le déficit sous 3 % du PIB", "kind": "BELOW", "variable": "economy.deficitRatio", "threshold": 0.03, "groups": ["high_income", "self_employed", "seniors"]},
 {"id": "no_tax_increase", "label": "Ne pas augmenter les impôts des ménages", "kind": "NOT_ABOVE_START", "variable": "tax.households", "threshold": 0.002, "groups": ["middle_income", "high_income", "rural"]},
 {"id": "security", "label": "Améliorer nettement la sécurité", "kind": "ABOVE_START", "variable": "quality.security", "threshold": 0.04, "groups": ["seniors", "rural", "retirees"]},
 {"id": "health", "label": "Sauver l'hôpital", "kind": "ABOVE_START", "variable": "quality.health", "threshold": 0.04, "groups": ["seniors", "retirees", "civil_servants"]},
 {"id": "purchasing_power", "label": "Augmenter le pouvoir d'achat", "kind": "ABOVE_START", "variable": "economy.purchasingPower", "threshold": 0.02, "groups": ["low_income", "middle_income", "private_employees"]},
 {"id": "pension_reform", "label": "Réformer les retraites (âge légal)", "kind": "REFORM_ADOPTED", "reform": "pension_age_65", "groups": ["self_employed", "high_income"]},
 {"id": "no_pension_reform", "label": "Ne pas reculer l'âge de la retraite", "kind": "REFORM_NOT_ADOPTED", "reform": "pension_age_65", "groups": ["private_employees", "civil_servants"]},
 {"id": "nuclear", "label": "Relancer le nucléaire", "kind": "REFORM_ADOPTED", "reform": "nuclear_program", "groups": ["rural", "self_employed"]},
 {"id": "no_war", "label": "Ne pas engager la France dans une guerre", "kind": "NO_WAR", "groups": ["young", "low_income", "urban"]},
 {"id": "debt", "label": "Réduire la dette publique", "kind": "BELOW_START", "variable": "economy.debtRatio", "threshold": 0.0, "groups": ["high_income", "seniors"]},
 {"id": "environment", "label": "Accélérer la transition écologique", "kind": "ABOVE_START", "variable": "quality.environment", "threshold": 0.04, "groups": ["young", "urban"]},
]
json.dump({"promises": P, "maxPromises": 3, "keptBonus": 0.06, "brokenPenalty": 0.08}, open("promises.json", "w"), ensure_ascii=False, indent=1)
c = json.load(open("country.json"))
c["reforms"] = "countries/FRA/reforms.json"; c["promises"] = "countries/FRA/promises.json"
c["demography"] = {"birthRate": 0.0105, "deathRate": 0.0095, "netMigrationRate": 0.0025, "agingPerYear": 0.0025}
json.dump(c, open("country.json", "w"), ensure_ascii=False, indent=1)
print(len(reforms), "réformes", len(P), "promesses")
