"""Sommets internationaux et votes à l'ONU (assets/data/events/summits.json, dialogue/fr/summits.json).

Chaque sommet revient au mois prévu ; un ou deux sujets sont à l'ordre du jour. La position prise
par la France pèse sur ses relations avec tout un bloc (effets « alliance.EU.KIND »), ou avec les
belligérants d'une guerre étrangère (« war.attackers.KIND », « war.defenders.KIND »).
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


def opt(id, label, hint, effects, outcome="NEUTRAL"):
    return {"id": id, "label": label, "hint": hint, "effects": list(effects), "outcome": outcome}


EVENTS, TEMPLATES = [], []
# Probabilité quotidienne pendant le mois du sommet : environ une chance sur deux qu'un sujet donné soit retenu.
P = 0.025


def summit(id, headline, text, months, template, default, options, extra_conditions=(), prob=P, urgency="IMPORTANT"):
    conditions = [{"variable": "season.month", "oneOf": months}] + list(extra_conditions)
    EVENTS.append({"id": id, "category": "DIPLOMACY", "scope": "NATIONAL", "baseDailyProbability": prob, "cooldownDays": 200,
                   "urgency": urgency, "headline": headline, "notificationText": text, "conditions": conditions,
                   "message": {"template": template["id"], "sender": "MINISTER", "ministry": "foreign", "responseDays": 4,
                               "defaultOption": default, "options": options}})
    TEMPLATES.append(template)


EU_MONTHS = [3, 6, 10, 12]
WAR = [{"variable": "military.foreignWar", "min": 1}]

# ================================ Conseil européen ==============================================
summit("eu_budget", "Conseil européen : bras de fer sur le budget de l'Union", "Les Vingt-Sept négocient le budget pluriannuel.", EU_MONTHS,
 letter("eu_budget",
  [V("Conseil européen : le budget de l'Union"), V("Budget européen : notre position"), V("Bruxelles : la négociation budgétaire")],
  [V("Le Conseil européen doit trancher le cadre financier pluriannuel de l'Union."),
   V("Les pays dits « frugaux » réclament une baisse des contributions, les pays du Sud davantage de solidarité."),
   V("La Commission propose un budget en hausse pour financer la défense et la transition écologique.")],
  [V("La France est contributrice nette : toute hausse pèsera sur nos finances publiques."),
   V("Notre position sera observée de près par Berlin, Rome et Madrid.")],
  [V("Je vous propose de soutenir la hausse, de défendre une stabilisation, ou d'exiger une baisse de notre contribution."),
   V("Votre arbitrage déterminera la ligne de notre délégation.")]),
 "stabilize", [
  opt("increase", "Soutenir un budget européen plus ambitieux", "Coût : 1,5 Md€ ; nos partenaires apprécient",
      [E("budget.oneOff", 1.5), E("alliance.EU.TALK_CORDIAL", 0.03), E(G + "urban", 0.002), E(G + "rural", -0.002)], "ACCEPTED"),
  opt("stabilize", "Défendre une stabilisation", "Position médiane", [E("alliance.EU.TALK_CORDIAL", 0.01)], "NEUTRAL"),
  opt("cut", "Exiger une baisse de notre contribution", "Économie : 800 M€ ; tensions avec nos partenaires",
      [E("budget.oneOff", -0.8), E("alliance.EU.TALK_TENSE", -0.03), E(G + "rural", 0.003)], "REFUSED")])

summit("eu_migration", "Conseil européen : le pacte sur les migrations", "Les dirigeants européens se divisent sur l'accueil des migrants.", EU_MONTHS,
 letter("eu_migration",
  [V("Conseil européen : les migrations"), V("Pacte migratoire : quelle ligne ?"), V("Migrations : la négociation européenne")],
  [V("Le Conseil européen examine un mécanisme de solidarité obligatoire pour l'accueil des demandeurs d'asile."),
   V("L'Italie et la Grèce réclament une répartition des arrivées ; la Pologne et la Hongrie s'y opposent."),
   V("Les arrivées par la Méditerranée ont fortement augmenté cette année.")],
  [V("Le sujet est explosif dans tous les pays, et chez nous aussi."),
   V("Un échec relancerait les contrôles aux frontières intérieures.")],
  [V("Je vous propose de soutenir la répartition obligatoire, de défendre un compromis, ou de privilégier le renforcement des frontières extérieures."),
   V("Votre arbitrage est attendu avant le sommet.")]),
 "compromise", [
  opt("solidarity", "Soutenir la répartition obligatoire", "Coût : 500 M€ ; pays du Sud reconnaissants",
      [E("budget.oneOff", 0.5), E("memory.ITA.NEGOTIATION_GOODWILL", 0.04), E("memory.GRC.NEGOTIATION_GOODWILL", 0.04), E("memory.ESP.NEGOTIATION_GOODWILL", 0.02),
       E("memory.POL.TALK_TENSE", -0.03), E(G + "rural", -0.004), E(G + "young", 0.003)], "ACCEPTED"),
  opt("compromise", "Défendre un compromis", "Équilibre", [E("alliance.EU.TALK_CORDIAL", 0.01)], "NEUTRAL"),
  opt("borders", "Privilégier les frontières extérieures", "Populaire chez certains ; pays du Sud déçus",
      [E("memory.ITA.TALK_TENSE", -0.02), E("memory.POL.NEGOTIATION_GOODWILL", 0.03), E(G + "rural", 0.004), E(G + "young", -0.003)], "REFUSED")])

summit("eu_defense", "Conseil européen : vers une défense européenne ?", "La question d'un fonds commun pour la défense est sur la table.", EU_MONTHS,
 letter("eu_defense",
  [V("Défense européenne : une occasion historique"), V("Conseil européen : la défense commune"), V("Autonomie stratégique européenne")],
  [V("Plusieurs États proposent un emprunt commun pour financer l'industrie de défense européenne."),
   V("La France plaide depuis longtemps pour l'autonomie stratégique ; c'est le moment de vérité."),
   V("Certains partenaires préfèrent acheter du matériel américain, plus rapidement disponible.")],
  [V("Notre industrie de défense serait la première bénéficiaire d'un fonds européen."),
   V("Les pays nordiques et baltes restent attachés au lien transatlantique avant tout.")],
  [V("Je vous propose de porter le projet, de le soutenir sans s'exposer, ou de privilégier les coopérations bilatérales."),
   V("La position de la France est très attendue.")]),
 "support", [
  opt("lead", "Porter le projet de défense européenne", "Coût : 2 Md€ ; industrie et partenaires",
      [E("budget.oneOff", 2.0), E("alliance.EU.NEGOTIATION_GOODWILL", 0.03), E("economy.businessConfidence", 0.003), E("military.readiness", 0.01, days=365)], "ACCEPTED"),
  opt("support", "Soutenir sans s'exposer", "Position prudente", [E("alliance.EU.TALK_CORDIAL", 0.01)], "NEUTRAL"),
  opt("bilateral", "Privilégier les coopérations bilatérales", "Aucun coût ; occasion manquée", [E("memory.DEU.TALK_TENSE", -0.02)], "REFUSED")])

summit("eu_sanctions_war", "Conseil européen : sanctions contre {aggressorThe}", "Les Vingt-Sept débattent d'un nouveau paquet de sanctions.", EU_MONTHS,
 letter("eu_sanctions_war",
  [V("Sanctions européennes contre {aggressorThe}"), V("Conseil européen : la guerre {victimIn}"), V("Un nouveau paquet de sanctions")],
  [V("La guerre menée par {aggressorThe} contre {victimThe} domine l'ordre du jour du Conseil européen."),
   V("La Commission propose un nouveau paquet de sanctions économiques."),
   V("Plusieurs pays, inquiets pour leur économie, freinent des quatre fers.")],
  [V("Des sanctions renforcées auraient un coût pour nos entreprises et nos prix de l'énergie."),
   V("Ne rien faire affaiblirait la crédibilité de l'Union.")],
  [V("Je vous propose de soutenir des sanctions fortes, un paquet limité, ou de vous y opposer."),
   V("Votre arbitrage est attendu.")]),
 "limited", [
  opt("strong", "Soutenir des sanctions fortes", "Coût économique ; alliés reconnaissants",
      [E("war.attackers.SANCTION", -0.06), E("war.defenders.MILITARY_SUPPORT", 0.04), E("alliance.EU.NEGOTIATION_GOODWILL", 0.02), E("economy.output", -0.0008, days=180)], "ACCEPTED"),
  opt("limited", "Un paquet limité", "Compromis", [E("war.attackers.TALK_TENSE", -0.02), E("war.defenders.TALK_CORDIAL", 0.01)], "PARTIAL"),
  opt("oppose", "S'opposer à de nouvelles sanctions", "Économie préservée ; isolement", [E("alliance.EU.TALK_TENSE", -0.03), E("war.defenders.TALK_TENSE", -0.04)], "REFUSED")],
 extra_conditions=WAR, prob=0.05, urgency="URGENT")

summit("eu_common_debt", "Conseil européen : un nouvel emprunt commun ?", "Le débat sur la dette européenne commune est relancé.", EU_MONTHS,
 letter("eu_common_debt",
  [V("Emprunt européen commun"), V("Conseil européen : la dette commune"), V("Mutualisation de la dette : notre position")],
  [V("Plusieurs États proposent un nouvel emprunt commun pour financer l'investissement européen."),
   V("L'Allemagne et les Pays-Bas restent réticents ; l'Italie et l'Espagne y sont favorables.")],
  [V("Un emprunt commun soulagerait nos finances publiques à moyen terme."),
   V("Le sujet divise profondément les Vingt-Sept.")],
  [V("Je vous propose de soutenir l'emprunt, de rester neutre, ou de vous y opposer."),
   V("Votre position pèsera dans la balance.")]),
 "neutral", [
  opt("support", "Soutenir l'emprunt commun", "Investissement européen ; pays du Nord réticents",
      [E("economy.businessConfidence", 0.004), E("memory.ITA.NEGOTIATION_GOODWILL", 0.03), E("memory.ESP.NEGOTIATION_GOODWILL", 0.03), E("memory.NLD.TALK_TENSE", -0.02), E("memory.DEU.TALK_TENSE", -0.01)], "ACCEPTED"),
  opt("neutral", "Rester neutre", "Aucune conséquence", [], "NEUTRAL"),
  opt("oppose", "S'y opposer", "Pays du Nord rassurés ; Sud déçu", [E("memory.NLD.NEGOTIATION_GOODWILL", 0.02), E("memory.ITA.TALK_TENSE", -0.02)], "REFUSED")])

# ================================ G7 =============================================================
summit("g7_tax", "Sommet du G7 : un impôt mondial sur les multinationales", "Les grandes puissances négocient une taxation minimale.", [6],
 letter("g7_tax",
  [V("G7 : l'impôt mondial minimum"), V("Taxation des multinationales"), V("Sommet du G7 : la question fiscale")],
  [V("Le G7 négocie un taux minimum mondial d'imposition des multinationales."),
   V("Les États-Unis acceptent le principe, mais refusent de taxer davantage leurs géants du numérique."),
   V("Un accord rapporterait plusieurs milliards d'euros par an à la France.")],
  [V("Un échec relancerait la guerre des taxes numériques avec Washington."),
   V("Nos entreprises redoutent de perdre en compétitivité.")],
  [V("Je vous propose de défendre un taux élevé, d'accepter le compromis américain, ou de faire cavalier seul."),
   V("Votre arbitrage est attendu.")]),
 "compromise", [
  opt("ambitious", "Défendre un taux ambitieux", "Recettes futures ; tensions avec Washington",
      [E("budget.oneOff", -1.5, delay=180), E("memory.USA.TALK_TENSE", -0.03), E(G + "low_income", 0.003)], "ACCEPTED"),
  opt("compromise", "Accepter le compromis américain", "Recettes plus modestes ; apaisement", [E("budget.oneOff", -0.6, delay=180), E("memory.USA.NEGOTIATION_GOODWILL", 0.02)], "PARTIAL"),
  opt("alone", "Maintenir notre taxe nationale", "Recettes immédiates ; risque de représailles", [E("budget.oneOff", -0.4), E("memory.USA.TALK_TENSE", -0.05)], "REFUSED")])

summit("g7_africa", "Sommet du G7 : un plan pour l'Afrique", "Les dirigeants discutent d'aide au développement et de dette africaine.", [6],
 letter("g7_africa",
  [V("G7 : le partenariat avec l'Afrique"), V("Aide au développement : notre engagement"), V("Sommet du G7 : l'Afrique à l'ordre du jour")],
  [V("Le G7 envisage un grand plan d'investissement en Afrique pour contrer l'influence chinoise."),
   V("Plusieurs pays africains réclament une annulation partielle de leur dette."),
   V("La France est attendue, compte tenu de ses liens historiques avec le continent.")],
  [V("Un engagement fort renforcerait notre influence ; un engagement faible serait remarqué."),
   V("Nos finances publiques limitent nos marges.")],
  [V("Je vous propose un engagement ambitieux, une contribution modeste, ou de laisser d'autres porter le projet."),
   V("Votre arbitrage est attendu.")]),
 "modest", [
  opt("ambitious", "Engagement ambitieux", "Coût : 2 Md€ ; influence renforcée",
      [E("budget.oneOff", 2.0), E("memory.MAR.NEGOTIATION_GOODWILL", 0.04), E("memory.DZA.NEGOTIATION_GOODWILL", 0.03), E("memory.TUN.NEGOTIATION_GOODWILL", 0.04), E("memory.EGY.NEGOTIATION_GOODWILL", 0.03)], "ACCEPTED"),
  opt("modest", "Contribution modeste", "Coût : 500 M€", [E("budget.oneOff", 0.5), E("memory.MAR.TALK_CORDIAL", 0.01)], "PARTIAL"),
  opt("none", "Laisser d'autres porter le projet", "Aucun coût ; influence en recul", [E("memory.MAR.TALK_TENSE", -0.02), E("memory.DZA.TALK_TENSE", -0.02)], "REFUSED")])

# ================================ OTAN ===========================================================
summit("nato_spending", "Sommet de l'OTAN : Washington exige plus de dépenses militaires", "Les alliés sont sommés de consacrer 3 % de leur PIB à la défense.", [7],
 letter("nato_spending",
  [V("Sommet de l'OTAN : l'effort de défense"), V("OTAN : l'objectif de 3 %"), V("Dépenses militaires : la pression américaine")],
  [V("Les États-Unis exigent des alliés qu'ils portent leurs dépenses militaires à 3 % du PIB."),
   V("La France consacre aujourd'hui environ 2 % de son PIB à la défense."),
   V("Les pays d'Europe de l'Est soutiennent fortement l'objectif américain.")],
  [V("Atteindre cet objectif représenterait des dizaines de milliards d'euros supplémentaires."),
   V("Refuser fragiliserait notre position dans l'Alliance.")],
  [V("Je vous propose de souscrire à l'objectif, de proposer une trajectoire plus progressive, ou de refuser."),
   V("Votre arbitrage est attendu.")]),
 "gradual", [
  opt("accept", "Souscrire à l'objectif de 3 %", "Budget des armées +15 % ; alliés satisfaits",
      [E("spending.defense", 0.15), E("alliance.NATO.NEGOTIATION_GOODWILL", 0.03), E("military.readiness", 0.03, days=730)], "ACCEPTED"),
  opt("gradual", "Proposer une trajectoire progressive", "Budget des armées +5 %", [E("spending.defense", 0.05), E("alliance.NATO.TALK_CORDIAL", 0.01)], "PARTIAL"),
  opt("refuse", "Refuser l'objectif", "Finances préservées ; Washington mécontent", [E("memory.USA.TALK_TENSE", -0.04), E("memory.POL.TALK_TENSE", -0.02)], "REFUSED")])

summit("nato_war_support", "Sommet de l'OTAN : quel soutien {victimTo} ?", "Les alliés débattent de l'aide militaire à fournir.", [7],
 letter("nato_war_support",
  [V("OTAN : le soutien {victimTo}"), V("Aide militaire : la position de la France"), V("Sommet de l'OTAN : la guerre {victimIn}")],
  [V("Les alliés débattent d'une augmentation de l'aide militaire {victimTo}, attaqué par {aggressorThe}."),
   V("Le pays agressé réclame des armes à longue portée et des systèmes de défense aérienne."),
   V("Certains alliés craignent une escalade.")],
  [V("Livrer des armes renforce notre crédibilité, mais puise dans nos propres stocks."),
   V("Une position trop timide serait sévèrement jugée par nos partenaires de l'Est.")],
  [V("Je vous propose une aide militaire importante, une aide humanitaire et économique, ou la neutralité."),
   V("Votre arbitrage est attendu.")]),
 "humanitarian", [
  opt("military", "Aide militaire importante", "Stocks de munitions réduits ; alliés reconnaissants",
      [E("military.ammoStock", -0.08), E("war.defenders.MILITARY_SUPPORT", 0.06), E("war.attackers.THREAT", -0.04), E("alliance.NATO.NEGOTIATION_GOODWILL", 0.02)], "ACCEPTED"),
  opt("humanitarian", "Aide humanitaire et économique", "Coût : 800 M€", [E("budget.oneOff", 0.8), E("war.defenders.CRISIS_SOLIDARITY", 0.04)], "PARTIAL"),
  opt("neutral", "Rester en retrait", "Aucun coût ; crédibilité entamée", [E("war.defenders.TALK_TENSE", -0.04), E("alliance.NATO.TALK_TENSE", -0.02)], "REFUSED")],
 extra_conditions=WAR, prob=0.05, urgency="URGENT")

# ================================ ONU ============================================================
summit("un_resolution", "ONU : vote sur une résolution condamnant {aggressorThe}", "L'Assemblée générale se prononce sur la guerre {victimIn}.", [9, 3],
 letter("un_resolution",
  [V("Nations unies : le vote de la France"), V("Résolution de l'ONU sur la guerre {victimIn}"), V("Assemblée générale : condamnation {aggressorOf}")],
  [V("L'Assemblée générale des Nations unies vote une résolution exigeant le retrait des troupes {aggressorOf}."),
   V("Le texte a été préparé par plusieurs pays européens et soutenu par une large coalition."),
   V("{AggressorThe} fait pression sur les pays du Sud pour qu'ils s'abstiennent.")],
  [V("Notre vote sera observé par nos alliés comme par nos partenaires africains et asiatiques."),
   V("Membre permanent du Conseil de sécurité, la France a une responsabilité particulière.")],
  [V("Je vous propose de voter pour et de coparrainer le texte, de voter pour sans s'exposer, ou de s'abstenir."),
   V("Votre arbitrage est attendu.")]),
 "vote", [
  opt("sponsor", "Voter pour et coparrainer le texte", "Fermeté ; relations dégradées avec l'agresseur",
      [E("war.attackers.CONDEMNATION", -0.06), E("war.defenders.NEGOTIATION_GOODWILL", 0.04), E("alliance.EU.TALK_CORDIAL", 0.01)], "ACCEPTED"),
  opt("vote", "Voter pour", "Position claire et mesurée", [E("war.attackers.TALK_TENSE", -0.03), E("war.defenders.TALK_CORDIAL", 0.02)], "PARTIAL"),
  opt("abstain", "S'abstenir", "Prudence ; alliés déçus", [E("war.defenders.TALK_TENSE", -0.05), E("alliance.EU.TALK_TENSE", -0.02), E("war.attackers.TALK_CORDIAL", 0.02)], "REFUSED")],
 extra_conditions=WAR, prob=0.05, urgency="URGENT")

summit("un_nuclear", "ONU : la France appelée à soutenir le désarmement nucléaire", "Un traité d'interdiction des armes nucléaires gagne des soutiens.", [9],
 letter("un_nuclear",
  [V("Désarmement nucléaire : la pression monte"), V("ONU : traité d'interdiction des armes nucléaires"), V("Dissuasion et diplomatie")],
  [V("Une coalition de pays pousse un traité d'interdiction des armes nucléaires à l'Assemblée générale."),
   V("Plusieurs ONG et prix Nobel interpellent directement la France."),
   V("Nos partenaires de l'OTAN dotés de l'arme nucléaire s'y opposent.")],
  [V("La dissuasion reste le fondement de notre défense."),
   V("Un geste diplomatique pourrait améliorer notre image auprès des pays du Sud.")],
  [V("Je vous propose de réaffirmer la dissuasion, de proposer des mesures de transparence, ou d'ouvrir un dialogue sur le traité."),
   V("Votre arbitrage est attendu.")]),
 "transparency", [
  opt("deterrence", "Réaffirmer la doctrine de dissuasion", "Cohérence stratégique ; image écornée", [E("memory.USA.TALK_CORDIAL", 0.01), E("memory.GBR.TALK_CORDIAL", 0.01), E(G + "young", -0.002)], "REFUSED"),
  opt("transparency", "Proposer des mesures de transparence", "Compromis", [E(G + "young", 0.001)], "PARTIAL"),
  opt("dialogue", "Ouvrir un dialogue sur le traité", "Image améliorée ; alliés inquiets", [E("memory.USA.TALK_TENSE", -0.03), E("memory.GBR.TALK_TENSE", -0.02), E(G + "young", 0.004)], "ACCEPTED")])

# ================================ COP ============================================================
summit("cop_climate", "COP : la France attendue sur ses engagements climatiques", "La conférence mondiale sur le climat s'ouvre.", [11],
 letter("cop_climate",
  [V("COP : nos engagements climatiques"), V("Conférence sur le climat : quelle ambition ?"), V("Climat : la France attendue")],
  [V("La conférence des Nations unies sur le climat s'ouvre dans quelques jours."),
   V("Les pays en développement réclament que les pays riches tiennent leurs promesses de financement."),
   V("Nos émissions baissent, mais pas assez vite pour atteindre nos objectifs.")],
  [V("Un engagement fort aurait un coût pour nos industries et nos ménages."),
   V("L'Accord de Paris porte le nom de notre capitale : notre crédibilité est en jeu.")],
  [V("Je vous propose de rehausser nos objectifs, de tenir les engagements existants, ou de temporiser."),
   V("Votre arbitrage est attendu.")]),
 "maintain", [
  opt("raise", "Rehausser nos objectifs et la finance climat", "Coût : 1 Md€ ; image et environnement",
      [E("budget.oneOff", 1.0), E("quality.environment", 0.004), E(G + "young", 0.005), E(G + "urban", 0.003), E(G + "rural", -0.003), E("alliance.EU.TALK_CORDIAL", 0.01)], "ACCEPTED"),
  opt("maintain", "Tenir les engagements existants", "Position stable", [], "NEUTRAL"),
  opt("delay", "Temporiser au nom de la compétitivité", "Industrie soulagée ; critiques internationales", [E("economy.businessConfidence", 0.003), E(G + "young", -0.005), E("alliance.EU.TALK_TENSE", -0.01)], "REFUSED")])

json.dump({"events": EVENTS}, open(os.path.join(ROOT, "events", "summits.json"), "w"), ensure_ascii=False, indent=1)
json.dump({"templates": TEMPLATES}, open(os.path.join(ROOT, "dialogue", "fr", "summits.json"), "w"), ensure_ascii=False, indent=1)
print(len(EVENTS), "sujets de sommets", len(TEMPLATES), "modèles")
