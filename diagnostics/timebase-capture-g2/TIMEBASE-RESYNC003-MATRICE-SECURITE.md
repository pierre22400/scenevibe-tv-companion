# TIMEBASE RESYNC 003 — Matrice de sécurité

Résultats liés au workflow final 41286895277. Les preuves Android et les tests à données fabriquées restent distingués ci-dessous.

| Invariant | Mécanisme | Preuve attendue / limite |
|---|---|---|
| Consentement neuf, volontaire | Réservation génération avant dialogue ; consommation unique ; service non sticky | Refus réel et révocation réelle sur chaque banc ; ancien consentement refusé par tests de politique |
| Délai borné et départ volontaire | 500–60000 ms ; départ de notre Activity requis ; aucun suivi de package | Contrôle réel seconde Activity et maintien dans préparation négatif |
| Acquisition unique | Une création de VirtualDisplay ; une validation atomique de frame ; plafond 240 buffers / 10 s | Compteurs received/rejected/usefulFrames et imagesClosed |
| Amorçage / doublons | 250 ms init ; timestamps comparés seulement entre images ; génération courante | Tests de politique pour callbacks tardifs/génération ; capture réelle pour motif possédé |
| Fenêtre protégée | FLAG_SECURE propriétaire, aucune API de repli | Contrôles G1 réexécutés ; G2 ne transforme jamais le noir en verdict physique |
| Surface protégée distincte | SurfaceView.setSecure(true), UI ordinaire distincte | Contrôle mixte G1 réexécuté, G2 seconde Activity |
| ROI normalisée / formats | Validation finie, bornes display ≤4096, RGBA8888 et strides | Tests scalaires/buffers ; pas de coordonnées Netflix prédéfinies |
| Clôture native | Worker unique : Image.close, display.release, reader.close, projection.stop, stopSelf | Comptage des images et erreurs ; absence de reprise après arrêt |
| Inspection | Action humaine après clôture ; fenêtre ResultActivity secure | Contrôle réel ouverture/fermeture ; aucune Activity lancée par le service |
| Mémoire | Tableau ROI + Bitmap seulement ; effacement manuel/sortie/30 s ; état non sauvegardé | Expiration réelle et test de zéro du tableau ; pas de garantie forensique GPU/OS |
| Classification | Aucun A/B automatique ; labels humains et préfixe SIMULATED pour fixtures | Test de label synthétique ; C/INCONCLUSIVE sans ROI possibles après clôture |
| Confidentialité | Aucun Internet/audio/OCR/accessibility/stockage partagé ; journal technique fermé | Manifeste APK et tests statiques ; inventaire stockage privé sans images |
| Isolation | Projet / package / build / signature de diagnostic séparés | Empreintes des fichiers existants et diff GitHub depuis fe3a797 |
| Physique | Aucun accès Sony ni capture d'application tierce | NOT EXECUTED ; protocole documentaire et nouveau GO requis |

Aucune sécurité de Netflix ou Sony n'est qualifiée par cette matrice. Les tests d'images invalides, limites et générations sont des contrôles portables à données fabriquées, pas des captures de lecteurs tiers.

## Résultats définitifs

**PASS logiciel**, code `4213cb060bb77cd0cd3f986001861acd1ea3b987`, workflow `41286895277`. Android TV API31 et Android API35 standard : G1 7/7 + G2 9/9 sur chaque banc. Refus et révocation : réels ; seconde Activity et inspection : réels ; expiration 30 s et zéro du tableau : réels ; fenêtre secure : zéro marqueur ; mixte : UI ordinaire exploitable, surface protégée non exploitable dans le contrôle G1 réexécuté. Toutes les tentatives G2 journalisent zéro erreur de fermeture et autant d'Images fermées que reçues. Aucun FATAL AndroidRuntime observé dans le passage final.

Les scénarios buffers invalides, mauvaise génération, double armement et ancien consentement sont des tests portables à données fabriquées. Ils ne prouvent pas une injection native Android de ces pannes. Les protections Sony/Netflix ne sont pas testées. La matrice est qualifiée pour les contrôles possédés seulement ; **G2 physique NOT EXECUTED**, admissibilité **LEGAL REVIEW REQUIRED**.
