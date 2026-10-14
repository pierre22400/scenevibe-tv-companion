# E5-NATIVE-035 — Protocole Sony ultérieur, non exécuté

Date : 14 octobre 2026. Mission de finalisation documentaire uniquement. GitHub est la source de vérité.

- Base TV : `17cbe36ae99ac7f48aaf861e0d1feac702a9e521`.
- HEAD logiciel examiné : [`102a0193e862abe64e28d94614eb2cd0fb0e2488`](https://github.com/pierre22400/scenevibe-tv-companion/commit/102a0193e862abe64e28d94614eb2cd0fb0e2488).
- Branche : `work/scenevibe-e5-native-l0-l1-035`.
- [PR #18](https://github.com/pierre22400/scenevibe-tv-companion/pull/18) : OPEN / DRAFT / UNMERGED au contrôle.
- Autorité fonctionnelle : [clôture WORK 034 et six décisions ratifiées](https://github.com/pierre22400/interface-scenevibe/blob/8376d64d51ac35d699f6dd443f0cc4c364a806b1/docs/studio/phase-c1/scenevibe-e5-work-034-owner-closure.md).

**Statut : NOT READY — API35 PLATFORM GATE.** Le run E5 push réussit sur API 31/35 ; le dernier rejeu de la PR échoue sur API 35. Le succès isolé ne ferme pas cette réserve. Aucun essai physique Sony ; aucune nouvelle correction technique par WORK dans ce mandat.


**SONY PHYSICAL QUALIFICATION — NOT EXECUTED.** Ce protocole décrit une campagne future ; il n'autorise ni connexion ADB, installation, test ou modification de TV. Le gate API 35 actuel reste ouvert. Un GO propriétaire distinct et une revue indépendante préalable sont nécessaires.

## Préflight futur

Consigner Sony/modèle/firmware, Android/API, Prime/version, programme VOD, conditions et identifiant pseudonymisé. Le profil réel est Sony/API 31 uniquement ; live, inconnus ou non contrôlables refusés. Pas de capture de contenu Prime.

Identifier HEAD logiciel retenu, APK exacte/SHA, applicationId/versionCode, certificat et fixture. La candidate logicielle de102a est liée dans le rapportsoftware, sans qualification de signature Sony. Les APK de jobs/locales peuvent avoir des certificats différents : ne pas les interchanger. **Signature incompatible avec mise à jour conservant les données ⇒ STOP.** Pas de désinstallation, clear data, reset pairing ou contournement. Préparer séparément une candidate compatible sous autorisation appropriée.

Avant installation future : relever état d'installation/pairing/cache/révision, droits overlay/notificationlistener, service et autostart, sans divulguer les secrets. Confirmer Cloud vide/LAN OFF et opt-in debug. Vérifier disponibilités Settings/UsageAccess ; accord manuel explicitement prévu dans le GO physique. Absence de preuve foreground = refus sans pause. Aucun réglage n'a été changé ici.

## Nominal répété

Au moins trois passages, mêmes paramètres. Prime VOD PLAYING/contrôlable/session unique ; commande Diagnostics manuelle, attestation et retour à Prime. Vérifier READY réel avant PAUSE, PAUSED observé avant start, mouvement et tonalité audibles, graphiques/clip dans le même renderer, EOF normal, revalidation et PLAYING confirmé. Contrôler libération de host/player/Surface/descripteur/fichier privé/bail.

Relever positions avant PAUSE, ancre PAUSED, marque provider pendant clip, pré-PLAY et après reprise. Distinguer avance normale de commande, dérive et seek. Mesurer délais/âges et compatibilité du0 ms stationnaire sans changer ces paramètres pendant la campagne. Faux refus OEM ⇒ documenter puis correction/revue distinctes ; pas d'élargissement silencieux.

Tester la télécommande avant/pendant/après ; les flags logiciels ne prouvent pas son fonctionnement OEM. Observer audio Prime/tonalité/retour sans affirmer politique de mixage ou ducking. Aucun AudioFocus demandé ; aucune qualification publicitaire ou générale D05-V.

## Refus/incidents nécessaires

| Cas futur | Attendu |
|---|---|
|Prime déjà PAUSED / VOD non attestée |Refus, aucune appropriation |
|Deuxième demande |Refus, pas de queue/préemption |
|HOME/app/programme/token changé |Cleanup, aucun PLAY à nouvelle cible |
|Seek ou commande manuelle pendant pause |Garde invalidée ; reprise manuelle |
|STOP ou player/Surface/permission perdus |Cleanup et RECOVERY_REQUIRED après pause ; aucun PLAY automatique |
|Pause/PLAY non confirmé |Timeout fini, aucun faux succès/retry aveugle |
|Service détruit/restart |Aucun bail ou PLAY restauré |

Les corruptions et callbacks difficiles à provoquer restent couverts d'abord par tests logiciels ; ne pas modifier l'application sur TV pour les créer. Aucune manipulation de live/publicité n'est requise pour qualifier ce profil VOD limité.

## Nettoyage, rollback et critères

Consigner diagnostic terminal et absence de fuite/fenêtre après chaque passage ; vérifier pairing/cache/révision et M5 sans Send/ACK réel. Après incident, Prime peut rester PAUSED : récupération par télécommande, pas PLAY de nettoyage.

Rollback futur : reconstruire OFF, préserver signature et données, respecter contraintes versionCode. Incompatibilité downgrade/signature ⇒ STOP ; aucune désinstallation/reset. Aujourd'hui aucune TV ou production modifiée, aucun rollback exécuté.

PASS physique exigerait nominales répétées, positions/confirmations, son/vidéo, télécommande, refus et non-régression pairing/service, avec rapport indépendant sur APK/versions. Un build, un émulateur ou le Spike ancien ne remplace pas cette preuve. Merge/production restent des décisions distinctes.

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

