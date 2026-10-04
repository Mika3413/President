"""Deuxième vague d'événements (assets/data/events/crises.json) et leurs dialogues (dialogue/fr/crises.json)."""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")

def E(target, amount=0.0, param=None, factor=1.0, days=0.0, delay=0.0):
    d = {"target": target}
    if param: d["param"] = param; d["factor"] = factor
    else: d["amount"] = amount
    if days: d["days"] = days
    if delay: d["delayDays"] = delay
    return d
def opt(id, label, hint="", effects=(), outcome="NEUTRAL"):
    return {"id": id, "label": label, "hint": hint, "effects": list(effects), "outcome": outcome}
def mod(var, frm, to, fa, fb): return {"variable": var, "from": frm, "to": to, "factorAtFrom": fa, "factorAtTo": fb}
def msg(template, sender, days, default, options, ministry=None):
    m = {"template": template, "sender": sender, "responseDays": days, "defaultOption": default, "options": options}
    if ministry: m["ministry"] = ministry
    return m
G = "opinion.group."
events = [
 {"id": "president_scandal", "category": "POLITICS", "scope": "NATIONAL", "baseDailyProbability": 0.0008, "cooldownDays": 500, "urgency": "URGENT",
  "headline": "Révélations visant l'entourage du président", "notificationText": "La presse publie une enquête embarrassante.",
  "modifiers": [mod("president.integrity", 0.2, 0.8, 3.0, 0.3)],
  "immediateEffects": [E("president.scandal", 1), E("opinion.national", -0.012)],
  "message": msg("president_scandal", "PRIME_MINISTER", 3, "silence", [
    opt("assume", "Reconnaître les faits et sanctionner", "Coût immédiat, l'affaire s'éteint plus vite", [E("opinion.national", -0.008), E("sender.relation", 0.05)]),
    opt("inquiry", "Saisir une autorité indépendante", "Transparence, procédure longue", [E("opinion.national", 0.004), E("budget.oneOff", 0.01)]),
    opt("deny", "Démentir fermement", "Risque d'un second scandale", [E("opinion.national", -0.02, delay=25, days=10), E("president.scandal", 1, delay=25)]),
    opt("silence", "Ne pas commenter", "L'affaire suit son cours", [E("opinion.national", -0.01, days=15)])])},
 {"id": "epidemic", "category": "DISASTER", "scope": "NATIONAL", "baseDailyProbability": 0.003, "cooldownDays": 400, "urgency": "URGENT",
  "headline": "Épidémie : les hôpitaux sous tension", "notificationText": "Un virus respiratoire se propage rapidement.",
  "conditions": [{"variable": "season.month", "oneOf": [11, 12, 1, 2, 3]}], "modifiers": [mod("quality.health", 0.35, 0.65, 2.0, 0.6)],
  "immediateEffects": [E("quality.health", -0.02, days=30), E("economy.output", -0.0005, days=30)],
  "message": msg("epidemic", "MINISTER", 3, "minimal", [
    opt("restrictions", "Mesures de restriction ciblées", "Freine l'épidémie, pèse sur l'activité", [E("economy.output", -0.003, days=45), E("quality.health", 0.015, days=30), E(G+"young", -0.02), E(G+"seniors", 0.02)]),
    opt("vaccination", "Campagne massive de vaccination", "Coût : 1,5 Md€", [E("budget.oneOff", 1.5, days=60), E("quality.health", 0.02, days=60), E("opinion.national", 0.005)]),
    opt("minimal", "Recommandations sanitaires", "Aucun coût, risque de saturation", [E("quality.health", -0.02, days=30), E(G+"seniors", -0.02)])], "health")},
 {"id": "blackout", "category": "ENERGY", "scope": "NATIONAL", "baseDailyProbability": 0.02, "cooldownDays": 60, "urgency": "URGENT",
  "headline": "Coupures d'électricité dans plusieurs régions", "notificationText": "La production ne couvre plus la demande aux heures de pointe.",
  "conditions": [{"variable": "energy.margin", "max": 0.01}],
  "immediateEffects": [E("economy.output", -0.0008, days=10), E("opinion.national", -0.012)],
  "message": msg("blackout", "MINISTER", 2, "shedding", [
    opt("import", "Importer de l'électricité en urgence", "Coût : 800 M€", [E("budget.oneOff", 0.8), E("opinion.national", 0.006)]),
    opt("shedding", "Délestages tournants organisés", "Aucun coût, mécontentement", [E(G+"rural", -0.02), E("economy.output", -0.0005, days=20)]),
    opt("gas", "Relancer toutes les centrales à gaz", "Coût : 400 M€, émissions accrues", [E("budget.oneOff", 0.4), E("quality.environment", -0.01)])], "ecology")},
 {"id": "fuel_shortage", "category": "ENERGY", "scope": "NATIONAL", "baseDailyProbability": 0.002, "cooldownDays": 200, "urgency": "IMPORTANT",
  "headline": "Pénurie de carburant dans les stations-service", "notificationText": "Files d'attente et ruptures d'approvisionnement.",
  "modifiers": [mod("energy.priceIndex", 1.0, 1.6, 1.0, 4.0), mod("opinion.group.private_employees", 0.25, 0.5, 2.0, 0.7)],
  "immediateEffects": [E("economy.output", -0.0005, days=15), E(G+"rural", -0.015)],
  "message": msg("fuel_shortage", "MINISTER", 2, "wait", [
    opt("reserves", "Libérer les stocks stratégiques", "Puise dans les réserves de carburant des armées", [E("military.fuelStock", -0.15), E(G+"rural", 0.015)]),
    opt("price_cap", "Remise à la pompe financée par l'État", "Coût : 2 Md€", [E("budget.oneOff", 2.0, days=60), E(G+"rural", 0.02), E(G+"low_income", 0.01)]),
    opt("wait", "Laisser le marché se rééquilibrer", "Aucun coût", [E(G+"rural", -0.01, days=15)])], "ecology")},
 {"id": "terror_attack", "category": "SECURITY", "scope": "CITY", "maxCityRank": 2, "baseDailyProbability": 0.00003, "cooldownDays": 240, "urgency": "URGENT",
  "headline": "Attentat à {city}", "notificationText": "Le pays est sous le choc. Le ministre de l'Intérieur attend vos décisions.",
  "modifiers": [mod("quality.security", 0.35, 0.65, 2.5, 0.5), mod("military.atWar", 0, 1, 1.0, 2.5)],
  "immediateEffects": [E("opinion.national", 0.01), E("scope.satisfaction", -0.05), E("economy.consumerConfidence", -0.02)],
  "message": msg("terror_attack", "MINISTER", 1, "unity", [
    opt("emergency", "Décréter l'état d'urgence", "Sécurité renforcée, libertés restreintes", [E("quality.security", 0.02, days=60), E(G+"young", -0.02), E(G+"seniors", 0.02), E(G+"urban", -0.01)]),
    opt("intelligence", "Renforcer le renseignement intérieur", "Coût : 600 M€", [E("budget.oneOff", 0.6, days=180), E("quality.security", 0.03, days=365)]),
    opt("unity", "Hommage national et appel à l'unité", "Apaise sans répondre au fond", [E("opinion.national", 0.006)])], "interior")},
 {"id": "storm", "category": "DISASTER", "scope": "DEPARTMENT", "baseDailyProbability": 0.00008, "cooldownDays": 30, "scopeCooldownDays": 300, "urgency": "IMPORTANT",
  "headline": "Tempête violente : dégâts {departmentIn}", "notificationText": "Toitures arrachées, routes coupées, foyers privés d'électricité.",
  "conditions": [{"variable": "season.month", "oneOf": [10, 11, 12, 1, 2]}],
  "params": [{"name": "amount", "min": 0.04, "max": 0.25, "scaleBy": "scope.populationMillions", "minScale": 0.3, "round": 0.005}],
  "immediateEffects": [E("scope.approval", -0.01), E("economy.output", -0.0001, days=10)],
  "message": msg("storm", "PREFECT", 3, "minimal", [
    opt("full", "Fonds d'urgence et reconnaissance de catastrophe naturelle", "Coût : {amountText}", [E("budget.oneOff", param="amount"), E("scope.approval", 0.03, days=10), E("sender.relation", 0.06)], "ACCEPTED"),
    opt("minimal", "Mobiliser les services de l'État sans aide spécifique", "Coût réduit", [E("budget.oneOff", param="amount", factor=0.2), E("scope.approval", -0.01, days=10)], "PARTIAL")])},
 {"id": "wildfire", "category": "DISASTER", "scope": "DEPARTMENT", "baseDailyProbability": 0.00012, "cooldownDays": 20, "scopeCooldownDays": 200, "urgency": "URGENT",
  "headline": "Incendies de forêt {departmentIn}", "notificationText": "Des milliers d'hectares brûlent ; des habitants sont évacués.",
  "conditions": [{"variable": "season.month", "oneOf": [6, 7, 8, 9]}], "modifiers": [mod("scope.mediterranean", 0, 1, 0.2, 2.5)],
  "params": [{"name": "amount", "min": 0.03, "max": 0.15, "round": 0.005}],
  "immediateEffects": [E("scope.approval", -0.015), E("quality.environment", -0.005)],
  "message": msg("wildfire", "PREFECT", 2, "standard", [
    opt("reinforce", "Envoyer des renforts aériens et militaires", "Coût : {amountText}", [E("budget.oneOff", param="amount"), E("scope.approval", 0.03, days=10), E("military.readiness", -0.01)], "ACCEPTED"),
    opt("standard", "Laisser agir les moyens habituels", "Aucun coût supplémentaire", [E("scope.approval", -0.01), E("quality.environment", -0.005)], "REFUSED")])},
 {"id": "bankruptcy", "category": "ECONOMY", "scope": "NATIONAL", "baseDailyProbability": 0.0015, "cooldownDays": 240, "urgency": "URGENT",
  "headline": "Un grand groupe industriel au bord de la faillite", "notificationText": "Des dizaines de milliers d'emplois sont menacés.",
  "modifiers": [mod("economy.businessConfidence", 0.3, 0.55, 3.0, 0.4), mod("energy.priceIndex", 1.0, 1.5, 1.0, 2.0)],
  "message": msg("bankruptcy", "MINISTER", 4, "let_go", [
    opt("nationalize", "Nationalisation temporaire", "Coût : 4 Md€, emplois sauvés", [E("budget.oneOff", 4.0), E(G+"private_employees", 0.02), E("economy.businessConfidence", -0.01)]),
    opt("loan", "Prêt garanti par l'État", "Coût : 600 M€, sauvetage partiel", [E("budget.oneOff", 0.6), E("economy.unemployment", 0.001, days=120)]),
    opt("let_go", "Laisser la procédure suivre son cours", "Aucun coût, choc social", [E("economy.unemployment", 0.003, days=120), E(G+"private_employees", -0.02), E("economy.output", -0.001, days=90)])], "economy")},
 {"id": "farmers_protest", "category": "POLITICS", "scope": "NATIONAL", "baseDailyProbability": 0.004, "cooldownDays": 300, "urgency": "IMPORTANT",
  "headline": "Colère agricole : blocages dans tout le pays", "notificationText": "Les agriculteurs dénoncent leurs revenus et les normes.",
  "conditions": [{"variable": "season.month", "oneOf": [1, 2, 3, 11, 12]}],
  "modifiers": [mod("quality.agriculture", 0.35, 0.6, 2.5, 0.5), mod("energy.priceIndex", 1.0, 1.4, 1.0, 2.0)],
  "immediateEffects": [E(G+"rural", -0.01), E("economy.output", -0.0003, days=14)],
  "message": msg("farmers_protest", "MINISTER", 3, "firm", [
    opt("aid", "Plan d'aide d'urgence", "Coût : 1,2 Md€", [E("budget.oneOff", 1.2), E(G+"rural", 0.03), E("quality.agriculture", 0.02, days=90)]),
    opt("simplify", "Suspendre certaines normes environnementales", "Aucun coût, environnement affaibli", [E(G+"rural", 0.02), E("quality.environment", -0.02), E(G+"young", -0.01)]),
    opt("firm", "Fermeté et dialogue", "Les blocages peuvent durer", [E(G+"rural", -0.02, days=20)])], "agriculture")},
 {"id": "teachers_strike", "category": "POLITICS", "scope": "NATIONAL", "baseDailyProbability": 0.003, "cooldownDays": 240, "urgency": "IMPORTANT",
  "headline": "Grève massive dans l'Éducation nationale", "notificationText": "Les enseignants réclament des moyens et des revalorisations.",
  "conditions": [{"variable": "season.month", "oneOf": [9, 10, 11, 1, 2, 3, 4, 5]}], "modifiers": [mod("quality.education", 0.35, 0.6, 3.0, 0.4), mod("economy.inflation", 0.01, 0.04, 0.8, 2.0)],
  "message": msg("teachers_strike", "MINISTER", 4, "firm", [
    opt("raise", "Revalorisation salariale", "Budget de l'éducation +2 %", [E("spending.education", 0.02), E(G+"civil_servants", 0.03), E("quality.education", 0.01, days=90)]),
    opt("dialogue", "Ouvrir une concertation", "Apaisement partiel", [E(G+"civil_servants", 0.01)]),
    opt("firm", "Tenir bon", "Mobilisation prolongée", [E(G+"civil_servants", -0.03), E("quality.education", -0.01, days=30)])], "education")},
 {"id": "diplomatic_incident", "category": "DIPLOMACY", "scope": "FOREIGN_COUNTRY", "baseDailyProbability": 0.0008, "cooldownDays": 60, "scopeCooldownDays": 365, "urgency": "IMPORTANT",
  "headline": "Incident diplomatique avec {foreignThe}", "notificationText": "Des propos de responsables étrangers provoquent un tollé.",
  "modifiers": [mod("scope.relation", 0.2, 0.6, 3.0, 0.3)],
  "message": msg("diplomatic_incident", "MINISTER", 3, "protest", [
    opt("summon", "Convoquer l'ambassadeur", "Fermeté, relations dégradées", [E("country.DISAGREEMENT", -0.04), E("opinion.national", 0.004)]),
    opt("protest", "Protestation diplomatique discrète", "Mesuré", [E("country.DISAGREEMENT", -0.015)]),
    opt("appease", "Apaiser et passer outre", "Relations préservées, image de faiblesse", [E("country.NEGOTIATION_GOODWILL", 0.02), E("opinion.national", -0.004)])], "foreign")},
 {"id": "military_accident", "category": "MILITARY", "scope": "NATIONAL", "baseDailyProbability": 0.001, "cooldownDays": 200, "urgency": "IMPORTANT",
  "headline": "Accident militaire lors d'un exercice", "notificationText": "Plusieurs militaires ont perdu la vie.",
  "modifiers": [mod("military.readiness", 0.4, 0.75, 3.0, 0.5)],
  "immediateEffects": [E("military.readiness", -0.01), E("opinion.national", -0.003)],
  "message": msg("military_accident", "MINISTER", 4, "inquiry", [
    opt("maintenance", "Plan d'urgence pour l'entretien des matériels", "Budget de la défense +3 %", [E("spending.defense", 0.03), E("military.readiness", 0.03, days=180)]),
    opt("inquiry", "Enquête de commandement", "Aucun coût", [E("opinion.national", 0.001)])], "armed_forces")},
 {"id": "refugee_crisis", "category": "SECURITY", "scope": "NATIONAL", "baseDailyProbability": 0.01, "cooldownDays": 180, "urgency": "IMPORTANT",
  "headline": "Afflux de réfugiés fuyant la guerre", "notificationText": "Des milliers de personnes arrivent aux frontières de l'Europe.",
  "conditions": [{"variable": "military.nearbyWar", "min": 1}],
  "message": msg("refugee_crisis", "MINISTER", 4, "european", [
    opt("welcome", "Accueil large et intégration", "Coût : 1,5 Md€", [E("budget.oneOff", 1.5, days=180), E(G+"urban", 0.02), E(G+"young", 0.02), E(G+"rural", -0.02), E(G+"seniors", -0.01)]),
    opt("european", "Répartition européenne négociée", "Équilibre", [E("budget.oneOff", 0.5, days=180), E("memory.DEU.NEGOTIATION_GOODWILL", 0.02), E("memory.ITA.NEGOTIATION_GOODWILL", 0.02)]),
    opt("closed", "Contrôles renforcés aux frontières", "Populaire chez certains, critiqué par nos partenaires", [E(G+"rural", 0.02), E(G+"young", -0.02), E("memory.DEU.DISAGREEMENT", -0.02)])], "interior")},
 {"id": "investment_announcement", "category": "ECONOMY", "scope": "NATIONAL", "baseDailyProbability": 0.004, "cooldownDays": 120,
  "headline": "Un géant industriel annonce une usine géante en France", "notificationText": "Des milliers d'emplois à la clé.",
  "modifiers": [mod("economy.businessConfidence", 0.4, 0.65, 0.3, 3.0)],
  "immediateEffects": [E("economy.output", 0.0015, days=365), E("economy.unemployment", -0.001, days=365), E("economy.businessConfidence", 0.01), E("opinion.national", 0.004)]},
 {"id": "sports_victory", "category": "POLITICS", "scope": "NATIONAL", "baseDailyProbability": 0.0008, "cooldownDays": 365,
  "headline": "Victoire historique de l'équipe de France", "notificationText": "Le pays célèbre ses champions.",
  "immediateEffects": [E("opinion.national", 0.015), E("economy.consumerConfidence", 0.01)]},
 {"id": "drought", "category": "DISASTER", "scope": "NATIONAL", "baseDailyProbability": 0.006, "cooldownDays": 300, "urgency": "IMPORTANT",
  "headline": "Sécheresse historique : restrictions d'eau", "notificationText": "Les récoltes sont menacées, les prix alimentaires montent.",
  "conditions": [{"variable": "season.month", "oneOf": [7, 8]}],
  "immediateEffects": [E("economy.inflation", 0.002, days=90), E(G+"rural", -0.015), E("quality.agriculture", -0.02, days=60), E("economy.output", -0.0005, days=60)]},
]
json.dump({"events": events}, open(os.path.join(ROOT, "events", "crises.json"), "w"), ensure_ascii=False, indent=1)

