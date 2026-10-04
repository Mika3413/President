"""Options de réponse supplémentaires par famille d'événements -> assets/data/events/responses.json.

Un président ne choisit pas entre deux options : devant un incendie, il peut aussi aller sur place,
réquisitionner, demander l'aide européenne, évacuer... Chaque paquet ajoute des options aux
événements qu'il cite (identifiants « x_... ») et propose des mesures de crise cumulables
(confinement, plan ORSEC, couvre-feu...) définies dans countries/FRA/measures.json.
Les effets des options suivent l'ampleur de l'événement (limitée, grave, exceptionnelle...).
"""
import glob, json, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dialogue_fr_lib import V, letter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
G = "opinion.group."


def e(target, amount, days=0, delay=0):
    d = {"target": target, "amount": amount}
    if days:
        d["days"] = days
    if delay:
        d["delayDays"] = delay
    return d


def o(id, label, hint, effects, outcome="NEUTRAL"):
    return {"id": "x_" + id, "label": label, "hint": hint, "effects": effects, "outcome": outcome}


PACKS = []


def pack(id, label, events, options, measures=()):
    PACKS.append({"id": id, "label": label, "events": events, "options": options, "measures": list(measures)})


# --- Catastrophes sur le territoire -------------------------------------------------------------
LOCAL_DISASTERS = ["wildfire", "flood", "storm", "cyclone"]
pack("disaster_local", "Catastrophe naturelle", LOCAL_DISASTERS, [
    o("visit", "Me rendre sur place dès ce soir", "Coût : 2 M€ ; présence saluée, risque de huées",
      [e("budget.oneOff", 0.002), e("scope.approval", 0.03, 15), e("opinion.national", 0.004)], "ACCEPTED"),
    o("army", "Déployer l'armée en renfort (opération Résilience)", "Coût : 40 M€ ; soldats mobilisés",
      [e("budget.oneOff", 0.04), e("scope.approval", 0.03, 20), e("military.readiness", -0.01)], "ACCEPTED"),
    o("eu", "Activer le mécanisme européen de protection civile", "Renforts étrangers ; aveu de faiblesse",
      [e("scope.approval", 0.02, 15), e("alliance.EU.NEGOTIATION_GOODWILL", 0.005), e("opinion.national", -0.002)], "PARTIAL"),
    o("insurers", "Exiger des assureurs une indemnisation sous un mois", "Sinistrés soulagés, assureurs furieux",
      [e(G + "middle_income", 0.01), e("scope.approval", 0.015), e("economy.businessConfidence", -0.003)]),
    o("rebuild", "Plan de reconstruction et d'adaptation sur cinq ans", "Coût : 600 M€ étalés ; territoire mieux protégé",
      [e("budget.oneOff", 0.6, 365), e("scope.approval", 0.04, 30), e("economy.output", 0.0002, 180), e("quality.environment", 0.004)], "ACCEPTED"),
    o("inquiry", "Mission d'inspection sur les responsabilités", "Rassure l'opinion, inquiète les élus locaux",
      [e("opinion.national", 0.003), e("sender.relation", -0.03)]),
], ["orsec", "evacuation", "army_relief", "disaster_fund"])

pack("fire", "Feux", ["wildfire", "forest_fire_prevention"], [
    o("canadair_buy", "Commander de nouveaux Canadair", "Coût : 400 M€ ; livrés dans deux ans",
      [e("budget.oneOff", 0.4, 365), e("quality.environment", 0.006, 0, 365), e(G + "rural", 0.01)], "ACCEPTED"),
    o("arsonists", "Traquer les incendiaires : enquêteurs et peines alourdies", "Coût : 10 M€ ; message de fermeté",
      [e("budget.oneOff", 0.01), e("quality.security", 0.003), e(G + "seniors", 0.008)]),
    o("volunteers", "Grande campagne pour les pompiers volontaires", "Coût : 50 M€ ; plus de bras l'an prochain",
      [e("budget.oneOff", 0.05), e(G + "rural", 0.01), e("quality.environment", 0.003, 0, 180)]),
], ["canadair", "forest_ban", "fire_prevention"])

pack("water", "Crues et tempêtes", ["flood", "storm", "cyclone", "flood_defense", "coastal_erosion"], [
    o("no_build", "Interdire toute construction en zone inondable", "Moins de victimes demain ; élus et promoteurs fâchés",
      [e("quality.environment", 0.005), e("sender.relation", -0.04), e("economy.businessConfidence", -0.002)]),
    o("levees", "Programme national de digues", "Coût : 800 M€ étalés sur deux ans",
      [e("budget.oneOff", 0.8, 730), e("scope.approval", 0.02, 30), e("quality.environment", 0.004, 0, 365)], "ACCEPTED"),
], ["flood_watch", "evacuation"])

pack("heat", "Chaleur et eau", ["heatwave", "overseas_water", "water_crisis", "ski_no_snow"], [
    o("cooling", "Ouvrir salles rafraîchies et visites aux personnes âgées", "Coût : 30 M€ ; vies sauvées",
      [e("budget.oneOff", 0.03), e("quality.health", 0.004, 30), e(G + "seniors", 0.015)], "ACCEPTED"),
    o("work_hours", "Adapter les horaires de travail et l'école", "Activité un peu freinée, salariés soulagés",
      [e("economy.output", -0.0002, 10), e(G + "private_employees", 0.008), e(G + "young", 0.005)]),
    o("farm_water", "Aide d'urgence aux agriculteurs et retenues d'eau", "Coût : 150 M€ ; écologistes opposés",
      [e("budget.oneOff", 0.15), e("quality.agriculture", 0.006), e(G + "rural", 0.012), e("quality.environment", -0.003)]),
], ["heat_plan", "water_restrictions"])

