# Modèles de simulation

Principe : **réalisme dans les conséquences, simplification dans les procédures**. Tous les
coefficients sont dans les données (`assets/data/economy/model_parameters.json`,
`countries/FRA/*.json`), jamais dans le code.

## Économie (`MacroEconomyModel`, mensuel, tous les pays)

Variables : PIB nominal, croissance réelle et potentielle, écart de production, inflation, niveau
des prix, salaires, chômage, confiance des ménages et des entreprises, dette, taux moyen et taux de
marché, prix de l'énergie.

Chaque mois :
1. **Confiance** : les ménages réagissent au chômage, à l'inflation et à la dynamique ; les
   entreprises à la dette, à la croissance, à la stabilité politique et au coût de l'énergie.
2. **Croissance** = potentiel + rappel de l'écart de production + effet de la confiance
   + impulsions budgétaires en cours + chocs (événements) + prix de l'énergie + accords commerciaux + bruit.
3. **Chômage** : loi d'Okun et retour lent vers le chômage structurel.
4. **Inflation** : ancrage sur la cible, courbe de Phillips (écart de production), énergie.
5. **Taux** : taux sans risque + primes (dette au-delà d'un seuil, déficit, défiance) ; le taux
   moyen de la dette suit lentement (refinancement progressif).
6. **Budget** (`BudgetCalculator`) :
   * recettes = montant initial × (taux / taux initial)^(1 − perte comportementale) × évolution de
     l'assiette (masse salariale, consommation, profits, PIB, prix) ;
   * dépenses = budget initial × décision politique × indexation (aucune, prix, chômage) ;
   * intérêts = dette × taux moyen ; dette += déficit / 12.

Une décision n'agit jamais d'un coup : une hausse d'impôt votée produit des recettes immédiates,
un choc de confiance, puis une **impulsion de demande négative étalée sur 12 mois** (multiplicateur
budgétaire), qui pèse sur l'emploi, donc sur l'opinion, donc sur les recettes futures.

Les budgets non indexés perdent du pouvoir d'achat avec l'inflation : la **qualité des services
publics** (`ServiceQualitySystem`) suit le financement réel et l'efficacité du ministre.

Les pays IA utilisent le même modèle avec un budget agrégé (mode allégé).

## Énergie

Production = Σ centrales (puissance × facteur de charge × disponibilité selon l'état matériel,
0 si arrêtée) + parcs agrégés (éolien, solaire, hydraulique diffus...). Demande indexée sur
l'activité. Marge = (production − demande − exportations contractuelles) / demande. Le prix de
l'énergie monte quand la marge se réduit → inflation, confiance des entreprises, opinion des ruraux.

Les centrales vieillissent selon l'entretien choisi ; un entretien faible augmente la probabilité
d'incident (arrêt de plusieurs semaines). Rénover coûte cher mais restaure l'état.

## Opinion publique

13 groupes répartis en 4 partitions qui couvrent chacune toute la population (âge, statut,
revenus, habitat). Les facteurs (chômage, prix, pouvoir d'achat, impôts des ménages et des
entreprises, santé, école, sécurité, retraites, protection sociale, transports, environnement,
dette, prix de l'énergie, activité) sont normalisés en scores [-1, 1], la plupart **relativement à
la situation de départ** (le joueur est jugé sur son bilan). Chaque groupe a ses sensibilités.

* Opinion de fond → converge vers la cible ; **chocs** temporaires des décisions/événements.
* **État de grâce** initial qui s'estompe.
* Opinion locale = composition sociale du département (urbanisation, âge, revenus) × opinion des
  groupes + écart de chômage local + **proximité politique** du territoire avec le président
  + chocs locaux (décisions territoriales, catastrophes).

## Élections

Tous les 5 ans (données), deux tours à 14 jours d'intervalle. Pour chaque groupe :
participation (abstention plus forte chez les indifférents, mobilisation des mécontents et des
enthousiastes), puis choix entre candidats fictifs de 6 familles politiques par un modèle logit :
proximité idéologique + force de la famille + **jugement du bilan du sortant par ce groupe**
− scandales. Le résultat national agrège les groupes ; une estimation par département est fournie.
Des sondages bruités sont publiés toutes les deux semaines. Pas de règle « popularité > 50 % ».

## Gouvernement et Parlement

* Efficacité d'un ministère = compétence, gestion, expérience, loyauté du ministre + priorité
  (au plus 3 priorités hautes). Ministère vacant = intérim peu efficace.
* La loyauté dérive avec la relation au président et sa popularité ; sous un seuil, démission.
* Soutien parlementaire : base + popularité + ouverture (un Premier ministre d'une autre
  sensibilité élargit la majorité, mais trop d'écart nuit).
* Les mesures budgétaires sont votées après un délai ; un rejet peut être contourné par une
  adoption sans vote, au prix d'un coût politique.

## Événements

Chaque type d'événement (fichiers `events/*.json`) a une probabilité quotidienne de base,
multipliée par des **modificateurs liés à l'état** (ex. état d'une centrale, chômage local,
opinion d'un groupe, saison) et filtrée par des conditions. Il cible un territoire, une ville,
un équipement, un ministre ou un pays. Il peut produire des effets immédiats, une brève, une
notification et un message avec options (accepter, accepter partiellement, cofinancer, reporter,
demander des informations, refuser...). Sans réponse avant l'échéance, l'option par défaut est
appliquée par les services de l'État. Les effets sont différés/étalés et peuvent lancer un projet
dont les effets s'appliquent à son achèvement.

## Diplomatie

* **Mémoire diplomatique** : chaque pays garde des souvenirs datés (partenariat, désaccord, accord
  signé, engagement tenu, accord rompu, proposition ignorée...) avec une demi-vie. La relation et la
  confiance en découlent ; le joueur voit un libellé et les facteurs, jamais un score.
* **Négociation structurée** : une proposition est un ensemble de clauses paramétrées (fourniture
  d'électricité, barrières commerciales, aide, coopération de défense, investissement) et une durée.
* L'IA évalue chaque clause selon ses besoins (déficit électrique, commerce, finances), le
  tempérament de son dirigeant (exigence, prudence, souverainisme...), la relation et la confiance.
  Elle accepte, refuse en motivant, ou **construit une contre-proposition** en ajustant les
  paramètres pas à pas.
* **Perception imparfaite** : l'IA estime la capacité d'exportation française avec une erreur
  dépendant de la qualité de son renseignement ; elle peut se tromper.
* Les décisions de l'IA sont journalisées (Réglages → Journal de debug).

## Dialogues procéduraux

Un message = sections (intro, historique, contexte, problème, demande, conclusion, signature),
chacune avec de nombreuses variantes filtrées par des étiquettes : tempérament de l'expéditeur,
relation, **historique des échanges** (« Après vos précédents refus… »), urgence, saison, conjoncture,
popularité du président. Les synonymes `[[mot]]` multiplient les combinaisons. La signature de chaque
texte est mémorisée dans la sauvegarde : un texte identique n'est jamais réaffiché.

## Armée et guerre

* **Théâtre** : grille de zones d'1° (Europe, Afrique du Nord, Moyen-Orient), terrestres (rattachées
  à un pays, et à un département pour la France) ou maritimes côtières. Contrôle, occupation et
  annexions sont suivis par zone.
* **Unités** (brigades, escadres, groupes navals) : disponibilité, effectifs, moral, munitions,
  carburant, fatigue, expérience. Ordres : déplacer, attaquer, défendre, tenir, repli, soutien aérien,
  patrouille. Les escadres agissent dans leur rayon d'action (allongé par les ravitailleurs).
* **Déplacement** horaire le long d'un itinéraire calculé (A*) ; il faut un droit de passage pour
  traverser un pays non allié ; la progression est lente en territoire hostile.
* **Ravitaillement** : une unité est ravitaillée si une chaîne de zones amies la relie au territoire
  national (portée allongée par les brigades logistiques). Ravitaillée, elle puise dans les stocks
  nationaux ; isolée, elle s'use et peut se rendre si elle est encerclée.
* **Combat** horaire : puissance = valeur d'attaque ou de défense × effectifs × disponibilité ×
  moral × munitions × fatigue × expérience, posture défensive et territoire national favorisés,
  soutien aérien et naval. Pertes proportionnelles au rapport de forces ; le camp qui craque se
  replie et la zone change de mains.
* **Guerres** : alliances défensives appelées à l'aide (le joueur choisit d'entrer en guerre, d'aider
  sans combattre ou de rester à l'écart), lassitude croissante avec les pertes et la durée,
  mobilisation des réserves, capitulation à la chute de la capitale, cessez-le-feu et traités de paix
  (frontières d'avant-guerre ou annexion des zones occupées). Une capitale française occupée 30 jours
  met fin à la partie.
* **IA** : offensives là où le rapport de forces local est favorable, défense des fronts menacés,
  repli des unités usées, soutien aérien des batailles. Déclaration de guerre rare, contre un voisin
  hostile jugé plus faible, et jamais contre une puissance nucléaire ou ses alliés (dissuasion).
* **Renseignement** : seules les unités proches de nos forces, au contact ou alliées sont connues ;
  leurs effectifs sont estimés avec une erreur dépendant de la qualité du renseignement.

## Réformes, promesses, démographie

* Les **réformes** (fichier `countries/FRA/reforms.json`) sont votées après un mois, avec une
  exigence parlementaire propre ; elles combinent chocs d'opinion par groupe et effets progressifs
  (dépenses, croissance potentielle, chômage structurel, qualité des services, capacités
  électriques...). Certaines sont incompatibles entre elles.
* Les **promesses** choisies au lancement sont évaluées en continu et jugées au scrutin : tenues,
  elles améliorent le vote des groupes concernés ; rompues, elles le dégradent.
* La **démographie** suit naissances, décès et solde migratoire (modulé par la politique
  migratoire) ; les territoires vieillissent, ce qui modifie leur composition sociale.

## Monde vivant

Les pays étrangers changent de dirigeant (alternance ou reconduction selon leur popularité), nouent
des coopérations ou entrent en tension entre eux, sanctionnent les agresseurs, demandent de l'aide,
cherchent à acheter des armements français.
