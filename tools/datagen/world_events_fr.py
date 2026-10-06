"""La vie des autres pays -> assets/data/diplomacy/world_events.json.

Chaque mois, chaque pays étranger peut connaître des événements intérieurs (élections anticipées,
grèves, scandales, récession, catastrophes, coups d'État...) selon sa situation (popularité du
dirigeant, croissance, chômage, inflation, dette, guerre, tempérament du dirigeant), et les pays
interagissent entre eux (accords, crises diplomatiques, embargos, visites, incidents...).
Chaque événement a des conséquences : sur le pays (popularité, activité, chômage, prix), sur la
relation entre les deux pays, sur les cours des matières premières, et parfois sur la France.
Le tout alimente le « Journal du monde ».

Textes : {A}, {le_A}, {de_A}, {en_A} (et {B}... pour le second pays).
Cibles : A.approval, A.output, A.unemployment, A.inflation, A.confidence, A.leader (alternance),
AB.<SOUVENIR> (relation entre A et B), memory.{A}.<SOUVENIR> (relation de A envers la France),
commodity.<id> (cours), et toute cible française habituelle (sector.x, economy.x, opinion...).
"""
import json, os

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
EVENTS = []


def e(target, amount): return {"target": target, "amount": amount}
def c(var, mn=None, mx=None):
    d = {"var": var}
    if mn is not None: d["min"] = mn
    if mx is not None: d["max"] = mx
    return d


def ev(id, category, headline, detail, chance, effects, conditions=(), bilateral=False, france="", tone="NEUTRAL", major=False):
    EVENTS.append({"id": id, "category": category, "headline": headline, "detail": detail, "chance": chance,
                   "effects": effects, "conditions": list(conditions), "bilateral": bilateral,
                   "france": france, "tone": tone, "major": major})


# ---------------------------------------------------------------------------------------------
# Politique intérieure
# ---------------------------------------------------------------------------------------------
ev("snap_election", "politique", "{A} : élections anticipées", "Affaibli dans les sondages, le pouvoir {de_A} convoque les électeurs avant l'heure.",
   0.05, [e("A.approval", 0.05), e("A.confidence", -0.01)], [c("approval", mx=0.35)])
ev("government_collapse", "politique", "{A} : le gouvernement tombe", "Une motion de censure renverse le gouvernement {de_A} ; le pays cherche une majorité.",
   0.04, [e("A.approval", -0.04), e("A.confidence", -0.02), e("A.output", -0.002)], [c("approval", mx=0.38)], tone="BAD")
ev("coalition_deal", "politique", "{A} : accord de coalition", "Après des semaines de négociations, une coalition se forme {en_A}.",
   0.03, [e("A.approval", 0.03), e("A.confidence", 0.01)], [c("approval", 0.3, 0.55)])
ev("corruption_scandal", "politique", "Scandale de corruption {en_A}", "Des proches du pouvoir {de_A} sont mis en cause ; la presse se déchaîne.",
   0.04, [e("A.approval", -0.06)], tone="BAD")
ev("minister_resigns", "politique", "{A} : démission fracassante d'un ministre", "Le ministre claque la porte en dénonçant la ligne du gouvernement.",
   0.04, [e("A.approval", -0.02)])
ev("populist_surge", "politique", "{A} : poussée populiste dans les sondages", "Un parti antisystème caracole en tête des intentions de vote {en_A}.",
   0.04, [e("A.confidence", -0.01), e("memory.{A}.DISAGREEMENT", -0.01)], [c("unemployment", mn=0.08)], france="Les relations avec Paris pourraient se tendre si ce parti l'emporte.")
ev("referendum_abroad", "politique", "{A} : référendum historique", "Les électeurs {de_A} sont appelés à trancher une question qui divise le pays.",
   0.015, [e("A.approval", -0.02)])
ev("constitutional_crisis", "politique", "{A} : crise constitutionnelle", "Le pouvoir {de_A} s'affronte avec la Cour suprême ; l'opposition parle de dérive.",
   0.015, [e("A.approval", -0.04), e("A.confidence", -0.02), e("memory.{A}.DISAGREEMENT", -0.01)], [c("aggressiveness", mn=0.6)], tone="BAD",
   france="Les capitales européennes s'inquiètent de l'état de droit.")
