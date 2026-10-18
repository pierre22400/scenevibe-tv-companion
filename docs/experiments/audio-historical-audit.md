# SceneVibe OS — KIRO AUDIO 001 — Phase A — Audit historique audio

**Statut : AUDIT HISTORIQUE EN LECTURE SEULE — Phase A (audit + architecture), aucun développement.**
**Date : 18 octobre 2026.**
**Nature de la preuve : relecture de l'historique GitHub (branches, commits, fichiers, runs CI, commentaires de PR). Aucun essai physique nouveau n'a été conduit par cet audit.**

> **GO Phase A uniquement.** Ce document est un livrable documentaire. Aucun code Android n'a été écrit, aucun `SceneVibeAudioRenderer` n'a été créé, aucun build Gradle ni build signé n'a été lancé, le keystore / la ligne de référence M5 n'a pas été touché, aucune mutation du téléviseur Sony n'a été réalisée, aucune PR n'a été fusionnée ni créée. **STOP avant tout développement Android de la phase B.**

## 1. Références et portée

| Élément | Référence |
| --- | --- |
| Dépôt | `pierre22400/scenevibe-tv-companion` |
| Branche de travail de cet audit | `main` (base documentaire) ; relecture en lecture seule de `origin/*` |
| Dispositif physique de référence | Sony BRAVIA Android TV 12, Prime Video natif (`com.amazon.amazonvideo.livingroom`) |
| Package applicatif de production | `com.scenevibe.tvcompanionpoc` |
| Ligne de référence signée | `versionCode=15`, signature M5 stable |
| Commentaire TEXTE synchronisé | **PASS physique** (chemin TEXTE / overlay `TYPE_APPLICATION_OVERLAY`) |
| Commentaire AUDIO synchronisé | **NON encore qualifié physiquement** dans la ligne de référence signée |
| PR #27 (TV-FIRST 012, publication Android cumulative) | OPEN / DRAFT / UNMERGED — chemin TEXTE, l'AUDIO n'y est pas qualifié |

**Objet.** Inventorier chaque essai audio/média retrouvé dans l'historique GitHub pertinent (pas uniquement `main`), avec le tableau à six colonnes imposé par la section 3 du mandat KIRO AUDIO 001, en distinguant strictement les simulations JVM/CI des preuves physiques Sony. Les conclusions de cet audit alimentent l'architecture (FEAT-002) et le plan d'expérimentation audio-focus (FEAT-003).

**Portée des PASS.** Tout PASS physique énoncé ici est **strictement limité à la seule configuration Sony BRAVIA Android TV 12 / Prime Video natif testée**. Aucun PASS n'existe sur Netflix, Disney+, YouTube, Google TV, un autre modèle Sony ni un autre fabricant ; aucun PASS sur sources interrompues par publicité ; aucun niveau sonore (LUFS, frames, timestamps) n'a été mesuré.

## 2. Méthode et branches examinées (lecture seule)

Relecture `git show <branche>:<fichier>`, `git log`, `git diff` et `gh api` (REST uniquement ; GraphQL indisponible). Aucune écriture, aucun push, aucun merge. Branches et PR consultées :

