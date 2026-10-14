# E5-NATIVE-035 — Matrice de non-régression

Date : 14 octobre 2026. Mission de finalisation documentaire uniquement. GitHub est la source de vérité.

- Base TV : `17cbe36ae99ac7f48aaf861e0d1feac702a9e521`.
- HEAD logiciel examiné : [`102a0193e862abe64e28d94614eb2cd0fb0e2488`](https://github.com/pierre22400/scenevibe-tv-companion/commit/102a0193e862abe64e28d94614eb2cd0fb0e2488).
- Branche : `work/scenevibe-e5-native-l0-l1-035`.
- [PR #18](https://github.com/pierre22400/scenevibe-tv-companion/pull/18) : OPEN / DRAFT / UNMERGED au contrôle.
- Autorité fonctionnelle : [clôture WORK 034 et six décisions ratifiées](https://github.com/pierre22400/interface-scenevibe/blob/8376d64d51ac35d699f6dd443f0cc4c364a806b1/docs/studio/phase-c1/scenevibe-e5-work-034-owner-closure.md).

**Statut : NOT READY — API35 PLATFORM GATE.** Le run E5 push réussit sur API 31/35 ; le dernier rejeu de la PR échoue sur API 35. Le succès isolé ne ferme pas cette réserve. Aucun essai physique Sony ; aucune nouvelle correction technique par WORK dans ce mandat.


## Comptage réel au HEAD logiciel

| Preuve | PASS | FAIL/ERROR | SKIPPED | Total |
|---|---:|---:|---:|---:|
| JVM général, log du job115697448759 | 1114 | 0 | 1 | 1115 |
| XML JVM du candidat OFF, artefact11871694729 | 1114 | 0 | 1 | 1115 |
| XML JVM du candidat ON, même artefact | 1114 | 0 | 1 | 1115 |
| Python général, log CI | 125 | 0 | 0 | 125 |
| Python baseline exacte17c, étape du workflow E5 | 118 | 0 | 0 | 118 |

Les XML OFF/ON ont été reconcomptés dans l'archive ; les passages ne sont pas additionnés comme des tests distincts. Le seul skip est M1CloudInteropTest.originalColumboProjectionIsInstallable, opt-in historique lorsque SCENEVIBE_M1_SONY_FIXTURES manque. Aucun nouveau skip.

Les64 nouveaux succès sont reconnus explicitement par le résumé corrigé 102a :10+30+24 dans trois suites. Les noms/comptages des suites figées et la liste des skips autorisés sont conservés. La baseline de développement1050 PASS/1 SKIP est distinguée du recomptage actuel1114 PASS/1 SKIP. Cette finalisation ne reconstruit pas une nouvelle comparaison locale exhaustive des identifiants individuels historiques.

## Matrice de preuves

| Exigence | Suite ou contrôle | Résultat |
|---|---|---|
| Nominal EOF, PAUSED et PLAYING séparés | SceneRuntimeLocalInterludeTest ; NativeTransportOwnerTest | PASS JVM ; cas Android API 31 PASS |
| Native initialement PAUSED/live/inconnu | Tests d'admission/attestation/durée | PASS, absence de commandes |
| Session ambiguë, token/programme remplacé | NativeTransportOwnerTest | PASS, pas de substitution |
| Position indisponible/vieille/future ; seek | Garde de position et marque provider | PASS JVM |
| Pause envoyée mais non confirmée | Tests de confirmation/timeout | PASS, aucun start/PLAY |
| Seconde demande | Tests de bail et régie | PASS, sans préemption/queue |
| EOF doublé/prématuré ; PLAY non confirmé | Tests de régie/transport | PASS, pas de succès artificiel |
| Callback après annulation/release/ancienne génération | Régie et sondes Android | PASS JVM ; API 31 annulation réelle PASS |
| Erreur/watchdog/STOP/session perdue | Régie/transport | PASS, cleanup sans PLAY |
| Surface retirée et fenêtre passive | Sonde Android de retrait réel | API 31 PASS ; flags vérifiés |
| Fichier absent/corrompu | Préparation simulée JVM ; fichier réellement absent Android ; hash fixe | PASS dans leur scope ; pas de campagne physique de corruption |
| OFF/restart | Tests de flag, unload et nouveau propriétaire sans bail | PASS |
| DUE/EXPIRE/révision/éligibilité Track | Tests historiques + régie E5 | PASS JVM ; différentiel MEDIA SUCCESS |
| Installation/pairing/cache/durabilité | Workflow Android historique | SUCCESS ; aucune nouvelle preuve Sony |
| MEDIA/Cloud/contrat graphique v1 | Fichiers protégés et suites historiques | Contrats conservés ; aucune émission réelle |
| Profil Banner M6 | Branche non fusionnée, hors delta | Aucun test d'un M6 intégré à main prétendu |

Sources de suites : [tests JVM E5](https://github.com/pierre22400/scenevibe-tv-companion/tree/102a0193e862abe64e28d94614eb2cd0fb0e2488/app/src/test/java/com/scenevibe/tvcompanionpoc), [sonde Android](https://github.com/pierre22400/scenevibe-tv-companion/blob/102a0193e862abe64e28d94614eb2cd0fb0e2488/app/src/e5QualificationAndroidTest/java/com/scenevibe/tvcompanionpoc/E5NativeInstrumentation.java).

## Invariants historiques et correctives

La reconstruction par e5_native_provenance est limitée aux anciennes assertions de sources/inventaires. Elle exige hashes complets before/after et inverses littéraux uniques ; pas de wildcard. Le candidat réel est compilé/exécuté pour JVM et instrumentation. Les7 tests Python E5 vérifient bytes/inventaire réels, absence de mutation cachée, contrats protégés, dispatcher/host uniques, fixture et invariants des méthodes/assertions/docstrings.

b02e063 rend cette comparaison indépendante de la profondeur Git en validant les bytes reconstruits.102a019 ajoute précisément les 64 tests au résumé figé ; il n'efface pas les anciennes suites ni le skip opt-in. Les succès de développement ne remplacent pas une revue indépendante.

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
