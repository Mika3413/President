"""Génère assets/data/events/*.json (définitions d'événements dynamiques)."""
import json, os
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data"))

def E(target, amount=0.0, param=None, factor=1.0, days=0.0, delay=0.0):
    d = {"target": target}
    if param: d["param"] = param; d["factor"] = factor
    else: d["amount"] = amount
    if days: d["days"] = days
    if delay: d["delayDays"] = delay
    return d

def opt(id, label, hint="", effects=(), outcome="NEUTRAL", project=None, reask=None, details=False):
    o = {"id": id, "label": label, "hint": hint, "effects": list(effects), "outcome": outcome}
    if project: o["project"] = project
    if reask: o["reaskAfterDays"] = reask
    if details: o["requestDetails"] = True
    return o

def mod(var, frm, to, fa, fb): return {"variable": var, "from": frm, "to": to, "factorAtFrom": fa, "factorAtTo": fb}

territory = [
 {"id": "city_transport_request", "category": "TERRITORY", "scope": "CITY", "maxCityRank": 2,
  "baseDailyProbability": 0.002, "cooldownDays": 8, "scopeCooldownDays": 300,
  "headline": "{city} sollicite l'État pour un projet de transport",
  "modifiers": [mod("quality.transport", 0.4, 0.7, 1.6, 0.7), mod("scope.satisfaction", 0.3, 0.6, 1.3, 0.9)],
  "params": [{"name": "amount", "min": 0.15, "max": 0.4, "scaleBy": "scope.populationMillions", "minScale": 0.25, "round": 0.005}],
  "message": {"template": "territorial_transport_request", "detailsTemplate": "territorial_request_details", "sender": "MAYOR", "responseDays": 7, "defaultOption": "refuse",
   "options": [
    opt("accept", "Financer intégralement", "Coût : {amountText} étalés sur 2 ans", [E("sender.relation", 0.15), E("scope.approval", 0.03, days=20)], "ACCEPTED",
        {"name": "Transports de {city}", "kind": "transport", "durationDays": 720, "costParam": "amount", "costFactor": 1.0,
         "onCompletion": [E("scope.satisfaction", 0.06), E("scope.approval", 0.02, days=30), E("quality.transport", 0.004), E("scope.unemployment", -0.001, days=90)]}),
    opt("partial", "Financer à moitié", "Coût : la moitié, projet réduit", [E("sender.relation", 0.05), E("scope.approval", 0.01, days=20)], "PARTIAL",
        {"name": "Transports de {city} (version réduite)", "kind": "transport", "durationDays": 900, "costParam": "amount", "costFactor": 0.5,
         "onCompletion": [E("scope.satisfaction", 0.03), E("quality.transport", 0.002)]}),
    opt("cofinance", "Proposer un cofinancement avec la région", "L'État ne paie que 30 % ; la région est sollicitée", [E("sender.relation", 0.02), E("region.approval", -0.005)], "PARTIAL",
        {"name": "Transports de {city} (cofinancés)", "kind": "transport", "durationDays": 1000, "costParam": "amount", "costFactor": 0.3,
         "onCompletion": [E("scope.satisfaction", 0.03), E("quality.transport", 0.002)]}),
    opt("postpone", "Reporter la décision", "La demande reviendra dans 45 jours", [E("sender.relation", -0.03)], "POSTPONED", reask=45),
    opt("details", "Demander davantage d'informations", "Le maire précisera son dossier", details=True),
    opt("refuse", "Refuser", "Aucun coût, mais mécontentement local", [E("sender.relation", -0.12), E("scope.approval", -0.02, days=10)], "REFUSED"),
   ]}},
 {"id": "hospital_overload", "category": "TERRITORY", "scope": "DEPARTMENT",
  "baseDailyProbability": 0.00005, "cooldownDays": 14, "scopeCooldownDays": 300,
  "headline": "Urgences saturées dans le département {department}",
  "modifiers": [mod("quality.health", 0.35, 0.65, 3.0, 0.4), mod("scope.seniorShare", 0.15, 0.3, 0.8, 1.5), mod("season.month", 1, 2, 1.5, 1.0)],
  "params": [{"name": "amount", "min": 0.03, "max": 0.12, "round": 0.005}],
  "message": {"template": "hospital_overload", "sender": "DEPARTMENT_PRESIDENT", "responseDays": 5, "defaultOption": "refuse",
   "options": [
    opt("emergency", "Débloquer des moyens d'urgence", "Coût immédiat : {amountText}", [E("budget.oneOff", param="amount"), E("scope.approval", 0.03, days=15), E("quality.health", 0.003), E("sender.relation", 0.1)], "ACCEPTED"),
    opt("plan", "Lancer un plan de modernisation", "Coût : 3 × {amountText} sur 18 mois", [E("sender.relation", 0.08), E("scope.approval", 0.01, days=15)], "ACCEPTED",
        {"name": "Modernisation hospitalière ({department})", "kind": "health", "durationDays": 540, "costParam": "amount", "costFactor": 3.0,
         "onCompletion": [E("quality.health", 0.008), E("scope.approval", 0.03, days=30)]}),
    opt("refuse", "S'en remettre à l'ARS", "Aucun coût ; risque de dégradation", [E("sender.relation", -0.08), E("scope.approval", -0.03, days=10), E("opinion.group.seniors", -0.004)], "REFUSED"),
   ]}},
 {"id": "factory_closure", "category": "ECONOMY", "scope": "DEPARTMENT",
  "baseDailyProbability": 0.00003, "cooldownDays": 20, "scopeCooldownDays": 365, "urgency": "IMPORTANT",
  "headline": "Menace de fermeture d'un site industriel ({department})",
  "notificationText": "Le préfet demande une réponse rapide de l'État.",
  "modifiers": [mod("economy.businessConfidence", 0.3, 0.6, 3.0, 0.5), mod("energy.priceIndex", 1.0, 1.5, 1.0, 2.0), mod("scope.unemployment", 0.06, 0.11, 0.8, 1.5)],
  "params": [{"name": "jobs", "min": 300, "max": 2500, "round": 50}, {"name": "amount", "min": 0.08, "max": 0.35, "round": 0.01}, {"name": "impact", "min": 0.0015, "max": 0.005}],
  "message": {"template": "factory_closure", "sender": "PREFECT", "responseDays": 6, "defaultOption": "let_go",
   "options": [
    opt("rescue", "Soutenir la reprise avec des fonds publics", "Coût : {amountText}", [E("budget.oneOff", param="amount"), E("scope.approval", 0.03, days=10), E("economy.businessConfidence", 0.005)], "ACCEPTED"),
    opt("broker", "Chercher un repreneur privé", "Coût faible ; succès partiel", [E("budget.oneOff", param="amount", factor=0.15), E("scope.unemployment", param="impact", factor=0.5, days=120), E("scope.approval", -0.01, days=10)], "PARTIAL"),
    opt("let_go", "Laisser faire le marché", "Aucun coût ; {jobs} emplois menacés", [E("scope.unemployment", param="impact", days=120), E("scope.industry", -0.01, days=120), E("scope.approval", -0.04, days=20), E("opinion.group.private_employees", -0.004)], "REFUSED"),
   ]}},
]

