# E5-040 — investigation Sony du 15 octobre 2026

**BLOCKED — ROOT CAUSE NOT PROVEN. E5-039 PHYSICAL FAIL.**

La tentative à 07:21:44 est expliquée par une session Prime réapparue en état NONE,
sans identité et avec une position ancienne. Les deux autres tentatives échouent avant
qu'une observation native soit transmise au propriétaire de transport. Les traces ne
contiennent pas la branche interne qui les refuse. Plusieurs causes différentes donnent
exactement ces mêmes traces sur le code inchangé ; le rejeu joint le démontre.

Il n'y a donc pas de correction fonctionnelle ni d'APK candidate E5-040 dans cette livraison.
Une nouvelle durée de tolérance, un autre choix de session ou une acceptation de premier
plan sans preuve seraient des modifications spéculatives. Aucune garde n'a été changée.
Le résultat ne doit pas être présenté comme READY FOR SONY TEST.

## Base et périmètre

Vérification GitHub avant mutation :

| Référence | État | HEAD |
|---|---|---|
| PR #18 | OPEN / DRAFT / UNMERGED | `47f675dbd05218fb1caca2b090fd81c822e513e0` |
| PR #19 | OPEN / DRAFT / UNMERGED | `efd3a7983a03458ec1f919d760c10f1b1f9c57ff` |
| PR #20, base E5-040 | OPEN / DRAFT / UNMERGED | `b97aa06885b264d64274dc65f054ffbf2cd6e53e` |

Le HEAD logiciel `24a1b9376eb629567bf018b1a4e967f01625688b` est un ancêtre du
HEAD documentaire `b97aa06885b264d64274dc65f054ffbf2cd6e53e`. Le checkout initial était propre.
La branche dédiée est `work/scenevibe-e5-native-admission-040`, issue de ce dernier HEAD.
La nouvelle PR Draft cible `work/scenevibe-e5-native-stale-position-039`.

Tous les fichiers `app/` sont byte-identiques à E5-039 : runtime, renderer, sécurité,
manifestes, tests Android/JVM, identité applicative et configuration de signature.
Banner, Studio, main et les branches antérieures ne sont pas modifiés. Les seuls ajouts
sont les preuves expurgées, les scripts de reproduction, ce rapport, la procédure et
une CI d'investigation. Le registre historique admet ces chemins par hashes complets,
sans changer un test existant. Les journaux originaux ne sont pas publiés au dépôt public.

## Preuves originales

Les deux fichiers ont été lus intégralement en tant que fichiers, et non remplacés par
un résumé de conversation. Encodage UTF-16LE avec BOM ; lignes comptées après décodage.
Les heures ci-dessous sont celles de logcat Sony, sans offset explicite dans le fichier.

| Fichier | Octets | SHA-256 |
|---|---:|---|
| `scenevibe-e5-039-full-logcat.txt` | 13146050 | `a75143cc0dee6457d1e61205df9df3a5aec7d160da43c6fda38b2b4fbee75896` |
| `scenevibe-e5-039-sony-logcat.txt` | 169424 | `a63aaf7e51b4b719f3f526c95c5081722c9ce079e08d563b75838ffe24ad1c1c` |

[sony-evidence.json](sony-evidence.json) conserve les extraits et numéros de lignes du
journal complet. L'identifiant du contenu Amazon et l'identifiant Java du contrôleur
sont expurgés. Les empreintes de session déjà pseudonymisées par E5 sont conservées.
Le journal court corrobore les trois tentatives ; ce ne sont pas seulement deux tentatives.

| Tentative | Horaires | Lignes complètes | Décision effective |
|---|---|---|---|
| `f734cb3b-1` | 07:20:55.659–07:20:56.262 | 35088–35648 | TARGET, observation native absente, CLEANUP REFUSED |
| `f734cb3b-2` | 07:21:44.143–07:21:44.493 | 41213–41856 | STALE_POSITION, garde DURATION_OR_IDENTITY, CLEANUP REFUSED |
| `f734cb3b-3` | 07:21:59.735–07:22:00.106 | 46134–46647 | TARGET, observation native absente, CLEANUP REFUSED |

Les ACK ADB existent pour les générations 1 et 3. Aucun `SceneVibeE5Adb` n'accompagne la
génération 2 : ne pas l'attribuer automatiquement au même déclenchement ADB. Le journal
montre Companion/Diagnostics puis les paramètres Usage Access entre 07:21:06 et 07:21:35,
et `moveTaskToBack` à 07:21:44.101. Cette transition est distincte du retest ADB à 07:21:59.
Aucune de ces tentatives ne produit TARGET_VALIDATED, PAUSE_SENT, PAUSED_CONFIRMED ou PLAY_SENT.

