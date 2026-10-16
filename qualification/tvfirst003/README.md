# TV-FIRST 003 — qualification additive exacte

BASE immuable : `17cbe36ae99ac7f48aaf861e0d1feac702a9e521`.
Livraison 002 acceptée : archive SHA-256
`dce1e0066dc03f72afec18f6569b2de328f6955c60677bdc83534fa93f2261bd`.
Son manifeste original est conservé dans `delivery002-files.json`, SHA-256
`5035f6f5b2ed4ef6a7373e555d069b089039fdb78d5d6bd9a3bcf7c7258e53ee`.
Les 60 fichiers 002 restent intégralement identiques à ce manifeste, y compris
ses anciens scripts : le point d'entrée courant est **003**, pas le gate 002.

## Autorisation de contenu et commit

`gate.py` vérifie l'objet Git BASE complet, sa présence dans l'ascendance du HEAD,
les 60 contenus acceptés, le manifeste d'origine, l'inventaire exact des fichiers,
les modes Git, les modes exécutables locaux et l'égalité HEAD/index/arbre source.
Aucun chemin supplémentaire n'est autorisé par un simple préfixe de répertoire.
La vérification accepte un commit nouveau **seulement avec le contenu scellé**.
`--expected-head` lie aussi le checkout au SHA de l'événement GitHub. La CI teste
le HEAD de la PR, pas son commit de merge synthétique. `fetch-depth: 0` fournit la
BASE et les ancêtres requis. Un checkout superficiel incomplet échoue explicitement.

Les 274 chemins historiques protégés sont comparés à BASE : **273 identiques** et
**1 exception CI déclarée**, `.github/workflows/android-debug.yml`, dont le contenu
courant est scellé. Les 17 méthodes et toutes les docstrings de MainActivity sont
contrôlées séparément. Tous les tests, fixtures, baselines M4/M5 et le résumé JVM
historique restent inchangés. Les assertions ne sont ni réécrites ni filtrées.

`seal.json` énumère les seuls fichiers du delta CI 003 et leur SHA-256. Son propre
SHA-256 est fixé dans `gate.py`. Pour éviter la référence circulaire, le hash du
verifier dans le sceau normalise **uniquement** cette déclaration de 64 caractères
à zéro ; toutes ses autres lignes restent couvertes. Le rapport de livraison
externe donne aussi les hashes bruts, le commit et l'arbre Git. Ces références
externes sont l'ancre de revue : comme tout code CI d'une PR, le verifier et son
sceau doivent être examinés contre la livraison acceptée. Aucun outil de génération
ou d'actualisation du sceau n'est livré dans le dépôt. Un changement ultérieur,
même documentaire, exige une nouvelle autorisation/revue de contenu.

Les seuls fichiers locaux ignorés sont les métadonnées `.git`, les sorties racine
`build`, `.gradle`, `app/build` et `local.properties` (outillage SDK local). Aucune
exception ne couvre `app/src`, `tests`, `.github` ou `qualification`.

## Chaîne logicielle reproductible

Prérequis : checkout neuf au commit de livraison, JDK17, Gradle8.9, SDK35 et
build-tools34.0.0 et 35.0.0 ; accès aux dépôts Google/Maven/Gradle. AGP8.7.3 choisit 34.0.0 par défaut ; 35.0.0 reste disponible pour les outils
historiques. Robolectric4.14.1 et
`forkEvery=1` restent ceux de 002. Aucun wrapper ni dépendance applicative ajouté.

```sh
python3 -B qualification/tvfirst003/gate.py --expected-head "$(git rev-parse HEAD)"
python3 -B qualification/tvfirst003/test_gate.py -v
python3 -B qualification/tvfirst003/run.py all
```

`run.py all` exige un checkout sans `app/build` ni `.gradle` ; il ne supprime rien.
Il produit des preuves dans `build/tvfirst003` et échoue au premier résultat invalide.

