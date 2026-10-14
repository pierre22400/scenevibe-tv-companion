# E5-NATIVE-035 — Spécification du transport natif

Date : 14 octobre 2026. Mission de finalisation documentaire uniquement. GitHub est la source de vérité.

- Base TV : `17cbe36ae99ac7f48aaf861e0d1feac702a9e521`.
- HEAD logiciel examiné : [`102a0193e862abe64e28d94614eb2cd0fb0e2488`](https://github.com/pierre22400/scenevibe-tv-companion/commit/102a0193e862abe64e28d94614eb2cd0fb0e2488).
- Branche : `work/scenevibe-e5-native-l0-l1-035`.
- [PR #18](https://github.com/pierre22400/scenevibe-tv-companion/pull/18) : OPEN / DRAFT / UNMERGED au contrôle.
- Autorité fonctionnelle : [clôture WORK 034 et six décisions ratifiées](https://github.com/pierre22400/interface-scenevibe/blob/8376d64d51ac35d699f6dd443f0cc4c364a806b1/docs/studio/phase-c1/scenevibe-e5-work-034-owner-closure.md).

**Statut : NOT READY — API35 PLATFORM GATE.** Le run E5 push réussit sur API 31/35 ; le dernier rejeu de la PR échoue sur API 35. Le succès isolé ne ferme pas cette réserve. Aucun essai physique Sony ; aucune nouvelle correction technique par WORK dans ce mandat.


## Bail et snapshot

[NativeTransportOwner](https://github.com/pierre22400/scenevibe-tv-companion/blob/102a0193e862abe64e28d94614eb2cd0fb0e2488/app/src/main/java/com/scenevibe/tvcompanionpoc/NativeTransportOwner.java) conserve un bail unique en RAM : tentative/génération, cible initiale, temps de notre PAUSE, snapshot PAUSED confirmé et preuve/temps de PLAY. Aucun restore, transfert ou persist.

[AndroidNativeSessionPort](https://github.com/pierre22400/scenevibe-tv-companion/blob/102a0193e862abe64e28d94614eb2cd0fb0e2488/app/src/main/java/com/scenevibe/tvcompanionpoc/AndroidNativeSessionPort.java) réacquiert le controller/token à chaque lecture et dispatch. Métadonnées/état/position viennent du même controller, avec seconde lecture rejetant une incohérence. Prime seul, foreground prouvé, pas de sélection arbitraire sur package. Token/programme/durée/métadonnées exacts ; pas de matcher permissif ni modification de MediaIdentityMatcher historique. Le sous-titre de métadonnées n'est pas une extraction des captions TV.

Les temps de réception et de mise à jour provider sont distincts. Le temps monotone de garde est lu après l'observation. Position manquante, négative, future ou trop ancienne = refus. Une relecture IPC n'est pas une transaction atomique avec les actions de l'utilisateur ; aucune garantie universelle d'absence de course invisible.

## Conditions et transitions

| Phase | Condition positive | Incident |
|---|---|---|
| PREPARING |Graphiques préflightés ; fichier/hash/pistes/codec/Surface prêts |REFUSED avant PAUSE |
| PAUSE_REQUESTED |VOD attestée, PLAYING vitesse1, actions PAUSE/PLAY ; dispatch revalidé |Envoi ≠ PAUSED ; timeout/échec confirmé distinct |
| PLAYER_STARTING |PAUSED observé après notre envoi, position compatible |Aucun start sous hypothèse de pause |
| PLAYER_PLAYING |Player réellement démarré ; bail observé périodiquement |Seek/état/session/app perdus ⇒ cleanup sans PLAY |
| RESUME_REQUESTED |EOF normal, STOPPED réel, lecteur libéré, ancre PAUSED revalidée |EOF seul n'autorise pas PLAY |
| SUCCESS |PLAY envoyé puis PLAYING observé avec avance compatible |Sans confirmation : RECOVERY_REQUIRED |

Au dispatch PAUSE : relecture PLAYING/vitesse1/actions ; au dispatch PLAY : token/programme, PAUSED/vitesse0, actionPLAY, même position brute et marque provider. Aucune nouvelle session ne reçoit PLAY pour réparer la précédente.

## Profil borné, non mesuré sur Sony

[NativeQualificationPolicy](https://github.com/pierre22400/scenevibe-tv-companion/blob/102a0193e862abe64e28d94614eb2cd0fb0e2488/app/src/main/java/com/scenevibe/tvcompanionpoc/NativeQualificationPolicy.java) :

| Paramètre | Valeur provisoire |
|---|---:|
| Préparation/démarrage |5000 ms |
| Confirmation PAUSED |3000 ms |
| Confirmation PLAYING |3000 ms |
| Watchdog clip |13000 ms |
| Âge réception |1000 ms |
| Âge provider |20000 ms |
| Tolérance stationnaire PAUSED |0 ms |
| Poll |250 ms |

L'ancre PAUSED est brute et exacte ; changement1 ms ou nouveau marqueur provider invalide la reprise. Cela peut refuser une actualisation OEM bénigne. À la confirmation, l'avance admise est bornée par temps écoulé depuis la commande plus deux polls. Le profil20 s couvre les budgets finis du clip et confirmations ; ce n'est pas une fraîcheur garantie Android/Sony.

EOF exige player STOPPED, au moins 9500 ms depuis start et avant watchdog. La durée de la fixture ne modifie aucun événement MEDIA. STOP, erreur, timeout, expiration, Surface/session/app perdue, génération obsolète ou seek entraînent nettoyage ; après prise de pause : RECOVERY_REQUIRED, aucun PLAY automatique. Télécommande manuelle pour récupérer Prime.

## Lifecycle et diagnostics

Release du player/descripteur/Surface avant detach ; tentative invalidée avant cleanup. EOF/callbacks dupliqués ou tardifs ne commandent pas une nouvelle tentative. Une deuxième demande est refusée, sans queue/préemption.

Les diagnostics distinguent commande envoyée et état observé : TARGET_VALIDATED, PAUSE_SENT, PAUSED_CONFIRMED, PLAYER_STARTED, EOF_NORMAL, TARGET_REVALIDATED, PLAY_SENT, PLAYING_CONFIRMED, LEASE_RELEASED, CLEANUP. Phase, génération, temps monotone, position/âge disponibles et motif borné. Token/identité pseudonymisés avec sel temporaire ; pas de titres, secrets ou tokens réutilisables. HOST_ATTACH_STATE identifie une étape de préparation refusée, pas une pause ou un succès.

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