## Chaîne causale auditée

Les chemins Java ci-dessous sont sous `app/src/main/java/com/scenevibe/tvcompanionpoc/`,
sauf le receiver sous `app/src/e5Qualification/java/` du même package. Les lignes
référencent le HEAD E5-039, identique au runtime de cette branche.

| Transition | Fonction / ligne | Données et garde | Résultat observé |
|---|---|---|---|
| ADB → service | `E5AdbTriggerReceiver.onReceive`, ligne 20 ; `E5LocalRequest.confirmedVod`, ligne 13 | Receiver explicite protégé DUMP, flag debug, overlay, enveloppe VOD vraie | QUEUED signifie service demandé ; aucune permission de PAUSE |
| Service → régie | `OverlayService.requestE5Local`, ligne 368 | Sony/API31, attestation, pas de présentation concurrente, accès Usage Stats | REQUEST généré, contrôleur local configuré |
| Préflight | `SceneRuntimeController.requestLocal`, ligne 329 | Plan VOD, slot graphique, préparation du lecteur local | PREFLIGHT PREPARING ; aucune cible native sélectionnée ici |
| Lecteur prêt | `SceneRuntimeController.localReady`, ligne 358 | Génération, délai 5 s, état READY du lecteur local | PLAYER_READY met la phase PAUSE_REQUESTED ; PAUSE n'est pas encore envoyé |
| Sélection native | `AndroidNativeSessionPort.choose`, ligne 81 | VOD, premier plan indépendant, accès notification, liste active, unicité Prime, pas de concurrent pertinent, double lecture cohérente | null aux générations 1 et 3 ; snapshot aux 2 |
| Admission | `NativeTransportOwner.requestPause`, ligne 75 ; `NativeQualificationPolicy.evidence`, ligne 67 | Package, attestation, identité/durée, position, horloges, âge, PLAYING/vitesse, actions | TARGET si null ; STALE_POSITION à la première preuve de fraîcheur insuffisante |
| Envoi PAUSE | `AndroidNativeSessionPort.pause`, ligne 44 | Nouvelle acquisition, même token/programme, fraîcheur, PLAYING, actions | Non atteint sur Sony dans ces traces |
| Confirmation / interlude | `confirmPaused` ligne 106, `pauseStillOwned` ligne 138 | Mise à jour après commande, position cohérente puis ancre brute immobile | Non atteint ; aucun interlude natif admis |
| Reprise | `SceneRuntimeController.localEof` ligne 391 ; `requestPlay` ligne 156 ; adapter `play` ligne 61 ; `confirmPlaying` ligne 171 | EOF normal, même cible, ancre PAUSED inchangée, PLAY unique puis observation PLAYING | Non atteint ; aucune reprise aveugle |
| Retrait | `SceneRuntimeController.finishLocal`, ligne 491 | Invalidation génération, release lecteur/bail, retrait hôte, CLEANUP | REFUSED terminal réel, sans commande PLAY |

Le probe `MediaSessionProbe.sample/snapshotController` lit séparément les contrôleurs actifs
chaque seconde et alimente le suivi MEDIA. Il ne fournit pas le snapshot au transport E5.
L'adaptateur E5 interroge le système au moment de select, observe et de chaque commande ;
il ne garde pas un ancien MediaController pour un dispatch futur. Le bail retient seulement
les preuves permettant la comparaison au token exact lors de chaque réacquisition.
Il n'y a pas de cible native déjà autorisée au préflight graphique à comparer au PLAYER_READY.

## Génération 2 : refus expliqué, à conserver

Ligne 41755 : `state=0`, `positionMs=0`, `estimatedPositionMs=0`, `durationMs=-1`,
`observedAtMs=49057465`, `updatedAtMs=49021558`, `providerAgeMs=35907`,
`sampleAgeMs=0`, `guard=DURATION_OR_IDENTITY`. La réception est neuve ; les données du
fournisseur ne le sont pas. L'estimation reste 0 parce que l'état est NONE, pas PLAYING.

La garde DURATION_OR_IDENTITY précède le contrôle d'âge. Avec duration=-1 et l'attestation
vraie passée par choose, elle implique un identifiant média vide. Cela est corroboré à
07:21:44.675 par le probe, ligne 42331 : NONE, position 0, âge 36170 ms, `mediaId=-`.
À 07:21:44.362, un composant Sony et un autre consommateur système rapportent eux aussi
`state=0, position=0, updated=49021558` (ligne 41663 et lignes voisines).

