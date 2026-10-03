"""Dialogues avec les dirigeants étrangers et comptes rendus d'entretiens (diplomacy.json, conversations.json).
Importé par dialogue_fr.py. Chaque dirigeant a son style (voice:*) et se souvient des échanges passés."""
from dialogue_fr_lib import V, sec, voiced, SIGN

# --- Ouvertures et formules des dirigeants -------------------------------------------------------
D_INTRO = voiced({
 "formal": ["{honorific}, au nom du gouvernement {foreignOf}, je vous adresse mes salutations les plus distinguées.",
            "{honorific}, j'ai l'honneur de m'adresser à vous au nom {foreignOf}.",
            "{honorific}, permettez-moi de vous écrire en ma qualité de {senderTitle}."],
 "direct": ["{honorific}, allons droit au but.", "{honorific}, je serai bref.", "{honorific}, une proposition concrète."],
 "warm": [V("{honorific}, cher ami,", when=["voice:warm", "president:male"]), V("{honorific}, chère amie,", when=["voice:warm", "president:female"]), "{honorific}, j'espère que ce message vous trouve en bonne santé.",
          "{honorific}, c'est toujours un plaisir de vous écrire.", "{honorific}, je garde un excellent souvenir de nos échanges."],
 "technical": ["{honorific}, mes services ont préparé la note qui suit.", "{honorific}, vous trouverez ci-dessous les éléments de notre position.",
               "{honorific}, à la suite des travaux de nos administrations respectives :"],
 "lyrical": ["{honorific}, nos deux peuples partagent une longue histoire ; écrivons-en une nouvelle page.",
             "{honorific}, il est des moments où les nations doivent se parler franchement : nous y sommes.",
             "{honorific}, entre {foreignThe} et la France, il n'y a parfois qu'un pas ; faisons-le ensemble."],
 "blunt": ["{honorific}, je n'ai pas l'habitude des détours.", "{honorific}, parlons clairement.", "{honorific}, voici notre position, sans fard."],
}, generic=[V("{honorific},"), V("{honorific}, cher collègue,", when=["relation:good", "president:male"]), V("{honorific}, chère collègue,", when=["relation:good", "president:female"]),
            V("{honorific}, au nom du gouvernement {foreignOf},")])

D_MEMORY = [
 V("À la suite de notre coopération récente, je souhaite aller plus loin.", when=["history:cooperated"]),
 V("Notre accord sur {lastTopic} fonctionne bien ; c'est une base solide pour la suite.", when=["history:cooperated", "history:topic"]),
 V("Vous aviez accepté {lastTopic} en {lastDate} ; mon gouvernement ne l'a pas oublié.", when=["history:cooperated", "history:topic"]),
 V("Nos échanges passés n'ont pas toujours abouti, mais je crois en une nouvelle étape.", when=["history:refused"]),
 V("Vous aviez décliné {lastTopic}. Je reviens vers vous avec une offre {{différente|mieux équilibrée}}.", when=["history:refused", "history:topic"]),
 V("Je ne vous cache pas que votre refus concernant {lastTopic} a été mal compris chez nous.", when=["history:refused", "history:topic", "voice:blunt"]),
 V("Malgré plusieurs refus de votre part, ma porte reste ouverte.", when=["history:refused_many"]),
 V("Nos deux pays entretiennent des liens anciens.", when=["relation:good"]),
 V("L'amitié entre nos peuples n'est pas un vain mot.", when=["relation:good", "voice:lyrical"]),
 V("Nos relations ont connu des moments difficiles ; cette proposition se veut un geste.", when=["relation:bad"]),
 V("Je sais que nos relations sont tendues. Raison de plus pour se parler.", when=["relation:bad", "voice:direct"]),
 V("C'est notre premier échange direct depuis votre prise de fonctions.", when=["history:none"]),
 V("Nous ne nous sommes pas encore entretenus personnellement ; je le regrette et j'espère y remédier.", when=["history:none", "voice:warm"]),
]

