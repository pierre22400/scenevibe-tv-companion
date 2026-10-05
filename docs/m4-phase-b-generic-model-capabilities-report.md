# SCENEVIBE OS — M4 PHASE B — Generic Model & Capabilities

Date : 2026-10-04. Repository : `pierre22400/scenevibe-tv-companion`.

## 1. Périmètre et références Git

Phase B uniquement : modèles immuables, capacités locales, contrat de handler et registre statique. Aucun appelant actuel n'est basculé. Production reste **SHADOW**. PR #14 reste **OPEN / DRAFT / non mergée**.

- Branche : `work/scenevibe-os-m4-tv-installation-001`.
- HEAD de départ vérifié sur GitHub : `3aa27f6e316f91cc8a58b47b8084104d28a8ca61`.
- `BASE_TV_M4` / TV main / base PR : `ecdf77bec9f93babf15239a63bf7f702fd7ca293`.
- `BASE_CLOUD_M4` / Cloud main : `5011c91aac61a0cc6dcc74c256a15b7dee03d785`.
- Commit logiciel Phase B : `c899a2e7932077de1cc468fef74b95b5b958a2fe` ; parent exact : HEAD de départ ; tree : `6d9eb6fbd180ae749318a662b898f220e957bd02`.
- HEAD final de clôture : commit qui ajoute ce rapport, sans delta logiciel. Son SHA exact et ses CI sont consignés dans le body de [PR #14](https://github.com/pierre22400/scenevibe-tv-companion/pull/14), après publication. Un SHA ne peut pas être inséré dans le contenu de son propre commit.

Deux commits étroits : implémentation + tests + gates, puis rapport. Publication fast-forward, sans rebase, force-push, modification de main ou du dépôt Cloud.

Les deux documents autoritatifs ont été lus intégralement :

- `docs/scenevibe-os-m4-tv-installation-architecture.md` ;
- `docs/m4-phase-b-generic-model-capabilities-work-order.md`.

Le HEAD de départ descend de la clôture Phase A `a3aaf94bfe0b7bc439460e10f5102fa4339c602c` par un seul commit documentaire : le work order Phase B. La baseline a été réexécutée avant modification : JVM **208 PASS / 0 FAIL / 1 SKIP**, Python **15 PASS / 0 FAIL / 0 SKIP**.

## 2. Classes et responsabilités introduites

Package indépendant : `com.scenevibe.tvcompanionpoc.installation`.

| Classe | Responsabilité Phase B |
| --- | --- |
| `InstallationBounds` | Validations bornées avant copie ; identifiants locaux sûrs ; aucune normalisation de contenu. |
| `InstallationStatus` | Vocabulaire fermé : VALIDATED, PREPARED, ARMED, STALE, UNSUPPORTED_CAPABILITY, INVALID_PACKAGE, CACHE_FAILED, ARM_FAILED. |
| `ExecutionRequirements` | Besoins locaux immuables : contrat de rendu, horloge, pause, nombre borné de valeurs temporelles, besoins d'assets. Aucun framework de négociation. |
| `TvCapabilities` | Singleton local déterministe décrivant les combinaisons réellement exécutables par les chemins de compatibilité actuels. |
| `InstallRequest` | Révision positive, codec borné, artefacts inertes ; copies défensives des tableaux en entrée et sortie. |
| `PreparedInstallation` | Canonical bytes immuables, handler id, valeurs scalaires préparées, requirements acceptées localement. PREPARED ne signifie jamais durable, armé, visible ou ACKable. |
| `InstallationHandler` | Contrat uniquement : validate, prepare, encodeForCache, restoreFromCache, arm. RuntimePorts reste une frontière minimale sans implementation. |
| `InstallationHandlerRegistry` | Métadonnées immuables et lookup déterministe de bindings explicites vers des instances déjà créées. |

Le modèle ne conserve aucun objet mutable de scheduler, fenêtre, service, réseau, credentials ou métier Video. Les artefacts et valeurs préparées peuvent contenir du contenu : ils ne sont jamais journalisés, et aucun `toString` de payload n'est ajouté. Les erreurs utilisent exclusivement des messages fixes.

Les requirements TV sont une représentation Java locale du profil nécessaire à l'installation ; aucune sérialisation ou modification des execution requirements Cloud M2 n'est effectuée.

## 3. Matrice exacte des capacités locales

| Codec / profil | Contrat de rendu | Horloge exécutable | Pause exécutable |
| --- | --- | --- | --- |
| `scenevibe.runtime-track-overlay.v1` | `scenevibe.overlay-manifest.v1` | MEDIA | FREEZE |
| `scenevibe.runtime-track.v1` | `scenevibe.track.v1` | MEDIA | FREEZE ou CONTINUE |
| Inconnu / combinaison croisée | Aucun | Refus | Refus |

Les deux codecs sont des identifiants **internes de formes de package**, sans nouveau contrat distribué. Ils décrivent les chemins actuels ; ils n'annoncent pas qu'un installateur générique est déjà branché.

| Autre capacité / limite | Valeur |
| --- | --- |
| Wall-clock execution | **false** ; même si le parser accepte `clock.mode=wall`. |
| Remote asset acquisition | **false** ; une référence locale n'implique pas un downloader. |
| Shared asset cache M7 | **false**, non annoncé comme disponible. |
| Rendering contracts | Native OverlayManifest et compatibilité legacy track. |
| Codecs / rendering contracts | Sets triés, immuables ; deux éléments chacun. |
| Clock modes | Set immuable contenant uniquement MEDIA. |
| Pause behaviors | Union FREEZE / CONTINUE ; le matcher vérifie les combinaisons par contrat. |
| Artefacts par package | 1 à 2 ; forme legacy = 1, forme manifestée = 2. |
| Package bytes | Au maximum **3 000 000 octets** cumulés. |
| Artifact bytes | Au maximum **2 400 000 octets** par artefact. |
| Limites de chaînes qualifiées | Runtime : **400 000 unités UTF-16** ; manifest : **800 000 unités UTF-16**. |
| Scenes / primitives | 256 scènes ; 256 primitives récursives **par scène** ; plafond total dérivé 65 536. |
| Group nesting / canvas / text | 4 ; 1920 × 1080 ; 2 000 points de code Unicode par primitive texte. |

Justification par le code actuel : `CloudControlClient` borne la réponse HTTP à 3 000 000 octets ; `CloudTrackRepository` borne les chaînes Java en UTF-16 ; `OverlayManifestParser` borne scènes, récursion, canvas et texte. 800 000 unités UTF-16 nécessitent au plus 2 400 000 octets UTF-8 : le nouveau plafond ne réduit donc pas silencieusement la capacité Unicode historique.

Ces limites externes sont nécessaires, pas suffisantes pour valider du JSON ou une bijection Video. `TvCapabilities.validate` vérifie uniquement profil et nombre d'artefacts ; la validation sémantique et la dérivation des requirements appartiennent aux futurs handlers statiques de Phase D. Phase B ne prétend pas vérifier ces sémantiques.

Le scheduler média reste l'autorité du temps pour les scènes natives : une pause bloque la progression de leur fenêtre. Le compteur legacy conserve son comportement `pauseFreezesDisplay=false`, donc CONTINUE est représenté pour ce seul profil. Aucun nouvel exécutant wall-clock n'est créé.

## 4. Bornes, immutabilité et registre

- Révision : `long >= 1`, valeur conservée sans incrément ni persistance.
- Identifiants locaux : ASCII `[A-Za-z0-9._:-]{1,128}`, sans URL, slash ni troncature ; aucun changement des IDs FinalTrack ou graphiques existants.
- Artefacts : non nuls, non vides, au plus deux ; limites par tableau et cumul contrôlées avant copie ; ordre déterministe.
- Prepared values : au plus 256 couples String/String, clé bornée, valeur non nulle de 4 000 unités UTF-16 maximum ; taille UTF-8 cumulée, clés incluses, au plus 3 000 000 octets. Aucun objet arbitraire retenu.
- Execution requirements : enums fermés, contrat borné, timedSceneCount de 1 à 256 ; un besoin WALL ou asset peut être représenté puis refusé.
- Collections retournées immuables ; tableaux toujours détachés. PreparedInstallation conserve uniquement des objets déjà immuables.
- Registry : zéro à deux bindings explicites ; IDs handler et codec uniques ; codecs inconnus refusés à la construction ; lookup null/inconnu = absence. Aucun fallback ou chargement dynamique.
- Le registre par défaut est **vide**. Aucun handler de production n'est créé, découvert ou enregistré. Le lookup n'appelle jamais validate, prepare, encode, restore ou arm. Seuls des doubles de test implémentent le contrat.

## 5. Preuves de frontière et de non-basculement

Quatre contrôles Python nouveaux :

1. Compilation réelle des huit classes contre un classpath/sourcepath vide, avec le JDK uniquement ; aucun Android, JSON, Cloud ou Video disponible.
2. Contrôle des imports et des références qualifiées : uniquement collections JDK et StandardCharsets ; aucun réseau, I/O, réflexion, discovery, thread, renderer, scheduler ou modèle métier.
3. SHA Git de départ des **32 classes Java de production existantes**, manifest Android, configuration Gradle/signature et sources des tests préexistants ; identité byte-exacte exigée.
4. Inventaire exact des classes de production : anciennes classes + huit définitions Phase B ; aucun ancien appelant ne référence ce package ou ces modèles.

Les deux nouveaux tests JVM d'effets composent les valeurs à côté des vrais cores qualifiés, cache manifesté et cache legacy : aucun commit, clear, ACK, chargement scheduler ou changement de génération. Le refus WALL ne retire pas une scène actuellement visible.

Les 30 cas Phase A et les 179 cas JVM antérieurs restent inchangés. Runtime, Cloud v1 envelope, corps ACK, révisions, redelivery, cache, restore, threading des fenêtres, scheduler, identité, pairing et credentials ne sont pas modifiés. Le dépôt Cloud et ses routes/SQL restent intacts.

## 6. Validation exécutée

| Suite | PASS | FAIL | SKIP | Total |
| --- | ---: | ---: | ---: | ---: |
| JVM antérieures | 178 | 0 | 1 | 179 |
| Characterization Phase A | 30 | 0 | 0 | 30 |
| Phase B : InstallationModelTest | 18 | 0 | 0 | 18 |
| Phase B : TvCapabilitiesTest | 10 | 0 | 0 | 10 |
| Phase B : InstallationRegistryTest | 9 | 0 | 0 | 9 |
| Phase B : M4PhaseBSideEffectTest | 2 | 0 | 0 | 2 |
| **JVM complètes** | **247** | **0** | **1** | **248** |
| Python antérieurs | 15 | 0 | 0 | 15 |
| Python Phase B | 4 | 0 | 0 | 4 |
| **Python complètes** | **19** | **0** | **0** | **19** |

Le SKIP unique reste `M1CloudInteropTest.originalColumboProjectionIsInstallable`, opt-in nécessitant `SCENEVIBE_M1_SONY_FIXTURES`. Aucun cas Phase A/B n'est sauté. Les sous-corpus JVM exécutés couvrent les **7 enveloppes M1** et les **94 cas OverlayManifest**.

Local : JDK 17, compilation de toutes les sources contre le jar mockable du SDK Android 35, puis JUnit réel. Pas de Gradle local : les gates Gradle/Android sont exécutées sur GitHub Actions. L'inventaire CI est lu depuis les vrais XML Gradle, avec baseline Phase A gelée et supplément Phase B séparé ; le script conserve le nom historique de l'artefact de résumé.

CI GitHub réellement inspectées sur le commit logiciel exact `c899a2e7932077de1cc468fef74b95b5b958a2fe` :

| Gate | Run / preuve | Résultat |
| --- | --- | --- |
| Android debug APK | [37206126661](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37206126661), job `111447563540` | **SUCCESS** |
| Python / Gradle JVM | Logs du même job : 19 tests Python ; `PASS=247 FAIL=0 SKIP=1 TOTAL=248` ; `Retained baseline: 179; Phase A: 30; Phase B: 39` | **PASS** |
| lintDebug / assembleDebug | Étape Consumer sans Cloud, Gradle réel | **PASS** |
| LAN DEV / Consumer Cloud-origin compile | Deux builds dédiés du même workflow | **PASS** |
| Stable signing / certificate | Étapes exécutées, non skipped ; digest apksigner identique | **PASS** |
| Android 15 / API 35 standard platform smoke | [37206126775](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/37206126775), job `111447563893` | **SUCCESS**, 6 contrôles exécutés PASS / 0 FAIL / 0 SKIP |

Les endpoints des runs confirment `head_sha` exact, trigger `pull_request`, statut completed et conclusion success. Les logs et les étapes sont inspectés, pas seulement le résumé des checks. L'artefact `scenevibe-m4-phase-a-test-summary` contient désormais les compteurs Phase A et Phase B ; artefact de cette exécution `11304439314`.

Les deux mêmes gates seront relues sur le HEAD final contenant uniquement l'ajout de ce rapport avant le verdict utilisateur. Leurs IDs et le SHA exact final sont publiés dans le body de PR #14 : cette provenance ne nécessite pas de créer un nouveau commit documentaire récursif.

## 7. Fixtures gelées

| Fixture | SHA-256 inchangé |
| --- | --- |
| `app/src/test/resources/m1/cloud-envelopes.json` | `19ee6fc3e2eba7009c9d345c33a63ca5617621fac9c8f5618cb24ee2932d6eab` |
| `app/src/test/resources/m1/overlay-corpus.json` | `c78addf877b277e38f66c12274e4c8ed2ce12a503d883e27fd9950215cafbf30` |
| `app/src/test/resources/m4/video-manifested-cache-v1.json` | `76d109c4b69f3e707912449f87e3fa6b9081e4564a12c77c633ae7ed7563beb1` |
| `app/src/test/resources/m4/video-legacy-cache-v1.json` | `1676ed2033ec0e589509da7bdab3294ed8b5949153eeb2cfdb539aa23409cc8c` |

## 8. Auto-audit et corrections localisées

Deux défauts de bord du code nouveau ont été corrigés avant publication :

- `TreeSet.contains(null)` aurait levé une exception dans le matcher de capacités ; garde null et test `unknownNullAndMismatchedProfilesAreRejected`.
- `TreeMap.get(null)` aurait levé une exception dans la lecture d'artefact ; retour absent et test `unknownAndNullArtifactLookupsAreAbsent`.

Toutes les suites ont été réexécutées après ces corrections. Aucun défaut ambigu de l'ancien runtime n'a été arbitrairement résolu par un choix d'architecture future. Aucun changement de comportement qualifié.

## 9. Inventaire du delta Phase B

17 fichiers : huit définitions Java ; quatre classes de tests JVM ; un test boundary Python ; deux scripts/inventaires CI ; un workflow dont seuls les labels de deux étapes sont ajustés ; ce rapport.

```text
app/src/main/java/com/scenevibe/tvcompanionpoc/installation/InstallationBounds.java
app/src/main/java/com/scenevibe/tvcompanionpoc/installation/InstallationStatus.java
app/src/main/java/com/scenevibe/tvcompanionpoc/installation/ExecutionRequirements.java
app/src/main/java/com/scenevibe/tvcompanionpoc/installation/TvCapabilities.java
app/src/main/java/com/scenevibe/tvcompanionpoc/installation/InstallRequest.java
app/src/main/java/com/scenevibe/tvcompanionpoc/installation/PreparedInstallation.java
app/src/main/java/com/scenevibe/tvcompanionpoc/installation/InstallationHandler.java
app/src/main/java/com/scenevibe/tvcompanionpoc/installation/InstallationHandlerRegistry.java
app/src/test/java/com/scenevibe/tvcompanionpoc/installation/InstallationModelTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/installation/TvCapabilitiesTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/installation/InstallationRegistryTest.java
app/src/test/java/com/scenevibe/tvcompanionpoc/M4PhaseBSideEffectTest.java
tests/test_m4_phase_b_boundary.py
.github/scripts/m4-phase-b-baseline.json
.github/scripts/m4-phase-a-test-summary.py
.github/workflows/android-debug.yml
docs/m4-phase-b-generic-model-capabilities-report.md
```

## 10. Continuité et limites de qualification

Application/package : `com.scenevibe.tvcompanionpoc`, versionCode 12, versionName `0.8.2-tv-release-hardening`, targetSdk 35, minSdk 26. Configuration de signature et permissions byte-identiques. Aucun usage de nouvelles APIs de collection Java nécessitant un changement de compatibilité Android dans le code de production.

Certificat stable SHA-256 **observé dans les logs apksigner**, identique à la chaîne SceneVibe qualifiée : `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.

La signature stable est vérifiée par la chaîne de qualification debug existante ; le workflow manuel assembleRelease n'est pas déclenché. L'origine Cloud de qualification et les flags Consumer/LAN sont conservés.

Aucune nouvelle qualification physique Sony n'est revendiquée. Les observations physiques antérieures restent distinctes de ces preuves logicielles ; l'Android 15 est uniquement un smoke de plateforme standard, sans qualification physique Android TV. La gate Sony finale de M4 demeure applicable après les futurs changements de runtime.

Les modèles sont volontairement inertes : pas de validation JSON/bridge derrière un handler, de persistance générique, d'orchestrateur, de branchement transport ou de restauration générique en Phase B. Aucun InstallationStore, PackageInstaller ou handler de compatibilité n'est implémenté. Aucun Phase C/D/E/F/G, M5+, Banner, wall-clock runtime, asset réseau, nouveau permission, suppression legacy ou cutover Production.

## Verdict

**READY FOR M4 PHASE C**.

Aucun blocker Phase B restant. Le non-basculement des appelants, les modèles bornés, l'isolation JDK, les profils exécutables, les tests anciens et nouveaux, les fixtures gelées et les gates Android du commit logiciel sont établis. La publication finale est clôturée uniquement après les mêmes checks verts sur le HEAD documentaire exact. Aucun travail Phase C commencé ; aucun merge.
