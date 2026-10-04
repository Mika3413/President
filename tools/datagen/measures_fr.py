"""Mesures de crise et de prévention (France) -> assets/data/countries/FRA/measures.json.

Une mesure dure (durée proposée ou jusqu'à sa levée), coûte au lancement et/ou chaque mois, a des
effets au début, chaque jour (« daily ») et à la fin, et modifie la probabilité et l'ampleur des
événements qu'elle vise (« affects »). Les mesures locales s'appliquent au département choisi
(« local.<champ> »). Les risques regroupent des événements pour l'affichage.
"""
import glob, json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
G = "opinion.group."


def e(target, amount, days=0):
    d = {"target": target, "amount": amount}
    if days:
        d["days"] = days
    return d


def hit(event, probability=1.0, intensity=1.0):
    return {"event": event, "probability": probability, "intensity": intensity}


FAMILIES = [
    ("prevention", "Prévention", "⊘", "Agir avant le drame : feux, crues, canicule, froid, épidémies, attentats, cyber."),
    ("health", "Santé publique", "⚕", "Confinement, couvre-feu, masques, plan blanc, frontières sanitaires."),
    ("disaster", "Secours", "✚", "Plan ORSEC, évacuations, armée en renfort, fonds d'urgence."),
    ("security", "Ordre public", "⚑", "Vigipirate, Sentinelle, interdictions, contrôles aux frontières."),
    ("energy", "Énergie", "⚡", "Délestages, sobriété, rationnement."),
    ("social", "Dialogue social", "☎", "Médiation, conférence sociale, réquisitions."),
    ("economy", "Économie de crise", "€", "Chômage partiel, soutien de trésorerie."),
]

M = []


def measure(family, id, label, icon, description, days=0, cost=0.0, monthly=0.0, start=(), daily=(), end=(), affects=(),
            local=False, requires=(), requires_text="", confirm=False, cooldown=0, exclusive=()):
    m = {"id": id, "label": label, "icon": icon, "family": family, "description": description, "defaultDays": days}
    if local: m["local"] = True
    if cost: m["costBillions"] = cost
    if monthly: m["costPerMonthBillions"] = monthly
    for k, v in (("start", start), ("daily", daily), ("end", end), ("affects", affects), ("requires", requires), ("exclusive", exclusive)):
        if v: m[k] = list(v)
    if requires_text: m["requiresText"] = requires_text
    if confirm: m["confirm"] = True
    if cooldown: m["cooldownDays"] = cooldown
    M.append(m)


# --- Prévention -------------------------------------------------------------------------------
measure("prevention", "fire_prevention", "Plan national de prévention des feux", "♣",
        "Débroussaillage obligatoire, patrouilles, surveillance par drones et guet aérien pendant l'été.",
        days=90, cost=0.05, monthly=0.02, affects=[hit("wildfire", 0.6, 0.6)])
measure("prevention", "canadair", "Prépositionner Canadair et colonnes de pompiers", "✈",
        "Les moyens aériens et des renforts de pompiers sont postés au plus près du risque.",
        local=True, days=60, monthly=0.01, affects=[hit("wildfire", 1.0, 0.45)])
measure("prevention", "forest_ban", "Interdire l'accès aux massifs forestiers", "⊘",
        "Randonnée, travaux et barbecues interdits en forêt : moins de départs de feu, touristes mécontents.",
        local=True, days=30, daily=[e("local.approval", -0.0004)], affects=[hit("wildfire", 0.4)])
measure("prevention", "flood_watch", "Vigilance crues : digues renforcées et alertes", "≈",
        "Surveillance des cours d'eau, sacs de sable, alertes par SMS, digues consolidées en urgence.",
        local=True, days=60, cost=0.02, affects=[hit("flood", 0.75, 0.5), hit("cyclone", 1.0, 0.7), hit("storm", 1.0, 0.8)])
measure("prevention", "heat_plan", "Plan canicule", "☀",
        "Salles rafraîchies, appels aux personnes âgées isolées, horaires de travail aménagés.",
        days=60, monthly=0.01, start=[e(G + "seniors", 0.004)], affects=[hit("heatwave", 1.0, 0.5)])