pack("cold", "Froid", ["cold_wave"], [
    o("homeless", "Ouvrir gymnases et casernes aux sans-abri", "Coût : 20 M€",
      [e("budget.oneOff", 0.02), e("quality.social", 0.004), e(G + "low_income", 0.01)], "ACCEPTED"),
    o("energy_cheque", "Chèque énergie exceptionnel", "Coût : 600 M€",
      [e("budget.oneOff", 0.6), e(G + "low_income", 0.02), e(G + "retirees", 0.01)], "ACCEPTED"),
], ["cold_plan", "load_shedding", "sobriety"])

# --- Santé ----------------------------------------------------------------------------------------
HEALTH = ["epidemic", "pandemic_wave", "new_virus", "bird_flu", "drug_shortage", "food_scandal"]
pack("health", "Santé publique", HEALTH, [
    o("council", "Réunir un conseil scientifique et suivre ses avis", "Décisions mieux acceptées",
      [e("opinion.national", 0.003), e("quality.health", 0.003, 30)]),
    o("address", "Allocution solennelle à la nation", "Rassemble si les chiffres suivent",
      [e("opinion.national", 0.006), e(G + "seniors", 0.01)]),
    o("eu_buy", "Achats groupés européens (vaccins, médicaments)", "Coût : 500 M€ ; plus lent, moins cher",
      [e("budget.oneOff", 0.5, 60), e("quality.health", 0.006, 60, 15), e("alliance.EU.NEGOTIATION_GOODWILL", 0.008)], "ACCEPTED"),
    o("caregivers", "Prime exceptionnelle aux soignants", "Coût : 800 M€",
      [e("budget.oneOff", 0.8), e(G + "civil_servants", 0.02), e("quality.health", 0.005, 60)], "ACCEPTED"),
    o("testing", "Dépistage gratuit et massif", "Coût : 1 Md€ ; foyers repérés plus tôt",
      [e("budget.oneOff", 1.0, 45), e("quality.health", 0.008, 45)], "ACCEPTED"),
    o("reserve", "Mobiliser la réserve sanitaire et les étudiants en santé", "Coût : 50 M€ ; examens reportés",
      [e("budget.oneOff", 0.05), e("quality.health", 0.004, 30), e(G + "young", -0.004)]),
    o("relocate", "Relocaliser la production de médicaments essentiels", "Coût : 1,2 Md€ sur deux ans",
      [e("budget.oneOff", 1.2, 730), e("economy.output", 0.0002, 365), e("quality.health", 0.005, 0, 365)], "ACCEPTED"),
], ["masks", "curfew", "local_lockdown", "lockdown", "white_plan", "health_borders", "health_emergency", "vaccination", "strategic_stocks", "short_time_work", "business_support"])

# --- Sécurité -------------------------------------------------------------------------------------
pack("terror", "Terrorisme", ["terror_attack", "corsica_tensions", "prison_escape", "embassy_attack"], [
    o("defense_council", "Conseil de défense immédiat à l'Élysée", "Montre que l'État tient la barre",
      [e("opinion.national", 0.004), e("quality.security", 0.004, 30)]),
    o("visit", "Me rendre sur les lieux et au chevet des blessés", "Coût : 1 M€ ; geste attendu",
      [e("budget.oneOff", 0.001), e("scope.approval", 0.03), e("opinion.national", 0.005)], "ACCEPTED"),
    o("victims", "Fonds d'indemnisation et cellule d'aide aux victimes", "Coût : 80 M€",
      [e("budget.oneOff", 0.08), e("scope.approval", 0.02), e(G + "seniors", 0.005)], "ACCEPTED"),
    o("strike", "Frapper les commanditaires à l'étranger", "Armées sollicitées ; risque d'escalade",
      [e("military.readiness", -0.02), e("quality.security", 0.008, 90), e("opinion.national", 0.006), e(G + "young", -0.006)]),
    o("law", "Nouvelle loi antiterroriste", "Sécurité accrue ; libertés en débat",
      [e("quality.security", 0.01, 0, 60), e("government.parliamentSupport", -0.01), e(G + "young", -0.008), e(G + "seniors", 0.01)]),
    o("eu_intel", "Proposer un parquet et un fichier européens", "Partenaires sollicités ; effet lent",
      [e("alliance.EU.NEGOTIATION_GOODWILL", 0.008), e("quality.security", 0.005, 0, 180)]),
], ["vigipirate", "sentinelle", "gathering_ban", "security_borders", "local_curfew"])

UNREST = ["urban_riots", "police_incident", "fuel_protest_hardens", "city_demonstration", "stadium_violence",
          "campus_occupation", "local_violence_school"]
