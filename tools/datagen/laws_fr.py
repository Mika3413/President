"""Lois de société, justice, travail, libertés et Constitution (France) -> assets/data/countries/FRA/laws.json.

Chaque loi a une situation actuelle (première option, en vigueur au début) et des alternatives.
Changer une loi passe par un vote du Parlement (difficulté propre à chaque option) ; une réforme
constitutionnelle peut aussi passer par référendum. Chaque option a :
  - des effets à l'adoption (effects) et progressifs (longTerm) ;
  - un effet sur la fréquence d'événements (events) ;
  - un effet sur trois indices : libertés publiques, liberté de la presse, État de droit ;
  - des « drapeaux » lus par la simulation (durée du mandat, limite de mandats, 49.3, censure).
  - un attrait auprès des électeurs en cas de référendum (appeal).
"""
import json, os, re

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data")
G = "opinion.group."


def e(target, amount, days=0, delay=0):
    d = {"target": target, "amount": amount}
    if days: d["days"] = days
    if delay: d["delayDays"] = delay
    return d


def opt(id, label, description, difficulty=0.0, effects=(), long=(), events=None, liberty=0, press=0, rule=0, flags=None, appeal=0.0, reform=None):
    o = {"id": id, "label": label, "description": description, "difficulty": difficulty}
    if reform: o["reform"] = reform
    if effects: o["effects"] = list(effects)
    if long: o["longTerm"] = list(long)
    if events: o["events"] = events
    if liberty: o["liberty"] = liberty
    if press: o["press"] = press
    if rule: o["rule"] = rule
    if flags: o["flags"] = flags
    if appeal: o["appeal"] = appeal
    return o


LAWS = []


def law(category, id, title, description, options, constitutional=False, numeric=None):
    """numeric : la loi se règle par un nombre (heures, âge...). « values » donne la valeur de chaque
    option ; toute valeur intermédiaire produit des effets interpolés entre les options voisines."""
    d = {"id": id, "category": category, "title": title, "description": description, "constitutional": constitutional, "options": options}
    if numeric:
        assert len(numeric["values"]) == len(options), id
        d["numeric"] = numeric
    LAWS.append(d)


CATEGORIES = [
    ("societe", "Société", "♥", "Mœurs, famille, laïcité, fin de vie."),
    ("justice", "Justice et sécurité", "⚖", "Peines, police, armes, prisons."),
    ("travail", "Travail et social", "⚒", "Durée du travail, salaire minimum, grève, dimanche."),
    ("libertes", "Libertés et médias", "▤", "Presse, internet, surveillance, manifestations."),
    ("institutions", "Constitution et institutions", "⌂", "Mandat, régime, référendum, mode de scrutin."),
]