- `origin/experiment/scenevibe-audio-video-spike-002` — PR #17 (OPEN/DRAFT/UNMERGED). Preuve physique AUDIO primaire (coexistence voix sans focus) + preuve vidéo A2.
- `origin/experiment/scenevibe-media-interlude-poc-001` — Spike 1.0 (interlude média, essais audio-focus/duck, échec M4A puis succès ADTS/AAC).
- Lignée E5 : `origin/audit/scenevibe-e5-l0-l1-work-036` (PR #18), `origin/qualification/scenevibe-e5-stable-work-037`, et `origin/work/scenevibe-e5-*` (035 native-l0-l1, 038 adb-trigger, 039 stale-position, 040 native-admission, 041 selection-diagnostics, 042 foreground).
- Origines du moteur de synchro : `origin/work/mediasession-probe-001`, `origin/work/mediasession-finaltrack-scheduler-001`, `origin/work/finaltrack-media-sender-001`.
- `origin/work/scenevibe-os-m5-scene-event-calendar-001` (SceneEvent + MediaCalendar), `origin/work/tv-media-overlay-001`, `origin/work/tv-media-observation*` / `origin/work/scenevibe-tv-first-006-media-observation`, `origin/work/scenevibe-os-m6-banner-wall-architecture-001`.

Faits d'identité vérifiés en historique : le HEAD logiciel ayant produit l'APK physiquement testée du Spike 2.0.2 est `27e09d529c1a55ab2ef5f861e63060943b634e35` (résout bien sur `origin/experiment/scenevibe-audio-video-spike-002`) ; la base M5 est `17cbe36`.

## 3. Inventaire des essais (tableau mandaté — section 3)

| Référence | Dispositif | Fonction testée | Résultat | Preuve | Réutilisation |
| --- | --- | --- | --- | --- | --- |
| (a) `VoiceCoexistenceProbe` (voix MP3 locale 10 s, AUCUNE demande d'AudioFocus) — branche `origin/experiment/scenevibe-audio-video-spike-002`, APK HEAD `27e09d5` | Sony physique (BRAVIA Android TV 12 / Prime natif) | Coexistence auditive : voix locale audible pendant que l'audio Prime reste audible à volume réduit, sans PAUSE, puis retour automatique du volume Prime | **PASS physique** | `docs/experiments/media-spike-2-sony-physical-closure-2026-10-08.md` gate A1 ; APK SHA-256 `a2578a12d21bcec963f1120bae78ce7bd2859f6385dcf7f6cc2f939c8170d41b` ; run CI `37843069020` ; PR #17 commentaires (clôture A1 volume `issuecomment-6069068813`) | **Oui — architecture de référence** (chemin voix sans focus) |
| (b) `AndroidAudioFocusPort` — demande explicite `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` (`USAGE_ASSISTANT` / `CONTENT_TYPE_SPEECH`, sans `setStreamVolume`) | Sony physique (BRAVIA / Prime natif) | Ducking du flux Prime via demande explicite d'audio focus transitoire | **FAIL** (Prime passe en PAUSE au lieu de ducker, et ne reprend pas de lui-même) | `docs/experiments/media-interlude-poc-report.md` section K (Trial 5 : Prime pause=YES, reprise auto=NO) ; `media-spike-2-sony-physical-closure-2026-10-08.md` sections 2 et 4 (DUCK historique = FAIL, non reclassé par A1) | **Non — voie abandonnée** |
| (c) Interlude vidéo MP4 local — PAUSE possédée par SceneVibe puis PLAY gardé (A2) — `origin/experiment/scenevibe-media-interlude-poc-001` 0.1.4 + spike-2 A2 | Sony physique (BRAVIA / Prime natif) | Pause native possédée → vidéo locale plein écran avec audio → revalidation → PLAY garanti unique → PLAYING | **PASS physique** | `media-interlude-poc-report.md` section K (chemin interlude complet CLOSED/PASS, candidat `0.1.4-media-interlude-poc-audio-adts`) ; `media-spike-2-...closure` gate A2 (+ A2 répété 4 fois) | **À vérifier** — politique de transport DIFFÉRENTE (contrôle de transport assumé), hors coexistence audio ; pertinent pour la matrice d'architecture |
| (d) Fixture audio Sony/MediaTek — conteneur M4A puis remux ADTS/AAC — `origin/experiment/scenevibe-media-interlude-poc-001` | Sony physique (BRAVIA / Prime natif, décodeur MediaTek) | Décodage de la fixture vocale locale (M4A vs ADTS/AAC) | **M4A = FAIL / ADTS = PASS** | `media-interlude-poc-report.md` sections J/K : `Incompatible brand: M4A`, `Invalid chunk size: 4`, `parseMP4Chunk error(-1007)`, `MediaPlayerNative error (1, -2147483648)`, puis `LOCAL_AUDIO_MATERIALIZED` ; mêmes AAC-LC access units remuxés en ADTS = succès | **Oui — contrainte codec : préférer AAC/ADTS**, proscrire le conteneur M4A sur cette chaîne |
| (e) Suite JVM `:mediaexperiment` (`InterludeRuntimeTest`, `SafeResumeGuardTest`, `InterludeStateMachineTest`, `SessionCatalogTest`, `VoiceCoexistenceProbeTest`, 63 tests) | JVM / CI | Logique de décision, câblage des ports, machine à états, garde de reprise | **PASS logiciel UNIQUEMENT** (logique/câblage ; ne qualifie PAS l'audibilité) | runs CI `37843069020` / `37528995979` ; cœur Java sans Android, `VoiceCoexistenceProbe` sans `AudioFocusPort` ni `MediaControlPort` | **À vérifier** — ne prouve ni l'audibilité, ni le vrai `MediaController` Prime, ni le foreground OEM, ni la reprise réelle |
| (f) Lignée E5 interlude natif avec transport **SIMULATED_NATIVE** (WORK 035/036 audit, PR #18) — `origin/audit/scenevibe-e5-l0-l1-work-036`, HEAD candidat `47f675db` | JVM/CI + émulateur API 31/35 (transport SIMULATED_NATIVE) | Admission native, régie unique, garde de reprise, durabilité d'installation, différentiel scheduler | **PASS logiciel / NON concluant physiquement pour l'audio** | `scenevibe-e5-work-036-independent-audit.md` (verdict « PASS — READY FOR OWNER SONY QUALIFICATION DECISION » ; énonce explicitement que SIMULATED_NATIVE **ne qualifie pas** l'audibilité, le `MediaController` Prime, le foreground OEM, la télécommande ni la reprise réelle) | **À vérifier** — aptitude à une qualification Sony/API 31 ultérieure, pas une preuve audio physique ; intermittence API 35 non résolue (NON-BLOCKER consigné) |
| (g) Incident télécommande 0.2.1 (overlay focusable + `requestFocus()` → touches Sony non répondantes, reboot ADB requis) et correctif 0.2.2 (`FLAG_NOT_FOCUSABLE` + `FLAG_NOT_TOUCHABLE`, sans boutons) | Sony physique (BRAVIA Android TV 12) | Innocuité de l'overlay de diagnostic vis-à-vis de la télécommande native | **Incident 0.2.1 = FAIL / correctif 0.2.2 R0 = PASS** | `docs/experiments/audio-video-spike-2-operator.md` (correctif focus télécommande, incident du 8 oct. 2026) ; `media-spike-2-...closure` gates R0.2 / R0.3 (télécommande nominale, `hide` sans reboot) | **Oui — contrainte UX : overlay passif, non focusable, non touchable** |
| (h) `MediaSessionProbe` — échantillonnage passif en lecture seule de la session Prime | Sony physique (BRAVIA / Prime natif) | Observation passive : Prime reconnu `com.amazon.amazonvideo.livingroom`, `playback state: PLAYING`, `notification access: granted` ; n'émet JAMAIS de transport | **PASS** | `media-spike-2-...closure` gate R0.4 ; source `MediaSessionProbe.java` (sampler passif 1 s, lecture seule) | **Oui — brique de synchro réutilisée** (autorité temporelle fondée sur la position média, à réutiliser pour l'AUDIO) |

## 4. Simulation JVM vs preuve physique

Conformément à la section 3 du mandat, **aucun résultat JVM, instrumenté ou SIMULATED_NATIVE n'est présenté comme une preuve physique**. Classification sans ambiguïté :

- **Lignes JVM / simulation uniquement :** (e) suite JVM `:mediaexperiment` et (f) lignée E5 SIMULATED_NATIVE. Ces lignes établissent la logique, le câblage et la non-régression logicielle ; elles **ne qualifient pas** l'audibilité, le vrai `MediaController` Prime, le foreground OEM, la télécommande Sony ni la reprise réelle.
- **Lignes Sony physiques :** (a), (b), (c), (d), (g), (h). Elles reposent sur des observations oculaires/auditives de l'opérateur devant le téléviseur, non sur des mesures instrumentées.

Points de vigilance explicites :

- L'état logiciel `COMPLETED_NEEDS_PHYSICAL_PROOF` de `VoiceCoexistenceProbe` est un **état mécanique** signifiant seulement que le logiciel a vu PLAYING stable jusqu'à la fin locale. Ce n'est **pas** une preuve que le son était audible ni que l'image avançait.
- La cause du ducking observé en A1 (ligne a) est **INCONNUE** : `VoiceCoexistenceProbe` ne demande AUCUN focus. L'atténuation observée **n'est pas** prouvée pilotée par l'audio focus ; la cause (politique OEM, mixage audio, interactions Android) reste indéterminée.
- Un succès CI (vert) n'est **jamais** une preuve que Sony a mixé le son : il atteste une exécution logicielle reproductible sur un run donné.

## 5. Provenance et chaîne de preuve

Règle d'identité immuable reprise de la clôture du Spike 2.0.2 :

- Le **SHA-256 de l'APK** (`a2578a12d21bcec963f1120bae78ce7bd2859f6385dcf7f6cc2f939c8170d41b`) identifie les **octets logiciels réellement testés physiquement**. Le HEAD logiciel correspondant est `27e09d5`.
- Le **HEAD documentaire** (le commit contenant un rapport, y compris le présent audit) est **nécessairement postérieur** et **n'est PAS** le binaire testé. Ne jamais substituer un HEAD documentaire à la preuve physique.
- La **rétention des artefacts GitHub Actions est limitée** : le SHA-256 et les URL de run (`37843069020`) constituent la provenance conservée, non une promesse de disponibilité permanente du ZIP.

## 6. Verdict d'audit

**Existe-t-il une preuve physique AUDIO antérieure ?**

- **OUI** — pour la **coexistence vocale SANS demande d'audio focus** : ligne (a), A1 = **PASS physique**, strictement sur **la seule configuration Sony BRAVIA Android TV 12 / Prime Video natif** testée. La voix locale est audible pendant que l'audio Prime reste audible à volume réduit, sans PAUSE, avec retour automatique du volume. La cause du mixage reste inconnue.
- **NON** — pour le **ducking par demande explicite d'audio focus** : ligne (b), `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` = **FAIL historique** (Prime passe en PAUSE sans reprise automatique). Ce FAIL n'est pas reclassé par l'observation A1.

**Ne pas redévelopper (section 3 : « ne pas redévelopper une fonctionnalité déjà démontrée opérationnelle ») :**

- **Réutiliser** le chemin « voix locale SANS demande d'audio focus » (a) comme architecture de référence de la coexistence audio, ainsi que les briques de synchro passives (h) `MediaSessionProbe` et le moteur piloté par la position média.
- **Ne pas réutiliser** le chemin `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` (b) : voie démontrée en échec sur ce Sony/Prime.

**Portée stricte de tous les PASS :** uniquement Sony BRAVIA Android TV 12 / Prime Video natif. Aucun PASS sur Netflix, Disney+, YouTube, Google TV, un autre modèle/fabricant, des sources interrompues par publicité, ni avec niveaux sonores mesurés. L'AUDIO synchronisé **n'est pas qualifié** dans la ligne de référence signée M5 (`versionCode=15`) ; PR #27 (chemin TEXTE) le confirme.

> **FIN DE L'AUDIT HISTORIQUE — Phase A.** Les livrables suivants (architecture audio, plan d'expérimentation audio-focus Sony) sont traités séparément. **STOP avant tout développement Android de la phase B** jusqu'à validation du choix technique de gestion de l'audio focus par le propriétaire.
