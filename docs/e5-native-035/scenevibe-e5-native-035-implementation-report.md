# E5-NATIVE-035 — Rapport d'implémentation et finalisation

Date : 14 octobre 2026. Mission de finalisation documentaire uniquement. GitHub est la source de vérité.

- Base TV : `17cbe36ae99ac7f48aaf861e0d1feac702a9e521`.
- HEAD logiciel examiné : [`102a0193e862abe64e28d94614eb2cd0fb0e2488`](https://github.com/pierre22400/scenevibe-tv-companion/commit/102a0193e862abe64e28d94614eb2cd0fb0e2488).
- Branche : `work/scenevibe-e5-native-l0-l1-035`.
- [PR #18](https://github.com/pierre22400/scenevibe-tv-companion/pull/18) : OPEN / DRAFT / UNMERGED au contrôle.
- Autorité fonctionnelle : [clôture WORK 034 et six décisions ratifiées](https://github.com/pierre22400/interface-scenevibe/blob/8376d64d51ac35d699f6dd443f0cc4c364a806b1/docs/studio/phase-c1/scenevibe-e5-work-034-owner-closure.md).

**Statut : NOT READY — API35 PLATFORM GATE.** Le run E5 push réussit sur API 31/35 ; le dernier rejeu de la PR échoue sur API 35. Le succès isolé ne ferme pas cette réserve. Aucun essai physique Sony ; aucune nouvelle correction technique par WORK dans ce mandat.


## Identités et périmètre

Le [diff logiciel complet](https://github.com/pierre22400/scenevibe-tv-companion/compare/17cbe36ae99ac7f48aaf861e0d1feac702a9e521...102a0193e862abe64e28d94614eb2cd0fb0e2488) contient 44 fichiers. Les autorités d'identité/calendrier MEDIA, parseur graphique v1, CloudProtocol, stockage durable et manifeste principal ne sont pas modifiés dans ce diff. PR M6 #16 et Spike #17 n'ont pas été fusionnées dans cette livraison.

Le présent mandat publie uniquement les sept rapports. HEAD logiciel final :102a0193… ; le HEAD documentaire final exact est remis dans le corps de la PR et dans la livraison. Un document ne peut pas contenir sa propre empreinte future. Le diff logiciel→documentaire doit être limité à ces sept Markdown, sans modification applicative, test, workflow ou registre.

## Correctives prises en charge par le propriétaire

- [b02e063](https://github.com/pierre22400/scenevibe-tv-companion/commit/b02e063c61780904fb868f660f3496e45f70b728) : le test d'invariants historiques n'appelle plus git show sur une histoire profonde. Il utilise les bytes historiques reconstruits et vérifie leur hash de blob complet avant comparaison AST. Le code applicatif n'est pas modifié.
- [102a019](https://github.com/pierre22400/scenevibe-tv-companion/commit/102a0193e862abe64e28d94614eb2cd0fb0e2488) : le résumé JVM figé reconnaît exactement E5LocalPlanAndForegroundTest 10, NativeTransportOwnerTest 30 et SceneRuntimeLocalInterludeTest 24. Les anciennes suites et règles de skip restent ; toute addition différente est refusée. Le registre de provenance décrit l'inverse précis de cette correction.
- Ces deux commits et leurs sorties ont été relus. WORK ne les réimplémente pas et ne fait pas de nouvelle correction dans ce mandat documentaire.

Les étapes précédentes avaient isolé le source set AndroidTest E5 des contrôles négatifs historiques, démarré explicitement le worker depuis onCreate et borné am instrument. Les diagnostics précisent désormais l'étape de préparation sans exposer chemin ou message arbitraire. Ces changements appartiennent au logiciel déjà livré, pas à ce commit documentaire.

## Parcours livré et limites

Le plan local fixe est préparé avant pause ; pause réellement observée avant start ; EOF puis garde token/programme/position ; PLAY puis PLAYING ; cleanup. STOP, erreur, perte de Surface/session/foreground, seek ou timeout ne provoquent pas de PLAY automatique. Les graphiques et le clip partagent le SceneRenderer. Aucun moteur général de montage ni priorité Banner/Video.

API 31 termine les 5 cas de plateforme ; API 35 a un succès et un dernier rejeu en échec. L'échec à l'attachement HOST_ATTACH_STATE est documenté, mais sa cause exacte/intermittence n'est pas corrigée ou arbitrée ici. Aucun SOFTWARE PASS final ni READY FOR INDEPENDENT AUDIT.

## Fixture et droits documentés

La fixture Spike a été récupérée inchangée : MP4 de2 146 888 octets, SHA-256 `c66ded0b6a2ea41ef34320a289f2c1ec37e2a87d34ef580d9ac45d08815682ec`. La copie dans l'APK de102a a été hashée à nouveau pendant cette finalisation et correspond exactement.

Propriétés consignées dans le [NOTICE](https://github.com/pierre22400/scenevibe-tv-companion/blob/102a0193e862abe64e28d94614eb2cd0fb0e2488/app/src/e5Qualification/assets/NOTICE-E5-035.txt) :10,000 s, H.264 Main level 3.1,1280×720,30 fps, AAC-LC48 kHz mono et tonalité440 Hz. Ces métadonnées ne sont pas présentées comme un nouveau ffprobe exécuté aujourd'hui. Extrait Big Buck Bunny, ©2008 Blender Foundation, CC-BY3.0 ; réencodage vidéo/audio synthétique du générateur Spike. Aucun contenu Prime capturé. L'audibilité physique demeure non testée.

## Inventaire logiciel exhaustif

- `.github/scripts/e5-native-035-platform.sh`
- `.github/scripts/e5-native-035-provenance.json`
- `.github/scripts/e5-native-035.init.gradle`
- `.github/scripts/m4-phase-a-test-summary.py`
- `.github/workflows/e5-native-035-software.yml`
- `app/build.gradle`
- `app/src/e5Qualification/AndroidManifest.xml`
- `app/src/e5Qualification/assets/NOTICE-E5-035.txt`
- `app/src/e5Qualification/assets/scenevibe_interlude_10 s.mp4`
- `app/src/e5QualificationAndroidTest/java/com/scenevibe/tvcompanionpoc/E5NativeInstrumentation.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/AndroidForegroundEvidence.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/AndroidLocalVideoPort.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/AndroidNativeSessionPort.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/DiagnosticsActivity.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/LocalInterludePlan.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/LocalMediaResource.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/LocalVideoPort.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/NativeDiagnostics.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/NativeForegroundProof.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/NativeQualificationPolicy.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/NativeSessionPort.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/NativeSessionSnapshot.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/NativeTransportOwner.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayRenderer.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/OverlayService.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/SceneRenderer.java`
- `app/src/main/java/com/scenevibe/tvcompanionpoc/SceneRuntimeController.java`
- `app/src/test/java/com/scenevibe/tvcompanionpoc/E5LocalPlanAndForegroundTest.java`
- `app/src/test/java/com/scenevibe/tvcompanionpoc/E5TestHarness.java`
- `app/src/test/java/com/scenevibe/tvcompanionpoc/NativeTransportOwnerTest.java`
- `app/src/test/java/com/scenevibe/tvcompanionpoc/SceneRuntimeLocalInterludeTest.java`
- `tests/e5_native_provenance.py`
- `tests/m5_phase_d_provenance.py`
- `tests/test_e5_native_boundary.py`
- `tests/test_m4_phase_b_boundary.py`
- `tests/test_m4_phase_c_boundary.py`
- `tests/test_m4_phase_d_boundary.py`
- `tests/test_m4_phase_e_boundary.py`
- `tests/test_m4_phase_f_boundary.py`
- `tests/test_m4_phase_g_boundary.py`
- `tests/test_m4_sony_corrective_boundary.py`
- `tests/test_m5_phase_b_boundary.py`
- `tests/test_m5_phase_c_boundary.py`
- `tests/test_m5_phase_d_boundary.py`

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