1. Autorisation exacte du code courant.
2. **Python, rétention BASE** : reconstruction exacte des 276 fichiers historiques,
   projection des deux edits 002 et du seul edit workflow 003 vers BASE, omission
   explicite des ajouts. Exécution des 118 assertions originales sans sélection.
   Ce PASS ne qualifie pas la nouvelle UI. Le résultat Python brut rouge sur
   l'arbre étendu demeure une preuve d'inventaire, jamais un PASS silencieux.
3. **Code courant réel** : assembleDebug + assembleRelease, sans Cloud ni LAN DEV.
4. Contrôle des APK locaux : fixture JSON et classe LocalDemo en debug seulement,
   absence en release ; aucune signature stable ni publication d'APK.
5. **JVM courant + lint** : tous les tests debug, lintDebug et lintRelease.
6. **Release séparée** : testReleaseUnitTest ciblant le test de fermeture 002.
7. Résumé de tous les XML courants : 1089 cas debug, 1088 PASS, un skip historique
   autorisé ; 38 cas de présentation inclus ; 1 cas release, aucun skip. Le résumé
   historique intact reçoit uniquement ses 1051 cas XML réels, dans une vue temporaire
   explicitement nommée ; ses fixtures et journaux différentiels restent vérifiés.
   Les 38 cas supplémentaires sont validés séparément, sans les faire disparaître.

Les phases `python`, `jvm` et `packaging` sont accessibles pour diagnostiquer une
étape ; elles ne remplacent pas le verdict de `all`. `SCENEVIBE_GRADLE` permet un
exécutable local absolu. `SCENEVIBE_QUALIFICATION_INIT_SCRIPT` permet uniquement une
configuration d'outillage locale (proxy/SDK Robolectric préchargé) ; la CI ne le
renseigne pas. Le rapport doit signaler tout init script utilisé. Les variables
Cloud/LAN sont forcées par le runner, même si l'environnement les avait définies.

## Workflows et portée

Le workflow debug garde ses déclencheurs historiques (push sur les branches nommées,
PR vers main et manuel). Un job de profil vérifie le sceau avant de choisir le
chaînage 003. Le job debug historique est conservé avec ses commandes mais n'est
pas exécuté pour le profil 003 ; ses builds Cloud/LAN, signatures et uploads d'APK
ne sont donc pas activés par ce profil. Le workflow réutilisable 003 n'a qu'un
`workflow_call`, sans secrets hérités ; il dépose uniquement rapports/XML/lint,
avec conservation 30 jours. Un gate rouge n'est jamais converti en fallback vert.

Les trois workflows PR d'émulateur M4/M5/API35 restent inchangés et seront déclenchés
par une future PR, Draft comprise. Ils ne sont pas une qualification Sony. Le smoke
historique compile une origine Cloud ; ce n'est pas une nouvelle connexion ou un
appairage autorisé. Le workflow release manuel reste historique : ne pas le lancer
pour cette livraison ; son Python brut est encore incompatible avec l'inventaire
étendu et ses étapes signent une APK. Une adaptation éventuelle relève d'un autre GO.

Le nom du job debug historique reste présent mais est SKIPPED pour 003. Avant de
considérer la CI distante qualifiée, vérifier les checks exigés par la protection
`main` et la présence du nouveau job logiciel effectif. Aucun paramètre distant
n'est lu ou modifié par ces scripts.

## Négatifs et limites

`test_gate.py` ne modifie que des clones temporaires : positif après commit,
altération OverlayService, altération TvNavigation, mutation historique commitée,
fichier supplémentaire, script CI modifié, manifeste 002 modifié et mauvais HEAD
d'événement. Chaque refus exige le code retour 1 et une raison JSON précise.
Les copies sont supprimées systématiquement ; la référence reste intacte.

Ce lot ne change aucun composant Android, transport, allocateur, cache, renderer,
scheduler, contrat Cloud, M6 ou E5. Il ne qualifie pas une TV physique, une signature,
une installation, ni un raccordement Cloud. Aucun push/PR/merge/déploiement ici.