pack("unrest", "Violences et désordres", UNREST, [
    o("mayors", "Réunir les maires des villes touchées", "Écoute ; les élus se sentent soutenus",
      [e("sender.relation", 0.04), e("scope.approval", 0.015), e("opinion.national", 0.002)]),
    o("gendarmerie", "Envoyer gendarmes mobiles et blindés", "Coût : 15 M€ ; ordre rétabli, images dures",
      [e("budget.oneOff", 0.015), e("quality.security", 0.008, 20), e(G + "seniors", 0.012), e(G + "young", -0.015)]),
    o("fast_justice", "Comparutions immédiates et peines rapides", "Fermeté ; magistrats débordés",
      [e("quality.security", 0.005, 30), e("quality.justice", -0.004), e(G + "seniors", 0.008)]),
    o("families", "Recevoir les familles et les associations", "Apaise ; vos soutiens y voient de la faiblesse",
      [e(G + "young", 0.012), e(G + "urban", 0.006), e(G + "seniors", -0.006)]),
    o("neighbourhoods", "Plan pour les quartiers : emploi, écoles, transports", "Coût : 1,5 Md€ sur deux ans",
      [e("budget.oneOff", 1.5, 730), e("scope.approval", 0.03, 60), e(G + "low_income", 0.015), e("quality.social", 0.006, 0, 120)], "ACCEPTED"),
    o("parents", "Sanctionner les parents (allocations suspendues)", "Populaire à droite, contesté",
      [e(G + "seniors", 0.01), e(G + "low_income", -0.012), e("government.parliamentSupport", -0.004)]),
], ["local_curfew", "gathering_ban", "community_policing", "mediator"])

# --- Conflits sociaux -----------------------------------------------------------------------------
SOCIAL = ["national_strike", "rail_strike", "teachers_strike", "doctor_strike", "strike_spreads", "farmers_protest",
          "fuel_tax_protest", "delivery_strike", "student_protest", "youth_movement", "cost_of_living_overseas", "court_backlog"]
pack("social", "Conflits sociaux", SOCIAL, [
    o("tv", "M'exprimer au journal de 20 heures", "Reprend la main ; ou ravive la colère",
      [e("opinion.national", 0.005), e(G + "private_employees", -0.003)]),
    o("pm", "Laisser le Premier ministre mener la négociation", "Vous protège ; il s'use",
      [e("opinion.national", 0.002), e("government.parliamentSupport", -0.004)]),
    o("mediator", "Nommer un médiateur indépendant", "Coût : 1 M€ ; temps gagné",
      [e("budget.oneOff", 0.001), e("opinion.national", 0.002), e("economy.output", -0.0002, 10)], "PARTIAL"),
    o("conference", "Grande conférence sociale avec syndicats et patronat", "Dialogue ; le patronat se méfie",
      [e(G + "private_employees", 0.01), e(G + "civil_servants", 0.01), e("economy.businessConfidence", -0.003)], "PARTIAL"),
    o("minimum_service", "Imposer un service minimum", "Usagers soulagés, syndicats furieux",
      [e(G + "seniors", 0.01), e(G + "self_employed", 0.008), e(G + "civil_servants", -0.015), e("economy.output", 0.0002, 10)]),
    o("referendum_idea", "Proposer une consultation citoyenne", "Coût : 20 M€ ; sort du face-à-face",
      [e("budget.oneOff", 0.02), e("opinion.national", 0.003), e("government.parliamentSupport", -0.005)]),
], ["social_conference", "mediator", "requisition", "gathering_ban", "short_time_work"])

# --- Énergie --------------------------------------------------------------------------------------
ENERGY = ["blackout", "fuel_shortage", "fuel_blockade", "gas_price_spike", "nuclear_incident", "cold_wave"]
pack("energy", "Énergie", ENERGY, [
    o("sobriety_call", "Appel national à la sobriété", "Gratuit ; demande −5 % suivie à moitié",
      [e("opinion.national", 0.002), e("economy.output", -0.0001, 15)]),
    o("eu_grid", "Demander la solidarité énergétique européenne", "Importations garanties ; dette politique",
      [e("alliance.EU.NEGOTIATION_GOODWILL", -0.004), e("economy.output", 0.0003, 20), e("budget.oneOff", 0.2)], "PARTIAL"),
    o("reactors", "Accélérer le redémarrage des réacteurs", "Coût : 300 M€ ; l'Autorité de sûreté s'inquiète",
      [e("budget.oneOff", 0.3), e("economy.output", 0.0004, 30), e("quality.environment", 0.002)], "ACCEPTED"),
    o("coal", "Rouvrir une centrale à charbon pour l'hiver", "Coût : 100 M€ ; émissions et colère écologiste",
      [e("budget.oneOff", 0.1), e("economy.output", 0.0003, 60), e("quality.environment", -0.008), e(G + "young", -0.008)]),
    o("price_cap", "Bloquer les prix à la pompe et sur les factures", "Coût : 3 Md€ ; très populaire",
      [e("budget.oneOff", 3.0, 90), e(G + "low_income", 0.02), e(G + "middle_income", 0.015), e("economy.inflation", -0.002)], "ACCEPTED"),
], ["load_shedding", "sobriety", "fuel_rationing", "strategic_stocks", "requisition"])

# --- Cyber et menaces hybrides --------------------------------------------------------------------
CYBER = ["cyberattack", "health_data_leak", "hybrid_cyber", "cyber_espionage", "data_lawsuit", "defense_leak",
         "deepfake_president", "hybrid_sabotage"]