infrastructure = [
 {"id": "nuclear_incident", "category": "ENERGY", "scope": "INFRASTRUCTURE", "infraTypes": ["NUCLEAR_PLANT"],
  "baseDailyProbability": 0.00035, "cooldownDays": 15, "scopeCooldownDays": 120, "urgency": "URGENT",
  "headline": "Arrêt de {infrastructure} après un incident technique",
  "notificationText": "La centrale a été arrêtée par sécurité. Le ministère attend vos instructions.",
  "modifiers": [mod("scope.condition", 0.3, 0.8, 4.0, 0.5), mod("scope.maintenance", 0.5, 1.5, 2.0, 0.6)],
  "params": [{"name": "days", "min": 12, "max": 60, "round": 1}],
  "immediateEffects": [E("scope.offlineDays", param="days"), E("scope.condition", -0.03)],
  "message": {"template": "nuclear_incident", "sender": "MINISTER", "ministry": "ecology", "responseDays": 3, "defaultOption": "standard",
   "options": [
    opt("inspection", "Inspection approfondie et entretien renforcé", "Arrêt prolongé, coût 80 M€, risque futur réduit", [E("scope.offlineDays", param="days", factor=1.5), E("scope.maintenance", 0.3), E("budget.oneOff", 0.08), E("opinion.national", 0.004)]),
    opt("standard", "Procédure normale de redémarrage", "Reprise dans les délais prévus", []),
    opt("transparency", "Communiquer largement sur l'incident", "Rassure l'opinion, inquiète nos voisins", [E("opinion.national", 0.006), E("memory.DEU.DISAGREEMENT", -0.01)]),
   ]}},
 {"id": "dam_drought", "category": "DISASTER", "scope": "INFRASTRUCTURE", "infraTypes": ["HYDRO_DAM"],
  "baseDailyProbability": 0.004, "cooldownDays": 20, "scopeCooldownDays": 300, "urgency": "IMPORTANT",
  "headline": "Sécheresse : production réduite au {infrastructure}",
  "notificationText": "Le niveau des retenues est au plus bas. L'agriculture locale est également touchée.",
  "conditions": [{"variable": "season.month", "oneOf": [6, 7, 8, 9]}],
  "params": [{"name": "days", "min": 20, "max": 50, "round": 1}],
  "immediateEffects": [E("scope.offlineDays", param="days"), E("opinion.group.rural", -0.006, days=20), E("economy.output", -0.0002, days=30), E("economy.inflation", 0.0005, days=60)]},
 {"id": "refinery_accident", "category": "SECURITY", "scope": "INFRASTRUCTURE", "infraTypes": ["REFINERY"],
  "baseDailyProbability": 0.0003, "cooldownDays": 30, "scopeCooldownDays": 365, "urgency": "URGENT",
  "headline": "Accident industriel à la {infrastructure}",
  "notificationText": "Un incendie s'est déclaré sur le site. Les secours sont mobilisés.",
  "modifiers": [mod("scope.condition", 0.3, 0.8, 4.0, 0.4), mod("scope.maintenance", 0.5, 1.5, 2.0, 0.6)],
  "params": [{"name": "days", "min": 15, "max": 90, "round": 1}],
  "immediateEffects": [E("scope.offlineDays", param="days"), E("scope.condition", -0.08), E("scope.approval", -0.03, days=10), E("economy.output", -0.0003, days=30)],
  "message": {"template": "refinery_accident", "sender": "MINISTER", "ministry": "interior", "responseDays": 3, "defaultOption": "investigate",
   "options": [
    opt("investigate", "Ouvrir une enquête administrative", "Pas de coût ; l'exploitant reste responsable", [E("scope.approval", 0.005)]),
    opt("prevention", "Imposer un plan de prévention renforcé", "Coût public : 60 M€ ; risque futur réduit", [E("budget.oneOff", 0.06), E("scope.maintenance", 0.4), E("scope.approval", 0.02, days=10), E("economy.businessConfidence", -0.003)]),
    opt("support", "Indemniser rapidement les riverains", "Coût : 120 M€ ; apaise la colère locale", [E("budget.oneOff", 0.12), E("scope.approval", 0.04, days=10)]),
   ]}},
 {"id": "port_strike", "category": "ECONOMY", "scope": "INFRASTRUCTURE", "infraTypes": ["PORT"],
  "baseDailyProbability": 0.0004, "cooldownDays": 25, "scopeCooldownDays": 200, "urgency": "IMPORTANT",
  "headline": "Grève des dockers : le {infrastructure} paralysé",
  "notificationText": "Les exportations sont ralenties.",
  "modifiers": [mod("opinion.group.private_employees", 0.25, 0.55, 3.0, 0.4), mod("economy.inflation", 0.01, 0.05, 0.8, 2.0)],
  "params": [{"name": "days", "min": 3, "max": 14, "round": 1}],
  "immediateEffects": [E("scope.offlineDays", param="days"), E("economy.output", -0.0002, days=14), E("economy.businessConfidence", -0.004)]},
]

