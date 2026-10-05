"""Décisions nationales du président (France) -> assets/data/countries/FRA/national_actions.json.

Chaque décision : coût (Md€), durée (jours, 0 = immédiat), délai avant de recommencer, effets
immédiats (éventuellement étalés avec "days") et effets à l'aboutissement. Les effets sont
volontairement contrastés : un gain pour certains groupes est souvent une perte pour d'autres.
Ordres de grandeur alignés sur les réformes et les événements existants.
"""
import json
import os

CATEGORIES = [
    ("economy", "Économie", "€", "Relance, pouvoir d'achat, industrie, finances publiques."),
    ("social", "Social", "♥", "Emploi, santé, logement, solidarité."),
    ("security", "Sécurité", "⚑", "Police, justice, frontières, ordre public."),
    ("ecology", "Écologie", "♻", "Climat, énergie, transports du quotidien."),
    ("institutions", "Institutions", "⚖", "Démocratie, territoires, vie publique."),
    ("communication", "Communication", "☀", "Prises de parole et déplacements du président."),
    ("international", "International", "☎", "Déplacements et initiatives diplomatiques."),
    ("defense", "Défense", "⚔", "Armées, stocks, alliances militaires."),
]


def e(target, amount, days=0, delay=0):
    d = {"target": target, "amount": amount}
    if days:
        d["days"] = days
    if delay:
        d["delayDays"] = delay
    return d


def g(group, amount, days=0):
    return e(f"opinion.group.{group}", amount, days)


A = []


def cond(variable, min=None, max=None):
    c = {"variable": variable}
    if min is not None:
        c["min"] = min
    if max is not None:
        c["max"] = max
    return c


def action(cat, id, label, icon, description, cost=0.0, duration=0, cooldown=365, immediate=(), completion=(),
           requires=(), requires_text="", confirm=False):
    a = {"id": id, "label": label, "icon": icon, "category": cat, "description": description}
    if requires:
        a["requires"] = list(requires)
        a["requiresText"] = requires_text
    if confirm:
        a["confirm"] = True
    if cost:
        a["costBillions"] = cost
    if duration:
        a["durationDays"] = duration
    a["cooldownDays"] = cooldown
    if immediate:
        a["immediate"] = list(immediate)
    if completion:
        a["onCompletion"] = list(completion)
    A.append(a)


# --- Économie ---------------------------------------------------------------------------------
action("economy", "stimulus_plan", "Plan de relance", "▲",
       "Commande publique, aides à l'investissement, chantiers : l'activité repart, la dette aussi.",
       cost=20, duration=365, cooldown=1095,
       immediate=[e("economy.output", 0.004, 365), e("economy.businessConfidence", 0.02), g("self_employed", 0.02)],
       completion=[e("economy.unemployment", -0.002, 180)])
action("economy", "energy_cheque", "Chèque énergie", "⚡",
       "Une aide directe aux ménages modestes pour payer le chauffage et l'électricité.",
       cost=4, cooldown=365,
       immediate=[g("low_income", 0.03), g("middle_income", 0.01), e("economy.consumerConfidence", 0.01)])
action("economy", "price_shield", "Bouclier tarifaire", "◆",
       "L'État plafonne la hausse des prix de l'énergie pendant six mois. Efficace, mais très cher.",
       cost=10, duration=180, cooldown=730,
       immediate=[e("economy.inflation", -0.003, 60), e("economy.consumerConfidence", 0.015), e("opinion.national", 0.005)])
action("economy", "red_tape", "Choc de simplification", "✂",
       "Suppression de formulaires et de normes : les entreprises respirent, l'administration grince.",
       duration=365, cooldown=1460,
       immediate=[g("self_employed", 0.03), g("civil_servants", -0.02)],
       completion=[e("economy.businessConfidence", 0.02), e("economy.potentialGrowth", 0.0005, 365)])
action("economy", "industry_plan", "Plan France Industrie", "⚒",
       "Batteries, semi-conducteurs, médicaments : relocaliser les productions stratégiques.",
       cost=8, duration=730, cooldown=1460,
       immediate=[e("economy.businessConfidence", 0.01), g("private_employees", 0.01)],
       completion=[e("economy.potentialGrowth", 0.001, 730), e("economy.unemployment", -0.001, 365)])