ev("leader_health", "politique", "{A} : rumeurs sur la santé du dirigeant", "Absent depuis des semaines, le chef {de_A} alimente les spéculations.",
   0.01, [e("A.approval", -0.02), e("A.confidence", -0.01)])
ev("mass_protests", "société", "{A} : manifestations massives contre le pouvoir", "Des centaines de milliers de personnes défilent dans les rues {de_A}.",
   0.05, [e("A.approval", -0.05), e("A.output", -0.002)], [c("approval", mx=0.35)], tone="BAD")
ev("coup_attempt", "politique", "{A} : tentative de coup d'État", "Des militaires tentent de s'emparer du pouvoir {en_A}.",
   0.006, [e("A.approval", -0.1), e("A.output", -0.01), e("A.confidence", -0.06), e("A.leader", 1)], [c("approval", mx=0.25), c("eu", mx=0)],
   tone="BAD", major=True, france="Paris doit choisir : condamner, attendre, ou parler aux nouveaux maîtres du pays.")
ev("leader_landslide", "politique", "{A} : le dirigeant au sommet de sa popularité", "Une série de succès porte le chef {de_A} à des records dans les sondages.",
   0.03, [e("A.approval", 0.04), e("A.confidence", 0.01)], [c("approval", mn=0.55)], tone="GOOD")
ev("opposition_jailed", "politique", "{A} : le chef de l'opposition arrêté", "Le principal opposant {de_A} est placé en détention ; ses partisans crient au procès politique.",
   0.02, [e("A.approval", -0.02), e("memory.{A}.DISAGREEMENT", -0.02), e("opinion.group.young", -0.002)], [c("aggressiveness", mn=0.6), c("eu", mx=0)],
   tone="BAD", france="Les ONG demandent à la France de réagir.")

# ---------------------------------------------------------------------------------------------
# Économie
# ---------------------------------------------------------------------------------------------
ev("recession_abroad", "économie", "{A} : entrée en récession", "Deux trimestres de recul : l'économie {de_A} se contracte.",
   0.08, [e("A.confidence", -0.03), e("A.unemployment", 0.003), e("sector.industry", -0.002), e("sector.luxury", -0.002)], [c("growth", mx=0.0)], tone="BAD",
   france="Nos exportateurs vendent moins {en_A}.")
ev("boom_abroad", "économie", "Croissance record {en_A}", "L'économie {de_A} tourne à plein régime ; les entreprises françaises en profitent.",
   0.06, [e("A.confidence", 0.02), e("sector.luxury", 0.002), e("sector.aerospace", 0.001)], [c("growth", mn=0.025)], tone="GOOD",
   france="Davantage de commandes pour nos exportateurs.")
ev("inflation_spike_abroad", "économie", "{A} : l'inflation s'emballe", "Les prix flambent {en_A} ; la banque centrale relève ses taux en urgence.",
   0.06, [e("A.approval", -0.03), e("A.confidence", -0.02), e("A.output", -0.002)], [c("inflation", mn=0.05)], tone="BAD")
ev("debt_scare", "économie", "{A} : les marchés doutent de la dette", "Les taux d'emprunt {de_A} s'envolent ; les agences menacent de dégrader sa note.",
   0.05, [e("A.confidence", -0.03), e("A.output", -0.003), e("sector.banking", -0.002)], [c("debt", mn=1.1)], tone="BAD",
   france="Contagion possible : les banques françaises sont exposées.")
ev("currency_crash", "économie", "{A} : effondrement de la monnaie", "La devise {de_A} perd un quart de sa valeur en quelques jours.",
   0.02, [e("A.inflation", 0.02), e("A.approval", -0.05), e("A.confidence", -0.04)], [c("eu", mx=0), c("inflation", mn=0.04)], tone="BAD")
ev("big_strike_abroad", "société", "{A} : grève générale", "Les syndicats {de_A} paralysent les transports et l'industrie.",
   0.04, [e("A.output", -0.003), e("A.approval", -0.02)], [c("unemployment", mn=0.06)])
