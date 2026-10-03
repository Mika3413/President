"""Briques communes des générateurs de dialogues.

Syntaxe des textes :
  {variable}        remplacée par le moteur (ville, montant, expéditeur...)
  [[mot]]           synonyme tiré du lexique
  {{a|b|c}}         alternative tirée au sort dans la phrase
Étiquettes de contexte utiles dans when/unless :
  voice:formal|direct|warm|technical|lyrical|blunt   style propre à chaque personnage
  relation:good|neutral|bad, history:none|refused|cooperated|postponed|refused_many|cooperated_many
  history:topic (avec {lastTopic} et {lastDate}), history:recent|old
  news:recent (avec {recentNews}), world:war, economy:good|bad, president:popular|unpopular
  season:winter|spring|summer|autumn, sender:male|female, president:male|female
Le moteur ne réemploie jamais une phrase déjà écrite par le même personnage, ni une phrase lue
dans les 40 derniers messages : plus les listes sont longues, plus les courriers sont variés.
"""


def V(text, when=(), unless=(), weight=1.0):
    d = {"text": text}
    if when: d["when"] = list(when)
    if unless: d["unless"] = list(unless)
    if weight != 1.0: d["weight"] = weight
    return d


def sec(id, variants, optional=False, chance=1.0):
    d = {"id": id, "variants": variants}
    if optional: d["optional"] = True
    if chance != 1.0: d["chance"] = chance
    return d


def voiced(by_voice, generic=(), extra_when=()):
    """Variantes par style d'écriture, plus des variantes valables pour tous.
    extra_when restreint l'ensemble (ex. sender:elected pour les seuls élus locaux)."""
    out = []
    for g in generic:
        g = dict(g)
        if extra_when: g["when"] = list(g.get("when", [])) + list(extra_when)
        out.append(g)
    for voice, texts in by_voice.items():
        for t in texts:
            out.append(t if isinstance(t, dict) else V(t, when=["voice:" + voice] + list(extra_when)))
    return out


lexicon = {"words": {
 "important": ["important", "majeur", "essentiel", "déterminant", "structurant", "décisif", "de premier plan"],
 "urgent": ["urgent", "pressant", "impérieux", "prioritaire"],
 "problem": ["difficulté", "situation préoccupante", "problème", "situation délicate", "impasse"],
 "residents": ["habitants", "administrés", "concitoyens", "familles", "usagers", "riverains"],
 "project": ["projet", "programme", "chantier", "dossier", "plan"],
 "quickly": ["rapidement", "sans tarder", "dans les meilleurs délais", "au plus vite", "dès que possible", "à brève échéance"],
 "concern": ["préoccupation", "inquiétude", "crainte", "appréhension"],
 "support": ["soutien", "appui", "concours", "engagement", "aide"],
 "crucial": ["crucial", "vital", "indispensable", "capital", "primordial"],
 "worsen": ["se dégrade", "s'aggrave", "se détériore", "empire", "se tend"],
 "decision": ["décision", "arbitrage", "choix", "réponse"],
 "examine": ["examiner", "étudier", "considérer", "regarder avec attention", "instruire"],
 "grateful": ["reconnaissant", "sensible à votre attention", "très attentif à votre réponse", "obligé"],
 "today": ["aujourd'hui", "à ce jour", "en ce moment", "actuellement", "désormais"],
 "strongly": ["vivement", "fermement", "instamment", "solennellement", "avec insistance"],
 "situation": ["situation", "conjoncture", "contexte", "état des lieux"],
 "tension": ["tension", "exaspération", "colère", "lassitude", "inquiétude"],
 "measure": ["mesure", "initiative", "réponse", "geste"],
 "partner": ["partenaire", "ami", "allié"],
 "cooperation": ["coopération", "collaboration", "partenariat", "relation de travail"],
 "territory": ["territoire", "bassin de vie", "département", "secteur"],
 "people": ["la population", "nos concitoyens", "les habitants", "les familles", "les élus locaux"],
 "soon": ["prochainement", "dans les semaines à venir", "très vite", "bientôt"],
 "honestly": ["franchement", "sincèrement", "en toute honnêteté", "sans détour"],
 "deeply": ["profondément", "sincèrement", "vivement", "réellement"],
 "trust": ["confiance", "loyauté", "bonne foi"],
 "hope": ["espère", "souhaite", "forme le vœu", "veux croire"],
}}