society = [
 {"id": "national_strike", "category": "POLITICS", "scope": "NATIONAL",
  "baseDailyProbability": 0.004, "cooldownDays": 90, "urgency": "URGENT",
  "headline": "Mouvement social : grève interprofessionnelle",
  "notificationText": "Les syndicats appellent à une journée de mobilisation nationale.",
  "conditions": [{"variable": "opinion.group.private_employees", "max": 0.42}],
  "modifiers": [mod("opinion.group.private_employees", 0.25, 0.42, 3.0, 0.6), mod("economy.inflation", 0.01, 0.05, 0.8, 2.5), mod("tax.households", 0.25, 0.32, 0.8, 1.5)],
  "params": [{"name": "amount", "min": 1.5, "max": 4.0, "round": 0.1}],
  "immediateEffects": [E("economy.output", -0.0008, days=10), E("economy.consumerConfidence", -0.01)],
  "message": {"template": "national_strike", "sender": "MINISTER", "ministry": "labour", "responseDays": 4, "defaultOption": "firm",
   "options": [
    opt("negotiate", "Ouvrir des négociations salariales", "Coût : {amountText} par an de mesures", [E("budget.oneOff", param="amount"), E("opinion.group.private_employees", 0.03, days=20), E("opinion.group.low_income", 0.02, days=20), E("economy.businessConfidence", -0.01)]),
    opt("partial", "Concessions ciblées", "Coût : un tiers ; apaisement partiel", [E("budget.oneOff", param="amount", factor=0.33), E("opinion.group.private_employees", 0.01, days=20)]),
    opt("firm", "Tenir bon", "Aucun coût ; le mouvement peut durer", [E("opinion.group.private_employees", -0.02, days=20), E("economy.output", -0.0005, days=20), E("economy.businessConfidence", 0.005)]),
   ]}},
 {"id": "city_demonstration", "category": "SECURITY", "scope": "CITY", "maxCityRank": 2,
  "baseDailyProbability": 0.0006, "cooldownDays": 6, "scopeCooldownDays": 60, "urgency": "IMPORTANT",
  "headline": "Importante manifestation à {city}",
  "notificationText": "Des milliers de personnes défilent contre la politique du gouvernement.",
  "modifiers": [mod("scope.satisfaction", 0.25, 0.5, 4.0, 0.2), mod("economy.inflation", 0.01, 0.05, 0.8, 1.8)],
  "immediateEffects": [E("scope.approval", -0.01)],
  "message": {"template": "demonstration", "sender": "PREFECT", "responseDays": 2, "defaultOption": "order",
   "options": [
    opt("dialogue", "Recevoir les organisateurs", "Apaisement local, image de faiblesse pour certains", [E("scope.approval", 0.015, days=10), E("opinion.group.seniors", -0.002)]),
    opt("order", "Maintenir l'ordre avec fermeté", "Rassure une partie de l'opinion", [E("opinion.group.seniors", 0.004), E("opinion.group.young", -0.005), E("scope.approval", -0.005)]),
   ]}},
 {"id": "heatwave", "category": "DISASTER", "scope": "NATIONAL",
  "baseDailyProbability": 0.02, "cooldownDays": 120, "urgency": "IMPORTANT",
  "headline": "Canicule sur une grande partie du pays",
  "notificationText": "Les hôpitaux se préparent à un afflux de patients.",
  "conditions": [{"variable": "season.month", "oneOf": [6, 7, 8]}],
  "immediateEffects": [E("quality.health", -0.01, days=20), E("economy.output", -0.0002, days=20)],
  "message": {"template": "heatwave", "sender": "MINISTER", "ministry": "health", "responseDays": 2, "defaultOption": "standard",
   "options": [
    opt("plan", "Déclencher le plan canicule renforcé", "Coût : 200 M€ ; protège les plus fragiles", [E("budget.oneOff", 0.2), E("opinion.group.seniors", 0.015, days=15), E("quality.health", 0.006)]),
    opt("standard", "Appliquer le dispositif habituel", "Aucun coût supplémentaire", [E("opinion.group.seniors", -0.006, days=15)]),
   ]}},
 {"id": "flood", "category": "DISASTER", "scope": "DEPARTMENT",
  "baseDailyProbability": 0.00012, "cooldownDays": 25, "scopeCooldownDays": 300, "urgency": "URGENT",
  "headline": "Inondations dans le département {department}",
  "notificationText": "Des habitations ont été évacuées. Le préfet sollicite l'État.",
  "conditions": [{"variable": "season.month", "oneOf": [10, 11, 12, 1, 2, 3]}],
  "params": [{"name": "amount", "min": 0.05, "max": 0.4, "scaleBy": "scope.populationMillions", "minScale": 0.3, "round": 0.005}],
  "immediateEffects": [E("scope.approval", -0.01), E("economy.output", -0.0001, days=15)],
  "message": {"template": "flood", "sender": "PREFECT", "responseDays": 3, "defaultOption": "minimal",
   "options": [
    opt("full", "Reconnaissance de catastrophe naturelle et fonds d'urgence", "Coût : {amountText}", [E("budget.oneOff", param="amount"), E("scope.approval", 0.04, days=10), E("sender.relation", 0.08)], "ACCEPTED"),
    opt("minimal", "Aide minimale de l'État", "Coût : 25 % ; la population attendait plus", [E("budget.oneOff", param="amount", factor=0.25), E("scope.approval", -0.01, days=10)], "PARTIAL"),
   ]}},
 {"id": "cyberattack", "category": "SECURITY", "scope": "NATIONAL",
  "baseDailyProbability": 0.0015, "cooldownDays": 120, "urgency": "URGENT",
  "headline": "Cyberattaque contre des services publics",
  "notificationText": "Plusieurs hôpitaux et administrations sont touchés.",
  "modifiers": [mod("quality.security", 0.35, 0.65, 2.5, 0.5)],
  "immediateEffects": [E("quality.health", -0.005), E("economy.businessConfidence", -0.005)],
  "message": {"template": "cyberattack", "sender": "MINISTER", "ministry": "interior", "responseDays": 3, "defaultOption": "contain",
   "options": [
    opt("invest", "Plan national de cybersécurité", "Coût : 500 M€ sur un an", [E("budget.oneOff", 0.5, days=365), E("quality.security", 0.01, days=180), E("opinion.national", 0.004)]),
    opt("contain", "Gérer la crise avec les moyens existants", "Aucun coût supplémentaire", [E("quality.security", -0.003)]),
   ]}},
 {"id": "rating_downgrade", "category": "ECONOMY", "scope": "NATIONAL",
  "baseDailyProbability": 0.004, "cooldownDays": 180, "urgency": "URGENT",
  "headline": "Une agence de notation dégrade la note de la France",
  "notificationText": "Les marchés s'inquiètent de la trajectoire des finances publiques.",
  "conditions": [{"variable": "economy.debtRatio", "min": 1.2}, {"variable": "economy.deficitRatio", "min": 0.05}],
  "modifiers": [mod("economy.deficitRatio", 0.05, 0.08, 1.0, 3.0)],
  "immediateEffects": [E("economy.businessConfidence", -0.03), E("economy.consumerConfidence", -0.01), E("opinion.national", -0.005)]},
]

