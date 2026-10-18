# SceneVibe OS — KIRO AUDIO 001 — Phase A — Plan d'expérimentation audio-focus Sony

**Statut : PROTOCOLE À EXÉCUTER PLUS TARD — Phase A (audit + architecture), aucun essai physique conduit maintenant.**
**Date : 18 octobre 2026.**
**Nature du document : protocole opératoire pour la qualification physique Sony (mandat Phase D). C'est un plan, PAS un rapport de résultats.**

> **GO Phase A uniquement.** Ce document est un livrable documentaire. Il décrit une expérimentation à conduire **ULTÉRIEUREMENT** sur le téléviseur Sony physique, **sous un GO propriétaire SÉPARÉ** (mandat section 9, Phase D). **Aucune** mutation du Sony, **aucune** installation, **aucun** build (Gradle ou signé) n'est réalisé par ce livrable. La ligne de référence signée M5 (`versionCode=15`), la signature et le keystore stable **ne sont pas touchés**. **STOP avant tout développement Android de la phase B** : les phases B, C et D **ne sont pas autorisées** par ce document.
>
> Documents frères (Phase A) : audit historique [`audio-historical-audit.md`](audio-historical-audit.md) (FEAT-001) et proposition d'architecture [`audio-architecture.md`](audio-architecture.md) (FEAT-002). Le présent plan en dépend pour son cadrage.

## 1. Risque central à trancher (section 6)

Le mandat impose d'établir **expérimentalement** si **Android TV 12 autorise la restitution d'un commentaire audio SceneVibe pendant que Prime Video continue de produire son propre son**. C'est le **risque principal** du chantier AUDIO.

### 1.1. Cadrage issu de l'audit historique (FEAT-001)

- **Coexistence vocale SANS demande d'audio focus = A1 PASS physique**, mais **strictement limité à la seule configuration Sony BRAVIA Android TV 12 / Prime Video natif testée**. La voix locale était audible pendant que l'audio Prime restait audible à volume réduit, sans PAUSE, avec retour automatique du volume.
- **Le MÉCANISME du mixage reste INCONNU.** `VoiceCoexistenceProbe` ne demande **aucun** focus ; l'atténuation observée en A1 **n'est pas prouvée** pilotée par l'audio focus. La cause (politique OEM, mixage, interactions Android) est indéterminée.
- **Demande explicite `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` (`USAGE_ASSISTANT` / `CONTENT_TYPE_SPEECH`) = FAIL historique** : Prime passe en **PAUSE** au lieu de ducker, et **ne reprend pas de lui-même**. Ce FAIL n'est **pas** reclassé par l'observation A1.

### 1.2. Règle d'or (section 6)

> **Ne pas supposer qu'une politique d'audio focus garantit le mixage.** Le **comportement réel sur Sony fait foi**. Une politique de focus (ou son absence) n'est qu'une hypothèse ; seule l'observation physique devant le téléviseur tranche.
>
> **Si la coexistence sonore est impossible ou instable, ARRÊTER cette voie** et **documenter le blocage** (verdict **BLOCKED**, section 8.4, consigné dans le rapport `audio-qualification-report.md`) **avant toute nouvelle architecture**. Aucune refonte ne doit précéder le constat physique.

### 1.3. Rappel : ce qui NE qualifie PAS (section 12)