# --- Ouvertures ---------------------------------------------------------------------------
ELECTED_INTROS = voiced({
 "formal": ["{honorific},", "{honorific}, j'ai l'honneur de {{porter à votre connaissance|soumettre à votre haute attention}} la situation suivante.",
            "{honorific}, permettez-moi de {{vous saisir|solliciter votre attention}} sur un dossier {{qui engage l'avenir de notre territoire|d'une importance particulière}}.",
            "{honorific}, je vous prie de bien vouloir excuser cette démarche directe, que les circonstances imposent."],
 "direct": ["{honorific}, je vais à l'essentiel.", "{honorific}, un point {{précis|simple}}, et {{important|urgent}}.",
            "{honorific}, je vous écris pour une raison {{concrète|claire}}.", "{honorific}, quelques lignes, car le temps presse."],
 "warm": ["{honorific}, j'espère que ce courrier vous trouve {{en bonne forme|en bonne santé}}.",
          "{honorific}, c'est avec {{plaisir|une certaine émotion}} que je prends la plume.",
          "{honorific}, je vous écris le cœur un peu lourd, mais plein d'espoir.", "{honorific}, je pense souvent à notre dernière rencontre en vous écrivant."],
 "technical": ["{honorific}, vous trouverez ci-dessous une note de situation.", "{honorific}, objet : demande d'intervention de l'État.",
               "{honorific}, je vous transmets les éléments {{chiffrés|techniques}} d'un dossier en souffrance.",
               "{honorific}, je me permets de vous adresser une synthèse du dossier."],
 "lyrical": ["{honorific}, il est des moments où un territoire entier retient son souffle.",
             "{honorific}, je vous écris au nom d'une terre qui ne demande qu'à vivre.",
             "{honorific}, nos {{clochers|rues|places}} ont vu passer bien des épreuves ; celle-ci n'est pas la moindre.",
             "{honorific}, l'histoire de notre territoire s'écrit aujourd'hui, et elle dépend en partie de vous."],
 "blunt": ["{honorific}, je ne vais pas faire semblant : la situation est mauvaise.", "{honorific}, je vous écris parce que personne ne nous répond.",
           "{honorific}, assez de promesses.", "{honorific}, il faut que vous sachiez ce qui se passe ici."],
}, generic=[
 V("{honorific},"), V("{honorific}, je me permets de vous {{saisir|solliciter}} {{personnellement|directement}}."),
 V("{honorific}, je vous adresse ce courrier au nom de nos [[residents]]."),
 V("{honorific}, cher ami,", when=["relation:good", "voice:warm", "president:male"]),
 V("{honorific}, chère amie,", when=["relation:good", "voice:warm", "president:female"]),
 V("{honorific}, je reviens vers vous comme convenu.", when=["followup:reask"]),
 V("{honorific}, je me vois {{contraint|obligé}} de vous solliciter à nouveau.", when=["followup:reask", "sender:male"]),
 V("{honorific}, je me vois {{contrainte|obligée}} de vous solliciter à nouveau.", when=["followup:reask", "sender:female"]),
 V("{honorific}, en cette période estivale, je me permets de vous écrire.", when=["season:summer"]),
 V("{honorific}, alors que l'hiver approche, je souhaite attirer votre attention.", when=["season:autumn"]),
 V("{honorific}, au cœur de cet hiver, je dois vous alerter.", when=["season:winter"]),
 V("{honorific}, avec le retour du printemps, je reviens vers l'État.", when=["season:spring"]),
], extra_when=["sender:elected"])