D_WORLD = [
 V("Dans le contexte international actuel, la coopération entre nos pays est plus nécessaire que jamais.", when=["world:war"]),
 V("La guerre que traverse votre pays nous préoccupe ; elle rend nos échanges d'autant plus importants.", when=["world:war", "voice:warm"]),
 V("Nous suivons avec attention l'actualité française ({recentNews}).", when=["news:recent"]),
 V("J'ai vu que {recentNews} ; je vous adresse mes pensées.", when=["news:recent", "news:disaster", "voice:warm"]),
 V("La conjoncture économique européenne pèse sur nous tous.", when=["economy:bad"]),
]

D_CLOSE = voiced({
 "formal": ["Je vous prie d'agréer, {honorific}, l'assurance de ma très haute considération.", "Avec ma haute considération.",
            "Veuillez croire, {honorific}, à l'expression de mes sentiments les plus distingués."],
 "direct": ["Dans l'attente de votre réponse.", "À vous lire.", "Merci de me répondre rapidement."],
 "warm": ["Bien amicalement.", "Avec toute mon amitié.", "Au plaisir de vous revoir très bientôt.", "Mes amitiés à toute votre équipe."],
 "technical": ["Nos équipes restent à la disposition des vôtres pour finaliser les détails.", "Mes conseillers prendront contact avec les vôtres."],
 "lyrical": ["Que vive l'amitié entre nos deux nations.", "L'avenir nous regarde."],
 "blunt": ["Je n'attendrai pas indéfiniment.", "Réfléchissez bien.", "C'est à prendre ou à laisser."],
}, generic=[V("Je vous adresse mes salutations distinguées."), V("Bien cordialement.", when=["relation:good"])])

proposal = {"id": "diplomatic_proposal", "subject": [
  V("Proposition du gouvernement {foreignOf}"), V("{foreignCountry} : proposition d'accord"), V("Une offre {foreignOf}"),
  V("{foreignCountry} souhaite conclure un accord"), V("Lettre de {sender} : une proposition"), V("{foreignCountry} vous tend la main", when=["relation:bad"]),
  V("Nouvelle proposition {foreignOf}", when=["history:refused"])], "sections": [
 sec("intro", D_INTRO),
 sec("memory", D_MEMORY, optional=True, chance=0.85),
 sec("world", D_WORLD, optional=True, chance=0.35),
 sec("motive", voiced({
   "formal": ["Mon gouvernement souhaite {motive}, et c'est dans cet esprit que je vous soumets la proposition qui suit.",
              "Afin de {motive}, j'ai l'honneur de vous proposer l'accord suivant."],
   "direct": ["Nous voulons {motive}.", "Objectif : {motive}."],
   "warm": ["Nous aimerions {motive}, et nous pensons sincèrement que la France est le meilleur partenaire pour cela."],
   "technical": ["Nos analyses montrent qu'il nous faut {motive} ; un accord avec la France y répondrait efficacement."],
   "lyrical": ["Pour {motive}, nos deux pays ont tout à gagner à unir leurs forces."],
   "blunt": ["Nous avons besoin de {motive}. Vous pouvez nous y aider, et vous y gagnerez aussi."],
 }, generic=[V("Mon gouvernement souhaite {motive}."), V("Afin de {motive}, je vous soumets la proposition suivante."),
             V("Nous avons besoin de {motive} et pensons que la France peut y contribuer.")])),
 sec("terms", [V("Termes proposés :\n{clauses}"), V("Voici ce que nous proposons :\n{clauses}"), V("Concrètement :\n{clauses}"),
               V("Le projet d'accord tient en quelques points :\n{clauses}")]),
 sec("benefit", [V("Je suis convaincu que cet accord profitera à nos deux pays.", when=["sender:male"]),
                 V("Je suis convaincue que cet accord profitera à nos deux pays.", when=["sender:female"]),
                 V("Chacun y trouvera son compte, j'en suis certain.", when=["voice:warm", "sender:male"]),
                 V("Chacun y trouvera son compte, j'en suis certaine.", when=["voice:warm", "sender:female"]),
                 V("Les chiffres parlent d'eux-mêmes : l'équilibre est réel.", when=["voice:technical"]),
                 V("Un refus serait une occasion manquée, pour vous comme pour nous.", when=["voice:blunt"])], optional=True, chance=0.6),
 sec("request", [V("Je serais heureux de connaître votre position.", when=["sender:male"]), V("Je serais heureuse de connaître votre position.", when=["sender:female"]),
                 V("Nous attendons votre réponse dans les prochains jours."), V("Nous sommes ouverts à la discussion sur les modalités.", when=["voice:direct"]),
                 V("Je vous serais reconnaissant de ne pas laisser cette offre sans réponse.", when=["voice:lyrical", "sender:male"]),
                 V("Je vous serais reconnaissante de ne pas laisser cette offre sans réponse.", when=["voice:lyrical", "sender:female"]),
                 V("Mes équipes sont prêtes à négocier les détails.", when=["voice:technical"]),
                 V("Votre réponse dira beaucoup de l'état de nos relations.", when=["voice:blunt"]),
                 V("Je me réjouis par avance de votre réponse.", when=["voice:warm"])]),
 sec("closing", D_CLOSE), sec("signature", SIGN)]}