pack("cyber", "Cyber et hybride", CYBER, [
    o("anssi", "Mobiliser l'ANSSI et les meilleurs experts privés", "Coût : 60 M€ ; services rétablis plus vite",
      [e("budget.oneOff", 0.06), e("quality.security", 0.006, 60), e("economy.output", 0.0002, 15)], "ACCEPTED"),
    o("transparency", "Tout dire aux Français, chiffres à l'appui", "Confiance préservée, inquiétude immédiate",
      [e("opinion.national", 0.003), e("economy.consumerConfidence", -0.003)]),
    o("cyber_command", "Doubler les moyens du Commandement cyber", "Coût : 500 M€ sur un an",
      [e("budget.oneOff", 0.5, 365), e("quality.security", 0.01, 0, 120), e("military.readiness", 0.005)], "ACCEPTED"),
    o("eu_attribution", "Attribution publique conjointe avec nos alliés", "L'agresseur est désigné ; il peut répliquer",
      [e("alliance.EU.NEGOTIATION_GOODWILL", 0.006), e("alliance.NATO.NEGOTIATION_GOODWILL", 0.004), e("opinion.national", 0.002)]),
    o("platforms", "Convoquer les plateformes et exiger des retraits", "Désinformation freinée ; Washington agacé",
      [e("opinion.national", 0.002), e("memory.USA.DISAGREEMENT", -0.01)]),
], ["cyber_shield", "vigipirate"])

# --- Accidents industriels ------------------------------------------------------------------------
pack("industrial", "Accident industriel", ["refinery_accident", "nuclear_incident"], [
    o("visit", "Me rendre sur le site avec les experts", "Coût : 1 M€",
      [e("budget.oneOff", 0.001), e("scope.approval", 0.025), e("opinion.national", 0.003)], "ACCEPTED"),
    o("health_check", "Suivi médical gratuit des riverains", "Coût : 40 M€",
      [e("budget.oneOff", 0.04), e("scope.approval", 0.03), e("scope.healthAccess", 0.02)], "ACCEPTED"),
    o("polluter_pays", "Faire payer l'exploitant, jusqu'au bout", "Exploitant poursuivi ; industrie inquiète",
      [e("scope.approval", 0.02), e("economy.businessConfidence", -0.004), e("quality.environment", 0.004)]),
    o("audit", "Audit de tous les sites à risque du pays", "Coût : 120 M€ ; d'autres accidents évités",
      [e("budget.oneOff", 0.12, 180), e("quality.environment", 0.005, 0, 120), e("quality.security", 0.003)], "ACCEPTED"),
], ["orsec", "evacuation", "local_lockdown"])

# --- Crises économiques ---------------------------------------------------------------------------
ECON = ["bank_fragility", "bank_run", "bankruptcy", "factory_closure", "ai_layoffs", "wine_crisis", "farmers_local",
        "strategic_acquisition", "trade_tariffs"]
pack("economy", "Crise économique", ECON, [
    o("bpi", "Entrée de Bpifrance au capital", "Coût : 500 M€ ; l'État actionnaire",
      [e("budget.oneOff", 0.5), e("economy.businessConfidence", 0.004), e(G + "private_employees", 0.008)], "ACCEPTED"),
    o("buyer", "Chercher un repreneur, Bercy en première ligne", "Lent ; peut sauver l'essentiel",
      [e("economy.businessConfidence", 0.002), e("scope.approval", 0.01, 30, 20)], "PARTIAL"),
    o("eu_fund", "Mobiliser le fonds européen d'ajustement", "Bruxelles paie une partie de la facture",
      [e("budget.oneOff", -0.05), e("alliance.EU.NEGOTIATION_GOODWILL", 0.004), e("scope.approval", 0.01)], "PARTIAL"),
    o("reskill", "Cellule de reclassement et formation des salariés", "Coût : 80 M€",
      [e("budget.oneOff", 0.08), e("scope.unemployment", -0.001, 0, 90), e(G + "private_employees", 0.008)], "ACCEPTED"),
    o("ceo", "Convoquer les dirigeants à l'Élysée", "Pression publique ; les investisseurs observent",
      [e("opinion.national", 0.003), e("economy.businessConfidence", -0.003)]),
], ["short_time_work", "business_support"])

# --- Catastrophes à l'étranger --------------------------------------------------------------------
ABROAD = ["disaster_abroad", "flood_abroad", "wildfire_abroad", "storm_abroad", "drought_abroad"]
pack("abroad_disaster", "Catastrophe à l'étranger", ABROAD, [
    o("rescuers", "Envoyer la Sécurité civile : sauveteurs et chiens", "Coût : 20 M€ ; geste très visible",
      [e("budget.oneOff", 0.02), e("country.CRISIS_SOLIDARITY", 0.04), e("abroad.approval", 0.005), e("opinion.national", 0.002)], "ACCEPTED"),
    o("navy", "Envoyer un porte-hélicoptères et un hôpital de campagne", "Coût : 60 M€ ; marine mobilisée",
      [e("budget.oneOff", 0.06), e("country.CRISIS_SOLIDARITY", 0.06), e("abroad.output", 0.002, 90), e("military.readiness", -0.008)], "ACCEPTED"),
    o("eu_coord", "Coordonner une réponse européenne", "Moins cher ; mérite partagé",
      [e("budget.oneOff", 0.02), e("country.CRISIS_SOLIDARITY", 0.03), e("alliance.EU.NEGOTIATION_GOODWILL", 0.006)], "PARTIAL"),
    o("nationals", "Cellule de crise et rapatriement des Français", "Coût : 15 M€ ; familles rassurées",
      [e("budget.oneOff", 0.015), e("opinion.national", 0.004)], "PARTIAL"),
    o("ngo", "Doubler chaque euro donné aux ONG", "Coût : 30 M€ ; élan de générosité",
      [e("budget.oneOff", 0.03), e("country.CRISIS_SOLIDARITY", 0.025), e("opinion.national", 0.002)], "ACCEPTED"),
    o("debt", "Suspendre le remboursement de sa dette envers la France", "Coût : 100 M€ ; reconnaissance durable",
      [e("budget.oneOff", 0.1), e("country.CRISIS_SOLIDARITY", 0.07), e("country.NEGOTIATION_GOODWILL", 0.03), e("abroad.output", 0.002, 180)], "ACCEPTED"),
    o("visit", "Me rendre sur place", "Coût : 2 M€ ; image forte, sécurité délicate",
      [e("budget.oneOff", 0.002), e("country.CRISIS_SOLIDARITY", 0.03), e("abroad.approval", 0.006), e("opinion.national", 0.003)], "ACCEPTED"),
])