# --- Société -------------------------------------------------------------------------------------
law("societe", "end_of_life", "Fin de vie", "Accompagner les malades en fin de vie.", [
    opt("palliative", "Soins palliatifs, sédation profonde", "La situation actuelle."),
    opt("assisted", "Aide à mourir encadrée", "Pour les malades incurables, avec l'avis de médecins.", 0.04,
        [e(G + "young", 0.008), e(G + "urban", 0.008), e(G + "seniors", -0.006)], liberty=2, appeal=0.12),
])
law("societe", "cannabis", "Cannabis", "Le cannabis est interdit ; la consommation reste répandue.", [
    opt("forbidden", "Interdit, amende forfaitaire", "La situation actuelle."),
    opt("decriminalized", "Dépénalisé (usage personnel)", "Plus de poursuites pour usage ; trafic toujours réprimé.", 0.06,
        [e(G + "young", 0.012), e(G + "seniors", -0.012), e("quality.justice", 0.004)], liberty=2, appeal=-0.02),
    opt("legal", "Légal et encadré par l'État", "Vente contrôlée, taxée ; le trafic perd des revenus.", 0.12,
        [e(G + "young", 0.018), e(G + "seniors", -0.02), e(G + "rural", -0.008)], [e("budget.oneOff", -1.0, 365, 180), e("quality.security", 0.004, 0, 180)],
        events={"urban_riots": 0.9}, liberty=3, appeal=-0.08),
])
law("societe", "secularism", "Laïcité et signes religieux", "Les signes religieux sont interdits à l'école publique.", [
    opt("school", "Interdits à l'école publique", "La situation actuelle."),
    opt("public_space", "Interdits dans tout l'espace public", "Y compris la rue et l'université.", 0.12,
        [e(G + "seniors", 0.012), e(G + "young", -0.012), e(G + "urban", -0.01), e("alliance.EU.DISAGREEMENT", -0.01)],
        events={"urban_riots": 1.2, "city_demonstration": 1.2}, liberty=-6, rule=-3, appeal=0.04),
    opt("relaxed", "Assouplissement (accompagnatrices scolaires)", "Les mères accompagnatrices peuvent porter un signe religieux.", 0.06,
        [e(G + "urban", 0.006), e(G + "seniors", -0.012)], liberty=2, appeal=-0.1),
])
law("societe", "surrogacy", "Gestation pour autrui", "La GPA est interdite en France.", [
    opt("forbidden", "Interdite", "La situation actuelle."),
    opt("ethical", "Autorisée, encadrée et non rémunérée", "Sur le modèle britannique.", 0.1,
        [e(G + "young", 0.006), e(G + "seniors", -0.012), e(G + "rural", -0.008)], liberty=1, appeal=-0.12),
])
law("societe", "nationality", "Droit du sol", "Un enfant né en France de parents étrangers peut devenir français à sa majorité.", [
    opt("current", "Droit du sol à la majorité", "La situation actuelle."),
    opt("restricted", "Droit du sol restreint", "Il faut avoir un parent en situation régulière et en faire la demande.", 0.08,
        [e(G + "seniors", 0.01), e(G + "young", -0.008), e("demography.immigration", -0.03)], liberty=-1, appeal=0.08),
    opt("abolished", "Suppression du droit du sol", "Réforme constitutionnelle très contestée.", 0.18,
        [e(G + "seniors", 0.012), e(G + "urban", -0.015), e("alliance.EU.DISAGREEMENT", -0.008)], events={"urban_riots": 1.25}, liberty=-3, rule=-3, appeal=0.0),
])
law("societe", "family_reunification", "Regroupement familial", "Les étrangers réguliers peuvent faire venir leur famille sous conditions.", [
    opt("current", "Sous conditions de ressources et de logement", "La situation actuelle."),
    opt("strict", "Conditions durcies (langue, revenus)", "Délais et exigences renforcés.", 0.06,
        [e(G + "seniors", 0.008), e("demography.immigration", -0.04)], liberty=-1, appeal=0.1),
    opt("suspended", "Suspendu pour cinq ans", "Contraire aux engagements européens.", 0.16,
        [e(G + "seniors", 0.01), e("demography.immigration", -0.08), e("alliance.EU.DISAGREEMENT", -0.02)], liberty=-3, rule=-4, appeal=0.02),
])
law("societe", "social_media_minors", "Réseaux sociaux et mineurs", "Les mineurs accèdent librement aux réseaux sociaux.", [
    opt("free", "Accès libre", "La situation actuelle."),
    opt("under15", "Interdits avant 15 ans (vérification d'âge)", "Les plateformes doivent vérifier l'âge.", 0.03,
        [e(G + "adults", 0.012), e(G + "young", -0.008), e("memory.USA.DISAGREEMENT", -0.01)], liberty=-1, appeal=0.2),
], numeric={"unit": "ans", "values": [0, 15], "min": 0, "max": 18, "step": 1, "decimals": 0, "zeroLabel": "Aucun âge minimum"})
law("societe", "bullfighting", "Corrida", "La corrida est autorisée là où existe une tradition locale.", [
    opt("tradition", "Autorisée par tradition locale", "La situation actuelle."),
    opt("banned", "Interdite", "Les régions taurines protestent.", 0.05, [e(G + "urban", 0.006), e(G + "rural", -0.006)], appeal=0.1),
])
law("societe", "hunting", "Chasse", "La chasse est autorisée tous les jours de la saison.", [
    opt("current", "Tous les jours", "La situation actuelle."),
    opt("sunday_ban", "Interdite le dimanche", "Pour les promeneurs ; les chasseurs sont furieux.", 0.06,
        [e(G + "urban", 0.006), e(G + "rural", -0.014)], appeal=0.08),
])

