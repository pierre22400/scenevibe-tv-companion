# SceneVibe OS — KIRO AUDIO 001 — Phase A — Architecture de restitution audio

**Statut : PROPOSITION D'ARCHITECTURE — Phase A (audit + architecture), aucune implémentation.**
**Date : 18 octobre 2026.**
**Nature du document : conception/prose uniquement. Les types et interfaces décrits ici (`SceneVibeAudioRenderer`, `LocalAudioPort`, extension d'événement AUDIO) sont PROPOSÉS, PAS implémentés dans cette phase.**

> **GO Phase A uniquement.** La production reste en SHADOW/inchangée : aucun code Android n'a été écrit, aucun `SceneVibeAudioRenderer` n'a été créé, aucun build Gradle ni build signé n'a été lancé, la ligne de référence signée M5 (`versionCode=15`) et le keystore stable n'ont pas été touchés, aucune mutation du téléviseur Sony n'a été réalisée, aucune PR n'a été fusionnée ni créée. **STOP avant tout développement Android de la phase B.**

## 1. Portée et dépendances

Ce document traite la **section 5** du mandat KIRO AUDIO 001 (architecture attendue), la **section 7** (synchronisation), la **section 8** (interface utilisateur minimale) et rappelle la **section 10** (interdictions). Il s'appuie sur les conclusions de l'audit historique (`audio-historical-audit.md`, FEAT-001) :

- **Chemin de référence pour la coexistence audio** : la lecture d'une voix locale **SANS aucune demande d'audio focus** (`VoiceCoexistenceProbe`, A1 = **PASS physique** sur la seule configuration Sony BRAVIA Android TV 12 / Prime Video natif testée).
- **Voie proscrite** : la demande explicite `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` (`USAGE_ASSISTANT` / `CONTENT_TYPE_SPEECH`) qui a fait **passer Prime en PAUSE sans reprise automatique** (**FAIL historique**, non reclassé par A1).
- **Cause du mixage A1 : INCONNUE.** L'atténuation observée n'est pas prouvée pilotée par l'audio focus. Toute politique d'audio focus est donc un **RISQUE** à trancher par l'expérimentation Sony (`audio-focus-experiment-plan.md`, FEAT-003), pas une hypothèse d'architecture.

Les faits de code cités ci-dessous ont été relus sur la branche `main` (sources réelles de `app/`).

## 2. Réutiliser le moteur de synchronisation (section 5.1)

L'AUDIO réutilise **tel quel** le moteur de synchronisation piloté par la position média déjà présent sur `main`. Aucune brique n'est dupliquée, aucune horloge n'est ajoutée.

### 2.1. Briques réutilisées (telles que trouvées dans les sources)

- **`MediaSessionProbe`** — échantillonneur **passif, en lecture seule, à 1 s**. Il n'envoie **JAMAIS** de commande de transport ; il émet seulement un `Snapshot {packageName, state, positionMs, estimatedPositionMs, mediaId, title, ...}`. C'est la source d'observation de la position et de l'état de lecture Prime.
- **`MediaSyncedTrackScheduler`** (et son jumeau calendrier **`MediaCalendarScheduler`**) — **seule autorité temporelle**, pilotée par la **position média** (jamais par une horloge murale). Comportements vérifiés dans le code :
  - Position retenue = `estimatedPositionMs` si `>= 0`, sinon `positionMs`.
  - **Seek avant** : `deltaMs > FORWARD_SEEK_THRESHOLD_MS` (**5000 ms**) → `skipThrough(position)` **consomme** tous les commentaires dont `startMs <= position`, puis `expireElapsed`.
  - **Seek arrière** : `deltaMs < -BACKWARD_SEEK_THRESHOLD_MS` (**2000 ms**) → `rearmFrom(position)` **réarme** (dé-consomme) les commentaires futurs et expire toute fenêtre visible en attente pour éviter les doublons.
  - **Coupe « trop tard »** : `skipTooOld` consomme sans rendre tout commentaire dont `startMs < position - MAX_LATE_MS` (**2000 ms**).
  - **Les fenêtres n'avancent que sur progression de la position EN LECTURE** : `renderDue` / `expireElapsed` ne sont appelés que lorsque `state == STATE_PLAYING` et que la position progresse. **Une pause fige donc naturellement la fenêtre** (la position n'avance plus → aucune expiration).
  - Non-concordance d'identité média → `deactivate(...)` qui vide les fenêtres et émet `onEligibility(false)`.