# Ministres, préfets, Premier ministre : notes internes, ton de l'administration.
OFFICIAL_INTROS = voiced({
 "formal": ["{honorific}, j'ai l'honneur de vous rendre compte de la situation suivante.",
            "{honorific}, je me permets d'appeler votre haute attention sur un dossier sensible."],
 "direct": ["{honorific}, un dossier requiert votre arbitrage.", "{honorific}, en bref :"],
 "warm": ["{honorific}, je sais pouvoir compter sur votre écoute, comme toujours.", "{honorific}, je vous écris en toute confiance."],
 "technical": ["Note à l'attention de {honorific}.", "{honorific}, vous trouverez ci-après une note de situation et des options.",
               "Objet : demande d'arbitrage présidentiel."],
 "lyrical": ["{honorific}, l'État est attendu, et il doit être à la hauteur.", "{honorific}, ce que nous déciderons dans les jours qui viennent marquera les esprits."],
 "blunt": ["{honorific}, je préfère vous parler franchement : la situation est sérieuse.", "{honorific}, pas de détour : il faut trancher."],
}, generic=[V("{honorific},"), V("{honorific}, je vous adresse cette note {{en urgence|sans attendre}}."),
            V("{honorific}, comme convenu, je reviens vers vous.", when=["followup:reask"])], extra_when=["sender:official"])

# Expéditeur inconnu ou sans fonction précise.
NEUTRAL_INTROS = [V("{honorific},", unless=["sender:elected", "sender:official"]),
                  V("{honorific}, permettez-moi de vous écrire.", unless=["sender:elected", "sender:official"])]

INTROS = ELECTED_INTROS + OFFICIAL_INTROS + NEUTRAL_INTROS

# --- Rappel de l'historique ------------------------------------------------------------------
HISTORY = [
 # Ministres et préfets : rappel des notes précédentes, ton administratif.
 V("Comme lors de notre dernier point, en {lastDate}, je me permets de vous solliciter directement.", when=["sender:official", "history:topic"]),
 V("Vous aviez retenu mes recommandations sur {lastTopic} ; je vous en remercie.", when=["sender:official", "history:topic", "history:cooperated"]),
 V("Vous n'aviez pas suivi mon avis sur {lastTopic} ; je respecte votre arbitrage, mais je vous dois la vérité sur celui-ci.", when=["sender:official", "history:topic", "history:refused"]),
 V("C'est la première note que je vous adresse sur un sujet de cette gravité.", when=["sender:official", "history:none"]),
 V("Je vous adresse cette note après consultation de mes services.", when=["sender:official"]),
 V("Les services de l'État sont mobilisés depuis plusieurs heures.", when=["sender:official", "urgency:high"]),

 V("Après vos précédents refus, je mesure combien ma démarche peut sembler insistante.", when=["sender:elected", "history:refused"]),
 V("Malgré les refus répétés opposés à nos demandes, je veux croire que le dialogue reste possible.", when=["sender:elected", "history:refused_many"]),
 V("Je n'ai pas oublié votre réponse négative, mais la situation a changé.", when=["sender:elected", "history:refused"]),
 V("Lors de notre dernier échange, en {lastDate}, au sujet de {lastTopic}, l'État avait dit non. Je reviens avec des arguments nouveaux.", when=["sender:elected", "history:refused", "history:topic"]),
 V("Vous aviez refusé de nous suivre sur {lastTopic}. Je ne vous en tiens pas rigueur, mais nos [[residents]], eux, s'en souviennent.", when=["sender:elected", "history:refused", "history:topic", "voice:blunt"]),
 V("Je garde un souvenir précis de {lastTopic} : votre refus a été durement ressenti ici.", when=["sender:elected", "history:refused", "history:topic", "relation:bad"]),
 V("À la suite de notre coopération récente, je me tourne de nouveau vers vous avec confiance.", when=["sender:elected", "history:cooperated"]),
 V("Votre soutien lors de nos derniers échanges a été apprécié de tous, et je vous en remercie encore.", when=["sender:elected", "history:cooperated"]),
 V("Votre décision sur {lastTopic}, en {lastDate}, a fait beaucoup de bien ici ; on m'en parle encore.", when=["sender:elected", "history:cooperated", "history:topic"]),
 V("Grâce à vous, {lastTopic} a trouvé une issue. C'est fort de ce précédent que je vous écris.", when=["sender:elected", "history:cooperated", "history:topic"]),
 V("On se souvient encore, chez nous, de votre geste au sujet de {lastTopic}.", when=["sender:elected", "history:cooperated", "history:topic", "voice:warm"]),
 V("Compte tenu de l'aide que l'État nous a déjà accordée à plusieurs reprises, je mesure la portée de ma demande.", when=["sender:elected", "history:cooperated_many"]),
 V("Vous nous avez souvent aidés ; je n'abuserais pas de votre bienveillance si la situation ne l'exigeait pas.", when=["sender:elected", "history:cooperated_many", "voice:formal"]),
 V("Vous m'aviez demandé de patienter ; nous avons patienté.", when=["sender:elected", "history:postponed"]),
 V("Le report de votre décision sur {lastTopic} n'a malheureusement pas fait disparaître le problème.", when=["sender:elected", "history:postponed", "history:topic"]),
 V("Le report de votre décision n'a malheureusement pas fait disparaître le problème.", when=["sender:elected", "history:postponed"]),
 V("Depuis notre dernier contact, en {lastDate}, les choses ont {{beaucoup|sensiblement}} évolué.", when=["sender:elected", "history:topic", "history:old"]),
 V("Il y a peu, nous évoquions {lastTopic} ; voici un autre sujet, tout aussi pressant.", when=["sender:elected", "history:topic", "history:recent"]),
 V("C'est la première fois que je m'adresse directement à vous depuis votre élection.", when=["sender:elected", "history:none"]),
 V("Nous ne nous connaissons pas encore, mais je crois en la qualité de notre future relation.", when=["sender:elected", "history:none", "voice:warm"]),
 V("Je ne vous ai jamais rien demandé jusqu'ici ; c'est dire si la situation est sérieuse.", when=["sender:elected", "history:none", "voice:blunt"]),
 V("Permettez à un élu que vous ne connaissez pas encore de vous exposer un dossier précis.", when=["sender:elected", "history:none", "voice:formal", "sender:male"]),
 V("Permettez à une élue que vous ne connaissez pas encore de vous exposer un dossier précis.", when=["sender:elected", "history:none", "voice:formal", "sender:female"]),
]

