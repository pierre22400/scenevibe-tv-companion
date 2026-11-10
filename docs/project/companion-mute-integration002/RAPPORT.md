# COMPANION MUTE INTEGRATION 002 — livraison v32 M5

Décision propriétaire et amendement 001 du 10 novembre 2026.

**Verdict : READY FOR SONY — qualification logicielle terminée.**

**SONY MUTE : NOT EXECUTED — qualification physique requise.**

## Livraison

- [APK principal v32 signé M5](https://chatgpt.com/library?file_id=libfile_f99201b69b6881918b7b4d5412dd085e&account_id=personal).
- [PR Draft #49](https://github.com/pierre22400/scenevibe-tv-companion/pull/49), directement basée sur #47, commit de base `3a24849603f0e88f60946b870cb7b30b327da26c`.
- Source exacte de l’APK : `01da23263c0d042192d536c2ed497b6b34daecc1`. Les commits documentaires ultérieurs ne changent pas cet APK.
- Package : `com.scenevibe.tvcompanionpoc` ; versionCode **32** ; versionName `0.8.2-tv-release-hardening-companion-mute-integration002-v32` ; APK non debuggable.
- Signature RSA 4096 M5 historique, schémas v2 et v3 vérifiés. Certificat SHA-256 : `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.

SHA-256 de `scenevibe-companion-mute-integration002-v32-signed.apk` :

```text
d4e45b19dd6fd99e6ab6d7cdfa4dfd31e04bac8a50d43e366ba284ff3c5e612b
```

Ce hash est différent des deux anciens candidats v32 refusés. #48 reste Draft, non fusionnée et ne constitue pas la base de cette branche.

## Déclenchement intégré

Le raccourci Android affecté par le propriétaire appelle le véritable `AccessibilityButtonController.AccessibilityButtonCallback` de `CompanionShortcutService`, dans le package principal. Ce service transmet à l’instance courante de `TvFirst009TextServiceOwner`. Le propriétaire vérifie service de restitution, admission/cache courants, permissions existantes, identité MEDIA compatible et capture fraîche avant d’ouvrir le panneau existant avec son `WindowManager`.

La connexion seule ne démarre aucune Track. Un callback sans Track est refusé ; une seconde ouverture conserve la même fenêtre. Le journal distingue callback reçu et fenêtre demandée ; les essais vérifient séparément l’attachement, le focus réellement obtenu, le D-pad reçu et le retour du focus.

Aucun `panelIntent(...).send()`, appel direct à `onClicked`, recherche de notification ou retour dans l’Activity n’est utilisé pour déclencher le scénario natif MUTE de cette qualification. Les tests unitaires de callbacks synthétiques sont explicitement identifiés comme tels.

Le banc configure le vrai service Android TV 31 et injecte la combinaison système standard **BACK + BAS**, reconnue par la politique d’entrée Android. Android délivre ensuite lui-même le callback. Cela qualifie le raccordement système jusqu’au panneau ; cela ne prouve pas que le firmware Sony transmet MUTE. L’action globale 13, indisponible sur cette image TV, n’est pas comptée comme un succès.

## Recalage et non-régression

Les widgets CURRENT/NEW et les pas 60/10/1 seconde réutilisent les composants PRODUCT UX 002. Le candidat demeure détaché de l’horloge jusqu’à OK. BACK, fermeture et perte de focus annulent sans application partielle. La validation recontrôle la session et modifie uniquement la projection MEDIA existante de la Track ; aucun second moteur, cache ou lecteur n’est créé.

Les essais privés et publics appliquent réellement 30 s → 20 s sans reprise, annulent une modification, puis valident 80 s avec reprise. Ils vérifient ensuite un MP3 natif après recalage à 31 s et une fenêtre TEXT après recalage à 21 s. Le lecteur de fixture conserve son état PAUSED pendant la validation ; seule la fixture reprend elle-même sa MEDIA. Aucun transport du lecteur tiers n’est envoyé. Le calendrier, les protections de relecture et le lecteur MP3 du Companion restent communs.

Les sources des clients Cloud, caches, reçus, association autonome, `MainActivity` et contrôles privés/publics de #47 ne sont pas réécrites. Les tests historiques conservent leurs assertions. Le retour à la navigation ordinaire après fermeture d’une route MUTE est vérifié.

## Résultats sur la source livrée

[Chaîne complète](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42803530873) : **SUCCESS**, cinq jobs requis réussis. [P0 natif final](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/42803531229) : **SUCCESS**.

| Contrôle | Résultat |
| --- | --- |
| JVM principal | 1 430 PASS ; 1 SKIPPED ; aucun échec |
| UI Robolectric | 46/46 PASS : panneau historique 25, recalage 14, service/rappels synthétiques 7 |
| P0 natif | 1 connexion/callback réel sans Track + 3 cas privés PASS, avec ouverture et recalage réels |
| Parcours public/privé MUTE | 11 publics + 3 privés + 3 phases de redémarrage PASS |
| Parcours privé v31 conservé | 52 cas historiques + 1 smoke + 2 phases de redémarrage + 3 cas privés PASS |
| Mise à jour signée réelle | 3/3 PASS : seed v31 M5, lecture v32 M5, callback du service signé après configuration initiale |
| Permissions compilées | Identiques à v31 ; nouveau service approuvé et protégé par BIND_ACCESSIBILITY_SERVICE |
| Assemblage/lint/signature | PASS ; lint 0 erreur, 84 avertissements ; autres variantes conservées compilées |
| Python et outils Windows | 15 contrats POC, 11 outils Linux, 3 gardes G11 ; 11 cas sous PowerShell 5 et 11 sous PowerShell 7 PASS |
| Page locale dans Chromium | PASS sur le véritable listener HTTP de fixture |
| HTTPS externe / fournisseurs | NOT EXECUTED dans ce mandat, sans écriture Cloud/Neon/R2 |
| Sony MUTE / Prime physiques | NOT EXECUTED |

Le test historique `M1CloudInteropTest.originalColumboProjectionIsInstallable` reste ignoré car `SCENEVIBE_M1_SONY_FIXTURES` n’est pas fourni. Il n’est pas compté PASS. Les états TEXT/AUDIO natifs portent sur nos MediaSession et packages de fixture ; ils ne constituent pas une observation de Prime ou des haut-parleurs Sony.

La mise à jour utilise l’APK v31 physique de référence (SHA-256 `98ac6b74f71f02cbb52bd09998565012c864ec61aeb0a094d610de614283710f`) puis `install -r` du nouveau v32. Cache privé 3 TEXT + 3 MP3, credential Android Keystore, préférences et accès superposition/MEDIA survivent sans réassociation ni nouvelle attribution de ces accès. Le test d’accessibilité est une phase initiale séparée, explicitement autorisée.

Le type `specialUse` du service public était déjà présent dans le manifeste principal et l’APK compilé. Il est répété à l’identique dans les fragments de variante pour le modèle lint ; le contrôle de l’APK exige la conservation des types des services de premier plan existants. Aucune nouvelle permission demandée n’a été ajoutée. Le manifeste de l’APK signé est identique à celui audité avant signature ; l’Activity de fixture est absente du manifeste et du DEX livrés.

## Configuration Android et réception Sony

Le composant expérimental `com.scenevibe.tvcompanionpoc.netflixoverlay001/com.scenevibe.tvcompanionpoc.NetflixShortcutService` et le nouveau composant `com.scenevibe.tvcompanionpoc/com.scenevibe.tvcompanionpoc.CompanionShortcutService` sont distincts. Une affectation visant l’ancien composant n’est pas transférée automatiquement au nouveau.

La configuration actuellement enregistrée sur la Sony n’a pas été lue. Si elle vise le service expérimental, sélectionner **SceneVibe — raccourci du Companion**, l’activer si Android le demande et lui affecter le raccourci MUTE dans le parcours de réglages existant. Cette configuration initiale est **autorisée et non bloquante**. Aucun assistant de configuration supplémentaire n’est ajouté. La persistance future n’est pas présumée.

Le [protocole Sony complet](../../companion-mute-integration002.md) décrit : v31 → mise à jour v32 sans désinstallation → éventuelle affectation initiale → Track active → Prime → MUTE → panneau → recalage/annulation → fermeture → retour du focus et des commandes à Prime. Aucun passage par une notification ou l’ouverture du Companion n’est requis pour appeler le panneau.

La transmission réelle de MUTE par Sony, la réaction de Prime au focus et la restitution physique de la télécommande doivent être constatées pendant cet essai. **Aucune installation Sony, modification de réglages physiques, fusion, publication Play ou modification de fournisseur n’a été réalisée.** Ces actions restent hors du GO actuel.

## Preuves conservées

- [Provenance CI et artefacts](proofs/ci.json), [provenance APK](proofs/delivery.json), [signature](proofs/apk/apksigner-verify.txt), [permissions](proofs/apk/permission-comparison.json).
- [Inventaire JVM/UI](proofs/unit/results.json), [lint](proofs/lint-results-tvux002.xml).
- [Ouverture native](proofs/native/public001b-native/mute-p0-open.json), [recalage privé](proofs/native/public001b-native/mute-private-proof.json), [recalage public](proofs/native/public001b-native/mute-public-proof.json).
- [Mise à jour signée](proofs/upgrade/v32-retained.log), [callback du service signé](proofs/upgrade/signed-shortcut.log).
- Les preuves P0 initiales à la racine de `proofs/p0` concernent `4ef8ba7…` et précèdent l’intégration sélective du recalage. Le sous-dossier `proofs/p0/mute002-p0` concerne la source finale.

