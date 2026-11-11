# COMPANION MUTE INTEGRATION 002 — livraison v32 M5

Décision propriétaire et amendement 001 du 10 novembre 2026.

**Verdict actualisé au 11 novembre 2026 : PASS PHYSIQUE SONY — MUTE 002 sur Prime Video, parcours privé.**

**La qualification physique est rapportée par le propriétaire.** L'APK v32 est gelé ; le parcours public, Netflix et la publication Play ne font pas partie de ce PASS.

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
| Sony MUTE / Prime physiques | **PASS TERRAIN (11/11/2026)** — déclaration propriétaire, parcours privé Columbo ; détails ci-dessous |

Le test historique `M1CloudInteropTest.originalColumboProjectionIsInstallable` reste ignoré car `SCENEVIBE_M1_SONY_FIXTURES` n’est pas fourni. Il n’est pas compté PASS. Les états TEXT/AUDIO natifs portent sur nos MediaSession et packages de fixture ; ils ne constituent pas une observation de Prime ou des haut-parleurs Sony.

La mise à jour utilise l’APK v31 physique de référence (SHA-256 `98ac6b74f71f02cbb52bd09998565012c864ec61aeb0a094d610de614283710f`) puis `install -r` du nouveau v32. Cache privé 3 TEXT + 3 MP3, credential Android Keystore, préférences et accès superposition/MEDIA survivent sans réassociation ni nouvelle attribution de ces accès. Le test d’accessibilité est une phase initiale séparée, explicitement autorisée.

Le type `specialUse` du service public était déjà présent dans le manifeste principal et l’APK compilé. Il est répété à l’identique dans les fragments de variante pour le modèle lint ; le contrôle de l’APK exige la conservation des types des services de premier plan existants. Aucune nouvelle permission demandée n’a été ajoutée. Le manifeste de l’APK signé est identique à celui audité avant signature ; l’Activity de fixture est absente du manifeste et du DEX livrés.

## Configuration Android et réception Sony

Le composant expérimental `com.scenevibe.tvcompanionpoc.netflixoverlay001/com.scenevibe.tvcompanionpoc.NetflixShortcutService` et le nouveau composant `com.scenevibe.tvcompanionpoc/com.scenevibe.tvcompanionpoc.CompanionShortcutService` sont distincts. Une affectation visant l’ancien composant n’est pas transférée automatiquement au nouveau.

Lors de la livraison logicielle initiale, la configuration Sony n’avait pas encore été lue. Depuis, le propriétaire a configuré et vérifié le service principal sur son Sony BRAVIA, puis constaté le déclenchement physique MUTE. Si elle vise le service expérimental, sélectionner **SceneVibe — raccourci du Companion**, l’activer si Android le demande et lui affecter le raccourci MUTE dans le parcours de réglages existant. Cette configuration initiale est **autorisée et non bloquante**. Aucun assistant de configuration supplémentaire n’est ajouté. La persistance future n’est pas présumée.

Le [protocole Sony complet](../../companion-mute-integration002.md) décrit : v31 → mise à jour v32 sans désinstallation → éventuelle affectation initiale → Track active → Prime → MUTE → panneau → recalage/annulation → fermeture → retour du focus et des commandes à Prime. Aucun passage par une notification ou l’ouverture du Companion n’est requis pour appeler le panneau.

Ces contrôles ont ensuite été exécutés physiquement par le propriétaire le 11 novembre 2026 et sont consignés dans la réception ci-dessous. Le mandat initial de livraison logicielle n'avait réalisé aucune intervention Sony. **Aucune fusion, publication Play ni modification de fournisseur n'est autorisée par cette réception.**

## Réception physique Sony — 11 novembre 2026 (décision propriétaire)

**Verdict : PASS TERRAIN sur le parcours privé COMPANION MUTE INTEGRATION 002.** Source Android **immuable** : `01da23263c0d042192d536c2ed497b6b34daecc1` ; APK signé M5, versionCode **32**, SHA-256 `d4e45b19dd6fd99e6ab6d7cdfa4dfd31e04bac8a50d43e366ba284ff3c5e612b`. Sony BRAVIA VH1 sous Android TV 12 ; application principale `com.scenevibe.tvcompanionpoc`. Les observations ci-dessous ont été faites par le propriétaire et communiquées dans la conversation de qualification, **sans capture logcat complète ni artefact vidéo ajouté à GitHub**. Elles ne sont pas des résultats automatiques CI.