Un test JVM vert, un état logiciel `COMPLETED_NEEDS_PHYSICAL_PROOF` de `VoiceCoexistenceProbe` (état **mécanique**, pas une preuve d'audibilité), ou une simple lecture audio dans SceneVibe **sans Prime Video**, **ne suffisent pas** et **ne qualifient pas** le produit. Seule la coexistence réelle observée devant le Sony compte.

## 2. Dispositif, portée et conditions préalables

| Élément | Valeur |
| --- | --- |
| Téléviseur de référence | Sony BRAVIA Android TV 12 |
| Lecteur tiers | Prime Video natif (`com.amazon.amazonvideo.livingroom`) |
| Package de production (intouché) | `com.scenevibe.tvcompanionpoc` |
| Ligne signée de référence (intouchée) | `versionCode=15`, signature / keystore stable M5 |
| APK expérimentale | APK **isolée** installée **à côté** de la production (voir section 6), chemin de référence = voix locale SANS focus (`VoiceCoexistenceProbe`) |
| Autorisation | **GO propriétaire SÉPARÉ requis** (Phase D). Sans ce GO, aucune étape physique de ce plan n'est exécutée. |

**Portée stricte.** Tout verdict obtenu par ce plan sera limité à la **seule** configuration Sony BRAVIA Android TV 12 / Prime Video natif. Aucun résultat ne s'étend à Netflix, Disney+, YouTube, Google TV, un autre modèle/fabricant, ni à des sources interrompues par publicité.

**Diagnostics autorisés.** Enregistrer uniquement des **diagnostics grossiers** : états observés (PLAYING / PAUSED), audibilité oui/non, progression de l'image oui/non, SHA-256 de la fixture audio, SHA-256 de l'APK expérimentale, version logicielle du téléviseur et version de Prime Video. **Ne jamais** consigner de titres de média, jetons de session, sous-titres ni identifiants/crédentiels.

---

## 3. Scénario A — Sans demande d'audio focus

**Objet (section 6).** Lancer un **court commentaire SceneVibe sans demander explicitement le focus** et vérifier si **les deux sons restent audibles**. C'est le chemin de référence (`VoiceCoexistenceProbe`, aucune demande d'`AudioFocus`, aucune commande de transport).

**Pré-conditions.** GATE opérateur R0 (section 6) PASS ; Prime Video lancé et en lecture ; scan passif effectué.

**Étapes.**
1. Confirmer par un **scan passif** (lecture seule, `MediaSessionProbe`) que la session native Prime est **PLAYING** avec un jeton stable. Ne jamais émettre de transport.
2. Démarrer une **ressource vocale locale bornée** (fixture courte embarquée/locale), **sans** demande d'`AudioFocus` et **sans** PAUSE/PLAY/SEEK Prime.
3. Observer, les yeux et les oreilles devant le téléviseur, pendant toute la durée du commentaire.
4. Laisser le commentaire se terminer **naturellement**. **Ne jamais** envoyer de PLAY ni de reprise automatique.
5. Re-scanner la session d'origine une fois après la fin locale pour confirmer qu'elle est inchangée.

**Critère PASS.** TOUS les points suivants :
- Le commentaire vocal SceneVibe est **réellement audible** ;
- Le son de Prime Video **reste audible** (même atténué) ;
- L'**image Prime continue de progresser** ;
- Prime **reste en lecture (PLAYING)**, **sans PAUSE** ;
- La session d'origine est **inchangée** ;
- **Aucune reprise automatique** n'est émise par SceneVibe.

**Critères FAIL.**
- Prime passe en **PAUSE** → **FAIL de coexistence, même si la parole est audible** ;
- Le son Prime devient inaudible / coupé, ou l'image cesse de progresser ;
- La session d'origine est remplacée ou altérée ;
- Un son SceneVibe résiduel persiste après la fin, ou une commande de transport est émise.

**Enregistrer.** États observés (PLAYING / PAUSED), audibilité des deux sources, progression image, SHA fixture, SHA APK, versions TV/Prime. Jamais de titres/jetons/sous-titres/crédentiels.

---

## 4. Scénario B — Demande de focus transitoire

**Objet (section 6).** **Mesurer** si Prime **baisse son volume, s'interrompt ou conserve sa lecture** lorsqu'une demande de focus **transitoire** est émise, par exemple `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` avec `USAGE_ASSISTANT` / `CONTENT_TYPE_SPEECH`.

> **Nature : diagnostic comparatif, PAS une politique de production.** Ce scénario sert uniquement à **comparer** le comportement Sony à celui du scénario A. Il n'est **pas** la politique de restitution retenue : l'architecture (FEAT-002) proscrit déjà cette voie.

