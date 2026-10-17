# TV-FIRST 006 — qualification évolutive bornée

Ce dossier autorise uniquement le delta fini décrit dans `manifest.json`, sur la BASE
`a3cf497dc8bba6a2d1980476d7c24cbbe1aa153a`, arbre
`f51796913d549e660133243a94cb62d19bffb2ce`. Il ne remplace aucun sceau 002/003.

`gate.py` compare les 341 chemins de la BASE, les cinq modifications déclarées et les
seize ajouts nommés individuellement. Les 336 autres fichiers sont conservés octet par
octet. Le manifeste est épinglé par SHA-256 dans le gate. Pour éviter sa référence
circulaire, seul ce littéral est normalisé à 64 zéros pour l'empreinte du gate dans
le manifeste. La livraison externe donne aussi l'empreinte brute du gate, du manifeste,
du patch, du commit et de l'arbre. Il n'existe pas de commande de reseal en CI.

Les contrôles couvrent filiation, arbre de BASE, HEAD d'événement s'il est fourni,
contenu, modes Git et modes exécutables locaux, index, worktree, liens symboliques,
fichiers supplémentaires, suppressions et rétention des Javadocs historiques.
Seules les sorties nommées `.git`, `.gradle`, `build`, `app/build` et
`local.properties` sont exclues de l'inventaire des sources. Aucun préfixe de source
n'est une autorisation ouverte.

## Rejouer localement après application et commit du patch

Utiliser un clone local complet, propre, dont la BASE est ancêtre de HEAD. Python 3,
JDK 17, Gradle 8.9, SDK Android 35 et build-tools 34/35 doivent être disponibles.
Les dépendances Gradle et Robolectric nécessitent un accès aux dépôts officiels ou
un cache déjà rempli. Aucun credential SceneVibe n'est nécessaire.

```sh
python3 -B qualification/tvfirst006/gate.py --expected-head COMMIT_LOCAL_A_VERIFIER
python3 -B qualification/tvfirst006/test_gate.py -v
python3 -B qualification/tvfirst006/run.py all
```

`SCENEVIBE_GRADLE` peut désigner le chemin absolu d'un lanceur Gradle local. Ne pas
modifier le code produit pour adapter un proxy ou un SDK local. Ne pas utiliser
`python -O`. La phase `all` exige un checkout sans `app/build` ni `.gradle`.
`EXPECTED_HEAD` sert à lier le runner au SHA d'événement en CI.

Ordre du runner :

1. Écrire un reçu FAIL « non terminé », puis autoriser le code courant exact 006.
2. Cloner les objets locaux et vérifier le gate 003 inchangé sur son arbre exact
   de 341 fichiers à la BASE ; exécuter ses huit contrôles positifs/négatifs.
3. Seulement ensuite construire la vue historique de 276 fichiers à
   `17cbe36ae99ac7f48aaf861e0d1feac702a9e521` et exécuter ses 118 assertions Python.
4. Exécuter les quatorze contrôles 006 ; construire les trois variantes courantes
   sans signature, Cloud vide et LAN DEV OFF ; vérifier leur packaging.
5. Exécuter tous les JVM debug et observation, les trois lint, et le test de
   fermeture release. Les tests de service et lecteur tournent sous Robolectric
   Android 12/API 31 et API 35. Ce ne sont pas des essais de système physique.
6. Compter les XML réellement produits : 1051 cas historiques par profil complet,
   38 cas de présentation debug, 39 nouveaux cas 006 debug et 63 nouveaux cas 006
   observation. Les 22 cas de présentation issus des sources de test communes sont
   aussi exigés et comptés séparément en observation (les 16 vues natives sont dans
   testDebug seulement). Le skip opt-in M1 historique est contrôlé par le compteur historique
   inchangé et doit être identique en observation ; aucun nouveau skip n'est admis.
7. Réautoriser le checkout avant d'écrire le reçu final.

Le compteur JVM historique reçoit uniquement son bucket de résultats issus du code
courant, dans sa vue d'entrée originale. Les nouveaux cas ne sont ni cachés ni
présentés comme historiques. `jvm-current.json` contient tous les comptes et suites.
Les sous-phases `python`, `jvm`, `packaging` sont étiquetées ; leur PASS ne signifie
pas que `all` a été exécuté. Tout échec remplace le reçu précédent par FAIL.

Les APK produits sont exclusivement des intermédiaires locaux non signés de
packaging. Aucune publication, installation, candidature Sony, signature stable ou
même signature debug n'est réalisée. Le workflow 006 préparé n'uploade que les
rapports et XML ; il n'a pas été publié ou exécuté à distance dans le lot 006.

Le routeur Android traite 006 en premier et 003 uniquement si son gate exact passe.
Une génération absente ou corrompue échoue ; aucune erreur ne retourne vers le job
historique de signature, dont le corps est conservé sans être sélectionnable ici.

Résultat attendu du lot : revue propriétaire d'observation, jamais qualification
MEDIA/TEXT Sony. Voir `docs/tv-first-006-observation.md` et le rapport externe.