La v32 a été installée par mise à jour `adb install -r`, sans désinstallation ni effacement de données. Le service d'accessibilité principal a été affecté au raccourci MUTE avec le paramétrage Android TV initial explicitement autorisé. Le callback physique `SHORTCUT_CALLBACK_RECEIVED` a été constaté avant la réactivation de la Track (alors `SHORTCUT_NOT_READY`, refus attendu hors session admise).

Le parcours privé historique a été récupéré avec l'appui de [COMPANION CLOUD RECOVERY 001 — PR Draft Cloud #62](https://github.com/pierre22400/interface-scenevibe/pull/62), qui a rétabli `/tv` sur l'origine intégrée à l'APK. La Sony a conservé son identifiant d'appareil ; la nouvelle génération a exigé une vérification du cache multimédia et un téléchargement/réception compatibles. La Track privée « Columbo — Eaux troubles » a été retrouvée et utilisée dans Prime Video.

| Contrôle terrain déclaré par le propriétaire | Verdict |
| --- | --- |
| Association historique de la Sony, catalogue et Track privée | **PASS** |
| TEXT visibles et AUDIO audibles pendant Prime Video après activation locale | **PASS** |
| Raccourci physique MUTE → panneau « Recalage SceneVibe » sans quitter Prime | **PASS** |
| Fenêtre superposée à Prime ; navigation télécommande et retour des commandes après fermeture | **PASS** |
| Recalage effectif du temps SceneVibe, sans SEEK commandé au lecteur tiers | **PASS pour le recalage** ; absence de commande lecteur conforme au code, non instrumentée physiquement |
| Modification candidate puis BACK : annulation sans décalage de +60 s appliqué | **PASS** |
| Trois ouvertures/fermetures répétées : pas de fenêtre fantôme ni de perte de télécommande | **PASS** |
| Recaler et reprendre ; TEXT/AUDIO suivant le nouvel horaire | **PASS** |
| Recaler sans reprise ; commentaires suspendus puis reprise volontaire | **PASS** |
| Redémarrage complet Sony : association, Track et raccourci MUTE fonctionnels après réactivation explicite TEXT/AUDIO | **PASS** |

**Observations non bloquantes conservées pour suivi ultérieur :**

- Un premier essai après récupération du compte n'a pas montré les commentaires immédiatement ; un retour dans SceneVibe a semblé débloquer la restitution. **Non reproduit ensuite, y compris après hard reboot** ; cause non démontrée, pas de correction conjecturale.
- Après redémarrage, le cache et l'association restent disponibles, mais la restitution TEXT/AUDIO exige une **nouvelle activation locale explicite**. Conforme à la politique de consentement actuelle ; amélioration ergonomique éventuelle, **non demandée dans le présent gel**.
- Dans l'écran d'édition « SceneVibe — recalage », les commandes visuelles « Annuler » et « × Fermer » ne sont pas accessibles à la télécommande dans le parcours constaté. BACK permet néanmoins d'annuler et le recalage par OK fonctionne. **Défaut UX mineur non bloquant : aucune modification autorisée maintenant.**

**Limites de cette réception :** elle ne qualifie pas la distribution publique sans compte créateur, Netflix/Disney+, la persistance de consentement automatique, le comportement publicitaire de Prime ni une publication Play Store. Le caractère non intrusif de l'overlay et le fait que Prime continue de tourner derrière la fenêtre ont été observés ; la totalité des états de transport de Prime n'a pas fait l'objet d'une instrumentation.

**Décision de gel propriétaire :** conserver strictement le code Android, l'APK v32 signé, le certificat, les contrats et le Cloud dans leur état qualifié. La présente actualisation GitHub est **documentaire uniquement**. Conserver la PR Draft #49 ouverte et non fusionnée ; ne pas promouvoir ni publier l'APK. Toute évolution (y compris UX) nécessite un mandat distinct et une nouvelle qualification de non-régression.

## Preuves conservées

- [Provenance CI et artefacts](proofs/ci.json), [provenance APK](proofs/delivery.json), [signature](proofs/apk/apksigner-verify.txt), [permissions](proofs/apk/permission-comparison.json).
- [Inventaire JVM/UI](proofs/unit/results.json), [lint](proofs/lint-results-tvux002.xml).
- [Ouverture native](proofs/native/public001b-native/mute-p0-open.json), [recalage privé](proofs/native/public001b-native/mute-private-proof.json), [recalage public](proofs/native/public001b-native/mute-public-proof.json).
- [Mise à jour signée](proofs/upgrade/v32-retained.log), [callback du service signé](proofs/upgrade/signed-shortcut.log).
- Les preuves P0 initiales à la racine de `proofs/p0` concernent `4ef8ba7…` et précèdent l’intégration sélective du recalage. Le sous-dossier `proofs/p0/mute002-p0` concerne la source finale.