response = {"id": "diplomatic_response", "subject": [
  V("Réponse {foreignOf} : accord", when=["response:accepted"]), V("{foreignCountry} accepte votre proposition", when=["response:accepted"]),
  V("Bonne nouvelle {foreignOf}", when=["response:accepted"]), V("{sender} donne son accord", when=["response:accepted"]),
  V("Réponse {foreignOf} : refus", when=["response:refused"]), V("{foreignCountry} décline votre proposition", when=["response:refused"]),
  V("{sender} ne donne pas suite", when=["response:refused"]), V("Fin de non-recevoir {foreignOf}", when=["response:refused", "relation:bad"]),
  V("Contre-proposition {foreignOf}", when=["response:countered"]), V("{foreignCountry} propose d'amender l'accord", when=["response:countered"]),
  V("{sender} répond par une contre-offre", when=["response:countered"])], "sections": [
 sec("intro", D_INTRO),
 sec("memory", D_MEMORY, optional=True, chance=0.5),
 sec("analysis", voiced({
   "formal": ["Mon gouvernement a étudié avec la plus grande attention votre proposition.", "Votre proposition a fait l'objet d'un examen approfondi au sein de mon gouvernement."],
   "direct": ["Nous avons regardé votre offre.", "J'ai lu votre proposition."],
   "warm": ["Merci pour votre proposition, que nous avons étudiée avec beaucoup d'intérêt."],
   "technical": ["Nos ministères ont analysé votre offre point par point.", "Nos experts ont chiffré chacune des clauses."],
   "lyrical": ["Votre proposition a été lue avec l'attention qu'on doit à un pays ami."],
   "blunt": ["Votre proposition, je vous le dis tout net, nous a surpris."],
 }, generic=[V("Mon gouvernement a étudié attentivement votre proposition."), V("Nous avons pris connaissance de votre offre.")])),
 sec("verdict", [
  V("Je suis heureux de vous annoncer que nous l'acceptons.", when=["response:accepted", "sender:male"]),
  V("Je suis heureuse de vous annoncer que nous l'acceptons.", when=["response:accepted", "sender:female"]),
  V("Elle répond à nos attentes : nous sommes prêts à signer.", when=["response:accepted"]),
  V("C'est une bonne nouvelle pour nos deux pays : nous acceptons.", when=["response:accepted", "relation:good"]),
  V("Accord. Nos équipes peuvent préparer la signature.", when=["response:accepted", "voice:direct"]),
  V("Les conditions sont équilibrées ; nous donnons notre accord.", when=["response:accepted", "voice:technical"]),
  V("Nous acceptons, et je m'en réjouis personnellement.", when=["response:accepted", "voice:warm"]),
  V("Nous ne pouvons malheureusement pas l'accepter en l'état : {reasons}.", when=["response:refused"]),
  V("Je dois vous dire franchement que cette offre ne nous convient pas : {reasons}.", when=["response:refused", "voice:blunt"]),
  V("À regret, nous déclinons : {reasons}.", when=["response:refused"]),
  V("C'est non : {reasons}.", when=["response:refused", "voice:direct"]),
  V("Après mûre réflexion, nous ne pouvons y souscrire, pour les raisons suivantes : {reasons}.", when=["response:refused", "voice:formal"]),
  V("Il m'en coûte de vous décevoir, mais nous ne pouvons pas accepter : {reasons}.", when=["response:refused", "voice:warm"]),
  V("Nous ne pouvons l'accepter telle quelle ({reasons}), mais nous vous soumettons une version amendée.", when=["response:countered"]),
  V("Plutôt qu'un refus, nous préférons vous faire une contre-proposition, car {reasons}.", when=["response:countered"]),
  V("L'idée nous plaît ; les termes, moins ({reasons}). Voici ce que nous proposons.", when=["response:countered", "voice:direct"])]),
 sec("terms", [V("Termes :\n{clauses}", when=["response:accepted"]), V("Pour mémoire, l'accord prévoit :\n{clauses}", when=["response:accepted"]),
               V("Notre contre-proposition :\n{clauses}", when=["response:countered"]), V("Ce que nous pourrions accepter :\n{clauses}", when=["response:countered"])], optional=True),
 sec("future", [V("La porte reste ouverte à de futures discussions.", when=["response:refused"]),
                V("N'y voyez aucune hostilité : nous restons disposés à travailler ensemble sur d'autres bases.", when=["response:refused", "voice:formal"]),
                V("Revenez vers nous avec autre chose, et nous en reparlerons.", when=["response:refused", "voice:direct"]),
                V("Je me réjouis de cette nouvelle étape de notre coopération.", when=["response:accepted"]),
                V("Voilà qui renforce l'amitié entre nos peuples.", when=["response:accepted", "voice:lyrical"]),
                V("Nous espérons que ces ajustements vous conviendront.", when=["response:countered"]),
                V("Nous sommes proches d'un accord ; ne laissons pas passer l'occasion.", when=["response:countered", "voice:warm"])], optional=True),
 sec("closing", D_CLOSE), sec("signature", SIGN)]}