action("economy", "spending_freeze", "Gel des dépenses de l'État", "❄",
       "Pas de nouvelles embauches ni de crédits supplémentaires : un signal aux marchés et à Bruxelles.",
       cooldown=730,
       immediate=[e("spending.state_operations", -0.03), g("civil_servants", -0.04),
                  e("economy.businessConfidence", 0.01), e("alliance.EU.NEGOTIATION_GOODWILL", 0.01)])
action("economy", "tax_fraud", "Offensive contre la fraude fiscale", "⚖",
       "Contrôleurs supplémentaires et croisement des fichiers : plusieurs milliards récupérés au bout d'un an.",
       cost=0.3, duration=365, cooldown=730,
       immediate=[g("high_income", -0.02), g("low_income", 0.01)],
       completion=[e("budget.oneOff", -4)])
action("economy", "purchasing_bonus", "Prime de pouvoir d'achat", "€",
       "Les entreprises peuvent verser une prime défiscalisée à leurs salariés.",
       cooldown=730,
       immediate=[g("private_employees", 0.03), g("self_employed", -0.02), e("economy.consumerConfidence", 0.01)])

# --- Social -----------------------------------------------------------------------------------
action("social", "minimum_income", "Revaloriser les minima sociaux", "✚",
       "RSA, minimum vieillesse et allocations relevés plus vite que les prix.",
       cost=3, cooldown=730,
       immediate=[g("low_income", 0.04), g("inactive", 0.03), g("high_income", -0.01), e("spending.solidarity", 0.02)])
action("social", "youth_jobs", "Un jeune, une solution", "★",
       "Apprentissage, contrats aidés et formations pour les moins de 26 ans.",
       cost=6, duration=365, cooldown=1095,
       immediate=[g("young", 0.04)],
       completion=[e("economy.unemployment", -0.0015, 180)])
action("social", "hospital_plan", "Plan d'urgence pour l'hôpital", "⚕",
       "Primes, recrutements et lits rouverts dans les services les plus en difficulté.",
       cost=5, duration=180, cooldown=1095,
       immediate=[g("civil_servants", 0.02), e("opinion.national", 0.004)],
       completion=[e("quality.health", 0.02, 180)])
action("social", "housing_plan", "Plan logement", "⌂",
       "Construire, rénover, encadrer les loyers dans les villes les plus chères.",
       cost=7, duration=730, cooldown=1460,
       immediate=[g("young", 0.02), g("low_income", 0.02), g("high_income", -0.01)],
       completion=[e("economy.output", 0.002, 365)])
action("social", "carers_plan", "Plan grand âge", "♥",
       "Aides à domicile, EHPAD contrôlés et mieux dotés.",
       cost=3, duration=365, cooldown=1460,
       immediate=[g("seniors", 0.03), g("retirees", 0.02)],
       completion=[e("quality.social", 0.01, 180)])
action("social", "school_plan", "Plan pour l'école", "✎",
       "Classes dédoublées, enseignants mieux payés, soutien scolaire gratuit.",
       cost=4, duration=365, cooldown=1460,
       immediate=[g("civil_servants", 0.02), g("adults", 0.01)],
       completion=[e("quality.education", 0.02, 365)])

# --- Sécurité ---------------------------------------------------------------------------------
action("security", "state_of_emergency", "Déclarer l'état d'urgence", "⚠",
       "Pouvoirs exceptionnels pendant trois mois : perquisitions, assignations, interdictions de rassemblement.",
       duration=90, cooldown=365, confirm=True,
       immediate=[e("quality.security", 0.02), g("seniors", 0.02), g("young", -0.03),
                  e("government.parliamentSupport", -0.02)])
action("security", "police_hiring", "Recruter 10 000 policiers et gendarmes", "⚑",
       "Plus de forces sur le terrain d'ici deux ans.",
       cost=1.5, duration=730, cooldown=1460,
       immediate=[e("spending.police", 0.03), g("seniors", 0.02), g("rural", 0.01)],
       completion=[e("quality.security", 0.03, 180)])
