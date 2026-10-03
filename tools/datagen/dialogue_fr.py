"""Génère les modèles de dialogue procéduraux français (assets/data/dialogue/fr/).
Chaque section a de nombreuses variantes ; [[mot]] tire un synonyme du lexique ;
{variable} est remplacée par le moteur. Les étiquettes 'when'/'unless' filtrent les variantes."""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
os.chdir(os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "..", "assets", "data", "dialogue", "fr"))

from dialogue_fr_lib import V, sec, letter, INTROS, HISTORY, CLOSINGS_FORMAL, SIGN, lexicon

transport = letter("territorial_transport_request",
 [V("Demande de financement : transports à {city}"), V("{city} : un projet de transport en attente de l'État"),
  V("Mobilité à {city} — sollicitation de l'État"), V("Projet de transport à {city}"), V("Requête de la ville de {city}")],
 [V("Notre agglomération connaît une croissance démographique soutenue et nos réseaux de transport arrivent à saturation."),
  V("Chaque matin, des milliers de [[residents]] de {city} passent plus d'une heure dans les embouteillages."),
  V("Le développement économique de {city} dépend désormais directement de la qualité de ses transports."),
  V("Les entreprises qui s'installent à {city} nous le répètent : la desserte de nos zones d'activité est insuffisante."),
  V("Malgré les difficultés économiques du moment, {city} continue d'attirer de nouveaux habitants.", when=["economy:bad"]),
  V("La dynamique économique actuelle est une chance pour {city}, encore faut-il pouvoir l'accompagner.", when=["economy:good"])],
 [V("Nous avons conçu un [[project]] [[important]] de transport collectif, mais son financement dépasse nos seules capacités."),
  V("Le [[project]] que nous portons est prêt techniquement ; il ne manque que la participation de l'État."),
  V("Sans [[support]] national, ce [[project]] [[crucial]] devra être abandonné ou repoussé de plusieurs années."),
  V("Le conseil municipal a voté le principe du [[project]] à une large majorité, mais nos finances ne suffiront pas."),
  V("Je ne vous cache pas que la patience de nos [[residents]] s'épuise.", when=["relation:bad"])],
 [V("Je sollicite donc une participation de l'État à hauteur de {amountText}."),
  V("Le montant demandé à l'État s'élève à {amountText}, étalés sur la durée des travaux."),
  V("Une contribution de {amountText} permettrait de lancer les travaux dès cette année."),
  V("Je vous demande [[strongly]] d'accorder à {city} une aide de {amountText}."),
  V("Nous attendons de l'État un engagement clair : {amountText}. Pas moins.", when=["trait:aggressive"]),
  V("Toute participation, même partielle, de l'ordre de {amountText} serait déterminante.", when=["trait:pragmatic"])])

details = {"id": "territorial_request_details", "subject": [V("Précisions")], "sections": [
 sec("details_intro", [V("Comme vous me l'avez demandé, voici quelques précisions sur notre dossier."),
                      V("Suite à votre demande d'informations complémentaires, je vous transmets les éléments suivants."),
                      V("Vos services souhaitaient en savoir davantage : voici l'essentiel.")]),
 sec("details_body", [V("Le coût total est estimé à {amountText} pour l'État. Les études d'impact prévoient une baisse sensible de la circulation automobile et la création d'emplois pendant les travaux."),
                     V("Le [[project]] représenterait {amountText} pour l'État. Selon nos estimations, plusieurs dizaines de milliers d'usagers en bénéficieraient chaque jour."),
                     V("La participation demandée ({amountText}) serait versée progressivement. La collectivité prendra en charge l'exploitation du réseau.")]),
 sec("details_alt", [V("Un cofinancement avec la région est envisageable si l'État s'engage le premier."),
                    V("Une version réduite du [[project]] est possible, au prix d'un service moins ambitieux."),
                    V("Nous sommes ouverts à un phasage des travaux pour alléger l'effort annuel de l'État.")], optional=True, chance=0.8)]}

