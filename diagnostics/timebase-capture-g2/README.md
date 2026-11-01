# TIMEBASE capture G2 — PRIVATE DIAGNOSTIC — NOT FOR DISTRIBUTION

Projet Android autonome issu d'une copie limitée des composants G1 : aucune dépendance ni modification de G1/Companion. Base : `fe3a797197ed4bc36459f1cb4e7fc8627bf88dc4`, PR #38. Nouvelle PR Draft : https://github.com/pierre22400/scenevibe-tv-companion/pull/39 . Package distinct `com.scenevibe.timebasecaptureg2`.

Correctif **003-B / versionCode 3** : conservation volatile de la ROI pendant 5 minutes ; seul le délai d'inspection change. Le correctif **003-A / versionCode 2** et les rapports 003 précédents restent des références historiques.

## Cycle de vie

Préparation volontaire → réservation volatile avant dialogue (120 s maximum pour consommer le résultat) → consentement Android neuf → projection visible sans VirtualDisplay pendant un délai configurable 500–60 000 ms → départ volontaire de notre Activity → un seul VirtualDisplay/ImageReader → fenêtre ≤10 s, création de la file après une barrière de 250 ms et ≤240 buffers → une ROI → fermeture native → inspection volontaire secure → effacement ≤5 min après acquisition. Aucun lancement d'Activity par le service, aucune découverte de package/fenêtre tiers, aucun OCR.

La projection peut avoir accès au display entier en RAM. Seule la ROI normalisée est copiée (RGBA8888, pixelStride 4, rowStride validé, dimensions 1–4096). Les timestamps d'image servent uniquement à refuser doublons/non-croissance entre images : aucune soustraction avec elapsedRealtime. L'âge de réception et le délai utilisent elapsedRealtime ; uptime et heure murale sont journalisés séparément. La barrière de 250 ms est conservée avant création de l'ImageReader/VirtualDisplay, sans allonger la fenêtre de 10 s. La première image de ce pipeline neuf peut être acceptée : aucun second buffer n'est nécessaire. Un premier timestamp zéro n'est pas un doublon ; un timestamp non observé est journalisé null. Génération, identité du lecteur et display actif sont vérifiés avant lecture. Une image invalide clôt la tentative ; aucun retry de consentement. Un timeout ne prouve pas une protection.

`CapturePolicy` borne génération/consentement/délais ; `CaptureService` possède et ferme les ressources ; `RoiLease` possède le tableau de pixels ; `ResultActivity` efface aussi sa copie Bitmap dès la perte de premier plan et lors de toute sortie. Aucun pixel n'est sauvegardé dans l'état Android. Le zéro explicite et l'abandon des références réduisent la rétention applicative ; ils ne garantissent pas un effacement forensique des mémoires Android/GPU.

Les labels sont exclusivement humains. Le contexte par défaut est SYNTHETIC et ses labels portent `SIMULATED_`. Une réception de buffer ne produit jamais A/B automatiquement. Le futur contexte externe exige une déclaration humaine après GO/admissibilité distincts. Cette déclaration n'est pas une preuve juridique.

## Reproduction sur émulateur uniquement

JDK 17, Gradle 8.9, AGP 8.7.3, compileSdk/targetSdk 35, minSdk 31 ; bibliothèques AndroidX limitées à l'instrumentation. Aucune bibliothèque runtime. Installer les SDK requis avec les outils Android standards. Définir `GITHUB_SHA` au commit source pour le champ de traçabilité, puis :

```bash
bash diagnostics/timebase-capture-g2/tests/core-checks.sh
python3 -B -m unittest discover -s diagnostics/timebase-capture-g2/tests -v
gradle -p diagnostics/timebase-capture-g2 --no-daemon --max-workers=2 :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug
bash diagnostics/timebase-capture-g2/tests/emulator-checks.sh
```

Le dernier script refuse tout serial autre que l'émulateur fixe et vérifie `ro.kernel.qemu=1` avant installation. L'instrumentation refuse tout matériel autre que ranchu/goldfish. Consentement système réel, aucune capture ADB. Le workflow dédié réexécute aussi les 47 invariants et les sept contrôles Android G1 sans modifier G1. L'ancien test d'isolation G1 porte sur un périmètre de diff antérieur ; l'isolation du nouveau travail est vérifiée depuis fe3a797.

Signature de diagnostic Android debug, **distincte de M5**, APK debuggable privée de laboratoire. Aucun certificat/secret productif. Vérification : `apksigner verify --verbose --print-certs APK`, `aapt dump badging APK`, `aapt dump permissions APK`. Aucun déploiement ou publication. Les APK de laboratoire peuvent utiliser des certificats éphémères différents entre compilations : `install -r` n'est possible que si le certificat est identique. Sauvegarder le journal G2 avant une éventuelle désinstallation de la version précédente. Aucun changement physique n'est réalisé par la qualification logicielle ; le Companion reste inchangé.

Journal technique UTF-8 sans BOM dans le stockage privé `files/g2.jsonl`, plafond environ 1 Mio avec rotation du journal technique ancien. Suppression volontaire depuis la préparation après arrêt. Aucun fichier image. Les seules sorties de tests sont des journaux techniques de contrôles possédés.

Sony / Netflix : **NOT EXECUTED**. Voir le protocole et le dossier d'admissibilité ; **LEGAL REVIEW REQUIRED**, aucun essai physique avant nouveau GO.