# --- Crises diplomatiques -------------------------------------------------------------------------
DIPLO = ["diplomatic_incident", "embassy_attack", "hostages_abroad", "foreign_interference", "fishing_dispute",
         "trade_tariffs", "cyber_espionage", "hybrid_sabotage", "hybrid_cyber", "strategic_acquisition", "arms_contract"]
pack("diplomacy", "Crise diplomatique", DIPLO, [
    o("call", "Appeler directement son dirigeant", "Désamorce, ou montre notre faiblesse",
      [e("country.TALK_CORDIAL", 0.03), e("opinion.national", -0.001)], "PARTIAL"),
    o("ambassador", "Rappeler notre ambassadeur pour consultations", "Signal fort ; canal coupé",
      [e("country.TALK_TENSE", -0.04), e("opinion.national", 0.003)], "REFUSED"),
    o("eu_front", "Obtenir une position européenne commune", "Plus de poids ; il faut convaincre Berlin",
      [e("alliance.EU.NEGOTIATION_GOODWILL", 0.005), e("country.DISAGREEMENT", -0.02)]),
    o("un", "Saisir le Conseil de sécurité de l'ONU", "Tribune mondiale ; résultat incertain",
      [e("country.DISAGREEMENT", -0.03), e("opinion.national", 0.002)]),
    o("mediation", "Proposer une médiation d'un pays tiers", "Lent, mais sauve la face des deux côtés",
      [e("country.NEGOTIATION_GOODWILL", 0.03), e("opinion.national", -0.001)], "PARTIAL"),
    o("sanction", "Sanctions économiques ciblées", "Il en paiera le prix ; nos exportateurs aussi",
      [e("operation.sanction", 1), e("economy.businessConfidence", -0.002), e("opinion.national", 0.002)], "REFUSED"),
])

# --- Affaires et scandales ------------------------------------------------------------------------
SCANDAL = ["minister_scandal", "minister_indicted", "minister_investigation", "president_scandal", "tax_leak", "child_protection", "ehpad_scandal"]
pack("scandal", "Affaires", SCANDAL, [
    o("press", "Conférence de presse : répondre à toutes les questions", "Risqué mais courageux",
      [e("opinion.national", 0.004), e("president.popularity", 0.005)]),
    o("ethics_law", "Loi de moralisation de la vie publique", "Répond à la défiance ; la majorité grince",
      [e("opinion.national", 0.005), e("government.parliamentSupport", -0.008), e("quality.justice", 0.003)]),
    o("independent", "Confier l'enquête à une autorité indépendante", "Vous met à distance",
      [e("opinion.national", 0.003), e("quality.justice", 0.002)], "PARTIAL"),
], [])

# --- Terrain : demandes des élus -----------------------------------------------------------------
TERRAIN = ["hospital_overload", "hospital_maternity", "medical_desert", "migrant_reception", "police_reinforcement",
           "prison_overcrowding", "refugee_crisis"]
pack("terrain", "Demande de terrain", TERRAIN, [
    o("visit", "Aller sur place écouter les personnels", "Coût : 1 M€ ; élus ravis",
      [e("budget.oneOff", 0.001), e("scope.approval", 0.025), e("sender.relation", 0.04)], "ACCEPTED"),
    o("prefect", "Mandater le préfet pour une solution en un mois", "Rien à payer maintenant ; attente",
      [e("scope.approval", 0.008, 30, 15), e("sender.relation", 0.01)], "PARTIAL"),
    o("experiment", "En faire un territoire d'expérimentation", "Coût : 30 M€ ; innovation locale",
      [e("budget.oneOff", 0.03), e("scope.approval", 0.02), e("sender.relation", 0.03)], "ACCEPTED"),
], ["mediator", "community_policing", "white_plan"])

# --- Sommets et négociations internationales ----------------------------------------------------
SUMMITS = ["eu_budget", "eu_migration", "eu_defense", "eu_sanctions_war", "eu_common_debt", "g7_tax", "g7_africa",
           "nato_spending", "nato_war_support", "un_resolution", "un_nuclear", "cop_climate", "mercosur_deal", "eu_deficit_procedure"]
pack("summit", "Sommet", SUMMITS, [
    o("berlin", "Préparer une position commune avec Berlin", "Le couple franco-allemand pèse double",
      [e("memory.DEU.NEGOTIATION_GOODWILL", 0.03), e("alliance.EU.NEGOTIATION_GOODWILL", 0.004)], "PARTIAL"),
    o("south", "Former une coalition avec Rome et Madrid", "L'Europe du Sud derrière nous ; le Nord se braque",
      [e("memory.ITA.NEGOTIATION_GOODWILL", 0.03), e("memory.ESP.NEGOTIATION_GOODWILL", 0.03), e("memory.NLD.DISAGREEMENT", -0.02)], "PARTIAL"),
    o("parliament", "Faire voter la position française au Parlement", "Mandat clair ; marge de négociation réduite",
      [e("government.parliamentSupport", 0.006), e("opinion.national", 0.002)]),
    o("veto", "Menacer d'un veto", "Fermeté saluée en France ; partenaires agacés",
      [e("opinion.national", 0.004), e("alliance.EU.DISAGREEMENT", -0.01)], "REFUSED"),
])

