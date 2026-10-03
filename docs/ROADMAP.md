# Feuille de route

La tranche verticale pose le moteur. Les étapes suivantes s'appuient dessus sans le réécrire.

## Prochaines étapes (ordre conseillé)

1. **Validation Android** : compiler `:android`, tester sur appareil (cycle pause/reprise,
   WorkManager, permission de notification, densités d'écran, performance de la carte).
2. **Armée jouable** : ordres aux unités sur la carte (déplacement, défense, attaque, repli,
   patrouille), logistique par routes/ports/bases, consommation de munitions et carburant,
   production et achats, renseignement.
3. **Guerre** intégrée : coûts, pertes, opinion, sanctions, réfugiés, infrastructures détruites,
   cessez-le-feu et paix négociés via le moteur de diplomatie existant.
4. **Diplomatie étendue** : sanctions, alliances, ultimatums, droits de passage, garanties,
   médiation ; relations entre pays IA ; davantage de pays (données).
5. **Réformes** structurelles (retraites, travail, fiscalité) en plus des leviers budgétaires,
   avec promesses de campagne suivies par l'électorat.
6. **Territoires** : plus de demandes locales (écoles, logement, sécurité), présidents de région
   et de département actifs, élections locales influençant le climat politique.
7. **Carte fine** : tuiles chargées à la demande (communes, routes détaillées, zones
   industrielles, agriculture), légende des cartes de chaleur.
8. **Contenu** : plus d'événements (épidémie, pénurie, attentat, faillite, accident militaire...),
   plus de variantes de dialogue, portraits générés.
9. **Seconde nationalité jouable** : ajouter un pays en `detail: FULL` avec ses fichiers
   (territoire, gouvernement, groupes sociaux, élections) — le moteur ne contient aucune valeur
   propre à la France.

## Dette technique connue

* Migrations de sauvegarde : le cadre existe, aucune migration n'est encore nécessaire (format 1).
* L'interface reconstruit les panneaux toutes les 3 s ; à optimiser si des panneaux deviennent lourds.
* Les tracés de réseaux (LGV, autoroutes) sont schématiques (segments entre villes).