law("societe", "fracking", "Gaz de schiste", "La loi de 2011 interdit la fracturation hydraulique.", [
    opt("banned", "Fracturation interdite", "La situation actuelle.", flags={"shaleBan": 0}),
    opt("allowed", "Exploration et exploitation autorisées", "Le gouvernement peut ensuite autoriser des forages (panneau « Commerce »).", 0.1,
        [e(G + "young", -0.012), e(G + "rural", -0.006), e("economy.businessConfidence", 0.004)], appeal=-0.1, flags={"shaleBan": 1}),
])

# --- Justice et sécurité -------------------------------------------------------------------------
law("justice", "death_penalty", "Peine de mort", "Abolie en 1981, interdite par la Constitution et les traités européens.", [
    opt("abolished", "Abolie", "La situation actuelle."),
    opt("terrorism", "Rétablie pour les crimes terroristes", "Rupture avec la Convention européenne des droits de l'homme.", 0.3,
        [e(G + "seniors", 0.01), e(G + "young", -0.015), e("alliance.EU.DISAGREEMENT", -0.06), e("quality.justice", -0.01)],
        liberty=-6, rule=-15, appeal=-0.05),
], constitutional=True)
law("justice", "minimum_sentences", "Peines planchers", "Les juges fixent librement la peine dans la limite du maximum.", [
    opt("free", "Liberté du juge", "La situation actuelle."),
    opt("floors", "Peines planchers pour les récidivistes", "Plus de détenus, prisons surpeuplées.", 0.06,
        [e(G + "seniors", 0.012), e(G + "young", -0.006)], [e("quality.security", 0.006, 0, 180), e("budget.oneOff", 0.4, 365)],
        events={"prison_overcrowding": 1.5, "court_backlog": 1.2}, rule=-2, appeal=0.15),
])
law("justice", "juvenile_justice", "Justice des mineurs", "Les mineurs bénéficient d'une excuse de minorité.", [
    opt("current", "Excuse de minorité", "La situation actuelle."),
    opt("16_adult", "Jugés comme des majeurs dès 16 ans pour les crimes", "Comparution immédiate possible.", 0.08,
        [e(G + "seniors", 0.012), e(G + "young", -0.012)], [e("quality.security", 0.004, 0, 180)], rule=-3, appeal=0.12),
])
law("justice", "life_sentence", "Perpétuité réelle", "La perpétuité réelle est réservée à de rares crimes.", [
    opt("rare", "Exceptionnelle", "La situation actuelle."),
    opt("extended", "Étendue aux crimes terroristes et aux meurtres de policiers", "Message de fermeté.", 0.05,
        [e(G + "seniors", 0.01), e("quality.security", 0.003)], rule=-1, appeal=0.15),
])
law("justice", "firearms", "Port d'armes", "Le port d'armes est strictement réservé à quelques professions.", [
    opt("strict", "Strictement encadré", "La situation actuelle."),
    opt("relaxed", "Assoupli pour la légitime défense", "Les armes se multiplient dans les foyers.", 0.14,
        [e(G + "rural", 0.006), e(G + "urban", -0.012), e("quality.security", -0.01, 0, 120)], events={"terror_attack": 1.1, "local_violence_school": 1.3}, appeal=-0.15),
])
law("justice", "police_cameras", "Caméras-piétons et contrôle des forces de l'ordre", "Les caméras-piétons sont facultatives.", [
    opt("optional", "Facultatives", "La situation actuelle."),
    opt("mandatory", "Obligatoires et enregistrement systématique", "Moins de bavures, plus de preuves.", 0.03,
        [e("budget.oneOff", 0.15), e(G + "young", 0.008)], events={"police_incident": 0.6, "urban_riots": 0.85}, rule=2, appeal=0.12),
])
law("justice", "national_service", "Service militaire", "Le service militaire est suspendu depuis 1997.", [
    opt("suspended", "Suspendu", "La situation actuelle."),
    opt("voluntary", "Service volontaire rémunéré", "Quelques dizaines de milliers de jeunes par an.", 0.03,
        [e("budget.oneOff", 0.6, 365), e("military.readiness", 0.01, 0, 120), e(G + "young", 0.004)], appeal=0.1),
    opt("mandatory", "Service obligatoire de six mois", "Coûteux ; la jeunesse proteste.", 0.12,
        [e("budget.oneOff", 3.0, 365), e("military.readiness", 0.03, 0, 365), e(G + "young", -0.02), e(G + "seniors", 0.012)],
        events={"student_protest": 1.5}, liberty=-1, appeal=0.02),
])