**Attente historique (FEAT-001).** `MAY_DUCK` a fait **passer Prime en PAUSE sans reprise automatique** → **FAIL historique**. Le but ici est de confirmer ou d'infirmer physiquement ce comportement, pas de l'adopter.

**Étapes.**
1. Confirmer Prime PLAYING par scan passif (comme en A).
2. Émettre la demande de focus transitoire (`AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`, `USAGE_ASSISTANT` / `CONTENT_TYPE_SPEECH`).
3. Démarrer la même fixture vocale locale bornée.
4. Observer le comportement de Prime : **baisse de volume (ducking)**, **interruption/PAUSE**, ou **lecture conservée**.
5. Laisser se terminer, ne jamais envoyer de PLAY ni de reprise.

**Règles dures.**
- **Aucun `setStreamVolume`.** Le ducking éventuel est réalisé par **le système Android, PAS par SceneVibe**. SceneVibe ne modifie jamais le volume système ni celui de Prime.
- **Si Prime passe en PAUSE → FAIL de coexistence**, et le scénario **s'arrête immédiatement** **sans que SceneVibe envoie la moindre commande de transport** (ni PLAY, ni reprise). On ne tente pas de « corriger » la pause.

**Critère PASS (diagnostic).** Prime **conserve sa lecture** (PLAYING) et les deux sons restent audibles — c'est-à-dire un comportement équivalent au scénario A obtenu via le focus. (Résultat **non attendu** d'après l'historique.)

**Critère FAIL (attendu historiquement).** Prime **PAUSE** et ne reprend pas de lui-même ; ou toute instabilité (coupure, remplacement de session). Dans ce cas, consigner le FAIL et conclure que la voie `MAY_DUCK` reste proscrite (cohérent avec FEAT-001 et FEAT-002).

**Enregistrer.** Comportement observé (duck / pause / inchangé), audibilité, états, SHA fixture, SHA APK, versions TV/Prime.

---

## 5. Scénario C — Retour à Prime Video

**Objet (section 6).** Vérifier le comportement **après la fin du commentaire** et **après une perte de premier plan de SceneVibe** : Prime doit revenir à son état nominal **sans aucune action de SceneVibe**.

**Étapes et vérifications.**
1. **Fin du commentaire (scénario A réussi).** Vérifier que le volume / l'état de Prime se **restaure automatiquement**, **sans** commande SceneVibe. **Critère PASS** : restauration automatique, aucune lecture SceneVibe résiduelle, **aucun PLAY initié par SceneVibe**. **FAIL** : son SceneVibe résiduel, ou toute commande de transport émise par SceneVibe.
2. **Perte du premier plan SceneVibe** (bascule d'application, retour à Prime au premier plan). **Critère PASS** : la restitution SceneVibe **s'arrête de façon sûre** ; Prime reste maître de son transport ; aucune lecture résiduelle. **FAIL** : audio SceneVibe persistant en arrière-plan, ou commande de transport émise.
3. **AUDIO OFF** (commande UI). **Critère PASS** : **arrêt immédiat** de la restitution SceneVibe (`stop()` + `release()`), indépendamment du scheduler. **FAIL** : latence anormale, son résiduel, ou effet sur le transport Prime.
4. **Perte d'éligibilité** (session Prime indisparue / non-concordance d'identité média). **Critère PASS** : **arrêt sûr**, aucune lecture résiduelle, aucun PLAY SceneVibe. **FAIL** : lecture résiduelle ou action sur Prime.

**Règle transverse.** À aucun moment de C, SceneVibe n'émet de PAUSE/PLAY/SEEK vers Prime. La restauration de Prime est **toujours** le fait du système ou de l'utilisateur, jamais de SceneVibe.

---

## 6. Comportements de synchronisation à tester (section 7)

Chaque situation de la matrice du mandat est traduite en **étapes physiques concrètes** sur le Sony, pilotées par l'**unique horloge média** (position Prime observée passivement). Le branchement moteur de chaque ligne est documenté dans FEAT-002, section 5.

