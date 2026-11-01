# TIMEBASE RESYNC 003 — Admissibilité G2

Alex — 1er novembre 2026. **LEGAL REVIEW REQUIRED** pour un futur essai Netflix. Le présent développement et ses essais synthétiques ne constituent pas une autorisation de capture tierce.

## Sources examinées

Les cinq rapports G1 (qualification, sécurité, reproduction, préflight et décision) et les quatre documents TIMEBASE001 (faisabilité, protocole, risques et architecture) ont été lus intégralement avant développement. Le nom d'archive G1 annoncé dans le mandat diffère du fichier effectivement livré : `scenevibe-timebase-resync002-g1-delivery.zip`, SHA-256 `ce9c6330de8493e2fabcd7f4926b959ed256a52661a953f0e492822f01635063`. Ce constat de nom ne change pas les références de code qualifié.

Sources primaires consultées le 1er novembre 2026 :

- [Android MediaProjection](https://developer.android.com/media/grow/media-projection) : consentement par session, VirtualDisplay, ImageReader, service de premier plan et contraintes Android 14+.
- [Conditions Netflix, URL du centre d'aide français](https://help.netflix.com/fr/legal/termsofuse), notamment 1.8 : restrictions de reproduction de l'interface, protections techniques, collecte automatisée et extraction. La réponse consultée est en anglais malgré le chemin `/fr/`. L'applicabilité territoriale et la qualification juridique précise du compteur d'interface doivent être vérifiées ; cette URL ne prouve pas les conditions du compte de Pierre.
- [Google Play — Device and Network Abuse](https://support.google.com/googleplay/android-developer/answer/16559646) : accès autorisé aux autres services, respect de FLAG_SECURE et absence d'interférence publicitaire. Aucune soumission Play n'a été effectuée.

## Questions et statut

| Question | Statut | Portée |
|---|---|---|
| API ordinaire MediaProjection et consentement visible | Résolue techniquement | Ce consentement autorise la projection Android ; il ne tranche pas les droits contractuels du service tiers. |
| Display entier transitoire puis ROI | Résolue par conception | L'API ne capture pas uniquement la ROI. Le buffer entier existe en RAM ; seule la ROI est copiée. |
| Fenêtre / surface secure | Résolue sur contrôles possédés, sous réserve des résultats logiciels | Aucun contournement ni API de repli. Une zone noire ne prouve pas à elle seule son origine. |
| DRM Netflix et composition Sony | Non déterminée | Aucun test Netflix/Sony, aucune extraction ou interception. |
| Autorisation de capture ponctuelle d'une horloge Netflix | Validation juridique requise | Aucune exemption expresse ni accord Netflix établi. Le caractère privé, bref et local ne suffit pas à conclure à une autorisation. |
| Conformité Play d'une future application | Non déterminée | APK privée de laboratoire. Les tests et la signature ne valent pas approbation Play. |
| Confidentialité | Bornée techniquement | Pas de réseau/audio/image persistante ; ROI en mémoire 30 s au plus et inspection secure après clôture. Android possède ses propres buffers et politiques mémoire, dont l'effacement physique n'est pas attesté par une application ordinaire. |
| Usage physique dans cette mission | Bloqué par le mandat | NOT EXECUTED, quel que soit le résultat logiciel. |

## Conditions avant toute campagne future

Obtenir un examen favorable des conditions applicables au compte et au territoire, des droits portant sur l'interface et de l'objectif strictement expérimental. Si cet examen conclut à une interdiction, ne pas effectuer l'essai. Ne pas contourner une protection, demander un privilège ou essayer automatiquement une autre API. Puis obtenir un GO physique explicite de Pierre et vérifier l'APK exacte et le protocole.

Le consentement système, la case de déclaration humaine de l'instrument et le GO du propriétaire sont trois éléments différents. La case de l'application ne prouve ni une autorisation juridique ni un nouveau mandat.