# --- Travail et social ---------------------------------------------------------------------------
law("travail", "work_week", "Durée légale du travail", "35 heures par semaine, heures supplémentaires majorées.", [
    opt("35h", "35 heures", "La situation actuelle."),
    opt("39h", "39 heures", "Plus de production, salariés mécontents.", 0.1,
        [e(G + "private_employees", -0.02), e(G + "civil_servants", -0.015), e("economy.businessConfidence", 0.02)],
        [e("economy.potentialGrowth", 0.0015, 365)], events={"national_strike": 1.4}, appeal=-0.15),
    opt("32h", "32 heures (semaine de quatre jours)", "Plus de temps libre ; coût pour les entreprises.", 0.12,
        [e(G + "private_employees", 0.015), e(G + "young", 0.012), e("economy.businessConfidence", -0.03)],
        [e("economy.potentialGrowth", -0.0015, 365)], appeal=0.05),
], numeric={"unit": "h", "values": [35, 39, 32], "min": 30, "max": 42, "step": 0.5, "decimals": 1})
law("travail", "minimum_wage", "SMIC national ou régional", "Un SMIC unique dans tout le pays (son niveau se fixe par décret).", [
    opt("indexed", "SMIC national unique", "La situation actuelle."),
    opt("regional", "SMIC régional modulé", "Plus bas là où la vie coûte moins cher.", 0.12,
        [e(G + "low_income", -0.02), e(G + "rural", -0.012), e("economy.businessConfidence", 0.015)], events={"national_strike": 1.3}, appeal=-0.25),
])
law("travail", "sunday_work", "Travail le dimanche", "Autorisé dans les zones touristiques et quelques commerces.", [
    opt("limited", "Limité (zones touristiques)", "La situation actuelle."),
    opt("free", "Libre avec majoration", "Les commerces ouvrent le dimanche partout.", 0.05,
        [e("sector.retail", 0.02), e(G + "private_employees", -0.008), e(G + "self_employed", 0.006)], appeal=-0.02),
])
law("travail", "strike_service", "Service minimum", "Un service minimum existe dans les transports, sans réquisition.", [
    opt("current", "Prévisibilité (déclaration 48 h avant)", "La situation actuelle."),
    opt("guaranteed", "Service minimum garanti (transports, écoles)", "Réquisitions possibles ; syndicats furieux.", 0.08,
        [e(G + "self_employed", 0.01), e(G + "civil_servants", -0.015)], events={"rail_strike": 0.6, "national_strike": 0.85}, liberty=-2, appeal=0.15),
])
law("travail", "unemployment_rules", "Assurance chômage", "Six mois travaillés pour ouvrir des droits.", [
    opt("current", "Règles actuelles", "La situation actuelle."),
    opt("strict", "Durcies (douze mois, dégressivité)", "Économies, retour à l'emploi plus rapide, colère syndicale.", 0.06,
        [e("budget.oneOff", -2.0, 365), e(G + "low_income", -0.015), e(G + "inactive", -0.015)], [e("economy.naturalUnemployment", -0.002, 365)], appeal=-0.05,
        reform="unemployment_insurance"),
    opt("generous", "Assouplies (quatre mois)", "Protection accrue, coût élevé.", 0.04,
        [e("budget.oneOff", 2.5, 365), e(G + "low_income", 0.012)], appeal=0.1),
])