hospital = letter("hospital_overload",
 [V("Urgences saturées {departmentIn}"), V("Alerte hospitalière : {department}"), V("Situation sanitaire préoccupante à {city}", when=["has:city"]),
  V("Hôpitaux {departmentOf} : appel à l'État")],
 [V("Les services d'urgence {departmentOf} fonctionnent depuis plusieurs semaines au-delà de leurs capacités."),
  V("Les soignants de l'hôpital de {city} m'alertent quotidiennement sur leur épuisement.", when=["has:city"]),
  V("Les soignants des hôpitaux du département m'alertent quotidiennement sur leur épuisement."),
  V("Faute de lits disponibles, des patients patientent désormais plus de vingt-quatre heures sur des brancards."),
  V("L'épidémie hivernale a achevé de déstabiliser un système déjà fragile.", when=["season:winter"]),
  V("La vague de chaleur a fortement accru l'afflux de patients âgés.", when=["season:summer"])],
 [V("La [[situation]] [[worsen]] de jour en jour et plusieurs services menacent de fermer la nuit."),
  V("Sans renfort rapide, nous ne pourrons plus garantir la sécurité des patients."),
  V("Le personnel parle ouvertement de démissions collectives."),
  V("Je crains un drame que personne ne pourrait justifier.", when=["trait:aggressive"])],
 [V("Je vous demande un [[measure]] d'urgence de l'ordre de {amountText} pour renforcer les équipes."),
  V("Une aide immédiate de {amountText} permettrait de passer le cap, en attendant une réforme de fond."),
  V("J'en appelle à votre arbitrage : {amountText} suffiraient à rouvrir les lits fermés.")])

factory = letter("factory_closure",
 [V("Menace sur {jobs} emplois {departmentIn}"), V("Fermeture annoncée d'un site industriel près de {city}", when=["has:city"]), V("Fermeture annoncée d'un site industriel ({department})"),
  V("Urgence industrielle : {department}"), V("Plan social : {jobs} emplois menacés")],
 [V("La direction d'un grand groupe industriel vient d'annoncer son intention de fermer son site près de {city}.", when=["has:city"]),
  V("La direction d'un grand groupe industriel vient d'annoncer son intention de fermer son site {departmentIn}."),
  V("Un site industriel historique {departmentOf} est menacé de fermeture."),
  V("La hausse des coûts de l'énergie a fragilisé l'usine, qui emploie {jobs} salariés.", when=["economy:bad"]),
  V("Malgré un carnet de commandes correct, le groupe souhaite délocaliser sa production.")],
 [V("Ce sont {jobs} emplois directs, et bien davantage chez les sous-traitants, qui sont en jeu."),
  V("Pour le bassin d'emploi, déjà fragile, ce serait un choc [[important]]."),
  V("Les élus locaux et les syndicats demandent une intervention de l'État."),
  V("La [[tension]] monte sur le site ; des blocages sont annoncés.")],
 [V("Plusieurs options s'offrent à l'État : une aide à la reprise, la recherche d'un repreneur ou le laisser-faire. J'attends vos instructions."),
  V("Je sollicite votre [[decision]] [[quickly]] : un repreneur pourrait se manifester si l'État apporte des garanties."),
  V("Une aide publique de {amountText} pourrait sauver l'essentiel des emplois.")])

nuclear = letter("nuclear_incident",
 [V("Incident technique {infrastructureAt}"), V("Arrêt {infrastructureOf} : point de situation"), V("Note urgente : {infrastructure}")],
 [V("Un incident technique a conduit à l'arrêt automatique d'un réacteur {infrastructureOf}."),
  V("Les équipes {infrastructureOf} ont procédé à la mise à l'arrêt d'une unité après la détection d'une anomalie."),
  V("L'Autorité de sûreté a été immédiatement informée d'un événement sur le site {infrastructureOf}.")],
 [V("Il n'y a aucune conséquence pour la population, mais l'arrêt devrait durer environ {days} jours."),
  V("L'installation restera indisponible quelques semaines, ce qui réduit nos marges de production électrique."),
  V("L'état général de la centrale explique en partie cet incident ; un entretien renforcé est recommandé.", when=["urgency:high"]),
  V("Nos voisins européens suivent la situation avec attention.")],
 [V("Je vous propose trois options : une inspection approfondie, un redémarrage selon la procédure normale, ou une communication très ouverte."),
  V("Votre arbitrage est attendu sur la conduite à tenir dans les prochains jours."),
  V("Je recommande pour ma part une inspection approfondie, même si elle prolongera l'arrêt.", when=["trait:cautious"]),
  V("Je recommande un redémarrage rapide : chaque jour d'arrêt coûte cher.", when=["trait:pragmatic"])])