action("security", "fast_justice", "Justice de proximité", "⚖",
       "Magistrats et greffiers recrutés, procédures accélérées pour les petits délits.",
       cost=0.8, duration=365, cooldown=1095,
       immediate=[g("adults", 0.01)],
       completion=[e("quality.justice", 0.03, 180)])
action("security", "border_controls", "Renforcer les contrôles aux frontières", "⊘",
       "Contrôles rétablis aux frontières intérieures et reconduites accélérées.",
       cost=0.5, cooldown=730,
       immediate=[e("demography.immigration", -0.15, 180), g("rural", 0.02), g("seniors", 0.01), g("urban", -0.01),
                  e("alliance.EU.DISAGREEMENT", -0.01)])
action("security", "drug_plan", "Plan contre le narcotrafic", "✖",
       "Brigades spécialisées, saisies d'avoirs, coopération avec les ports.",
       cost=0.6, duration=365, cooldown=1095,
       immediate=[g("urban", 0.01), g("seniors", 0.01)],
       completion=[e("quality.security", 0.02, 180)])

# --- Écologie ---------------------------------------------------------------------------------
action("ecology", "home_renovation", "Rénovation thermique des logements", "♻",
       "Aides pour isoler les maisons et remplacer les chaudières au fioul.",
       cost=5, duration=730, cooldown=1460,
       immediate=[g("middle_income", 0.02), e("economy.output", 0.0015, 730)],
       completion=[e("quality.environment", 0.02, 365)])
action("ecology", "bike_plan", "Transports du quotidien", "⚲",
       "Pistes cyclables, trains régionaux et bus express.",
       cost=2, duration=365, cooldown=1095,
       immediate=[g("urban", 0.02), g("rural", -0.01)],
       completion=[e("quality.transport", 0.01, 180), e("quality.environment", 0.01, 180)])
action("ecology", "new_reactors", "Relance du nucléaire", "☢",
       "Commande de nouveaux réacteurs et prolongation du parc existant. Long, cher, durable.",
       cost=12, duration=1460, cooldown=3650,
       immediate=[e("economy.businessConfidence", 0.01), g("urban", -0.01)],
       completion=[e("energy.capacity.new_nuclear", 6600)])
action("ecology", "renewables_push", "Accélérer les renouvelables", "☀",
       "Procédures raccourcies pour l'éolien en mer et le solaire.",
       cost=3, duration=730, cooldown=1460,
       immediate=[g("young", 0.02), g("rural", -0.01)],
       completion=[e("energy.capacity.new_renewables", 5000), e("quality.environment", 0.01, 180)])
action("ecology", "lez_pause", "Suspendre les zones à faibles émissions", "‖",
       "Plus d'interdiction de circuler pour les vieilles voitures en ville.",
       cooldown=1095,
       immediate=[g("rural", 0.03), g("low_income", 0.01), g("urban", -0.01), e("quality.environment", -0.01)])
action("ecology", "farm_support", "Plan de soutien aux agriculteurs", "✿",
       "Prix planchers, simplification des normes et aides à la transition.",
       cost=2, duration=180, cooldown=730,
       immediate=[g("rural", 0.03), g("self_employed", 0.01)],
       completion=[e("quality.agriculture", 0.02, 180)])

# --- Institutions -----------------------------------------------------------------------------
action("institutions", "great_debate", "Grand débat national", "☰",
       "Deux mois de réunions publiques partout en France pour écouter les colères.",
       cost=0.1, duration=60, cooldown=1095,
       completion=[e("opinion.national", 0.008, 60), e("government.parliamentSupport", -0.005)])
action("institutions", "citizens_convention", "Convention citoyenne", "◎",
       "150 citoyens tirés au sort proposent des mesures sur un grand sujet.",
       cost=0.02, duration=180, cooldown=1460,
       immediate=[g("young", 0.02)],
       completion=[e("quality.environment", 0.005), e("opinion.national", 0.003)])
action("institutions", "ethics_law", "Loi de moralisation de la vie publique", "✔",
       "Interdiction des emplois familiaux, contrôle des frais, casier judiciaire vierge exigé.",
       cooldown=1460,
       immediate=[e("opinion.national", 0.005), e("government.parliamentSupport", -0.02)])