# --- Allusions à l'actualité ----------------------------------------------------------------
NEWS = [
 V("Je sais que l'actualité nationale est chargée — {recentNews} — mais je vous demande de ne pas oublier les territoires.", when=["news:recent", "sender:elected"]),
 V("Ce dossier s'ajoute à une actualité déjà lourde ({recentNews}).", when=["news:recent", "sender:official"]),
 V("Dans le contexte actuel — {recentNews} — ce sujet risque d'être éclipsé ; il ne doit pas l'être.", when=["news:recent", "sender:official"]),
 V("Alors que tout le monde ne parle que d'une chose ({recentNews}), notre dossier risque de passer inaperçu. Il ne le mérite pas.", when=["news:recent", "voice:blunt", "sender:elected"]),
 V("Dans le contexte actuel ({recentNews}), je comprends que vos priorités soient nombreuses.", when=["news:recent", "voice:formal"]),
 V("Avec ce qui fait la une — {recentNews} — nos [[residents]] ont le sentiment que leurs difficultés passent au second plan.", when=["news:recent", "sender:elected"]),
 V("Je mesure que l'actualité vous sollicite ({recentNews}) ; nos difficultés, elles, n'ont pas pris de vacances.", when=["news:recent", "sender:elected", "voice:direct"]),
 V("Les chaînes d'information tournent en boucle sur {recentNews}. Chez nous, on parle d'autre chose : de fins de mois et d'avenir.", when=["news:recent", "sender:elected", "voice:blunt"]),
 V("Je ne voudrais pas que {recentNews} fasse oublier ce qui se joue dans nos communes.", when=["news:recent", "sender:elected", "voice:warm"]),
 V("Au moment où l'attention nationale se porte ailleurs ({recentNews}), je me permets de rappeler que la France, c'est aussi nos territoires.", when=["news:recent", "sender:elected", "voice:formal"]),
 V("Malgré {recentNews}, nos services ont continué à travailler sur ce dossier ; en voici l'état.", when=["news:recent", "sender:elected", "voice:technical"]),
 V("Pendant que le pays a les yeux tournés vers {recentNews}, une autre histoire s'écrit ici, plus silencieuse.", when=["news:recent", "sender:elected", "voice:lyrical"]),
 V("Je sais que {recentNews} mobilise vos équipes ; ce dossier mérite néanmoins votre attention.", when=["news:recent", "sender:official", "voice:formal"]),
 V("Malgré {recentNews}, je dois vous saisir d'un autre sujet.", when=["news:recent", "sender:official", "voice:direct"]),
 V("Je n'ignore pas le contexte ({recentNews}) ; je serai donc concis.", when=["news:recent", "sender:official", "voice:technical"]),
 V("Le pays est en guerre, je le sais, et je pèse mes mots ; mais la vie continue ici, et ses besoins aussi.", when=["world:war", "sender:elected"]),
 V("Le conflit en cours mobilise nos moyens ; ce dossier exige pourtant une réponse rapide.", when=["world:war", "sender:official"]),
 V("La conjoncture économique est difficile ; elle frappe notre {{territoire|ville|département}} plus durement qu'ailleurs.", when=["economy:bad", "sender:elected"]),
 V("La conjoncture économique réduit nos marges de manœuvre, j'en ai conscience.", when=["economy:bad", "sender:official"]),
 V("L'économie repart, c'est une bonne nouvelle ; encore faut-il que tout le monde en profite.", when=["economy:good"]),
]

