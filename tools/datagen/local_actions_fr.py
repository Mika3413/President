"""Actions locales du président, département par département -> assets/data/countries/FRA/local_actions.json.

Garde les actions existantes et en ajoute des dizaines, rangées par rubriques. Chacune a un coût,
une durée de chantier, un délai avant de la refaire, et des conséquences : locales (popularité,
chômage, délinquance, accès aux soins, pollution, industrie), nationales (groupes sociaux,
secteurs, services publics, acteurs, énergie) et parfois des effets pervers (pollution, colère
des riverains, risque d'émeutes...).

Certaines n'ont de sens que dans certains départements (littoral, montagne, frontière, vignoble,
langue régionale, centrale nucléaire, port, aéroport, départements urbains ou ruraux, outre-mer) :
elles n'apparaissent que là. D'autres dépendent de la situation (délinquance, désert médical,
chômage) : elles sont montrées verrouillées avec leur condition.
"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
PATH = os.path.join(ROOT, "countries", "FRA", "local_actions.json")
G = "opinion.group."


def e(target, amount, days=0):
    d = {"target": target, "amount": amount}
    if days: d["days"] = days
    return d


def L(field, amount, days=0): return e("local." + field, amount, days)
def g(group, amount): return e(G + group, amount)
def a(actor, amount): return e("actor." + actor, amount)
def cond(var, mn=None, mx=None):
    d = {"variable": var}
    if mn is not None: d["min"] = mn
    if mx is not None: d["max"] = mx
    return d


NEW = []


def act(id, label, icon, category, description, cost=0.0, days=0, cooldown=365, global_cooldown=0, immediate=(), completion=(),
        requires=(), requires_text="", confirm=False):
    d = {"id": id, "label": label, "icon": icon, "category": category, "description": description, "cooldownDays": cooldown}
    if cost: d["costBillions"] = cost
    if days: d["durationDays"] = days
    if global_cooldown: d["globalCooldownDays"] = global_cooldown
    if immediate: d["immediate"] = list(immediate)
    if completion: d["onCompletion"] = list(completion)
    if requires: d["requires"] = list(requires)
    if requires_text: d["requiresText"] = requires_text
    if confirm: d["confirm"] = True
    NEW.append(d)


URBAN = [cond("scope.urbanShare", mn=0.65)]
RURAL = [cond("scope.urbanShare", mx=0.55)]
COAST = [cond("scope.coastal", mn=1)]
MOUNT = [cond("scope.mountain", mn=1)]
OVERSEAS = [cond("scope.overseas", mn=1)]

# ---- Transports -------------------------------------------------------------------------------
act("motorway_widening", "Élargir l'autoroute (2 × 3 voies)", "⇄", "transport",
    "Moins de bouchons et d'accidents, des chantiers pour le BTP ; plus de trafic et de pollution à terme.",
    0.35, 720, 1825, completion=[L("approval", 0.02), L("unemployment", -0.001), L("pollution", 0.02), e("sector.construction", 0.002), g("rural", 0.002), a("ngo_environment", -0.1)])
act("bypass", "Construire une rocade de contournement", "⇄", "transport",
    "Les camions ne traversent plus le centre-ville : moins de bruit et de pollution en ville.",
    0.12, 540, 1825, completion=[L("approval", 0.025), L("pollution", -0.01), e("sector.construction", 0.001)])
act("tram", "Créer une ligne de tramway", "⇄", "transport",
    "Un tramway structure la ville : moins de voitures, quartiers désenclavés, commerces dynamisés.",
    0.3, 900, 3650, completion=[L("approval", 0.03), L("pollution", -0.05), L("unemployment", -0.001), g("urban", 0.003), g("young", 0.002)],
    requires=URBAN)
act("rer_metro", "RER métropolitain (trains du quotidien)", "⇄", "transport",
    "Des trains toutes les quinze minutes entre la métropole et sa périphérie : un vrai changement de vie.",
    0.6, 1460, 3650, completion=[L("approval", 0.04), L("unemployment", -0.002), L("pollution", -0.04), g("middle_income", 0.003), e("quality.transport", 0.004)],
    requires=[cond("scope.populationMillions", mn=0.9)])
act("small_line", "Rouvrir une petite ligne de train", "⇄", "transport",
    "La gare rouvre : les villages ne sont plus coupés du monde.",
    0.08, 540, 1825, completion=[L("approval", 0.03), L("pollution", -0.01), g("rural", 0.004), e("quality.transport", 0.002)], requires=RURAL)
act("airport_extension", "Agrandir l'aéroport", "✈", "transport",
    "Plus de lignes, de touristes et d'emplois ; plus de bruit pour les riverains.",
    0.25, 720, 3650, completion=[L("unemployment", -0.003), L("pollution", 0.04), L("approval", 0.01), e("sector.tourism", 0.002), a("ngo_environment", -0.15), e("chain.airport_noise", 0.3)],
    requires=[cond("scope.airport", mn=1)])
act("port_modernize", "Moderniser le port", "⚓", "transport",
    "Des quais pour les plus grands porte-conteneurs : le port reprend des parts à Anvers et Rotterdam.",
    0.3, 720, 3650, completion=[L("unemployment", -0.003), L("industry", 0.006), L("approval", 0.015), e("sector.transport", 0.002)],
    requires=[cond("scope.port", mn=1)])
act("bike_plan", "Plan vélo : pistes cyclables", "♻", "transport",
    "Des pistes sûres et continues : les citadins adorent, une partie des automobilistes râle.",
    0.02, 240, 1095, completion=[L("pollution", -0.015), L("approval", 0.008), g("young", 0.002), g("rural", -0.001)])
act("free_transport_youth", "Transports gratuits pour les jeunes", "⇄", "transport",
    "Bus et trains régionaux gratuits pour les moins de 26 ans.",
    0.04, 0, 730, immediate=[L("approval", 0.02), g("young", 0.003), L("pollution", -0.005)])
act("speed_cameras", "Radars et sécurité routière", "⚠", "transport",
    "Moins de morts sur les routes ; les automobilistes y voient une pompe à fric.",
    0.0, 0, 730, immediate=[L("approval", -0.015), g("rural", -0.002), e("budget.oneOff", -0.01)])

# ---- Services publics -------------------------------------------------------------------------
act("save_maternity", "Sauver la maternité menacée", "✚", "services",
    "La maternité devait fermer faute de médecins : vous la maintenez avec des renforts.",
    0.03, 0, 1095, immediate=[L("healthAccess", 0.03), L("approval", 0.03), g("rural", 0.002)], requires=RURAL)
act("samu_helicopter", "Hélicoptère du SAMU", "✚", "services",
    "Un hélicoptère médicalisé basé sur place : l'hôpital à vingt minutes de partout.",
    0.01, 90, 1460, completion=[L("healthAccess", 0.03), L("approval", 0.01)], requires=[cond("scope.urbanShare", mx=0.65)])
act("telemedicine", "Cabines de télémédecine", "✚", "services",
    "Consulter un médecin à distance depuis la pharmacie ou la mairie.",
    0.005, 120, 730, completion=[L("healthAccess", 0.02), L("approval", 0.005)])
act("nursing_home", "Construire un EHPAD public", "✚", "services",
    "Des places en maison de retraite à prix modéré, et des emplois d'aides-soignants.",
    0.05, 540, 1825, completion=[L("approval", 0.02), L("unemployment", -0.001), g("seniors", 0.002), g("retirees", 0.002)],
    requires=[cond("scope.seniorShare", mn=0.22)], requires_text="Réservé aux départements où les seniors sont nombreux (plus de 22 %).")
act("france_services", "Maisons France Services", "✚", "services",
    "Un guichet unique pour la CAF, les impôts, la retraite ou le permis : l'État revient au village.",
    0.004, 90, 730, completion=[L("approval", 0.015), g("rural", 0.002), g("seniors", 0.001)])
act("post_offices", "Maintenir les bureaux de poste", "✉", "services",
    "Vous imposez à La Poste de garder ses bureaux ruraux ouverts.",
    0.003, 0, 1095, immediate=[L("approval", 0.01), g("rural", 0.001)])
act("high_school", "Construire un lycée", "✎", "services",
    "Les lycéens ne font plus une heure de car matin et soir.",
    0.08, 730, 1825, completion=[L("approval", 0.02), L("unemployment", -0.001), g("young", 0.002), g("adults", 0.001)])
act("small_classes", "Dédoubler les classes de CP et de CE1", "✎", "services",
    "Douze élèves par classe dans les quartiers modestes : la lecture progresse.",
    0.02, 365, 1460, completion=[L("approval", 0.015), e("quality.education", 0.001), g("low_income", 0.002)],
    requires=[cond("scope.incomeIndex", mx=0.97)], requires_text="Réservé aux départements aux revenus modestes.")
act("medical_school", "Ouvrir une faculté de médecine", "✚", "services",
    "Former les médecins sur place : ils s'installent souvent là où ils ont étudié.",
    0.12, 1095, 3650, completion=[L("healthAccess", 0.06), L("approval", 0.02), L("unemployment", -0.001)],
    requires=[cond("scope.healthAccess", mx=0.95)], requires_text="Pour les départements où l'accès aux soins est difficile.")
act("mental_health", "Centre de santé mentale", "✚", "services",
    "Psychiatres et psychologues accessibles : moins de drames, moins d'urgences saturées.",
    0.02, 365, 1460, completion=[L("healthAccess", 0.02), L("crime", -0.01), L("approval", 0.008)])
act("disability_plan", "Plan handicap et accessibilité", "✚", "services",
    "Écoles, transports et administrations accessibles à tous.",
    0.03, 365, 1460, completion=[L("approval", 0.01), g("inactive", 0.002)])

# ---- Sécurité ---------------------------------------------------------------------------------
act("cctv", "Vidéoprotection", "◉", "security",
    "Des caméras dans les rues : délinquance en baisse, débat sur les libertés.",
    0.01, 120, 1095, completion=[L("crime", -0.03), L("approval", 0.005), g("seniors", 0.002), g("young", -0.001), a("ngo_rights", -0.05)])
act("drug_raid", "Opération « place nette » contre les trafics", "⚑", "security",
    "Descentes massives dans les points de deal : effet spectaculaire, risque de tensions.",
    0.005, 0, 365, immediate=[L("crime", -0.05), L("approval", 0.02), g("seniors", 0.002), e("chain.urban_riots", 0.15, 0)],
    requires=[cond("scope.crime", mn=1.15)], requires_text="Seulement là où la délinquance dépasse nettement la moyenne.")
act("prison", "Construire une prison", "⊘", "security",
    "Des places de prison en plus : les peines sont exécutées, mais personne ne veut d'une prison à côté de chez soi.",
    0.25, 1095, 3650, completion=[L("crime", -0.04), L("approval", -0.01), L("unemployment", -0.001), e("quality.justice", 0.003)])
act("local_court", "Rouvrir un tribunal de proximité", "⚖", "security",
    "La justice au plus près : les délais d'audience fondent.",
    0.03, 365, 1825, completion=[L("crime", -0.02), L("approval", 0.015), e("quality.justice", 0.001)])
act("border_police", "Renforcer la police aux frontières", "⚑", "security",
    "Plus de contrôles aux frontières : trafics et passages illégaux reculent.",
    0.02, 90, 1095, completion=[L("crime", -0.02), L("approval", 0.01), e("demography.immigration", -0.01), g("seniors", 0.001)],
    requires=[cond("scope.border", mn=1)])
act("youth_workers", "Éducateurs de rue et maisons de quartier", "⚑", "security",
    "La prévention plutôt que la répression : moins de jeunes basculent.",
    0.02, 365, 1095, completion=[L("crime", -0.03), L("approval", 0.01), g("young", 0.002)])
act("cyber_center", "Centre régional de cybersécurité", "◉", "security",
    "Protéger hôpitaux et collectivités contre les rançongiciels.",
    0.04, 365, 1825, completion=[L("unemployment", -0.001), e("quality.security", 0.001), e("sector.tech", 0.001)])
act("firefighters", "Renforcer les pompiers et les Canadair", "⚠", "security",
    "Plus d'hommes et d'avions contre les feux de forêt.",
    0.06, 365, 1460, completion=[L("approval", 0.02), e("quality.environment", 0.001)],
    requires=[cond("scope.mediterranean", mn=1)])

# ---- Emploi et économie -----------------------------------------------------------------------
act("gigafactory", "Gigafactory de batteries", "⚒", "jobs",
    "Une usine géante de batteries : des milliers d'emplois, une région qui renaît, mais une usine qui pollue.",
    1.0, 900, 3650, 730, immediate=[L("approval", 0.01)],
    completion=[L("unemployment", -0.01), L("industry", 0.02), L("pollution", 0.03), L("approval", 0.04), e("sector.industry", 0.004), e("economy.potentialGrowth", 0.0002)],
    requires=[cond("scope.industryShare", mn=0.15)], requires_text="Réservé aux bassins industriels.", confirm=True)
act("data_center", "Data center géant", "⚒", "jobs",
    "Les géants du numérique s'installent : investissements, mais une grosse consommation d'électricité et d'eau.",
    0.3, 540, 3650, completion=[L("unemployment", -0.002), e("sector.tech", 0.003), L("pollution", 0.01), L("approval", 0.01)])
act("startup_campus", "Campus de start-up", "⚒", "jobs",
    "Un incubateur, des locaux, des investisseurs : les jeunes diplômés restent au pays.",
    0.06, 365, 1825, completion=[L("unemployment", -0.002), g("young", 0.002), e("sector.tech", 0.001)])
act("craft_school", "École d'artisanat et d'apprentissage", "⚒", "jobs",
    "Former plombiers, électriciens, boulangers : les métiers qui recrutent.",
    0.02, 365, 1460, completion=[L("unemployment", -0.002), g("young", 0.002), g("self_employed", 0.001)])
act("relocate_agency", "Délocaliser une administration ici", "⚒", "jobs",
    "Un service de l'État quitte Paris pour s'installer ici : des centaines d'emplois, des fonctionnaires mécontents de déménager.",
    0.05, 540, 3650, 365, completion=[L("unemployment", -0.002), L("approval", 0.03), g("civil_servants", -0.002)])
act("arms_plant", "Usine d'armement (économie de guerre)", "⚔", "jobs",
    "Obus et missiles produits en France : emplois, souveraineté, stocks reconstitués.",
    0.4, 720, 3650, 730, completion=[L("unemployment", -0.004), L("industry", 0.01), e("sector.aerospace", 0.003), e("military.ammoStock", 0.03)])
act("epr_reactor", "Nouveau réacteur nucléaire (EPR)", "☢", "jobs",
    "Deux réacteurs de nouvelle génération : électricité décarbonée pour soixante ans, chantier géant.",
    8.0, 3650, 7300, 3650, immediate=[L("approval", 0.01), L("unemployment", -0.003), a("ngo_environment", -0.3), a("nuclear_lobby", 0.3)],
    completion=[e("energy.capacity.new_nuclear", 3300), L("unemployment", -0.003), L("approval", 0.02)],
    requires=[cond("scope.nuclear", mn=1)], confirm=True)
act("offshore_wind", "Parc éolien en mer", "⚡", "jobs",
    "Cinquante éoliennes au large : électricité verte, mais les pêcheurs et les riverains protestent.",
    1.5, 1095, 3650, 730, completion=[e("energy.capacity.wind", 500), L("unemployment", -0.002), L("pollution", -0.02), g("self_employed", -0.002), L("approval", -0.005)],
    requires=COAST)
act("solar_farm", "Centrale solaire", "☀", "jobs",
    "Des panneaux sur d'anciennes friches et des parkings.",
    0.1, 365, 1825, completion=[e("energy.capacity.solar", 300), L("pollution", -0.01), L("unemployment", -0.001)])
act("methanizer", "Méthaniseurs agricoles", "♻", "jobs",
    "Transformer les déchets agricoles en gaz : un revenu de plus pour les éleveurs.",
    0.02, 365, 1460, completion=[L("pollution", -0.01), L("approval", 0.005), g("rural", 0.002)], requires=RURAL)
act("wasteland", "Dépolluer une friche industrielle", "♻", "jobs",
    "L'ancienne usine devient un parc d'activités.",
    0.06, 540, 1825, completion=[L("pollution", -0.03), L("industry", 0.002), L("approval", 0.01)],
    requires=[cond("scope.industryShare", mn=0.15)], requires_text="Réservé aux bassins industriels.")
act("factory_visit", "Rencontrer les salariés d'une usine menacée", "☀", "jobs",
    "Vous venez écouter les ouvriers : un geste qui compte, et une promesse qu'on vous rappellera.",
    0.0, 0, 365, immediate=[L("approval", 0.02), g("private_employees", 0.002)],
    requires=[cond("scope.unemployment", mn=0.09)], requires_text="Là où le chômage dépasse 9 %.")

# ---- Campagnes et agriculture -----------------------------------------------------------------
act("farm_emergency", "Aide d'urgence aux agriculteurs", "❦", "rural",
    "Une enveloppe pour les exploitations en difficulté après une mauvaise année.",
    0.03, 0, 730, immediate=[L("approval", 0.02), g("rural", 0.003), e("quality.agriculture", 0.001), a("farmers_union", 0.1)], requires=RURAL)
act("water_reserve", "Réserves d'eau agricoles (« bassines »)", "❦", "rural",
    "Stocker l'eau l'hiver pour irriguer l'été : les agriculteurs applaudissent, les écologistes manifestent.",
    0.02, 365, 1460, completion=[L("approval", 0.005), g("rural", 0.003), g("young", -0.002), a("farmers_union", 0.1), a("ngo_environment", -0.2), e("chain.city_demonstration", 0.2)],
    requires=RURAL)
act("local_canteens", "Cantines bio et locales", "❦", "rural",
    "Les cantines achètent aux producteurs du coin.",
    0.01, 180, 1095, completion=[L("approval", 0.01), e("sector.agrifood", 0.001), g("adults", 0.001)])
act("wolf_plan", "Plan loup : autoriser les tirs", "❦", "rural",
    "Les éleveurs pourront abattre les loups qui attaquent leurs troupeaux.",
    0.0, 0, 730, immediate=[g("rural", 0.004), e("quality.environment", -0.002), a("ngo_environment", -0.15), a("hunters", 0.15), a("farmers_union", 0.1)],
    requires=MOUNT)
act("vineyard_aid", "Plan d'aide aux viticulteurs", "❦", "rural",
    "Arrachage aidé, distillation, promotion à l'export : la filière respire.",
    0.05, 0, 1095, immediate=[L("approval", 0.02), g("rural", 0.003), e("sector.agrifood", 0.001)],
    requires=[cond("scope.wine", mn=1)])
act("village_shops", "Rouvrir des commerces de village", "❦", "rural",
    "Épicerie, café, boulangerie : le village revit.",
    0.01, 180, 1095, completion=[L("approval", 0.015), g("rural", 0.002)], requires=RURAL)

# ---- Nature, mer et montagne ------------------------------------------------------------------
act("coast_protection", "Protéger le littoral contre l'érosion", "❀", "nature",
    "Digues, enrochements, recul organisé : la mer grignote moins les côtes.",
    0.06, 540, 1825, completion=[L("approval", 0.02), e("quality.environment", 0.001)], requires=COAST)
act("ski_transition", "Reconvertir les stations de ski", "❀", "nature",
    "Moins de neige : VTT, randonnée, thermalisme, pour faire vivre la montagne toute l'année.",
    0.08, 720, 1825, completion=[L("unemployment", -0.001), e("sector.tourism", 0.001), L("approval", 0.01)], requires=MOUNT)
act("national_park", "Créer un parc national", "❀", "nature",
    "Protéger un territoire exceptionnel : tourisme vert, mais des contraintes pour les habitants.",
    0.02, 365, 7300, completion=[L("pollution", -0.02), e("sector.tourism", 0.001), L("approval", 0.005), g("rural", -0.001), a("ngo_environment", 0.15)])
act("river_cleanup", "Dépolluer la rivière", "❀", "nature",
    "Stations d'épuration modernisées : on peut de nouveau s'y baigner.",
    0.04, 540, 1825, completion=[L("pollution", -0.04), L("approval", 0.01)])
act("dikes", "Renforcer les digues contre les crues", "❀", "nature",
    "Des digues rehaussées et des zones d'expansion des crues.",
    0.08, 540, 1825, completion=[L("approval", 0.015), e("quality.environment", 0.001)])
act("reforestation", "Replanter la forêt", "❀", "nature",
    "Un million d'arbres adaptés au climat qui vient.",
    0.03, 720, 1825, completion=[L("pollution", -0.02), L("approval", 0.005)])

# ---- Culture, sport et patrimoine -------------------------------------------------------------
act("festival", "Soutenir un grand festival", "✪", "culture",
    "Musique, théâtre ou cinéma : des touristes, de la fierté locale.",
    0.005, 0, 365, immediate=[L("approval", 0.01), e("sector.tourism", 0.0005), g("young", 0.001)])
act("museum", "Ouvrir une antenne d'un grand musée", "✪", "culture",
    "Les chefs-d'œuvre nationaux exposés hors de Paris.",
    0.08, 900, 3650, completion=[L("approval", 0.02), e("sector.tourism", 0.001), L("unemployment", -0.0005)])
act("heritage", "Restaurer le patrimoine (cathédrale, château)", "✪", "culture",
    "Un monument sauvé de la ruine : fierté et touristes.",
    0.03, 540, 1825, completion=[L("approval", 0.015), e("sector.tourism", 0.0005), g("seniors", 0.001)])
act("stadium", "Construire une salle de sport et un stade", "✪", "culture",
    "Des équipements sportifs pour les clubs et les écoles.",
    0.05, 540, 1825, completion=[L("approval", 0.015), g("young", 0.002)])
act("pools", "Rénover les piscines", "✪", "culture",
    "Apprendre à nager, se rafraîchir l'été : les piscines fermées rouvrent.",
    0.02, 365, 1460, completion=[L("approval", 0.01), g("adults", 0.001)])
act("libraries", "Médiathèques ouvertes le dimanche", "✪", "culture",
    "Des lieux de culture et de calme ouverts quand les gens sont libres.",
    0.003, 0, 1095, immediate=[L("approval", 0.005), g("young", 0.001)])
act("regional_language", "Soutenir la langue régionale", "✪", "culture",
    "Écoles bilingues, signalétique, médias : une identité reconnue.",
    0.002, 0, 1460, immediate=[L("approval", 0.015)], requires=[cond("scope.regionalLanguage", mn=1)])
act("tour_de_france", "Accueillir une étape du Tour de France", "✪", "culture",
    "Des millions de téléspectateurs découvrent le département.",
    0.002, 0, 365, 30, immediate=[L("approval", 0.01), e("sector.tourism", 0.0003)])

# ---- Solidarité et logement -------------------------------------------------------------------
act("social_housing", "Construire des logements sociaux", "◐", "social",
    "Des logements abordables pour les familles modestes et les jeunes.",
    0.1, 720, 1460, completion=[L("approval", 0.02), g("low_income", 0.002), L("unemployment", -0.001), e("sector.construction", 0.001)])
act("urban_renewal", "Rénovation urbaine des quartiers", "◐", "social",
    "Démolir les barres, reconstruire, désenclaver : les quartiers changent de visage.",
    0.2, 1095, 3650, completion=[L("crime", -0.04), L("approval", 0.025), g("low_income", 0.002), e("sector.construction", 0.002)],
    requires=URBAN)
act("shelter", "Centre d'hébergement d'urgence", "◐", "social",
    "Des lits pour les sans-abri ; certains riverains protestent.",
    0.01, 90, 730, completion=[L("crime", -0.005), L("approval", -0.003), g("low_income", 0.001), a("church", 0.05)])
act("food_aid", "Aide alimentaire d'urgence", "◐", "social",
    "Un soutien aux banques alimentaires débordées.",
    0.005, 0, 365, immediate=[L("approval", 0.01), g("low_income", 0.002), a("church", 0.05)])
act("insulation", "Rénovation énergétique des logements", "◐", "social",
    "Isoler les passoires thermiques : factures en baisse, artisans au travail.",
    0.06, 365, 1095, completion=[L("pollution", -0.01), L("approval", 0.01), e("sector.construction", 0.001), g("low_income", 0.001)])
act("migrant_center", "Ouvrir un centre d'accueil de demandeurs d'asile", "◐", "social",
    "Répartir l'accueil sur le territoire : humanité saluée par les associations, colère d'une partie des habitants.",
    0.02, 120, 1095, completion=[L("approval", -0.02), L("crime", 0.005), a("ngo_rights", 0.1), g("seniors", -0.001)])

# ---- Présence ---------------------------------------------------------------------------------
act("town_debate", "Grand débat avec les habitants", "☀", "presence",
    "Trois heures de questions sans filtre, en direct.",
    0.0, 0, 365, 14, immediate=[L("approval", 0.02), e("opinion.national", 0.001)])
act("hospital_visit", "Visite d'un hôpital", "☀", "presence",
    "Vous passez une nuit aux urgences avec les soignants.",
    0.0, 0, 180, 7, immediate=[L("approval", 0.01), g("seniors", 0.001)])
act("mayors_elysee", "Recevoir les maires du département à l'Élysée", "☀", "presence",
    "Les élus locaux reçus et écoutés : ils s'en souviendront.",
    0.0, 0, 365, 7, immediate=[L("approval", 0.015)])
act("commemoration", "Cérémonie de commémoration", "☀", "presence",
    "Un hommage à l'histoire locale : résistants, soldats, victimes.",
    0.0, 0, 365, 14, immediate=[L("approval", 0.01), g("seniors", 0.002)])

# ---- Outre-mer --------------------------------------------------------------------------------
act("overseas_water", "Plan eau potable outre-mer", "✚", "services",
    "Des canalisations neuves : fini les coupures d'eau à répétition.",
    0.1, 540, 1825, completion=[L("healthAccess", 0.04), L("approval", 0.03)], requires=OVERSEAS)
act("overseas_prices", "Bouclier qualité-prix contre la vie chère", "◐", "social",
    "Des prix plafonnés sur les produits de première nécessité.",
    0.02, 0, 730, immediate=[L("approval", 0.03), g("low_income", 0.001)], requires=OVERSEAS)
act("overseas_security", "Renforts de gendarmerie outre-mer", "⚑", "security",
    "Escadrons de gendarmerie et lutte contre les trafics.",
    0.02, 60, 730, completion=[L("crime", -0.05), L("approval", 0.01)], requires=OVERSEAS)

CATEGORIES = [
    {"id": "services", "label": "Services publics", "icon": "✚"},
    {"id": "jobs", "label": "Emploi et industrie", "icon": "⚒"},
    {"id": "security", "label": "Sécurité", "icon": "⚑"},
    {"id": "transport", "label": "Transports", "icon": "⇄"},
    {"id": "social", "label": "Solidarité et logement", "icon": "◐"},
    {"id": "rural", "label": "Campagnes", "icon": "❦"},
    {"id": "nature", "label": "Nature, mer, montagne", "icon": "❀"},
    {"id": "culture", "label": "Culture et sport", "icon": "✪"},
    {"id": "living", "label": "Cadre de vie", "icon": "♻"},
    {"id": "presence", "label": "Présence", "icon": "☀"},
]

data = json.load(open(PATH, encoding="utf-8"))
new_ids = {x["id"] for x in NEW}
assert len(new_ids) == len(NEW)
kept = [x for x in data["actions"] if x["id"] not in new_ids]
data["actions"] = kept + NEW
data["categories"] = CATEGORIES
cats = {c["id"] for c in CATEGORIES}
for x in data["actions"]: assert x["category"] in cats, x["id"]
raw = open(PATH, encoding="utf-8").read()
with open(PATH, "w", encoding="utf-8") as f:
    json.dump(data, f, ensure_ascii=False, indent=1 if raw.startswith('{\n "') else 2)
    f.write("\n")
print(f"{len(data['actions'])} actions locales ({len(NEW)} nouvelles), {len(CATEGORIES)} rubriques")
