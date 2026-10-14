# E5-NATIVE-035 — Architecture intégrée

Date : 14 octobre 2026. Mission de finalisation documentaire uniquement. GitHub est la source de vérité.

- Base TV : `17cbe36ae99ac7f48aaf861e0d1feac702a9e521`.
- HEAD logiciel examiné : [`102a0193e862abe64e28d94614eb2cd0fb0e2488`](https://github.com/pierre22400/scenevibe-tv-companion/commit/102a0193e862abe64e28d94614eb2cd0fb0e2488).
- Branche : `work/scenevibe-e5-native-l0-l1-035`.
- [PR #18](https://github.com/pierre22400/scenevibe-tv-companion/pull/18) : OPEN / DRAFT / UNMERGED au contrôle.
- Autorité fonctionnelle : [clôture WORK 034 et six décisions ratifiées](https://github.com/pierre22400/interface-scenevibe/blob/8376d64d51ac35d699f6dd443f0cc4c364a806b1/docs/studio/phase-c1/scenevibe-e5-work-034-owner-closure.md).

**Statut : NOT READY — API35 PLATFORM GATE.** Le run E5 push réussit sur API 31/35 ; le dernier rejeu de la PR échoue sur API 35. Le succès isolé ne ferme pas cette réserve. Aucun essai physique Sony ; aucune nouvelle correction technique par WORK dans ce mandat.


## Responsabilités effectivement implémentées

| Composant | Responsabilité |
|---|---|
| SceneRuntimeController | Régie unique ; admission, génération, unique présentation locale, transitions et cleanup |
| SceneRenderer | Unique root/fenêtre de scène ; graphiques v1, géométrie, couches et host TextureView |
| LocalVideoPort / AndroidLocalVideoPort | Ressource locale, préparation, MediaPlayer, Surface, état réel, EOF/erreur et libération |
| NativeTransportOwner / NativeSessionPort | Bail natif unique en RAM ; garde de pause/reprise et confirmations ; subordonné à la régie |
| MediaCalendarScheduler / autorité WALL | Temporalités éditoriales conservées ; aucune insertion du plan local dans leurs calendriers |
| OverlayService | Wiring Android, contexte main sérialisé, contrôle FGS et polling des budgets de qualification |

Sources : [régie](https://github.com/pierre22400/scenevibe-tv-companion/blob/102a0193e862abe64e28d94614eb2cd0fb0e2488/app/src/main/java/com/scenevibe/tvcompanionpoc/SceneRuntimeController.java), [renderer](https://github.com/pierre22400/scenevibe-tv-companion/blob/102a0193e862abe64e28d94614eb2cd0fb0e2488/app/src/main/java/com/scenevibe/tvcompanionpoc/SceneRenderer.java), [lecteur](https://github.com/pierre22400/scenevibe-tv-companion/blob/102a0193e862abe64e28d94614eb2cd0fb0e2488/app/src/main/java/com/scenevibe/tvcompanionpoc/AndroidLocalVideoPort.java), [bail](https://github.com/pierre22400/scenevibe-tv-companion/blob/102a0193e862abe64e28d94614eb2cd0fb0e2488/app/src/main/java/com/scenevibe/tvcompanionpoc/NativeTransportOwner.java).

## Composition et réemploi du Spike

Le port reprend MediaPlayer, TextureView, Surface, prepareAsync, EOF et release. Il reçoit le host du SceneRenderer existant ; le WindowManager expérimental du Spike n'est pas repris. SafeResumeGuard/SessionCatalog et les snapshots expérimentaux ont inspiré token/package, identité et actions. Le bail intégré ajoute position, âge provider, génération, garde stationnaire et confirmations observées.

[LocalInterludePlan](https://github.com/pierre22400/scenevibe-tv-companion/blob/102a0193e862abe64e28d94614eb2cd0fb0e2488/app/src/main/java/com/scenevibe/tvcompanionpoc/LocalInterludePlan.java) est en mémoire et contient une scène rectangle/texte construite par le parseur graphique v1 existant, plus une ressource locale fixe. LocalMediaResource distingue provenance, MIME, taille, durée et SHA d'une AssetReference persistante. Aucun URI fourni par un manifeste distant, aucune primitive VIDEO, nouveau contrat ou moteur de publication.

Une seule présentation et un seul bail. Les demandes concurrentes sont refusées, sans file ni préemption. Une scène graphique active ou un commentaire historique visible empêche l'admission. DUE/EXPIRE ne remplacent pas une présentation locale ; les autorités éditoriales ne sont pas déplacées dans le lecteur. Une ancienne Track ne fournit pas l'identité du déclenchement manuel : le bail observe son programme natif propre.

## Lifecycle

ATTACH → PREPARE → READY → PLAY → STOP/EOF/ERROR → RELEASE → DETACH.

La fixture APK est copiée complètement vers un fichier privé unique, hashée et ouverte avec descripteur en lecture seule. MediaExtractor vérifie pistes et formats ; la disponibilité des décodeurs puis prepareAsync précèdent toute PAUSE. Le host reste transparent durant la préparation. READY exige player préparé et Surface disponible ; READY n'est pas START.

Le renderer conserve unités du canvas, zIndex, transparence, annulation des fades et flags NOT_FOCUSABLE/NOT_TOUCHABLE. La présentation graphique locale conserve le TextureView préparé. Release précède la destruction de Surface/host. Une présentation graphique qui retire ce host déclenche release synchroniquement. Les callbacks vérifient tentative, état et player capturé ; ils ne ressuscitent pas une présentation annulée.

EOF normal libère le lecteur avant PLAY. Le retrait final attend PLAYING confirmé. Une fin anormale nettoie sans PLAY. Le polling250 ms sert aux confirmations/watchdogs monotones ; il n'est pas un scheduler MEDIA/WALL.

## Profil Android et activation

[Build](https://github.com/pierre22400/scenevibe-tv-companion/blob/102a0193e862abe64e28d94614eb2cd0fb0e2488/app/build.gradle) : OFF par défaut et en release. Le token debug exact `SCENEVIBE_E5_NATIVE_QUALIFICATION=e5-native-035` exige Cloud vide et LAN OFF ; release opt-in refusée. Déclenchement explicite local dans Diagnostics/service non exporté. Aucun boot, restore ou événement distant ne lance le clip.

Le manifeste principal reste inchangé. L'overlay de manifeste opt-in debug ajoute FGS mediaPlayback et PACKAGE_USAGE_STATS. MediaPlayback est activé seulement pour la demande locale ; hors lecture, API 34+ conserve specialUse et pre34 utilise NONE. Usage Access doit être accordé manuellement : une session PAUSED peut survivre à HOME, d'où la nécessité d'une preuve foreground distincte. Lecture limitée aux 30 s récentes, sans historique persistant ; preuve absente/ambiguë = refus.

La cible réelle initiale reste Sony/API 31/VOD attestée/PLAYING/contrôlable. La preuve des émulateurs API 31/35 ne qualifie pas Prime. Aucun AudioFocus, contrôle général du volume, publicité/live, voix, ressources distantes ou L2–L8.

## Références GitHub et état des preuves

| Workflow, au HEAD logiciel ci-dessus | Exécution | Résultat constaté |
|---|---|---|
| Android debug APK | [38516842225](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38516842225) | SUCCESS ; 1 114 JVM PASS, 1 SKIPPED historique, 0 FAIL ; 125 Python PASS |
| Android installation disk and process durability | [38516842301](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38516842301) | SUCCESS |
| Android M5 media scheduler differential | [38516842189](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38516842189) | SUCCESS |
| Android 15 API 35 platform smoke (NOT Android TV) | [38516842143](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38516842143) | SUCCESS |
| E5 native 035 isolated software — push | [38516837495](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38516837495) | SUCCESS ; software, platform31 et platform35 SUCCESS |
| E5 native 035 isolated software — PR, attempt 2 | [38516842153](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38516842153) | FAILURE ; software/31 SUCCESS, platform35 FAILURE |

Preuves détaillées : [logiciel + APK E5](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38516837495/artifacts/11871694729), [API 31 PASS](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38516837495/artifacts/11872406837), [API 35 PASS du push](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38516837495/artifacts/11872143140), [archive API 35 en échec associée au run PR](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38516842153/artifacts/11872431545). La dernière archive montre HOST_ATTACH_STATE puis REFUSED/CLEANUP avant PAUSE ; son horodatage interne est 05:33 UTC. Le log du dernier job rejoué, 115700122029, est à 05:40 UTC et rapporte failed=1, passed=0, ANDROID_OWNER_ASSERTION. L'archive n'est donc pas présentée comme une nouvelle trace horodatée de cet attempt 2. Les deux preuves restent conservées séparément ; la cause précise de l'intermittence n'est pas tranchée.

Les résultats sont ceux de la CI relue et des artefacts reconcomptés pendant cette finalisation. Aucune nouvelle suite locale, aucun nouveau build et aucun essai Sony n'ont été exécutés dans ce mandat. Les émulateurs standards exercent le vrai player/renderer avec transport natif **SIMULATED**, pas Prime ni une Sony.

## Réserve d'admission documentaire

La publication est limitée aux sept fichiers Markdown de `docs/e5-native-035/`. Le [test d'inventaire fini](https://github.com/pierre22400/scenevibe-tv-companion/blob/102a0193e862abe64e28d94614eb2cd0fb0e2488/tests/test_e5_native_boundary.py) compare tous les fichiers réels de `docs/` aux chemins inscrits dans le registre E5. Au HEAD logiciel, ces sept nouveaux chemins n'y figurent pas. Leur publication laisse donc cette assertion documentaire insatisfaite ; les 125 succès du logiciel 102a ne sont pas attribués automatiquement au nouveau HEAD documentaire.

Le registre `.github/scripts/e5-native-035-provenance.json`, les tests et les workflows ne sont pas modifiés par cette livraison. Une admission documentaire explicite relève du propriétaire qui prend en charge les correctives ; WORK signale cet écart et ne le corrige pas silencieusement. Il ne s'agit pas d'un changement de comportement applicatif. La réserve API 35 reste bloquante indépendamment de cette admission.