| # | Situation | Étapes physiques sur Sony | Critère PASS | Compte comme FAIL |
| --- | --- | --- | --- | --- |
| 1 | **Lecture normale** | Prime PLAYING ; laisser la position atteindre `startMs` d'un commentaire. | Le commentaire audio est **déclenché au bon instant média**, une seule fois. | Déclenchement au mauvais instant, en double, ou absence de déclenchement alors que dû. |
| 2 | **Pause Prime Video** | Pendant la lecture, mettre Prime en PAUSE (par la télécommande, pas par SceneVibe). | Le commentaire est **suspendu/arrêté selon la politique explicite STOP-ON-PAUSE** (section 6.bis) ; la fenêtre média ne draine pas (position figée). | Audio SceneVibe qui continue pendant une image figée ; drainage ou consommation de fenêtre pendant la pause. |
| 3 | **Reprise** | Reprendre la lecture Prime. | Comportement **cohérent avec la position média** ; pas de rattrapage par horloge murale ; un commentaire déjà consommé n'est pas rejoué. | Rejeu d'un commentaire consommé, rattrapage artificiel, désynchronisation. |
| 4 | **Seek avant** | Effectuer un saut avant au-delà d'un commentaire à venir (delta > 5000 ms). | Le commentaire **devenu obsolète n'est pas lancé** (`skipThrough` le consomme sans rendre). | Lancement d'un commentaire périmé par le saut. |
| 5 | **Seek arrière** | Effectuer un retour arrière avant le début d'un commentaire déjà vu (delta < -2000 ms). | **Réarmement contrôlé, sans doublons** (`rearmFrom` dé-consomme ; les fenêtres visibles en attente expirent avant replay). | Doublons, chevauchement, commentaire manquant au repassage. |
| 6 | **Média non reconnu** | Lire un média dont l'identité ne correspond pas à la Track. | **Aucun commentaire audio** (`MediaIdentityMatcher` échoue → `onEligibility(false)`). | Tout commentaire audio émis sur un média non reconnu. |
| 7 | **Changement d'épisode** | Passer à l'épisode suivant (nouvelle identité média). | **Arrêt des commentaires de l'épisode précédent** ; fenêtres vidées ; nouvelle Track réarmée. | Persistance d'un commentaire de l'épisode précédent. |
| 8 | **AUDIO OFF** | Actionner la commande UI AUDIO OFF pendant une restitution. | **Arrêt immédiat** de la restitution SceneVibe (`stop()`/`release()`), indépendant du scheduler. | Latence, son résiduel, ou effet sur Prime. |
| 9 | **Perte d'éligibilité** | Rendre la session indisponible / provoquer une non-concordance pendant la restitution. | **Arrêt sûr, sans lecture résiduelle** (`deactivate` vide les fenêtres). | Lecture résiduelle, action sur Prime. |

### 6.bis. Politique explicite d'un commentaire déjà commencé (pause / seek)

Le mandat exige une **politique définie** pour un commentaire audio **déjà commencé** lorsqu'une **pause** ou un **seek** survient. La politique proposée par l'architecture (FEAT-002, section 6) est **STOP-ON-PAUSE**, à **confirmer physiquement** par ce protocole :

- **Pause pendant un commentaire en cours → STOP** (l'audio s'arrête, contrairement au TEXTE qui gèle). **PASS** : le son s'arrête net à la pause, pas de reprise à mi-phrase. **FAIL** : audio figé puis repris en milieu de phrase, ou son maintenu sur image figée.
- **Seek arrière avant le début d'un commentaire en cours → STOP puis réarmement propre.** **PASS** : arrêt immédiat puis replay sans doublon au repassage dû. **FAIL** : chevauchement ou doublon.
- **Seek avant au-delà de la fenêtre → STOP.** **PASS** : l'asset dont la fenêtre est dépassée est arrêté/non lancé. **FAIL** : lecture d'un asset périmé.
- **Fin de fenêtre média (`onExpire`) → STOP propre et libération.** **PASS** : arrêt déterministe, pas de fuite de ressource.

