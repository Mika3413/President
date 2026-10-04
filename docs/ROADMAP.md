# Feuille de route

Le jeu couvre le cahier des charges initial et ses enrichissements (Assemblée, Sénat, élections
locales et européennes, référendums, sommets internationaux, outre-mer, entretiens, histoires à
rebondissements, notifications application fermée). Pistes suivantes :

1. **Retours de test sur appareil** : fluidité de la carte sur petits téléphones, ergonomie tactile,
   comportement de l'arrière-plan selon les marques (optimisations de batterie agressives).
2. **Musique** : les sons d'interface existent (générés par programme) ; une ambiance musicale reste à faire.
3. **Autres pays jouables** : le moteur est générique, mais tout le contenu (institutions, réformes,
   événements, dialogues) est écrit pour la France. Chaque nouveau pays demande son propre jeu de
   données et de textes : c'est un projet à part entière.
4. **Carte fine** : communes et routes détaillées chargées par tuiles.
5. **Guerre** : parachutage, débarquement, frappes et cyberattaque sont en place ; restent la guerre électronique et les opérations combinées multi-unités.
6. **Équilibrage continu** à partir des parties réelles (`BalanceProbeTest`, journal de debug intégré).
