"""Encore plus d'événements (France) -> assets/data/events/more.json et dialogue/fr/more.json.

Vie quotidienne, territoires (littoral, montagne, villes, campagnes), économie, société, sécurité,
numérique, santé, environnement. Chaque événement propose plusieurs réponses aux conséquences
contrastées ; certaines entraînent des suites (« chain.<id> »). Les événements de département
ne surviennent que là où ils ont un sens (littoral, montagne, frontière, villes...).
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dialogue_fr_lib import V, letter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
G = "opinion.group."


def E(target, amount=0.0, days=0.0, delay=0.0):
    d = {"target": target, "amount": amount}
    if days: d["days"] = days
    if delay: d["delayDays"] = delay
    return d


def chain(event, probability, delay): return E("chain." + event, probability, delay=delay)
def opt(id, label, hint, effects, outcome="NEUTRAL"): return {"id": id, "label": label, "hint": hint, "effects": list(effects), "outcome": outcome}
def mod(var, frm, to, fa, fb): return {"variable": var, "from": frm, "to": to, "factorAtFrom": fa, "factorAtTo": fb}
def cond(var, mn=None, mx=None):
    d = {"variable": var}
    if mn is not None: d["min"] = mn
    if mx is not None: d["max"] = mx
    return d


EVENTS, TEMPLATES = [], []


def event(id, category, headline, text, prob, cooldown, urgency, subjects, context, problem, request, sender, default, options,
          ministry=None, scope="NATIONAL", modifiers=(), conditions=(), days=4, scope_cooldown=0, immediate=()):
    options = list(options)
    # Une quatrième voie, toujours possible : y aller soi-même (territoire) ou consulter (national).
    if scope == "DEPARTMENT":
        options.append(opt("visit", "Vous rendre sur place", "Geste fort ; une journée d'agenda",
                           [E("scope.approval", 0.02), E("opinion.national", 0.001)], "PARTIAL"))
    else:
        options.append(opt("consult", "Lancer une concertation avant de trancher", "Apaisement ; décision repoussée",
                           [E("government.parliamentSupport", 0.005), E("opinion.national", -0.001)], "POSTPONED"))
    m = {"template": id, "sender": sender, "responseDays": days, "defaultOption": default, "options": options}
    if ministry: m["ministry"] = ministry
    ev = {"id": id, "category": category, "scope": scope, "baseDailyProbability": prob, "cooldownDays": cooldown, "urgency": urgency,
          "headline": headline, "notificationText": text, "modifiers": list(modifiers), "immediateEffects": list(immediate), "message": m}
    if conditions: ev["conditions"] = list(conditions)
    if scope_cooldown: ev["scopeCooldownDays"] = scope_cooldown
    EVENTS.append(ev)
    TEMPLATES.append(letter(id, [V(s) for s in subjects], [V(s) for s in context], [V(s) for s in problem], [V(s) for s in request]))


def local(id, category, headline, text, prob, subjects, context, problem, request, default, options, conditions=(), sender="PREFECT", urgency="IMPORTANT", immediate=()):
    event(id, category, headline, text, prob, 30, urgency, subjects, context, problem, request, sender, default, options,
          scope="DEPARTMENT", conditions=conditions, scope_cooldown=720, immediate=immediate)


D = 0.00012   # probabilité de base, par département et par jour
N = 0.0007    # probabilité de base nationale, par jour

# ================================ Territoires ================================
local("coast_erosion_storm", "DISASTER", "Tempête {departmentIn} : la mer engloutit des maisons du littoral", "Des habitations menacées de s'effondrer.",
 D, ["Le littoral attaqué par la mer", "Érosion côtière : des maisons condamnées", "Tempête sur la côte"],
 ["La dernière tempête a fait reculer la dune de plusieurs mètres {departmentIn}.", "Un immeuble en front de mer a dû être évacué en pleine nuit."],
 ["Les propriétaires réclament d'être indemnisés.", "Les scientifiques préviennent : avec la montée des eaux, ce ne sera pas le dernier."],
 ["Je vous propose d'indemniser et de relocaliser, de bâtir des digues, ou de laisser faire."],
 "relocate", [
  opt("relocate", "Indemniser et organiser le recul", "Coût : 150 M€ ; solution durable", [E("budget.oneOff", 0.15), E("scope.approval", 0.02), E("quality.environment", 0.002)], "ACCEPTED"),
  opt("dikes", "Construire des digues", "Coût : 250 M€ ; répit de quelques décennies", [E("budget.oneOff", 0.25), E("scope.approval", 0.03), E("quality.environment", -0.002)], "PARTIAL"),
  opt("none", "Laisser les assurances jouer", "Aucun coût ; colère locale", [E("scope.approval", -0.04)], "REFUSED")],
 conditions=[cond("scope.coastal", mn=1)])

local("avalanche_resort", "DISASTER", "Avalanche meurtrière dans une station {departmentIn}", "Des skieurs ensevelis hors-piste.",
 D, ["Avalanche en station", "Drame en montagne", "Une avalanche frappe une station"],
 ["Une avalanche a emporté plusieurs skieurs {departmentIn}.", "Les secours en montagne ont travaillé toute la nuit."],
 ["La sécurité des stations est mise en cause.", "Les professionnels craignent pour la saison."],
 ["Je vous propose de renforcer les secours et la prévention, de fermer temporairement les zones à risque, ou de laisser les stations gérer."],
 "secours", [
  opt("secours", "Renforcer les secours en montagne", "Coût : 30 M€", [E("budget.oneOff", 0.03), E("scope.approval", 0.02)], "ACCEPTED"),
  opt("close", "Fermer les zones à risque", "Sécurité ; saison amputée", [E("sector.tourism", -0.002), E("scope.approval", -0.01)], "PARTIAL"),
  opt("none", "Laisser les stations gérer", "Aucun coût", [E("scope.approval", -0.02)], "REFUSED")],
 conditions=[cond("scope.mountain", mn=1), cond("season.month", mx=4)])

local("border_traffic_jam", "TERRITORY", "Contrôles aux frontières : des kilomètres de bouchons {departmentIn}", "Les frontaliers excédés.",
 D, ["Bouchons à la frontière", "Les frontaliers en colère", "Contrôles frontaliers : la paralysie"],
 ["Les contrôles renforcés provoquent des heures d'attente aux postes-frontières {departmentIn}.", "Des dizaines de milliers de frontaliers passent chaque jour."],
 ["Les entreprises de transport perdent des millions.", "Le pays voisin proteste."],
 ["Je vous propose d'alléger les contrôles, de les maintenir avec plus d'agents, ou de les durcir."],
 "agents", [
  opt("ease", "Alléger les contrôles", "Fluidité ; moins de sécurité", [E("scope.approval", 0.02), E("quality.security", -0.002), E("demography.immigration", 0.01)], "ACCEPTED"),
  opt("agents", "Maintenir avec plus d'agents", "Coût : 20 M€", [E("budget.oneOff", 0.02), E("scope.approval", 0.01)], "PARTIAL"),
  opt("harden", "Durcir les contrôles", "Fermeté ; économie locale pénalisée", [E("scope.approval", -0.03), E("scope.unemployment", 0.002), E(G + "seniors", 0.002)], "REFUSED")],
 conditions=[cond("scope.border", mn=1)])

local("nuclear_plant_leak", "ENERGY", "Incident à la centrale nucléaire {departmentOf}", "Une fuite radioactive mineure inquiète les riverains.",
 D, ["Incident nucléaire", "Fuite à la centrale", "La centrale en question"],
 ["Une fuite d'eau légèrement radioactive a été détectée à la centrale {departmentOf}.", "L'Autorité de sûreté classe l'incident au niveau 1 sur 7."],
 ["Les riverains s'inquiètent, les antinucléaires manifestent.", "Arrêter le réacteur coûterait cher en électricité."],
 ["Je vous propose d'arrêter le réacteur pour inspection, de poursuivre sous surveillance, ou de communiquer largement."],
 "monitor", [
  opt("shutdown", "Arrêter le réacteur pour inspection", "Sécurité ; électricité plus chère", [E("energy.capacity.new_nuclear", -900), E("energy.capacity.new_nuclear", 900, delay=120), E("scope.approval", 0.02), E("economy.inflation", 0.0003)], "ACCEPTED"),
  opt("monitor", "Poursuivre sous surveillance renforcée", "Aucun coût ; inquiétude", [E("scope.approval", -0.01)], "PARTIAL"),
  opt("communicate", "Tout publier en transparence", "Confiance ; débat national", [E("scope.approval", 0.01), E(G + "young", -0.002)], "REFUSED")],
 conditions=[cond("scope.nuclear", mn=1)])

local("wine_hail", "DISASTER", "Grêle dévastatrice sur les vignes {departmentOf}", "Des récoltes anéanties en dix minutes.",
 D, ["La grêle ravage le vignoble", "Vignerons sinistrés", "Récolte perdue"],
 ["Un orage de grêle a détruit une grande partie des vignes {departmentOf}.", "Certains vignerons ont tout perdu."],
 ["Beaucoup ne sont pas assurés.", "La filière était déjà fragile."],
 ["Je vous propose un fonds d'urgence, des prêts à taux zéro, ou de laisser jouer les assurances."],
 "fund", [
  opt("fund", "Fonds d'urgence pour les vignerons", "Coût : 80 M€", [E("budget.oneOff", 0.08), E("scope.approval", 0.03), E(G + "rural", 0.003)], "ACCEPTED"),
  opt("loans", "Prêts à taux zéro", "Coût : 20 M€", [E("budget.oneOff", 0.02), E("scope.approval", 0.01)], "PARTIAL"),
  opt("none", "Laisser les assurances jouer", "Aucun coût ; faillites", [E("scope.approval", -0.03), E("scope.unemployment", 0.001)], "REFUSED")],
 conditions=[cond("scope.wine", mn=1), cond("season.month", mn=4, mx=9)])

local("medical_desert_doctors", "TERRITORY", "Désert médical {departmentIn} : le dernier médecin du canton part à la retraite", "Des milliers d'habitants sans médecin traitant.",
 D * 1.5, ["Plus de médecin", "Désert médical", "Le dernier médecin s'en va"],
 ["Le dernier généraliste du canton part à la retraite sans successeur.", "Les patients doivent faire une heure de route pour consulter."],
 ["Les maires ont tout essayé : maisons de santé, primes, logements gratuits.", "Les urgences de l'hôpital le plus proche saturent."],
 ["Je vous propose d'obliger les jeunes médecins à s'installer ici deux ans, de financer un centre de santé avec des médecins salariés, ou de miser sur la télémédecine."],
 "center", [
  opt("oblige", "Obliger les jeunes médecins à s'y installer", "Efficace ; colère des médecins", [E("scope.healthAccess", 0.06), E("scope.approval", 0.03), E(G + "self_employed", -0.003), E("actor.union_reformist", -0.05)], "ACCEPTED"),
  opt("center", "Centre de santé avec médecins salariés", "Coût : 15 M€", [E("budget.oneOff", 0.015), E("scope.healthAccess", 0.04), E("scope.approval", 0.02)], "PARTIAL"),
  opt("tele", "Miser sur la télémédecine", "Coût : 3 M€ ; solution partielle", [E("budget.oneOff", 0.003), E("scope.healthAccess", 0.015)], "REFUSED")],
 conditions=[cond("scope.healthAccess", mx=0.95)], sender="DEPARTMENT_PRESIDENT")

local("drug_violence", "SECURITY", "Fusillade liée au trafic de drogue {departmentIn}", "Un adolescent tué par une balle perdue.",
 D * 1.5, ["Narcotrafic : un mort", "Fusillade en plein jour", "Les trafiquants font la loi"],
 ["Une fusillade entre bandes rivales a fait un mort {departmentIn}, un adolescent qui passait par là.", "Le quartier est sous le choc."],
 ["Les habitants n'osent plus sortir le soir.", "Les élus réclament l'armée, les associations la prévention."],
 ["Je vous propose une opération massive de police, un plan prévention et éducation, ou d'envoyer l'armée."],
 "police", [
  opt("police", "Opération policière massive", "Coût : 20 M€ ; effet rapide", [E("budget.oneOff", 0.02), E("scope.crime", -0.06), E("scope.approval", 0.02), E(G + "seniors", 0.003), chain("urban_riots", 0.15, 7)], "ACCEPTED"),
  opt("prevention", "Plan prévention, éducateurs, emplois", "Coût : 40 M€ ; effet lent", [E("budget.oneOff", 0.04), E("scope.crime", -0.03, days=365), E("scope.approval", 0.01), E(G + "young", 0.002)], "PARTIAL"),
  opt("army", "Envoyer l'armée", "Spectaculaire ; libertés en question", [E("scope.crime", -0.07), E("scope.approval", 0.01), E("liberty", -2), E("actor.ngo_rights", -0.2), E(G + "young", -0.004)], "REFUSED")],
 conditions=[cond("scope.crime", mn=1.1), cond("scope.urbanShare", mn=0.6)])

local("factory_relocation", "ECONOMY", "Délocalisation : l'usine {departmentOf} part en Europe de l'Est", "800 emplois menacés.",
 D * 1.5, ["Une usine délocalisée", "800 emplois menacés", "L'usine ferme ses portes"],
 ["Le groupe propriétaire annonce le transfert de l'usine {departmentOf} vers la Roumanie.", "800 salariés et autant de sous-traitants sont concernés."],
 ["Le bassin d'emploi est déjà sinistré.", "Les salariés occupent le site."],
 ["Je vous propose de trouver un repreneur avec des aides publiques, de nationaliser temporairement, ou d'accompagner les salariés."],
 "buyer", [
  opt("buyer", "Chercher un repreneur avec aides publiques", "Coût : 60 M€ ; succès incertain", [E("budget.oneOff", 0.06), E("scope.unemployment", -0.002), E("scope.approval", 0.02)], "ACCEPTED"),
  opt("nationalize", "Nationaliser temporairement", "Coût : 200 M€ ; emplois sauvés", [E("budget.oneOff", 0.2), E("scope.approval", 0.04), E("economy.businessConfidence", -0.005), E("actor.employers_big", -0.1)], "PARTIAL"),
  opt("support", "Accompagner les salariés (formation, primes)", "Coût : 20 M€ ; usine perdue", [E("budget.oneOff", 0.02), E("scope.unemployment", 0.004), E("scope.industry", -0.005), E("scope.approval", -0.03)], "REFUSED")],
 conditions=[cond("scope.industryShare", mn=0.14)])

local("tourism_overcrowding", "TERRITORY", "Surtourisme {departmentIn} : les habitants n'en peuvent plus", "Plages et villages pris d'assaut.",
 D, ["Trop de touristes", "Le surtourisme exaspère", "Les habitants saturent"],
 ["Les locations de courte durée ont fait flamber les loyers {departmentIn}.", "Les habitants ne trouvent plus à se loger."],
 ["Le tourisme fait vivre la région.", "Les élus locaux sont divisés."],
 ["Je vous propose de limiter les locations touristiques, de taxer les touristes, ou de ne rien changer."],
 "limit", [
  opt("limit", "Limiter les locations touristiques", "Logements libérés ; secteur mécontent", [E("scope.approval", 0.03), E("sector.tourism", -0.001), E(G + "young", 0.002)], "ACCEPTED"),
  opt("tax", "Taxe de séjour renforcée", "Recettes ; touristes un peu moins nombreux", [E("budget.oneOff", -0.02), E("scope.approval", 0.015), E("sector.tourism", -0.0005)], "PARTIAL"),
  opt("none", "Ne rien changer", "Économie touristique préservée", [E("scope.approval", -0.02)], "REFUSED")],
 conditions=[cond("scope.tourist", mn=1), cond("season.month", mn=6, mx=9)])

local("regional_identity", "POLITICS", "Revendications autonomistes {departmentIn}", "Les élus réclament plus de pouvoirs.",
 D, ["Autonomie : les élus s'impatientent", "Identité régionale", "Plus de pouvoirs pour le territoire"],
 ["Une majorité d'élus {departmentOf} réclame un statut d'autonomie et la reconnaissance de la langue régionale.", "Une manifestation a réuni des milliers de personnes."],
 ["Accorder trop créerait un précédent.", "Refuser pourrait radicaliser le mouvement."],
 ["Je vous propose d'ouvrir des négociations, de reconnaître la langue sans autonomie, ou de refuser fermement."],
 "language", [
  opt("negotiate", "Ouvrir des négociations sur l'autonomie", "Apaisement ; critiques des jacobins", [E("scope.approval", 0.05), E(G + "seniors", -0.002), E("government.parliamentSupport", -0.01)], "ACCEPTED"),
  opt("language", "Reconnaître la langue, sans autonomie", "Compromis", [E("scope.approval", 0.02)], "PARTIAL"),
  opt("refuse", "Refuser fermement", "Unité affirmée ; tensions locales", [E("scope.approval", -0.05), chain("corsica_tensions", 0.2, 30)], "REFUSED")],
 conditions=[cond("scope.regionalLanguage", mn=1)], sender="REGION_PRESIDENT")

local("overseas_water_cuts", "TERRITORY", "Coupures d'eau à répétition {departmentIn}", "Des familles privées d'eau courante plusieurs jours par semaine.",
 D * 2, ["L'eau ne coule plus", "Crise de l'eau outre-mer", "Coupures d'eau"],
 ["Les canalisations vétustes fuient de partout {departmentIn}.", "Des quartiers entiers n'ont l'eau que quelques heures par jour."],
 ["La population se sent abandonnée par la métropole.", "Des barrages routiers sont apparus."],
 ["Je vous propose un plan d'urgence de l'État, de prendre la gestion de l'eau en main, ou de laisser les collectivités agir."],
 "plan", [
  opt("plan", "Plan d'urgence de l'État", "Coût : 120 M€", [E("budget.oneOff", 0.12), E("scope.approval", 0.05), E("scope.healthAccess", 0.02)], "ACCEPTED"),
  opt("takeover", "Reprendre la gestion de l'eau", "Coût : 60 M€ ; élus locaux vexés", [E("budget.oneOff", 0.06), E("scope.approval", 0.03)], "PARTIAL"),
  opt("local", "Laisser les collectivités agir", "Aucun coût ; colère", [E("scope.approval", -0.05), chain("cost_of_living_overseas", 0.3, 20)], "REFUSED")],
 conditions=[cond("scope.overseas", mn=1)])

# ================================ National ================================
def nat(id, category, headline, text, subjects, context, problem, request, default, options, ministry=None, sender="MINISTER", prob=N, cooldown=720,
        urgency="IMPORTANT", modifiers=(), conditions=(), immediate=()):
    event(id, category, headline, text, prob, cooldown, urgency, subjects, context, problem, request, sender, default, options,
          ministry=ministry, modifiers=modifiers, conditions=conditions, immediate=immediate)


nat("ai_cheating_bac", "POLITICS", "Bac : des milliers de copies rédigées par l'intelligence artificielle", "Le diplôme perd de sa valeur.",
 ["Triche massive au bac", "L'IA et le baccalauréat", "Le bac discrédité"],
 ["Des milliers de candidats ont utilisé des outils d'intelligence artificielle pendant les épreuves.", "Les correcteurs sont démunis."],
 ["Les parents et les élèves honnêtes se sentent floués.", "Le ministère est accusé d'avoir sous-estimé le problème."],
 ["Je vous propose de refaire passer les épreuves, de réformer le bac vers l'oral, ou de minimiser l'affaire."],
 "reform", [
  opt("retake", "Faire repasser les épreuves", "Coût : 100 M€ ; colère des élèves", [E("budget.oneOff", 0.1), E(G + "young", -0.005), E("quality.education", 0.002)], "ACCEPTED"),
  opt("reform", "Réformer le bac vers plus d'oral", "Coût : 50 M€ ; réforme de fond", [E("budget.oneOff", 0.05), E("quality.education", 0.004), E(G + "adults", 0.002)], "PARTIAL"),
  opt("minimize", "Minimiser l'affaire", "Aucun coût ; diplôme dévalorisé", [E("quality.education", -0.005), E(G + "adults", -0.003)], "REFUSED")],
 ministry="education", modifiers=[mod("season.month", 6, 7, 3.0, 3.0)])

nat("heat_schools", "DISASTER", "Canicule : des écoles transformées en fournaises", "Les parents gardent leurs enfants à la maison.",
 ["Écoles surchauffées", "Canicule à l'école", "Classes à 38 degrés"],
 ["Les températures dépassent 35 degrés dans des centaines de classes.", "Des enseignants ont fait des malaises."],
 ["Les bâtiments scolaires n'ont jamais été adaptés.", "Les parents sont furieux."],
 ["Je vous propose un grand plan de rénovation des écoles, de fermer les écoles pendant les pics, ou d'acheter des ventilateurs."],
 "plan", [
  opt("plan", "Plan de rénovation thermique des écoles", "Coût : 2 Md€ sur plusieurs années", [E("budget.oneOff", 2.0), E("quality.education", 0.006), E(G + "adults", 0.004), E("sector.construction", 0.002)], "ACCEPTED"),
  opt("close", "Fermer pendant les pics de chaleur", "Aucun coût ; parents désorganisés", [E(G + "adults", -0.004), E("economy.output", -0.0005)], "PARTIAL"),
  opt("fans", "Acheter des ventilateurs", "Coût : 50 M€ ; pansement", [E("budget.oneOff", 0.05), E(G + "adults", -0.001)], "REFUSED")],
 ministry="education", modifiers=[mod("season.month", 6, 8, 3.0, 3.0)], conditions=[cond("season.month", mn=5, mx=9)])

nat("train_chaos", "TERRITORY", "Chaos ferroviaire : un été de trains annulés", "Pannes, grèves, retards : les vacances gâchées.",
 ["Trains annulés", "La SNCF en crise", "Un été de galère ferroviaire"],
 ["Pannes de matériel, manque de conducteurs, grèves locales : des milliers de trains annulés.", "Les vacanciers sont bloqués dans les gares."],
 ["Le réseau souffre de décennies de sous-investissement.", "Les usagers réclament des indemnisations."],
 ["Je vous propose un plan d'investissement massif dans le rail, des indemnisations exceptionnelles, ou de changer la direction de la SNCF."],
 "invest", [
  opt("invest", "Plan d'investissement dans le rail", "Coût : 3 Md€ ; effets dans plusieurs années", [E("budget.oneOff", 3.0), E("quality.transport", 0.01, days=730), E(G + "middle_income", 0.002)], "ACCEPTED"),
  opt("refund", "Indemniser les voyageurs", "Coût : 200 M€", [E("budget.oneOff", 0.2), E(G + "middle_income", 0.002)], "PARTIAL"),
  opt("ceo", "Changer la direction de la SNCF", "Aucun coût ; geste symbolique", [E("opinion.national", 0.001), E("quality.transport", -0.002)], "REFUSED")],
 ministry="transport", modifiers=[mod("quality.transport", 0.4, 0.7, 2.5, 0.5)])

nat("bank_scam_wave", "SECURITY", "Arnaques bancaires : des milliers de retraités dépouillés", "Faux conseillers, SMS piégés : l'épargne envolée.",
 ["Vague d'escroqueries", "Les retraités arnaqués", "Fraude bancaire massive"],
 ["Des réseaux d'escrocs se font passer pour des conseillers bancaires.", "Des retraités ont perdu les économies d'une vie."],
 ["Les banques refusent souvent de rembourser.", "Les plaintes s'accumulent sans suite."],
 ["Je vous propose d'obliger les banques à rembourser, de créer un parquet spécialisé, ou une campagne de prévention."],
 "banks", [
  opt("banks", "Obliger les banques à rembourser", "Victimes protégées ; banques furieuses", [E(G + "retirees", 0.006), E(G + "seniors", 0.004), E("sector.banking", -0.002), E("actor.banking_lobby", -0.15)], "ACCEPTED"),
  opt("parquet", "Créer un parquet spécialisé", "Coût : 30 M€", [E("budget.oneOff", 0.03), E("quality.justice", 0.003), E(G + "retirees", 0.002)], "PARTIAL"),
  opt("campaign", "Campagne de prévention", "Coût : 5 M€", [E("budget.oneOff", 0.005)], "REFUSED")],
 ministry="interior")

nat("social_media_harassment", "POLITICS", "Harcèlement en ligne : une adolescente met fin à ses jours", "Le pays bouleversé.",
 ["Harcèlement sur les réseaux", "Un drame qui bouleverse le pays", "Réseaux sociaux mis en cause"],
 ["Une collégienne harcelée pendant des mois sur les réseaux sociaux s'est donné la mort.", "Ses parents interpellent le président."],
 ["Les plateformes répondent lentement aux signalements.", "Les associations demandent une majorité numérique à 15 ans."],
 ["Je vous propose d'interdire les réseaux sociaux aux moins de 15 ans, de sanctionner lourdement les plateformes, ou un plan d'éducation."],
 "sanction", [
  opt("ban", "Interdire les réseaux aux moins de 15 ans", "Protection ; débat sur les libertés", [E(G + "adults", 0.006), E(G + "young", -0.004), E("liberty", -1), E("actor.tech_lobby", -0.2)], "ACCEPTED"),
  opt("sanction", "Amendes lourdes pour les plateformes", "Bruxelles doit suivre", [E(G + "adults", 0.004), E("actor.tech_lobby", -0.15), E("alliance.EU.NEGOTIATION_GOODWILL", 0.005)], "PARTIAL"),
  opt("education", "Plan d'éducation au numérique", "Coût : 40 M€ ; effet lent", [E("budget.oneOff", 0.04), E("quality.education", 0.002)], "REFUSED")],
 ministry="education")

nat("pension_fund_scandal", "ECONOMY", "Un fonds de retraite complémentaire au bord de la faillite", "Des centaines de milliers de pensions menacées.",
 ["Retraites complémentaires en danger", "Un fonds de pension en faillite", "Les retraités inquiets"],
 ["Un grand fonds de retraite complémentaire a perdu des milliards sur des placements risqués.", "Les pensions pourraient baisser de 20 %."],
 ["Les retraités concernés sont paniqués.", "Renflouer coûterait cher et créerait un précédent."],
 ["Je vous propose de renflouer le fonds, de garantir seulement les petites pensions, ou de laisser faire."],
 "small", [
  opt("bailout", "Renflouer le fonds", "Coût : 4 Md€", [E("budget.oneOff", 4.0), E(G + "retirees", 0.008)], "ACCEPTED"),
  opt("small", "Garantir les petites pensions", "Coût : 1,2 Md€", [E("budget.oneOff", 1.2), E(G + "retirees", 0.003), E(G + "low_income", 0.002)], "PARTIAL"),
  opt("none", "Laisser faire", "Aucun coût ; colère des retraités", [E(G + "retirees", -0.012), E("unrest.pensions", 0.5)], "REFUSED")],
 ministry="economy", conditions=[cond("economy.growth", mx=0.01)])

nat("housing_crash_builders", "ECONOMY", "Le bâtiment s'effondre : chantiers à l'arrêt, faillites en série", "100 000 emplois menacés.",
 ["Crise du bâtiment", "Le logement neuf s'effondre", "Les promoteurs en faillite"],
 ["Les ventes de logements neufs ont chuté de moitié en un an.", "Les promoteurs annulent leurs projets."],
 ["Les artisans et les ouvriers du bâtiment perdent leur emploi.", "La pénurie de logements va s'aggraver."],
 ["Je vous propose un plan de relance du logement, un prêt à taux zéro élargi, ou de laisser le marché se purger."],
 "ptz", [
  opt("relaunch", "Plan de relance du logement", "Coût : 5 Md€", [E("budget.oneOff", 5.0), E("sector.construction", 0.01), E("economy.unemployment", -0.002)], "ACCEPTED"),
  opt("ptz", "Prêt à taux zéro élargi", "Coût : 1,5 Md€", [E("budget.oneOff", 1.5), E("sector.construction", 0.004), E(G + "young", 0.002)], "PARTIAL"),
  opt("none", "Laisser le marché se purger", "Aucun coût ; faillites", [E("sector.construction", -0.006), E("economy.unemployment", 0.002)], "REFUSED")],
 ministry="economy", conditions=[cond("society.housePrices", mx=0.95)])

nat("rent_explosion", "TERRITORY", "Loyers : les étudiants dorment dans leur voiture", "La crise du logement étudiant fait scandale.",
 ["Étudiants sans logement", "La crise du logement étudiant", "Des étudiants à la rue"],
 ["Les loyers ont tellement augmenté que des étudiants dorment dans leur voiture ou chez des amis.", "Les résidences universitaires refusent des milliers de demandes."],
 ["Les syndicats étudiants appellent à manifester.", "Les propriétaires réclament des avantages fiscaux."],
 ["Je vous propose de construire 50 000 logements étudiants, d'encadrer les loyers, ou d'augmenter les aides au logement."],
 "build", [
  opt("build", "Construire 50 000 logements étudiants", "Coût : 3 Md€", [E("budget.oneOff", 3.0), E(G + "young", 0.008), E("sector.construction", 0.003)], "ACCEPTED"),
  opt("cap", "Encadrer les loyers dans les villes universitaires", "Aucun coût ; propriétaires mécontents", [E(G + "young", 0.005), E(G + "high_income", -0.003), E("sector.construction", -0.002)], "PARTIAL"),
  opt("aid", "Augmenter les aides au logement", "Coût : 800 M€ ; loyers tirés vers le haut", [E("budget.oneOff", 0.8), E(G + "young", 0.003)], "REFUSED")],
 ministry="labour", conditions=[cond("society.realRent", mn=1.06)])

nat("baby_bust", "POLITICS", "Natalité : la France fait de moins en moins d'enfants", "Le plus bas niveau depuis la guerre.",
 ["La natalité s'effondre", "Hiver démographique", "Moins de naissances"],
 ["Le nombre de naissances est au plus bas depuis 1945.", "Les maternités ferment faute de naissances."],
 ["À long terme, le financement des retraites est en jeu.", "Les jeunes couples invoquent le logement et l'inquiétude pour l'avenir."],
 ["Je vous propose un grand plan famille, un congé parental mieux payé, ou de ne rien faire."],
 "leave", [
  opt("family", "Grand plan famille (crèches, allocations)", "Coût : 4 Md€ par an", [E("budget.oneOff", 4.0), E(G + "adults", 0.006), E("actor.church", 0.1)], "ACCEPTED"),
  opt("leave", "Congé parental mieux payé", "Coût : 1,5 Md€ par an", [E("budget.oneOff", 1.5), E(G + "adults", 0.004), E(G + "young", 0.002)], "PARTIAL"),
  opt("none", "Ne rien faire", "Aucun coût", [E(G + "adults", -0.002)], "REFUSED")],
 ministry="labour", conditions=[cond("society.fertility", mx=1.55)], cooldown=1460)

nat("undeclared_work_raid", "ECONOMY", "Travail au noir : un vaste réseau démantelé dans le BTP", "Des milliers de travailleurs non déclarés.",
 ["Travail dissimulé", "Réseau de travail au noir", "Fraude sociale massive"],
 ["L'inspection du travail a démantelé un réseau employant des milliers de travailleurs non déclarés sur des chantiers.", "Des grands groupes sous-traitaient sans contrôler."],
 ["La fraude coûte des milliards à la Sécurité sociale.", "Les entreprises honnêtes subissent une concurrence déloyale."],
 ["Je vous propose de multiplier les contrôles, d'alléger les charges sur les bas salaires, ou de responsabiliser les donneurs d'ordre."],
 "controls", [
  opt("controls", "Multiplier les contrôles", "Coût : 100 M€ ; recettes récupérées", [E("budget.oneOff", -0.4), E("actor.employers_small", -0.05), E("quality.justice", 0.002)], "ACCEPTED"),
  opt("charges", "Alléger les charges sur les bas salaires", "Coût : 2 Md€ ; travail déclaré plus intéressant", [E("budget.oneOff", 2.0), E("economy.unemployment", -0.001), E("actor.employers_small", 0.1)], "PARTIAL"),
  opt("contractors", "Responsabiliser les donneurs d'ordre", "Aucun coût ; patronat mécontent", [E("actor.employers_big", -0.1), E("budget.oneOff", -0.2)], "REFUSED")],
 ministry="labour", conditions=[cond("society.informal", mn=0.115)])

nat("savings_record", "ECONOMY", "Épargne record : 6 000 milliards dorment sur les comptes", "Les Français épargnent au lieu de consommer.",
 ["Épargne record", "Les Français thésaurisent", "L'épargne déborde"],
 ["Le taux d'épargne atteint un record : par peur de l'avenir, les ménages mettent de côté.", "Les commerces et le bâtiment souffrent."],
 ["Mobiliser cette épargne pourrait financer l'industrie et la transition.", "Toucher à l'épargne populaire est explosif."],
 ["Je vous propose un grand emprunt national pour l'industrie, de taxer l'épargne qui dort, ou de rassurer pour relancer la consommation."],
 "reassure", [
  opt("loan", "Grand emprunt national pour l'industrie", "Investissement ; dette en plus", [E("economy.potentialGrowth", 0.0005), E("sector.industry", 0.004), E("budget.oneOff", 1.0)], "ACCEPTED"),
  opt("tax", "Taxer l'épargne qui dort", "Recettes ; colère des épargnants", [E("budget.oneOff", -2.0), E(G + "retirees", -0.006), E(G + "middle_income", -0.004)], "PARTIAL"),
  opt("reassure", "Rassurer : visibilité fiscale et emploi", "Aucun coût", [E("economy.consumerConfidence", 0.01)], "REFUSED")],
 ministry="economy", conditions=[cond("society.savingsRate", mn=0.19)])

nat("life_expectancy_report", "POLITICS", "Rapport choc : l'espérance de vie recule dans les territoires pauvres", "Dix ans d'écart entre riches et pauvres.",
 ["Inégalités face à la mort", "L'espérance de vie recule", "Rapport sur la santé des Français"],
 ["Un rapport montre que l'espérance de vie recule dans les territoires pauvres.", "L'écart atteint dix ans entre les plus riches et les plus pauvres."],
 ["Le système de santé est accusé d'abandonner les plus fragiles.", "L'opposition s'empare du sujet."],
 ["Je vous propose un plan de santé publique dans les quartiers et les campagnes, de rembourser mieux la prévention, ou de commander un nouveau rapport."],
 "plan", [
  opt("plan", "Plan de santé publique ciblé", "Coût : 1,5 Md€", [E("budget.oneOff", 1.5), E("quality.health", 0.006), E(G + "low_income", 0.004)], "ACCEPTED"),
  opt("prevention", "Mieux rembourser la prévention", "Coût : 500 M€", [E("budget.oneOff", 0.5), E("quality.health", 0.003)], "PARTIAL"),
  opt("report", "Commander un nouveau rapport", "Aucun coût ; immobilisme dénoncé", [E(G + "low_income", -0.003)], "REFUSED")],
 ministry="health", conditions=[cond("society.lifeExpectancy", mx=82.6)])

nat("world_crisis_fallout", "ECONOMY", "La crise mondiale frappe nos exportateurs", "Commandes annulées, usines au ralenti.",
 ["Contrecoup de la crise mondiale", "Nos exportations plongent", "La crise venue d'ailleurs"],
 ["Plusieurs de nos grands clients sont en récession : les commandes s'effondrent.", "L'aéronautique, le luxe et l'automobile ralentissent."],
 ["Les intérimaires sont les premiers touchés.", "Les régions industrielles s'inquiètent."],
 ["Je vous propose un plan de soutien à l'export, du chômage partiel massif, ou d'attendre la reprise."],
 "partial", [
  opt("export", "Plan de soutien à l'export", "Coût : 1 Md€", [E("budget.oneOff", 1.0), E("sector.industry", 0.003), E("sector.aerospace", 0.002)], "ACCEPTED"),
  opt("partial", "Chômage partiel massif", "Coût : 3 Md€ ; emplois préservés", [E("budget.oneOff", 3.0), E("economy.unemployment", -0.002)], "PARTIAL"),
  opt("wait", "Attendre la reprise", "Aucun coût", [E("economy.unemployment", 0.002), E(G + "private_employees", -0.003)], "REFUSED")],
 ministry="economy", conditions=[cond("economy.growth", mx=0.005)])

nat("drone_threat", "SECURITY", "Des drones survolent des centrales nucléaires", "Personne ne sait qui les pilote.",
 ["Drones au-dessus des centrales", "Survols mystérieux", "Menace sur les sites sensibles"],
 ["Des drones ont survolé plusieurs centrales nucléaires et bases militaires la même nuit.", "Les pilotes n'ont pas été identifiés."],
 ["Les services soupçonnent une puissance étrangère.", "L'opinion s'inquiète de la sécurité des sites."],
 ["Je vous propose d'équiper les sites de brouilleurs et de lasers, de renforcer la surveillance aérienne, ou de minimiser."],
 "jammers", [
  opt("jammers", "Brouilleurs et armes anti-drones", "Coût : 400 M€", [E("budget.oneOff", 0.4), E("quality.security", 0.005), E("quality.defense", 0.003)], "ACCEPTED"),
  opt("watch", "Renforcer la surveillance aérienne", "Coût : 100 M€", [E("budget.oneOff", 0.1), E("quality.defense", 0.002)], "PARTIAL"),
  opt("minimize", "Minimiser publiquement", "Aucun coût ; risque", [E("quality.security", -0.003)], "REFUSED")],
 ministry="armed_forces")

nat("olympic_legacy", "POLITICS", "Équipements sportifs : les clubs amateurs à l'agonie", "Les licenciés affluent, les installations manquent.",
 ["Le sport amateur en crise", "Plus de place dans les clubs", "Équipements sportifs vétustes"],
 ["Les clubs refusent des milliers d'enfants faute de créneaux et de gymnases.", "Les bénévoles s'épuisent."],
 ["Le sport est un enjeu de santé publique.", "Les communes n'ont plus les moyens."],
 ["Je vous propose un plan de 5 000 équipements sportifs, une aide aux clubs, ou de laisser les communes gérer."],
 "clubs", [
  opt("plan", "Plan 5 000 équipements sportifs", "Coût : 1 Md€", [E("budget.oneOff", 1.0), E(G + "young", 0.005), E("quality.health", 0.002), E("sector.construction", 0.001)], "ACCEPTED"),
  opt("clubs", "Aide directe aux clubs", "Coût : 200 M€", [E("budget.oneOff", 0.2), E(G + "young", 0.003)], "PARTIAL"),
  opt("none", "Laisser les communes gérer", "Aucun coût", [E(G + "young", -0.002)], "REFUSED")],
 ministry="education", cooldown=1460)

nat("heritage_fire", "DISASTER", "Incendie d'un monument historique", "Une partie du patrimoine national part en fumée.",
 ["Un monument en flammes", "Le patrimoine en deuil", "Incendie historique"],
 ["Un incendie a ravagé une cathédrale classée monument historique.", "La charpente médiévale est détruite."],
 ["Les dons affluent de toute la France.", "Les experts débattent : reconstruire à l'identique ou moderniser ?"],
 ["Je vous propose une reconstruction à l'identique en cinq ans, un concours d'architecture, ou de laisser le temps aux experts."],
 "identical", [
  opt("identical", "Reconstruire à l'identique en cinq ans", "Coût : 800 M€ ; fierté nationale", [E("budget.oneOff", 0.8), E("opinion.national", 0.006), E(G + "seniors", 0.004)], "ACCEPTED"),
  opt("contest", "Concours d'architecture contemporaine", "Coût : 600 M€ ; débat passionné", [E("budget.oneOff", 0.6), E(G + "young", 0.003), E(G + "seniors", -0.004)], "PARTIAL"),
  opt("experts", "Laisser le temps aux experts", "Aucun coût immédiat", [E("opinion.national", -0.002)], "REFUSED")],
 ministry="education", prob=N * 0.4, cooldown=3650)

json.dump({"events": EVENTS}, open(os.path.join(ROOT, "events", "more.json"), "w"), ensure_ascii=False, indent=1)
json.dump({"templates": TEMPLATES}, open(os.path.join(ROOT, "dialogue", "fr", "more.json"), "w"), ensure_ascii=False, indent=1)
print(len(EVENTS), "nouveaux événements,", len(TEMPLATES), "modèles")