> L'alternative **freeze-and-resume** n'est **à évaluer** que si l'expérimentation Sony démontre une coexistence confortable. Par défaut, **STOP-ON-PAUSE** s'applique jusqu'à preuve physique contraire.

---

## 7. Protocole opérateur et sécurité

Modelé sur les protocoles opérateurs existants ([`audio-video-spike-2-operator.md`](audio-video-spike-2-operator.md) et [`../m5-sony-targeted-qualification-protocol.md`](../m5-sony-targeted-qualification-protocol.md)) : exécution par l'opérateur devant le téléviseur, **une action puis son observation à la fois**.

### 7.1. Installation isolée

- Installer l'APK expérimentale **isolée, À CÔTÉ de la production** (package distinct, p. ex. harnais `.mediaexperiment`). **Ne pas désinstaller** `com.scenevibe.tvcompanionpoc`, **ne pas** `pm clear`, **ne pas** réinitialiser le Sony/M5, **ne pas** refaire le pairing.
- Accorder à l'APK expérimentale **ses propres** accès overlay et notification. **Conserver intacts** le pairing et les données de production.
- En cas de conflit de clé de signature debug, ne désinstaller **que** le package expérimental, **jamais** la production.

### 7.2. Overlay passif obligatoire (leçon 0.2.1)

> **Report de l'incident télécommande 0.2.1** (overlay focusable + `requestFocus()` → touches Sony non répondantes, reboot ADB requis — **BLOCKER physique**). L'overlay de diagnostic **DOIT** :
> - poser **`FLAG_NOT_FOCUSABLE` ET `FLAG_NOT_TOUCHABLE`** ;
> - ne contenir **aucun bouton** cliquable ;
> - **ne jamais** appeler `requestFocus()` ;
> - rester **en lecture seule**, même re-superposé.
>
> Prime / Android TV conservent le focus D-pad. Les commandes UI (AUDIO ON/OFF, volume) vivent dans l'activité navigable, pas dans l'overlay.

### 7.3. GATE opérateur R0 (avant tout test média)

Préalable obligatoire, repris de la clôture physique du spike : depuis un Sony nominal, relever la ligne de base télécommande (Home, Back, D-pad, OK, volume) ; installer l'APK candidate ; ouvrir le panneau de diagnostic **sans** commande ; confirmer que **toutes** les touches répondent et qu'aucun bouton focusable n'intercepte la télécommande ; exécuter un `hide` et confirmer que l'overlay disparaît et que la télécommande fonctionne **sans reboot**. **Si R0 échoue, force-stop du seul package expérimental, consigner, et ABANDON** des tests média. Les scénarios A/B/C ne commencent **que** si R0 est PASS.

### 7.4. Interdictions (section 10)

Sur le chemin audio, **sont interdits** :
- **Aucun contrôle du transport Prime Video** (pas de PAUSE/PLAY/SEEK émis par SceneVibe) ;
- **Aucune capture** audio ou vidéo du lecteur tiers ;
- **Aucun contournement DRM** ;
- **Aucun remplacement de la bande sonore** originale ;
- **Aucun mid-roll** publicitaire ;
- **Aucune mutation du volume système ni du volume Prime** (`aucun setStreamVolume`, aucune action sur `AudioManager` global ; le volume SceneVibe est un gain local) ;
- **Aucune modification des contrats Cloud de production** ;
- **Aucun usage du keystore stable M5** ni modification de la ligne signée `versionCode=15` ;
- **Aucune fusion GitHub automatique** ;
- **Aucune installation Sony sans GO propriétaire** ;
- **Aucun chantier Studio ou CATALOGUE parallèle.**

---

## 8. Critères de verdict

Le rapport de résultats (**`audio-qualification-report.md`**, **livrable Phase D NON produit maintenant**) devra distinguer **sans ambiguïté** trois axes, en miroir de la section 9 (Phase D) et de la section 12 (critères de réussite) :

