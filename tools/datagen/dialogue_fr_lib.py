"""Briques communes des générateurs de dialogues."""
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

lexicon = {"words": {
 "important": ["important", "majeur", "essentiel", "déterminant", "structurant", "décisif"],
 "urgent": ["urgent", "pressant", "impérieux", "prioritaire"],
 "problem": ["difficulté", "situation préoccupante", "problème", "situation délicate"],
 "residents": ["habitants", "administrés", "concitoyens", "familles", "usagers"],
 "project": ["projet", "programme", "chantier", "dossier"],
 "quickly": ["rapidement", "sans tarder", "dans les meilleurs délais", "au plus vite", "dès que possible"],
 "concern": ["préoccupation", "inquiétude", "crainte"],
 "support": ["soutien", "appui", "concours", "engagement"],
 "crucial": ["crucial", "vital", "indispensable", "capital"],
 "worsen": ["se dégrade", "s'aggrave", "se détériore", "empire"],
 "decision": ["décision", "arbitrage", "choix"],
 "examine": ["examiner", "étudier", "considérer", "regarder avec attention"],
 "grateful": ["reconnaissant", "sensible à votre attention", "très attentif à votre réponse"],
 "today": ["aujourd'hui", "à ce jour", "en ce moment", "actuellement"],
 "strongly": ["vivement", "fermement", "instamment", "solennellement"],
 "situation": ["situation", "conjoncture", "contexte"],
 "tension": ["tension", "exaspération", "colère", "lassitude"],
 "measure": ["mesure", "initiative", "réponse"],
 "partner": ["partenaire", "ami", "allié"],
 "cooperation": ["coopération", "collaboration", "partenariat"]}}

CLOSINGS_FORMAL = [
 V("Je vous prie d'agréer, {honorific}, l'expression de ma très haute considération."),
 V("Je vous prie de croire, {honorific}, à l'assurance de mon profond respect."),
 V("Veuillez agréer, {honorific}, l'expression de mes sentiments les plus respectueux."),
 V("Dans l'attente de votre [[decision]], je vous prie d'agréer, {honorific}, mes salutations les plus respectueuses."),
 V("Je reste à votre disposition et vous prie d'agréer, {honorific}, l'assurance de ma haute considération."),
 V("Avec mes respectueuses salutations.", when=["relation:good"]),
 V("Je compte sur vous, comme toujours.", when=["relation:good", "history:cooperated"]),
 V("Je sais pouvoir compter sur votre sens de l'intérêt général.", when=["trait:warm"]),
 V("Je ne doute pas que vous saurez prendre la mesure de l'enjeu.", when=["trait:aggressive"]),
 V("J'attends votre réponse avec une certaine impatience, je ne vous le cache pas.", when=["relation:bad"]),
]
SIGN = [V("{sender}\n{senderTitle}"), V("{sender}, {senderTitle}"), V("— {sender}")]

def letter(id, subjects, context, problem, request, extra=(), history=None, closing=None):
    sections = [sec("intro", INTROS)]
    sections.append(sec("history", history or HISTORY, optional=True, chance=0.85))
    sections += [sec("context", context), sec("problem", problem)]
    for s in extra: sections.append(s)
    sections += [sec("request", request), sec("closing", closing or CLOSINGS_FORMAL), sec("signature", SIGN)]
    return {"id": id, "subject": subjects, "sections": sections}

INTROS = [
 V("{honorific},"), V("{honorific}, permettez-moi de vous écrire directement."),
 V("{honorific}, je me permets de vous saisir personnellement."),
 V("{honorific}, je vous adresse ce courrier au nom de nos [[residents]]."),
 V("{honorific}, je sais votre agenda chargé et serai bref.", when=["trait:pragmatic"]),
 V("{honorific}, je n'ai pas pour habitude de prendre la plume pour rien.", when=["trait:proud"]),
 V("{honorific}, cher ami,", when=["relation:good", "trait:warm"]),
 V("{honorific}, je reviens vers vous comme convenu.", when=["followup:reask"]),
 V("{honorific}, je me vois contraint de vous solliciter à nouveau.", when=["followup:reask", "sender:male"]),
 V("{honorific}, je me vois contrainte de vous solliciter à nouveau.", when=["followup:reask", "sender:female"]),
 V("{honorific}, en cette période estivale, je me permets de vous écrire.", when=["season:summer"]),
 V("{honorific}, alors que l'hiver approche, je souhaite attirer votre attention.", when=["season:autumn"]),
]
HISTORY = [
 V("Après vos précédents refus, je mesure combien ma démarche peut sembler insistante.", when=["history:refused"]),
 V("Malgré les refus répétés opposés à nos demandes, je veux croire que le dialogue reste possible.", when=["history:refused_many"]),
 V("Je n'ai pas oublié votre réponse négative de l'an passé, mais la situation a changé.", when=["history:refused"]),
 V("À la suite de notre coopération récente, je me tourne de nouveau vers vous avec confiance.", when=["history:cooperated"]),
 V("Votre soutien lors de nos derniers échanges a été apprécié de tous, et je vous en remercie encore.", when=["history:cooperated"]),
 V("Compte tenu de l'aide que l'État nous a déjà accordée à plusieurs reprises, je mesure la portée de ma demande.", when=["history:cooperated_many"]),
 V("Vous m'aviez demandé de patienter ; nous avons patienté.", when=["history:postponed"]),
 V("Le report de votre décision n'a malheureusement pas fait disparaître le problème.", when=["history:postponed"]),
 V("C'est la première fois que je m'adresse directement à vous depuis votre élection.", when=["history:none"]),
 V("Nous ne nous connaissons pas encore, mais je crois en la qualité de notre future relation.", when=["history:none", "trait:warm"]),
]