# --- Formules de fin --------------------------------------------------------------------------
CLOSINGS_FORMAL = voiced({
 "formal": ["Je vous prie d'agréer, {honorific}, l'expression de ma très haute considération.",
            "Je vous prie de croire, {honorific}, à l'assurance de mon profond respect.",
            "Veuillez agréer, {honorific}, l'expression de mes sentiments les plus respectueux.",
            "Dans l'attente de votre [[decision]], je vous prie d'agréer, {honorific}, mes salutations les plus respectueuses.",
            "Je reste à votre entière disposition et vous prie d'agréer, {honorific}, l'assurance de ma haute considération."],
 "direct": ["Merci d'avance pour votre réponse.", "J'attends votre réponse. Merci.", "Je reste joignable à tout moment.", "Je compte sur une réponse rapide."],
 "warm": ["Avec toute ma confiance et mon amitié républicaine.", "Je vous remercie du fond du cœur pour l'attention que vous y porterez.",
          "Avec mes pensées les plus cordiales.", "Bien fidèlement."],
 "technical": ["Je tiens l'ensemble des pièces du dossier à la disposition de vos services.",
               "Mes services se tiennent prêts à travailler avec les vôtres. Avec mes salutations respectueuses.",
               "Je reste à disposition pour tout complément d'information. Respectueusement."],
 "lyrical": [V("Notre territoire saura s'en souvenir.", when=["voice:lyrical", "sender:elected"]),
             V("Je vous prie de croire que nos [[residents]] attendent beaucoup de vous, et moi avec eux.", when=["voice:lyrical", "sender:elected"]),
             "Avec l'espoir que ce courrier ne restera pas lettre morte.",
             V("Que ce courrier soit le début d'une belle histoire entre l'État et nous.", when=["voice:lyrical", "sender:elected"]),
             V("L'histoire jugera notre réponse ; je sais que vous en avez conscience.", when=["voice:lyrical", "sender:official"]),
             V("Je sais que vous saurez trouver la voie juste.", when=["voice:lyrical", "sender:official"])],
 "blunt": ["Je n'en dirai pas plus.", "La balle est dans votre camp.",
           V("Nous verrons ce que vaut la parole de l'État.", when=["voice:blunt", "sender:elected"]), "J'attends des actes.",
           V("Il faut décider vite : chaque jour perdu se paiera.", when=["voice:blunt", "sender:official"])],
}, generic=[
 V("Avec mes respectueuses salutations.", when=["relation:good"]),
 V("Je compte sur vous, comme toujours.", when=["relation:good", "history:cooperated"]),
 V("Je sais pouvoir compter sur votre sens de l'intérêt général."),
 V("J'attends votre réponse avec une certaine impatience, je ne vous le cache pas.", when=["relation:bad"]),
 V("Avec mes salutations républicaines."),
])
SIGN = [V("{sender}\n{senderTitle}"), V("{sender}, {senderTitle}"), V("— {sender}"), V("{sender}\n{senderTitle}", when=["voice:formal"]),
        V("{senderLast}", when=["voice:direct"]), V("{sender}\n{senderTitle}", when=["voice:lyrical"])]