Le probe rapportait NO_ACTIVE_SESSIONS avant le retour à Prime (ligne 41184). Il observe
ensuite NONE à 07:21:44–48, BUFFERING à 07:21:49, STOPPED à 07:21:50 et PLAYING à
07:21:51. Ce n'est pas une contradiction entre un probe frais PLAYING et un E5 périmé
au même instant. La session est en cours de réapparition après le retour depuis Companion.
La continuité du token avant/après cette transition n'est pas démontrable : le probe
ne journalise pas ce token. Il serait abusif de conclure à un changement de session exact.

Même avec un ID inventé, l'âge 35907 ms dépasserait la limite existante 20000 ms et
l'état NONE n'autoriserait aucune pause. Le rejeu reproduit STALE_POSITION et sa première
garde sur ces valeurs, puis vérifie qu'une vraie position ancienne avec ID valide reste refusée.
Aucune conversion ms/ns ni confusion wall/monotone n'est nécessaire pour expliquer ce refus.
Le mécanisme interne Prime (nouveau token, réinitialisation ou réactivation du même token)
reste indéterminé ; le refus sûr est démontré sans prétendre diagnostiquer ce mécanisme.

## Générations 1 et 3 : limite de preuve démontrée

Pour la génération 3, le probe observe PLAYING à 07:21:59.941 (ligne 46358), position
2914566, estimation 2914568, âge 2 ms, durée -1 et ID présent. L'admission à 07:22:00.050
porte pourtant `guard=NO_OBSERVATION`, sans session, identité, état ou position.
Même structure à la génération 1, entourée de positions fraîches.

`NativeDiagnostics.describe` ligne 49 n'émet pas les champs snapshot quand sample=null.
`requestPause` journalise le résultat de `select()` avant les gardes. `choose()` ne retourne
qu'une sélection Prime ou null : le cas observé prouve donc **un échec de sélection**, pas
une mauvaise valeur de position inspectée par le propriétaire de transport.

Les retours null suivants sont fusionnés sans motif :

- preuve de premier plan refusée, fenêtre UsageEvents vide/ancienne ou ambiguë ;
- accès notification absent, service/liste indisponible ou exception système ;
- absence de Prime, plusieurs sessions Prime ou autre session pertinente ;
- métadonnées/état absents ou différents entre les deux lectures du même contrôleur.

La fenêtre de 30000 ms et le réducteur par package constituent une limite réelle du code :
une activité longtemps au premier plan ne réémet pas nécessairement RESUMED dans la fenêtre.
La double lecture peut aussi refuser une mise à jour normale entre deux lectures Binder.
Le rejeu exécute ces deux chemins sur le véritable adaptateur et obtient dans les deux cas
TARGET / snapshot=null / aucune commande. **Cela ne démontre pas lequel s'est exécuté sur Sony.**

Pour la génération 1, le lancement Prime à 07:20:00.328 (ligne 19321) précède
l'admission de près de 56 s : la fenêtre de 30 s est donc une hypothèse appuyée par le
contexte. Les UsageEvents effectifs ne sont toutefois pas dans la preuve. Pour la
génération 3, le signal Sony de retour à Prime précède l'admission d'environ 16 s :
il faut éviter d'appliquer automatiquement la même explication aux deux refus.

Aucune session d'index >0 n'apparaît dans le probe complet. Cela réduit l'indice de concurrence
aux instants échantillonnés, sans prouver la liste active au moment exact de choose.
Le signal Sony `onActivityChanged` n'est pas le résultat de UsageStatsManager.queryEvents.
Le retour visible à Prime à 07:21:44 ne suffit donc pas à reconstruire l'ensemble réduit à
07:22:00. Une causalité unique « fenêtre 30 s » ne serait pas établie pour cette génération.

## Décisions avant/après cette investigation

Il n'y a pas de colonne « corrigé PASS » fabriquée : le logiciel est inchangé.

| Entrée réellement testée | E5-039 | Après E5-040 | Justification |
|---|---|---|---|
| Prime, ID natif, durée -1, VOD attestée, position fraîche, foreground cohérent | PAUSE admis puis cycle confirmé dans le rejeu | Identique | Les preuves synthétiques sont suffisantes |
| NONE, ID absent, position 0, âge 35907 ms | STALE_POSITION, 0 PAUSE | Identique | Reproduit la génération 2, refus à conserver |
| Position vraiment périmée avec ID valide | STALE_POSITION / PROVIDER_STALE | Identique | Aucune hausse de seuil |
| Cible absente/incorrecte/concurrente | TARGET, 0 PAUSE | Identique | Pas de substitution de cible |
| Foreground manquant / mise à jour entre double lecture | TARGET, 0 PAUSE | Identique | Même sortie, causes distinctes ; source physique non identifiée |
| Live ou VOD sans attestation | TARGET, 0 PAUSE par l'adaptateur | Identique | VOD n'est jamais déduit de la durée ; une fausse attestation n'est pas détectable par ce mécanisme |
| Changement token avant dispatch / concurrent après pause / seek / interruption | Refus, aucune reprise aveugle | Identique | Réacquisitions et ancre restent obligatoires |