measure("prevention", "cold_plan", "Plan grand froid", "❄",
        "Hébergement d'urgence, maraudes, appels à la sobriété électrique aux heures de pointe.",
        days=60, monthly=0.02, start=[e(G + "low_income", 0.003)], affects=[hit("cold_wave", 1.0, 0.6), hit("blackout", 0.7)])
measure("prevention", "water_restrictions", "Restrictions d'eau", "♒",
        "Arrosage, lavage et remplissage des piscines interdits ; priorité à l'eau potable et aux élevages.",
        days=60, daily=[e(G + "rural", -0.0003), e("quality.agriculture", -0.0001)], affects=[hit("drought", 1.0, 0.6), hit("dam_drought", 0.7)])
measure("prevention", "vaccination", "Grande campagne de vaccination", "✚",
        "Vaccination gratuite en pharmacie, à l'école et en entreprise.",
        days=90, cost=0.3, daily=[e("quality.health", 0.0001)], affects=[hit("epidemic", 0.5, 0.7), hit("pandemic_wave", 0.6, 0.7)])
measure("prevention", "strategic_stocks", "Constituer des stocks stratégiques", "▣",
        "Masques, médicaments essentiels, carburant et pièces critiques stockés pour tenir des mois.",
        days=180, cost=0.4, affects=[hit("drug_shortage", 0.4, 0.6), hit("fuel_shortage", 0.7, 0.5), hit("pandemic_wave", 1.0, 0.85)])
measure("prevention", "cyber_shield", "Cyberbouclier de l'ANSSI", "◎",
        "Audits d'urgence, sauvegardes hors ligne, équipes de réponse dans les hôpitaux et collectivités.",
        days=180, monthly=0.1, affects=[hit("cyberattack", 0.5, 0.6), hit("hybrid_cyber", 1.0, 0.6), hit("health_data_leak", 0.5, 0.6)])
measure("prevention", "seveso_inspections", "Inspections renforcées des sites à risque", "☢",
        "Centrales, raffineries et usines chimiques contrôlées de fond en comble ; les industriels râlent.",
        days=180, monthly=0.02, start=[e("economy.businessConfidence", -0.003)],
        affects=[hit("nuclear_incident", 0.6, 0.7), hit("refinery_accident", 0.5, 0.7)])
measure("prevention", "social_conference", "Conférence sociale permanente", "☎",
        "Syndicats et patronat réunis chaque semaine : on désamorce avant de bloquer.",
        days=90, affects=[hit("national_strike", 0.6), hit("rail_strike", 0.7), hit("teachers_strike", 0.7), hit("port_strike", 0.7)])
measure("prevention", "community_policing", "Police de proximité dans les quartiers", "⚑",
        "Des policiers connus des habitants, des médiateurs et des éducateurs de rue.",
        days=180, monthly=0.05, affects=[hit("urban_riots", 0.6, 0.8), hit("police_incident", 0.7)])

# --- Santé publique ---------------------------------------------------------------------------
measure("health", "lockdown", "Confinement national", "⌂",
        "Chacun reste chez soi sauf nécessité. L'épidémie recule fortement, l'économie et le moral aussi.",
        days=30, monthly=3.0, confirm=True, cooldown=30,
        start=[e("economy.consumerConfidence", -0.03), e("opinion.national", 0.004)],
        daily=[e("economy.output", -0.0006), e("economy.unemployment", 0.00004), e("opinion.national", -0.0005), e(G + "young", -0.0004), e(G + "self_employed", -0.0006)],
        end=[e("economy.output", 0.003, 60), e("economy.consumerConfidence", 0.015)],
        affects=[hit("epidemic", 0.3, 0.5), hit("pandemic_wave", 0.2, 0.4), hit("city_demonstration", 0.2), hit("urban_riots", 0.5), hit("terror_attack", 0.6)])
measure("health", "local_lockdown", "Confinement local", "⌂",
        "Le département est confiné : la circulation du virus y est freinée, l'économie locale souffre.",
        local=True, days=21, monthly=0.05, cooldown=15,
        daily=[e("local.approval", -0.001), e("local.unemployment", 0.00005), e("economy.output", -0.00003)],
        affects=[hit("epidemic", 0.85, 0.85), hit("pandemic_wave", 0.85, 0.85)])