- **Identification du média** — `MediaIdentityMatcher.matches(track, snapshot)` : le scheduler n'accepte l'horloge que si la session active correspond exactement à l'identité média de la Track (`MediaIdentity {platform, videoId, title, durationMs}`).
- **`SceneRuntimeController`** — la **régie** JVM-testable. Son javadoc est explicite : elle **« owns NO clock »**, **« never creates a `Handler`/`postDelayed` timer and never re-derives media position »**, et **« NEVER commands the video player »**. Elle ne dessine rien elle-même : tout passe par le seam `SceneSink` (`preflight` / `show` / `hide` / `hideAll`).
- **Cache / affectations existants** et **mécanismes de pause, reprise et seek** : réutilisés sans modification, puisqu'ils sont déjà entièrement portés par le scheduler et la régie.

### 2.2. Callbacks de synchronisation partagés

L'AUDIO se branche sur **les mêmes callbacks** que le TEXTE, sans en créer de nouveaux :

| Callback (`MediaSyncedTrackScheduler.Listener` / `MediaCalendarScheduler.Sink`) | Rôle pour l'AUDIO |
| --- | --- |
| `onRender(Event)` / `onDue(token, eventId)` | Déclenche la préparation + le départ d'un commentaire audio **au même instant média** que le rendu TEXTE. |
| `onExpire(Event)` / `onExpire(token, eventId)` | Fin de la fenêtre média du commentaire → arrêt propre de la restitution audio. |
| `onEligibility(boolean)` / `onEligibility(token, eligible)` | `false` → arrêt sûr immédiat, aucune restitution résiduelle. |
| `onPlayback(playing, pauseFreezesDisplay)` / `onPlayback(token, playing, freezeOnPause)` | Transmet l'état PLAYING/paused observé ; applique la politique de pause (section 6). |

### 2.3. Contrainte dure : une seule horloge

> **Interdiction formelle de créer une seconde horloge concurrente pour les événements audio.** Un événement AUDIO **DOIT** être déclenché par la **même position média de référence** que TEXT, via **les mêmes callbacks** du scheduler. Le `SceneVibeAudioRenderer` ne possède **aucun** `Handler`, `postDelayed`, `Timer`, `Thread` de minutage ni compteur de position : il est purement **réactif** aux événements de la régie. Toute dérive temporelle propre à l'audio est proscrite — c'est précisément le mode d'échec que la régie `SceneRuntimeController` interdit déjà par conception.

## 3. Moteur de restitution audio distinct (section 5.2)

### 3.1. `SceneVibeAudioRenderer` — composant DESIGN-ONLY

Introduire un composant Android dédié, **distinct** de la régie et du scheduler, par exemple `SceneVibeAudioRenderer`, dans le module `app/` (voir section 7). Il n'est **pas** implémenté en Phase A. Ses six responsabilités, reprises mot pour mot du mandat :

