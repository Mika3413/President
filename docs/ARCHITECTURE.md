# Architecture

## Modules

```
engine  ──►  core  ──►  desktop
                   └──►  android
```

| Module | Rôle | Dépendances |
|---|---|---|
| `engine` | Toute la simulation, les données, la sauvegarde, la lisibilité (readouts) | kotlinx-serialization uniquement |
| `core` | Carte, interface scene2d, écrans, pilotage temps réel (`GameController`) | `engine`, libGDX, gdx-freetype |
| `desktop` | Lanceur LWJGL3, automatisation de captures | `core` |
| `android` | Activité, notifications, `BackgroundSimulationWorker` | `core`, WorkManager |

La règle est stricte : **le moteur ne connaît ni Android ni libGDX**. Le worker Android
simule une partie sans aucun contexte graphique, et le moteur est intégralement testable sur JVM.

## Packages du moteur (`fr.president.engine`)

| Package | Contenu |
|---|---|
| `time` | `WorldTime` (secondes du monde), `GamePace`, `WorldClock` (temps réel ↔ temps du monde) |
| `simulation` | `Simulator` (boucle de rattrapage), `SimulationSystem` + `Cadence`, `Scheduler`, `ScheduledAction`, `ActionDispatcher`, `SimulationContext` |
| `world` | `WorldState` (racine sérialisable), `CountryState`, `PlayerState` |
| `data` | Définitions des fichiers de données, `DataLoader`, `DataValidator`, `GameDatabase` |
| `economy` | `EconomyState`, `MacroEconomyModel`, `BudgetCalculator`, `ServiceQualitySystem` |
| `energy` | Bilan électrique (production des centrales selon leur état, demande, prix) |
| `government` | Gouvernement, efficacité des ministères, Parlement, mesures budgétaires (`PolicyService`) |
| `opinion` | Opinion par groupe social puis par territoire |
| `territory` | Départements, régions, villes, grands projets |
| `infrastructure` | État et entretien des équipements |
| `military` | Unités et bases (disponibilité, stocks, moral) |
| `events` | Événements pilotés par les données, résolution des variables, lancement, réponses |
| `effects` | Effets de gameplay différés et progressifs |
| `dialogue` | Composition procédurale des messages, mémoire des échanges |
| `diplomacy` | Relations (mémoire), clauses, évaluation IA, contre-propositions, accords |
| `ai` | Décisions des gouvernements étrangers |
| `elections` | Sondages et scrutins |
| `inbox`, `notifications` | Messagerie du président, alertes et réglages |
| `readout` | Traduction des valeurs internes en libellés lisibles + détails |
| `setup` | Création d'une partie depuis un snapshot |
| `save` | Codec, migrations, dépôt atomique |
| `session` | `GameSession` : façade utilisée par toutes les interfaces, commandes du joueur |

## Le temps

* Chaque partie choisit un rythme (`data/config/game_config.json`), **verrouillé** dans `WorldClock`.
* `WorldClock.worldTimeAt(realUtcMillis)` donne l'instant du monde correspondant à l'heure réelle.
  Si l'horloge système recule, le monde attend : il ne recule jamais.
* `Simulator.advanceTo(target)` avance heure par heure du monde. Les systèmes déclarent leur
  cadence (`HOURLY`, `DAILY`, `MONTHLY`) et ne s'exécutent qu'aux changements d'heure/jour/mois.
  Les actions planifiées (réponse diplomatique, vote, tour d'élection, fin de chantier, survenue
  d'un événement) s'exécutent à leur instant exact.
* Le coût d'un rattrapage est faible : un an de monde ≈ 8 760 pas horaires, 365 pas quotidiens,
  12 pas mensuels.

### Déterminisme

Tout l'aléatoire passe par `GameRandom` (SplitMix64) dont l'état est sauvegardé. Un test vérifie
qu'avancer de 120 heures d'un coup ou par petits pas irréguliers produit **exactement** le même
monde (sérialisation identique), et qu'une partie rechargée continue exactement comme l'originale.
C'est ce qui permet au worker Android et au jeu de simuler indifféremment la même partie.

## Persistance sur Android

1. Au passage en arrière-plan : rattrapage, sauvegarde, puis planification d'un travail
   périodique WorkManager (15 min, minimum Android).
2. Le worker charge la sauvegarde, simule jusqu'à maintenant, enregistre, et publie les
   notifications retenues par les réglages du joueur (urgentes seulement / toutes / désactivées).
3. Au retour, si la sauvegarde est plus récente que la session en mémoire, elle est rechargée ;
   sinon la session rattrape simplement le temps écoulé. Un résumé de l'absence est affiché.

Le jeu ne dépend jamais du maintien du processus en vie.

## Sauvegardes

`SaveFile { formatVersion, savedAtRealUtcMillis, gameVersion, state }`, JSON compressé.
Écriture dans un fichier temporaire, vérification par relecture, conservation de la version
précédente (`.bak`), renommage. Au chargement, `SaveMigrator` met à niveau l'arbre JSON format par
format avant désérialisation ; un format plus récent que le jeu est refusé proprement.
La sauvegarde contient le monde complet (graine, état du générateur, personnages, relations,
signatures des messages, accords, projets, élections...) : **elle ne dépend plus du snapshot**.

## Carte (module `core`)

* Géométrie générée hors ligne par `tools/mapgen/build_map.py` depuis des données ouvertes,
  stockée en longitude/latitude, projetée au chargement (équirectangulaire centrée sur 46,5°N).
* Polygones triangulés une fois (`EarClippingTriangulator`) et dessinés par `PolygonSpriteBatch`.
* `SpatialGrid` (grille uniforme) pour la sélection et le culling ; boîtes englobantes par anneau.
* `LodPolicy` : 5 niveaux (monde, Europe, France, région, local) déterminent ce qui est dessiné.
* `MapRenderer` dessine en espace monde ; `OverlayRenderer` dessine marqueurs et étiquettes en
  espace écran (taille constante) avec évitement des chevauchements.
* Prochaine étape pour la carte fine (communes, routes détaillées) : tuiles chargées à la demande.

## Interface

* scene2d.ui avec un habillage construit par programme (`UiSkin`), polices DejaVu générées à la
  densité de l'écran (`uiScale`).
* Chaque panneau (`Panel`) reconstruit son contenu à partir de l'état ; les données affichées
  passent par les *readouts* du moteur (libellés qualitatifs + bouton « Détails »).