measure("health", "curfew", "Couvre-feu national (21 h – 6 h)", "☾",
        "Bars, restaurants et sorties le soir fermés. Moins dur qu'un confinement.",
        days=30, cooldown=15, daily=[e("economy.output", -0.0002), e(G + "young", -0.0004), e(G + "self_employed", -0.0003)],
        affects=[hit("epidemic", 0.7, 0.8), hit("pandemic_wave", 0.6, 0.8), hit("urban_riots", 0.4)])
measure("health", "masks", "Port du masque obligatoire", "◐",
        "Dans les transports, les commerces et les lieux clos.",
        days=60, daily=[e("opinion.national", -0.0001)], affects=[hit("epidemic", 0.7, 0.85), hit("pandemic_wave", 0.8, 0.8)])
measure("health", "white_plan", "Plan blanc dans les hôpitaux", "✚",
        "Opérations non urgentes reportées, personnels rappelés, lits supplémentaires ouverts.",
        days=30, monthly=0.1, daily=[e("quality.health", 0.0002), e(G + "civil_servants", -0.0002)],
        affects=[hit("epidemic", 1.0, 0.7), hit("pandemic_wave", 1.0, 0.75), hit("terror_attack", 1.0, 0.85)])
measure("health", "health_borders", "Fermeture sanitaire des frontières", "⊠",
        "Voyageurs testés et mis en quarantaine ; vols suspendus depuis les zones touchées.",
        days=30, confirm=True, start=[e("alliance.EU.DISAGREEMENT", -0.01)],
        daily=[e("economy.output", -0.0003), e("economy.businessConfidence", -0.0002)],
        affects=[hit("new_virus", 0.5), hit("pandemic_wave", 0.6, 0.9)])
measure("health", "health_emergency", "État d'urgence sanitaire", "⚠",
        "Cadre légal d'exception : le gouvernement peut décider vite, l'Assemblée s'agace.",
        days=60, confirm=True, daily=[e("government.parliamentSupport", -0.0002)], affects=[hit("pandemic_wave", 1.0, 0.85), hit("epidemic", 1.0, 0.85)])

# --- Secours ----------------------------------------------------------------------------------
measure("disaster", "orsec", "Déclencher le plan ORSEC", "✚",
        "Le préfet coordonne pompiers, gendarmes, SAMU et collectivités sous une direction unique.",
        local=True, days=15, cost=0.02, start=[e("local.approval", 0.01)],
        affects=[hit("flood", 1.0, 0.6), hit("storm", 1.0, 0.6), hit("wildfire", 1.0, 0.6), hit("cyclone", 1.0, 0.6)])
measure("disaster", "evacuation", "Évacuer la population", "⇢",
        "Les habitants menacés sont mis à l'abri : beaucoup moins de victimes, une économie locale à l'arrêt.",
        local=True, days=7, cost=0.03, start=[e("local.approval", -0.005)], daily=[e("economy.output", -0.00005)],
        affects=[hit("flood", 1.0, 0.4), hit("wildfire", 1.0, 0.4), hit("cyclone", 1.0, 0.4), hit("storm", 1.0, 0.5)])
measure("disaster", "army_relief", "Déployer l'armée en soutien des secours", "⚔",
        "Génie, hélicoptères et hôpitaux de campagne au service de la population.",
        days=30, monthly=0.05, daily=[e("military.readiness", -0.0005)], start=[e("opinion.national", 0.003)],
        affects=[hit("flood", 1.0, 0.75), hit("storm", 1.0, 0.75), hit("wildfire", 1.0, 0.75), hit("cyclone", 1.0, 0.7)])
measure("disaster", "disaster_fund", "Fonds d'urgence catastrophes naturelles", "€",
        "Indemnisation accélérée des sinistrés et des communes, avances sur assurances.",
        days=90, cost=0.3, start=[e(G + "rural", 0.004), e("opinion.national", 0.002)], affects=[hit("storm", 1.0, 0.9), hit("flood", 1.0, 0.9)])

