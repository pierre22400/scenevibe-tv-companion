# CLOUD → ANDROID TV 002 — CLOSED / PASS

Le 26 octobre 2026, Pierre a qualifié physiquement le Companion v17 sur Sony
BRAVIA Android TV pendant Prime Video. Le parcours privé Studio → API Vercel →
Neon/R2 → association compte/appareil → téléchargement HTTPS → cache Android →
OverlayService → TEXT + AUDIO est acquis. Cette qualification n'est pas relancée
pour préparer la PR #30.

## Références du jalon physique

- TV : `e9e676b27e1235fa0a237c36ab12380319faee57`, package
  `com.scenevibe.tvcompanionpoc`, versionCode 17,
  `0.8.2-tv-release-hardening-cloud-android-tv002`.
- Cloud : `pierre22400/interface-scenevibe@6502fcfa5a4e78e7c8c86ef4d00b4ea4f891130e`.
- Track : Columbo — Eaux troubles, 16 TEXT réels et trois MP3 synthétiques de trois
  secondes à 01:30, 05:00 et 15:00 ; calendrier commun de 19 événements.
- Candidat signé : SHA-256
  `a90b67532e0863d04f60c938e7c0ecf346ad03dc056bd8f8b01a63babb523041`.
- Certificat M5 du candidat : SHA-256
  `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`,
  signatures v2/v3. Les octets installés sur Sony n'ont pas été extraits pour
  comparaison ; le lancement de v17 est une observation du propriétaire.
- Dossier de clôture : `SCENEVIBE-CLOUD-ANDROID-TV-002-CLOTURE.md` dans
  `scenevibe-cloud-android-tv-002-cloture.zip`, livré le 27 octobre 2026.

## Preuves distinctes

Le propriétaire confirme l'association, le catalogue, le téléchargement privé,
le cache vérifié et sa relecture, les autorisations Android, les TEXT visibles,
les trois MP3 entendus et leur relecture après déplacement temporel. Les deux
journaux Sony se chevauchent : trois démarrages AUDIO uniques aux positions
90 481, 300 526 et 900 519 ms, et six demandes TEXT uniques. Ils ne constituent
pas une preuve automatique d'affichage des seize TEXT sur Sony, ni une mesure de
latence acoustique. Les périodes SESSION_UNAVAILABLE, CAPTURE_TIMEOUT et
MEDIA_CLOCK_UNUSABLE restent documentées ; la continuité parfaite sur tout le
film n'est pas revendiquée.

Les campagnes antérieures [staging réel](https://github.com/pierre22400/interface-scenevibe/actions/runs/40383849383)
et [banc local](https://github.com/pierre22400/interface-scenevibe/actions/runs/40383849512)
ont exécuté les mêmes six scénarios Android : vrais boutons du Companion,
OverlayService, cache privé, contrôles et redémarrage hors réseau sans démarrage
automatique. L'horloge de ces essais appartient à SceneVibe ; ils ne sont pas les
essais physiques Prime. Le staging a également persisté deux reçus
CACHE_CONFIRMED relus après reconnexion. Les résultats de ce jalon sont 41 tests
Cloud locaux et 1 306 JVM réussis, avec un test Sony optionnel ignoré.

## Fonctionnement et limite publicitaire acceptée

Le service conserve SceneRenderer, AndroidLocalAudioPort, le calendrier M5 et une
seule sonde MediaSession passive. Les contrôles TEXT/AUDIO sont indépendants.
Les démarrages différés revalident le permis ; changer le cache, la génération ou
le consentement le révoque. Restaurer le cache ne démarre aucun commentaire et
aucune commande n'est envoyée à Prime.

Pierre a entendu le MP3 de 15:00 pendant une publicité Prime. Cette observation
illustre l'autonomie de restitution SceneVibe vis-à-vis du contenu affiché, sans
prouver une indépendance complète de l'horloge Android MEDIA. C'est une limite
acceptée : aucune correction, détection de publicité ou modification de
synchronisation n'est demandée. Aucune publicité SceneVibe n'est insérée pendant
l'œuvre. Le chantier AD INTERRUPTION reste fermé.

Le mapping compare les empreintes du snapshot Studio, du runtime TEXT et du
manifeste qualifiés ; les champs éditoriaux UNKNOWN restent inchangés. Chaque
départ exige la confirmation locale de l'édition de référence. Cette confirmation
reste en mémoire et ne constitue pas une preuve juridique.

## Vérifications d'intégration de la PR

- `Android debug APK` compile/lint le code courant et exécute tous les tests JVM
  debug, les quinze frontières Python actuelles (permissions, composants,
  transport et MEDIA passif), puis le compteur M4/M5. Celui-ci exige toujours
  chaque suite historique avec son nombre exact de cas, les fixtures figées,
  les 82 comparaisons brutes et l'absence de nouveau skip ; il accepte les suites
  supplémentaires des fonctionnalités ultérieures.
- Les anciens gates TV-FIRST 003/006 et assertions Python de provenance figée
  restent inchangés. Ils portent sur leurs arbres historiques exacts et ne sont
  plus utilisés comme autorisation de tout nouveau contenu dans `main`. Leur
  inventaire fermé rejetait déjà `main`, sans décrire une régression produit.
- `Cloud Android TV 002 Companion` compile/lint v17 et v15, exécute les tests du
  profil privé 008 sans fixture fournisseur, et compile séparément les trois
  familles natives 003-E, Cloud 001 et Cloud 002 sur leurs variantes respectives.
  Les tests requérant un vrai serveur ou le snapshot Studio privé ne sont pas
  lancés sans ces prérequis. Leurs preuves précédentes restent celles du jalon.
- Les bancs M4 de persistance disque/processus et M5 de comparaison différentielle
  restent actifs sur API 31 et 35. Chacun compile uniquement son runner et ses
  auxiliaires ; sa configuration est appliquée après le DSL de l'application.
  Le contrôle négatif M4 sur l'ancienne version est conservé.
- Les PR ne reçoivent aucun secret de signature dans les étapes de build. La
  signature optionnelle est réservée à un lancement manuel du workflow existant.

Les changements de préparation concernent les bancs, le build et la documentation,
pas les fonctions métier qualifiées. Aucun déploiement, changement Neon/R2,
installation Sony ou fusion dans main n'est effectué par cette préparation.
