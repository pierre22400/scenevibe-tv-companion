# E5-NATIVE-035 — Rapport de qualification logicielle

Date : 14 octobre 2026. Mission de finalisation documentaire uniquement. GitHub est la source de vérité.

- Base TV : `17cbe36ae99ac7f48aaf861e0d1feac702a9e521`.
- HEAD logiciel examiné : [`102a0193e862abe64e28d94614eb2cd0fb0e2488`](https://github.com/pierre22400/scenevibe-tv-companion/commit/102a0193e862abe64e28d94614eb2cd0fb0e2488).
- Branche : `work/scenevibe-e5-native-l0-l1-035`.
- [PR #18](https://github.com/pierre22400/scenevibe-tv-companion/pull/18) : OPEN / DRAFT / UNMERGED au contrôle.
- Autorité fonctionnelle : [clôture WORK 034 et six décisions ratifiées](https://github.com/pierre22400/interface-scenevibe/blob/8376d64d51ac35d699f6dd443f0cc4c364a806b1/docs/studio/phase-c1/scenevibe-e5-work-034-owner-closure.md).

**Statut : NOT READY — API35 PLATFORM GATE.** Le run E5 push réussit sur API 31/35 ; le dernier rejeu de la PR échoue sur API 35. Le succès isolé ne ferme pas cette réserve. Aucun essai physique Sony ; aucune nouvelle correction technique par WORK dans ce mandat.


## Verdict

**NOT READY — API35 PLATFORM GATE.** Builds/JVM/Python et plateformes historiques sont verts au HEAD logiciel ; la sonde E5 API 35 demeure instable entre deux exécutions du même HEAD. Un passage vert n'annule pas le dernier échec. Aucun essai Sony ni nouvelle qualification indépendante n'est déclaré.

## Commandes et résultats effectivement vérifiés

Le workflow E5 utilise Java 17, Gradle 8.9, SDK 35, builds isolés OFF puis opt-in local debug, Cloud vide/LAN OFF. Les logs GitHub du job software 115697434833 montrent :

| Commande/étape | Résultat observé |
|---|---|
| python3 -m unittest discover -s tests sur baseline17c |118 tests, OK |
| baseline assembleDebug/lintDebug/testDebugUnitTest |BUILD SUCCESSFUL |
| même Python sur candidat 102a |125 tests, OK |
| candidat OFF assembleDebug/assembleRelease/lintDebug/lintRelease/testDebugUnitTest |BUILD SUCCESSFUL |
| clean puis init-script E5, assembleDebug/assembleDebugAndroidTest/lintDebug/testDebugUnitTest avec token exact |BUILD SUCCESSFUL |
| JVM général + résumé corrigé 102a |PASS 1114, FAIL 0, SKIP 1, TOTAL 1115 |

Les steps positifs sont SUCCESS ; leurs commandes Gradle/Python se terminent sans code d'échec. Le script plateforme en échec termine explicitement avec code 1. Le INSTRUMENTATION_CODE0 d'un Bundle en échec n'est pas interprété comme un PASS : failed=1 et passed=0 sont lus.

Cette finalisation lit les sorties, reconcompte les XML et vérifie les empreintes. Elle ne relance aucune commande de build ou test localement. Les refus négatifs de token/Cloud/LAN/release avaient été testés durant le développement ; ils ne sont pas présentés comme nouvellement rejoués aujourd'hui. La lecture du build confirme toujours leurs gardes.

## Lint, skips et avertissements

Les XML lint debug OFF et ON de l'artefact102a contiennent25 Warning,0 Error. La baseline de développement était24 Warning ; l'ajout documenté est SetTextI18n dans DiagnosticsActivity. Les warnings sont conservés, ainsi que les notes SDK manager/Node/emulator des logs. Lint SUCCESS ne signifie pas zéro warning.

Le seul skip JVM est le corpus privé Columbo optionnel, absent ; aucune nouvelle désactivation. Les tâches Gradle marquées SKIPPED ne sont pas additionnées aux skips JUnit. Aucun accès PostgreSQL/Neon/Cloud/TV requis ou exécuté par cette finalisation.

## Résultats Android E5

| Exécution au même HEAD102a | API 31 | API 35 |
|---|---|---|
| Run38516837495, push |5 cas PASS |5 cas PASS |
| Run38516842153, dernier attempt 2 PR |SUCCESS |FAILURE,0 cas terminés,1échec de sonde |

API 31, archive11872406837 :98 mises à jour TextureView nominales ; PAUSE_SENT puis PAUSED_CONFIRMED ; PLAYER_STARTED ; EOF_NORMAL ; TARGET_REVALIDATED ; PLAY_SENT/PLAYING_CONFIRMED ; release et cleanup. Les commandes natives sont simulées. Les autres cas vérifient annulation pendant préparation, retrait de host pendant lecture/flags, fichier absent et OFF sans effet.

API 35 : le push aboutit réellement aux cinq cas. L'archive du run PR en échec montre REQUEST/PREFLIGHT → HOST_ATTACH_STATE → REFUSED/CLEANUP, sans PAUSE_SENT. Le dernier log de job attempt 2 confirme failed 1/passed 0 mais l'archive disponible a un horodatage antérieur ; elle ne suffit pas à attribuer la cause exacte du dernier rejeu. Aucun changement de permission, délai, condition ou assertion n'a été réalisé pour faire passer le cas. La cause technique et le caractère intermittent restent à traiter par le propriétaire.

Le build propre, l'extraction/codec, le transport simulé, la plateforme standard et Prime réel sont des niveaux distincts. Ni la sonde ni le Spike historique ne prouvent la télécommande, le son physique ou la reprise Prime du nouveau Companion sur Sony.

## Candidate et artefacts

[Archive logicielle E5 au HEAD 102a](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38516837495/artifacts/11871694729), ZIP2 686 143 octets ; SHA-256 `e7f2c8b99bc125bc6ac3aed3f667277de80e3a67eb906d6051c4ed68b5e8eeee`.

APK opt-in : `app/build/outputs/apk/debug/app-debug.apk`,2 305 017 octets ; SHA-256 `d885abfd4f2113bb9137848e106b2d91adfefa25a686c3f94fa8c04edd16ba12`. L'empreinte du logCI a été comparée aux bytes téléchargés. L'APK defaultOFF distinct du même ZIP fait157 123 octets, SHA-256 `37bd2900eba16996c388188c77914812e33670632c5cbcc351f5130ba8bee63a`.

La fixture embarquée est exacte :2 146 888 octets/SHA `c66ded0b6a2ea41ef34320a289f2c1ec37e2a87d34ef580d9ac45d08815682ec`, NOTICE inclus. Le certificat de cette nouvelle APK n'a pas été requalifié localement dans ce mandat ; aucune installation Sony permise. Les jobs plateformes rebâtissent à partir du même HEAD : ils ne sont pas présentés comme l'exécution des mêmes bytes/signature que l'APK du job software. Les artifacts peuvent expirer selon rétention Actions ; leur disponibilité a été vérifiée à la rédaction.

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

## Porte de sortie

Documents livrables, gate logiciel globale non fermée. **NO-GO pour recommander READY FOR INDEPENDENT AUDIT à ce stade.** Réserver les correctives au propriétaire, puis relecture de leurs preuves dans un mandat distinct. Sony, merge et production restent séparés.
