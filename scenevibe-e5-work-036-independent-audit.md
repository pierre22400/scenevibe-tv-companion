# SceneVibe OS — WORK 036 — Audit indépendant E5 L0 + L1

Date : 14 octobre 2026. Mandat : lecture, audit, tests isolés et publication de ce rapport sur une branche distincte. Aucun changement du logiciel, des tests, des workflows, des gardes ou de la PR candidate. Aucun essai Sony, session Prime réelle, installation physique ou merge.

**Verdict : PASS — READY FOR OWNER SONY QUALIFICATION DECISION**

Le candidat permet de recommander au propriétaire une qualification physique ciblée Sony Android 12/API 31 avec Prime VOD, sous une autorisation distincte. Ce verdict porte sur l'aptitude à entreprendre cette qualification, et ne constitue ni une qualification physique ni une autorisation d'installation. L'intermittence API 35 reste explicitement non résolue et non bloquante pour cette cible limitée, pour les raisons détaillées ci-dessous. Une réserve de budget de confirmation et les limites d'observation des ressources restent consignées.

## 1. Références et identité

- Dépôt : [pierre22400/scenevibe-tv-companion](https://github.com/pierre22400/scenevibe-tv-companion).
- [PR #18](https://github.com/pierre22400/scenevibe-tv-companion/pull/18), branche `work/scenevibe-e5-native-l0-l1-035` : OPEN, DRAFT, UNMERGED lors des contrôles de début et de fin d'analyse.
- HEAD candidat effectivement lu, cloné et exécuté : [`47f675dbd05218fb1caca2b090fd81c822e513e0`](https://github.com/pierre22400/scenevibe-tv-companion/commit/47f675dbd05218fb1caca2b090fd81c822e513e0). Aucune divergence observée.
- Base M5 : [`17cbe36ae99ac7f48aaf861e0d1feac702a9e521`](https://github.com/pierre22400/scenevibe-tv-companion/commit/17cbe36ae99ac7f48aaf861e0d1feac702a9e521).
- [Diff complet base → candidat](https://github.com/pierre22400/scenevibe-tv-companion/compare/17cbe36ae99ac7f48aaf861e0d1feac702a9e521...47f675dbd05218fb1caca2b090fd81c822e513e0) : 51 fichiers, 4 733 insertions, 119 suppressions.
- Autorité : [clôture WORK 034, six décisions ratifiées](https://github.com/pierre22400/interface-scenevibe/blob/8376d64d51ac35d699f6dd443f0cc4c364a806b1/docs/studio/phase-c1/scenevibe-e5-work-034-owner-closure.md).
- Les [sept rapports WORK 035](https://github.com/pierre22400/scenevibe-tv-companion/tree/47f675dbd05218fb1caca2b090fd81c822e513e0/docs/e5-native-035) ont été lus : architecture, implementation-report, non-regression, software-qualification, transport-guard, sony-protocol et handoff.

Les rapports 035 décrivent l'état logiciel `102a0193…` et les échecs API 35 alors disponibles. Leur mention NOT READY est une photographie historique, pas le résultat de ce nouvel audit. Le passage de `102a0193…` à `47f675db…` ajoute ces sept documents et sept admissions de provenance ; il ne modifie aucun code applicatif. Il ne constitue donc pas une correction démontrée de l'intermittence Android.

Les trois correctives propriétaires ont été examinées : [b02e063c](https://github.com/pierre22400/scenevibe-tv-companion/commit/b02e063c61780904fb868f660f3496e45f70b728), [102a0193](https://github.com/pierre22400/scenevibe-tv-companion/commit/102a0193e862abe64e28d94614eb2cd0fb0e2488), [47f675db](https://github.com/pierre22400/scenevibe-tv-companion/commit/47f675dbd05218fb1caca2b090fd81c822e513e0). Le dernier commit ne change que le registre de provenance, avec sept entrées documentaires.

## 2. Méthode et limites

Copie indépendante détachée du HEAD exact ; aucune utilisation d'une copie de développement antérieure. Lecture des différences et des chemins critiques, interrogation des métadonnées GitHub, lecture des journaux des cinq workflows, téléchargement et vérification des empreintes des archives E5, recomptage des XML JVM et inspection des traces Android. Les sondes nouvelles ont été écrites hors du dépôt et exécutées contre les classes candidates.

L'environnement isolé dispose de Java OpenJDK 17.0.20 et Python. Les dépendances JVM utilisées correspondent au build : JUnit 4.13.2, Hamcrest 1.3, org.json 20240303. Le compilateur Java a été invoqué via son module, sans modification du dépôt. Aucun SDK/Gradle Android ni émulateur local n'a été utilisé : les builds complets et les essais API 31/35 sont des preuves CI relues, pas des essais Android nouvellement exécutés par l'auditeur. Les tests locaux et leurs résultats sont distingués ci-dessous.

Les essais de plateforme exercent le vrai `AndroidLocalVideoPort`, `SceneRenderer` et `SceneRuntimeController`, avec un transport **SIMULATED_NATIVE**. Ils ne qualifient pas `MediaController` de Prime, le foreground OEM, la télécommande Sony, l'audibilité ou la reprise réelle d'un programme. L'instrumentation fournit un état natif simulé après ses commandes ; la suite JVM sépare explicitement commande et observation.

## 3. Architecture et conformité aux décisions

| Décision ratifiée / axe | Constat indépendant |
|---|---|
| Plan fixe local 10 s et graphiques dans un host | `LocalInterludePlan` fixe la fixture et sa géométrie ; `SceneRenderer.attachLocalVideo` place le `TextureView` dans son root existant. `showLocalScene` conserve cette vue et ajoute les primitives graphiques. Aucun second root audiovisuel `WindowManager`. Le renderer legacy historique subsiste ; l'admission E5 refuse sa présentation visible. |
| Régie unique | La présentation et le bail sont subordonnés à `SceneRuntimeController`. Le poll Android contrôle des budgets d'exécution ; il ne produit pas de nouveau calendrier éditorial. Le scheduler MEDIA reste l'autorité historique. Les demandes concurrentes sont refusées, sans queue ni préemption. |
| EOF normal et watchdog indépendant | Préparation avant PAUSE ; READY exige préparation et Surface ; start après PAUSED confirmé. EOF nécessite un player STOPPED et une durée dans le budget de fixture. Watchdog 13 s ; EOF prématuré ou expiré n'autorise pas PLAY. |
| Reprise sous garde | Bail lié au token, package, programme/métadonnées, ancre de position, mise à jour du provider et génération. PLAY uniquement après PAUSE envoyé par cette tentative, PAUSED observé et EOF normal, puis nouvelle validation au moment de la commande. PLAYING doit être observé pour SUCCESS. |
| Fin anormale | STOP, player/Surface, changement de cible, seek, timeout, unload ou destruction ne disposent d'aucun chemin de PLAY de nettoyage. Après appropriation, issue RECOVERY_REQUIRED et reprise manuelle éventuelle. |
| Cible limitée / OFF | Flag absent = OFF, release = OFF. Opt-in debug exact `e5-native-035` ; build refusé si Cloud origin non vide ou LAN actif. Entrée locale Diagnostics, service non exporté, attestation VOD et Sony/API 31 requis. Aucun déclenchement Cloud/distant ajouté. |

Sources candidates : [régie](https://github.com/pierre22400/scenevibe-tv-companion/blob/47f675dbd05218fb1caca2b090fd81c822e513e0/app/src/main/java/com/scenevibe/tvcompanionpoc/SceneRuntimeController.java#L314-L511), [renderer](https://github.com/pierre22400/scenevibe-tv-companion/blob/47f675dbd05218fb1caca2b090fd81c822e513e0/app/src/main/java/com/scenevibe/tvcompanionpoc/SceneRenderer.java#L64-L121), [admission service](https://github.com/pierre22400/scenevibe-tv-companion/blob/47f675dbd05218fb1caca2b090fd81c822e513e0/app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java#L360-L435), [profil de build](https://github.com/pierre22400/scenevibe-tv-companion/blob/47f675dbd05218fb1caca2b090fd81c822e513e0/app/build.gradle).

La `Surface` est possédée et libérée par le port player ; le `TextureView` et son attachement appartiennent au renderer. Les opérations sont sur le main owner Android. Le player retire ses callbacks et ressources avant retrait du host ; le renderer invalide le host avant détachement. Le `SurfaceTexture` reste attaché au cycle de vie du `TextureView`, sans second propriétaire graphique.

Aucun contrat graphique v1, identité MEDIA, ordonnanceur MEDIA/WALL, protocole Cloud, stockage durable, ACK ou publication n'est étendu. La fixture provient uniquement de l'APK, est copiée en cache privé et hashée ; aucune URI distante ni fichier utilisateur substituable. Aucun AudioFocus, mixage, ducking ou politique Banner/Video générale n'est créé. L0/L1 ne tranche pas D05-V ni les mécanismes généraux L2–L8.

## 4. Transport, fraîcheur et incidents

Lecture indépendante de [NativeTransportOwner](https://github.com/pierre22400/scenevibe-tv-companion/blob/47f675dbd05218fb1caca2b090fd81c822e513e0/app/src/main/java/com/scenevibe/tvcompanionpoc/NativeTransportOwner.java) et de [AndroidNativeSessionPort](https://github.com/pierre22400/scenevibe-tv-companion/blob/47f675dbd05218fb1caca2b090fd81c822e513e0/app/src/main/java/com/scenevibe/tvcompanionpoc/AndroidNativeSessionPort.java).

Le port réacquiert les contrôleurs à chaque observation et commande, refuse plusieurs Prime et les sessions concurrentes pertinentes, et exige la preuve foreground. Métadonnées et état sont lus deux fois sur le même contrôleur ; différences de programme, position, état, vitesse, actions ou timestamp refusées. Une commande ne vaut pas confirmation. La dernière validation du PLAY contrôle encore l'ancre brute PAUSED et le timestamp provider ; aucune sélection par seul package ne remplace le token initial.

Profil mesuré par tests logiciels : préparation 5 s, PAUSED 3 s, PLAYING 3 s, watchdog clip 13 s, âge de réception 1 s, âge provider 20 s, tolérance de position 0 ms, poll 250 ms. Ce sont des limites provisoires, pas des mesures Sony. Le VOD est attesté manuellement, et la stabilité de metadata ne prouve pas toute la sémantique publicitaire du fournisseur. La double lecture et la revalidation réduisent les courses ; elles ne rendent pas atomiques une query Binder et une commande distante.

| Chemin examiné | Résultat attendu et preuve disponible |
|---|---|
| Erreur player / Surface perdue | Libération ; aucun PLAY ; callbacks retirés et génération rejetée. Suites JVM et cas Android Surface. |
| STOP / timeout | Aucun PLAY de récupération ; libération du bail/host/player. JVM, sondes nouvelles et cas Android STOP. |
| HOME / autre app / token ou programme changé | Observation refuse ; aucun transfert ni PLAY sur autre cible. Code foreground/session et tests JVM ; non mesuré sur Prime réel. |
| Seek / timestamp provider modifié | Ancre invalidée, reprise refusée même pour 1 ms. JVM et sonde indépendante EOF après seek. |
| Callback tardif / ancienne génération | Aucune résurrection, aucune retraite d'une nouvelle tentative. Tests et sonde indépendante. |
| Service détruit / révision remplacée | Cancel avant teardown, suppression du poll ; aucun bail restauré ni PLAY au restart. Lecture service/régie et suites JVM. |

`finishLocal` invalide le plan avant teardown et utilise des finally imbriqués pour essayer la libération du player, du bail et du host. `removeNow` traite le cas déjà détaché et remet le root à null. La libération répétée est sans nouveau PLAY. La réserve sur la vérification de suppression physique du fichier est distinguée en OBS03.

## 5. Non-régression et provenance

Les 110 fichiers de tests JVM présents dans la base M5 sont **byte-identiques** au candidat. Les suites E5 sont ajoutées, pas substituées. Les baselines/inventaires historiques restent figés ; le résumé JVM admet exactement 10 + 30 + 24 = 64 tests E5, avec les anciens comptes et skips conservés.

Une vérification indépendante a interrogé les vrais blobs Git de `17cbe36…` : **276 startingBlobs concordent**, les 18 fichiers historiques modifiés correspondent aux before/after hashes du registre et leurs inverses uniques reconstruisent exactement les bytes de cette base. Cela ne repose pas uniquement sur des hashes déclarés par le registre. La reconstruction est limitée aux assertions historiques de périmètre ; les tests JVM/Android E5 et les builds utilisent bien le candidat. b02e063c retire la dépendance à un checkout profond sans changer cette ancre ; 102a0193 admet les 64 cas exacts ; 47f675db admet sept documents avec leurs hashes, sans wildcard.

La suite Python indépendante passe les contrôles de pairing/installation/durabilité, manifeste, calendrier et différentiels historiques ainsi que l'inventaire E5. Les XML CI OFF et ON contiennent chacun **1 114 PASS, 1 SKIPPED, 0 FAIL/ERROR**. Le skip est précisément `M1CloudInteropTest.originalColumboProjectionIsInstallable`. Il n'est pas transformé en succès ni nouvellement ajouté. Les modes OFF et ON ne sont pas additionnés pour produire un total artificiel.

## 6. Vérifications réellement exécutées par l'auditeur

| Exécution locale isolée, HEAD 47f675db… | Code de sortie | Résultat réel |
|---|---:|---|
| `PYTHONDONTWRITEBYTECODE=1 python -m unittest discover -s tests -p 'test_*.py'` | 0 | 125 tests PASS ; deux passages indépendants, 9,878 s et 10,408 s |
| Compilation via `java --module jdk.compiler/com.sun.tools.javac.Main`, classes E5 réelles + dépendances du build | 0 | Compilation JVM PASS |
| JUnitCore `NativeTransportOwnerTest SceneRuntimeLocalInterludeTest E5LocalPlanAndForegroundTest` | 0 | 64 tests PASS, 0,041 s |
| Compilation et JUnitCore `Independent036Test`, sonde extérieure au dépôt | 0 / 0 | 8 tests PASS, 0,022 s ; NB02 caractérisé explicitement |
| Vérification indépendante des blobs/inverses contre Git M5 | 0 | 276 ancres, 18 inverses exacts, 110 fichiers JVM historiques identiques |
| Recompte des XML téléchargés | 0 | OFF et ON : chacun 1 114 PASS, 1 SKIPPED, 0 échec |
| Extraction APK/fixture, SHA-256 et ffprobe de la fixture extraite | 0 | Identités et propriétés détaillées en section 9 |

Les huit sondes nouvelles utilisent les véritables classes `SceneRuntimeController` et `NativeTransportOwner`. Les ports déterministes du harness sont réutilisés comme seams, avec de nouvelles assertions ; deux sondes utilisent un port indépendant à horloge contrôlée. Elles vérifient : commande distincte de confirmation, ordre PAUSED/start et revalidation/PLAY, erreur Surface suivie d'EOF tardif, seek de 1 ms avant EOF, watchdog + STOP idempotent, callbacks d'une ancienne génération pendant une nouvelle préparation, seconde demande et destruction, refus d'une confirmation PAUSED à +3 001 ms, et caractérisation de PLAYING frais à +3 001 ms. Le dernier cas documente un écart de budget ; son succès d'assertion ne signifie pas que cet écart est corrigé.

## 7. Résultats GitHub relus au HEAD exact

| Workflow candidat | Preuve | Résultat relu |
|---|---|---|
| Android debug APK | [38521313103](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38521313103), job 115711401309 | SUCCESS ; build, 1 114 JVM PASS / 1 SKIPPED, 125 Python PASS |
| Installation disk/process durability | [38521313106](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38521313106) | SUCCESS API 31/35 ; 11 cas correctifs PASS par plateforme ; 30 invocations distinct-process sur 31 avec 4 contrôles pré-fix, 22 sur 35 |
| M5 MEDIA scheduler differential | [38521313157](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38521313157) | SUCCESS API 31/35 ; chacun 82 comparaisons, 4 traces raw HashMap, 0 divergence |
| API 35 platform smoke | [38521313134](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38521313134) | SUCCESS ; installation, lancement, absence de crash, persistance après force-stop ; ni boot physique ni Android TV qualifiés |
| E5 isolated software — PR | [38521313109](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38521313109) | SUCCESS logiciel/OFF/ON ; API 31 : 5/5 ; API 35 : 5/5 |
| E5 isolated software — push, corroboration | [38521308076](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38521308076) | SUCCESS logiciel/31/35 ; archive API 35 inspectée : 5/5 |

Les logs des jobs de durabilité, différentiel et smoke ont été relus, au-delà du seul badge. La CI E5 reconstruit la base immutable, exécute le candidat et conserve OFF avant le build opt-in. Les builds/lint/provenance sont des exécutions GitHub ; ils ne sont pas annoncés comme un nouveau Gradle local.

Les avertissements d'émulateur/headless/gfxstream et de dépréciation d'actions Node sont conservés dans les journaux sources ; ils n'ont pas été assimilés à des défauts applicatifs ni utilisés pour transformer un échec en succès. Aucun timeout, assertion, cible ou skip n'a été modifié.

## 8. Investigation obligatoire API 35

### Échantillon effectivement examiné

| HEAD / run | Archive examinée | Résultat réel |
|---|---|---|
| 102a0193…, push [38516837495](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38516837495) | [11872143140](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38516837495/artifacts/11872143140) | 5 PASS ; 169 updates de frames |
| 102a0193…, PR [38516842153](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38516842153), première tentative | [11872431545](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38516842153/artifacts/11872431545) | 0 PASS / 1 FAIL ; trace 05:33 UTC, HOST_ATTACH_STATE |
| Même run PR, rejeu | [11872343852](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38516842153/artifacts/11872343852) | 0 PASS / 1 FAIL ; trace 05:40 UTC, HOST_ATTACH_STATE ; journal job 115700122029 concordant |
| 47f675db…, PR [38521313109](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38521313109) | [11873689676](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38521313109/artifacts/11873689676) | 5 PASS ; 209 updates de frames |
| 47f675db…, push [38521308076](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38521308076) | [11874122843](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38521308076/artifacts/11874122843) | 5 PASS ; 164 updates de frames |

Sur ces cinq exécutions API 35 explicitement examinées : trois réussites et deux échecs. Il ne s'agit ni d'un inventaire exhaustif de toutes les campagnes ni d'une estimation statistique du taux de panne. L'archive 11872343852 permet ici de rattacher directement la trace 05:40 au rejeu ; les rapports 035 ne disposaient que de l'archive 05:33 et du log 05:40 séparés.

Les échecs contiennent REQUEST, PREFLIGHT, HOST_ATTACH_STATE, REFUSED et CLEANUP, **aucun PAUSE_SENT, PLAYER_READY ou PLAY_SENT**. L'instrumentation échoue effectivement sur NOMINAL_PHASE/ANDROID_OWNER_ASSERTION ; ce refus sécurisé n'est pas requalifié comme réussite.

### Cause : ce qui est établi et ce qui ne l'est pas

`AndroidLocalVideoPort.preparationFailure` convertit toute IllegalStateException à l'étape HOST_ATTACH en HOST_ATTACH_STATE et ne conserve pas son origine précise. `SceneRenderer.attachLocalVideo` peut explicitement refuser un host occupé ou l'absence de `Settings.canDrawOverlays`; le framework peut aussi refuser l'attachement. Dans le premier cas nominal du harness, renderer et host sont nouvellement créés : une ancienne présentation locale conservée n'est pas soutenue par ce parcours. Cela n'exclut pas un problème de lifecycle/framework.

Le script accorde `SYSTEM_ALERT_WINDOW` via appops puis lance l'instrumentation, sans conserver une mesure de `canDrawOverlays` au moment exact du refus. Une disponibilité transitoire de permission est une **hypothèse**, pas une cause démontrée. La [documentation Android Settings](https://developer.android.com/reference/android/provider/Settings#canDrawOverlays(android.content.Context)) confirme que déclarer la permission ne remplace pas l'autorisation effective contrôlée par cette API.

Comparaison des logs de l'échec et des réussites : même émulateur annoncé 37.2.12.0 / build 16428233, API 35 google_apis x86_64, gfxstream et options headless/swiftshader identiques (`-no-window -no-snapshot -noaudio -no-boot-anim -gpu swiftshader_indirect`). La révision cmdline-tools annoncée est 12 ; elle n'est pas une preuve de version de l'image système. Le digest/revision exact de cette image n'est pas conservé dans les preuves relues. Aucune différence documentée n'explique l'intermittence. Le dernier run PR passe : une règle systématique « push fonctionne / PR échoue » est contredite.

**Cause racine inconnue.** Il manque le résultat d'autorisation au point d'attachement et l'origine précise de l'exception pour départager définitivement permission, framework et lifecycle. Aucun défaut applicatif reproductible identique n'a été établi localement ; aucun émulateur local n'a été relancé. Le candidat n'a pas reçu de correction opérationnelle Android entre les échecs et les succès.

### Effet sur la décision Sony

NON-BLOCKER pour la décision de qualification **Sony/API 31 uniquement** : les refus observés interviennent avant contrôle du programme natif ; l'entrée de production refuse API 35 ; la plateforme API 31 candidate passe ses cinq cas (163 updates de frames dans l'archive PR [11873244310](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38521313109/artifacts/11873244310)). Il n'existe dans cet échantillon aucune démonstration de PAUSE sans reprise sûre causée par HOST_ATTACH_STATE.

Cela ne prouve pas que Sony est immunisée à un refus d'overlay. Le futur protocole doit vérifier l'autorisation réelle, consigner tout refus et interrompre un cas refusé sans tentative de contournement. La réserve API 35 reste ouverte pour une éventuelle cible 35 ou une qualification plateforme générale. Aucun succès actuel ne la « corrige » rétroactivement. Ce classement limité repose sur le fail-safe avant PAUSE et la cible ratifiée, pas sur une répétition jusqu'au vert.

## 9. APK et adéquation du protocole Sony

La candidate pertinente est celle du HEAD **47f675db…**, issue de l'[archive logiciel PR 11873723967](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38521313109/artifacts/11873723967). Son archive ZIP téléchargée a été vérifiée contre le digest GitHub : `20246a6601e0145fbbaa53834fb904db35397a74854a5056bc7228617f2f5b0b`.

- APK opt-in extraite : **2 305 017 octets**, SHA-256 **`607d04f63d8fd3b9b653c5bc815f0b6569291c0bdb8e044b34b480df916f39e5`**.
- Fixture extraite de cette APK : **2 146 888 octets**, SHA-256 **`c66ded0b6a2ea41ef34320a289f2c1ec37e2a87d34ef580d9ac45d08815682ec`** ; égalité avec la fixture source.
- Nouveau ffprobe indépendant : durée 10,000 s, H.264 Main level 3.1, 1280×720, 30 fps, AAC-LC 48 kHz mono. Ce contrôle décrit le fichier ; il ne mesure ni le son ni le décodage Sony.
- Configuration source/CI : applicationId `com.scenevibe.tvcompanionpoc`, opt-in debug, versionCode 14, versionName `0.8.2-tv-release-hardening-e5-native035`, Cloud vide et LAN OFF. L'APK OFF reste distincte.

L'APK de 102a citée dans les rapports 035 n'est pas interchangeable par son seul nom : son hash diffère. Le futur mandat physique doit retenir explicitement cette identité ou une reconstruction compatible traçable. Le certificat de l'application actuellement installée sur Sony n'a pas été consulté ; sa compatibilité de mise à jour n'est donc pas qualifiée. **Préflight signature/version/données obligatoire avant toute installation future** ; incompatibilité = STOP, sans désinstallation ni reset pairing.

Le [protocole Sony 035](https://github.com/pierre22400/scenevibe-tv-companion/blob/47f675dbd05218fb1caca2b090fd81c822e513e0/docs/e5-native-035/scenevibe-e5-native-035-sony-protocol.md) est adapté à cette étape, avec l'identité APK actuelle précisée par ce rapport : mesures du modèle/firmware/Prime, droits overlay/notification/Usage Access, VOD et session unique, trois nominales, positions/ancre/timestamps, télécommande et son, incidents sans PLAY automatique, contrôle pairing/cache/révision, reprise manuelle après incident, rollback OFF compatible. Les budgets et le 0 ms devront être observés sans élargissement silencieux. La réserve NB02 doit faire distinguer une confirmation réellement reçue dans le budget d'une réussite tardive.

## 10. Findings

Aucun BLOCKER démontré pour la décision limitée demandée.

| ID / classe | Constat, conséquence et suite minimale |
|---|---|
| NB01 — NON-BLOCKER | Intermittence API 35 HOST_ATTACH_STATE non expliquée : deux refus sécurisés avant PAUSE, trois succès dans l'échantillon. Ne pas déclarer sa cause corrigée. Pour diagnostic ultérieur, conserver canDrawOverlays/AppOps et code d'origine borné au point de refus, sous mandat distinct ; aucune instrumentation ajoutée ici. Non bloquant pour la cible Sony31 et un protocole qui s'arrête au refus. |
| NB02 — NON-BLOCKER | `confirmPlaying` contrôle la fraîcheur et l'identité après query, mais ne refuse pas un résultat PLAYING qui revient après les 3 s nominaux. Reproduction indépendante : PLAY autorisé, query commencée à l'intérieur du budget, horloge avançant de 3 001 ms durant observe, snapshot PLAYING frais ; résultat CONFIRMED. `confirmPaused` refuse le cas équivalent. `tickLocal` vérifie son deadline avant query, pas après. Conséquence : succès diagnostique potentiellement tardif ; aucun PLAY supplémentaire/aveugle ni validation d'une autre cible. Pas un obstacle à un essai physique contrôlé ; ne pas annoncer un budget dur de bout en bout. Correction minimale éventuelle : vérification post-query du budget PLAYING cohérente avec PAUSED, avec test, dans une mission séparée. Non réalisée. |
| OBS03 — OBSERVATION | Le cleanup libère les références, listeners, player, Surface et descripteur. `resourcesReleasedForTest` ne prouve pas à lui seul la disparition physique du fichier ; le retour de `File.delete()` n'est pas contrôlé. Aucun leak observé/démontré dans les preuves relues ; ne pas inventer une fuite. La qualification physique doit vérifier le cache et le cycle de service. |
| OBS04 — OBSERVATION | Les sept rapports 035 restent ancrés à 102a et à son ancien gate API35. Ce rapport apporte les références actuelles et le classement indépendant, sans réécrire le passé ni modifier la candidate. |
| OBS05 — OBSERVATION | L'intégration du vrai port MediaController/foreground dans OverlayService est revue statiquement ; les plateformes exécutent un port natif simulé. Compatibilité Prime/OEM, son, télécommande, signer installé et hard reboot restent exclusivement des preuves physiques futures. |

Aucun finding n'a été corrigé par l'auditeur. Aucun paramètre de sécurité n'a été détendu.

## 11. Décision recommandée et arrêt

**PASS — READY FOR OWNER SONY QUALIFICATION DECISION.** Recommander une autorisation propriétaire distincte pour une qualification Sony Android 12/API 31 / Prime VOD limitée à L0 + L1, avec le protocole et les préflights ci-dessus. L'incertitude API35 ne bloque pas cette décision limitée ; elle reste consignée et ne vaut pas qualification générale.

La PR #18 reste OPEN / DRAFT / UNMERGED, son HEAD candidat inchangé. Seul ce rapport est publié sur une branche d'audit séparée. Aucun accès Sony, session Prime, ADB physique, développement, merge, déploiement, L2–L8 ou opération Cloud n'est engagé. STOP après publication.

## Annexe — Sonde indépendante reproductible

La source suivante a été compilée hors du dépôt, dans le même package, contre les classes candidates et `E5TestHarness`. Invocation JUnitCore `com.scenevibe.tvcompanionpoc.Independent036Test`, sortie 0 :

```text
JUnit version 4.13.2
.OBSERVATION NB02: PLAYING fresh returned at +3001ms is accepted, despite 3000ms nominal budget; one PLAY only.
.......
Time: 0.022
OK (8 tests)
```

```java
package com.scenevibe.tvcompanionpoc;
import org.junit.Test;
import static org.junit.Assert.*;
public final class Independent036Test {
 @Test public void fullSequenceCommandsAreNotConfirmation() {
  E5TestHarness h=new E5TestHarness(); h.requestAndReady();
  assertEquals(1,h.session.pauses); assertEquals(0,h.player.starts);
  h.controller.tickLocal(); assertEquals(0,h.player.starts);
  h.confirmPauseAndStart(); h.completeFile();
  assertEquals(1,h.session.plays); assertNotEquals(SceneRuntimeController.LocalPhase.SUCCESS,h.controller.localPhase());
  h.confirmPlaying(); assertEquals(SceneRuntimeController.LocalPhase.SUCCESS,h.controller.localPhase());
  assertFalse(h.owner.hasLease()); assertFalse(h.controller.isLocalPresentationActive());
  assertTrue(h.events.indexOf("PAUSED_CONFIRMED")<h.events.indexOf("PLAYER_STARTED"));
  assertTrue(h.events.indexOf("TARGET_REVALIDATED")<h.events.indexOf("PLAY_SENT"));
 }
 @Test public void noPlayAfterSurfaceFailureAndLateEof() {
  E5TestHarness h=new E5TestHarness(); h.requestAndReady(); h.confirmPauseAndStart();
  long old=h.player.generation; LocalVideoPort.Callbacks cb=h.player.callbacks;
  cb.error(old,"SURFACE_LOST"); cb.eof(old); cb.ready(old); h.controller.tickLocal();
  assertEquals(0,h.session.plays); assertFalse(h.owner.hasLease());
  assertEquals(1,h.player.releases); assertEquals(SceneRuntimeController.LocalPhase.RECOVERY_REQUIRED,h.controller.localPhase());
 }
 @Test public void eofAfterSeekCannotResume() {
  E5TestHarness h=new E5TestHarness(); h.requestAndReady(); h.confirmPauseAndStart();
  h.clock.now+=10000; h.session.current=h.sample(NativeSessionSnapshot.PAUSED,300101,h.clock.now,h.clock.now);
  h.player.state=LocalVideoPort.State.STOPPED; h.player.callbacks.eof(h.player.generation);
  assertEquals(0,h.session.plays); assertEquals(SceneRuntimeController.LocalPhase.RECOVERY_REQUIRED,h.controller.localPhase()); assertFalse(h.owner.hasLease());
 }
 @Test public void expiredClipCannotResumeAndStopIsIdempotent() {
  E5TestHarness h=new E5TestHarness(); h.requestAndReady(); h.confirmPauseAndStart();
  h.clock.now+=13001; h.refreshPaused(); h.controller.tickLocal();
  h.controller.cancelLocal("STOP"); h.player.callbacks.eof(h.player.generation);
  assertEquals(0,h.session.plays); assertEquals(1,h.player.releases); assertFalse(h.owner.hasLease());
 }
 @Test public void oldGenerationCannotRetireNewAttempt() {
  E5TestHarness h=new E5TestHarness(); h.requestAndReady();
  long old=h.player.generation; LocalVideoPort.Callbacks cb=h.player.callbacks;
  h.controller.cancelLocal("STOP"); h.session.current=h.sample(NativeSessionSnapshot.PLAYING,300000,h.clock.now,h.clock.now);
  assertTrue(h.controller.requestLocal(LocalInterludePlan.qualification(true)));
  long newer=h.player.generation; assertNotEquals(old,newer); cb.ready(old); cb.error(old,"OLD"); cb.eof(old);
  assertEquals(SceneRuntimeController.LocalPhase.PREPARING,h.controller.localPhase());
  assertEquals(1,h.session.pauses); assertEquals(0,h.session.plays); h.player.ready(); assertEquals(2,h.session.pauses);
 }
 @Test public void secondPresentationNeverAcquiresLease() {
  E5TestHarness h=new E5TestHarness(); h.requestAndReady();
  assertFalse(h.controller.requestLocal(LocalInterludePlan.qualification(true))); assertEquals(1,h.player.preparations);
  h.controller.cancelLocal("SERVICE_DESTROYED"); assertEquals(0,h.session.plays); assertFalse(h.owner.hasLease());
 }
 static final class SlowPort implements NativeSessionPort {
  long now=100000; long delay; Object token=new Object(); int state=NativeSessionSnapshot.PLAYING; long updated=100000; int plays;
  NativeSessionSnapshot sample(){return new NativeSessionSnapshot(token,NativeQualificationPolicy.PRIME_PACKAGE,"programme-one","Specific programme","episode",3000000,300000,now,updated,state==NativeSessionSnapshot.PLAYING?1f:0f,state,true,true,true);}
  public NativeSessionSnapshot select(boolean v){return sample();}
  public NativeSessionSnapshot observe(NativeSessionSnapshot a){now+=delay; return sample();}
  public boolean pause(NativeSessionSnapshot a,NativeQualificationPolicy p,long n){return true;}
  public boolean play(NativeSessionSnapshot a,NativeQualificationPolicy p,long n){plays++;return true;}
 }
 @Test public void pauseReturningAfterDeadlineIsRejected() {
  SlowPort p=new SlowPort(); NativeTransportOwner n=new NativeTransportOwner(p,NativeQualificationPolicy.local035(),(e,g,s,r)->{},()->p.now);
  assertTrue(n.requestPause(1,p.now,true)); p.state=NativeSessionSnapshot.PAUSED; p.updated=p.now; p.delay=3001;
  assertEquals(NativeTransportOwner.Result.REFUSED,n.confirmPaused(1,p.now)); assertEquals(NativeTransportOwner.Reason.PAUSE_NOT_CONFIRMED,n.reason()); assertEquals(0,p.plays);
 }
 @Test public void characterizeLatePlayingConfirmationWithoutExtraPlay() {
  SlowPort p=new SlowPort(); NativeTransportOwner n=new NativeTransportOwner(p,NativeQualificationPolicy.local035(),(e,g,s,r)->{},()->p.now);
  assertTrue(n.requestPause(1,p.now,true)); p.state=NativeSessionSnapshot.PAUSED; p.updated=p.now;
  assertEquals(NativeTransportOwner.Result.CONFIRMED,n.confirmPaused(1,p.now)); assertTrue(n.requestPlay(1,p.now,true));
  p.state=NativeSessionSnapshot.PLAYING; p.updated=p.now; p.delay=3001;
  assertEquals(NativeTransportOwner.Result.CONFIRMED,n.confirmPlaying(1,p.now)); assertEquals(1,p.plays);
  System.out.println("OBSERVATION NB02: PLAYING fresh returned at +3001ms is accepted, despite 3000ms nominal budget; one PLAY only.");
 }
}
```
