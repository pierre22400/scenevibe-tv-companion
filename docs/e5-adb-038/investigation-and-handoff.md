# E5-038 — déclenchement ADB protégé

Date : 14 octobre 2026. Base immuable : `47f675dbd05218fb1caca2b090fd81c822e513e0`.
Branche isolée : `work/scenevibe-e5-adb-trigger-038`. Aucune fusion ni intervention Sony.

## Investigation

Le bouton `DiagnosticsActivity.requestE5Local` confirme la nature VOD puis envoie
`ACTION_E5_LOCAL` au service non exporté `OverlayService`. Ce service appelle
`requestE5Local`, configure les ports déjà existants et appelle
`SceneRuntimeController.requestLocal`. `NativeTransportOwner` conserve la sélection
Prime, les preuves foreground/session/identité/position, les capacités PAUSE/PLAY,
les confirmations et les règles de récupération. Le bouton ne réalise aucun
transport lui-même. Le propriétaire constate que lancer MainActivity fait sortir
Prime de la lecture ; remettre la tâche en arrière-plan ensuite ne restaure pas cet
état. Ce constat bloque le déclenchement, sans démontrer un défaut du renderer.

Les actions de service E5 existent mais `OverlayService` est non exporté. Aucune
entrée externe adaptée n'existe au HEAD vérifié. BootReceiver n'est pas une entrée
E5 et ne doit pas être détourné. Les rapports 035 restent des preuves historiques,
complétées par les commentaires ultérieurs de PR #18 ; ils ne sont pas réécrits.

## Choix Android et surface exposée

Un BroadcastReceiver existe uniquement dans le source set debug de qualification
explicitement opt-in. Aucun filtre d'intent : composant explicite obligatoire.
`android.permission.DUMP` protège sa réception côté système. Cette permission
plateforme signature/privileged/development est détenue par le shell ADB Android
12 et n'est pas demandée par Companion. Une application ordinaire ne peut pas
s'auto-accorder cette permission ; un acteur déjà privilégié ou autorisé par le
propriétaire via ADB appartient au périmètre de confiance. Ce n'est pas une
permission exclusivement réservée au shell. L'identité Binder dans `onReceive`
n'identifie pas l'émetteur original et n'est donc pas utilisée comme authentification.

L'action est fixe, la cible est fixe, le seul extra est le Boolean
`e5VodConfirmed=true`. URI, type, sélecteur, ClipData, catégories, extras inconnus
ou attestation absente/fausse sont refusés. Aucun chemin/fichier/commande arbitraire,
paramètre réseau ou ordre natif n'est accepté. Aucune Activity n'est lancée.

Diagnostics et le receiver emploient `E5LocalRequest.confirmedVod` : une seule
factory du même intent explicite vers OverlayService. Aucun nouveau moteur,
controller, decoder, renderer, calendrier, stockage ou chemin PAUSE/PLAY.
Le receiver vérifie le flag et l'autorisation overlay avant de demander le service.
Usage Access et notification listener restent requis par le chemin existant ;
leur absence ne déclenche aucune concession ou octroi automatique.

Android 12 autorise certains démarrages FGS depuis l'arrière-plan, notamment sous
SYSTEM_ALERT_WINDOW. Android peut néanmoins refuser le démarrage ; l'exception
est capturée comme `REFUSED_ANDROID_OR_PAYLOAD`, sans retry ni Activity de secours.
Android 15 impose des conditions supplémentaires ; le profil natif reste strictement
Sony/API31. Rien n'élargit cette cible. La commande reçoit `QUEUED_GUARDS_PENDING`
pour la réception et l'envoi au service, jamais une promesse de pause ou de succès.
Le diagnostic E5 est l'autorité pour les gardes et l'état final.

Sources :
- https://developer.android.com/develop/background-work/services/fgs/restrictions-bg-start
- https://developer.android.com/reference/android/Manifest.permission#DUMP
- https://android.googlesource.com/platform/frameworks/base/+/refs/tags/android-12.0.0_r1/packages/Shell/AndroidManifest.xml