# --- Entretiens à l'initiative du président -------------------------------------------------------
def combo(firsts, seconds, when=(), sep=" "):
    """Produit cartésien de fragments : chaque combinaison devient une variante distincte."""
    return [V(a + sep + b, when=list(when)) for a in firsts for b in seconds]

SPEAKER = "— {senderLast} : "

def openings(by_voice, where):
    out = []
    for voice, (firsts, seconds) in by_voice.items():
        out += combo([SPEAKER + f for f in firsts], seconds, when=["voice:" + voice])
    return out

def narration(mood, subjects, endings, extra=()):
    return combo(subjects, endings, when=["mood:" + mood] + list(extra))

# Le compte rendu alterne vos propos et ceux de votre interlocuteur. Sujets : topic:strengthen,
# reassure, seek_support, concern, warn (étrangers) ; listen, visit, praise, reprimand (élus).
def you(topic, lines):
    return [V("— Vous : " + t, when=["topic:" + topic]) for t in lines]

def them(topic, mood, lines, extra=()):
    return [V("— {senderLast} : " + t, when=["topic:" + topic, "mood:" + mood] + list(extra)) for t in lines]

FOREIGN_YOU = (
 you("strengthen", ["{{Je tenais|J'ai souhaité}} vous appeler pour faire le point sur nos relations, et voir comment les approfondir.",
                    "Nos deux pays ont beaucoup à faire ensemble ; je voulais vous le dire de vive voix.",
                    "Je voulais prendre de vos nouvelles et parler de l'avenir de nos relations.",
                    "Il me semble que nous pourrions travailler plus étroitement, et je voulais vous en parler directement."])
 + you("reassure", ["Je sais que certaines de nos décisions ont pu inquiéter {foreignIn}. Je veux vous rassurer : la France n'a aucune intention hostile.",
                    "Je tenais à dissiper tout malentendu : nous tenons à notre relation.",
                    "Il y a eu des tensions entre nous ; je souhaite sincèrement les apaiser."])
 + you("seek_support", ["La France aura besoin de ses amis dans les mois qui viennent. Puis-je compter sur {foreignThe} ?",
                        "Je voudrais savoir si votre gouvernement serait prêt à soutenir nos positions sur la scène internationale.",
                        "J'ai besoin de votre appui, et je préfère vous le demander directement."])
 + you("concern", ["Je dois vous faire part de ma préoccupation devant certaines décisions de votre gouvernement.",
                   "Je ne vous cache pas que la France est inquiète de l'orientation prise par {foreignThe}.",
                   "Entre partenaires, on se doit la franchise : certaines de vos décisions nous posent problème."])
 + you("warn", ["Je vous le dis solennellement : la France ne restera pas sans réagir si la situation devait se dégrader.",
                "Je veux être très clair : certaines lignes ne doivent pas être franchies.",
                "Je vous mets en garde, amicalement mais fermement : nos intérêts seront défendus."])
)

