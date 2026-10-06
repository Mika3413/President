"""Ampleur des événements -> assets/data/events/intensity.json.

Quatre niveaux tirés au hasard (le plus souvent limité, rarement exceptionnel) qui multiplient
effets et coûts. Titres adaptés au niveau pour les catastrophes et les crises ; les sommets,
nominations et demandes d'élus (dont les montants varient déjà) restent fixes.
"""
import glob, json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
LEVELS = [
    {"id": "limited", "label": "limitée", "factor": 0.5, "weight": 50},
    {"id": "significant", "label": "importante", "factor": 1.0, "weight": 32},
    {"id": "serious", "label": "grave", "factor": 1.7, "weight": 14},
    {"id": "exceptional", "label": "exceptionnelle", "factor": 2.8, "weight": 4},
]

events = {}
for f in glob.glob(os.path.join(ROOT, "events", "*.json")):
    if f.endswith(("intensity.json", "responses.json")):
        continue
    for e in json.load(open(f))["events"]:
        events[e["id"]] = os.path.basename(f)

# Les demandes d'élus locaux ont déjà un montant variable ; les sommets et votes sont des rendez-vous.
fixed = sorted(i for i, f in events.items() if f in ("summits.json", "local.json") and i not in ("cyclone", "flood_defense", "coastal_erosion"))
fixed += ["nobel_prize", "sports_victory", "investment_announcement", "tech_hq", "space_success", "winter_olympics",
          "ai_champion", "data_sovereignty", "group_split", "majority_rebels", "teen_screens", "nuclear_waste_site",
          "eu_budget", "eu_common_debt", "eu_defense", "eu_migration", "eu_sanctions_war", "minister_indicted",
          "minister_investigation", "minister_scandal", "president_scandal", "arms_contract", "mercosur_deal",
          "eu_deficit_procedure", "rating_downgrade", "pension_deficit"]
fixed = sorted(set(i for i in fixed if i in events))