action("institutions", "decentralisation", "Nouvel acte de décentralisation", "⌘",
       "Plus de compétences et de moyens pour les régions et les départements.",
       cost=1, duration=365, cooldown=1460,
       immediate=[e("spending.local_authorities", 0.02), g("rural", 0.02), g("civil_servants", -0.01)])
action("institutions", "reshuffle_majority", "Ouvrir la majorité", "⚇",
       "Des postes et des concessions pour rallier des députés hésitants.",
       cooldown=365,
       immediate=[e("government.parliamentSupport", 0.03), e("opinion.national", -0.003)])

# --- Communication ----------------------------------------------------------------------------
action("communication", "tv_address", "Allocution télévisée", "▣",
       "Vingt minutes à 20 h pour fixer le cap. L'effet s'use si l'on en abuse.",
       cooldown=60,
       immediate=[e("opinion.national", 0.003)])
action("communication", "press_conference", "Grande conférence de presse", "✉",
       "Deux heures de questions des journalistes : risqué, mais mémorable.",
       cooldown=120,
       immediate=[e("opinion.national", 0.002), e("president.popularity", 0.02)])
action("communication", "tour_of_france", "Tour de France des territoires", "⚐",
       "Un mois de déplacements dans les villes moyennes et les campagnes.",
       cost=0.01, duration=30, cooldown=180,
       immediate=[g("rural", 0.02)],
       completion=[g("adults", 0.01)])
action("communication", "youth_media", "Entretien avec des influenceurs", "▶",
       "Parler aux jeunes là où ils sont : réseaux sociaux et plateformes vidéo.",
       cooldown=120,
       immediate=[g("young", 0.02), g("seniors", -0.005)])
action("communication", "business_summit", "Sommet des investisseurs", "◆",
       "Les grands patrons du monde reçus à Versailles : annonces d'usines et d'emplois.",
       cost=0.02, cooldown=365,
       immediate=[e("economy.businessConfidence", 0.02), g("self_employed", 0.01), g("low_income", -0.01)])

# --- International ----------------------------------------------------------------------------
action("international", "eu_tour", "Tournée des capitales européennes", "✈",
       "Berlin, Rome, Madrid, Varsovie : préparer les prochains Conseils européens.",
       cooldown=180,
       immediate=[e("alliance.EU.NEGOTIATION_GOODWILL", 0.02)])
action("international", "washington_visit", "Visite d'État à Washington", "✈",
       "Dîner à la Maison-Blanche et discours au Congrès.",
       cooldown=365,
       immediate=[e("memory.USA.NEGOTIATION_GOODWILL", 0.03), e("alliance.NATO.NEGOTIATION_GOODWILL", 0.01)])
action("international", "beijing_visit", "Visite d'État à Pékin", "✈",
       "Contrats commerciaux et dialogue exigeant : Washington regarde.",
       cooldown=365,
       immediate=[e("memory.CHN.NEGOTIATION_GOODWILL", 0.03), e("economy.businessConfidence", 0.005),
                  e("memory.USA.DISAGREEMENT", -0.01)])
action("international", "africa_summit", "Sommet Afrique–France", "☼",
       "Nouveau partenariat avec les pays du Maghreb et d'Afrique.",
       cost=0.1, cooldown=365,
       immediate=[e("memory.MAR.NEGOTIATION_GOODWILL", 0.02), e("memory.DZA.NEGOTIATION_GOODWILL", 0.02),
                  e("memory.TUN.NEGOTIATION_GOODWILL", 0.02), e("memory.EGY.NEGOTIATION_GOODWILL", 0.02)])
action("international", "development_aid", "Hausse de l'aide au développement", "✚",
       "Deux milliards de plus pour la santé, l'eau et l'éducation dans les pays pauvres.",
       cost=2, cooldown=730,
       immediate=[e("alliance.EU.NEGOTIATION_GOODWILL", 0.01), e("memory.MAR.NEGOTIATION_GOODWILL", 0.02),
                  e("memory.TUN.NEGOTIATION_GOODWILL", 0.02), e("memory.EGY.NEGOTIATION_GOODWILL", 0.02),
                  g("high_income", -0.005), g("low_income", -0.01)])