ev("tax_reform_abroad", "économie", "{A} : baisse de l'impôt sur les sociétés", "Pour attirer les investisseurs, {le_A} taille dans l'impôt des entreprises.",
   0.02, [e("A.confidence", 0.02), e("economy.businessConfidence", -0.002)], france="Concurrence fiscale : certaines entreprises françaises sont tentées de s'y installer.")
ev("industrial_plan_abroad", "économie", "{A} : plan industriel géant", "Des centaines de milliards de subventions pour relocaliser usines et batteries.",
   0.015, [e("A.confidence", 0.02), e("A.output", 0.003), e("sector.industry", -0.003)], [c("gdp", mn=1500)],
   france="Nos industriels redoutent une fuite des investissements.")
ev("bank_failure_abroad", "économie", "{A} : faillite d'une grande banque", "Une banque majeure {de_A} s'effondre ; l'État intervient en catastrophe.",
   0.01, [e("A.confidence", -0.05), e("A.output", -0.005), e("sector.banking", -0.005)], tone="BAD", major=True,
   france="La Bourse de Paris chute par contagion.")
ev("tech_breakthrough", "économie", "{A} : percée technologique", "Un groupe {de_A} dévoile une innovation qui bouscule son secteur.",
   0.02, [e("A.confidence", 0.02), e("sector.tech", -0.001)], [c("gdp", mn=1000)], tone="GOOD")
ev("oil_output_cut", "économie", "{A} : baisse de la production de pétrole", "Pour soutenir les prix, {le_A} ferme les vannes.",
   0.05, [e("commodity.oil", 0.08), e("A.output", 0.002)], [c("producer_oil", mn=0.05)], france="Le plein va coûter plus cher en France.", tone="BAD")
ev("oil_output_rise", "économie", "{A} : hausse de la production de pétrole", "Pour gagner des parts de marché, la production repart à la hausse ; le baril recule.",
   0.04, [e("commodity.oil", -0.07)], [c("producer_oil", mn=0.05)], france="Bonne nouvelle pour le prix de l'essence.", tone="GOOD")
ev("gas_cut", "économie", "{A} : livraisons de gaz réduites", "Officiellement pour maintenance, les livraisons de gaz {de_A} baissent fortement.",
   0.03, [e("commodity.gas", 0.12), e("memory.{A}.DISAGREEMENT", -0.01)], [c("producer_gas", mn=0.05), c("relationFrance", mx=0.45)], tone="BAD",
   france="Les factures de gaz montent en Europe.")
ev("harvest_failure", "économie", "{A} : récolte catastrophique", "Sécheresse et maladies : la récolte de blé {de_A} s'effondre.",
   0.03, [e("commodity.wheat", 0.08), e("A.approval", -0.02)], [c("producer_wheat", mn=0.05)], france="Le prix du pain pourrait monter ; nos céréaliers vendent plus cher.")
ev("bumper_harvest", "économie", "{A} : récolte record", "Les silos {de_A} débordent ; les cours du blé reculent.",
   0.03, [e("commodity.wheat", -0.06), e("sector.agrifood", -0.001)], [c("producer_wheat", mn=0.05)], france="Nos agriculteurs vendent moins cher.")
ev("mine_strike", "économie", "{A} : grève dans les mines", "Les mineurs {de_A} cessent le travail ; le cuivre et le lithium s'envolent.",
   0.02, [e("commodity.copper", 0.08), e("commodity.lithium", 0.06)], [c("producer_copper", mn=0.05)])
ev("gold_rush", "économie", "Ruée vers l'or après des tensions {en_A}", "Les investisseurs se réfugient dans l'or.",
   0.02, [e("commodity.gold", 0.05)], [c("atWar", mn=1)])

# ---------------------------------------------------------------------------------------------
# Société et catastrophes
# ---------------------------------------------------------------------------------------------
ev("earthquake_abroad", "catastrophe", "Séisme meurtrier {en_A}", "Un violent tremblement de terre fait de nombreuses victimes {en_A}.",
   0.01, [e("A.output", -0.004), e("A.approval", 0.02)], [c("seismic", mn=1)], tone="BAD", major=True,
   france="Un geste de solidarité serait apprécié : envoyez la sécurité civile (Décider → International).")
