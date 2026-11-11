# SceneVibe Public Viewer 001-A — Mise à jour et récupération Sony

## APK et provenance

Les trois APK portent le package `com.scenevibe.tvcompanionpoc`. Le banc vérifie les signatures v2 et v3 et le certificat M5 historique `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.

| APK | Source applicative | SHA-256 |
| --- | --- | --- |
| v32, référence physique | `01da23263c0d042192d536c2ed497b6b34daecc1` | `d4e45b19dd6fd99e6ab6d7cdfa4dfd31e04bac8a50d43e366ba284ff3c5e612b` |
| v34, candidat public | `b2b5a1c4339bca517e6a4997d943f456ddc0f4e2` | `ec03ac6eed6271351b4331df3556996092d91355f4bb3185710ce5b92f5be4cd` |
| v35, candidat de récupération | `0c252fc9f2c57e2a12c367187d9ecf9a23570510` | `0ec4d42e53cfb3a8369693be75e040de5a163a14e559469b11c1f2b4d1e911ad` |

Le workflow de signature vérifie l’APK v34 non signé de SHA-256 `7084c6353105982f35696dd35e39352416b6af57794bec21a7ec120cdba703a6`, produit par la CI de `b1ed6637e050348247e21b3526fe1dcc9101f546`. La source v35 dérive directement de la source v32. Les deux seules modifications de code de build changent son numéro et son suffixe de version. Les contrôles portent sur les mêmes binaires de livraison à chaque essai. La recherche dans les branches, PR et releases accessibles n’a identifié aucun candidat v35 antérieur. Le numéro installé doit être lu sur la Sony lors de la recette autorisée.

- [CI de provenance et signature M5](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/43079608451)
- [Archive des trois APK, manifestes, signatures et empreintes](https://chatgpt.com/library?file_id=libfile_f112a6a640208191b8ed710196f5baed&account_id=personal)
- [PR Draft TV #49 — référence physique v32](https://github.com/pierre22400/scenevibe-tv-companion/pull/49)
- [PR Draft TV #50 — candidat public](https://github.com/pierre22400/scenevibe-tv-companion/pull/50)
- [PR Draft Cloud #63 — qualification Web et HTTPS](https://github.com/pierre22400/interface-scenevibe/pull/63)

## Audit des données et autorisations

| Périmètre | Constat |
| --- | --- |
| Manifeste v34 | `RECEIVE_BOOT_COMPLETED`, permission Android normale attribuée automatiquement, et récepteur public non exporté pour `BOOT_COMPLETED` et `MY_PACKAGE_REPLACED`. Le récepteur historique reste désactivé dans cette variante. La comparaison normalisée du manifeste montre que tous les autres services et récepteurs ont conservé leurs déclarations. |
| Manifeste v35 | Le diff du manifeste compilé v32/v35 contient uniquement le numéro et le nom de version. |
| Superposition, MEDIA et MUTE | L’identité du package, le composant `CompanionShortcutService`, sa protection `BIND_ACCESSIBILITY_SERVICE`, le composant `MediaSessionAccessService` et le type `specialUse` des services existants sont conservés. |
| Privé | Identité `tvfirst008_identity`, secrets chiffrés dans `cloud_secret` via Android Keystore, cache `multimedia003d/<génération>`, métadonnées `multimedia003d_cache_<génération>`, préférences `multimedia_user_preferences`. Aucun changement de format privé repéré dans le diff v32/v34. |
| Public | `public001b_identity`, cache `public001b/<génération>` et `public001d_playback` restent distincts du privé. `public_viewer_preferences` ajoute les deux choix persistants. Le code reprend les valeurs publiques antérieures présentes et initialise les valeurs manquantes. |
| Autorité | L’autorité privée doit toujours être admissible à la revalidation Cloud. Une expiration, une révocation ou un réglage Android retiré restent soumis au parcours normal de rétablissement. |

## Résultats sur banc

Banc qualifié au commit `43216ab39d266dc7bfa0900863a0939ef01dc635`, quatre tests d’instrumentation réussis.

**READY FOR SONY — qualification du banc Android terminée.**

| Contrôle | Observation |
| --- | --- |
| Provenance et signature M5 v2/v3 | PASS sur les trois APK et vérification de la signature du candidat exact. |
| Chaîne signée | PASS. Même image Android TV API 31, même package et même UID, installations `adb install -r` v32 → v34 → v35. |
| État privé | PASS sur v32, v34 et v35. Identité, génération, déchiffrement du secret par Android Keystore, reçu et empreintes des ressources du cache, et préférences privées conservés. |
| Lecture privée | PASS sur v32, v34 et v35. Trois demandes TEXT et trois démarrages MP3 par version. Aucun téléchargement privé supplémentaire pendant les mises à jour. |
| Public HTTPS sur v34 signée | PASS. Un code, envoi Activity fermée, reçu correspondant à la commande, cache vérifié, quatre TEXT et trois AUDIO. Deuxième Track avec AUDIO toujours désactivé après le choix volontaire. |
| Raccourci Android | PASS pour l’affectation enregistrée, les accès conservés et la connexion du service avant instrumentation sur les trois versions. Le raccourci système a ouvert le panneau sur v32, v34 et v35. D-pad, recalage de l’horloge SceneVibe, annulation BACK, retour du focus et restitution native après fermeture ont été observés. |
| Qualification de récupération | PASS. Après le parcours public v34, v35 a rouvert le cache privé, restauré la restitution TEXT/AUDIO et recalé la véritable horloge du Companion par le raccourci système. |

- [CI de la chaîne signée et de la distribution publique](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/43084711905)
- [Archive pérenne des preuves Android](https://chatgpt.com/library?file_id=libfile_5760cbc26294819183244ac6a620b73f&account_id=personal)
- [Journaux et instantanés Android](https://github.com/pierre22400/scenevibe-tv-companion/actions/runs/43084711905/artifacts/13885834950)

Le banc Android TV API 31 utilise un lecteur local avec une vraie MediaSession et les coordonnées de l’œuvre de référence. Le grant privé et les médias privés sont des fixtures propres à l’émulateur. Le parcours public utilise les véritables réponses HTTPS et la Track créée depuis le Studio sur le Preview autorisé. La qualification Web PC et mobile et la publication continue sont documentées dans la PR Cloud #63.

Le banc relève l’affectation enregistrée et la connexion Android du service avant de démarrer son instrumentation. Cette dernière peut arrêter le processus cible et marquer le service `Crashed`. Dans ce cas précis, le banc rétablit la même affectation dans l’émulateur avant les contrôles fonctionnels. Le raccourci BACK + BAS traverse ensuite le système Android TV. Le firmware Sony, la touche MUTE physique et Prime Video restent à vérifier sur la BRAVIA.

- [Niveau de protection de RECEIVE_BOOT_COMPLETED](https://developer.android.com/reference/android/Manifest.permission#RECEIVE_BOOT_COMPLETED)
- [Code Android 12 de démarrage de l’instrumentation](https://android.googlesource.com/platform/frameworks/base/+/refs/heads/android12-release/services/core/java/com/android/server/am/ActivityManagerService.java)


## Protocole Sony à exécuter après décision propriétaire

1. Préparer les APK v34 et v35. Sur le PC, vérifier leurs SHA-256 puis les versions et le certificat M5. Confirmer sur la Sony que le package installé a le `versionCode=32` et que le privé, TEXT/AUDIO et MUTE sont opérationnels. En ADB, lire `adb -s <sony> shell dumpsys package com.scenevibe.tvcompanionpoc` et relever `versionCode`.
2. Installer v34 avec `adb -s <sony> install -r scenevibe-companion-public-viewer-v34-m5.apk`. Contrôler l’association privée et la Track historique avant d’associer le navigateur au parcours public. Utiliser le code public de SceneVibe une seule fois pour ce navigateur.
3. Envoyer la Track de qualification depuis le catalogue. Attendre « Track prête » avec l’interface Companion fermée. Dans Prime Video, ouvrir l’œuvre de référence, vérifier TEXT et AUDIO, ouvrir MUTE, effectuer un recalage, fermer le panneau et vérifier la reprise et le focus. Essayer une seconde Track et vérifier que les préférences et l’association survivent à un redémarrage de la Sony.
4. Si la récupération s’impose, lire à nouveau le `versionCode` installé. Continuer uniquement si le paquet cible est celui attendu et si `35 > versionCode installé`. Installer `adb -s <sony> install -r scenevibe-companion-recovery-v35-m5.apk`. En cas de refus d’installation, relever le message et conserver les données de la TV.
5. Après récupération, rouvrir la Track privée historique. Vérifier la même association et le cache, les deux types de commentaires, MUTE, l’ajustement de l’horloge, BACK et la restitution du focus à Prime. Relever séparément tout refus de revalidation Cloud ou toute autorisation Android révoquée.
6. Après v35, toute mise à jour ultérieure du même package devra porter un numéro supérieur. Conserver v32 comme référence binaire et documentaire.

**Sony physique — NOT EXECUTED.**