action("international", "peace_conference", "Conférence de paix à Paris", "☮",
       "Réunir belligérants et médiateurs : prestige si cela avance.",
       cost=0.05, cooldown=365,
       immediate=[e("war.attackers.NEGOTIATION_GOODWILL", 0.02), e("war.defenders.NEGOTIATION_GOODWILL", 0.02),
                  e("opinion.national", 0.002)])

# --- Défense ----------------------------------------------------------------------------------
action("defense", "military_budget", "Rallonge de la programmation militaire", "⚔",
       "Trois milliards de plus par an pour les armées.",
       cost=3, duration=365, cooldown=1095,
       immediate=[e("spending.defense", 0.05), e("alliance.NATO.NEGOTIATION_GOODWILL", 0.02)],
       completion=[e("military.readiness", 0.02)])
action("defense", "war_economy", "Économie de guerre : munitions", "✹",
       "Les industriels produisent jour et nuit obus et missiles.",
       cost=2, duration=270, cooldown=730,
       immediate=[e("economy.output", 0.0005, 270)],
       completion=[e("military.ammoStock", 0.1), e("military.fuelStock", 0.05)])
action("defense", "national_service", "Service national universel", "⚑",
       "Un mois obligatoire pour tous les jeunes de 16 ans.",
       cost=1.5, duration=365, cooldown=1460,
       immediate=[g("young", -0.02), g("seniors", 0.02)],
       completion=[e("military.readiness", 0.01)])
action("defense", "joint_exercise", "Grand exercice interarmées", "✪",
       "Manœuvres avec les alliés : les forces s'entraînent, le carburant brûle.",
       cost=0.2, duration=30, cooldown=180,
       immediate=[e("military.fuelStock", -0.02), e("alliance.NATO.NEGOTIATION_GOODWILL", 0.01)],
       completion=[e("military.readiness", 0.03)])

# --- Décisions de crise : débloquées par la situation --------------------------------------------
action("economy", "austerity_plan", "Plan de rigueur", "✂",
       "Coupes franches dans les dépenses de l'État et des collectivités pour rassurer Bruxelles et les marchés.",
       cooldown=1095, confirm=True,
       requires=[cond("economy.deficitRatio", min=0.05)], requires_text="si le déficit dépasse 5 % du PIB",
       immediate=[e("spending.state_operations", -0.05), e("spending.local_authorities", -0.03),
                  e("economy.businessConfidence", 0.02), e("alliance.EU.NEGOTIATION_GOODWILL", 0.02),
                  g("civil_servants", -0.05), g("low_income", -0.02), g("rural", -0.01)])
action("economy", "market_reassurance", "Rassurer les marchés", "◆",
       "Trajectoire de dette crédible présentée aux agences de notation et aux investisseurs.",
       cooldown=365,
       requires=[cond("economy.debtRatio", min=1.2)], requires_text="si la dette dépasse 120 % du PIB",
       immediate=[e("economy.businessConfidence", 0.03), e("alliance.EU.NEGOTIATION_GOODWILL", 0.01)])
action("economy", "price_freeze", "Blocage des prix alimentaires", "‖",
       "Les prix de cent produits de base sont gelés trois mois. Les commerçants protestent.",
       duration=90, cooldown=730,
       requires=[cond("economy.inflation", min=0.04)], requires_text="si l'inflation dépasse 4 %",
       immediate=[e("economy.inflation", -0.005, 90), g("low_income", 0.03), g("middle_income", 0.01),
                  g("self_employed", -0.03), e("economy.businessConfidence", -0.01)])
action("economy", "big_loan", "Grand emprunt national", "▲",
       "Trente milliards empruntés pour investir dans l'avenir quand l'économie cale.",
       cost=30, duration=730, cooldown=1825,
       requires=[cond("economy.growth", max=0.0)], requires_text="en récession (croissance négative)",
       immediate=[e("economy.output", 0.006, 365), e("economy.businessConfidence", 0.02)],
       completion=[e("economy.potentialGrowth", 0.001, 730)])