## Sécurité et non-régression

La fixture, le renderer L0/L1, OverlayService, NativeTransportOwner,
NativeQualificationPolicy, SceneRuntimeController, les ports Android, les règles
pause/reprise, le manifeste main, les sources Cloud/LAN et toutes les autres sources
main restent byte-exact au HEAD E5, hormis la substitution de factory dans Diagnostics.
La nouvelle variante est versionCode 15 ; OFF/release gardent leur version historique
12 et aucun receiver de qualification. Aucun pairing/cache/configuration durable
n'est modifié par la correction. Le registre de provenance conserve toutes les
assertions anciennes et ajoute seulement des chemins et SHA précis et des inverses
littéraux exacts ; aucun wildcard ni skip ajouté.

La base E5 est dérivée du socle M5 ; elle ne contient pas le renderer Banner M6,
qui vit sur `work/scenevibe-os-m6-banner-wall-architecture-001`. E5-038 ne peut donc
pas revendiquer une qualification de Banner M6 dans cette APK. Aucune importation
M6 n'est autorisée ni effectuée. La branche M6 reste intacte. Une exigence de
coexistence effective E5/M6 dans un même binaire nécessite un mandat distinct ;
cette limitation est conservée explicitement dans le verdict de livraison.

## Couverture et exécution

- Python : inventaire fini, voie commune Diagnostics/ADB, protection et fichiers
  exclus byte-exact. Première exécution complète : 128 tests PASS.
- JVM héritée : nominal PLAYING, PAUSED, sélection absente/ambiguë, capacités
  manquantes, autre application, double demande, flag OFF, changement token/média,
  intervention manuelle, perte session, cleanup et aucune reprise dangereuse.
  Les classes `NativeTransportOwnerTest`, `SceneRuntimeLocalInterludeTest` et
  `E5LocalPlanAndForegroundTest` restent inchangées (64 cas).
- Android : `E5AdbTriggerInstrumentation` teste 15 assertions d'enveloppe/protection
  et capture le même intent qu'envoie Diagnostics. Aucun transport réel.
- Android : `E5AdbSenderInstrumentation` s'exécute sous l'UID distinct du package de
  test, sans DUMP ; le système doit empêcher la livraison du même broadcast valide.
- Shell API31 : commande ADB réelle, témoin foreground Settings inchangé,
  refus normal de cible non Sony, commande malformée, endpoint absent APK OFF.
- Workflow isolé : OFF/ON, lint, tests, plateforme31 puis seulement signature avec
  la clé stable existante. Aucune génération/extraction de clé, aucun build Cloud,
  déploiement ou publication Play. Résultats finaux à compléter après exécution.

## APK de référence

La référence exacte `SceneVibe_E5_47f675db_stable.apk` a été récupérée et vérifiée :
applicationId `com.scenevibe.tvcompanionpoc`, versionCode 14,
versionName `0.8.2-tv-release-hardening-e5-native035`.
Certificat SHA-256 :
`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.
La pièce nommée `SceneVibe_Sony_installee.apk` est distincte : versionCode 13,
versionName `0.8.3-m6-phase-e`, même certificat. Elle n'est pas confondue avec E5.

La candidate n'est installée sur aucun appareil physique. La signature/version
rendent une future mise à jour en place admissible, mais la conservation physique
des données et des autorisations reste à constater par le propriétaire. Aucune
commande de désinstallation, clear data ou reset pairing n'est prévue.

## Statut provisoire

BLOCKED — preuves finales Android/APK en cours ; pas de READY anticipé.
Même si le déclenchement passe, la non-régression Banner M6 ne peut pas être
revendiquée pour un binaire issu de cette base E5 dépourvue de M6.
SONY NOT EXECUTED. Aucune fusion, installation, production, Neon, Vercel ou Cloud.