refinery = letter("refinery_accident",
 [V("Accident {infrastructureAt}"), V("Incendie industriel : {infrastructure}"), V("Point de situation — {infrastructure}")],
 [V("Un incendie s'est déclaré cette nuit sur le site {infrastructureOf}."),
  V("Une explosion a été entendue à plusieurs kilomètres {infrastructureOf}."),
  V("Les secours sont intervenus en nombre sur le site {infrastructureOf} après un accident industriel.")],
 [V("Les riverains s'inquiètent de la qualité de l'air et demandent des comptes."),
  V("Le site restera fermé plusieurs semaines ; l'approvisionnement en carburant de la région pourrait être perturbé."),
  V("Le vieillissement des installations est pointé du doigt par les syndicats.")],
 [V("Je vous propose d'ouvrir une enquête, d'imposer un plan de prévention ou d'indemniser rapidement les riverains."),
  V("Votre [[decision]] est attendue [[quickly]] : la presse locale s'est emparée du sujet.")])

strike = letter("national_strike",
 [V("Mobilisation sociale : votre arbitrage"), V("Grève interprofessionnelle"), V("Mouvement social : point d'étape"), V("Les syndicats appellent à la grève")],
 [V("Les principales organisations syndicales appellent à une journée de grève nationale."),
  V("La hausse des prix pèse lourdement sur les salariés, qui réclament des mesures fortes.", when=["economy:bad"]),
  V("Le mécontentement social, latent depuis plusieurs mois, s'exprime désormais dans la rue."),
  V("Les transports, l'énergie et l'enseignement seront fortement perturbés.")],
 [V("Le mouvement risque de durer si aucun signal n'est envoyé."),
  V("Les entreprises s'inquiètent de l'impact sur l'activité."),
  V("La popularité du gouvernement est en jeu.", when=["president:unpopular"])],
 [V("Je vous propose d'ouvrir des négociations salariales, de consentir des concessions ciblées, ou de tenir bon. Chaque option a un coût."),
  V("Le dialogue me semble préférable, mais il coûtera environ {amountText}.", when=["trait:warm"]),
  V("Je recommande la fermeté : céder maintenant créerait un précédent.", when=["trait:tough"])])

demo = letter("demonstration",
 [V("Manifestation à {city}"), V("Ordre public à {city}"), V("Rassemblement massif à {city}")],
 [V("Plusieurs milliers de personnes ont défilé aujourd'hui dans les rues de {city}."),
  V("Une manifestation d'ampleur s'est tenue à {city} contre la politique du gouvernement."),
  V("Le cortège parti du centre-ville de {city} a réuni bien plus de monde que prévu.")],
 [V("Quelques incidents ont éclaté en fin de journée."),
  V("Les organisateurs annoncent de nouvelles mobilisations."),
  V("Le climat local est tendu ; la presse nationale relaie les images.")],
 [V("Souhaitez-vous que je reçoive les organisateurs, ou que l'accent soit mis sur le maintien de l'ordre ?"),
  V("J'attends vos instructions sur la stratégie à adopter.")])

heat = letter("heatwave",
 [V("Canicule : déclenchement du plan national ?"), V("Épisode caniculaire en cours"), V("Alerte chaleur")],
 [V("Les températures dépassent 38 °C sur une grande partie du territoire."),
  V("Météo-France annonce un épisode de chaleur exceptionnel pour les prochains jours.")],
 [V("Les personnes âgées et les plus fragiles sont particulièrement exposées."),
  V("Les hôpitaux et les EHPAD se préparent à un afflux de patients.")],
 [V("Je vous propose de déclencher le plan canicule renforcé, pour un coût d'environ 200 M€."),
  V("Le dispositif habituel peut suffire, mais un renforcement protégerait mieux les plus vulnérables.")])