action("social", "jobs_emergency", "Plan d'urgence pour l'emploi", "⚒",
       "Contrats aidés, chômage partiel prolongé, primes à l'embauche.",
       cost=8, duration=365, cooldown=1095,
       requires=[cond("economy.unemployment", min=0.09)], requires_text="si le chômage dépasse 9 %",
       immediate=[e("economy.unemployment", -0.004, 365), g("low_income", 0.02), g("inactive", 0.02)])
action("ecology", "energy_sobriety", "Plan de sobriété énergétique", "❄",
       "Chauffage à 19 °C, éclairage public réduit, industrie incitée à décaler sa consommation.",
       cooldown=365,
       requires=[cond("energy.priceIndex", min=1.3)], requires_text="si les prix de l'énergie ont bondi de plus de 30 %",
       immediate=[e("economy.consumerConfidence", -0.005), e("quality.environment", 0.01),
                  e("economy.inflation", -0.002, 120), e("opinion.national", 0.002)])
action("institutions", "unity_address", "Appel à l'unité nationale", "☰",
       "Un discours solennel pour retrouver la confiance quand tout semble perdu.",
       cooldown=365,
       requires=[cond("opinion.national", max=0.35)], requires_text="si votre popularité tombe sous 35 %",
       immediate=[e("opinion.national", 0.01)])
action("institutions", "confidence_vote", "Engager la responsabilité du gouvernement", "⚖",
       "Le Premier ministre met sa majorité au pied du mur : soutien ou dissolution.",
       cooldown=365, confirm=True,
       requires=[cond("government.parliamentSupport", max=0.5)], requires_text="si l'Assemblée vous soutient à moins de 50 %",
       immediate=[e("government.parliamentSupport", 0.05), e("opinion.national", -0.004)])
action("defense", "general_mobilization", "Mobilisation générale", "⚑",
       "Rappel des réservistes et réquisition de l'industrie pour l'effort de guerre.",
       cost=5, cooldown=1095, confirm=True,
       requires=[cond("military.atWar", min=1)], requires_text="en guerre",
       immediate=[e("military.readiness", 0.08), e("economy.output", -0.004, 180), g("young", -0.05), g("seniors", 0.02)])
action("defense", "war_tax", "Contribution de solidarité nationale", "€",
       "Un impôt exceptionnel sur les plus hauts revenus pour financer la guerre.",
       cooldown=730,
       requires=[cond("military.atWar", min=1)], requires_text="en guerre",
       immediate=[e("budget.oneOff", -10), g("high_income", -0.04), e("opinion.national", 0.005)])
action("international", "refugee_reception", "Plan d'accueil des réfugiés", "♥",
       "Hébergement, scolarisation et accompagnement des familles qui fuient la guerre.",
       cost=1.5, cooldown=730,
       requires=[cond("military.nearbyWar", min=1)], requires_text="si une guerre éclate en Europe",
       immediate=[e("alliance.EU.NEGOTIATION_GOODWILL", 0.02), g("urban", 0.01), g("rural", -0.02),
                  e("demography.immigration", 0.1, 180)])

# --- Ajouts : grands programmes et actes présidentiels -------------------------------------------
action("economy", "france_2030", "France 2030", "★",
       "54 Md€ sur cinq ans pour les technologies d'avenir : petits réacteurs, hydrogène, batteries, santé, spatial, semi-conducteurs.",
       cost=54, duration=1825, cooldown=1825,
       immediate=[e("economy.businessConfidence", 0.015), g("self_employed", 0.01)],
       completion=[e("economy.potentialGrowth", 0.002), e("sector.tech", 0.03), e("sector.energy", 0.02), e("sector.aerospace", 0.02),
                   e("sector.health", 0.02), e("economy.unemployment", -0.002, 365)])
action("economy", "france_relance", "France Relance", "▲",
       "100 Md€ en deux ans : rénovation des bâtiments, industrie, emploi des jeunes, en partie financés par l'Europe.",
       cost=100, duration=730, cooldown=1825,
       requires=[cond("economy.growth", max=0.005)], requires_text="si la croissance s'essouffle (moins de 0,5 %)",
       immediate=[e("economy.output", 0.012, 730), e("economy.businessConfidence", 0.03), g("self_employed", 0.02),
                  e("alliance.EU.NEGOTIATION_GOODWILL", 0.02)],
       completion=[e("economy.unemployment", -0.006, 365), e("quality.environment", 0.01), e("sector.construction", 0.03)])
