# SceneVibe OS — Media Spike 2.0.2 — clôture physique Sony / Prime

**Statut : PHYSICAL CAPABILITY PASS — Sony BRAVIA / Prime Video, périmètre expérimental uniquement.**
**Date des observations : 8 octobre 2026.**
**Nature de la preuve : déclarations de l'opérateur devant la télévision, et preuves CI distinctes.**

## 1. Identité immuable du candidat qualifié

| Élément | Référence |
| --- | --- |
| Dépôt | `pierre22400/scenevibe-tv-companion` |
| Branche | `experiment/scenevibe-audio-video-spike-002` |
| PR | [#17](https://github.com/pierre22400/scenevibe-tv-companion/pull/17) — OPEN / DRAFT / unmerged |
| **HEAD logiciel qui a produit l'APK physiquement testée** | **`27e09d529c1a55ab2ef5f861e63060943b634e35`** |
| Run de qualification logicielle | [37843069020](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37843069020) — SUCCESS |
| Archive APK CI | [11578213469](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37843069020/artifacts/11578213469) |
| SHA-256 APK | `a2578a12d21bcec963f1120bae78ce7bd2859f6385dcf7f6cc2f939c8170d41b` |
| package Android | `com.scenevibe.tvcompanionpoc.mediaexperiment` |
| APK versionCode / versionName | `8` / `0.2.2-remote-focus-safety` |
| Environnement physique | Sony BRAVIA Android TV 12, Prime Video natif |
| État production | SHADOW ; M6 `0.8.3-m6-phase-e` qualifié séparément par l'opérateur |

**Règle de provenance :** le SHA ci-dessus identifie les **octets logiciels de l'APK physique**. Le commit contenant le présent rapport documentaire est nécessairement postérieur ; **il n'est pas** le HEAD de l'APK testée. Ne pas substituer le HEAD documentaire à la preuve physique. Aucune installation du candidat documentaire sur Sony n'est revendiquée.

Deux fixtures intégrées à cette APK ont été vérifiées en CI :
- Voix MP3 `scenevibe_voice_10s.mp3` — 10,031 s, 160 958 octets, SHA-256 `1f23967de3e48ee7feda87d79abf97070a060b4a2101876db56c7972097d5d40`.
- Interlude H.264/AAC `scenevibe_interlude_10s.mp4` — 10,000 s, 2 146 888 octets, SHA-256 `c66ded0b6a2ea41ef34320a289f2c1ec37e2a87d34ef580d9ac45d08815682ec`.

Ces empreintes concernent le run 37843069020. L'archive GitHub Actions a une durée de rétention limitée : les SHA et URL sont ici la provenance conservée, non une promesse de disponibilité permanente du ZIP.

## 2. Contexte historique et anomalie télécommande

Le Spike 1.0 [documenté ici](media-interlude-poc-report.md) avait qualifié la pause contrôlée, l'interlude vidéo local et la reprise gardée, mais la sollicitation explicite `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` avait amené Prime à **PAUSE** au lieu de simplement baisser le volume : **AUDIO DUCK historique = FAIL**.

Le premier Spike 2.0.1 avait provoqué une anomalie de télécommande : certaines touches ne répondaient plus, et un reboot ADB avait été nécessaire pour restaurer un fonctionnement nominal. Son panneau contenait des boutons focusables et `requestFocus()`, mécanisme plausible sans preuve certaine de causalité OEM.

Le correctif 2.0.2 substitue une fenêtre de diagnostic `TYPE_APPLICATION_OVERLAY` avec `FLAG_NOT_FOCUSABLE` et `FLAG_NOT_TOUCHABLE`, sans boutons, et utilise des actions ADB explicites via l'Activity translucide de qualification. Il ne transforme pas ce mécanisme de pilotage debug en API de production.

## 3. Résultats physiques observés

| Gate | Observation transmise par l'opérateur | Verdict |
| --- | --- | --- |
| **R0.2** | Avec panneau passif affiché : Haut/Bas, Gauche/Droite, OK, Retour, Accueil et Volume ± restent opérationnels. | **PASS** |
| **R0.3** | Action `hide` : panneau disparu, télécommande nominale **sans reboot**. | **PASS** |
| **R0.4** | Prime reconnu comme `com.amazon.amazonvideo.livingroom` ; `playback state: PLAYING` et `notification access: granted`. L'image/le son continuent et la télécommande reste nominale. | **PASS** |
| **A1 — voix** | La voix du MP3 SceneVibe est audible ; le son original Prime reste audible simultanément avec un niveau réduit ; image et lecture Prime continues **sans PAUSE**. | **PASS physique** |
| **A1 — fin de voix** | Après les dix secondes, le volume initial de Prime **revient automatiquement**, sans action sur les boutons ni intervention de l'opérateur. État logiciel `COMPLETED_NEEDS_PHYSICAL_PROOF`. | **PASS physique** |
| **A2 — interlude MP4** | Prime PAUSE ; affichage d'une vidéo réellement animée avec tonalité audible, ≈10 s ; Prime reprend **automatiquement à la même position** ; télécommande nominale. | **PASS physique** |
| **A2 — répétabilité** | L'opérateur indique avoir **répété quatre fois** l'interlude avec le même comportement nominal. Le nombre total d'exécutions distinctes n'est pas inféré. | **PASS sur quatre répétitions rapportées** |

La dernière observation ne représente pas quatre captures indépendantes de logs Android. Les termes « bonne position » et « volume initial » sont des constats humains, **pas** une mesure de frames, de LUFS, de timestamps ou une preuve de politique audio spécifique.

**Verdict final du Spike 2.0.2 :** `R0 = PASS ; A1 = PASS ; A2 = PASS ; A2_REPEATED = PASS` **sur la seule configuration Sony BRAVIA / Prime native testée**.

## 4. Ce que la preuve permet — et ce qu'elle ne permet pas

### Démontré physiquement
- Coexistence auditive d'une voix locale et de l'audio Prime alors que le flux vidéo natif progresse.
- Atténuation temporaire de Prime pendant la voix et retour automatique au volume d'origine perçu après cette voix.
- Chemin d'interlude MP4 avec pause native, vraie vidéo et audio locaux, puis reprise au point d'arrêt perçu.
- Pilotage de qualification qui n'intercepte plus la télécommande dans les gates R0, avec nettoyage `hide` testé avant A1/A2.

### Ne pas inférer
- L'atténuation n'est **pas** une preuve que l'application demande explicitement AudioFocus : le `VoiceCoexistenceProbe` ne demande aucun focus. La cause (politique OEM, mixage audio, interactions Android) reste inconnue.
- Aucun PASS sur Netflix, Disney+, YouTube, Google TV, d'autres modèles Sony ni d'autres fabricants.
- Aucun PASS sur les sources interrompues par publicité, les seeks pendant voix/interlude, les changements de session, le reboot pendant l'interlude, la perte de permissions, l'extinction d'écran ou la reprise après crash.
- Aucun niveau sonore mesuré, aucun arrêt d'urgence durant l'interlude physiquement observé, et aucune confirmation distincte de `hide` **après** la série des quatre répétitions (R0.3 `hide` a été testé avant).
- Aucune preuve que la fonctionnalité est conforme aux conditions contractuelles de chaque plateforme ou prête pour une diffusion produit.
- L'ancien `AUDIO DUCK` explicite demeure **FAIL historique** ; il ne devient pas PASS par l'observation A1.

## 5. Registre des preuves et chaîne de contrôle

- [Run GitHub Actions 37843069020](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37843069020) : compilation, tests JVM, lint, guard focus, existence des deux fixtures dans l'APK, isolement des octets de production — SUCCESS.
- PR #17 : [rapport gate R0](https://github.com/pierre22400/scenevibe-tv-companion/pull/17#issuecomment-6069027888), [clôture A1 volume](https://github.com/pierre22400/scenevibe-tv-companion/pull/17#issuecomment-6069068813), [première observation A2](https://github.com/pierre22400/scenevibe-tv-companion/pull/17#issuecomment-6069117528), [répétabilité A2](https://github.com/pierre22400/scenevibe-tv-companion/pull/17#issuecomment-6069133066).
- [Protocole opérateur / correctif focus](audio-video-spike-2-operator.md).
- La qualification physique distincte M6 `0.8.3-m6-phase-e` reste indépendante, sans nouvel ACK, envoi, appairage, écriture Neon, merge ni déploiement production généré par ce Spike.

## 6. Clôture et suites non incluses

**SPIKE 2.0.2 — CAPABILITY PROOF PHYSICALLY QUALIFIED, NO PRODUCT INTEGRATION.**

Conserver la PR #17 **OPEN / DRAFT / UNMERGED**, l'APK qualifiée et le HEAD exact ci-dessus. Ne pas fusionner le module `mediaexperiment` dans `app/`.

L'étape suivante est une **architecture d'intégration post-M6**, documentée dans [media-spike-2-post-m6-integration-work-order.md](media-spike-2-post-m6-integration-work-order.md). Elle doit être étudiée et auditée séparément avant tout changement de source productif ou de contrat Cloud.