# --- Ordre public -----------------------------------------------------------------------------
measure("security", "vigipirate", "Vigipirate « urgence attentat »", "⚑",
        "Fouilles, militaires devant les écoles et les lieux de culte, événements annulés.",
        days=30, monthly=0.05, daily=[e("quality.security", 0.0002), e("economy.consumerConfidence", -0.0001)], affects=[hit("terror_attack", 0.6, 0.7)])
measure("security", "sentinelle", "Renforcer l'opération Sentinelle", "⚔",
        "Dix mille soldats patrouillent dans les grandes villes.",
        days=90, monthly=0.08, daily=[e("military.readiness", -0.0003)], affects=[hit("terror_attack", 0.7, 0.8)])
measure("security", "gathering_ban", "Interdire les rassemblements", "⊘",
        "Manifestations et grands événements interdits : l'ordre est maintenu, les libertés en débat.",
        days=15, confirm=True, daily=[e(G + "young", -0.0005), e("government.parliamentSupport", -0.0002)],
        affects=[hit("city_demonstration", 0.3), hit("urban_riots", 0.6), hit("terror_attack", 0.8)])
measure("security", "local_curfew", "Couvre-feu local", "☾",
        "Dans le département touché par les violences : rues vidées la nuit.",
        local=True, days=10, daily=[e("local.crime", -0.0005), e("local.approval", -0.0005)], affects=[hit("urban_riots", 0.3, 0.6)])
measure("security", "security_borders", "Rétablir les contrôles aux frontières", "⊠",
        "Contrôles d'identité aux frontières intérieures de l'espace Schengen.",
        days=60, start=[e("alliance.EU.DISAGREEMENT", -0.01)], daily=[e("economy.output", -0.0001)],
        affects=[hit("terror_attack", 0.85), hit("refugee_crisis", 1.0, 0.7)])

# --- Énergie ----------------------------------------------------------------------------------
measure("energy", "load_shedding", "Délestages tournants préventifs", "⚡",
        "Deux heures de coupure programmée par quartier, pour éviter la panne générale.",
        days=15, requires=[{"variable": "energy.margin", "max": 0.08}], requires_text="quand la marge électrique est faible",
        daily=[e("economy.output", -0.0003), e("opinion.national", -0.0004)], affects=[hit("blackout", 0.2, 0.5)])
measure("energy", "sobriety", "Sobriété énergétique obligatoire", "♻",
        "Chauffage limité à 19 °C dans les bâtiments publics, vitrines éteintes la nuit.",
        days=90, daily=[e("economy.consumerConfidence", -0.0001)], affects=[hit("blackout", 0.6), hit("cold_wave", 1.0, 0.85), hit("gas_price_spike", 1.0, 0.8)])
measure("energy", "fuel_rationing", "Rationnement du carburant", "⛁",
        "Trente litres par plein, priorité aux soignants, aux transporteurs et aux agriculteurs.",
        days=15, daily=[e(G + "rural", -0.0005)], affects=[hit("fuel_shortage", 1.0, 0.5), hit("fuel_blockade", 0.6, 0.6)])

# --- Dialogue social --------------------------------------------------------------------------
measure("social", "mediator", "Nommer un médiateur national", "☎",
        "Une personnalité respectée réunit les parties pour sortir d'un conflit.",
        days=60, cost=0.005, affects=[hit("national_strike", 0.7, 0.8), hit("rail_strike", 0.7, 0.8), hit("strike_spreads", 0.6), hit("teachers_strike", 0.7, 0.8)])
measure("social", "requisition", "Réquisitionner les grévistes des secteurs vitaux", "⚠",
        "Raffineries, énergie, soins : le service est assuré, la colère syndicale explose.",
        days=15, confirm=True, daily=[e(G + "civil_servants", -0.0008), e(G + "private_employees", -0.0003)],
        affects=[hit("fuel_blockade", 0.4, 0.5), hit("strike_spreads", 1.0, 0.6), hit("fuel_shortage", 1.0, 0.6)])

# --- Économie de crise ------------------------------------------------------------------------
measure("economy", "short_time_work", "Chômage partiel massif", "€",
        "L'État paie une grande partie des salaires des entreprises à l'arrêt : les emplois sont sauvés.",
        days=90, monthly=1.5, daily=[e("economy.unemployment", -0.00004), e("economy.consumerConfidence", 0.0001), e(G + "private_employees", 0.0002)])