# --- Demandes locales : chantiers et services ---------------------------------------------------
LOCAL = ["school_renovation", "housing_shortage", "rail_line", "broadband", "sports_facility", "heritage_restoration",
         "industrial_zone", "post_office_closure", "bridge_repair", "tourism_fund", "university_campus", "energy_bills_local",
         "public_transport_strike_local", "cultural_center", "road_safety", "neighborhood_renewal", "airport_noise",
         "illegal_gold_mining", "city_transport_request"]
pack("local", "Demande locale", LOCAL, [
    o("visit", "Venir sur place annoncer moi-même la décision", "Coût : 1 M€ ; la presse régionale en parle",
      [e("budget.oneOff", 0.001), e("scope.approval", 0.02), e("sender.relation", 0.03)], "ACCEPTED"),
    o("contract", "Contrat pluriannuel État-collectivité", "Coût : 120 M€ sur trois ans ; partenaires engagés",
      [e("budget.oneOff", 0.12, 1095), e("scope.approval", 0.025, 60), e("sender.relation", 0.05)], "ACCEPTED"),
    o("bank", "Prêt de la Banque des territoires", "Rien pour l'État ; la collectivité s'endette",
      [e("scope.approval", 0.008), e("sender.relation", -0.01)], "PARTIAL"),
])

# --- Réformes et débats de société --------------------------------------------------------------
POLICY = ["pension_deficit", "teacher_shortage", "teen_screens", "nuclear_waste_site", "ai_exodus", "data_sovereignty",
          "winter_olympics", "tech_hq", "ai_champion", "nobel_prize", "space_success", "space_failure", "military_accident"]
pack("policy", "Débat national", POLICY, [
    o("citizens", "Confier la question à une convention citoyenne", "Coût : 10 M€ ; décision plus légitime, plus lente",
      [e("budget.oneOff", 0.01), e("opinion.national", 0.003), e("government.parliamentSupport", -0.003)], "PARTIAL"),
    o("tv", "Trancher et l'expliquer à la télévision", "Autorité affirmée",
      [e("opinion.national", 0.003), e("president.popularity", 0.003)]),
    {"id": "x_experts", "label": "Commander un rapport d'experts (réponse dans deux mois)", "hint": "Le dossier reviendra",
     "effects": [], "outcome": "POSTPONED", "reaskAfterDays": 60},
])

pack("majority", "Majorité", ["majority_rebels", "group_split"], [
    o("dinner", "Recevoir les frondeurs à dîner à l'Élysée", "Liens renoués ; on dira que vous cédez",
      [e("government.parliamentSupport", 0.01), e("opinion.national", -0.001)], "PARTIAL"),
    o("posts", "Leur offrir des postes (rapports, missions)", "Efficace et discret ; la presse finira par le savoir",
      [e("government.parliamentSupport", 0.012), e("opinion.national", -0.002, 0, 30)], "ACCEPTED"),
    o("dissolution_threat", "Laisser planer la menace d'une dissolution", "Les députés réfléchissent à deux fois",
      [e("government.parliamentSupport", 0.006), e("opinion.national", -0.002)]),
])

# --- Courriers pour les événements qui n'en avaient pas --------------------------------------------
MESSAGES, TEMPLATES = {}, []


def message(event, sender, ministry, days, default, options, subjects, context, problem, request):
    m = {"template": "resp_" + event, "sender": sender, "responseDays": days, "defaultOption": default, "options": options}
    if ministry: m["ministry"] = ministry
    MESSAGES[event] = m
    TEMPLATES.append(letter("resp_" + event, [V(t) for t in subjects], [V(t) for t in context], [V(t) for t in problem], [V(t) for t in request]))


def op(id, label, hint, effects, outcome="NEUTRAL"):
    return {"id": id, "label": label, "hint": hint, "effects": effects, "outcome": outcome}


message("drought", "MINISTER", "agriculture", 5, "prefects", [
    op("restrictions", "Restrictions d'eau renforcées dans tout le pays", "Nappes préservées ; agriculteurs et golfs furieux",
       [e("quality.environment", 0.006), e("quality.agriculture", -0.004), e(G + "rural", -0.008)], "PARTIAL"),
    op("farm_aid", "Aide d'urgence aux éleveurs et agriculteurs", "Coût : 400 M€",
       [e("budget.oneOff", 0.4), e("quality.agriculture", 0.01, 90), e(G + "rural", 0.015)], "ACCEPTED"),
    op("reservoirs", "Programme de retenues d'eau agricoles", "Coût : 800 M€ sur deux ans ; écologistes opposés",
       [e("budget.oneOff", 0.8, 730), e("quality.agriculture", 0.008, 0, 365), e("quality.environment", -0.006), e(G + "young", -0.006)], "ACCEPTED"),
    op("prefects", "Laisser les préfets gérer au cas par cas", "Aucun coût ; réponse inégale selon les territoires",
       [e(G + "rural", -0.006)], "REFUSED"),
], ["Sécheresse : vos arbitrages", "Sécheresse historique : les nappes au plus bas", "Manque d'eau : décisions urgentes"],
   ["Les nappes phréatiques sont au plus bas dans les deux tiers du pays.", "Plus de soixante départements sont déjà soumis à des restrictions d'eau.",
    "Les rivières sont à sec dans plusieurs régions et les récoltes souffrent."],
   ["Les éleveurs manquent de fourrage et certaines communes sont ravitaillées par camions-citernes.",
    "Si rien n'est fait, les prix alimentaires vont grimper cet automne."],
   ["Je vous propose de renforcer les restrictions, d'aider l'agriculture, de lancer des retenues d'eau, ou de laisser agir les préfets."])

