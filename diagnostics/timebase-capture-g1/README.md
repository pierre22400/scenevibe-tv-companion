# SceneVibe TIMEBASE G1

PRIVATE DIAGNOSTIC — NOT FOR DISTRIBUTION. Application autonome `com.scenevibe.timebasecaptureg1`, minSdk31, target/compile35. Signature debug de diagnostic, distincte de M5 ; aucun secret de production. G1 capture seulement ses propres écrans synthétiques. Aucun OCR, réseau, audio, accessibilité ou commande de lecteur.

## Architecture et limites

Préparation volontaire D-pad → validation de ROI normalisée → consentement système réel → service de premier plan → délai borné → une projection ImageReader → copie ROI → preuve scalaire → libération. Une session reçoit éventuellement des buffers d'amorçage, sans enregistrement vidéo. Quitter la fixture ferme la session ; ce garde G1 empêche toute utilisation comme capture Netflix. Une évolution G2 demanderait un autre mandat.

Les quatre marqueurs colorés sont fabriqués et connus. Leur comparaison ne reconnaît pas les chiffres `01:36`. Contrôle mixte : SurfaceView sécurisée distincte sous une vue ordinaire. Contrôle protégé : FLAG_SECURE sur toute la fenêtre. Un résultat ROI_NOT_VISIBLE décrit seulement le motif absent de l'image synthétique.

Un seul worker, ImageReader maxImages=2, délai500–5000ms, armement≤15s, acquisition≤10s, ressources fermées sur arrêt/révocation/erreur. Aucun jeton persistant, aucune reprise de processus. Dimensions≤4096, formatRGBA, pixel/row stride validés. Le plein buffer système existe transitoirement ; seul le ROI est copié. Les copies sont écrasées après comptage, aucune image n'est stockée. Journal privé UTF-8 sans BOM borné à1Mo, suppression volontaire. Horloges requestElapsed/requestUptime/wall séparées ; la base du timestamp Image n'est pas supposée.

## Qualification reproductible

JDK17, Gradle8.9, AGP8.7.3, AndroidSDK35/build-tools35.0.0. Le projet n'inclut pas le build Companion.

```bash
bash diagnostics/timebase-capture-g1/tests/core-checks.sh
python3 -B -m unittest discover -s diagnostics/timebase-capture-g1/tests -v
gradle -p diagnostics/timebase-capture-g1 :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Le workflow dédié est l'unique ajout hors du répertoire G1 : nécessaire à la compilation et aux bancs émulés, il ne modifie aucun workflow historique. Bancs : Android TV API31 et Android35 standard (ce dernier n'est pas Android TV). Le script de banc rejette les appareils physiques avant installation. Son UI automation ne manipule que notre app et le dialogue système de consentement.

Les tests JVM et les doubles ne démontrent pas MediaProjection. Seuls les résultats ImageReader instrumentés peuvent qualifier la capture de fixture. Aucun résultat n'est une preuve Sony/Netflix. G2 : NOT EXECUTED. Aucun lancement G2, fusion, déploiement ou publication autorisé.

## Livraison qualifiée, 1er novembre 2026

Code/APK : `33e59cf26e5f2b3c077e978c797b0b3b2834706e`. Workflow final [41272817005](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/41272817005) SUCCESS. 47 invariants portables, 6 contrats et 7/7 tests réels par banc API31/API35. Les limites d'injection de panne restent explicites dans la matrice.

- [Rapport G1](TIMEBASE-RESYNC002-RAPPORT-G1.md)
- [Matrice de sécurité](TIMEBASE-RESYNC002-MATRICE-SECURITE.md)
- [Reproduction](TIMEBASE-RESYNC002-REPRODUCTION.md)
- [Préflight G2 non exécuté](TIMEBASE-RESYNC002-PREFLIGHT-G2-SONY.md)
- [Décision proposée](TIMEBASE-RESYNC002-DECISION-G1.md)
