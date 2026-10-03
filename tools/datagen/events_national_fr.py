"""Événements nationaux et internationaux supplémentaires, avec des histoires en plusieurs épisodes
(assets/data/events/national.json, dialogue/fr/national.json).

Une option peut provoquer la suite d'une histoire : effet « chain.<id> » avec une probabilité
(amount, de 0 à 1) et un délai (delayDays). Les épisodes suivants ont une probabilité de base nulle :
ils n'arrivent que comme conséquence d'une décision.
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from dialogue_fr_lib import V, letter

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
G = "opinion.group."


def E(target, amount=0.0, param=None, factor=1.0, days=0.0, delay=0.0):
    d = {"target": target}
    if param: d["param"] = param; d["factor"] = factor
    else: d["amount"] = amount
    if days: d["days"] = days
    if delay: d["delayDays"] = delay
    return d


def chain(event, probability, delay):
    return E("chain." + event, probability, delay=delay)


def opt(id, label, hint="", effects=(), outcome="NEUTRAL"):
    return {"id": id, "label": label, "hint": hint, "effects": list(effects), "outcome": outcome}


def mod(var, frm, to, fa, fb):
    return {"variable": var, "from": frm, "to": to, "factorAtFrom": fa, "factorAtTo": fb}


def msg(template, sender, days, default, options, ministry=None):
    m = {"template": template, "sender": sender, "responseDays": days, "defaultOption": default, "options": options}
    if ministry: m["ministry"] = ministry
    return m


def event(id, category, headline, text, prob, cooldown, urgency, template, sender, default, options, ministry=None,
          scope="NATIONAL", modifiers=(), conditions=(), immediate=(), days=4, scope_cooldown=0):
    ev = {"id": id, "category": category, "scope": scope, "baseDailyProbability": prob, "cooldownDays": cooldown, "urgency": urgency,
          "headline": headline, "notificationText": text, "modifiers": list(modifiers), "immediateEffects": list(immediate),
          "message": msg(template["id"], sender, days, default, options, ministry)}
    if conditions: ev["conditions"] = list(conditions)
    if scope_cooldown: ev["scopeCooldownDays"] = scope_cooldown
    return ev, template


EVENTS, TEMPLATES = [], []
def add(pair):
    EVENTS.append(pair[0]); TEMPLATES.append(pair[1])

# =============================== Histoire : la grève des transports ===============================
add(event("rail_strike", "POLITICS", "Grève à la SNCF : le trafic fortement perturbé", "Les syndicats de cheminots lancent un mouvement reconductible.",
 0.0015, 240, "IMPORTANT",
 letter("rail_strike",
  [V("Grève reconductible à la SNCF"), V("Transports : un mouvement social qui s'installe"), V("Cheminots en grève : votre arbitrage"), V("Trafic ferroviaire paralysé")],
  [V("Les quatre principaux syndicats de cheminots ont déposé un préavis de grève reconductible."),
   V("Un train sur trois seulement circule depuis ce matin ; les Franciliens sont les plus touchés."),
   V("Le mouvement porte sur les salaires et les conditions de travail."),
   V("Les vacances scolaires approchent et des millions de voyageurs s'inquiètent.", when=["season:summer"])],
  [V("Chaque jour de grève coûte des dizaines de millions d'euros à l'économie."),
   V("Si rien ne bouge, d'autres secteurs pourraient rejoindre le mouvement."),
   V("L'opinion est partagée, mais pourrait basculer en faveur des grévistes.")],
  [V("Je vous propose d'ouvrir une négociation salariale, de faire une concession ciblée, ou de tenir bon en organisant un service minimum."),
   V("Trois options : négocier, concéder, tenir. Chacune a un coût.")]),
 "MINISTER", "firm", [
  opt("negotiate", "Ouvrir une négociation salariale", "Coût : 400 M€ ; fin probable du conflit", [E("budget.oneOff", 0.4), E(G + "private_employees", 0.004), E(G + "civil_servants", 0.006)], "ACCEPTED"),
  opt("partial", "Concession ciblée sur les conditions de travail", "Coût : 150 M€ ; le conflit peut durer", [E("budget.oneOff", 0.15), chain("strike_spreads", 0.3, 10)], "PARTIAL"),
  opt("firm", "Tenir bon et garantir un service minimum", "Aucun coût direct ; risque d'extension", [E("economy.output", -0.001, days=20), E(G + "civil_servants", -0.01), chain("strike_spreads", 0.7, 9)], "REFUSED")],
 ministry="transport", modifiers=[mod("economy.purchasingPower", -0.02, 0.02, 2.0, 0.6)]))

add(event("strike_spreads", "POLITICS", "La grève s'étend : raffineries et ports rejoignent le mouvement", "Le mouvement social devient interprofessionnel.",
 0.0, 30, "URGENT",
 letter("strike_spreads",
  [V("Le mouvement social s'étend"), V("Grève interprofessionnelle : la situation se durcit"), V("Raffineries bloquées, ports à l'arrêt")],
  [V("Après les cheminots, les salariés des raffineries et les dockers ont voté la grève."),
   V("Des stations-service commencent à manquer de carburant dans plusieurs régions."),
   V("Les centrales syndicales appellent à une journée de mobilisation nationale.")],
  [V("Le pays risque la paralysie d'ici quelques jours."),
   V("Les entreprises de transport routier menacent de bloquer les autoroutes en réaction."),
   V("La confiance des ménages et des entreprises se dégrade rapidement.")],
  [V("Il faut désormais choisir : négocier globalement, réquisitionner les raffineries, ou attendre l'essoufflement."),
   V("Je vous recommande une décision rapide : chaque jour aggrave la situation.")]),
 "PRIME_MINISTER", "wait", [
  opt("summit", "Convoquer un sommet social", "Coût : 1 Md€ ; apaisement", [E("budget.oneOff", 1.0), E("opinion.national", 0.004), E(G + "private_employees", 0.006)], "ACCEPTED"),
  opt("requisition", "Réquisitionner les raffineries", "Fermeté ; colère syndicale", [E(G + "civil_servants", -0.01), E(G + "private_employees", -0.006), E(G + "self_employed", 0.006), chain("fuel_blockade", 0.3, 6)], "PARTIAL"),
  opt("wait", "Attendre l'essoufflement", "Aucun coût direct ; pénuries probables", [E("economy.output", -0.002, days=20), E("economy.consumerConfidence", -0.01), chain("fuel_blockade", 0.6, 5)], "REFUSED")]))

add(event("fuel_blockade", "ENERGY", "Pénurie de carburant : un tiers des stations à sec", "Les blocages paralysent l'approvisionnement.",
 0.0, 30, "URGENT",
 letter("fuel_blockade",
  [V("Pénurie de carburant généralisée"), V("Stations à sec : la colère monte"), V("Approvisionnement en carburant : état d'urgence")],
  [V("Un tiers des stations-service du pays sont à sec ou en rupture partielle."),
   V("Des files d'attente de plusieurs heures se forment, et des altercations ont été signalées."),
   V("Les soignants et les services de secours peinent à se déplacer.")],
  [V("La situation pourrait tourner à la crise de l'ordre public."),
   V("Les stocks stratégiques permettent de tenir une dizaine de jours.")],
  [V("Je propose de puiser dans les stocks stratégiques et de prioriser les services essentiels."),
   V("Votre arbitrage est attendu dans l'heure.")]),
 "MINISTER", "stocks", [
  opt("stocks", "Débloquer les stocks stratégiques", "Coût : 300 M€ ; soulagement rapide", [E("budget.oneOff", 0.3), E("economy.output", 0.0005, days=10)], "ACCEPTED"),
  opt("rationing", "Instaurer un rationnement", "Impopulaire mais efficace", [E("opinion.national", -0.008), E(G + "rural", -0.008)], "PARTIAL")],
 ministry="ecology"))

# =============================== Histoire : un nouveau virus ======================================
add(event("new_virus", "DISASTER", "Un nouveau virus détecté à l'étranger", "Les autorités sanitaires internationales s'inquiètent.",
 0.0007, 900, "IMPORTANT",
 letter("new_virus",
  [V("Alerte sanitaire internationale"), V("Nouveau virus : faut-il se préparer ?"), V("Note du ministère de la Santé : menace épidémique")],
  [V("Un nouveau virus respiratoire a été identifié en Asie ; plusieurs foyers sont confirmés."),
   V("L'Organisation mondiale de la santé a convoqué son comité d'urgence."),
   V("Les premiers cas importés ont été détectés en Europe.")],
  [V("Sa contagiosité semble élevée ; sa dangerosité reste incertaine."),
   V("Nos stocks de masques et de matériel sont insuffisants pour faire face à une vague importante.")],
  [V("Je vous propose de constituer dès maintenant des stocks et de renforcer les contrôles, ou d'attendre d'en savoir plus."),
   V("Anticiper coûte cher ; ne pas anticiper pourrait coûter bien davantage.")]),
 "MINISTER", "wait", [
  opt("prepare", "Se préparer : stocks, tests, plan hospitalier", "Coût : 800 M€ ; une éventuelle vague serait bien moins grave", [E("budget.oneOff", 0.8), E("quality.health", 0.004), chain("pandemic_wave", 0.25, 30)], "ACCEPTED"),
  opt("borders", "Contrôles sanitaires aux frontières", "Coût : 100 M€ ; efficacité incertaine", [E("budget.oneOff", 0.1), E("economy.output", -0.0003, days=30), chain("pandemic_wave", 0.45, 28)], "PARTIAL"),
  opt("wait", "Attendre d'en savoir plus", "Aucun coût immédiat", [chain("pandemic_wave", 0.65, 25)], "REFUSED")],
 ministry="health"))

add(event("pandemic_wave", "DISASTER", "Épidémie : une vague frappe le pays", "Les hôpitaux se remplissent rapidement.",
 0.0, 200, "URGENT",
 letter("pandemic_wave",
  [V("Vague épidémique : décisions urgentes"), V("Hôpitaux saturés : que faire ?"), V("Épidémie : le moment de vérité")],
  [V("Le nombre d'hospitalisations double tous les dix jours."),
   V("Plusieurs régions déclenchent leur plan blanc."),
   V("Les écoles enregistrent un absentéisme massif, chez les élèves comme chez les enseignants.")],
  [V("Sans mesures fortes, les services de réanimation seront saturés d'ici deux semaines."),
   V("Chaque mesure de restriction aura un coût économique et social important.")],
  [V("Trois options : un confinement strict, des restrictions ciblées, ou miser sur la responsabilité individuelle."),
   V("Votre décision engagera le pays pour plusieurs semaines.")]),
 "MINISTER", "targeted", [
  opt("lockdown", "Confinement strict de quatre semaines", "Très coûteux pour l'économie ; protège les hôpitaux", [E("economy.output", -0.012, days=30), E("budget.oneOff", 6.0, days=60), E("quality.health", 0.01, days=30), E(G + "young", -0.02), E(G + "seniors", 0.015), E(G + "self_employed", -0.02)], "ACCEPTED"),
  opt("targeted", "Restrictions ciblées et vaccination", "Équilibre ; coût : 2 Md€", [E("economy.output", -0.004, days=45), E("budget.oneOff", 2.0, days=60), E("quality.health", 0.005, days=30)], "PARTIAL"),
  opt("liberty", "Miser sur la responsabilité individuelle", "Économie préservée ; hôpitaux débordés", [E("quality.health", -0.04, days=45), E(G + "seniors", -0.03), E("opinion.national", -0.01, delay=20, days=20)], "REFUSED")],
 ministry="health", immediate=[E("economy.output", -0.002, days=30), E("quality.health", -0.02, days=30)]))

# =============================== Histoire : l'affaire ministérielle ===============================
add(event("minister_investigation", "GOVERNMENT", "Enquête judiciaire visant {minister}", "Une perquisition a eu lieu au ministère.",
 0.0005, 300, "IMPORTANT",
 letter("minister_investigation",
  [V("Perquisition au ministère"), V("Affaire {minister} : la justice enquête"), V("Note confidentielle : enquête judiciaire en cours")],
  [V("Les enquêteurs ont perquisitionné ce matin le bureau de {minister}."),
   V("Le parquet national financier enquête sur des marchés publics attribués il y a plusieurs années."),
   V("L'intéressé dénonce une manœuvre politique et clame son innocence.")],
  [V("La presse évoque déjà une possible mise en examen."),
   V("L'opposition exige une démission immédiate."),
   V("Le gouvernement risque d'être absorbé par cette affaire pendant des semaines.")],
  [V("Souhaitez-vous le maintenir en poste, lui demander de se mettre en retrait, ou attendre l'évolution de la procédure ?"),
   V("Votre arbitrage est attendu : chaque option a ses risques.")]),
 "PRIME_MINISTER", "wait", [
  opt("dismiss", "Lui demander de démissionner", "Affaire close politiquement ; ministère en intérim", [E("subject.dismiss", 1), E("opinion.national", 0.003)], "ACCEPTED"),
  opt("support", "Le soutenir publiquement", "Loyauté ; risque si l'affaire s'aggrave", [E("subject.loyalty", 0.15), chain("minister_indicted", 0.5, 40)], "PARTIAL"),
  opt("wait", "Attendre l'évolution de la procédure", "Prudence", [chain("minister_indicted", 0.4, 45)], "NEUTRAL")],
 scope="MINISTER", modifiers=[mod("scope.integrity", 0.2, 0.8, 3.0, 0.3)]))

add(event("minister_indicted", "GOVERNMENT", "{minister} mis en examen", "La justice franchit une étape décisive dans l'affaire.",
 0.0, 100, "URGENT",
 letter("minister_indicted",
  [V("Mise en examen : décision immédiate"), V("Affaire {minister} : la mise en examen"), V("Le gouvernement fragilisé")],
  [V("{minister} vient d'être mis en examen pour favoritisme et prise illégale d'intérêts."),
   V("Les médias ne parlent plus que de cela depuis ce matin."),
   V("Plusieurs députés de la majorité réclament publiquement son départ.")],
  [V("Le maintenir exposerait directement l'Élysée."),
   V("Votre soutien passé sera rappelé par l'opposition à chaque occasion.")],
  [V("Je vous recommande de tirer les conséquences sans attendre."),
   V("Il faut trancher aujourd'hui.")]),
 "PRIME_MINISTER", "dismiss", [
  opt("dismiss", "Exiger sa démission", "Coût politique limité", [E("subject.dismiss", 1), E("opinion.national", -0.003)], "ACCEPTED"),
  opt("keep", "Le maintenir au nom de la présomption d'innocence", "Risqué : scandale pour l'exécutif", [E("president.scandal", 1), E("opinion.national", -0.015), E("government.parliamentSupport", -0.03)], "REFUSED")],
 scope="MINISTER"))

# =============================== Histoire : la banque fragile =====================================
add(event("bank_fragility", "ECONOMY", "Une grande banque en difficulté", "Les marchés s'inquiètent de la solidité d'un établissement français.",
 0.0005, 700, "IMPORTANT",
 letter("bank_fragility",
  [V("Système bancaire : un établissement fragilisé"), V("Note confidentielle : risque bancaire"), V("Une banque française sous pression")],
  [V("Une grande banque française a annoncé des pertes inattendues sur ses activités de marché."),
   V("Son cours de bourse a perdu trente pour cent en deux séances."),
   V("Les agences de notation menacent de dégrader sa note.")],
  [V("Si la confiance se rompt, les déposants pourraient retirer massivement leur épargne."),
   V("La contagion à d'autres établissements n'est pas exclue."),
   V("La Banque centrale européenne suit la situation de près.")],
  [V("Je vous propose une garantie publique, une recapitalisation conditionnée, ou de laisser faire le marché."),
   V("Une intervention rapide coûte moins cher qu'une crise systémique.")]),
 "MINISTER", "wait", [
  opt("guarantee", "Garantie publique des dépôts et des financements", "Engagement de l'État ; calme probable", [E("budget.oneOff", 0.5), E("economy.businessConfidence", 0.006)], "ACCEPTED"),
  opt("recap", "Recapitalisation contre contreparties", "Coût : 3 Md€ ; l'État devient actionnaire", [E("budget.oneOff", 3.0), E("economy.businessConfidence", 0.01), E(G + "low_income", -0.004)], "PARTIAL"),
  opt("wait", "Laisser le marché s'ajuster", "Aucun coût ; risque de panique", [chain("bank_run", 0.5, 12)], "REFUSED")],
 ministry="economy", modifiers=[mod("economy.debtRatio", 0.9, 1.3, 0.7, 1.6)]))

add(event("bank_run", "ECONOMY", "Panique bancaire : les clients se ruent aux guichets", "Les retraits massifs menacent tout le système.",
 0.0, 300, "URGENT",
 letter("bank_run",
  [V("Panique bancaire"), V("Retraits massifs : la crise est là"), V("Urgence financière")],
  [V("Des files d'attente se sont formées devant les agences de la banque en difficulté."),
   V("Les retraits en ligne ont atteint des niveaux sans précédent."),
   V("Deux autres établissements voient leurs cours s'effondrer.")],
  [V("Sans intervention massive, le système bancaire pourrait vaciller."),
   V("Les ménages modestes, qui n'ont qu'un compte, sont les plus inquiets.")],
  [V("Il faut un plan de sauvetage immédiat, ou accepter une faillite ordonnée."),
   V("Chaque heure compte.")]),
 "MINISTER", "rescue", [
  opt("rescue", "Plan de sauvetage massif", "Coût : 15 Md€ ; crise contenue", [E("budget.oneOff", 15.0), E("economy.businessConfidence", -0.01), E("economy.consumerConfidence", -0.01)], "ACCEPTED"),
  opt("resolution", "Faillite ordonnée de la banque", "Coût moindre ; choc économique", [E("budget.oneOff", 4.0), E("economy.output", -0.008, days=90), E("economy.businessConfidence", -0.04), E("economy.consumerConfidence", -0.03)], "PARTIAL")],
 ministry="economy", immediate=[E("economy.consumerConfidence", -0.02), E("economy.businessConfidence", -0.03)]))

# =============================== Histoire : bavure et émeutes ====================================
add(event("police_incident", "SECURITY", "Un jeune tué lors d'un contrôle de police à {city}", "La vidéo de l'intervention circule massivement.",
 0.0008, 400, "URGENT",
 letter("police_incident",
  [V("Drame à {city} : la situation peut dégénérer"), V("Mort d'un jeune lors d'un contrôle"), V("Note urgente du ministère de l'Intérieur")],
  [V("Un adolescent de dix-sept ans a été tué par un tir policier lors d'un contrôle routier à {city}."),
   V("Une vidéo contredit la version initiale des policiers ; elle a été vue des millions de fois."),
   V("Des rassemblements spontanés ont lieu dans plusieurs quartiers.")],
  [V("Les risques de violences urbaines dans les prochaines nuits sont très élevés."),
   V("Les syndicats de police dénoncent un emballement médiatique."),
   V("La famille demande justice et appelle au calme.")],
  [V("Je vous propose une prise de parole forte, la suspension de l'agent et une enquête indépendante, ou le soutien aux forces de l'ordre."),
   V("Le ton que vous donnerez pèsera lourd dans les jours qui viennent.")]),
 "MINISTER", "support_police", [
  opt("justice", "Parole forte, enquête indépendante, agent suspendu", "Apaisement probable ; colère policière", [E(G + "young", 0.008), E(G + "urban", 0.004), E(G + "retirees", -0.004), chain("urban_riots", 0.25, 2)], "ACCEPTED"),
  opt("balanced", "Appel au calme et confiance dans la justice", "Neutre", [chain("urban_riots", 0.45, 2)], "NEUTRAL"),
  opt("support_police", "Soutien appuyé aux forces de l'ordre", "Fermeté ; risque d'embrasement", [E(G + "young", -0.012), E(G + "retirees", 0.006), chain("urban_riots", 0.7, 2)], "REFUSED")],
 ministry="interior", scope="CITY", modifiers=[mod("scope.crime", 0.9, 1.5, 0.6, 1.8)], scope_cooldown=2000))

add(event("urban_riots", "SECURITY", "Nuits d'émeutes à {city} et dans plusieurs villes", "Bâtiments publics incendiés, centaines d'interpellations.",
 0.0, 120, "URGENT",
 letter("urban_riots",
  [V("Émeutes urbaines : la situation"), V("Violences urbaines : décisions urgentes"), V("Nuits d'émeutes")],
  [V("Pour la troisième nuit consécutive, des affrontements ont éclaté à {city} et dans une dizaine d'autres villes."),
   V("Des écoles, des mairies annexes et des commissariats ont été incendiés."),
   V("Plus de huit cents personnes ont été interpellées, en majorité des mineurs.")],
  [V("Les forces de l'ordre sont épuisées."),
   V("Les maires concernés réclament l'état d'urgence ou, à l'inverse, un geste d'apaisement.")],
  [V("Je vous propose de décréter l'état d'urgence, de renforcer massivement les effectifs, ou de privilégier la médiation."),
   V("Votre décision doit intervenir avant la nuit prochaine.")]),
 "MINISTER", "reinforce", [
  opt("emergency", "Décréter l'état d'urgence", "Retour à l'ordre rapide ; libertés restreintes", [E("quality.security", 0.003), E(G + "young", -0.015), E(G + "retirees", 0.01), E("opinion.national", 0.002)], "ACCEPTED"),
  opt("reinforce", "Renforts massifs de police et de gendarmerie", "Coût : 200 M€", [E("budget.oneOff", 0.2), E(G + "retirees", 0.005), E(G + "young", -0.004)], "PARTIAL"),
  opt("mediation", "Médiation et plan pour les quartiers", "Coût : 1 Md€ ; apaisement plus lent", [E("budget.oneOff", 1.0), E(G + "young", 0.008), E(G + "urban", 0.004), E(G + "retirees", -0.008)], "NEUTRAL")],
 ministry="interior", scope="CITY", immediate=[E("scope.approval", -0.03, days=20), E("economy.output", -0.0008, days=20), E("scope.crime", 0.08)]))

# =============================== Histoire : la taxe carburant ====================================
add(event("fuel_tax_protest", "POLITICS", "Colère contre le prix du carburant : des blocages partout en France", "Un mouvement spontané naît sur les réseaux sociaux.",
 0.0012, 400, "URGENT",
 letter("fuel_tax_protest",
  [V("Mouvement de colère contre les prix du carburant"), V("Ronds-points bloqués dans tout le pays"), V("Une colère qui ne ressemble à aucune autre")],
  [V("Des dizaines de milliers de personnes bloquent des ronds-points dans toute la France."),
   V("Le mouvement, sans chef ni syndicat, s'organise sur les réseaux sociaux."),
   V("Les manifestants dénoncent le coût de la vie, les taxes et le mépris des élites.")],
  [V("Le soutien de l'opinion au mouvement est massif."),
   V("Les revendications s'élargissent chaque jour : pouvoir d'achat, services publics, démocratie."),
   V("Des violences ont éclaté en marge de certains rassemblements.")],
  [V("Je vous propose de baisser les taxes sur le carburant, d'ouvrir un grand débat national, ou de tenir bon."),
   V("Le temps joue contre nous.")]),
 "PRIME_MINISTER", "firm", [
  opt("tax_cut", "Baisser les taxes sur le carburant", "Coût : 4 Md€ par an ; apaisement", [E("revenue.energy_taxes", -10), E(G + "rural", 0.012), E(G + "low_income", 0.008)], "ACCEPTED"),
  opt("debate", "Ouvrir un grand débat national", "Coût : 100 M€ ; apaisement progressif", [E("budget.oneOff", 0.1), E("opinion.national", 0.004, days=60), chain("fuel_protest_hardens", 0.25, 20)], "PARTIAL"),
  opt("firm", "Maintenir le cap", "Aucun coût ; risque de durcissement", [E(G + "rural", -0.012), E(G + "low_income", -0.008), chain("fuel_protest_hardens", 0.65, 14)], "REFUSED")],
 modifiers=[mod("energy.priceIndex", 1.0, 1.5, 0.4, 3.0), mod("economy.purchasingPower", -0.03, 0.02, 2.5, 0.5)]))

add(event("fuel_protest_hardens", "SECURITY", "Le mouvement se durcit : violences à Paris", "Des scènes de chaos sur les Champs-Élysées.",
 0.0, 120, "URGENT",
 letter("fuel_protest_hardens",
  [V("Violences à Paris : le mouvement se radicalise"), V("Le pays sous tension"), V("Samedi noir dans la capitale")],
  [V("Des affrontements d'une rare violence ont eu lieu ce samedi à Paris."),
   V("L'Arc de Triomphe a été tagué, des commerces pillés, des voitures incendiées."),
   V("Le mouvement conserve pourtant le soutien d'une large partie des Français.")],
  [V("Les images font le tour du monde et ternissent l'image du pays."),
   V("Les commerçants des grandes villes réclament des mesures fortes.")],
  [V("Il faut choisir entre des concessions importantes et un durcissement du maintien de l'ordre."),
   V("Je vous demande de trancher.")]),
 "PRIME_MINISTER", "order", [
  opt("concessions", "Concessions sociales importantes", "Coût : 10 Md€ ; fin probable du mouvement", [E("budget.oneOff", 10.0), E(G + "low_income", 0.015), E(G + "rural", 0.01), E("opinion.national", 0.006)], "ACCEPTED"),
  opt("order", "Durcir le maintien de l'ordre", "Fermeté ; division du pays", [E(G + "retirees", 0.008), E(G + "low_income", -0.01), E(G + "young", -0.008), E("economy.output", -0.001, days=30)], "REFUSED")]))

# =============================== Événements isolés nationaux ======================================
add(event("cold_wave", "DISASTER", "Vague de froid : le réseau électrique sous tension", "Des températures polaires s'abattent sur le pays.",
 0.004, 300, "IMPORTANT",
 letter("cold_wave",
  [V("Grand froid : mesures d'urgence"), V("Vague de froid et sans-abri"), V("Températures polaires : le pays se prépare")],
  [V("Les températures descendent sous les moins quinze degrés dans le nord-est."),
   V("La consommation d'électricité atteint des records historiques."),
   V("Plusieurs sans-abri sont morts de froid ces derniers jours.")],
  [V("Le réseau électrique est au bord de la rupture aux heures de pointe."),
   V("Les centres d'hébergement d'urgence sont saturés.")],
  [V("Je vous propose d'ouvrir des gymnases et des places d'hébergement, et d'appeler à la sobriété."),
   V("Votre arbitrage est attendu sur les moyens à déployer.")]),
 "MINISTER", "minimal", [
  opt("plan", "Plan grand froid renforcé", "Coût : 150 M€", [E("budget.oneOff", 0.15), E("opinion.national", 0.003), E("quality.social", 0.002)], "ACCEPTED"),
  opt("minimal", "Dispositif habituel", "Aucun coût supplémentaire", [E(G + "low_income", -0.004)], "NEUTRAL")],
 ministry="ecology", conditions=[{"variable": "season.month", "oneOf": [12, 1, 2]}]))

add(event("food_scandal", "SECURITY", "Scandale alimentaire : des produits contaminés retirés de la vente", "Plusieurs enfants hospitalisés.",
 0.0012, 300, "IMPORTANT",
 letter("food_scandal",
  [V("Contamination alimentaire : point de situation"), V("Scandale sanitaire dans l'agroalimentaire"), V("Rappel massif de produits")],
  [V("Des produits laitiers contaminés à la salmonelle ont été vendus dans toute la France."),
   V("Une trentaine d'enfants ont été hospitalisés ; aucun décès n'est à déplorer pour l'instant."),
   V("L'industriel concerné aurait été alerté depuis plusieurs semaines.")],
  [V("Les familles sont en colère et la confiance des consommateurs s'effondre."),
   V("Les contrôles sanitaires ont été réduits ces dernières années, faute de moyens.")],
  [V("Je propose de renforcer les contrôles, d'engager des poursuites et de créer un fonds d'indemnisation."),
   V("Une réponse ferme est attendue.")]),
 "MINISTER", "prosecute", [
  opt("controls", "Renforcer massivement les contrôles sanitaires", "Coût : 200 M€", [E("budget.oneOff", 0.2), E("quality.agriculture", 0.002), E("opinion.national", 0.004)], "ACCEPTED"),
  opt("prosecute", "Poursuites contre l'industriel", "Aucun coût ; effet limité", [E("opinion.national", 0.002)], "NEUTRAL")],
 ministry="agriculture"))

add(event("teacher_shortage", "POLITICS", "Rentrée scolaire : des milliers de postes d'enseignants non pourvus", "Des classes sans professeur dès le premier jour.",
 0.02, 300, "IMPORTANT",
 letter("teacher_shortage",
  [V("Rentrée : la pénurie d'enseignants"), V("Des classes sans professeur"), V("Éducation nationale : une rentrée sous tension")],
  [V("Plus de trois mille postes d'enseignants n'ont pas été pourvus à la rentrée."),
   V("Les concours de recrutement n'attirent plus assez de candidats."),
   V("Des contractuels formés en quelques jours sont envoyés devant les classes.")],
  [V("Les parents d'élèves sont exaspérés et les syndicats menacent de faire grève."),
   V("La dégradation du niveau scolaire inquiète.")],
  [V("Je vous propose une revalorisation salariale, un plan de recrutement exceptionnel, ou de s'en tenir aux mesures existantes."),
   V("Votre arbitrage est attendu.")]),
 "MINISTER", "status_quo", [
  opt("raise", "Revaloriser les salaires des enseignants", "Budget de l'éducation +3 %", [E("spending.education", 0.03), E(G + "civil_servants", 0.01), E("quality.education", 0.004, days=180)], "ACCEPTED"),
  opt("recruit", "Plan de recrutement exceptionnel", "Coût : 300 M€", [E("budget.oneOff", 0.3), E("quality.education", 0.002, days=120)], "PARTIAL"),
  opt("status_quo", "S'en tenir aux mesures existantes", "Aucun coût", [E(G + "civil_servants", -0.006), E("quality.education", -0.003, days=60)], "REFUSED")],
 ministry="education", conditions=[{"variable": "season.month", "oneOf": [9]}], modifiers=[mod("quality.education", 0.4, 0.7, 2.0, 0.4)]))

add(event("space_failure", "ECONOMY", "Échec d'un lancement d'Ariane", "La fusée a explosé quelques minutes après le décollage.",
 0.0006, 500, "IMPORTANT",
 letter("space_failure",
  [V("Échec du lancement d'Ariane"), V("Spatial européen : un coup dur"), V("Après l'échec d'Ariane")],
  [V("Le lanceur Ariane a explosé peu après son décollage de Kourou, détruisant deux satellites."),
   V("C'est un revers majeur pour l'industrie spatiale européenne."),
   V("Nos concurrents américains proposent déjà de reprendre les contrats.")],
  [V("Sans soutien, la filière pourrait perdre des parts de marché décisives."),
   V("Des milliers d'emplois en dépendent, notamment à Toulouse et aux Mureaux.")],
  [V("Je vous propose un plan de soutien à la filière ou de laisser l'Agence spatiale européenne gérer."),
   V("L'autonomie stratégique de l'Europe est en jeu.")]),
 "MINISTER", "esa", [
  opt("support", "Plan de soutien à la filière spatiale", "Coût : 600 M€", [E("budget.oneOff", 0.6), E("economy.potentialGrowth", 0.0002), E("economy.businessConfidence", 0.004)], "ACCEPTED"),
  opt("esa", "S'en remettre à l'Agence spatiale européenne", "Aucun coût", [E("economy.businessConfidence", -0.003)], "NEUTRAL")],
 ministry="economy"))

add(event("nobel_prize", "POLITICS", "Une chercheuse française reçoit le prix Nobel", "Une fierté nationale et un symbole pour la recherche.",
 0.0015, 365, "IMPORTANT",
 letter("nobel_prize",
  [V("Prix Nobel : une fierté nationale"), V("Une Française couronnée par le Nobel"), V("Recherche : le moment de saisir l'élan")],
  [V("Une chercheuse française vient de recevoir le prix Nobel pour ses travaux sur les maladies génétiques."),
   V("Elle a fait toute sa carrière dans un laboratoire public, souvent avec des moyens limités.")],
  [V("Dans ses premières déclarations, elle a plaidé pour davantage de moyens pour la recherche."),
   V("C'est une occasion unique de valoriser la science française.")],
  [V("Je vous propose de saisir cet élan avec un plan pour la recherche, ou simplement de saluer cette réussite."),
   V("Le pays attend un geste.")], positive=True),
 "MINISTER", "honor", [
  opt("plan", "Annoncer un plan pour la recherche", "Coût : 1 Md€ ; croissance potentielle", [E("budget.oneOff", 1.0), E("economy.potentialGrowth", 0.0004), E("quality.education", 0.002), E("opinion.national", 0.006)], "ACCEPTED"),
  opt("honor", "La recevoir à l'Élysée et saluer sa réussite", "Aucun coût", [E("opinion.national", 0.003)], "NEUTRAL")],
 ministry="education", conditions=[{"variable": "season.month", "oneOf": [10]}]))

add(event("defense_leak", "MILITARY", "Fuite de documents classifiés de la Défense", "Des informations sensibles publiées en ligne.",
 0.0006, 500, "IMPORTANT",
 letter("defense_leak",
  [V("Fuite de documents classifiés"), V("Sécurité nationale : une fuite grave"), V("Note du ministère des Armées")],
  [V("Des centaines de documents classifiés ont été publiés sur un forum en ligne."),
   V("Ils concernent nos opérations extérieures et nos capacités militaires."),
   V("Nos alliés nous demandent des explications.")],
  [V("Certaines de nos sources pourraient être compromises."),
   V("L'opposition dénonce une faillite de la sécurité de l'État.")],
  [V("Je vous propose un audit complet de la sécurité, ou une enquête interne discrète."),
   V("Votre décision sera scrutée par nos partenaires.")]),
 "MINISTER", "internal", [
  opt("audit", "Audit complet et renforcement de la sécurité", "Coût : 250 M€ ; confiance des alliés restaurée", [E("budget.oneOff", 0.25), E("memory.USA.NEGOTIATION_GOODWILL", 0.02), E("memory.GBR.NEGOTIATION_GOODWILL", 0.02)], "ACCEPTED"),
  opt("internal", "Enquête interne discrète", "Aucun coût", [E("memory.USA.DISAGREEMENT", -0.01), E("military.readiness", -0.005)], "NEUTRAL")],
 ministry="armed_forces"))

add(event("pension_deficit", "ECONOMY", "Le déficit des retraites se creuse", "Le Conseil d'orientation des retraites publie un rapport alarmant.",
 0.0015, 500, "IMPORTANT",
 letter("pension_deficit",
  [V("Retraites : le rapport qui inquiète"), V("Déficit des retraites"), V("Financement des pensions : un signal d'alarme")],
  [V("Le Conseil d'orientation des retraites prévoit un déficit croissant dans les dix prochaines années."),
   V("Le vieillissement de la population pèse plus vite que prévu sur l'équilibre du système.")],
  [V("Les marchés financiers et nos partenaires européens suivent ce dossier de près."),
   V("Toute réforme sera explosive socialement.")],
  [V("Je vous propose une hausse modérée des cotisations, une désindexation temporaire des pensions, ou de reporter le débat."),
   V("Votre arbitrage est attendu.")]),
 "MINISTER", "postpone", [
  opt("contributions", "Hausse modérée des cotisations", "Recettes en hausse ; salariés mécontents", [E("revenue.social_contributions", 2), E(G + "private_employees", -0.006)], "ACCEPTED"),
  opt("deindex", "Désindexer temporairement les pensions", "Économies ; retraités en colère", [E("spending.pensions", -0.015), E(G + "retirees", -0.015), E(G + "seniors", -0.01)], "PARTIAL"),
  opt("postpone", "Reporter le débat", "Aucun coût immédiat ; dette en hausse", [E("economy.businessConfidence", -0.003)], "REFUSED")],
 ministry="labour"))

# =============================== International ===================================================
add(event("hostages_abroad", "DIPLOMACY", "Des Français pris en otage {foreignIn}", "Un groupe armé revendique l'enlèvement.",
 0.0003, 400, "URGENT",
 letter("hostages_abroad",
  [V("Prise d'otages : des ressortissants français"), V("Otages {foreignIn} : décisions urgentes"), V("Note du Quai d'Orsay : enlèvement")],
  [V("Trois ressortissants français, des humanitaires, ont été enlevés {foreignIn}."),
   V("Un groupe armé revendique l'enlèvement et pose ses conditions."),
   V("Les familles ont été informées ; elles demandent la plus grande discrétion.")],
  [V("Chaque option comporte des risques pour la vie des otages."),
   V("Nos services de renseignement ont une idée de leur localisation.")],
  [V("Je vous propose une négociation discrète, une opération de libération, ou de s'en remettre aux autorités locales."),
   V("La décision vous appartient ; elle est lourde.")]),
 "MINISTER", "negotiate", [
  opt("negotiate", "Négociation discrète", "Coût : 50 M€ ; libération probable", [E("budget.oneOff", 0.05), E("opinion.national", 0.006, delay=30)], "ACCEPTED"),
  opt("operation", "Opération de libération par les forces spéciales", "Risqué ; succès retentissant ou drame", [E("military.readiness", -0.005), E("opinion.national", 0.004, delay=10)], "PARTIAL"),
  opt("local", "S'en remettre aux autorités locales", "Aucun coût ; issue incertaine", [E("opinion.national", -0.006, delay=20), E("country.DISAGREEMENT", -0.01)], "REFUSED")],
 ministry="foreign", scope="FOREIGN_COUNTRY", scope_cooldown=1500, conditions=[{"variable": "scope.relation", "max": 0.7}]))

add(event("cyber_espionage", "DIPLOMACY", "Cyberespionnage : nos services accusent {foreignThe}", "Des intrusions dans des ministères attribuées à un État étranger.",
 0.0005, 300, "IMPORTANT",
 letter("cyber_espionage",
  [V("Cyberespionnage : attribution à un État"), V("Intrusions informatiques : {foreignThe} mis en cause"), V("Note de l'ANSSI")],
  [V("L'Agence nationale de la sécurité des systèmes d'information a détecté des intrusions dans plusieurs ministères."),
   V("Les indices techniques désignent avec une forte probabilité des services liés {foreignTo}."),
   V("Des documents relatifs à nos négociations commerciales auraient été dérobés.")],
  [V("Attribuer publiquement l'attaque aurait des conséquences diplomatiques."),
   V("Ne rien dire pourrait encourager de nouvelles intrusions.")],
  [V("Je vous propose une attribution publique assortie de sanctions, une protestation discrète, ou le silence."),
   V("Votre arbitrage est attendu.")]),
 "MINISTER", "discreet", [
  opt("public", "Attribution publique et expulsion de diplomates", "Fermeté ; relations dégradées", [E("country.DISAGREEMENT", -0.06), E("opinion.national", 0.003)], "ACCEPTED"),
  opt("discreet", "Protestation discrète", "Équilibre", [E("country.DISAGREEMENT", -0.02)], "PARTIAL"),
  opt("silence", "Ne rien dire", "Aucune conséquence immédiate", [E("quality.security", -0.002)], "NEUTRAL")],
 ministry="interior", scope="FOREIGN_COUNTRY", scope_cooldown=500, conditions=[{"variable": "scope.relation", "max": 0.55}]))

add(event("strategic_acquisition", "ECONOMY", "Un groupe étranger veut racheter un fleuron industriel français", "Le rachat concerne une entreprise stratégique.",
 0.0008, 300, "IMPORTANT",
 letter("strategic_acquisition",
  [V("Rachat d'une entreprise stratégique"), V("Souveraineté industrielle : une offre de rachat {foreignOf}"), V("Contrôle des investissements étrangers")],
  [V("Un groupe {{industriel|financier}} lié {foreignTo} a déposé une offre sur une entreprise française de haute technologie."),
   V("L'entreprise fournit des composants essentiels à notre industrie de défense."),
   V("L'offre est généreuse et les actionnaires sont tentés.")],
  [V("Bloquer le rachat enverrait un signal négatif aux investisseurs étrangers."),
   V("Le laisser faire pourrait compromettre notre souveraineté technologique.")],
  [V("Je vous propose de bloquer l'opération, de l'autoriser sous conditions strictes, ou de la laisser se faire."),
   V("La procédure de contrôle des investissements étrangers vous laisse le dernier mot.")]),
 "MINISTER", "conditions", [
  opt("block", "Bloquer le rachat", "Souveraineté ; investisseurs refroidis", [E("country.DISAGREEMENT", -0.03), E("economy.businessConfidence", -0.003), E(G + "private_employees", 0.003)], "REFUSED"),
  opt("conditions", "Autoriser sous conditions strictes", "Compromis", [E("country.NEGOTIATION_GOODWILL", 0.01)], "PARTIAL"),
  opt("allow", "Laisser l'opération se faire", "Capitaux étrangers ; critiques souverainistes", [E("country.NEGOTIATION_GOODWILL", 0.03), E("economy.businessConfidence", 0.004), E(G + "rural", -0.003)], "ACCEPTED")],
 ministry="economy", scope="FOREIGN_COUNTRY", scope_cooldown=700))

add(event("fishing_dispute", "DIPLOMACY", "Conflit de pêche avec {foreignThe}", "Des chalutiers français interdits d'accès à des zones de pêche.",
 0.0008, 300, "IMPORTANT",
 letter("fishing_dispute",
  [V("Pêche : le conflit s'envenime"), V("Nos pêcheurs privés de licences"), V("Différend de pêche avec {foreignThe}")],
  [V("Les autorités {foreignOf} ont refusé de renouveler les licences de dizaines de navires français."),
   V("Les pêcheurs bretons et normands menacent de bloquer les ports."),
   V("Des tensions ont éclaté en mer entre chalutiers.")],
  [V("Toute une filière, déjà fragile, est menacée."),
   V("Le dossier est devenu très politique des deux côtés.")],
  [V("Je vous propose des mesures de rétorsion, une négociation, ou une indemnisation de nos pêcheurs."),
   V("Votre arbitrage est attendu.")]),
 "MINISTER", "negotiate", [
  opt("retaliate", "Mesures de rétorsion commerciales", "Fermeté ; escalade possible", [E("country.DISAGREEMENT", -0.05), E(G + "rural", 0.004)], "REFUSED"),
  opt("negotiate", "Négociation", "Apaisement", [E("country.NEGOTIATION_GOODWILL", 0.01)], "PARTIAL"),
  opt("compensate", "Indemniser nos pêcheurs", "Coût : 80 M€", [E("budget.oneOff", 0.08), E(G + "rural", 0.003)], "ACCEPTED")],
 ministry="agriculture", scope="FOREIGN_COUNTRY", scope_cooldown=700, conditions=[{"variable": "scope.fisheryNeighbor", "min": 1}]))

add(event("trade_tariffs", "ECONOMY", "{ForeignThe} impose des droits de douane sur les produits français", "Le vin, le fromage et l'aéronautique sont visés.",
 0.0007, 300, "IMPORTANT",
 letter("trade_tariffs",
  [V("Guerre commerciale : des droits de douane sur nos produits"), V("Exportations françaises taxées"), V("Riposte commerciale : votre arbitrage")],
  [V("Le gouvernement {foreignOf} a annoncé des droits de douane de vingt-cinq pour cent sur plusieurs produits français."),
   V("Les viticulteurs et les producteurs de fromage sont les premiers touchés."),
   V("La mesure répond à notre taxe sur les services numériques.")],
  [V("Nos exportateurs craignent de perdre des marchés difficiles à reconquérir."),
   V("Une riposte pourrait entraîner une escalade.")],
  [V("Je vous propose une riposte proportionnée, une négociation, ou un soutien aux filières touchées."),
   V("L'Union européenne attend notre position.")]),
 "MINISTER", "negotiate", [
  opt("retaliate", "Riposte proportionnée", "Fermeté ; escalade possible", [E("country.DISAGREEMENT", -0.05), E("economy.output", -0.0005, days=90)], "REFUSED"),
  opt("negotiate", "Négocier, au besoin en assouplissant notre taxe", "Apaisement ; recettes en baisse", [E("country.NEGOTIATION_GOODWILL", 0.02), E("budget.oneOff", 0.3)], "ACCEPTED"),
  opt("support", "Soutenir les filières touchées", "Coût : 400 M€", [E("budget.oneOff", 0.4), E(G + "rural", 0.004), E("economy.output", -0.0003, days=90)], "PARTIAL")],
 ministry="economy", scope="FOREIGN_COUNTRY", scope_cooldown=700, conditions=[{"variable": "scope.relation", "max": 0.6}]))

add(event("disaster_abroad", "DIPLOMACY", "Séisme meurtrier {foreignIn}", "Des milliers de victimes ; le pays appelle à l'aide internationale.",
 0.0008, 200, "IMPORTANT",
 letter("disaster_abroad",
  [V("Séisme {foreignIn} : faut-il envoyer de l'aide ?"), V("Catastrophe {foreignIn}"), V("Aide humanitaire d'urgence")],
  [V("Un séisme de magnitude 7,5 a frappé {foreignThe} ; le bilan dépasse déjà plusieurs milliers de morts."),
   V("Les autorités locales lancent un appel à l'aide internationale."),
   V("Plusieurs ressortissants français sont portés disparus.")],
  [V("Nos partenaires européens annoncent déjà l'envoi d'équipes de secours."),
   V("Une aide rapide serait remarquée et appréciée.")],
  [V("Je vous propose d'envoyer la sécurité civile et une aide financière, ou une aide symbolique."),
   V("Votre décision est attendue rapidement.")]),
 "MINISTER", "symbolic", [
  opt("major", "Envoi massif de secours et aide financière", "Coût : 150 M€ ; relation renforcée", [E("budget.oneOff", 0.15), E("country.CRISIS_SOLIDARITY", 0.08), E("opinion.national", 0.003)], "ACCEPTED"),
  opt("symbolic", "Aide symbolique et message de soutien", "Coût minime", [E("budget.oneOff", 0.01), E("country.CRISIS_SOLIDARITY", 0.02)], "PARTIAL")],
 ministry="foreign", scope="FOREIGN_COUNTRY", scope_cooldown=1000))

json.dump({"events": EVENTS}, open(os.path.join(ROOT, "events", "national.json"), "w"), ensure_ascii=False, indent=1)
json.dump({"templates": TEMPLATES}, open(os.path.join(ROOT, "dialogue", "fr", "national.json"), "w"), ensure_ascii=False, indent=1)
print(len(EVENTS), "événements nationaux et internationaux", len(TEMPLATES), "modèles")