FOREIGN_THEM = (
 them("strengthen", "warm", ["Je partage entièrement votre sentiment. Nos équipes vont travailler ensemble, je vous le promets.",
                             "Quel plaisir de vous entendre ! {ForeignThe} considère la France comme un partenaire essentiel.",
                             "Vous avez raison, et j'ai envie d'aller plus loin. Parlons-en lors du prochain sommet.",
                             "C'est exactement ce que j'espérais entendre. Comptez sur moi."])
 + them("strengthen", "neutral", ["Je vous remercie de cet appel. Nos relations sont bonnes ; voyons ce que l'avenir nous réserve.",
                                  "C'est une intention louable. Mes ministres étudieront les pistes possibles.",
                                  "Volontiers, mais chaque chose en son temps."])
 + them("strengthen", "cold", ["Je prends note. Mais les paroles ne suffisent pas toujours, vous le savez.",
                               "C'est aimable, mais j'attends de la France des gestes concrets avant de parler d'avenir.",
                               "Nous verrons. Pour l'instant, la prudence s'impose."])
 + them("reassure", "warm", ["Merci, cela me rassure. Considérons cet épisode comme clos.", "Je vous crois, et j'apprécie votre démarche."])
 + them("reassure", "neutral", ["J'entends vos assurances. Le temps dira si elles se confirment.", "Bien. Nous jugerons sur les faits."])
 + them("reassure", "cold", ["Des mots, encore des mots. Mon gouvernement attendra des preuves.", "Je doute que cela suffise."])
 + them("seek_support", "warm", ["Vous pouvez compter sur nous. {ForeignThe} n'oublie pas ses amis.", "Bien sûr. Dites-moi ce dont vous avez besoin, et nous verrons comment vous aider."])
 + them("seek_support", "neutral", ["Je comprends votre demande. Je ne peux rien promettre, mais je ne dis pas non.", "Laissez-moi consulter mon gouvernement."])
 + them("seek_support", "cold", ["Je ne vois pas pourquoi {foreignThe} devrait s'engager pour vous.", "Ce n'est pas le moment. Et franchement, je trouve la demande déplacée."])
 + them("concern", "warm", ["Je comprends votre inquiétude ; nous allons en tenir compte.", "Merci de votre franchise. C'est ainsi que des amis se parlent."])
 + them("concern", "neutral", ["J'entends vos préoccupations, même si je ne les partage pas toutes.", "Nos choix répondent à nos intérêts, mais je note votre position."])
 + them("concern", "cold", ["Je n'ai pas de leçons à recevoir de la France.", "Ce sont nos affaires, et je vous prie de les respecter."])
 + them("warn", "warm", ["Message reçu. Personne n'a intérêt à l'escalade.", "Je comprends. Nous allons éviter toute provocation."])
 + them("warn", "neutral", ["Je prends note de votre fermeté.", "Vous êtes très clair. Je le serai aussi : nous défendrons nos intérêts."])
 + them("warn", "cold", ["Est-ce une menace ? {ForeignThe} ne cède pas aux pressions.", "Vous faites une erreur en me parlant sur ce ton."])
)

