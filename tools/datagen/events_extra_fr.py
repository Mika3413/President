"""Événements supplémentaires (France) : quatre histoires en plusieurs épisodes et une vingtaine
d'événements isolés -> assets/data/events/extra.json et dialogue/fr/extra.json.

Même format que events_national_fr.py : un épisode suivant n'arrive que comme conséquence d'un
choix (effet « chain.<id> » : probabilité et délai).
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


def chain(event, probability, delay):
    return E("chain." + event, probability, delay=delay)


def opt(id, label, hint, effects, outcome="NEUTRAL"):
    return {"id": id, "label": label, "hint": hint, "effects": list(effects), "outcome": outcome}


def mod(var, frm, to, fa, fb):
    return {"variable": var, "from": frm, "to": to, "factorAtFrom": fa, "factorAtTo": fb}


EVENTS, TEMPLATES = [], []


def event(id, category, headline, text, prob, cooldown, urgency, subjects, context, problem, request, sender, default, options,
          ministry=None, scope="NATIONAL", modifiers=(), conditions=(), days=4, scope_cooldown=0):
    m = {"template": id, "sender": sender, "responseDays": days, "defaultOption": default, "options": options}
    if ministry: m["ministry"] = ministry
    ev = {"id": id, "category": category, "scope": scope, "baseDailyProbability": prob, "cooldownDays": cooldown, "urgency": urgency,
          "headline": headline, "notificationText": text, "modifiers": list(modifiers), "immediateEffects": [], "message": m}
    if conditions: ev["conditions"] = list(conditions)
    if scope_cooldown: ev["scopeCooldownDays"] = scope_cooldown
    EVENTS.append(ev)
    TEMPLATES.append(letter(id, [V(s) for s in subjects], [V(s) for s in context], [V(s) for s in problem], [V(s) for s in request]))


# ============================ Histoire 1 : l'intelligence artificielle et l'emploi ============================
event("ai_layoffs", "ECONOMY", "Intelligence artificielle : un grand groupe supprime 5 000 postes", "Les syndicats parlent d'un « plan social numérique ».",
 0.0009, 700, "IMPORTANT",
 ["Licenciements liés à l'intelligence artificielle", "IA et emploi : un premier choc", "Plan social chez un champion du CAC 40"],
 ["Un grand groupe de services annonce la suppression de 5 000 postes, remplacés par des outils d'intelligence artificielle.",
  "Les métiers administratifs et les centres d'appels sont les premiers touchés.",
  "D'autres entreprises préparent des annonces similaires, selon la presse économique."],
 ["L'inquiétude gagne les salariés du tertiaire, qui se croyaient protégés.",
  "Si l'État ne réagit pas, le sujet pourrait dominer la prochaine campagne.",
  "Les investisseurs saluent pourtant des gains de productivité importants."],
 ["Je vous propose un grand plan de formation, une taxe sur les robots logiciels, ou de laisser faire le marché.",
  "Trois voies s'offrent à nous : accompagner, taxer, ou laisser faire."],
 "MINISTER", "train", [
  opt("train", "Grand plan de reconversion", "Coût : 1,5 Md€ ; salariés rassurés", [E("budget.oneOff", 1.5), E(G + "private_employees", 0.01), E("economy.naturalUnemployment", -0.0005, days=365)], "ACCEPTED"),
  opt("tax", "Taxer l'automatisation", "Recettes ; entreprises furieuses", [E("budget.oneOff", -1.0), E("economy.businessConfidence", -0.02), E(G + "private_employees", 0.006), chain("ai_exodus", 0.6, 25)], "PARTIAL"),
  opt("market", "Laisser faire le marché", "Croissance ; colère sociale", [E("economy.potentialGrowth", 0.0003, days=365), E(G + "private_employees", -0.012), chain("ai_exodus", 0.15, 30)], "REFUSED")],
 ministry="labour")

event("ai_exodus", "ECONOMY", "Les géants du numérique menacent de quitter la France", "Plusieurs entreprises gèlent leurs investissements.",
 0.0, 120, "URGENT",
 ["Le numérique menace de partir", "Exode des entreprises technologiques ?", "Investissements numériques gelés"],
 ["Trois grandes entreprises du numérique annoncent geler leurs investissements en France.",
  "Leurs dirigeants dénoncent un climat « hostile à l'innovation ».",
  "Plusieurs jeunes pousses envisagent de déménager à Londres ou à Berlin."],
 ["Des milliers d'emplois qualifiés sont en jeu.", "Nos partenaires européens se frottent les mains."],
 ["Je vous propose de revoir la taxe, de créer un fonds souverain pour nos champions, ou de tenir bon."],
 "MINISTER", "hold", [
  opt("fund", "Créer un fonds souverain pour l'IA", "Coût : 3 Md€ ; champion national possible", [E("budget.oneOff", 3.0), E("economy.businessConfidence", 0.02), chain("ai_champion", 0.6, 120)], "ACCEPTED"),
  opt("soften", "Assouplir la taxe", "Apaisement ; recettes perdues", [E("budget.oneOff", 0.6), E("economy.businessConfidence", 0.015), E(G + "private_employees", -0.004)], "PARTIAL"),
  opt("hold", "Tenir bon", "Principe ; investissements perdus", [E("economy.output", -0.002, days=180), E("economy.businessConfidence", -0.02), E(G + "low_income", 0.004)], "REFUSED")],
 ministry="economy")

event("ai_champion", "ECONOMY", "Un champion français de l'IA devient numéro un européen", "La jeune pousse lève des milliards et recrute massivement.",
 0.0, 300, "IMPORTANT",
 ["Un champion français de l'IA", "Succès technologique : la France en pointe", "Une réussite à célébrer"],
 ["Soutenue par le fonds souverain, une entreprise française devient la première société d'IA d'Europe.",
  "Elle annonce 4 000 recrutements et un nouveau centre de recherche."],
 ["Les États-Unis voient d'un mauvais œil l'émergence de ce concurrent.", "C'est l'occasion de faire de la France un pôle mondial."],
 ["Je vous propose une visite officielle et un grand plan de formation aux métiers de l'IA, ou de rester discret."],
 "MINISTER", "visit", [
  opt("visit", "Visite et plan de formation aux métiers de l'IA", "Coût : 500 M€ ; prestige", [E("budget.oneOff", 0.5), E("opinion.national", 0.005), E("economy.potentialGrowth", 0.0005, days=365), E(G + "young", 0.01)], "ACCEPTED"),
  opt("discreet", "Rester discret", "Aucun coût", [E("economy.businessConfidence", 0.01)], "NEUTRAL")],
 ministry="economy")

# ============================ Histoire 2 : la fuite de données de santé ============================
event("health_data_leak", "SECURITY", "Fuite massive : les données de santé de 20 millions de Français en ligne", "Des pirates revendiquent le vol et réclament une rançon.",
 0.0008, 800, "URGENT",
 ["Fuite de données de santé", "Cyberattaque contre l'assurance maladie", "Données médicales dérobées"],
 ["Un groupe de pirates affirme détenir les dossiers médicaux de 20 millions d'assurés.",
  "Un échantillon a été publié sur un forum : il paraît authentique.",
  "Les pirates exigent une rançon de 50 millions d'euros."],
 ["Les Français s'inquiètent : maladies, traitements, tout pourrait être exposé.",
  "Payer encouragerait d'autres attaques ; refuser expose les victimes."],
 ["Je vous propose de refuser de payer et de lancer une riposte, de négocier discrètement, ou d'informer chaque assuré et de renforcer nos défenses."],
 "MINISTER", "refuse", [
  opt("refuse", "Refuser et riposter avec nos services", "Fermeté ; les données seront publiées", [E(G + "seniors", -0.01), E("opinion.national", -0.004), chain("data_lawsuit", 0.7, 20)], "REFUSED"),
  opt("inform", "Informer chaque assuré et investir dans la cybersécurité", "Coût : 800 M€", [E("budget.oneOff", 0.8), E("opinion.national", 0.002), chain("data_lawsuit", 0.3, 25), chain("data_sovereignty", 0.6, 60)], "ACCEPTED"),
  opt("pay", "Négocier discrètement", "Risque de scandale", [E("budget.oneOff", 0.05), chain("data_lawsuit", 0.5, 15)], "PARTIAL")],
 ministry="health")

event("data_lawsuit", "POLITICS", "Fuite de données : 300 000 Français portent plainte contre l'État", "Une action collective sans précédent.",
 0.0, 200, "IMPORTANT",
 ["Plainte collective contre l'État", "Fuite de données : les victimes s'organisent", "Responsabilité de l'État mise en cause"],
 ["Des associations de patients ont rassemblé 300 000 plaignants.", "Leurs avocats réclament une indemnisation pour chaque victime."],
 ["Une condamnation coûterait très cher.", "L'opposition réclame une commission d'enquête."],
 ["Je vous propose d'indemniser à l'amiable, d'accepter une commission d'enquête, ou de contester devant les tribunaux."],
 "PRIME_MINISTER", "contest", [
  opt("settle", "Indemniser à l'amiable", "Coût : 1,2 Md€ ; affaire close", [E("budget.oneOff", 1.2), E("opinion.national", 0.004)], "ACCEPTED"),
  opt("inquiry", "Accepter une commission d'enquête", "Transparence ; débat prolongé", [E("government.parliamentSupport", 0.01), E("opinion.national", -0.002)], "PARTIAL"),
  opt("contest", "Contester devant les tribunaux", "Aucun coût immédiat ; image dégradée", [E("opinion.national", -0.006), E(G + "seniors", -0.006)], "REFUSED")])

event("data_sovereignty", "SECURITY", "Cloud souverain : les industriels proposent une alliance", "Une alternative européenne aux géants américains.",
 0.0, 300, "INFO",
 ["Projet de cloud souverain", "Souveraineté numérique : une proposition", "Héberger nos données en Europe"],
 ["Trois industriels français proposent de bâtir un cloud européen pour les données publiques.",
  "L'Allemagne se dit prête à s'associer au projet."],
 ["Le projet est coûteux et prendra des années.", "Washington y voit une mesure protectionniste."],
 ["Je vous propose de lancer le projet avec l'Allemagne, ou de rester sur les solutions existantes."],
 "MINISTER", "existing", [
  opt("launch", "Lancer le cloud souverain avec l'Allemagne", "Coût : 2 Md€ ; Europe renforcée", [E("budget.oneOff", 2.0), E("memory.DEU.NEGOTIATION_GOODWILL", 0.03), E("memory.USA.DISAGREEMENT", -0.01), E("economy.potentialGrowth", 0.0003, days=730)], "ACCEPTED"),
  opt("existing", "Garder les solutions existantes", "Aucun coût", [E("memory.USA.NEGOTIATION_GOODWILL", 0.01)], "REFUSED")],
 ministry="economy")

# ============================ Histoire 3 : la colère étudiante ============================
event("student_protest", "POLITICS", "Les étudiants dans la rue contre la précarité", "Des dizaines de milliers de manifestants dans les grandes villes.",
 0.0010, 400, "IMPORTANT",
 ["Mobilisation étudiante", "Précarité étudiante : la colère monte", "Les universités s'agitent"],
 ["Des dizaines de milliers d'étudiants ont manifesté contre la hausse des loyers et des frais d'inscription.",
  "Les images de files d'attente devant les distributions alimentaires ont choqué le pays.",
  "Les syndicats étudiants appellent à bloquer les universités."],
 ["Une partie de l'opinion soutient le mouvement.", "Le mouvement pourrait s'étendre aux lycées."],
 ["Je vous propose un repas à un euro et une hausse des bourses, un dialogue sans engagement, ou la fermeté."],
 "MINISTER", "dialogue", [
  opt("grants", "Repas à un euro et bourses revalorisées", "Coût : 900 M€ ; apaisement", [E("budget.oneOff", 0.9), E(G + "young", 0.02), E(G + "inactive", 0.015)], "ACCEPTED"),
  opt("dialogue", "Dialogue sans engagement", "Aucun coût ; le mouvement peut durer", [E(G + "young", -0.004), chain("campus_occupation", 0.4, 12)], "PARTIAL"),
  opt("firm", "Fermeté et évacuation des blocages", "Ordre ; colère accrue", [E(G + "young", -0.02), E(G + "seniors", 0.006), chain("campus_occupation", 0.7, 8)], "REFUSED")],
 ministry="education", modifiers=[mod("economy.inflation", 0.01, 0.05, 0.7, 2.5)])

event("campus_occupation", "SECURITY", "Universités occupées : les examens menacés", "Une vingtaine de campus bloqués.",
 0.0, 60, "URGENT",
 ["Campus occupés", "Les examens menacés", "Blocage des universités"],
 ["Une vingtaine d'universités sont occupées ; les présidents d'université appellent à l'aide.",
  "Des affrontements ont eu lieu lors d'une tentative d'évacuation."],
 ["Les examens de fin d'année pourraient être annulés.", "Le mouvement gagne les lycées."],
 ["Je vous propose une grande concertation, l'évacuation des campus, ou de reporter les examens."],
 "PRIME_MINISTER", "evacuate", [
  opt("talks", "Grande concertation sur la vie étudiante", "Coût : 500 M€ ; sortie de crise", [E("budget.oneOff", 0.5), E(G + "young", 0.012)], "ACCEPTED"),
  opt("evacuate", "Évacuer les campus", "Ordre rétabli ; mouvement national possible", [E(G + "young", -0.015), E(G + "seniors", 0.008), chain("youth_movement", 0.5, 10)], "REFUSED"),
  opt("postpone", "Reporter les examens", "Apaisement ; désordre administratif", [E("quality.education", -0.01), E(G + "young", 0.004)], "PARTIAL")],
 ministry="interior")

event("youth_movement", "POLITICS", "Un mouvement de jeunesse national s'organise", "La mobilisation gagne tout le pays.",
 0.0, 200, "URGENT",
 ["Un mouvement de jeunesse national", "La jeunesse se lève", "Mobilisation générale de la jeunesse"],
 ["Lycéens, étudiants et jeunes actifs défilent ensemble dans plus de cent villes.",
  "Leur porte-parole réclame un « plan Marshall pour la jeunesse »."],
 ["Ce mouvement pourrait marquer toute une génération d'électeurs.", "L'opposition tente de le récupérer."],
 ["Je vous propose de recevoir les porte-parole et d'annoncer un plan jeunesse, ou d'attendre la fin de l'année scolaire."],
 "PRIME_MINISTER", "wait", [
  opt("plan", "Recevoir les porte-parole et lancer un plan jeunesse", "Coût : 2 Md€ ; mouvement apaisé", [E("budget.oneOff", 2.0), E(G + "young", 0.03), E("opinion.national", 0.004)], "ACCEPTED"),
  opt("wait", "Attendre la fin de l'année scolaire", "Usure ; une génération perdue", [E(G + "young", -0.025), E("opinion.national", -0.005)], "REFUSED")])

# ============================ Histoire 4 : les frondeurs ============================
event("majority_rebels", "GOVERNMENT", "Des députés de la majorité entrent en fronde", "Une trentaine d'élus menacent de ne plus voter les textes.",
 0.0010, 500, "IMPORTANT",
 ["Fronde dans la majorité", "Des députés frondeurs", "Votre majorité se fissure"],
 ["Une trentaine de députés de la majorité publient une tribune critiquant la ligne du gouvernement.",
  "Ils réclament un « tournant social » et menacent de s'abstenir sur les prochains textes."],
 ["Sans eux, la majorité devient fragile.", "Les médias parlent déjà de crise politique."],
 ["Je vous propose de les recevoir et de faire des concessions, de les menacer d'exclusion, ou de les ignorer."],
 "PRIME_MINISTER", "ignore", [
  opt("concede", "Les recevoir et faire des concessions", "Majorité ressoudée ; ligne infléchie", [E("government.parliamentSupport", 0.03), E("spending.solidarity", 0.01), E(G + "self_employed", -0.004)], "ACCEPTED"),
  opt("threaten", "Menacer d'exclusion", "Autorité ; risque de scission", [E("government.parliamentSupport", -0.01), chain("group_split", 0.5, 20)], "REFUSED"),
  opt("ignore", "Les ignorer", "Aucun coût immédiat", [E("government.parliamentSupport", -0.02), chain("group_split", 0.3, 30)], "PARTIAL")],
 modifiers=[mod("opinion.national", 0.3, 0.55, 2.5, 0.6)])

event("group_split", "GOVERNMENT", "Scission : les frondeurs créent leur propre groupe", "La majorité perd des sièges.",
 0.0, 300, "URGENT",
 ["Scission dans la majorité", "Un nouveau groupe à l'Assemblée", "Votre majorité amputée"],
 ["Les frondeurs ont créé leur propre groupe parlementaire.", "Ils se disent « ni dans la majorité, ni dans l'opposition »."],
 ["Chaque texte devra désormais être négocié.", "L'opposition prépare une motion de censure."],
 ["Je vous propose un remaniement d'ouverture, un accord texte par texte, ou une dissolution."],
 "PRIME_MINISTER", "deal", [
  opt("reshuffle", "Remaniement d'ouverture", "Majorité élargie ; base agacée", [E("government.parliamentSupport", 0.04), E("opinion.national", -0.003)], "ACCEPTED"),
  opt("deal", "Accord texte par texte", "Fragile mais sans coût", [E("government.parliamentSupport", -0.01)], "PARTIAL"),
  opt("hardline", "Ignorer et gouverner par ordonnances", "Autorité ; risque de censure", [E("government.parliamentSupport", -0.04), E("opinion.national", -0.004)], "REFUSED")])

# ============================ Événements isolés ============================
def simple(id, category, headline, text, prob, cooldown, urgency, subjects, context, problem, request, sender, default, options, **kw):
    event(id, category, headline, text, prob, cooldown, urgency, subjects, context, problem, request, sender, default, options, **kw)


simple("drug_shortage", "DISASTER", "Pénurie de médicaments : antibiotiques et anticancéreux introuvables", "Les pharmacies sont en rupture.",
 0.0010, 400, "IMPORTANT",
 ["Pénurie de médicaments", "Ruptures en pharmacie", "Médicaments essentiels manquants"],
 ["Plus de 300 médicaments sont en rupture, dont des antibiotiques pour enfants.", "Les pharmaciens rationnent leurs stocks."],
 ["La production est concentrée en Asie.", "Les familles s'inquiètent à l'approche de l'hiver."],
 ["Je vous propose de relocaliser la production, d'importer en urgence, ou de laisser le marché se rétablir."],
 "MINISTER", "import", [
  opt("relocate", "Relocaliser la production", "Coût : 1 Md€ ; effet durable", [E("budget.oneOff", 1.0), E("quality.health", 0.01, days=365), E(G + "seniors", 0.008)], "ACCEPTED"),
  opt("import", "Importer en urgence", "Coût : 200 M€", [E("budget.oneOff", 0.2), E("quality.health", 0.003)], "PARTIAL"),
  opt("wait", "Laisser le marché se rétablir", "Aucun coût ; colère des familles", [E("quality.health", -0.01), E(G + "adults", -0.008)], "REFUSED")],
 ministry="health", modifiers=[mod("season.month", 9, 12, 1.0, 2.5)])

simple("doctor_strike", "POLITICS", "Les médecins généralistes ferment leurs cabinets", "Grève illimitée des médecins libéraux.",
 0.0008, 500, "IMPORTANT",
 ["Grève des médecins", "Cabinets fermés", "Les généralistes en colère"],
 ["Les médecins généralistes réclament une consultation à 35 euros.", "Les urgences hospitalières débordent."],
 ["Chaque jour de grève aggrave les déserts médicaux.", "L'Assurance maladie redoute le coût."],
 ["Je vous propose d'accepter la revalorisation, un compromis, ou de refuser."],
 "MINISTER", "compromise", [
  opt("accept", "Accepter la revalorisation", "Coût : 1,1 Md€ par an", [E("spending.health", 0.005), E("quality.health", 0.01), E(G + "self_employed", 0.006)], "ACCEPTED"),
  opt("compromise", "Compromis contre des gardes partagées", "Coût : 500 M€", [E("budget.oneOff", 0.5), E("quality.health", 0.004)], "PARTIAL"),
  opt("refuse", "Refuser", "Économie ; soins perturbés", [E("quality.health", -0.015), E(G + "rural", -0.008)], "REFUSED")],
 ministry="health")

simple("bird_flu", "DISASTER", "Grippe aviaire : des millions de volailles abattues", "L'épizootie frappe le Sud-Ouest.",
 0.0009, 365, "IMPORTANT",
 ["Grippe aviaire", "Épizootie dans les élevages", "Les éleveurs de volailles touchés"],
 ["Le virus de la grippe aviaire se propage dans les élevages du Sud-Ouest.", "Des millions de volailles doivent être abattues."],
 ["Les éleveurs sont au bord de la faillite.", "La vaccination des volailles inquiète nos clients à l'export."],
 ["Je vous propose d'indemniser et de vacciner, d'indemniser seulement, ou de laisser les filières s'organiser."],
 "MINISTER", "compensate", [
  opt("vaccinate", "Indemniser et vacciner", "Coût : 600 M€ ; épidémie stoppée", [E("budget.oneOff", 0.6), E(G + "rural", 0.01), E("quality.agriculture", 0.005)], "ACCEPTED"),
  opt("compensate", "Indemniser seulement", "Coût : 400 M€", [E("budget.oneOff", 0.4), E(G + "rural", 0.004)], "PARTIAL"),
  opt("none", "Laisser les filières s'organiser", "Aucun coût ; faillites", [E(G + "rural", -0.015), E("quality.agriculture", -0.01)], "REFUSED")],
 ministry="agriculture", modifiers=[mod("season.month", 1, 12, 2.0, 1.5)])

simple("wine_crisis", "ECONOMY", "Viticulture : des vignes arrachées par milliers d'hectares", "La surproduction et la baisse de la consommation frappent la filière.",
 0.0007, 600, "INFO",
 ["Crise viticole", "Les vignerons en détresse", "Arrachage de vignes"],
 ["La consommation de vin recule et les stocks débordent.", "Les vignerons du Languedoc et du Bordelais manifestent."],
 ["Des milliers d'exploitations familiales sont menacées.", "Les campagnes votent beaucoup."],
 ["Je vous propose un plan d'arrachage aidé, une distillation de crise, ou rien."],
 "MINISTER", "distill", [
  opt("uproot", "Plan d'arrachage aidé et reconversion", "Coût : 300 M€ ; filière assainie", [E("budget.oneOff", 0.3), E(G + "rural", 0.008)], "ACCEPTED"),
  opt("distill", "Distillation de crise", "Coût : 150 M€ ; répit", [E("budget.oneOff", 0.15), E(G + "rural", 0.004)], "PARTIAL"),
  opt("none", "Ne rien faire", "Aucun coût ; colère rurale", [E(G + "rural", -0.012)], "REFUSED")],
 ministry="agriculture")

simple("winter_olympics", "POLITICS", "Jeux olympiques d'hiver : la France peut se porter candidate", "Le mouvement sportif attend votre feu vert.",
 0.0004, 3000, "INFO",
 ["Candidature olympique", "Accueillir les Jeux d'hiver ?", "Le rêve olympique"],
 ["Le Comité olympique propose une candidature des Alpes françaises.", "Les régions concernées sont enthousiastes."],
 ["Le coût est élevé et le réchauffement menace l'enneigement.", "Les écologistes s'y opposent."],
 ["Je vous propose de soutenir pleinement la candidature, une candidature sobre, ou de renoncer."],
 "MINISTER", "sober", [
  opt("full", "Candidature ambitieuse", "Coût : 3 Md€ ; prestige et travaux", [E("budget.oneOff", 3.0), E("opinion.national", 0.006), E("economy.output", 0.001, days=730), E("quality.environment", -0.005)], "ACCEPTED"),
  opt("sober", "Candidature sobre, sites existants", "Coût : 1 Md€", [E("budget.oneOff", 1.0), E("opinion.national", 0.003)], "PARTIAL"),
  opt("no", "Renoncer", "Économie ; déception", [E(G + "young", -0.004)], "REFUSED")],
 ministry="education")

simple("arms_contract", "DIPLOMACY", "{foreignThe} veut acheter des avions de combat français", "Un contrat de plusieurs milliards est en jeu.",
 0.0007, 300, "IMPORTANT",
 ["Contrat d'armement en vue", "Avions de combat : une commande possible", "Export d'armement : votre arbitrage"],
 ["{foreignThe} souhaite acquérir une trentaine d'avions de combat français.", "Nos industriels sont en concurrence avec les Américains."],
 ["Le contrat ferait travailler des milliers de personnes.", "Des ONG dénoncent l'usage possible de ces armes."],
 ["Je vous propose de signer avec un transfert de technologie, de signer sans transfert, ou de refuser."],
 "MINISTER", "sign", [
  opt("transfer", "Signer avec transfert de technologie", "Contrat assuré ; savoir-faire partagé", [E("budget.oneOff", -2.0), E("country.NEGOTIATION_GOODWILL", 0.05), E("economy.output", 0.001, days=365)], "ACCEPTED"),
  opt("sign", "Signer sans transfert", "Contrat probable", [E("budget.oneOff", -1.0), E("country.NEGOTIATION_GOODWILL", 0.02)], "PARTIAL"),
  opt("refuse", "Refuser pour raisons éthiques", "Principes ; relation refroidie", [E("country.DISAGREEMENT", -0.03), E(G + "young", 0.004)], "REFUSED")],
 ministry="armed_forces", scope="FOREIGN_COUNTRY", scope_cooldown=900, conditions=[{"variable": "scope.relation", "min": 0.45}])

simple("space_success", "POLITICS", "Ariane réussit un lancement historique", "L'Europe spatiale célèbre une première mondiale.",
 0.0005, 600, "INFO",
 ["Succès spatial", "Ariane : une première", "La France dans l'espace"],
 ["La nouvelle fusée a placé en orbite une constellation européenne.", "C'est une première pour l'Europe."],
 ["Nos partenaires veulent accélérer le programme.", "Le budget spatial est déjà sous tension."],
 ["Je vous propose d'augmenter le budget spatial ou de saluer simplement ce succès."],
 "MINISTER", "salute", [
  opt("invest", "Augmenter le budget spatial", "Coût : 800 M€ ; avance technologique", [E("budget.oneOff", 0.8), E("economy.potentialGrowth", 0.0002, days=730), E("alliance.EU.NEGOTIATION_GOODWILL", 0.01), E("opinion.national", 0.002)], "ACCEPTED"),
  opt("salute", "Saluer ce succès", "Aucun coût", [E("opinion.national", 0.002)], "NEUTRAL")],
 ministry="economy")

simple("tax_leak", "ECONOMY", "« Évasion files » : des milliers de fortunes françaises dans les paradis fiscaux", "Une fuite de documents bancaires fait scandale.",
 0.0006, 700, "IMPORTANT",
 ["Scandale d'évasion fiscale", "Les fortunes cachées révélées", "Paradis fiscaux : la fuite"],
 ["Un consortium de journalistes révèle les comptes cachés de milliers de contribuables fortunés.", "Des sportifs et des patrons connus sont cités."],
 ["L'opinion réclame des sanctions.", "Les montants en jeu se chiffrent en milliards."],
 ["Je vous propose une cellule de redressement fiscal, une amnistie contre rapatriement, ou de laisser la justice faire."],
 "MINISTER", "justice", [
  opt("crackdown", "Cellule de redressement et sanctions", "Recettes ; riches mécontents", [E("budget.oneOff", -2.5), E(G + "low_income", 0.01), E(G + "high_income", -0.015)], "ACCEPTED"),
  opt("amnesty", "Amnistie contre rapatriement des fonds", "Recettes rapides ; scandale moral", [E("budget.oneOff", -4.0), E("opinion.national", -0.008)], "PARTIAL"),
  opt("justice", "Laisser la justice faire", "Aucun coût", [E(G + "low_income", -0.006)], "REFUSED")],
 ministry="economy")

simple("delivery_strike", "POLITICS", "Les livreurs des plateformes en grève", "Ils réclament le statut de salarié.",
 0.0008, 400, "INFO",
 ["Grève des livreurs", "Travailleurs des plateformes", "Ubérisation : la colère"],
 ["Des milliers de livreurs à vélo bloquent les restaurants des grandes villes.", "Ils réclament un salaire minimum et une protection sociale."],
 ["Bruxelles prépare une directive sur le sujet.", "Les plateformes menacent de réduire leur activité."],
 ["Je vous propose une présomption de salariat, un revenu minimum garanti, ou le statu quo."],
 "MINISTER", "minimum", [
  opt("employees", "Présomption de salariat", "Protection ; plateformes furieuses", [E(G + "young", 0.012), E(G + "low_income", 0.006), E("economy.businessConfidence", -0.01)], "ACCEPTED"),
  opt("minimum", "Revenu minimum garanti", "Compromis", [E(G + "young", 0.006)], "PARTIAL"),
  opt("status_quo", "Statu quo", "Aucun coût ; précarité", [E(G + "young", -0.008)], "REFUSED")],
 ministry="labour")

simple("child_protection", "POLITICS", "Scandale à l'aide sociale à l'enfance", "Un rapport accablant sur les foyers d'accueil.",
 0.0007, 600, "IMPORTANT",
 ["Protection de l'enfance : un rapport accablant", "Enfants placés en danger", "Scandale dans les foyers"],
 ["Un rapport révèle des violences dans plusieurs foyers de l'aide sociale à l'enfance.", "Des enfants placés sont livrés à eux-mêmes dans des hôtels."],
 ["Les départements manquent de moyens.", "L'émotion est immense."],
 ["Je vous propose une reprise par l'État avec des moyens nouveaux, un plan d'urgence, ou de laisser les départements agir."],
 "MINISTER", "plan", [
  opt("state", "Reprise par l'État et moyens nouveaux", "Coût : 1,5 Md€", [E("budget.oneOff", 1.5), E("quality.social", 0.015), E("opinion.national", 0.004)], "ACCEPTED"),
  opt("plan", "Plan d'urgence", "Coût : 400 M€", [E("budget.oneOff", 0.4), E("quality.social", 0.005)], "PARTIAL"),
  opt("departments", "Laisser les départements agir", "Aucun coût ; indignation", [E("opinion.national", -0.006)], "REFUSED")],
 ministry="justice")

simple("prison_escape", "SECURITY", "Évasion spectaculaire : un chef de gang s'échappe en hélicoptère", "La traque est lancée.",
 0.0005, 500, "IMPORTANT",
 ["Évasion spectaculaire", "Un détenu dangereux en fuite", "Prison : la faille"],
 ["Un chef de réseau de trafic de drogue s'est évadé par hélicoptère.", "Deux surveillants ont été blessés."],
 ["L'opposition parle de « faillite de l'État ».", "Les surveillants menacent de bloquer les prisons."],
 ["Je vous propose un plan de sécurisation des prisons, une enquête interne, ou de limoger le directeur."],
 "MINISTER", "inquiry", [
  opt("plan", "Plan de sécurisation des prisons", "Coût : 500 M€", [E("budget.oneOff", 0.5), E("quality.justice", 0.01), E("quality.security", 0.005)], "ACCEPTED"),
  opt("inquiry", "Enquête interne", "Aucun coût", [E("opinion.national", -0.002)], "PARTIAL"),
  opt("fire", "Limoger le directeur", "Responsable désigné ; surveillants furieux", [E(G + "civil_servants", -0.006), E(G + "seniors", 0.004)], "REFUSED")],
 ministry="justice")

simple("court_backlog", "POLITICS", "Les magistrats en colère : la justice au bord de l'asphyxie", "Une tribune signée par 3 000 magistrats.",
 0.0006, 600, "INFO",
 ["La justice asphyxiée", "Tribune des magistrats", "Délais de justice records"],
 ["Trois mille magistrats dénoncent des délais de jugement de plusieurs années.", "Des audiences se terminent au milieu de la nuit."],
 ["Les victimes attendent trop longtemps.", "La confiance dans la justice s'effrite."],
 ["Je vous propose de recruter massivement, de simplifier les procédures, ou de répondre que l'effort est déjà fait."],
 "MINISTER", "simplify", [
  opt("hire", "Recruter magistrats et greffiers", "Coût : 600 M€ par an", [E("spending.justice", 0.06), E("quality.justice", 0.015)], "ACCEPTED"),
  opt("simplify", "Simplifier les procédures", "Gratuit ; avocats réservés", [E("quality.justice", 0.005)], "PARTIAL"),
  opt("enough", "L'effort est déjà fait", "Aucun coût", [E("quality.justice", -0.005), E(G + "civil_servants", -0.004)], "REFUSED")],
 ministry="justice")

simple("corsica_tensions", "SECURITY", "Corse : attentats contre des résidences secondaires", "Le FLNC revendique.",
 0.0005, 600, "IMPORTANT",
 ["Tensions en Corse", "Corse : la violence revient", "Attentats en Corse"],
 ["Une série d'attentats a visé des résidences secondaires en Corse.", "Les élus nationalistes réclament l'autonomie."],
 ["Le dialogue est rompu depuis des mois.", "Toute concession serait scrutée par d'autres régions."],
 ["Je vous propose d'ouvrir des négociations sur l'autonomie, de renforcer la sécurité, ou les deux."],
 "MINISTER", "security", [
  opt("autonomy", "Ouvrir des négociations sur l'autonomie", "Apaisement ; débat national", [E("government.parliamentSupport", -0.01), E("opinion.national", 0.001)], "ACCEPTED"),
  opt("security", "Renforcer la sécurité", "Coût : 100 M€", [E("budget.oneOff", 0.1), E("quality.security", 0.003)], "REFUSED"),
  opt("both", "Fermeté et dialogue", "Coût : 100 M€ ; équilibre", [E("budget.oneOff", 0.1)], "PARTIAL")],
 ministry="interior")

simple("deepfake_president", "POLITICS", "Une fausse vidéo du président devient virale", "Une vidéo truquée par IA vous prête des propos choquants.",
 0.0007, 400, "URGENT",
 ["Fausse vidéo virale", "Deepfake présidentiel", "Désinformation : vous êtes visé"],
 ["Une vidéo truquée vous montre tenant des propos méprisants envers les retraités.", "Elle a été vue vingt millions de fois en une nuit."],
 ["Une partie du public croit qu'elle est authentique.", "Nos services soupçonnent une opération étrangère."],
 ["Je vous propose de démentir solennellement, de porter plainte et d'attaquer les plateformes, ou d'ignorer."],
 "PRIME_MINISTER", "deny", [
  opt("deny", "Démenti solennel à la télévision", "Limite les dégâts", [E("president.popularity", -0.01), E(G + "retirees", -0.004)], "PARTIAL"),
  opt("platforms", "Plainte et loi contre les plateformes", "Fermeté ; débat sur la liberté d'expression", [E("president.popularity", 0.005), E("government.parliamentSupport", -0.005)], "ACCEPTED"),
  opt("ignore", "Ignorer", "Le mal est fait", [E(G + "retirees", -0.012), E("president.popularity", -0.02)], "REFUSED")])

simple("foreign_interference", "DIPLOMACY", "Ingérence : {foreignThe} accusé de manipuler l'opinion française", "Des réseaux de faux comptes démantelés.",
 0.0006, 300, "IMPORTANT",
 ["Ingérence étrangère", "Manipulation de l'opinion", "Réseaux de faux comptes"],
 ["Nos services ont démantelé un réseau de milliers de faux comptes lié à {foreignThe}.", "Il diffusait de fausses informations sur l'immigration et l'énergie."],
 ["Des élections approchent.", "Nos partenaires ont subi des opérations similaires."],
 ["Je vous propose d'expulser des diplomates, de dénoncer publiquement, ou de rester discret."],
 "MINISTER", "denounce", [
  opt("expel", "Expulser des diplomates", "Fermeté ; représailles probables", [E("country.DISAGREEMENT", -0.06), E("opinion.national", 0.003)], "REFUSED"),
  opt("denounce", "Dénoncer publiquement", "Avertissement", [E("country.DISAGREEMENT", -0.02), E("alliance.EU.NEGOTIATION_GOODWILL", 0.01)], "PARTIAL"),
  opt("quiet", "Rester discret", "Pas d'escalade", [E("opinion.national", -0.002)], "NEUTRAL")],
 ministry="foreign", scope="FOREIGN_COUNTRY", scope_cooldown=900, conditions=[{"variable": "scope.relation", "max": 0.4}])

simple("embassy_attack", "DIPLOMACY", "L'ambassade de France attaquée {foreignIn}", "Des manifestants ont incendié une partie du bâtiment.",
 0.0005, 500, "URGENT",
 ["Ambassade attaquée", "Notre ambassade prise pour cible", "Sécurité de nos diplomates"],
 ["Des manifestants ont pénétré dans l'enceinte de notre ambassade {foreignIn}.", "Le personnel a été mis à l'abri ; un bâtiment a brûlé."],
 ["Les autorités locales ont tardé à intervenir.", "Nos ressortissants s'inquiètent."],
 ["Je vous propose de rappeler notre ambassadeur, d'exiger des excuses, ou d'évacuer nos ressortissants."],
 "MINISTER", "apology", [
  opt("recall", "Rappeler notre ambassadeur", "Rupture ; fermeté saluée", [E("country.DISAGREEMENT", -0.08), E("opinion.national", 0.003)], "REFUSED"),
  opt("apology", "Exiger des excuses et des réparations", "Fermeté mesurée", [E("country.DISAGREEMENT", -0.03)], "PARTIAL"),
  opt("evacuate", "Évacuer nos ressortissants", "Coût : 60 M€ ; prudence", [E("budget.oneOff", 0.06), E("opinion.national", 0.002)], "ACCEPTED")],
 ministry="foreign", scope="FOREIGN_COUNTRY", scope_cooldown=1500, conditions=[{"variable": "scope.relation", "max": 0.35}])

simple("mercosur_deal", "ECONOMY", "Accord UE–Mercosur : les agriculteurs bloquent les routes", "Bruxelles veut signer un grand accord commercial.",
 0.0005, 1000, "IMPORTANT",
 ["Accord avec le Mercosur", "Libre-échange : les agriculteurs inquiets", "Bruxelles et l'Amérique du Sud"],
 ["La Commission européenne s'apprête à signer un accord de libre-échange avec le Mercosur.", "Les agriculteurs français dénoncent une concurrence déloyale."],
 ["L'industrie y gagnerait de nouveaux marchés.", "Nos partenaires allemands y tiennent beaucoup."],
 ["Je vous propose de bloquer l'accord, d'exiger des clauses miroirs, ou de le soutenir."],
 "MINISTER", "mirror", [
  opt("block", "Bloquer l'accord", "Agriculteurs ravis ; Berlin agacé", [E(G + "rural", 0.015), E("memory.DEU.DISAGREEMENT", -0.03), E("alliance.EU.DISAGREEMENT", -0.01)], "REFUSED"),
  opt("mirror", "Exiger des clauses miroirs", "Compromis", [E(G + "rural", 0.005), E("alliance.EU.NEGOTIATION_GOODWILL", 0.005)], "PARTIAL"),
  opt("support", "Soutenir l'accord", "Industrie gagnante ; colère rurale", [E(G + "rural", -0.02), E("economy.businessConfidence", 0.01), E("memory.DEU.NEGOTIATION_GOODWILL", 0.03)], "ACCEPTED")],
 ministry="agriculture")

simple("eu_deficit_procedure", "ECONOMY", "Bruxelles ouvre une procédure pour déficit excessif", "La Commission exige un plan de redressement.",
 0.0012, 365, "IMPORTANT",
 ["Procédure pour déficit excessif", "Bruxelles hausse le ton", "Le budget français sous surveillance"],
 ["La Commission européenne ouvre une procédure pour déficit excessif contre la France.", "Elle exige un plan crédible de retour sous les 3 %."],
 ["Les marchés observent notre réponse.", "Une partie de l'opinion dénonce le diktat de Bruxelles."],
 ["Je vous propose un plan d'économies, une négociation sur le calendrier, ou de contester les règles."],
 "MINISTER", "negotiate", [
  opt("savings", "Plan d'économies crédible", "Marchés rassurés ; services réduits", [E("spending.state_operations", -0.03), E("economy.businessConfidence", 0.02), E("alliance.EU.NEGOTIATION_GOODWILL", 0.02), E(G + "civil_servants", -0.01)], "ACCEPTED"),
  opt("negotiate", "Négocier le calendrier", "Délai obtenu", [E("alliance.EU.NEGOTIATION_GOODWILL", 0.005)], "PARTIAL"),
  opt("contest", "Contester les règles européennes", "Souveraineté ; tensions", [E("alliance.EU.DISAGREEMENT", -0.03), E("economy.businessConfidence", -0.02), E(G + "low_income", 0.004)], "REFUSED")],
 ministry="economy", conditions=[{"variable": "economy.deficitRatio", "min": 0.045}])

simple("gas_price_spike", "ENERGY", "Le prix du gaz double en un mois", "Tensions sur les marchés mondiaux de l'énergie.",
 0.0007, 400, "IMPORTANT",
 ["Flambée du gaz", "Prix de l'énergie : alerte", "Le gaz hors de prix"],
 ["Le prix du gaz a doublé sur les marchés en un mois.", "Les industriels gros consommateurs réduisent leur production."],
 ["Les factures des ménages vont exploser cet hiver.", "Les boulangers et les artisans sont en première ligne."],
 ["Je vous propose un bouclier pour les petites entreprises, des contrats d'approvisionnement à long terme, ou d'attendre."],
 "MINISTER", "wait", [
  opt("shield", "Bouclier pour les artisans et PME", "Coût : 2 Md€", [E("budget.oneOff", 2.0), E(G + "self_employed", 0.012), E("economy.inflation", -0.001)], "ACCEPTED"),
  opt("contracts", "Contrats d'approvisionnement à long terme", "Stabilité ; dépendance", [E("economy.inflation", -0.0005), E("economy.businessConfidence", 0.005)], "PARTIAL"),
  opt("wait", "Attendre la détente des prix", "Aucun coût ; inflation", [E("economy.inflation", 0.003), E("economy.output", -0.001, days=120), E(G + "self_employed", -0.01)], "REFUSED")],
 ministry="ecology", modifiers=[mod("season.month", 6, 11, 0.6, 2.0)])

simple("nuclear_waste_site", "ENERGY", "Déchets nucléaires : le site d'enfouissement contesté", "Des milliers d'opposants sur place.",
 0.0005, 900, "INFO",
 ["Déchets nucléaires", "Le site d'enfouissement contesté", "Mobilisation antinucléaire"],
 ["Le chantier du site d'enfouissement des déchets radioactifs doit démarrer.", "Des milliers d'opposants occupent le terrain."],
 ["Sans ce site, nos piscines d'entreposage seront pleines d'ici dix ans.", "Les élus locaux sont divisés."],
 ["Je vous propose de lancer le chantier avec compensations, de repousser la décision, ou d'organiser un débat public."],
 "MINISTER", "debate", [
  opt("build", "Lancer le chantier avec des compensations", "Coût : 300 M€ ; filière sécurisée", [E("budget.oneOff", 0.3), E(G + "young", -0.006), E("economy.businessConfidence", 0.005)], "ACCEPTED"),
  opt("debate", "Débat public", "Six mois de répit", [E(G + "young", 0.003)], "PARTIAL"),
  opt("delay", "Repousser la décision", "Aucun coût ; problème reporté", [E("economy.businessConfidence", -0.005)], "REFUSED")],
 ministry="ecology")

simple("teen_screens", "POLITICS", "Écrans et adolescents : l'appel des pédiatres", "Mille médecins demandent d'interdire les réseaux sociaux avant 15 ans.",
 0.0006, 900, "INFO",
 ["Écrans et adolescents", "Réseaux sociaux : faut-il interdire ?", "L'alerte des pédiatres"],
 ["Mille pédiatres et psychiatres demandent d'interdire les réseaux sociaux aux moins de 15 ans.", "Les troubles anxieux explosent chez les adolescents."],
 ["Les plateformes jugent la mesure inapplicable.", "Les parents sont majoritairement favorables."],
 ["Je vous propose une interdiction avant 15 ans, une campagne de prévention, ou de laisser faire les familles."],
 "MINISTER", "prevention", [
  opt("ban", "Interdire les réseaux sociaux avant 15 ans", "Parents ravis ; jeunes furieux", [E(G + "adults", 0.01), E(G + "young", -0.008), E("quality.education", 0.005)], "ACCEPTED"),
  opt("prevention", "Campagne de prévention", "Coût : 50 M€", [E("budget.oneOff", 0.05), E(G + "adults", 0.003)], "PARTIAL"),
  opt("families", "Laisser faire les familles", "Aucun coût", [E(G + "adults", -0.004)], "REFUSED")],
 ministry="education")

simple("stadium_violence", "SECURITY", "Violences au stade : un match tourne à l'émeute", "Des dizaines de blessés.",
 0.0007, 300, "IMPORTANT",
 ["Violences au stade", "Hooliganisme", "Un match qui dégénère"],
 ["Des supporters ont envahi la pelouse et affronté la police ; on compte des dizaines de blessés.", "Les images ont fait le tour du monde."],
 ["La France doit accueillir une grande compétition l'an prochain.", "Les clubs rejettent la faute sur la police."],
 ["Je vous propose des interdictions de stade massives, la reconnaissance faciale dans les stades, ou de laisser la fédération sanctionner."],
 "MINISTER", "federation", [
  opt("bans", "Interdictions de stade massives", "Fermeté", [E("quality.security", 0.004), E(G + "seniors", 0.004)], "ACCEPTED"),
  opt("cameras", "Reconnaissance faciale dans les stades", "Efficace ; libertés en débat", [E("quality.security", 0.008), E(G + "young", -0.006)], "PARTIAL"),
  opt("federation", "Laisser la fédération sanctionner", "Aucun coût", [E("opinion.national", -0.002)], "REFUSED")],
 ministry="interior")

simple("tech_hq", "ECONOMY", "Un géant du numérique choisit Paris pour son siège européen", "Des milliers d'emplois qualifiés à la clé.",
 0.0005, 700, "INFO",
 ["Un siège européen à Paris", "Bonne nouvelle pour l'emploi", "Attractivité : un succès"],
 ["Un grand groupe technologique hésite entre Paris, Dublin et Amsterdam pour son siège européen.", "Il promet 3 000 emplois qualifiés."],
 ["Ses dirigeants demandent des garanties fiscales.", "Une victoire serait un symbole fort."],
 ["Je vous propose un accompagnement exceptionnel, une offre standard, ou de refuser toute faveur fiscale."],
 "MINISTER", "standard", [
  opt("vip", "Accompagnement exceptionnel", "Coût : 200 M€ ; siège quasi assuré", [E("budget.oneOff", 0.2), E("economy.businessConfidence", 0.02), E("economy.output", 0.001, days=365), E(G + "low_income", -0.003)], "ACCEPTED"),
  opt("standard", "Offre standard", "Chances moyennes", [E("economy.businessConfidence", 0.005)], "PARTIAL"),
  opt("refuse", "Aucune faveur fiscale", "Principe", [E(G + "low_income", 0.003)], "REFUSED")],
 ministry="economy")

simple("ski_no_snow", "ECONOMY", "Stations de ski sans neige : la montagne en détresse", "Saison catastrophique dans les Alpes et les Pyrénées.",
 0.0008, 365, "INFO",
 ["Saison blanche ratée", "Stations de ski en difficulté", "La montagne sans neige"],
 ["Faute de neige, une centaine de stations n'ont pas pu ouvrir.", "Les saisonniers se retrouvent sans emploi."],
 ["Le réchauffement rend le modèle du ski fragile.", "Les élus de montagne réclament des aides."],
 ["Je vous propose un plan de diversification, des canons à neige, ou une aide d'urgence."],
 "MINISTER", "emergency", [
  opt("diversify", "Plan de diversification quatre saisons", "Coût : 500 M€ ; effet durable", [E("budget.oneOff", 0.5), E(G + "rural", 0.006), E("quality.environment", 0.003)], "ACCEPTED"),
  opt("cannons", "Financer des canons à neige", "Coût : 200 M€ ; écologistes furieux", [E("budget.oneOff", 0.2), E(G + "rural", 0.004), E("quality.environment", -0.005)], "PARTIAL"),
  opt("emergency", "Aide d'urgence aux saisonniers", "Coût : 100 M€", [E("budget.oneOff", 0.1), E(G + "rural", 0.002)], "NEUTRAL")],
 ministry="economy", modifiers=[mod("season.month", 1, 12, 2.5, 2.0)])

json.dump({"events": EVENTS}, open(os.path.join(ROOT, "events", "extra.json"), "w"), ensure_ascii=False, indent=1)
json.dump({"templates": TEMPLATES}, open(os.path.join(ROOT, "dialogue", "fr", "extra.json"), "w"), ensure_ascii=False, indent=1)
print(len(EVENTS), "événements supplémentaires,", len(TEMPLATES), "modèles")