action("social", "apprenticeship_plan", "Grand plan pour l'apprentissage", "✎",
       "Aides aux employeurs, centres de formation, campagne nationale : objectif un million d'apprentis.",
       cost=4, duration=365, cooldown=1095,
       immediate=[g("young", 0.02), g("self_employed", 0.01)],
       completion=[e("economy.unemployment", -0.003, 365), e("economy.potentialGrowth", 0.0005), e("quality.education", 0.01)])
action("institutions", "article_16", "Recourir à l'article 16 (pleins pouvoirs)", "⚠",
       "Quand la Nation est menacée : vous gouvernez seul pendant trois mois, vos textes s'appliquent sans vote. Les libertés reculent, l'Europe s'alarme.",
       cooldown=1095, confirm=True,
       requires=[cond("derived.nationInDanger", min=1)], requires_text="seulement en cas d'insurrection ou d'invasion du territoire",
       immediate=[e("power.article16", 90), e("opinion.national", -0.02), e("alliance.EU.DISAGREEMENT", -0.05), g("young", -0.03)])
action("institutions", "pardon", "Grâce présidentielle", "✔",
       "Gracier une condamnée dont le cas émeut le pays : un geste d'humanité, une entorse à la justice pour d'autres.",
       cooldown=730,
       immediate=[g("young", 0.006), g("urban", 0.004), g("seniors", -0.004), e("quality.justice", -0.002)])
action("communication", "national_tribute", "Hommage national et entrée au Panthéon", "★",
       "Une grande figure entre au Panthéon : un moment d'unité nationale.",
       cooldown=730,
       immediate=[e("opinion.national", 0.004), e("president.popularity", 0.01)])
action("international", "recognize_palestine", "Reconnaître l'État de Palestine", "⚐",
       "Un acte diplomatique majeur : salué dans le monde arabe et par une partie de l'opinion, critiqué par Washington.",
       cooldown=36500, confirm=True,
       immediate=[e("memory.EGY.AID", 0.06), e("memory.SAU.AID", 0.05), e("memory.DZA.AID", 0.06), e("memory.MAR.AID", 0.04),
                  e("memory.TUN.AID", 0.05), e("memory.TUR.AID", 0.04), e("memory.USA.DISAGREEMENT", -0.04), g("urban", 0.01), g("young", 0.01), g("seniors", -0.004)])
action("defense", "ukraine_aid", "Aide militaire à l'Ukraine", "⚔",
       "Canons CAESAR, missiles, munitions et formation : 3 Md€ pour aider l'Ukraine à se défendre.",
       cost=3, cooldown=365,
       requires=[cond("military.nearbyWar", min=1)], requires_text="si une guerre est en cours en Europe",
       immediate=[e("memory.UKR.MILITARY_SUPPORT", 0.15), e("memory.RUS.THREAT", -0.08), e("military.ammoStock", -0.05),
                  e("alliance.EU.EU_PARTNERSHIP", 0.02), e("sector.aerospace", 0.01), g("seniors", -0.004)])
action("defense", "european_defense", "Initiative pour une défense européenne", "★",
       "Achats communs d'armement, état-major européen, fonds de défense : vers l'autonomie stratégique.",
       cost=1, cooldown=1095,
       immediate=[e("alliance.EU.EU_PARTNERSHIP", 0.03), e("memory.USA.DISAGREEMENT", -0.01), e("sector.aerospace", 0.015)])

out = {
    "_doc": "Décisions nationales du président (panneau « Décider »). Généré par tools/datagen/national_actions_fr.py.",
    "categories": [{"id": i, "label": l, "icon": ic, "description": d} for i, l, ic, d in CATEGORIES],
    "actions": A,
}
path = os.path.join(os.path.dirname(__file__), "..", "..", "assets", "data", "countries", "FRA", "national_actions.json")
with open(path, "w", encoding="utf-8") as f:
    json.dump(out, f, ensure_ascii=False, indent=1)
    f.write("\n")
print(f"{len(A)} décisions écrites dans {os.path.normpath(path)}")