# --- Enrichissement automatique ------------------------------------------------------------------
# Chaque phrase propre à un courrier est déclinée avec des tournures d'introduction, selon le style
# de l'auteur : la même information n'est jamais formulée deux fois de la même façon.
LEADINS = {
 "context": {
  None: ["Les faits sont les suivants : ", "Pour être précis, ", "Je vous le résume : ", "Voici la situation : "],
  "formal": ["!J'ai le regret de porter à votre connaissance que ", "Il m'appartient de vous informer que ", "Je dois vous faire savoir que "],
  "direct": ["En clair : ", "Concrètement, ", "Les faits : "],
  "warm": ["!Je dois vous confier, avec tristesse, que ", "!C'est avec inquiétude que je vous écris : ", "Je vous le dis avec le cœur : "],
  "technical": ["Selon les dernières données disponibles, ", "D'après les remontées de terrain, ", "Les relevés de nos services montrent que "],
  "lyrical": ["Imaginez la scène : ", "C'est une réalité que l'on peine à croire : ", "Voilà où nous en sommes : "],
  "blunt": ["Soyons clairs : ", "!Je ne vais pas tourner autour du pot : ", "!Inutile de se voiler la face : "],
 },
 "problem": {
  None: ["À cela s'ajoute que ", "!Plus préoccupant encore, ", "Il faut aussi savoir que ", "Surtout, "],
  "formal": ["Je me permets d'ajouter que ", "Il convient de souligner que "],
  "direct": ["Et ce n'est pas tout : ", "Autre point : "],
  "warm": ["!Ce qui m'inquiète le plus, c'est que ", "Ce qui me touche particulièrement, c'est que "],
  "technical": ["Les projections indiquent que ", "Nos analyses montrent que "],
  "lyrical": ["Et pendant ce temps, ", "Derrière les chiffres, il y a ceci : "],
  "blunt": ["!Le pire, c'est que ", "Et franchement, "],
 },
 "request": {
  None: ["C'est pourquoi ", "En conséquence, ", "Dans ces conditions, "],
  "formal": ["J'ai donc l'honneur de vous indiquer que ", "Aussi, "],
  "direct": ["Donc : ", "Ma demande est simple : "],
  "warm": ["C'est avec confiance que je vous l'écris : ", "Je me tourne vers vous, car "],
  "technical": ["Au vu de ces éléments, ", "Sur la base de cette analyse, "],
  "lyrical": ["Alors, aujourd'hui, ", "Il est temps d'agir : "],
  "blunt": ["Alors voilà : ", "Je vais être direct : "],
 },
}
# Premiers mots qui peuvent passer en minuscule après une tournure d'introduction.
LOWERABLE = set("""Le La Les Un Une Des Plusieurs Nos Notre Nous Je Il Elle Ils Elles Ce Cette Ces Chaque Depuis Sans Si Après
Avec Dans Pour Faute Toute Tous Toutes Aucun Aucune Trois Deux Quatre Cinq Six Sept Huit Neuf Dix Une Cet Leur Leurs Mon Ma Mes
Votre Vos Chaque Certains Certaines Malgré Selon Grâce Faute Entre Sur Sous Avant Pendant Lors Face Plus Moins On Personne Rien
Tout Quelques Près Huit Vingt Trente Quarante Cinquante Cent Mille Ni Pas Seule Seul Désormais Hier Ce Cela Ça""".split())


def _lower_first(text):
    first = text.split(" ", 1)[0]
    word = first.split("'", 1)[0] + ("'" if "'" in first else "")
    if first.startswith("L'") or first.startswith("D'") or first.startswith("J'") or first.startswith("C'") or first.startswith("S'") or first.startswith("N'") or first.startswith("Qu'"):
        return text[0].lower() + text[1:]
    if first in LOWERABLE or word.rstrip("'") in LOWERABLE:
        return text[0].lower() + text[1:]
    return None


