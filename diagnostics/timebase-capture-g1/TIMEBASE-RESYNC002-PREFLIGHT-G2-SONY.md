# TIMEBASE RESYNC 002 — Préflight G2 Sony, non exécuté

1er novembre 2026 — Alex pour Pierre. **G2 NOT EXECUTED. Aucune instruction d'installation Sony exécutable ici.** Ce texte prépare une éventuelle décision distincte TIMEBASE RESYNC003 ; il n'étend pas le GO G1.

## Prérequis cumulés

1. G1 qualifié par captures réelles de fixture, protections respectées, fermeture et journaux vérifiables, APK/commit/signature exacts.
2. GO propriétaire explicite pour un nouveau mandat physique ; aucune permission permanente déduite de cette livraison.
3. Examen recevable du cadre de la capture ponctuelle Netflix. Les rapports TIMEBASE001 ne donnent ni avis juridique favorable définitif ni acceptation Play ; ces réserves restent ouvertes.
4. Instrument G2 distinctement cadré. **L'APK G1 est bornée aux fixtures possédées et ne peut pas être utilisée telle quelle pour capturer Netflix.** Retirer ce garde ne fait pas partie de cette mission.

## Expérience future minimale proposée

Après les prérequis seulement, préparation volontaire et consentement système réel avant Netflix ; une tentative ponctuelle différée, sans Activity/overlay de diagnostic pendant le lecteur. Pierre affiche et interprète lui-même une horloge de programme en pause, sans déplacer un curseur de preview. Le diagnostic ne commande aucune pause, touche, reprise ou SEEK. Aucun OCR, son, vidéo enregistrée, réseau, capture permanente ou tentative supplémentaire automatique.

La session d'acquisition doit rester bornée à10s après déclenchement, avec arrêt/révocation. L'API31 acquiert potentiellement le display entier en RAM ; le consentement doit le dire. Extraire uniquement le ROI préannoncé, détruire le plein buffer, conserver le ROI au plus30s pour une inspection locale volontaire, pas de fichier image par défaut. Journaliser des scalaires techniques et le constat humain séparément. Aucun hash stable de contenu ou titre extrait.

| Issue future | Définition bornée | Suite |
|---|---|---|
| A | Compteur visible physiquement et pixels utiles reçus par API ordinaire | Acquisition démontrée pour cette combinaison seulement ; G3 nécessite autre décision |
| B | Instrument sain, image valide, compteur visible physiquement mais absent du buffer | STOP de cette voie ; pas d'essai pour neutraliser secure |
| C | Permission inaccessible/refusée, projection refusée ou révoquée | Arrêt propre, aucune autre méthode de privilège |
| INCONCLUSIVE | Mauvaise ROI, image d'amorçage, résolution inadéquate, timeline disparue ou contexte non établi | Rapporter l'incertitude et arrêter |

Toute perturbation du lecteur, conservation hors consentement, donnée privée ou nécessité de contournement impose STOP. Une réussite G1 sur émulateur ne démontre pas le firmware Sony, le consentement à la télécommande Sony ni la composition du lecteur Netflix.

## Conformité et prochaines limites

MediaProjection fournit un mécanisme public, pas une autorisation de lire des couches protégées ou une dérogation aux contrats streaming. Respecter FLAG_SECURE et les surfaces sécurisées. Ne pas utiliser AccessibilityService.takeScreenshot comme repli à un refus ; aucune nouvelle accessibilité ni backend ADB/root/privilegié.

Les questions contractuelles identifiées dans TIMEBASE001 restent à apprécier avant le test Netflix. Ce rapport n'en invente pas une résolution. Si l'examen est défavorable ou indéterminé, aucun lancement physique ; proposer le repli manuel par téléphone dans un mandat distinct.

Même un G2 positif n'autoriserait ni G3 OCR ni G4 recalage, pas plus qu'une admission Netflix. Companion/Prime/Studio/FinalTracks/Cloud restent inchangés. **STOP après G1 et sa livraison.**