ev("floods_abroad", "catastrophe", "Inondations historiques {en_A}", "Des régions entières {de_A} sont sous les eaux.",
   0.025, [e("A.output", -0.002), e("A.approval", -0.01)], tone="BAD")
ev("wildfires_abroad", "catastrophe", "Méga-feux {en_A}", "Des incendies géants ravagent les forêts {de_A}.",
   0.02, [e("A.output", -0.001)], [c("hot", mn=1)], tone="BAD")
ev("epidemic_abroad", "catastrophe", "Épidémie {en_A}", "Un virus se propage {en_A} ; l'OMS surveille la situation.",
   0.008, [e("A.output", -0.003), e("A.approval", -0.02), e("sector.tourism", -0.002)], tone="BAD",
   france="Le ministère de la Santé renforce la surveillance aux frontières.")
ev("terror_abroad", "société", "Attentat {en_A}", "Une attaque terroriste endeuille {le_A}.",
   0.012, [e("A.approval", 0.02), e("A.confidence", -0.01), e("memory.{A}.CRISIS_SOLIDARITY", 0.0)], tone="BAD",
   france="Un message de solidarité de l'Élysée est attendu.")
ev("migrant_wave", "société", "{A} : afflux de migrants à ses frontières", "Des milliers de personnes se pressent aux frontières {de_A}.",
   0.03, [e("A.approval", -0.02), e("demography.immigration", 0.01)], [c("neighborEurope", mn=1)],
   france="Une partie de ces arrivées pourrait gagner la France.")
ev("riots_abroad", "société", "{A} : émeutes dans les grandes villes", "Les quartiers populaires {de_A} s'embrasent après une bavure.",
   0.02, [e("A.approval", -0.03), e("A.output", -0.001)], [c("unemployment", mn=0.08)], tone="BAD")
ev("sport_win_abroad", "société", "{A} : victoire dans une grande compétition", "Liesse populaire {en_A} après une victoire sportive.",
   0.02, [e("A.approval", 0.02)], tone="GOOD")
ev("record_tourism", "société", "Afflux record de touristes {en_A}", "Des touristes qui auraient pu venir en France choisissent ce pays.",
   0.02, [e("A.output", 0.001), e("sector.tourism", -0.001)], [c("tourist", mn=1)])
ev("brain_drain", "société", "{A} : les jeunes diplômés s'en vont", "Faute d'emplois, les jeunes {de_A} partent à l'étranger, souvent en France.",
   0.02, [e("A.approval", -0.01), e("economy.potentialGrowth", 0.0001)], [c("unemployment", mn=0.1)])

# ---------------------------------------------------------------------------------------------
# Entre pays
# ---------------------------------------------------------------------------------------------
ev("trade_deal", "diplomatie", "Accord commercial entre {le_A} et {le_B}", "Les droits de douane tombent entre {le_A} et {le_B}.",
   0.25, [e("AB.AGREEMENT_SIGNED", 0.04), e("A.output", 0.001), e("B.output", 0.001)], [c("pairRelation", mn=0.5)], bilateral=True, tone="GOOD")
ev("diplomatic_spat", "diplomatie", "Crise diplomatique entre {le_A} et {le_B}", "Ambassadeurs rappelés, déclarations hostiles : le ton monte entre {le_A} et {le_B}.",
   0.3, [e("AB.DISAGREEMENT", -0.05)], [c("pairRelation", mx=0.45)], bilateral=True, tone="BAD")
ev("state_visit", "diplomatie", "Visite d'État : le dirigeant {de_A} reçu {en_B}", "Le dirigeant {de_A} est reçu en grande pompe {en_B}.",
   0.3, [e("AB.NEGOTIATION_GOODWILL", 0.04), e("A.approval", 0.01)], [c("pairRelation", mn=0.45)], bilateral=True, tone="GOOD")