## Reproduction et qualification

`python3 docs/e5-admission-040/reproduce_admission.py` vérifie d'abord les sept sources
app exactes contre b97aa, puis compile leur véritable code avec des doubles d'API Android.
Les 21 scénarios PASS incluent les deux formes de refus, concurrence, cible absente/incorrecte,
position absente/ancienne, base wall invalide, VOD non attestée, changement de token,
cycle indépendant PLAYING → PAUSED → EOF → PLAYING, seek 1 ms et interruption sans PLAY.
[replay-result.txt](replay-result.txt) est la sortie réelle conservée. Ce ne sont pas des tests
Android exécutés sur appareil ; l'émulation de données n'établit pas la cause Sony.

La batterie existante a également été rejouée localement : **131 Python PASS** et
**80 JVM E5 PASS**, avec les vrais JUnit 4.13.2 / Hamcrest / JSON et le code app courant.
`run_jvm.py` permet le rejeu ; seuls Log et l'horloge Android par défaut inutilisée par
les preuves explicites sont des stubs. Aucun test existant, docstring ou skip n'est modifié.
Les résultats finaux de CI de cette branche sont consignés dans [qualification.md](qualification.md).

La CI E5-039 [38668622187](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38668622187)
a été revalidée comme SUCCESS au HEAD logiciel 24a1. Son succès antérieur ne prouve ni la
réussite physique, ni une correction E5-040. La nouvelle CI compile/lint/teste l'app inchangée
OFF/ON et rejoue les sondes Android API31, sans job de signature de candidate.

## Blocage et suite concrète

L'information manquante est le motif de retour de **choose/primeForeground/snapshot pendant
la sélection native**, corrélé à attemptId et à l'instant monotone de la décision.
Les journaux fournis ne la contiennent pas ; aucun rejeu de ces fichiers ne peut la recréer.
Un dumpsys pris après coup aide au contexte mais n'est pas une observation atomique de choose.

La modification suivante à qualifier serait une instrumentation **uniquement observationnelle** :
codes bornés pour chaque sortie (foreground refusé avec compte d'événements et âge, accès,
liste, concurrence, métadonnées absentes, champ modifié entre deux lectures), même attemptId,
phase/garde et empreintes salées ; aucun titre, ID média brut, token réutilisable ou historique
d'applications. Les échantillons des deux lectures doivent rester distingués. Les codes de
sélection doivent parvenir au diagnostic terminal, sans devenir une permission de transport.
Ce plan n'est pas livré comme un correctif fonctionnel prouvé et aucune APK instrumentée
non qualifiée n'est proposée à installer.

[Procedure_PowerShell_E5_040.md](Procedure_PowerShell_E5_040.md) fournit une collecte propriétaire
de contexte, sous sa nouvelle autorisation, et les critères du futur vrai test. Elle ne lance
aucune installation ni commande distante depuis WORK. Il n'est pas demandé de répéter à
l'identique un test fonctionnel déclaré prêt : il faut d'abord lever le blocage de preuve.

Risques résiduels : foreground non continu, instants de lecture concurrents, absence de token
du probe et comportement PAUSED réel encore non qualifié. Aucun relâchement n'est justifié
par ces risques. Package/versionCode/signature E5-039 ne sont pas rebaptisés E5-040 ; une
candidate future devra avoir versionCode >16, le certificat stable vérifié et son propre SHA-256.

**STOP : aucun merge, Cloud, Vercel, Neon, Production ou accès ADB Sony exécuté.**
Le verdict physique fourni par le propriétaire reste FAIL. La décision suivante relève du
propriétaire : poursuite de l'instrumentation et collecte ciblée, avant tout nouveau test fonctionnel.

Références Android consultées le 15 octobre 2026 :
[UsageStatsManager.queryEvents](https://developer.android.com/reference/android/app/usage/UsageStatsManager#queryEvents(long,%20long))
requiert des bornes Unix et retourne les événements de l'intervalle ;
[PlaybackState](https://developer.android.com/reference/android/media/session/PlaybackState)
décrit séparément état, position et mise à jour. Le code réserve bien wall à UsageEvents
et elapsedRealtime aux positions natives ; aucune conversion de temps n'est corrigée sans preuve.
