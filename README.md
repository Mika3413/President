# Président

Jeu de simulation géopolitique moderne pour Android (Kotlin + libGDX).
Le joueur incarne le président fraîchement élu d'un pays réel — la **France** dans cette version —
et le dirige tant qu'il est réélu. Une élection perdue, une révolution ou un coup d'État met fin à la partie.

> **Simulation extrêmement profonde derrière, interface extrêmement lisible devant.**

Inspirations : *Supremacy 1914* pour la carte et la prise en main, *Geo-Political Simulator*
pour la profondeur — sans leurs défauts.

## Contenu

| Domaine | Ce qui est simulé |
|---|---|
| Carte | Monde → Europe → France → région → local (LOD, culling, index spatial) ; 13 régions, 96 départements, 53 villes françaises et 252 villes étrangères (capitales, grandes villes, colorées selon qui les tient), centrales, barrages, raffineries, ports, aéroports, LGV, autoroutes, bases ; 14 couches (opinion, chômage, revenus, santé, sécurité, industrie, agriculture, pollution, énergie, transports, militaire, crises...) avec légende et chiffre inscrit sur chaque département |
| Temps | Monde persistant, rythme verrouillé, rattrapage déterministe après absence, simulation d'arrière-plan Android (WorkManager) |
| Économie | Modèle macro interconnecté (confiance, impulsions différées, taux, dette, énergie, commerce, sanctions), budget détaillé voté au Parlement |
| Société | Opinion de 13 groupes sociaux et de chaque territoire, services publics, démographie, immigration |
| Politique | Gouvernement (Premier ministre, 11 ministres, priorités), Assemblée nationale (577 sièges, majorité/cohabitation, dissolution, motion de censure), référendums, 18 réformes structurelles, promesses de campagne, scandales |
| Élections | Présidentielle à deux tours, législatives (à échéance ou après dissolution), référendums, élections locales (départementales, régionales, municipales), participation, 6 familles politiques, bilan jugé par chaque groupe, promesses tenues ou rompues |
| Décisions | Panneau « ★ Décider » : 56 décisions nationales en 8 rubriques, dont 11 décisions de crise débloquées par la situation, avec prévision avant/après et confirmation des décisions lourdes (économie, social, sécurité, écologie, institutions, communication, international, défense), chacune avec coût, durée, délai et effets contrastés par groupe social, pays ou alliance ; 18 actions locales par département, classées par thème |
| Territoires | Demandes des maires, préfets, présidents de collectivités (accepter, partiellement, cofinancer, reporter, se renseigner, refuser), grands projets |
| Événements | 136 événements dépendant de l'état du monde (incidents industriels, catastrophes, crises sociales, attentats, scandales, épidémies, IA, cyber...), dont des histoires en plusieurs épisodes ; ampleur variable (limitée à exceptionnelle) et conséquences en chaîne ; chaque événement propose ses réponses propres plus de nombreuses autres (se rendre sur place, armée, aide européenne, reconstruction, sanctions, médiation...) et des mesures d'urgence cumulables |
| Crises et risques | Lassitude des mesures (respect qui s'érode), prorogation du régime d'exception votée par le Parlement au 12e jour, suspension possible par le Conseil d'État. Panneau « ⚠ Crises et risques » : probabilité à 30 jours de 10 risques (feux, crues, canicule, froid, épidémies, terrorisme, émeutes, cyber, pénuries, accidents industriels) et lieu le plus exposé ; 36 mesures durables, nationales ou locales (confinement national ou local, couvre-feu, masques, plan blanc, fermeture des frontières, état d'urgence, plan ORSEC, évacuation, armée en renfort, Vigipirate, Sentinelle, Canadair, interdiction des massifs, vigilance crues, plans canicule et grand froid, délestages, rationnement, réquisitions, chômage partiel...) avec coût mensuel, effets quotidiens, levée et délai avant relance, qui réduisent la probabilité et l'ampleur des drames |
| Gouvernement vivant | Les ministres agissent seuls selon leur tempérament et leur loyauté : 23 projets de ministère à soutenir ou refuser, 10 disputes entre ministres à trancher, menaces de démission, sorties de route ; un ministre ambitieux annonce son projet sans votre aval |
| Agenda | Le temps du président est compté : déplacements à l'étranger, sommets, tour de France, visites et entretiens occupent l'agenda (5 jours par semaine) ; pas de visite en France pendant un voyage, une crise grave en votre absence vous est reprochée |
| Secteurs et Bourse | 12 secteurs (industrie, luxe, aéronautique, tourisme, BTP, banque...) qui suivent le moral, l'énergie, les taux, la croissance mondiale, la sécurité et les mesures de crise ; 46 événements frappent des secteurs ; 19 grandes entreprises fictives, indice boursier et krachs ; soutenir une entreprise ou convoquer son PDG |
| Union européenne | 18 textes de la Commission votés au Conseil à la majorité qualifiée ou à l'unanimité ; position de la France, ralliement des partenaires, amendements, veto ; pronostic État par État, alliés et adversaires au Conseil |
| Président(e) | Création de votre président ou présidente : nom, visage, âge, parcours (haut fonctionnaire, chef d'entreprise, syndicaliste, officier... 12 au total) et personnalité, qui changent vos atouts de départ |
| Lois et Constitution | 39 lois de société, de justice, de travail, de libertés et d'institutions (fin de vie, cannabis, 35 heures, droit de manifester, gaz de schiste, durée et nombre de mandats, 49.3...) votées au Parlement ou par référendum ; indices des libertés, de la presse et de l'État de droit |
| Société civile | 23 acteurs (syndicats, patronat, cultes, lobbies, associations) dont la satisfaction suit vos choix ; les recevoir, céder à leurs revendications, au risque d'en fâcher d'autres |
| Finances publiques | 24 dispositifs fiscaux fins, BCE et règle de Taylor, euro face au dollar, stratégie de la dette, obligations vertes, nationalisations et privatisations, dividendes de l'État actionnaire |
| Commerce et matières premières | 7 matières premières à cours mondiaux (pétrole, gaz, uranium, blé, cuivre, lithium, or) qui font le prix de l'énergie et l'inflation ; stocks stratégiques, contrats d'approvisionnement, mine de lithium, gaz de schiste ; 9 produits d'exportation disputés à la concurrence, appels d'offres, garantie de l'État ; FMI (rapport annuel, programme d'aide), OMC (plaintes), Banque mondiale |
| Défense | Catalogue de 18 équipements réels (Rafale F4, frégates FDI, SAMP/T NG, SCALP, drones, cyber, porte-avions...) qui livrent des unités ou renforcent 7 capacités ; ventes d'armes (embargo sur les agresseurs, clients controversés) ; 12 bases à l'étranger (ouverture, fermeture, demandes de départ) ; dissuasion nucléaire (modernisation, dimension européenne, posture, essai, désarmement, avertissement solennel) |
| Renseignement | Opérations de la DGSE (espionnage politique, militaire, industriel, influence, financement de l'opposition, sabotage, coup d'État) parfois révélées au grand jour ; 10 groupes armés et terroristes dont la force pèse sur les attentats, enlèvements et émeutes (neutraliser, frapper, infiltrer, négocier, dissoudre) ; espionnage étranger et contre-espionnage |
| La rue et l'armée | 12 causes de contestation (retraites, vie chère, paysans, jeunesse, climat, violences policières...) : foule, radicalité et phases en direct (manifestations, blocages, émeutes, insurrection, révolution) ; allocution, dialogue, concession, maintien de l'ordre, fermeté, armée ; loyauté de l'armée, complots, coups d'État |
| ONU | Conseil de sécurité réel (5 permanents, 10 élus renouvelés chaque année) : résolutions sur les guerres en cours et les crises du monde, vote de chaque membre, vetos, propositions françaises, lobbying, amendements, effets concrets (sanctions, cessez-le-feu) |
| Diplomatie | 29 pays IA, mémoire diplomatique, négociation par clauses (14 types), contre-propositions, sanctions, ultimatums, condamnations, alliances (OTAN, UE, OTSC), alternances politiques à l'étranger |
| Armée et guerre | Unités sur une grille de 5 000 zones de théâtre, ordres (déplacer, attaquer, défendre, repli, soutien aérien, patrouille), opérations spéciales (parachutage, débarquement amphibie, frappes de missiles, cyberattaque), ordres directs sur la carte avec durée du trajet et rapport de forces, flèches et batailles affichées, prise de villes, frappes et cyberattaques de l'IA en guerre, attaques hybrides des pays hostiles en paix, ravitaillement, stocks, production, mobilisation, renseignement imparfait, combats, occupations, capitulations, cessez-le-feu et paix, dissuasion nucléaire |
| Bilan, presse, sondages | Courbes hebdomadaires du mandat et leurs causes (« Pourquoi ? »), opinion des 13 groupes sociaux, classement des pays, journal du mandat ; 4 journaux à ligne politique dont le climat pèse sur l'opinion, 3 instituts de sondage dont les résultats pèsent sur la majorité, préoccupations des Français ; négociation des textes au Parlement (chances d'adoption, amendements) |
| Dialogues | Messages procéduraux sans IA générative, mémoire des échanges, jamais deux fois le même texte |
| Partie | 6 situations de départ (France réelle, krach financier, pandémie, guerre aux portes de l'Union, majorité introuvable, hiver de pénuries), plusieurs parties sauvegardées côte à côte, bilan historique noté sur 20 (provisoire pendant le mandat, définitif à la fin), musique d'ambiance générée par le jeu (calme ou tendue selon la situation) ; fréquence des événements réglée sur des mandats complets simulés |
| Apprentissage | Académie : 10 modules guidés (bases, décider et gouverner, carte et territoire, économie, crises, diplomatie et Europe, armées, puissance, lois et rue, opinion et élections), chaque étape validée par l'action réelle ; partie d'entraînement séparée depuis l'accueil |
| Interface | Panneaux contextuels lisibles avec « Détails », fiches de territoire à onglets (Résumé en tuiles, Agir, Détails), conseils « À faire » menant à la bonne décision, tutoriel guidé pas à pas, info-bulles (survol ou appui long), bilan au retour, taille du texte, palette pour daltoniens, sons, rythme modifiable (Éclair, Express à Très long, et Temps réel : 1 heure = 1 heure, calendrier du jeu calé sur l'heure de Paris), portraits procéduraux, tutoriel progressif, aide et glossaire, notifications réglables par catégorie, mise en page téléphone |

## Installer l'APK

Chaque push déclenche la CI GitHub (onglet **Actions** → workflow *Build*) : tests du moteur puis
assemblage de l'APK de débogage, téléchargeable dans l'artefact **president-debug-apk**.

## Compiler et lancer

Prérequis : JDK 17+.

```bash
./gradlew :engine:test        # tests du moteur (déterminisme, sauvegarde, diplomatie, guerre, réformes, endurance...)
./gradlew :desktop:run        # lanceur PC de développement (même jeu que sur Android)
```

Le module `android` n'est inclus que si un SDK Android est configuré (`local.properties` avec
`sdk.dir=...` ou variable `ANDROID_HOME`) :

```bash
./gradlew :android:assembleDebug
```

> Le module Android est compilé et assemblé par la CI GitHub. Le lanceur PC utilise exactement
> les mêmes classes `core` et sert au développement.

Options de développement du lanceur PC :

```bash
# échelle d'interface (simule un écran haute densité)
./gradlew :desktop:run -Dpresident.uiScale=2
# script automatique (captures d'écran, zoom, sélection) — voir DevScriptDriver
./gradlew :desktop:run -Dpresident.script="new;wait:60;layer:OPINION;shot:/tmp/carte.png;quit"
```

## Organisation

```
engine/    Moteur de simulation Kotlin pur (aucune dépendance Android ni libGDX)
core/      Rendu de la carte, interface, écrans (libGDX)
desktop/   Lanceur PC (développement)
android/   Lanceur Android, notifications, simulation d'arrière-plan (WorkManager)
assets/    Données du jeu (snapshot du monde, pays, économie, événements, dialogues), géométrie, polices
tools/     Générateurs de données (carte depuis données ouvertes, snapshot France, événements, dialogues)
docs/      Architecture, modèles de simulation, décisions, feuille de route
```

Documentation : [Architecture](docs/ARCHITECTURE.md) · [Modèles de simulation](docs/SIMULATION.md) ·
[Décisions](docs/DECISIONS.md) · [Feuille de route](docs/ROADMAP.md) · [Format des données](assets/data/README.md) ·
[Attributions](ATTRIBUTIONS.md)