talk_foreign = {"id": "talk_foreign", "subject": [
  V("Entretien téléphonique avec {sender}"), V("Compte rendu : appel avec {sender}"), V("Échange avec le dirigeant {foreignOf}"),
  V("{foreignCountry} : compte rendu d'entretien"), V("Appel présidentiel — {foreignCountry}")], "sections": [
 sec("setting", [V("Entretien téléphonique, {{en fin de matinée|en début d'après-midi|en soirée|tard dans la soirée}}, {{avec interprètes|en présence des conseillers diplomatiques}}."),
                 V("Visioconférence d'une {{vingtaine de minutes|demi-heure|petite heure}}."),
                 V("Appel organisé à la demande de l'Élysée."), V("Échange en tête-à-tête, en marge d'une réunion internationale.")]),
 sec("opening", openings({
   "formal": (["{honorific},", "{honorific}, bonjour.", "Je vous salue, {honorific}.", "{honorific}, c'est un honneur."],
              ["je vous remercie de cet appel.", "je suis à votre écoute.", "merci pour votre appel.", "votre appel m'honore.", "je vous écoute avec attention."]),
   "direct": (["Bonjour.", "Merci d'appeler.", "Bien.", "Allô, oui."], ["Je vous écoute.", "Que puis-je pour vous ?", "Allons-y.", "De quoi s'agit-il ?", "Je n'ai que quelques minutes."]),
   "warm": (["Quel plaisir de vous entendre !", "Mon ami !", "Ah, enfin un appel de Paris !", "Bonjour, quelle bonne surprise !"],
            ["Comment allez-vous ?", "J'espère que tout va bien chez vous.", "Racontez-moi.", "Je pensais justement à vous.", "Votre famille va bien ?"]),
   "technical": (["Bonjour.", "Merci pour cet appel.", "Bonjour, {honorific}."],
                 ["Mes conseillers m'ont préparé quelques points.", "J'ai les notes de nos ministères sous les yeux.", "Je propose que nous passions en revue les dossiers.", "Commençons par l'ordre du jour, si vous voulez bien.", "J'ai quelques chiffres à partager."]),
   "lyrical": (["La voix de la France est toujours la bienvenue {foreignIn}.", "Paris m'appelle : c'est toujours un moment particulier.", "Entre nos deux pays, un appel n'est jamais anodin.", "Il y a dans chaque échange avec la France un peu d'histoire."],
               ["Je vous écoute.", "Parlons.", "Que me vaut cet honneur ?", "Dites-moi tout.", "Je suis tout ouïe."]),
   "blunt": (["Vous vouliez me parler.", "Bon.", "Je n'ai pas beaucoup de temps.", "Allez-y."], ["Je vous écoute.", "Soyez bref.", "Qu'y a-t-il ?", "Venons-en au fait.", "J'imagine que ce n'est pas un appel de courtoisie."]),
 }, "foreign")),
 sec("you", FOREIGN_YOU),
 sec("them", FOREIGN_THEM),
 sec("memory", [V("— {senderLast} : Je n'ai pas oublié {lastTopic}, d'ailleurs.", when=["history:topic", "history:recent"]),
                V("— {senderLast} : Après {lastTopic}, votre appel m'étonne, je l'avoue.", when=["history:topic", "history:refused"]),
                V("— {senderLast} : Notre dernier échange s'était bien passé ; continuons ainsi.", when=["history:cooperated"])], optional=True, chance=0.5),
 sec("close",
     narration("warm", ["L'appel", "L'échange", "La conversation", "L'entretien"],
               ["s'achève sur des paroles cordiales.", "se termine dans une atmosphère détendue.", "se conclut sur la promesse de se revoir bientôt.",
                "s'achève par des remerciements mutuels.", "se termine sur une note complice.", "s'achève sur un engagement à travailler ensemble."])
     + narration("neutral", ["L'appel", "L'échange", "La conversation", "L'entretien"],
                 ["reste courtois mais sans engagement.", "se termine poliment, chacun restant sur ses positions.", "s'achève sans percée particulière.",
                  "se conclut sur des formules d'usage.", "prend fin sans éclat.", "se termine sur un accord de principe pour se reparler."])
     + narration("cold", ["L'appel", "L'échange", "La conversation", "L'entretien"],
                 ["se termine sèchement.", "s'achève dans un silence pesant.", "tourne court.", "se conclut sans un mot aimable.",
                  "prend fin brutalement.", "laisse un goût amer aux deux parties."])),
 sec("advisor", [V("Note du conseiller diplomatique : la relation progresse.", when=["mood:warm"]),
                 V("Note du conseiller diplomatique : rien de décisif, mais le contact est maintenu.", when=["mood:neutral"]),
                 V("Note du conseiller diplomatique : il faudra du temps pour rétablir la confiance.", when=["mood:cold"])], optional=True, chance=0.7)]}