# --- Libertés et médias --------------------------------------------------------------------------
law("libertes", "mass_surveillance", "Renseignement et surveillance", "Algorithmes de surveillance encadrés par une autorité indépendante.", [
    opt("framed", "Encadrée par une autorité indépendante", "La situation actuelle."),
    opt("extended", "Surveillance algorithmique étendue", "Plus d'attentats déjoués ; libertés réduites.", 0.08,
        [e(G + "seniors", 0.008), e(G + "young", -0.01)], events={"terror_attack": 0.75, "cyber_espionage": 0.85}, liberty=-6, press=-3, rule=-3, appeal=0.02),
    opt("restricted", "Contrôle renforcé du juge", "Libertés protégées ; les services se plaignent.", 0.05,
        [e(G + "young", 0.006)], events={"terror_attack": 1.15}, liberty=3, rule=2, appeal=0.0),
])
law("libertes", "facial_recognition", "Reconnaissance faciale", "Interdite dans l'espace public.", [
    opt("banned", "Interdite", "La situation actuelle."),
    opt("events", "Autorisée lors des grands événements", "Expérimentation encadrée.", 0.05, [e("quality.security", 0.004)], events={"terror_attack": 0.9}, liberty=-3, appeal=0.05),
    opt("general", "Généralisée", "Sécurité accrue ; Bruxelles proteste.", 0.14,
        [e("quality.security", 0.01), e(G + "young", -0.012), e("alliance.EU.DISAGREEMENT", -0.015)], events={"terror_attack": 0.8, "urban_riots": 0.9}, liberty=-10, rule=-4, appeal=-0.08),
])
law("libertes", "press_sources", "Secret des sources", "Le secret des sources des journalistes est protégé, avec des exceptions.", [
    opt("current", "Protégé avec exceptions", "La situation actuelle."),
    opt("strong", "Protection renforcée", "Les journalistes enquêtent plus librement.", 0.04, [e(G + "urban", 0.004)],
        events={"president_scandal": 1.2, "minister_scandal": 1.2}, press=6, rule=2, appeal=0.08),
    opt("weakened", "Affaibli (sécurité nationale)", "Les sources se taisent ; la presse s'indigne.", 0.1,
        [e("alliance.EU.DISAGREEMENT", -0.01)], events={"president_scandal": 0.7, "minister_scandal": 0.7, "defense_leak": 0.6}, press=-10, rule=-3, appeal=-0.12),
])
law("libertes", "public_broadcasting", "Audiovisuel public", "France Télévisions et Radio France, financés par l'impôt.", [
    opt("current", "Public et indépendant", "La situation actuelle."),
    opt("merged", "Fusionné en une holding", "Économies d'échelle ; inquiétude des rédactions.", 0.06, [e("budget.oneOff", -0.3, 365)], press=-2, appeal=0.0),
    opt("privatized", "Privatisation partielle", "Recettes pour l'État, moins de pluralisme.", 0.14,
        [e("budget.oneOff", -3.0), e(G + "seniors", -0.01)], press=-6, appeal=-0.1),
])
law("libertes", "disinformation", "Fausses informations", "Un juge peut faire retirer une fausse information en période électorale.", [
    opt("electoral", "Retrait par le juge en période électorale", "La situation actuelle."),
    opt("permanent", "Retrait administratif permanent", "Efficace contre les manipulations ; risque de censure.", 0.1,
        [e("memory.USA.DISAGREEMENT", -0.01)], events={"deepfake_president": 0.6, "foreign_interference": 0.7}, liberty=-4, press=-6, appeal=0.02),
])
law("libertes", "internet_control", "Internet", "Internet est libre, sous le contrôle du juge.", [
    opt("free", "Libre, contrôle du juge", "La situation actuelle."),
    opt("blocking", "Blocage administratif des sites", "Contre le terrorisme et la haine en ligne.", 0.08, [], events={"terror_attack": 0.95}, liberty=-4, press=-4, appeal=0.05),
    opt("filtered", "Filtrage généralisé des réseaux", "Un internet sous contrôle, à la chinoise.", 0.25,
        [e(G + "young", -0.025), e("alliance.EU.DISAGREEMENT", -0.04), e("sector.tech", -0.03)], events={"student_protest": 1.5}, liberty=-12, press=-15, rule=-6, appeal=-0.3),
])
law("libertes", "right_to_protest", "Droit de manifester", "Les manifestations se déclarent ; le préfet peut les interdire.", [
    opt("current", "Déclaration préalable", "La situation actuelle."),
    opt("restricted", "Interdictions préventives et fichage", "Moins de casseurs ; les syndicats crient à l'atteinte aux libertés.", 0.1,
        [e(G + "young", -0.01), e(G + "civil_servants", -0.008)], events={"fuel_protest_hardens": 0.8, "urban_riots": 0.9, "city_demonstration": 0.85}, liberty=-6, rule=-2, appeal=-0.05),
])
law("libertes", "lobbying", "Lobbying et transparence", "Les représentants d'intérêts s'inscrivent sur un registre.", [
    opt("register", "Registre déclaratif", "La situation actuelle."),
    opt("strict", "Agenda public des décideurs, cadeaux interdits", "Transparence accrue ; les lobbies grincent.", 0.05,
        [e("opinion.national", 0.004), e("economy.businessConfidence", -0.004)], events={"minister_scandal": 0.8, "president_scandal": 0.85}, rule=3, appeal=0.2),
])

