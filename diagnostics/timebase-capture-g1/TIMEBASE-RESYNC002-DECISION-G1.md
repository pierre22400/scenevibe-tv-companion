# TIMEBASE RESYNC 002 — Décision G1 proposée

1er novembre 2026 — Alex pour Pierre.

**Verdict : PASS SOFTWARE G1 sur les contrôles synthétiques exécutés.** Workflow41272817005 entièrement SUCCESS, au commit APK `33e59cf26e5f2b3c077e978c797b0b3b2834706e`. Les réserves d'injection de panne sont détaillées dans la matrice ; aucun résultat non exécuté n'est présenté comme PASS.

**Décision proposée : GO G2 SONY REQUESTED.** Ce libellé demande une prochaine décision propriétaire ; il ne vaut pas autorisation d'installation ou de capture physique. **G2 : NOT EXECUTED.**

## Réponses aux huit questions

| Question | Réponse étayée |
|---|---|
| Capturer les pixels de notre compteur ? | Oui sur les deux émulateurs : ROI avec quatre repères fabriqués ; aucun chiffre reconnu par OCR |
| Consentement normal Android ? | Oui, dialogue réel accepté/refusé ; aucun appop ou droit de capture octroyé par shell |
| Protections respectées ? | Oui pour les fixtures mixte et FLAG_SECURE sur les deux bancs ; pas une preuve sur la composition Netflix |
| Isoler la ROI sans conserver le reste ? | Oui : plein buffer système transitoire, copie ROI seule, effacement après calcul, aucune image exportée |
| Acquisition puis arrêt ? | Oui sur les chemins exercés : une image par contrôle positif/protégé, fermeture reçues=fermées ; annulation/révocation terminales |
| Android TV API31 ? | Oui : 7/7 tests natifs. API35 standard : 7/7 ; il ne s'agit pas d'une autre TV |
| Limites avant Sony ? | Firmware/consentement Sony, pixels Netflix, charge réelle et cas de panne non injectés restent inconnus. L'APK G1 ne capture que sa fixture |
| Conformité suffisante pour lancer G2 ? | Non pour un lancement automatique : l'examen d'admissibilité contractuelle reste à obtenir. Aucun avis juridique favorable ou accord Play inventé |

## Prochain mandat minimal, non exécuté

TIMEBASE RESYNC003 devra cadrer séparément l'évolution du garde de fixture et une seule tentative physique consentie. G1 ne permet pas de commencer à lire Netflix sous couvert de ses tests synthétiques. Après prérequis logiciels et conformité recevables, une tentative permettrait seulement de classer les pixels A/B/C/INCONCLUSIVE. Aucun OCR, enregistrement vidéo ou réseau ; aucun changement Prime/Companion/Studio/Cloud. Protocole dans TIMEBASE-RESYNC002-PREFLIGHT-G2-SONY.md.

Si les prérequis ne sont pas réunis, rester arrêté. Si la capture ordinaire de l'horloge échoue de manière concluante, ne pas changer d'API pour contourner les protections ; proposer le repli manuel sous un autre mandat. Un G2 positif ne serait pas un GO OCR G3 ou une intégration G4.

## Livraison et arrêt

PR38 maintenue Draft. Branche isolée, commits tracés, APK privée signée hors production, rapports, résultats et archive avec SHA-256. Aucun fichier qualifié modifié, aucune nouvelle qualification Sony ou publicitaire. Aucune installation Sony, mutation de service distant, fusion ou publication effectuée.

**STOP APRÈS LIVRAISON.** Aucune campagne physique ni évolution OCR/recalage ne démarre automatiquement.