LOCAL_YOU = (
 you("listen", ["Je voulais vous entendre : quelles sont les priorités de {place} ?", "Dites-moi franchement ce qui ne va pas, et ce que l'État peut faire.",
                "Je souhaite comprendre les attentes de votre territoire. Je vous écoute."])
 + you("visit", ["Je viendrai prochainement à {place}. Je voulais vous l'annoncer moi-même.", "Je souhaite me rendre sur place ; préparons cette visite ensemble.",
                 "J'ai décidé de venir à {place} ; ce sera l'occasion de rencontrer les habitants."])
 + you("praise", ["Je tenais à saluer votre action ; on m'en dit beaucoup de bien.", "Vous faites un travail remarquable, et je voulais vous le dire.",
                  "Bravo pour ce que vous avez accompli à {place}."])
 + you("reprimand", ["Vos récentes déclarations m'ont surpris. L'État attend de la loyauté de ses partenaires.", "Je dois vous rappeler à vos responsabilités.",
                     "Certains de vos propos publics ne sont pas acceptables. Je vous le dis directement."])
)
LOCAL_THEM = (
 them("listen", "warm", ["Merci de m'écouter, c'est rare ! Je vous transmettrai un dossier précis très vite.", "Enfin ! Nous avons un projet prêt ; je vous envoie tout cela dans les jours qui viennent."])
 + them("listen", "neutral", ["Les besoins sont nombreux. Je vous écrirai pour formaliser une demande.", "Je vous remercie. Je vais préparer un courrier détaillé."])
 + them("listen", "cold", ["Écouter, c'est bien ; agir, c'est mieux. Vous recevrez un courrier, et j'attends une vraie réponse.", "On nous a souvent écoutés. Rarement entendus."])
 + them("visit", "warm", ["Vous serez accueilli à bras ouverts ! Les habitants seront ravis.", "Quelle bonne nouvelle ! Nous allons préparer cela au mieux."], extra=["president:male"])
 + them("visit", "warm", ["Vous serez accueillie à bras ouverts ! Les habitants seront ravis.", "Quelle bonne nouvelle ! Nous allons préparer cela au mieux."], extra=["president:female"])
 + them("visit", "neutral", ["Très bien. Je préviendrai les services.", "Entendu. Il faudra venir avec des annonces concrètes."])
 + them("visit", "cold", ["Venez, mais ne vous attendez pas à un accueil triomphal.", "Une visite ne remplace pas des moyens. Mais soit."])
 + them("praise", "warm", ["Merci, cela me touche beaucoup.", "C'est un travail d'équipe, mais je suis sensible à votre message."])
 + them("praise", "neutral", ["Merci. Mais nous avons encore besoin de l'État.", "C'est aimable. J'espère que les actes suivront."])
 + them("praise", "cold", ["Des compliments… Je préférerais des crédits.", "Je vous remercie, sans être dupe."])
 + them("reprimand", "warm", ["Vous avez raison, je me suis emporté. Cela ne se reproduira pas.", "Je comprends. Je serai plus mesuré."], extra=["sender:male"])
 + them("reprimand", "warm", ["Vous avez raison, je me suis emportée. Cela ne se reproduira pas.", "Je comprends. Je serai plus mesurée."], extra=["sender:female"])
 + them("reprimand", "neutral", ["Je défends mon territoire, c'est mon rôle. Mais j'entends votre message.", "Nous ne sommes pas d'accord, mais restons-en là."])
 + them("reprimand", "cold", ["Je ne suis pas votre subordonné. Mes électeurs jugeront.", "Je n'accepte pas ce ton. Et je le ferai savoir."], extra=["sender:male"])
 + them("reprimand", "cold", ["Je ne suis pas votre subordonnée. Mes électeurs jugeront.", "Je n'accepte pas ce ton. Et je le ferai savoir."], extra=["sender:female"])
)

