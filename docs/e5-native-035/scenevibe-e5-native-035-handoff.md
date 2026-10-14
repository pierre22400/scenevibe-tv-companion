# E5-NATIVE-035 — Handoff documentaire L0/L1

Date : 14 octobre 2026. Mission de finalisation documentaire uniquement. GitHub est la source de vérité.

- Base TV : `17cbe36ae99ac7f48aaf861e0d1feac702a9e521`.
- HEAD logiciel examiné : [`102a0193e862abe64e28d94614eb2cd0fb0e2488`](https://github.com/pierre22400/scenevibe-tv-companion/commit/102a0193e862abe64e28d94614eb2cd0fb0e2488).
- Branche : `work/scenevibe-e5-native-l0-l1-035`.
- [PR #18](https://github.com/pierre22400/scenevibe-tv-companion/pull/18) : OPEN / DRAFT / UNMERGED au contrôle.
- Autorité fonctionnelle : [clôture WORK 034 et six décisions ratifiées](https://github.com/pierre22400/interface-scenevibe/blob/8376d64d51ac35d699f6dd443f0cc4c364a806b1/docs/studio/phase-c1/scenevibe-e5-work-034-owner-closure.md).

**Statut : NOT READY — API35 PLATFORM GATE.** Le run E5 push réussit sur API 31/35 ; le dernier rejeu de la PR échoue sur API 35. Le succès isolé ne ferme pas cette réserve. Aucun essai physique Sony ; aucune nouvelle correction technique par WORK dans ce mandat.


## Livraison

**DOCUMENTATION PUBLISHED — SOFTWARE NOT READY — API35 PLATFORM GATE.**

La finalisation publie uniquement les sept rapports sur la branche existante et actualise le texte de la PR. Aucun code, test, workflow, registre de provenance, comportement ou dispositif n'est modifié. HEAD logiciel examiné :102a0193e862abe64e28d94614eb2cd0fb0e2488. HEAD documentaire final : SHA du commit portant ces rapports, remis exactement dans la PR et la réponse de livraison ; son diff contre102a doit comporter ces sept fichiers seulement.

1. [Architecture](scenevibe-e5-native-035-architecture.md)
2. [Rapport d'implémentation](scenevibe-e5-native-035-implementation-report.md)
3. [Non-régression](scenevibe-e5-native-035-non-regression.md)
4. [Qualification logicielle et artefacts](scenevibe-e5-native-035-software-qualification.md)
5. [Garde de transport](scenevibe-e5-native-035-transport-guard.md)
6. [Protocole Sony non exécuté](scenevibe-e5-native-035-sony-protocol.md)
7. Ce handoff.

## Correctives déjà publiées

[b02e063](https://github.com/pierre22400/scenevibe-tv-companion/commit/b02e063c61780904fb868f660f3496e45f70b728) restaure l'indépendance de la preuve historique vis-à-vis du checkout profond, avec hash complet avant comparaison. [102a019](https://github.com/pierre22400/scenevibe-tv-companion/commit/102a0193e862abe64e28d94614eb2cd0fb0e2488) admet précisément les 64 nouveaux tests dans le résumé JVM figé. WORK les a relues et n'a pas repris le développement.

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

## Candidate

[Artifact11871694729](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/38516837495/artifacts/11871694729) : APK E5 opt-in2 305 017 octets, SHA-256 `d885abfd4f2113bb9137848e106b2d91adfefa25a686c3f94fa8c04edd16ba12`. Empreinte vérifiée sur les bytes. FixtureSHA `c66ded0b6a2ea41ef34320a289f2c1ec37e2a87d34ef580d9ac45d08815682ec`. Candidat non installé, aucune signature Sony qualifiée. Les builds de plateforme sont issus du même HEAD, pas nécessairement de la même signature/ZIP.

## Réserves et prochaine porte

- API 35 E5 : le dernier attempt 2 reste FAIL, bien qu'un push ait PASS. Pas de conclusion artificielle de stabilité ni de cause détaillée. Traitement technique réservé au propriétaire.
- Âges/délais0 ms et preuve foreground : profil conservateur, non qualifié physiquement ; faux refus possibles.
- Certificat/signature/pairing : vérification future avant toute installation ; aucun reset pour forcer une mise à jour.
- Scope documentaire de provenance : décrit ci-dessous, sans correction silencieuse.

## Réserve d'admission documentaire

La publication est limitée aux sept fichiers Markdown de `docs/e5-native-035/`. Le [test d'inventaire fini](https://github.com/pierre22400/scenevibe-tv-companion/blob/102a0193e862abe64e28d94614eb2cd0fb0e2488/tests/test_e5_native_boundary.py) compare tous les fichiers réels de `docs/` aux chemins inscrits dans le registre E5. Au HEAD logiciel, ces sept nouveaux chemins n'y figurent pas. Leur publication laisse donc cette assertion documentaire insatisfaite ; les 125 succès du logiciel 102a ne sont pas attribués automatiquement au nouveau HEAD documentaire.

Le registre `.github/scripts/e5-native-035-provenance.json`, les tests et les workflows ne sont pas modifiés par cette livraison. Une admission documentaire explicite relève du propriétaire qui prend en charge les correctives ; WORK signale cet écart et ne le corrige pas silencieusement. Il ne s'agit pas d'un changement de comportement applicatif. La réserve API 35 reste bloquante indépendamment de cette admission.

## Décision et arrêt

**NOT READY — API35 PLATFORM GATE.** Ne pas recommander READY FOR INDEPENDENT AUDIT tant que le gate applicable et l'admission documentaire restent ouverts. Une revue ciblée pourra ensuite examiner bail, cohérencecontroller/position, EOF/confirmations, callbacks, cleanup et OFF, sans rouvrir E1/E2/Studio.

PR18 : OPEN / DRAFT / UNMERGED. Aucun GO Sony, merge ou production. La publication de ces documents ne ferme pas L0/L1. Le propriétaire prend en charge les suites techniques ; une éventuelle clôture et qualification physique nécessitent leurs preuves et autorisations distinctes.

**STOP après cette livraison. Aucun E4, L2–L8, Cloud, runtime supplémentaire ou chantier TV.**