measure("economy", "business_support", "Prêts garantis et reports de charges", "◆",
        "Trésorerie pour les entreprises en difficulté, reports de cotisations.",
        days=90, monthly=0.5, daily=[e("economy.businessConfidence", 0.0002), e(G + "self_employed", 0.0002)])

# --- Lassitude et contrôle juridique ---------------------------------------------------------
# fatigue : perte de respect par mois ; emergency : prorogation votée par le Parlement après 12 jours ;
# contestable : recours possible devant le Conseil d'État.
LEGAL = {
    "lockdown": (0.25, True, True), "local_lockdown": (0.2, False, True), "curfew": (0.18, True, True),
    "local_curfew": (0.15, False, True), "masks": (0.08, False, False), "health_borders": (0.05, True, True),
    "health_emergency": (0.06, True, True), "gathering_ban": (0.15, True, True), "forest_ban": (0.12, False, True),
    "water_restrictions": (0.08, False, False), "fuel_rationing": (0.2, False, True), "load_shedding": (0.2, False, False),
    "sobriety": (0.1, False, False), "requisition": (0.25, False, True), "sentinelle": (0.04, False, False),
    "security_borders": (0.04, False, True), "evacuation": (0.3, False, False),
}
for m in M:
    if m["id"] in LEGAL:
        fatigue, emergency, contestable = LEGAL.pop(m["id"])
        if fatigue: m["fatigue"] = fatigue
        if emergency: m["emergency"] = True
        if contestable: m["contestable"] = True
assert not LEGAL, LEGAL

RISKS = [
    ("fire", "Incendies de forêt", "♣", "Chaleur, sécheresse et vent font monter le risque l'été.", ["wildfire"]),
    ("flood", "Crues et tempêtes", "≈", "Pluies d'automne et d'hiver, cyclones outre-mer.", ["flood", "storm", "cyclone"]),
    ("heat", "Canicule et sécheresse", "☀", "Vagues de chaleur et manque d'eau.", ["heatwave", "drought", "dam_drought"]),
    ("cold", "Froid et électricité", "❄", "Vague de froid, réseau électrique sous tension.", ["cold_wave", "blackout"]),
    ("epidemic", "Épidémies", "⚕", "Virus saisonniers, nouvelles maladies, épizooties.", ["epidemic", "new_virus", "pandemic_wave", "bird_flu"]),
    ("terror", "Terrorisme", "⚑", "Menace terroriste sur les grandes villes.", ["terror_attack"]),
    ("unrest", "Colère sociale et émeutes", "☎", "Grèves, manifestations, violences urbaines.", ["urban_riots", "city_demonstration", "police_incident", "national_strike", "rail_strike", "teachers_strike"]),
    ("cyber", "Cyberattaques", "◎", "Rançongiciels, espionnage, attaques d'États hostiles.", ["cyberattack", "cyber_espionage", "health_data_leak"]),
    ("supply", "Pénuries", "▣", "Carburant, médicaments, gaz.", ["fuel_shortage", "drug_shortage", "gas_price_spike"]),
    ("industrial", "Accidents industriels", "☢", "Centrales, raffineries, sites chimiques.", ["nuclear_incident", "refinery_accident"]),
]

events = set()
for f in glob.glob(os.path.join(ROOT, "events", "*.json")):
    if f.endswith(("intensity.json", "responses.json")):
        continue
    events |= {x["id"] for x in json.load(open(f))["events"]}
missing = sorted({a["event"] for m in M for a in m.get("affects", [])} - events) + sorted({ev for r in RISKS for ev in r[4]} - events)
assert not missing, missing

out = {
    "_doc": "Mesures de crise et de prévention. Généré par tools/datagen/measures_fr.py.",
    "families": [{"id": i, "label": l, "icon": ic, "description": d} for i, l, ic, d in FAMILIES],
    "measures": M,
    "risks": [{"id": i, "label": l, "icon": ic, "description": d, "events": ev} for i, l, ic, d, ev in RISKS],
}
path = os.path.join(ROOT, "countries", "FRA", "measures.json")
json.dump(out, open(path, "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(len(M), "mesures,", len(RISKS), "risques")