# --- Constitution et institutions ----------------------------------------------------------------
law("institutions", "term_length", "Durée du mandat présidentiel", "Cinq ans depuis 2000 (quinquennat).", [
    opt("five", "Cinq ans", "La situation actuelle.", flags={"termYears": 5}),
    opt("seven", "Sept ans, non renouvelable", "Retour au septennat, mandat unique.", 0.15,
        [e("opinion.national", -0.006)], flags={"termYears": 7, "termLimit": 1}, appeal=-0.15),
], constitutional=True)
law("institutions", "term_limit", "Limite des mandats", "Pas plus de deux mandats consécutifs.", [
    opt("two", "Deux mandats consécutifs", "La situation actuelle.", flags={"termLimit": 2}),
    opt("unlimited", "Pas de limite", "Vous pourriez rester au pouvoir... et on vous le reprochera.", 0.22,
        [e("opinion.national", -0.02), e("president.popularity", -0.02), e("alliance.EU.DISAGREEMENT", -0.01)], rule=-6, flags={"termLimit": 0}, appeal=-0.3),
], constitutional=True)
law("institutions", "article_49_3", "Article 49.3", "Le gouvernement peut faire adopter un texte sans vote, au risque d'une censure.", [
    opt("kept", "Maintenu", "La situation actuelle.", flags={"forcePass": 1}),
    opt("limited", "Limité aux budgets", "Le gouvernement ne peut plus forcer les autres textes.", 0.05,
        [e("opinion.national", 0.008), e("government.parliamentSupport", 0.01)], rule=2, flags={"forcePass": 0}, appeal=0.25),
], constitutional=True)
law("institutions", "regime", "Régime politique", "Ve République : un président fort, un gouvernement responsable devant l'Assemblée.", [
    opt("semi", "Semi-présidentiel", "La situation actuelle.", flags={"censure": 1}),
    opt("presidential", "Présidentiel (pas de censure ni de dissolution)", "Le président gouverne seul ; l'Assemblée vote les lois.", 0.2,
        [e("opinion.national", -0.01), e("government.parliamentSupport", -0.03)], rule=-2, flags={"censure": 0}, appeal=-0.1),
    opt("parliamentary", "Parlementaire (VIe République)", "Le Premier ministre gouverne, le président arbitre.", 0.2,
        [e("opinion.national", 0.01), e("government.parliamentSupport", 0.03), e("president.popularity", -0.02)], rule=3, flags={"censure": 1}, appeal=0.05),
], constitutional=True)
law("institutions", "citizen_referendum", "Référendum d'initiative citoyenne", "Le référendum d'initiative partagée existe mais n'a jamais abouti.", [
    opt("shared", "Initiative partagée (quasi inaccessible)", "La situation actuelle."),
    opt("ric", "Référendum d'initiative citoyenne", "Avec 700 000 signatures, les citoyens peuvent proposer une loi.", 0.12,
        [e("opinion.national", 0.015), e(G + "low_income", 0.01), e("government.parliamentSupport", -0.02)], events={"fuel_protest_hardens": 0.7, "national_strike": 0.9}, liberty=3, appeal=0.3),
], constitutional=True)
law("institutions", "proportional", "Mode de scrutin législatif", "Scrutin majoritaire à deux tours par circonscription.", [
    opt("majority", "Majoritaire à deux tours", "La situation actuelle."),
    opt("mixed", "Dose de proportionnelle (20 %)", "Assemblée plus représentative, majorités plus fragiles.", 0.08,
        [e("opinion.national", 0.006), e("government.parliamentSupport", -0.02)], rule=1, appeal=0.2),
    opt("full", "Proportionnelle intégrale", "Coalitions obligatoires.", 0.14,
        [e("opinion.national", 0.008), e("government.parliamentSupport", -0.05)], rule=1, appeal=0.12),
])
law("institutions", "voting_age", "Droit de vote", "Vote à 18 ans, non obligatoire.", [
    opt("current", "18 ans, facultatif", "La situation actuelle."),
    opt("sixteen", "Vote à 16 ans", "La jeunesse entre dans les urnes.", 0.1, [e(G + "young", 0.015), e(G + "seniors", -0.006)], liberty=1, appeal=-0.15),
    opt("mandatory", "Vote obligatoire (amende)", "Participation record.", 0.12, [e(G + "young", -0.01)], appeal=-0.1),
], constitutional=True)
law("institutions", "senate", "Sénat", "Seconde chambre élue au suffrage indirect.", [
    opt("current", "Maintenu tel quel", "La situation actuelle."),
    opt("merged_cese", "Fusionné avec le CESE", "Moins de parlementaires, réforme de fond.", 0.18,
        [e("budget.oneOff", -0.2, 365), e("opinion.national", 0.006), e(G + "rural", -0.008)], appeal=0.15),
], constitutional=True)
law("institutions", "dual_mandate", "Cumul des mandats", "Un parlementaire ne peut pas être maire.", [
    opt("banned", "Interdit", "La situation actuelle."),
    opt("allowed", "Rétabli pour les maires de petites communes", "Ancrage local ; soupçons de conflits d'intérêts.", 0.05,
        [e(G + "rural", 0.008), e("government.parliamentSupport", 0.02), e("opinion.national", -0.004)], rule=-1, appeal=-0.05),
])