def _elide(leadin, rest):
    # « que il » -> « qu'il », « que elle » -> « qu'elle », « que un » -> « qu'un »...
    if leadin.endswith("que ") and rest[:1].lower() in "aeiouyéèêh":
        return leadin[:-2] + "'" + rest
    return leadin + rest


FIRST_PERSON = ("Je ", "J'", "Nous ", "Mon ", "Ma ", "Mes ", "Notre ", "Nos ")


def enrich(section_id, variants, positive=False):
    """positive : courrier porteur d'une bonne nouvelle, sans tournure alarmiste."""
    table = LEADINS.get(section_id)
    if not table:
        return variants
    out = list(variants)
    for v in variants:
        if section_id != "request" and v["text"].startswith(FIRST_PERSON):
            continue
        lowered = _lower_first(v["text"])
        if lowered is None or "\n" in v["text"]:
            continue
        for voice, leadins in table.items():
            for lead in leadins:
                if lead.startswith("!"):
                    if positive:
                        continue
                    lead = lead[1:]
                nv = dict(v)
                nv["text"] = _elide(lead, lowered)
                when = list(v.get("when", []))
                if voice:
                    if any(w.startswith("voice:") and w != "voice:" + voice for w in when):
                        continue
                    if "voice:" + voice not in when: when.append("voice:" + voice)
                if when: nv["when"] = when
                out.append(nv)
    return out


def letter(id, subjects, context, problem, request, extra=(), history=None, closing=None, news=True, positive=False):
    context = enrich("context", context, positive)
    problem = enrich("problem", problem, positive)
    request = enrich("request", request, positive)
    sections = [sec("intro", INTROS)]
    sections.append(sec("history", history or HISTORY, optional=True, chance=0.85))
    if news:
        sections.append(sec("news", NEWS, optional=True, chance=0.35))
    sections += [sec("context", context), sec("problem", problem)]
    for s in extra: sections.append(s)
    sections += [sec("request", request), sec("closing", closing or CLOSINGS_FORMAL), sec("signature", SIGN)]
    return {"id": id, "subject": subjects, "sections": sections}


# --- Demandes rédigées à partir des options réelles de l'événement ----------------------------------
NUMBERS = {2: "deux", 3: "trois", 4: "quatre", 5: "cinq"}


def _option_phrase(label):
    return label[0].lower() + label[1:] if label[:1].isupper() and not label[:2].isupper() else label


def option_requests(options):
    labels = [_option_phrase(o["label"]) for o in options]
    if len(labels) < 2:
        return []
    n = NUMBERS.get(len(labels), str(len(labels)))
    listing = ", ".join(labels[:-1]) + " ou " + labels[-1]
    semis = " ; ".join(labels[:-1]) + " ; ou " + labels[-1]
    return voiced({
        "formal": [f"Je soumets à votre arbitrage les options suivantes : {listing}.", f"{n.capitalize()} options me paraissent envisageables : {listing}."],
        "direct": [f"{n.capitalize()} possibilités : {listing}. À vous de trancher.", f"Options : {listing}."],
        "warm": [f"Je vous fais confiance pour choisir entre ces {n} voies : {listing}.", f"Quelle que soit votre décision — {listing} —, je serai à vos côtés."],
        "technical": [f"Options étudiées par mes services : {semis}.", f"Scénarios chiffrés : {semis}. Le détail est à votre disposition."],
        "lyrical": [f"{n.capitalize()} chemins s'ouvrent devant nous : {listing}.", f"L'histoire retiendra notre choix : {listing}."],
        "blunt": [f"Il faut choisir : {listing}.", f"{n.capitalize()} options, aucune n'est indolore : {listing}."],
    }, generic=[V(f"Plusieurs options s'offrent à vous : {listing}."), V(f"Les choix possibles sont les suivants : {listing}. Chacun a son prix.")])


def with_option_requests(template, options):
    """Ajoute à la section « request » d'un modèle des formulations tirées des options."""
    for section in template["sections"]:
        if section["id"] == "request":
            section["variants"] += option_requests(options)
    return template