ev("trade_war", "diplomatie", "Guerre commerciale entre {le_A} et {le_B}", "Taxes à 25 % de part et d'autre : {le_A} et {le_B} s'affrontent sur le commerce.",
   0.12, [e("AB.SANCTION", -0.06), e("A.output", -0.003), e("B.output", -0.003), e("A.confidence", -0.02), e("B.confidence", -0.02)],
   [c("pairRelation", mx=0.4), c("gdp", mn=1000), c("otherGdp", mn=1000)], bilateral=True, tone="BAD", major=True,
   france="Les entreprises françaises pourraient y gagner des marchés… ou être prises entre deux feux.")
ev("sanctions_abroad", "diplomatie", "{A} : sanctions contre {le_B}", "Gel des avoirs et embargo : {le_A} frappe {le_B} au portefeuille.",
   0.1, [e("AB.SANCTION", -0.08), e("B.output", -0.004), e("B.approval", 0.02)], [c("pairRelation", mx=0.3)], bilateral=True, tone="BAD")
ev("border_incident", "conflit", "Incident à la frontière entre {le_A} et {le_B}", "Des tirs ont été échangés ; chaque camp accuse l'autre.",
   0.08, [e("AB.THREAT", -0.08), e("A.approval", 0.01), e("B.approval", 0.01)], [c("pairRelation", mx=0.3), c("aggressiveness", mn=0.55), c("neighbors", mn=1)],
   bilateral=True, tone="BAD", major=True, france="Risque d'escalade : l'ONU pourrait être saisie.")
ev("spy_scandal", "diplomatie", "Affaire d'espionnage entre {le_A} et {le_B}", "Des diplomates {de_A} sont expulsés {de_B} pour espionnage.",
   0.15, [e("AB.DISAGREEMENT", -0.05)], [c("pairRelation", mx=0.5)], bilateral=True)
ev("arms_deal_abroad", "diplomatie", "{A} : contrat d'armement avec {le_B}", "Gros contrat d'armement : avions et blindés {de_A} pour {le_B}.",
   0.12, [e("AB.MILITARY_SUPPORT", 0.05), e("A.output", 0.001), e("sector.aerospace", -0.001)], [c("pairRelation", mn=0.5), c("arms", mn=1)],
   bilateral=True, france="Un marché perdu pour l'industrie française de défense.")
ev("aid_abroad", "diplomatie", "{A} : aide d'urgence pour {le_B}", "Aide financière et humanitaire {de_A} pour {le_B}.",
   0.15, [e("AB.AID", 0.05), e("B.approval", 0.01)], [c("pairRelation", mn=0.45), c("richer", mn=1)], bilateral=True, tone="GOOD")
ev("summit_failure", "diplomatie", "Échec du sommet entre {le_A} et {le_B}", "Les deux dirigeants se quittent sans accord, sur un constat de désaccord.",
   0.15, [e("AB.DISAGREEMENT", -0.03), e("A.approval", -0.01)], [c("pairRelation", 0.3, 0.6)], bilateral=True)
ev("energy_pact", "diplomatie", "Pacte énergétique entre {le_A} et {le_B}", "Gaz, pétrole ou électricité : {le_A} et {le_B} sécurisent leurs approvisionnements.",
   0.12, [e("AB.AGREEMENT_SIGNED", 0.05), e("commodity.gas", -0.02)], [c("pairRelation", mn=0.5)], bilateral=True, tone="GOOD")
ev("migration_dispute", "diplomatie", "Migrants : dispute entre {le_A} et {le_B}", "Chacun accuse l'autre de laisser passer les migrants.",
   0.1, [e("AB.DISAGREEMENT", -0.04)], [c("neighbors", mn=1)], bilateral=True)
ev("hostage_crisis", "conflit", "Ressortissants {de_A} retenus {en_B}", "Plusieurs citoyens {de_A} sont détenus {en_B} ; une crise s'ouvre.",
   0.05, [e("AB.THREAT", -0.06), e("A.approval", -0.01)], [c("pairRelation", mx=0.35)], bilateral=True, tone="BAD")
