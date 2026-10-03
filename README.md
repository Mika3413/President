# Président

Jeu de simulation géopolitique moderne pour Android (Kotlin + libGDX).
Le joueur incarne le président fraîchement élu d'un pays réel — la **France** dans cette version —
et le dirige tant qu'il est réélu. Une élection perdue met fin à la partie.

> **Simulation extrêmement profonde derrière, interface extrêmement lisible devant.**

Inspirations : *Supremacy 1914* pour la carte et la prise en main, *Geo-Political Simulator*
pour la profondeur — sans leurs défauts.

## État actuel : première tranche verticale jouable

| Élément | État |
|---|---|
| Projet multi-module (moteur pur / libGDX / PC / Android) | ✅ |
| Carte 2D vectorielle : monde → Europe → France → région → local, LOD, culling, index spatial | ✅ |
| 13 régions, 96 départements, 53 villes, centrales, barrages, raffineries, ports, aéroports, LGV, autoroutes, bases | ✅ |
| Couches : administratif, opinion, chômage, population, revenus, énergie, transports, militaire, crises | ✅ |
| Temps du monde persistant (rythme verrouillé), rattrapage `simulate(from, to)` déterministe | ✅ |
| Sauvegarde robuste (gzip, écriture atomique, copie de secours, version de format + migrations) | ✅ |
| Économie macro interconnectée (budget détaillé, confiance, impulsions différées, taux, dette, énergie) | ✅ |
| Gouvernement : Premier ministre, 11 ministres, priorités, nominations, Parlement, vote des mesures | ✅ |
| Opinion par groupes sociaux (13 groupes, 4 partitions) et par territoire | ✅ |
| Événements dynamiques dépendant de l'état (16 types) avec décisions et effets différés | ✅ |
| Messages procéduraux sans IA générative, mémoire des échanges, unicité garantie | ✅ |
| Diplomatie structurée (5 pays IA, clauses, contre-propositions, mémoire diplomatique, perception imparfaite) | ✅ |
| Élections présidentielles à deux tours (participation, familles politiques, bilan par groupe) | ✅ |
| Notifications internes + Android (WorkManager, réglages par catégorie) | ✅ (Android non compilé ici, voir ci-dessous) |
| Armée : unités cohérentes et bases (consultation, disponibilité) — ordres et guerre | 🔜 |

## Compiler et lancer

Prérequis : JDK 17+.

```bash
./gradlew :engine:test        # tests du moteur (25 tests : déterminisme, sauvegarde, diplomatie, élections...)
./gradlew :desktop:run        # lanceur PC de développement (même jeu que sur Android)
```

Le module `android` n'est inclus que si un SDK Android est configuré (`local.properties` avec
`sdk.dir=...` ou variable `ANDROID_HOME`) :

```bash
./gradlew :android:assembleDebug
```

> Note : l'environnement de développement initial n'avait pas accès au dépôt Maven de Google ;
> le module Android a été écrit mais n'a pas pu être compilé ici. Le moteur, le rendu et
> l'interface sont validés via le lanceur PC (mêmes classes `core`).

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