message("dam_drought", "MINISTER", "ecology", 4, "standard", [
    op("drinking_water", "Priorité absolue à l'eau potable et à l'irrigation", "Barrage moins productif plus longtemps",
       [e("scope.offlineDays", 5), e(G + "rural", 0.01), e("quality.agriculture", 0.004)], "PARTIAL"),
    op("imports", "Compenser par des importations d'électricité", "Coût : 150 M€",
       [e("budget.oneOff", 0.15), e("economy.output", 0.0001, 30)], "ACCEPTED"),
    op("derogation", "Dérogation pour turbiner davantage", "Production rétablie plus vite ; rivières asséchées",
       [e("scope.offlineDays", -6), e("quality.environment", -0.006), e(G + "rural", -0.006)]),
    op("standard", "Laisser l'exploitant gérer", "Aucun coût", [], "REFUSED"),
], ["Barrage à l'arrêt faute d'eau", "Sécheresse : production hydraulique réduite", "Retenues au plus bas"],
   ["Le niveau du lac de retenue est tombé sous le seuil d'exploitation.", "La production hydroélectrique est fortement réduite.",
    "Les agriculteurs de la vallée réclament que l'eau soit gardée pour l'irrigation."],
   ["Électricité, eau potable, irrigation et vie des rivières se disputent la même eau.",
    "Les réserves ne se reconstitueront pas avant les pluies d'automne."],
   ["Je vous propose de prioriser l'eau potable, de compenser par des importations, d'accorder une dérogation, ou de laisser l'exploitant gérer."])

message("port_strike", "MINISTER", "transport", 3, "wait", [
    op("negotiate", "Ouvrir une négociation avec les dockers", "Coût : 100 M€ ; reprise rapide",
       [e("budget.oneOff", 0.1), e("scope.offlineDays", -4), e(G + "private_employees", 0.006)], "ACCEPTED"),
    op("requisition", "Réquisitionner les grutiers", "Port rouvert ; syndicats en colère",
       [e("scope.offlineDays", -6), e(G + "low_income", -0.008), e("economy.businessConfidence", 0.004), e("chain.national_strike", 0.15, 0, 10)]),
    op("mediator", "Nommer un médiateur", "Temps gagné",
       [e("scope.offlineDays", -2), e("opinion.national", 0.001)], "PARTIAL"),
    op("wait", "Laisser le conflit suivre son cours", "Exportations bloquées plus longtemps",
       [e("economy.businessConfidence", -0.004), e("economy.output", -0.0002, 14)], "REFUSED"),
], ["Port bloqué par la grève", "Grève des dockers : vos instructions", "Les exportations à l'arrêt"],
   ["Les dockers ont cessé le travail et plus aucun navire n'est déchargé.", "Des dizaines de porte-conteneurs attendent au large.",
    "Les entreprises exportatrices s'inquiètent de leurs livraisons."],
   ["Chaque jour de blocage coûte cher aux industriels et risque de détourner le trafic vers Anvers ou Rotterdam.",
    "Le conflit porte sur les retraites et la pénibilité."],
   ["Je vous propose de négocier, de réquisitionner, de nommer un médiateur, ou d'attendre."])

message("rating_downgrade", "MINISTER", "economy", 4, "ignore", [
    op("savings", "Annoncer un plan d'économies immédiat", "Marchés rassurés ; impopulaire",
       [e("budget.oneOff", -3.0, 365), e("economy.businessConfidence", 0.015), e("opinion.national", -0.006), e(G + "civil_servants", -0.01)], "ACCEPTED"),
    op("roadshow", "Tournée des investisseurs par le ministre", "Confiance partiellement restaurée",
       [e("economy.businessConfidence", 0.008)], "PARTIAL"),
    op("contest", "Contester publiquement l'agence", "Populaire ; les marchés n'aiment pas",
       [e("opinion.national", 0.003), e("economy.businessConfidence", -0.006)]),
    op("ignore", "Ne pas commenter", "Aucun effet immédiat", [], "REFUSED"),
], ["Note souveraine dégradée", "Les agences de notation sanctionnent la France", "Dette : la note de la France abaissée"],
   ["Une grande agence de notation vient d'abaisser la note de la dette française.", "L'agence pointe un déficit qui ne se réduit pas et une dette élevée.",
    "Les taux d'emprunt de l'État ont aussitôt monté sur les marchés."],
   ["Chaque hausse de taux alourdit la charge de la dette pour des années.", "Les autres agences pourraient suivre dans les prochains mois."],
   ["Je vous propose un plan d'économies, une tournée des investisseurs, une contestation publique, ou le silence."])