ev("cyber_attack_abroad", "conflit", "Cyberattaque massive contre {le_B}", "Les services {de_B} accusent {le_A} d'avoir paralysé hôpitaux et administrations.",
   0.08, [e("AB.THREAT", -0.07), e("B.output", -0.002)], [c("pairRelation", mx=0.35), c("aggressiveness", mn=0.5)], bilateral=True, tone="BAD")
ev("reconciliation", "diplomatie", "Réconciliation historique entre {le_A} et {le_B}", "Après des années de brouille, les deux pays tournent la page.",
   0.06, [e("AB.AGREEMENT_SIGNED", 0.08), e("A.approval", 0.02), e("B.approval", 0.02)], [c("pairRelation", 0.2, 0.45), c("openness", mn=0.55)],
   bilateral=True, tone="GOOD")
ev("military_exercise", "conflit", "Grandes manœuvres {de_A} près de {le_B}", "Des dizaines de milliers de soldats {de_A} manœuvrent aux portes {de_B}.",
   0.08, [e("AB.THREAT", -0.05), e("B.confidence", -0.01)], [c("pairRelation", mx=0.35), c("aggressiveness", mn=0.6), c("military", mn=20)],
   bilateral=True, tone="BAD", france="L'OTAN et l'UE suivent la situation de près.")

# ---------------------------------------------------------------------------------------------
# Contrôles
# ---------------------------------------------------------------------------------------------
TRADE = json.load(open(os.path.join(ROOT, "diplomacy", "trade.json")))
COMMODITIES = {x["id"] for x in TRADE["commodities"]}
SECTORS = {x["id"] for x in json.load(open(os.path.join(ROOT, "countries", "FRA", "sectors.json")))["sectors"]}
GROUPS = {x["id"] for x in json.load(open(os.path.join(ROOT, "countries", "FRA", "social_groups.json")))["groups"]}
VARS = {"approval", "growth", "unemployment", "inflation", "debt", "atWar", "aggressiveness", "openness", "eu", "nato", "relationFrance", "gdp",
        "otherGdp", "pairRelation", "neighbors", "richer", "arms", "military", "seismic", "hot", "tourist", "neighborEurope",
        "producer_oil", "producer_gas", "producer_wheat", "producer_copper"}
KINDS = {"AGREEMENT_SIGNED", "DISAGREEMENT", "NEGOTIATION_GOODWILL", "SANCTION", "THREAT", "MILITARY_SUPPORT", "AID", "CRISIS_SOLIDARITY", "CONDEMNATION"}
seen = set()
for x in EVENTS:
    assert x["id"] not in seen, x["id"]; seen.add(x["id"])
    assert x["category"] in {"politique", "économie", "société", "catastrophe", "diplomatie", "conflit"}, x["id"]
    for cd in x["conditions"]: assert cd["var"] in VARS, (x["id"], cd)
    for fx in x["effects"]:
        t = fx["target"]
        p = t.split(".")
        if p[0] in ("A", "B"): assert p[1] in {"approval", "output", "unemployment", "inflation", "confidence", "leader"}, t; assert p[0] == "A" or x["bilateral"], t
        elif p[0] == "AB": assert x["bilateral"] and p[1] in KINDS, t
        elif p[0] == "memory": assert p[1] == "{A}" and p[2] in KINDS, t
        elif p[0] == "commodity": assert p[1] in COMMODITIES, t
        elif p[0] == "sector": assert p[1] in SECTORS, t
        elif p[0] == "opinion": assert p[2] in GROUPS, t
        else: assert t in {"economy.businessConfidence", "economy.potentialGrowth", "demography.immigration"}, t
    if not x["bilateral"]: assert "{B}" not in x["headline"] + x["detail"] and "_B}" not in x["headline"] + x["detail"], x["id"]

with open(os.path.join(ROOT, "diplomacy", "world_events.json"), "w", encoding="utf-8") as f:
    json.dump({"_doc": __doc__.strip().splitlines()[0], "events": EVENTS}, f, ensure_ascii=False, indent=1)
    f.write("\n")
print(f"{len(EVENTS)} événements du monde")