from dialogue_fr_lib import letter, V
templates = [
 letter("president_scandal", [V("Révélations de presse : votre entourage mis en cause"), V("Note confidentielle : l'affaire qui vise l'Élysée"), V("Crise politique : quelle réponse ?")],
   [V("Un quotidien national publie ce matin une enquête sur des avantages accordés à des proches de votre cabinet."), V("Des documents mettent en cause plusieurs de vos collaborateurs dans l'attribution de marchés publics."), V("L'opposition s'est emparée d'une affaire impliquant votre entourage proche.")],
   [V("La confiance des Français est en jeu."), V("Chaque jour de silence alimente les soupçons."), V("Les chaînes d'information ne parlent que de cela.", when=["president:unpopular"])],
   [V("Il nous faut arrêter une ligne : reconnaître, saisir une autorité indépendante, démentir ou ne rien dire."), V("Je vous recommande la transparence, mais la décision vous appartient.", when=["trait:cautious"]), V("Un démenti ferme peut suffire, si nous sommes certains des faits.", when=["trait:proud"])]),
 letter("epidemic", [V("Épidémie : propositions du ministère de la Santé"), V("Point de situation sanitaire"), V("Note urgente : propagation épidémique")],
   [V("Un virus respiratoire se propage rapidement dans tout le pays."), V("Le nombre d'hospitalisations double chaque semaine."), V("Les urgences pédiatriques et gériatriques sont saturées.")],
   [V("Sans mesure, les hôpitaux risquent de devoir déprogrammer des soins."), V("Les plus âgés sont particulièrement exposés.")],
   [V("Trois options : restrictions ciblées, campagne de vaccination massive, ou simples recommandations."), V("Votre arbitrage est attendu [[quickly]].")]),
 letter("blackout", [V("Coupures d'électricité : décision urgente"), V("Réseau électrique sous tension"), V("Alerte RTE : déséquilibre du réseau")],
   [V("Le gestionnaire du réseau a dû procéder à des coupures dans plusieurs régions."), V("La production ne couvre plus la consommation aux heures de pointe.")],
   [V("Hôpitaux et industries s'inquiètent ; la colère monte dans les zones touchées."), V("Notre parc vieillissant et nos engagements d'exportation pèsent sur la situation.")],
   [V("Nous pouvons importer en urgence, organiser des délestages ou relancer les centrales à gaz."), V("Votre [[decision]] conditionnera les prochains jours.")]),
 letter("fuel_shortage", [V("Pénurie de carburant"), V("Stations-service à sec"), V("Approvisionnement en carburant : alerte")],
   [V("De nombreuses stations-service sont en rupture."), V("Les files d'attente s'allongent devant les pompes.")],
   [V("Les professionnels de la route et les ruraux sont les premiers touchés."), V("La situation pourrait dégénérer en mouvement social.")],
   [V("Je vous propose de libérer les stocks stratégiques, de financer une remise à la pompe, ou d'attendre le retour à la normale.")]),
 letter("terror_attack", [V("Attentat à {city}"), V("Note urgente du ministère de l'Intérieur"), V("Attaque terroriste : premières décisions")],
   [V("Une attaque terroriste a frappé {city} il y a quelques heures."), V("Le bilan est lourd et la population est sous le choc.")],
   [V("La menace reste élevée sur l'ensemble du territoire."), V("Les Français attendent une réponse ferme et rassurante.")],
   [V("Je vous propose l'état d'urgence, un renforcement du renseignement, ou un appel solennel à l'unité."), V("Votre [[decision]] est attendue dans la journée.")]),
 letter("storm", [V("Tempête : dégâts {departmentIn}"), V("Dégâts considérables après la tempête"), V("Intempéries : appel à l'État")],
   [V("Une tempête d'une rare violence a frappé {departmentThe}."), V("Des vents à plus de 150 km/h ont causé d'importants dégâts.")],
   [V("Des milliers de foyers sont privés d'électricité et plusieurs routes sont coupées."), V("Les élus locaux réclament l'aide de l'État.")],
   [V("Je sollicite un fonds d'urgence de {amountText}."), V("Une aide de {amountText} permettrait de réparer l'essentiel.")]),
 letter("wildfire", [V("Incendies {departmentIn}"), V("Feux de forêt : demande de renforts"), V("Incendie hors de contrôle")],
   [V("Plusieurs feux de forêt ravagent {departmentThe}."), V("Attisés par le vent et la sécheresse, les incendies progressent rapidement.")],
   [V("Des habitants ont dû être évacués ; les pompiers sont épuisés."), V("Les moyens locaux ne suffisent plus.")],
   [V("Je vous demande l'envoi de renforts aériens et militaires (coût estimé : {amountText})."), V("Sans renforts, la situation pourrait devenir dramatique.")]),
 letter("bankruptcy", [V("Un fleuron industriel menacé de faillite"), V("Note urgente : risque de défaillance"), V("Sauvetage industriel : votre arbitrage")],
   [V("Un grand groupe industriel français annonce une cessation de paiements imminente."), V("Nos services ont été alertés d'une défaillance probable d'un grand employeur.")],
   [V("Des dizaines de milliers d'emplois directs et indirects sont en jeu."), V("Les sous-traitants de toute une filière seraient emportés.")],
   [V("Nationalisation temporaire, prêt garanti ou laisser-faire : chaque option a un coût."), V("Je recommande une intervention, mais nos finances sont contraintes.", when=["trait:cautious"])]),
 letter("farmers_protest", [V("Colère agricole"), V("Blocages agricoles : que faire ?"), V("Les agriculteurs dans la rue")],
   [V("Des milliers de tracteurs bloquent les routes et les préfectures."), V("Les agriculteurs dénoncent l'effondrement de leurs revenus.")],
   [V("Les normes, les prix de l'énergie et la concurrence étrangère sont au cœur de leurs revendications."), V("La mobilisation s'étend chaque jour.")],
   [V("Plan d'aide, suspension de normes ou fermeté : j'attends vos instructions.")]),
 letter("teachers_strike", [V("Grève dans l'Éducation nationale"), V("Mobilisation enseignante"), V("Écoles fermées : votre arbitrage")],
   [V("Une grève massive touche les écoles, collèges et lycées."), V("Les enseignants dénoncent des classes surchargées et des salaires insuffisants.")],
   [V("Les parents s'inquiètent et la mobilisation pourrait durer."), V("Les recrutements peinent à couvrir les besoins.")],
   [V("Revalorisation, concertation ou fermeté : quelle ligne souhaitez-vous ?")]),
 letter("diplomatic_incident", [V("Incident diplomatique avec {foreignThe}"), V("Déclarations hostiles {foreignOf}"), V("Tension diplomatique")],
   [V("Des responsables {foreignOf} ont tenu des propos très hostiles envers la France."), V("Une déclaration officielle {foreignOf} met en cause notre politique.")],
   [V("L'opinion attend une réaction."), V("Nos partenaires observent notre réponse.")],
   [V("Je vous propose de convoquer l'ambassadeur, de protester discrètement, ou d'apaiser la situation.")]),
 letter("military_accident", [V("Accident militaire"), V("Drame lors d'un exercice"), V("Note du ministère des Armées")],
   [V("Un accident survenu lors d'un exercice a coûté la vie à plusieurs militaires."), V("Un matériel défectueux serait à l'origine du drame.")],
   [V("L'état de nos équipements est mis en cause."), V("Les familles et les armées attendent un geste fort.")],
   [V("Je vous propose un plan d'urgence pour l'entretien, ou une enquête de commandement.")]),
 letter("refugee_crisis", [V("Afflux de réfugiés"), V("Crise migratoire liée à la guerre"), V("Réfugiés : la position de la France")],
   [V("La guerre jette des centaines de milliers de personnes sur les routes de l'exil."), V("Les pays voisins du conflit sont débordés et appellent à la solidarité européenne.")],
   [V("Nos partenaires attendent une position claire de la France."), V("L'opinion est profondément divisée sur la question.")],
   [V("Accueil large, répartition européenne ou contrôles renforcés : quelle ligne fixons-nous ?")]),
]
json.dump({"templates": templates}, open(os.path.join(ROOT, "dialogue", "fr", "crises.json"), "w"), ensure_ascii=False, indent=1)
c = json.load(open(os.path.join(ROOT, "config", "game_config.json")))
if "events/crises.json" not in c["files"]["events"]: c["files"]["events"].append("events/crises.json")
if "dialogue/fr/crises.json" not in c["files"]["dialogue"]: c["files"]["dialogue"].append("dialogue/fr/crises.json")
json.dump(c, open(os.path.join(ROOT, "config", "game_config.json"), "w"), ensure_ascii=False, indent=2)
print(len(events), "événements", len(templates), "modèles")
