# SOURCE FREEZE 019 — sources du v19 qualifié

Restauration historique uniquement pour [l'issue #42](https://github.com/pierre22400/scenevibe-tv-companion/issues/42), sur une branche dédiée issue de `work/scenevibe-real-track-tv-001` à `7d984261048156dd91a79e148fd2f419d8f5cafb`. Aucun développement fonctionnel supplémentaire. Le PASS physique propriétaire est documenté dans la [PR #41](https://github.com/pierre22400/scenevibe-tv-companion/pull/41).

## Provenance exacte

Archive UX 001-B jointe : SHA-256 `93924b4b757848bae8a3931ed8c66ba31288a30977c01946b429fe5f6ae1d671`.
`DIFF-FINAL.patch` et `PATCH/UX-001B-INTEGRE.patch` : SHA-256 `9bbdc54fd71df4e62c89a68256feb5b97c5b061bf8dbcc4ba2b8e821a04a668d`.

Reconstruction selon `RECONSTRUCTION-V18.md` : patches 003-A, 003-B, 003-C, 003-D, 003-E, 003-K-A, 003-K-B, MULTIPLATFORM MANUAL SYNC 001, puis DIFF-FINAL. Chaque étape passe `git apply --check`. Les trois fichiers BASELINE du lot UX 001 sont identiques octet pour octet au v18 intermédiaire. Le test notification est repris sans modification du lot 003-G, conformément à REPRODUCTION-UX-001B.md.

Archives d'entrée contrôlées (SHA-256) :

| Lot | Empreinte |
| --- | --- |
| 003-E, incluant 003-A/B/C/D | `01eec137efbf6104ba26f6657fb63fd99898cedd1374019bd8b21b677b442b72` |
| 003-K-A | `a5c17c7c30966d20db9f1fafa8336ef224d9c2ff4698a6f8757f99cfe7b5f4a4` |
| 003-K-B | `5f80da88bf82835fe0e0db064824b01928c9365d8940956dd36d4f61f59fd77c` |
| MULTIPLATFORM MANUAL SYNC 001 | `e9ce06778a46745c262f449ac7d26e8469acd11cdd3dbf083103336e68d2d020` |
| UX 001 SOURCE (BASELINE) | `2551825c198253a2f37753edf05d371fb7040c1337aff54fd7e202b74083e34e` |
| 003-G (test notification) | `fc2f9406feda2f81e4c6a20bb8e5bdde9c6b0ea461b1d05996ec93dcd5999c17` |

## Vérification source ↔ APK

APK signé qualifié : `bbb290750796ae3e860f8419af548836cdf9115487d15e8c0ce0a9889e9c8a6f` ; certificat public : `e49c665500106afc7fc3638c71a30f5e9352dd1f05bbba871fbaf6f2570df02a`. Signature v2/v3 vérifiée, un signataire. Package `com.scenevibe.tvcompanionpoc.netflixoverlay001`, versionCode 19.

Recompilation `:app:assembleNetflixmanual003b` réussie. APK non signé obtenu : `2ede1e1c8c791b38d9eb5341fa9affc78fb6145106f2d3acce0f34510b0fb974`. Comparaison des données décompressées des 95 entrées ZIP : **94 identiques**, incluant `classes.dex`, manifeste Android compilé, `resources.arsc`, XML, toutes les ressources et tous les assets/fixtures. Seul `META-INF/version-control-info.textproto` diffère : l'original indique `NO_SUPPORTED_VCS_FOUND`, la reconstruction identifie Git et le commit de départ. La signature et l'encapsulation ZIP ne sont pas une identité binaire revendiquée. **Correspondance du code compilé et des ressources : PASS.** Aucun APK ni secret de signature n'est committé.

## Dépendances et tests

JDK 17 (rejeu : Temurin 17.0.21), Gradle 8.9, AGP 8.7.3, SDK Android 35 r02, Build Tools 34.0.0, FFmpeg. Gradle ZIP : `d725d707bfabd4dfdc958c624003b3c80accc03f7037b5122c4b1d0ef15cecab`. SDK/build-tools : SHA-1 `0bb560a90a7a2cbd0dd8348224d518b638fe7949` / `d6d58e0c6925a9e4d9a541e84cd1f405c2f9d2a9`, identiques aux entrées historiques.

Preuve historique réutilisée : **192 PASS** (170 JVM relus dans les XML, zéro erreur/échec, et 22 Python). Rejeu SOURCE FREEZE : 15 contrats Python PASS ; 31/31 tests JVM ciblés PASS (NetflixManualPanelTest 24, NetflixManualLaunchActivityTest 5, NetflixNotificationAccess003GTest 2).

```sh
gradle --no-daemon --max-workers=2 :app:assembleNetflixmanual003b
gradle --no-daemon --max-workers=2 :app:testNetflixmanual003bUnitTest \
  --tests '*NetflixManualPanelTest' \
  --tests '*NetflixManualLaunchActivityTest' \
  --tests '*NetflixNotificationAccess003GTest'
python3 -m unittest discover -s tests -p test_poc_contract.py
```

Le banc historique utilise `forkEvery=0` via un script d'initialisation externe pour ces trois classes ; cela n'altère pas les sources applicatives. Prévoir le téléchargement du runtime Robolectric et l'accès Maven/Google depuis le banc. Les autres suites historiques conservent leurs configurations séparées décrites dans l'archive UX 001-B.

STOP avant fusion, installation Sony ou déploiement. Aucune branche historique ni le Companion principal v28 installé n'ont été modifiés.