# --- Vérifications -------------------------------------------------------------------------------
TARGETS = re.compile(r"^(budget\.oneOff|economy\.(output|consumerConfidence|businessConfidence|inflation|naturalUnemployment|potentialGrowth)|opinion\.national"
                     r"|opinion\.group\.\w+|quality\.\w+|military\.readiness|government\.parliamentSupport|president\.popularity"
                     r"|alliance\.(EU|NATO)\.[A-Z_]+|memory\.[A-Z]{3}\.[A-Z_]+|demography\.immigration|sector\.\w+)$")
groups = {g["id"] for g in json.load(open(os.path.join(ROOT, "countries", "FRA", "social_groups.json")))["groups"]}
events = set()
for f in os.listdir(os.path.join(ROOT, "events")):
    events |= {x["id"] for x in json.load(open(os.path.join(ROOT, "events", f))).get("events", [])}
sectors = {s["id"] for s in json.load(open(os.path.join(ROOT, "countries", "FRA", "sectors.json")))["sectors"]}
cats = {c[0] for c in CATEGORIES}
ids = set()
for l in LAWS:
    assert l["category"] in cats and l["id"] not in ids, l["id"]
    ids.add(l["id"])
    for o in l["options"]:
        for fx in o.get("effects", []) + o.get("longTerm", []):
            assert TARGETS.match(fx["target"]), (l["id"], fx)
            if fx["target"].startswith(G): assert fx["target"][len(G):] in groups, fx
            if fx["target"].startswith("sector."): assert fx["target"][7:] in sectors, fx
        for ev in o.get("events", {}):
            assert ev in events, (l["id"], ev)

out = {"_doc": "Lois de société, justice, travail, libertés et Constitution. Généré par tools/datagen/laws_fr.py.",
       "baseline": {"liberty": 78, "press": 72, "rule": 82},
       "referendumDays": 30,
       "categories": [{"id": i, "label": l, "icon": ic, "description": d} for i, l, ic, d in CATEGORIES],
       "laws": LAWS}
json.dump(out, open(os.path.join(ROOT, "countries", "FRA", "laws.json"), "w", encoding="utf-8"), ensure_ascii=False, indent=1)
print(f"{len(LAWS)} lois, {sum(len(l['options']) for l in LAWS)} options")