talk_local = {"id": "talk_local", "subject": [
  V("Entretien avec {sender}"), V("Compte rendu : échange avec {sender}"), V("{place} : entretien avec l'élu", when=["sender:male"]),
  V("{place} : entretien avec l'élue", when=["sender:female"]), V("Rendez-vous à l'Élysée : {sender}")], "sections": [
 sec("setting", [V("Entretien {{à l'Élysée|par téléphone|en visioconférence}}, {{en début de matinée|à l'heure du déjeuner|en fin de journée}}."),
                 V("Rencontre dans le salon {{vert|des Ambassadeurs|Murat}}, une {{vingtaine|trentaine}} de minutes."),
                 V("Échange téléphonique {{rapide|cordial|direct}}.")]),
 sec("opening", openings({
   "formal": (["{honorific},", "{honorific}, bonjour.", "Je vous salue, {honorific}."],
              ["je vous remercie de me recevoir.", "c'est un honneur de vous rencontrer ici.", "merci de cette invitation.", "je vous remercie sincèrement de ce moment.", "je mesure le privilège de cet entretien."]),
   "direct": (["Merci.", "Bonjour.", "Bien."], ["Je vous écoute.", "Allons droit au but.", "Je ne vais pas vous prendre trop de temps.", "J'ai des choses à vous dire.", "Venons-en au sujet."]),
   "warm": (["Merci infiniment de prendre ce temps pour nous.", "Quel plaisir de vous voir !", "Bonjour, et merci pour votre accueil !"],
            ["Tout {place} vous salue.", "On parle beaucoup de vous chez nous, en bien.", "J'avais hâte de ce moment.", "Je vous ai apporté une spécialité de chez nous.", "Comment allez-vous ?"]),
   "technical": (["Bonjour.", "Merci de me recevoir."],
                 ["J'ai apporté quelques chiffres sur {place}.", "Je vous ai préparé une note de deux pages.", "Nos services ont fait le point sur les dossiers en cours.", "J'ai la liste des projets en attente.", "Permettez-moi de commencer par un état des lieux."]),
   "lyrical": (["C'est tout {place} qui vous remercie de cette attention.", "Il y a des jours qui comptent pour un territoire.", "Venir jusqu'ici, pour un élu de {place}, ce n'est pas rien."],
               ["Je vous écoute.", "L'émotion est grande d'être ici.", "Merci.", "Parlons de l'avenir.", "Je porte ici la voix de nos habitants."]),
   "blunt": (["Allons-y.", "Bon.", "Je ne suis pas là pour les petits fours."], ["Je vous écoute.", "Qu'avez-vous à me dire ?", "Le temps presse chez nous.", "J'espère que ce rendez-vous servira à quelque chose.", "Soyons efficaces."]),
 }, "local")),
 sec("you", LOCAL_YOU),
 sec("them", LOCAL_THEM),
 sec("memory", [V("— {senderLast} : Et je n'oublie pas {lastTopic}.", when=["history:topic", "history:refused"]),
                V("— {senderLast} : Merci encore pour {lastTopic}, au passage.", when=["history:topic", "history:cooperated"])], optional=True, chance=0.6),
 sec("close",
     narration("warm", ["L'échange", "L'entretien", "La rencontre", "Le rendez-vous"],
               ["se termine chaleureusement.", "s'achève sur une poignée de main appuyée.", "se conclut dans la bonne humeur.",
                "se termine sur la promesse d'un suivi rapide.", "laisse une impression très positive.", "s'achève sur des remerciements sincères."])
     + narration("neutral", ["L'échange", "L'entretien", "La rencontre", "Le rendez-vous"],
                 ["reste courtois.", "se termine sans engagement précis.", "s'achève sur des formules polies.", "se conclut sans surprise.",
                  "prend fin dans le calme.", "se termine sur un rendez-vous à fixer."])
     + narration("cold", ["L'échange", "L'entretien", "La rencontre", "Le rendez-vous"],
                 ["se termine sur un froid.", "s'achève sans un sourire.", "tourne court.", "laisse les deux parties insatisfaites.",
                  "se conclut sèchement.", "se termine dans une ambiance tendue."]))]}

DIPLOMACY_TEMPLATES = [proposal, response]
CONVERSATION_TEMPLATES = [talk_foreign, talk_local]