H = {
 "disaster_abroad": ["Séisme {foreignIn} : dégâts limités", "Séisme meurtrier {foreignIn}", "Séisme dévastateur {foreignIn} : des milliers de victimes", "Catastrophe historique {foreignIn} : un séisme rase des villes entières"],
 "flood_abroad": ["Crues localisées {foreignIn}", "Inondations dévastatrices {foreignIn}", "Inondations meurtrières {foreignIn} : des régions sous les eaux", "Déluge historique {foreignIn} : des millions de sinistrés"],
 "wildfire_abroad": ["Feux de forêt {foreignIn}", "Mégafeux {foreignIn} : des villes évacuées", "Incendies géants {foreignIn} : des centaines de milliers d'hectares brûlés", "Brasier historique {foreignIn} : le pays en état de catastrophe"],
 "storm_abroad": ["Tempête {foreignIn} : dégâts matériels", "Ouragan dévastateur {foreignIn}", "Ouragan majeur {foreignIn} : le littoral ravagé", "Ouragan de catégorie 5 {foreignIn} : un désastre humanitaire"],
 "drought_abroad": ["Sécheresse {foreignIn} : restrictions d'eau", "Sécheresse et famine menacent {foreignThe}", "Famine {foreignIn} : des millions de personnes menacées", "Famine historique {foreignIn} : exode massif"],
 "storm": ["Coup de vent {departmentIn} : dégâts limités", "Tempête violente : dégâts {departmentIn}", "Tempête majeure {departmentIn} : des milliers de foyers sinistrés", "Tempête historique {departmentIn} : état de catastrophe naturelle"],
 "wildfire": ["Feu de forêt maîtrisé {departmentIn}", "Incendies de forêt {departmentIn}", "Mégafeu {departmentIn} : des villages évacués", "Incendie hors de contrôle {departmentIn} : des milliers d'hectares en cendres"],
 "flood": ["Crue {departmentIn} : quelques routes coupées", "Inondations {departmentIn}", "Inondations graves {departmentIn} : des quartiers sous les eaux", "Crue historique {departmentIn} : des milliers de sinistrés"],
 "cyclone": ["Tempête tropicale {departmentIn}", "Cyclone : {departmentThe} frappé de plein fouet", "Cyclone majeur : {departmentThe} dévasté", "Cyclone historique : {departmentThe} coupé du monde"],
 "heatwave": ["Fortes chaleurs dans le Sud", "Canicule sur une grande partie du pays", "Canicule extrême : alerte rouge dans vingt départements", "Canicule historique : des records absolus battus"],
 "cold_wave": ["Coup de froid sur le pays", "Vague de froid : le réseau électrique sous tension", "Grand froid : risque de délestages", "Froid historique : le réseau électrique au bord de la rupture"],
 "drought": ["Sécheresse : premières restrictions d'eau", "Sécheresse historique : restrictions d'eau", "Sécheresse sévère : des communes privées d'eau potable", "Sécheresse exceptionnelle : l'agriculture sinistrée"],
 "epidemic": ["Épidémie saisonnière plus forte que prévu", "Épidémie : les hôpitaux sous tension", "Épidémie grave : les hôpitaux débordés", "Épidémie massive : déprogrammations dans tout le pays"],
 "pandemic_wave": ["Épidémie : une petite vague", "Épidémie : une vague frappe le pays", "Épidémie : une vague violente submerge les hôpitaux", "Pandémie : la vague la plus forte depuis des décennies"],
 "blackout": ["Coupure d'électricité locale", "Coupures d'électricité dans plusieurs régions", "Panne géante : des millions de foyers privés de courant", "Black-out national : le pays dans le noir"],
 "terror_attack": ["Attaque déjouée de justesse à {city}", "Attentat à {city}", "Attentat meurtrier à {city}", "Attentat de masse à {city} : le pays sous le choc"],
 "urban_riots": ["Tensions et incidents à {city}", "Nuits d'émeutes à {city} et dans plusieurs villes", "Émeutes graves : des dizaines de villes touchées", "Émeutes généralisées : le pays s'embrase"],
 "cyberattack": ["Cyberattaque contre une administration", "Cyberattaque contre des services publics", "Cyberattaque grave : hôpitaux et préfectures paralysés", "Cyberattaque massive : l'État numérique à l'arrêt"],
 "hybrid_cyber": ["Cyberattaque ciblée : nos services soupçonnent {foreignThe}", "Cyberattaque massive : nos services soupçonnent {foreignThe}", "Cyberattaque d'ampleur nationale attribuée {foreignTo}", "Cyberattaque sans précédent : {foreignThe} mis en cause"],
 "hybrid_sabotage": ["Sabotage d'un câble : {foreignThe} soupçonné", "Sabotage : câbles et voies ferrées coupés, {foreignThe} soupçonné", "Vague de sabotages coordonnés : {foreignThe} mis en cause", "Sabotages massifs : le pays partiellement paralysé, {foreignThe} accusé"],
 "nuclear_incident": ["Arrêt préventif {infrastructureOf}", "Arrêt {infrastructureOf} après un incident technique", "Incident sérieux {infrastructureAt} : arrêt prolongé", "Incident grave {infrastructureAt} : alerte de l'Autorité de sûreté"],
 "refinery_accident": ["Incident {infrastructureAt}", "Accident industriel {infrastructureAt}", "Explosion {infrastructureAt}", "Catastrophe industrielle {infrastructureAt}"],
 "food_scandal": ["Rappel de produits par précaution", "Scandale alimentaire : des produits contaminés retirés de la vente", "Scandale alimentaire : des dizaines d'intoxications", "Scandale sanitaire majeur : des victimes dans tout le pays"],
 "bird_flu": ["Grippe aviaire : un foyer détecté", "Grippe aviaire : des millions de volailles abattues", "Grippe aviaire : toute une filière à l'arrêt", "Grippe aviaire : l'épizootie la plus grave de l'histoire"],
 "drug_shortage": ["Tensions sur quelques médicaments", "Pénurie de médicaments : antibiotiques et anticancéreux introuvables", "Pénurie grave : des traitements vitaux manquent", "Pénurie massive : les pharmacies vides"],
 "city_demonstration": ["Rassemblement à {city}", "Importante manifestation à {city}", "Manifestation massive à {city}", "Marée humaine à {city}"],
 "fuel_shortage": ["Tensions dans quelques stations-service", "Pénurie de carburant dans les stations-service", "Pénurie grave : une station sur deux à sec", "Pénurie générale : le pays à court de carburant"],
 "gas_price_spike": ["Le prix du gaz grimpe", "Le prix du gaz double en un mois", "Le prix du gaz triple : les industriels arrêtent leurs usines", "Choc gazier historique : le prix multiplié par cinq"],
 "health_data_leak": ["Fuite de données dans un hôpital", "Fuite massive : les données de santé de 20 millions de Français en ligne", "Fuite géante : les dossiers médicaux de 40 millions de Français exposés", "Fuite totale : les données de santé de tous les Français en vente"],
 "student_protest": ["Rassemblements étudiants contre la précarité", "Les étudiants dans la rue contre la précarité", "Mobilisation étudiante massive", "La jeunesse étudiante paralyse le pays"],
}
def e(target, amount, days=0):
    d = {"target": target, "amount": amount}
    if days:
        d["days"] = days
    return d