government = [
 {"id": "minister_scandal", "category": "GOVERNMENT", "scope": "MINISTER",
  "baseDailyProbability": 0.00012, "cooldownDays": 45, "scopeCooldownDays": 365, "urgency": "URGENT",
  "headline": "Révélations dans la presse : {minister} mis en cause",
  "notificationText": "Le Premier ministre souhaite connaître votre position.",
  "modifiers": [mod("scope.integrity", 0.15, 0.8, 5.0, 0.15)],
  "immediateEffects": [E("subject.popularity", -0.15), E("opinion.national", -0.008)],
  "message": {"template": "minister_scandal", "sender": "PRIME_MINISTER", "responseDays": 3, "defaultOption": "support",
   "options": [
    opt("support", "Maintenir le ministre", "Fidélité accrue, mais l'affaire pèse sur l'opinion", [E("subject.loyalty", 0.15), E("opinion.national", -0.012, days=20), E("government.parliamentSupport", -0.01)]),
    opt("dismiss", "Demander sa démission", "Le ministère passera en intérim", [E("subject.dismiss", 1.0), E("opinion.national", 0.006)]),
   ]}},
]
for name, events in [("territory", territory), ("infrastructure", infrastructure), ("society", society), ("government", government)]:
    json.dump({"events": events}, open(f"events/{name}.json", "w"), ensure_ascii=False, indent=1)
print("events ok")