1. **Préparer** une ressource audio **locale** (fichier embarqué/local, pas de capture d'un flux tiers).
2. **Déclencher** la lecture du commentaire.
3. **Arrêter ou interrompre** la lecture.
4. **Appliquer le volume SceneVibe** (gain propre à SceneVibe, voir section 7.3).
5. **Libérer** les ressources (préparation/arrêt déterministes, pas de fuite de `MediaPlayer`).
6. **Publier des diagnostics** (logs d'état : préparé, démarré, arrêté, erreur codec, etc.).

### 3.2. Patron de Ports (à refléter, conception uniquement)

Reproduire le patron de Ports déjà éprouvé dans `:mediaexperiment` (sur les branches d'expérimentation, pas sur `main`) :

- **Interface cœur sans Android** : `LocalAudioPort` (préparer / démarrer / arrêter / libérer / état), testable en JVM.
- **Adaptateur Android** : `AndroidLocalAudioPort`, s'appuyant sur `MediaPlayer` avec `AudioAttributes` `USAGE_ASSISTANT` / `CONTENT_TYPE_SPEECH`, et qui **n'appelle JAMAIS `setStreamVolume`** (le volume SceneVibe est un gain local, section 7.3).
- **Fake JVM** : implémentation de test enregistrant les appels, pour qualifier la logique sans matériel.

Le `SceneVibeAudioRenderer` orchestre ce port en réaction aux callbacks de la régie ; il ne porte aucune logique temporelle.

### 3.3. Aucun contrôle du lecteur Prime

> Le renderer audio **ne doit pas** prendre le contrôle du lecteur Prime Video. **Aucune** commande de transport (PAUSE / PLAY / SEEK), **aucun** `MediaController` actif, **aucune** demande d'audio focus présumée.

**Contraste avec l'interlude vidéo.** Le chemin interlude vidéo (A2, `media-interlude-poc`) possède **délibérément** le transport (PAUSE native possédée → vidéo locale plein écran → revalidation → PLAY garanti unique). C'est une **politique différente**, hors du périmètre de la **coexistence audio** : le commentaire audio SceneVibe doit **coexister** avec Prime qui **continue de jouer**, et non le piloter.

**Chemin de référence audio (report de l'audit FEAT-001).** La restitution audio suit le comportement `VoiceCoexistenceProbe` **SANS demande d'audio focus** (A1 = PASS physique), et **non** le ducking `MAY_DUCK` (FAIL historique). La politique d'audio focus éventuelle reste un **RISQUE ouvert** à régler par l'expérimentation Sony (FEAT-003), pas une hypothèse figée ici.

## 4. Analyse du contrat SceneEvent, PUIS extension minimale (section 5.3)

### 4.1. Analyse d'abord : le modèle existant peut-il représenter un AUDIO ?

Relecture des sources sur `main` :

- **`calendar/SceneEvent.java`** porte **uniquement** `{eventId, startMs, durationMs}`. Validation : `eventId` non vide `<= 128` caractères, `startMs` dans `[0, 43_200_000]`, `durationMs <= 60_000`. **Aucun** champ de type, de payload, de référence d'asset, de volume/gain, ni de politique de pause/seek.
- **`ScheduledTrack.Event`** porte `{id, text, startMs, durationMs, mediaBitmap}` — un payload **TEXTE / bitmap**. Toujours **aucune** référence d'asset audio, **aucun** volume, **aucune** politique de pause. La Track porte aussi `MediaIdentity {platform, videoId, title, durationMs}` et un drapeau `pauseFreezesDisplay`.

**Conclusion explicite.** Le modèle `SceneEvent` existant (et `ScheduledTrack.Event`) **NE PEUT PAS représenter un événement AUDIO en l'état** : il n'a **ni discriminant de type**, **ni référence d'asset**, **ni gain SceneVibe**, **ni politique de pause/seek**. Un champ `text`/`mediaBitmap` ne décrit pas une ressource audio. Une extension **minimale** est donc nécessaire.

### 4.2. Extension minimale PROPOSÉE (non figée)

Extension **minimale** du modèle d'événement capturant exactement les champs du mandat (section 5.3) :

| Champ | Rôle |
| --- | --- |
| Identifiant d'événement | Clé opaque, cohérente avec `eventId` existant (réutilisée pour `consumed`/`windowed`). |
| Type `= AUDIO` | Discriminant qui aujourd'hui **n'existe pas** ; distingue AUDIO de TEXTE. |
| Référence de l'asset | Pointeur vers une ressource audio **locale** (fichier embarqué/local). |
| Position de début | Position média de départ (analogue à `startMs`). |
| Durée attendue | Fenêtre média attendue (analogue à `durationMs`, bornée). |
| Volume / gain SceneVibe | Gain **local** SceneVibe, indépendant du volume système/Prime. |
| Politique de pause et de seek | Comportement du commentaire en cours sur pause/seek (section 6). |

**Garde-fous.**

- **Proposition à concevoir/auditer, PAS un contrat JSON/Cloud figé.** Conformément au mandat : **« ne pas modifier prématurément les contrats Cloud de production »**. Aucune forme sérialisée n'est gelée ici.
- Pour la **première qualification**, une **Track de démonstration** et des **fichiers audio locaux** suffisent — inutile d'introduire un asset Cloud ou un pipeline d'ingestion à ce stade.
- L'extension doit rester **additive** et compatible : le chemin TEXTE existant ne change pas ; le type `AUDIO` est un nouveau cas routé vers le `SceneVibeAudioRenderer` tandis que `TEXT` reste routé vers le `SceneRenderer`.

## 5. Matrice de synchronisation (section 7)

Chaque ligne est pilotée par **l'unique horloge média** via les branches concrètes du scheduler. Aucune ligne n'introduit de minuterie propre à l'audio.

| Situation | Comportement attendu | Branche / callback concret du moteur existant |
| --- | --- | --- |
| **Lecture normale** | Commentaire audio déclenché au bon instant média. | `renderDue(position)` → `onRender`/`onDue` quand `startMs <= position` en `STATE_PLAYING` ; le renderer prépare + démarre l'asset. |
| **Pause Prime Video** | Commentaire suspendu **ou** arrêté selon une **politique explicite** (section 6). | `onPlayback(playing=false, ...)` ; la position n'avance plus donc `expireElapsed` ne draine aucune fenêtre (gel naturel). |
| **Reprise** | Comportement cohérent avec la position média. | `onPlayback(playing=true, ...)` ; `renderDue`/`expireElapsed` reprennent sur progression de la position (pas de rattrapage par horloge murale). |
| **Seek avant** | Ne pas lancer un commentaire devenu obsolète. | `deltaMs > 5000 ms` → `skipThrough(position)` **consomme** sans rendre ; `expireElapsed` ferme les fenêtres dépassées. Le renderer ne démarre pas un asset consommé. |
| **Seek arrière** | Réarmement contrôlé, **sans doublons**. | `deltaMs < -2000 ms` → `rearmFrom(position)` dé-consomme les événements futurs et `onExpire` toute fenêtre visible en attente avant replay. |
| **Média non reconnu** | Aucun commentaire audio. | `MediaIdentityMatcher.matches` échoue → `deactivate("media_identity_mismatch")` → `onEligibility(false)` ; le renderer reste muet. |
| **Changement d'épisode** | Arrêt des commentaires de l'épisode précédent. | Nouvelle identité média → non-concordance puis `load(...)` d'une nouvelle Track : `consumed`/`windowed` vidés, `onEligibility(false)` → arrêt du renderer, nouvelle Track réarmée. |
| **AUDIO OFF** | Arrêt **immédiat** de la restitution SceneVibe. | Commande UI (section 7.2) : le renderer `stop()` + `release()` immédiatement ; indépendant du scheduler (le TEXTE peut rester actif). |
| **Perte d'éligibilité** | Arrêt **sûr**, sans lecture résiduelle. | `onEligibility(false)` (session indisponible / mismatch) → `deactivate` vide les fenêtres ; le renderer stoppe et libère, aucune lecture résiduelle. |

### 6. Politique explicite d'un commentaire audio déjà commencé (pause / seek)

Décision de conception **à confirmer physiquement** (FEAT-003), proposée par défaut :

- **Pause pendant un commentaire en cours → STOP-ON-PAUSE.** Contrairement au TEXTE qui **gèle** (la fenêtre média ne draine pas tant que la position n'avance pas, cf. `pauseFreezesDisplay`), l'audio **s'arrête** à la pause : un commentaire vocal figé puis repris à mi-phrase est perçu comme un défaut, et laisser du son pendant une image figée est indésirable. À la **reprise**, le commentaire n'est **pas** rejoué s'il a été consommé (cohérence avec la position média).
- **Seek arrière avant le début d'un commentaire en cours → STOP puis réarmement.** `rearmFrom` dé-consomme l'événement et expire sa fenêtre visible ; le renderer stoppe d'abord, puis l'asset pourra repartir proprement sur une relecture avant (pas de doublon, pas de chevauchement).
- **Seek avant au-delà de la fenêtre → STOP.** `skipThrough`/`expireElapsed` consomment/ferment la fenêtre ; le renderer stoppe un asset dont la fenêtre média est dépassée.
- **Fin de fenêtre média (`onExpire`) → STOP propre** et libération des ressources.

> Alternative possible (freeze-and-resume) consignée mais **non retenue par défaut** : à n'évaluer que si l'expérimentation Sony montre une coexistence confortable. La politique retenue reste **STOP-ON-PAUSE** jusqu'à preuve physique contraire.

## 7. Interface utilisateur minimale (section 8)

### 7.1. État actuel

L'application ne possède aujourd'hui qu'un **bouton Autostart** dans `MainActivity` (`autostartButton`, un `Button` navigable à la télécommande). Il n'existe **aucun** toggle TEXT/AUDIO. Les commandes ci-dessous sont donc **additives**.

### 7.2. Commandes proposées (D-pad)

- **TEXT ON/OFF** — active/désactive le chemin TEXTE (overlay).
- **AUDIO ON/OFF** — active/désactive le chemin AUDIO SceneVibe. **AUDIO OFF arrête immédiatement** la restitution (le renderer `stop()`/`release()` sans attendre un événement du scheduler).
- **Volume SceneVibe indépendant** — gain propre à SceneVibe, réglable par pas.

Toutes les commandes doivent être **utilisables à la télécommande Android TV** (navigation D-pad / OK), sur le même modèle que le `Button` Autostart existant. **Aucune interface complexe de mixage** n'est demandée dans cette phase.

### 7.3. Volume SceneVibe : gain local, jamais le volume système/Prime

> Le volume SceneVibe est un **gain local du renderer** (par ex. `MediaPlayer.setVolume`), appliqué uniquement au commentaire SceneVibe. Il **ne doit PAS** modifier volontairement le volume système ni celui de Prime Video : **aucun `setStreamVolume`**, aucune action sur `AudioManager` global — conforme à la règle du spike (coexistence sans toucher le flux Prime).

## 8. Contraintes et interdictions respectées (section 10)

La conception respecte intégralement les interdictions du mandat :

- **Aucun contrôle du transport Prime Video** sur le chemin audio (pas de PAUSE/PLAY/SEEK ; la régie elle-même « never commands the video player »).
- **Aucune capture audio ou vidéo** du lecteur tiers (ressources audio **locales** uniquement).
- **Aucun contournement DRM.**
- **Aucun remplacement de la bande sonore originale** (coexistence, Prime continue de jouer).
- **Aucun mid-roll publicitaire.**
- **Aucune modification des contrats Cloud de production** (extension d'événement proposée, non figée ; Track de démo + fichiers locaux pour la première qualification).
- **Aucune fusion GitHub automatique.**
- **Aucune installation Sony sans GO propriétaire ; aucune modification du keystore stable M5.**
- **Aucun chantier Studio ou CATALOGUE parallèle.**
- **Surface de contrôle overlay passive / non focusable** sur TV : report de l'incident 0.2.1 (overlay focusable + `requestFocus()` → télécommande Sony non répondante). L'overlay reste `FLAG_NOT_FOCUSABLE` / `FLAG_NOT_TOUCHABLE` ; les commandes UI vivent dans l'activité navigable, pas dans l'overlay.

> **`SceneVibeAudioRenderer` est un composant FUTUR distinct dans `app/`**, et **non** une copie du harnais de debug expérimental isolé `:mediaexperiment`. Ce dernier a servi de preuve physique (A1) ; le composant de production sera conçu proprement dans `app/`, réutilisant le patron de Ports mais pas le code jetable du harnais.

## 9. Note GO / STOP

Ce document est une **proposition d'architecture de Phase A**. L'**implémentation (Phase B)** — création effective du `SceneVibeAudioRenderer`, du `LocalAudioPort`/`AndroidLocalAudioPort`, de l'extension d'événement AUDIO et des commandes UI — **n'est PAS autorisée** tant que le **choix technique de gestion de l'audio focus** n'a pas été **validé par l'expérimentation Sony** décrite dans `audio-focus-experiment-plan.md` (FEAT-003). Rappel de l'audit (`audio-historical-audit.md`) : le chemin de référence est la coexistence **SANS** audio focus (A1 PASS physique) ; le ducking `MAY_DUCK` est un **FAIL historique** ; la cause du mixage A1 reste **INCONNUE**.

> **FIN DE LA PROPOSITION D'ARCHITECTURE — Phase A.** **STOP avant tout développement Android de la phase B**, jusqu'à validation du choix de gestion de l'audio focus par le propriétaire via l'expérimentation Sony.