G = "opinion.group."
# Conséquences pour une ampleur « importante » (multipliées par 0,5 à 2,8 selon l'ampleur tirée).
C = {
 "storm": [e("economy.output", -0.0004, 30), e("budget.oneOff", 0.05), e("region.approval", -0.005), e("economy.consumerConfidence", -0.002)],
 "wildfire": [e("scope.pollution", 0.02), e("budget.oneOff", 0.03), e("economy.output", -0.0002, 60), e(G + "rural", -0.003)],
 "flood": [e("budget.oneOff", 0.08), e("scope.healthAccess", -0.01), e("scope.unemployment", 0.0005, 90), e("economy.output", -0.0004, 60), e("quality.transport", -0.003)],
 "cyclone": [e("budget.oneOff", 0.1), e("scope.healthAccess", -0.03), e("scope.unemployment", 0.002, 120), e("scope.approval", -0.02), e("economy.output", -0.0002, 60)],
 "heatwave": [e("economy.output", -0.0003, 30), e(G + "seniors", -0.004), e("quality.agriculture", -0.005), e("quality.environment", -0.003)],
 "cold_wave": [e("economy.output", -0.0004, 30), e("economy.inflation", 0.0005, 60), e(G + "low_income", -0.005), e("quality.health", -0.005)],
 "drought": [e("economy.consumerConfidence", -0.002), e(G + "rural", -0.003)],
 "epidemic": [e("economy.consumerConfidence", -0.005), e(G + "seniors", -0.004), e("budget.oneOff", 0.2)],
 "pandemic_wave": [e("budget.oneOff", 1.0), e("economy.consumerConfidence", -0.01), e("economy.unemployment", 0.001, 120)],
 "blackout": [e("economy.businessConfidence", -0.005), e("quality.health", -0.003)],
 "fuel_shortage": [e("economy.consumerConfidence", -0.005), e("quality.transport", -0.005)],
 "terror_attack": [e("quality.security", -0.01), e("economy.output", -0.0003, 60), e("alliance.EU.CRISIS_SOLIDARITY", 0.01)],
 "urban_riots": [e("quality.security", -0.01), e("budget.oneOff", 0.2), e(G + "seniors", -0.005), e(G + "young", -0.004)],
 "cyberattack": [e("quality.security", -0.005), e("economy.output", -0.0003, 30)],
 "food_scandal": [e("economy.consumerConfidence", -0.005), e("quality.agriculture", -0.01), e("opinion.national", -0.003)],
 "bird_flu": [e("quality.agriculture", -0.01), e(G + "rural", -0.005), e("economy.inflation", 0.0003, 90)],
 "drug_shortage": [e("quality.health", -0.008), e(G + "seniors", -0.005)],
 "gas_price_spike": [e("economy.inflation", 0.003, 90), e("economy.consumerConfidence", -0.008), e("economy.businessConfidence", -0.01), e("economy.output", -0.0008, 120)],
 "health_data_leak": [e("quality.health", -0.005), e("opinion.national", -0.003), e("economy.consumerConfidence", -0.003)],
 "student_protest": [e(G + "young", -0.005), e("quality.education", -0.003)],
 "city_demonstration": [e("economy.output", -0.0001, 7)],
 "nuclear_incident": [e("economy.businessConfidence", -0.003), e("opinion.national", -0.002), e("memory.DEU.DISAGREEMENT", -0.005), e("memory.CHE.DISAGREEMENT", -0.005)],
 "refinery_accident": [e("scope.pollution", 0.05), e("economy.inflation", 0.0005, 60)],
 "diplomatic_incident": [e("abroad.partners.DISAGREEMENT", -0.005)],
 "cyber_espionage": [e("economy.businessConfidence", -0.003)],
 "trade_tariffs": [e("abroad.trade", -0.002, 120), e("economy.businessConfidence", -0.005)],
 "fishing_dispute": [e(G + "rural", -0.003)],
}
missing = [i for i in list(H) + list(C) if i not in events]
assert not missing, missing
for i, l in H.items():
    assert len(l) == len(LEVELS), i

out = {"_doc": "Ampleur des événements. Généré par tools/datagen/event_intensity_fr.py.", "levels": LEVELS, "fixed": fixed, "headlines": H, "consequences": C}
path = os.path.join(ROOT, "events", "intensity.json")
json.dump(out, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(len(C), "types d'événements avec conséquences en chaîne")
print(len(events), "événements :", len(events) - len(fixed), "à ampleur variable,", len(fixed), "fixes,", len(H), "avec titres par niveau")