message("sports_victory", "MINISTER", "education", 3, "message", [
    op("reception", "Recevoir les champions à l'Élysée", "Coût : 1 M€ ; moment de communion",
       [e("budget.oneOff", 0.001), e("opinion.national", 0.006), e(G + "young", 0.008)], "ACCEPTED"),
    op("sport_plan", "Lancer un plan « sport pour tous »", "Coût : 300 M€ ; équipements dans les quartiers",
       [e("budget.oneOff", 0.3, 365), e("quality.health", 0.004, 0, 180), e("quality.education", 0.003), e(G + "young", 0.01)], "ACCEPTED"),
    op("legion", "Les décorer de la Légion d'honneur", "Gratuit ; critiqué par certains",
       [e("opinion.national", 0.003)]),
    op("message", "Un simple message de félicitations", "Sobre", [e("opinion.national", 0.001)], "NEUTRAL"),
], ["Victoire historique des Bleus", "Le pays fête ses champions", "Une victoire qui rassemble"],
   ["L'équipe de France vient de remporter un titre historique.", "Des centaines de milliers de personnes ont fêté la victoire dans les rues.",
    "Les audiences télévisées ont battu tous les records."],
   ["Le pays traverse un rare moment d'unité ; l'opinion attend un geste.", "Les clubs amateurs espèrent un afflux de licenciés."],
   ["Je vous propose de recevoir l'équipe, de lancer un plan pour le sport, de les décorer, ou d'envoyer un message."])

message("investment_announcement", "MINISTER", "economy", 4, "nothing", [
    op("visit", "Annoncer moi-même l'investissement sur le site", "Coût : 1 M€ ; image de président bâtisseur",
       [e("budget.oneOff", 0.001), e("opinion.national", 0.004), e(G + "private_employees", 0.006)], "ACCEPTED"),
    op("subsidy", "Aides publiques pour accélérer le projet", "Coût : 500 M€ ; usine ouverte un an plus tôt",
       [e("budget.oneOff", 0.5), e("economy.output", 0.0006, 365), e("economy.unemployment", -0.0004, 0, 180)], "ACCEPTED"),
    op("conditions", "Conditionner les aides à l'emploi local et au climat", "Exigeant ; l'investisseur hésite",
       [e("quality.environment", 0.003), e("economy.businessConfidence", -0.003), e(G + "low_income", 0.004)], "PARTIAL"),
    op("nothing", "Laisser l'entreprise communiquer", "Aucun coût", [], "NEUTRAL"),
], ["Un investissement industriel majeur", "Une usine géante pour la France", "Bonne nouvelle pour l'emploi"],
   ["Un grand groupe industriel annonce la construction d'une usine géante en France.", "Plusieurs milliers d'emplois directs sont prévus.",
    "Le site a été choisi face à des concurrents allemands et espagnols."],
   ["L'annonce peut être un symbole de la réindustrialisation.", "La région d'accueil attend des infrastructures et des formations."],
   ["Je vous propose de l'annoncer vous-même, d'aider le projet, de poser des conditions, ou de laisser l'entreprise communiquer."])

for p in PACKS:
    if p["id"] == "heat": p["events"] += ["drought", "dam_drought"]
    if p["id"] == "social": p["events"] += ["port_strike"]

# --- Vérifications --------------------------------------------------------------------------------
events = {}
for f in glob.glob(os.path.join(ROOT, "events", "*.json")):
    if f.endswith(("intensity.json", "responses.json")):
        continue
    for x in json.load(open(f))["events"]:
        events[x["id"]] = x
measures = {m["id"] for m in json.load(open(os.path.join(ROOT, "countries", "FRA", "measures.json")))["measures"]}
groups = {g["id"] for g in json.load(open(os.path.join(ROOT, "countries", "FRA", "social_groups.json")))["groups"]}
TARGETS = re.compile(r"^(budget\.oneOff|economy\.(output|consumerConfidence|businessConfidence|inflation|unemployment)|opinion\.national"
                     r"|opinion\.group\.\w+|scope\.(approval|unemployment|healthAccess|crime)|sender\.relation|president\.popularity"
                     r"|quality\.(health|environment|security|transport|education|agriculture|social|justice)|military\.readiness"
                     r"|government\.parliamentSupport|alliance\.(EU|NATO)\.[A-Z_]+|memory\.[A-Z]{3}\.[A-Z_]+"
                     r"|country\.(CRISIS_SOLIDARITY|DISAGREEMENT|NEGOTIATION_GOODWILL|TALK_CORDIAL|TALK_TENSE)"
                     r"|abroad\.(output|approval|trade)|operation\.(sanction|cyber))$")
FOREIGN_ONLY = ("country.", "abroad.", "operation.")
for ev, m in MESSAGES.items():
    assert ev in events and not events[ev].get("message"), ev
    for opt in m["options"]:
        for fx in opt["effects"]:
            assert TARGETS.match(fx["target"]) or fx["target"].startswith(("chain.", "scope.offlineDays")), fx
for p in PACKS:
    for ev in p["events"]:
        assert ev in events, (p["id"], ev)
        foreign = events[ev]["scope"] == "FOREIGN_COUNTRY"
        for opt in p["options"]:
            for fx in opt["effects"]:
                t = fx["target"]
                assert TARGETS.match(t), (p["id"], t)
                if t.startswith(FOREIGN_ONLY):
                    assert foreign, (p["id"], ev, t)
                if t.startswith(G):
                    assert t[len(G):] in groups, t
    for m in p["measures"]:
        assert m in measures, (p["id"], m)

out = {"_doc": "Options et mesures supplémentaires par famille d'événements. Généré par tools/datagen/event_responses_fr.py.",
       "packs": PACKS, "messages": MESSAGES}
path = os.path.join(ROOT, "events", "responses.json")
json.dump(out, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)

json.dump({"templates": TEMPLATES}, open(os.path.join(ROOT, "dialogue", "fr", "responses.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
covered = {ev for p in PACKS for ev in p["events"]}
with_message = [i for i, x in events.items() if x.get("message") or i in MESSAGES]
print(f"{len(PACKS)} paquets, {sum(len(p['options']) for p in PACKS)} options, "
      f"{len(covered & set(with_message))}/{len(with_message)} événements enrichis")