flood = letter("flood",
 [V("Inondations : {department}"), V("Crues {departmentIn}"), V("Catastrophe naturelle : {department}")],
 [V("De fortes pluies ont provoqué des crues {departmentIn}."),
  V("Plusieurs communes autour de {city} sont sous les eaux.", when=["has:city"]),
  V("Plusieurs communes du département sont sous les eaux."),
  V("Les cours d'eau {departmentOf} ont atteint des niveaux records.")],
 [V("Des centaines d'habitations ont été évacuées et les dégâts sont considérables."),
  V("Les sinistrés attendent un geste fort de l'État."),
  V("Les agriculteurs du secteur ont perdu une partie de leurs récoltes.")],
 [V("Je sollicite la reconnaissance de l'état de catastrophe naturelle et un fonds d'urgence de {amountText}."),
  V("Une aide de {amountText} permettrait de reloger les familles et de réparer les équipements publics.")])

cyber = letter("cyberattack",
 [V("Cyberattaque contre nos services publics"), V("Incident cyber majeur"), V("Sécurité numérique : alerte")],
 [V("Une cyberattaque d'ampleur frappe plusieurs hôpitaux et administrations."),
  V("Nos services ont détecté une intrusion coordonnée dans plusieurs systèmes publics.")],
 [V("Certains services sont à l'arrêt ; l'origine de l'attaque reste à établir."),
  V("Les données de milliers d'usagers pourraient avoir été dérobées.")],
 [V("Je vous propose un plan national de cybersécurité de 500 M€, ou une gestion avec nos moyens actuels."),
  V("Votre arbitrage est attendu : l'opinion réclame des garanties.")])

scandal = letter("minister_scandal",
 [V("Affaire {minister} : quelle position ?"), V("Révélations visant {minister}"), V("Note confidentielle : {minister}")],
 [V("Un hebdomadaire publie des révélations embarrassantes concernant {minister}."),
  V("La presse met en cause {minister} pour des faits anciens de conflit d'intérêts."),
  V("Des documents compromettants visant {minister} circulent depuis ce matin.")],
 [V("L'opposition réclame déjà sa démission."),
  V("L'affaire menace de parasiter l'action du gouvernement pendant des semaines."),
  V("Pour l'instant, l'intéressé nie fermement.")],
 [V("Souhaitez-vous le maintenir, ou dois-je lui demander de se retirer ?"),
  V("Je me range à votre arbitrage, mais je crois qu'un départ protégerait le gouvernement.", when=["trait:cautious"]),
  V("Je vous suggère de le maintenir : la présomption d'innocence doit prévaloir.", when=["trait:proud"])])

welcome = {"id": "welcome", "subject": [V("Premiers jours à l'Élysée"), V("Bienvenue, {honorific}"), V("Note du Premier ministre")], "sections": [
 sec("intro", [V("{honorific},"), V("{honorific}, permettez-moi de vous féliciter une nouvelle fois pour votre élection.")]),
 sec("context", [V("Le pays vous a confié les rênes dans une période exigeante : la dette publique est élevée, la croissance reste modeste et les Français attendent des résultats sur leur pouvoir d'achat."),
                 V("Les attentes sont immenses : pouvoir d'achat, services publics, sécurité, finances publiques. Chaque décision comptera.")]),
 sec("advice", [V("Je vous suggère de commencer par examiner le budget et la composition du gouvernement, puis de parcourir la carte pour prendre le pouls des territoires."),
                V("Les maires, préfets et présidents de région vont rapidement vous solliciter. Nos partenaires européens aussi.")]),
 sec("closing", [V("Le gouvernement est à votre disposition."), V("Je reste à vos côtés pour mettre en œuvre votre projet.")]),
 sec("signature", SIGN)]}

# Diplomatie et entretiens : voir dialogue_fr_diplo.py
from dialogue_fr_diplo import DIPLOMACY_TEMPLATES, CONVERSATION_TEMPLATES

files = {
 "territory.json": [transport, details, hospital, factory],
 "incidents.json": [nuclear, refinery],
 "society.json": [strike, demo, heat, flood, cyber],
 "government.json": [scandal, welcome],
 "diplomacy.json": DIPLOMACY_TEMPLATES,
 "conversations.json": CONVERSATION_TEMPLATES,
}
for name, templates in files.items():
    json.dump({"templates": templates}, open(name, "w"), ensure_ascii=False, indent=1)
json.dump(lexicon, open("lexicon.json", "w"), ensure_ascii=False, indent=1)
print("dialogue ok")
