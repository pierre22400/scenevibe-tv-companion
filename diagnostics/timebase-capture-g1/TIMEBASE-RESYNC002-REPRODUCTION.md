# TIMEBASE RESYNC 002 — Reproduction G1

1er novembre 2026 — Alex pour Pierre. Projet autonome, privé, sans intégration au Companion. Ce document n'autorise aucune installation physique.

## Sources et outils

Branche `work/scenevibe-timebase-resync-002`, base approuvée `90b55c1c9dcb6894a8285c6a1dbe830a584bba8c`, PR Draft #38 dépendante de #35. Le commit de chaque APK est dans les preuves COMMIT.txt et dans le rapport final. JDK17, Gradle8.9, Android Gradle Plugin8.7.3, compile/target35, min31, build-tools35.0.0. Versions des dépendances de banc : runner1.6.2, ext-junit1.2.1, UIAutomator2.3.0 ; aucune de ces dépendances n'est intégrée au runtime de diagnostic.

## Linux / CI, contrôles locaux sans appareil

```bash
bash diagnostics/timebase-capture-g1/tests/core-checks.sh
python3 -B -m unittest discover -s diagnostics/timebase-capture-g1/tests -v
gradle -p diagnostics/timebase-capture-g1 --no-daemon --max-workers=2 :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Le compilateur portable est lancé par `java com.sun.tools.javac.Main`, permettant l'usage du JDK présent même si le binaire javac n'est pas dans PATH. Le projet ne référence pas le build principal. Les sorties debug sont destinées à l'instrumentation et à la récupération du journal par run-as, jamais à la distribution publique.

## Windows PowerShell, build seul

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

gradle -p .\diagnostics\timebase-capture-g1 --no-daemon --max-workers=2 :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
```

Préinstaller le JDK/Gradle/SDK indiqués. Aucun script d'installation Sony n'est livré ou exécuté. L'APK à remettre est copiée depuis app/build/outputs/apk/debug/app-debug.apk sous le nom `scenevibe-timebase-capture-g1-private-diagnostic.apk`, sans écraser un APK qualifié antérieur.

## Banc émulateur seulement

Le workflow dédié configure Android TV API31 x86/tv_1080p et Android35 standard x86_64/pixel_2. Le second banc n'est pas présenté comme une TV. Le script tests/emulator-checks.sh vérifie ro.kernel.qemu=1 sur emulator-5554 avant installation. La classe d'instrumentation refuse aussi tout matériel autre que ranchu/goldfish.

L'automatisation clique exclusivement notre écran possédé et le vrai dialogue système MediaProjection ; aucun appop ni jeton de projection fabriqué. Les publications synthétiques sont `PUBLIC`, `MIXED`, `SECURE`. Le mode MIXED utilise une SurfaceView setSecure(true), distincte du compteur ordinaire. Le mode SECURE met FLAG_SECURE sur toute la fenêtre. Aucun streaming n'est lancé.

Le banc exporte seulement g1.jsonl, instrumentation.log et les erreurs AndroidRuntime de l'émulateur. Les images sont libérées, les copies ROI écrasées ; aucune capture de pixels n'est remise. Les champs elapsed, uptime et muraux restent distincts. Le timestamp Image n'est pas utilisé comme elapsedRealtime.

## Vérification APK

```powershell
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass

Get-FileHash .\scenevibe-timebase-capture-g1-private-diagnostic.apk -Algorithm SHA256
apksigner verify --verbose --print-certs .\scenevibe-timebase-capture-g1-private-diagnostic.apk
aapt dump badging .\scenevibe-timebase-capture-g1-private-diagnostic.apk
aapt dump permissions .\scenevibe-timebase-capture-g1-private-diagnostic.apk
```

Le certificat de diagnostic doit être distinct de M5 (`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`). Ne pas importer de clé de production pour le signer. La clé privée éphémère de banc n'est jamais exportée ; le certificat public/son empreinte ne vaut pas une clé privée.

## Évaluation et limites

Les invariants portables ne qualifient pas la capture. Les doubles de buffer ne représentent pas une Image Android réelle. Chaque PASS d'acquisition exige ImageReader, motif effectivement observé et bilan de fermeture ; toute absence de banc demeure NOT EXECUTED/BLOCKED. Le rapport final conserve les échecs antérieurs et les corrections. Ne pas convertir une absence de motif en preuve de protection Netflix.

La preuve d'isolation compare les 537 fichiers suivis de la base choisie, octet pour octet, et la différence distante. Le nombre608 de TIMEBASE001 appartient à un autre état ; il n'est pas réutilisé comme total courant. Aucun test historique n'est recopié comme nouveau résultat.