### 8.1. Trois axes à distinguer

| Axe | Question | Démonstré par |
| --- | --- | --- |
| **(a) Coexistence sonore** | Les **deux sources** sont-elles réellement audibles en même temps (commentaire SceneVibe audible ET son Prime audible), Prime restant en lecture ? | Scénario A (et, en comparaison, B). Une PAUSE de Prime = **FAIL** de cet axe. |
| **(b) Synchronisation** | Le commentaire **suit-il la position média réelle** (déclenchement au bon instant, pause/reprise/seek/épisode cohérents) ? | Matrice section 6 (lignes 1 à 7) + politique 6.bis. |
| **(c) Absence de régression TEXT** | Le chemin **TEXTE reste-t-il opérationnel** et inchangé pendant l'exécution AUDIO ? | Vérification que TEXT continue de fonctionner (section 9 Phase D, étape 5). |

### 8.2. Reflet de la liste Phase D (section 9)

Le rapport reflètera la liste d'étapes Phase D : (1) lancer Prime et vérifier son son nominal ; (2) déclencher un commentaire audio SceneVibe ; (3) vérifier que les deux sources restent audibles ; (4) mesurer focus, pause, seek et volume ; (5) confirmer que TEXT fonctionne toujours.

### 8.3. Ce qui NE qualifie PAS (section 12)

> Un **test JVM réussi** ou une **simple lecture audio dans SceneVibe sans Prime Video** **ne suffisent pas** et **ne qualifient pas** le produit. Le prototype AUDIO n'est fonctionnel que si : un commentaire SceneVibe est **réellement audible**, le son Prime **reste audible**, la synchronisation **suit la lecture**, AUDIO OFF **fonctionne**, les pauses et déplacements **ne produisent pas de lecture incohérente**, **TEXT reste opérationnel**, et **aucune action interdite** n'est nécessaire.

### 8.4. Cadre de verdict FINAL / PARTIAL / BLOCKED (section 11.6, à remplir après exécution)

| Verdict | Signification | À renseigner après exécution |
| --- | --- | --- |
| **FINAL** | Les trois axes (a)+(b)+(c) sont PASS sur Sony, sans action interdite. | _(à remplir)_ |
| **PARTIAL** | Une partie seulement est démontrée (p. ex. coexistence A OK mais synchronisation incomplète). | _(à remplir)_ |
| **BLOCKED** | Coexistence impossible/instable (p. ex. Prime PAUSE systématique) ; la voie est **arrêtée** et le **blocage documenté** avant toute nouvelle architecture (règle section 6). | _(à remplir)_ |

> Les cases ci-dessus restent **vides** dans ce livrable Phase A : elles seront complétées dans `audio-qualification-report.md` lors de la Phase D, après GO propriétaire.

---

## 9. Note GO / STOP

Ce document est un **protocole de Phase A** destiné à une **exécution ultérieure** sous **autorisation propriétaire séparée** (Phase D). **Les phases B, C et D ne sont PAS autorisées par ce livrable.** Aucune mutation Sony, installation, build ou merge n'a été ni ne doit être réalisé maintenant. Le rapport de résultats `audio-qualification-report.md` est la **sortie Phase D** et **n'est pas produit ici**.

Rappel transverse (section 6) : **le comportement réel sur Sony fait foi** ; **ne pas supposer** qu'une politique d'audio focus garantit le mixage ; **si la coexistence est impossible ou instable, ARRÊTER cette voie et documenter le blocage** avant toute nouvelle architecture.

Références croisées : audit historique [`audio-historical-audit.md`](audio-historical-audit.md) (FEAT-001) et proposition d'architecture [`audio-architecture.md`](audio-architecture.md) (FEAT-002).

> **FIN DU PLAN D'EXPÉRIMENTATION AUDIO-FOCUS — Phase A.** **STOP avant tout développement Android de la phase B**, et **avant toute exécution physique de la phase D**, jusqu'à GO propriétaire explicite.
