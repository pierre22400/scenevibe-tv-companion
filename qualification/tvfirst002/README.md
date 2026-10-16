# SceneVibe TV-FIRST 002 — qualification locale

BASE Android obligatoire : `17cbe36ae99ac7f48aaf861e0d1feac702a9e521`.
Référence UX : `scenevibe-tv-handoff.zip`, SHA-256
`4de03132c37b0baa3931e0c6a799ce2ce1dc4d9d8913b9796b2f3424d40705c2`.

Cette livraison transpose la présentation en Java / Android Views. Elle ne raccorde
aucune nouvelle API et ne modifie pas le moteur d'exécution M4/M5. M6 et E5 restent
hors de cette composition. Il n'y a aucun candidat physique ni autorisation Sony.

## Exécuter les contrôles

Prérequis : JDK 17, Gradle 8.9, SDK Android 35 et Build Tools 35.0.0. Le dépôt de base
ne fournit pas de wrapper Gradle. Les dépendances de production restent inchangées ;
Robolectric 4.14.1 est une dépendance de test uniquement.

Le contrôle WORK repart d'un checkout sans sorties générées. Après un cycle de
qualification Robolectric, l'outillage local AGP peut conserver un ancien dex de
`R$color` : avant une reconstruction, supprimer uniquement `app/build` et `.gradle`
de ce checkout (jamais le cache global ni des données d'application). Un simple
`:app:clean` n'a pas suffi dans cet environnement. Un checkout neuf convient aussi.

```sh
SCENEVIBE_CLOUD_ORIGIN= SCENEVIBE_ENABLE_LAN_DEV=false gradle \
  :app:clean --no-build-cache --rerun-tasks :app:assembleDebug :app:assembleRelease \
  :app:testDebugUnitTest :app:lintDebug :app:lintRelease

SCENEVIBE_CLOUD_ORIGIN= SCENEVIBE_ENABLE_LAN_DEV=false gradle \
  :app:testReleaseUnitTest --tests '*.presentation.TvReleaseSourceTest'

python3 -B qualification/tvfirst002/verify_delivery.py
python3 -B qualification/tvfirst002/run_historical_retention.py
```

Le chargement du SDK Robolectric nécessite Maven Central, ou un répertoire de
dépendances préchargées configuré par les propriétés documentées de Robolectric.
L'outillage local WORK a utilisé le proxy de son environnement et un JAR API35
préchargé, sans ajouter ce proxy à l'application ni au dépôt.

`forkEvery=1` sépare les classes de test : les fixtures HTTPS historiques installent
une `URLStreamHandlerFactory` globale qui intercepterait autrement le chargement du
SDK de Robolectric. Aucun ancien test n'est exclu ni modifié.

## Nature des preuves

- Les tests JVM historiques exécutent le code Android modifié réellement compilé.
- Les tests `TvNativeViewsTest` exécutent de vraies Views Android et leur cycle de
  vie dans Robolectric API35, avec rendu graphique natif. Ils ne sont ni un essai
  d'émulateur Android TV ni une preuve Sony.
- Les images sont écrites dans `app/build/tvfirst002/native-views/`. Les deux
  configurations sont xhdpi / 1920×1080 et tvdpi / 1280×720. La composition est
  mesurée en dp/sp ; aucune transformation de type `TvStage` n'est utilisée.
- `verify_delivery.py` prouve les octets des fichiers historiques protégés, les
  méthodes d'action, les docstrings, les couleurs, les licences et l'exclusion du
  corpus de démonstration de l'APK release.
- `run_historical_retention.py` exécute toutes les assertions Python historiques
  intactes sur leur vue historique exactement reconstruite, après passage du
  contrôle d'intégrité de l'arbre courant. Il ne qualifie pas la nouvelle UI.

## Inventaires historiques figés

La commande brute `python3 -m unittest discover -s tests -v` sur l'arbre étendu
refuse les nouveaux fichiers et les deux edits déclarés : les inventaires M4/M5
décrivent des phases closes. Ce résultat n'est pas annoncé vert. Le gate de
rétention ci-dessus distingue cette contrainte de provenance des tests logiciels
de l'application courante. Les baselines, les workflows et les assertions
historiques sont conservés à l'identique.

Avant une future PR d'intégration, le propriétaire doit faire examiner le chaînage
de ce gate additif dans la qualification CI ; aucune configuration GitHub distante
n'est modifiée dans ce lot local.

## Démonstration et release

`src/debug` contient le corpus synthétique du handoff, avec identités préfixées
`demo:`. Les scénarios sont choisis explicitement dans Réglages → Scénarios locaux
de qualification. Aucun délai n'avance les étapes. Le dialogue natif remplace
l'outil concepteur F2, qui n'est pas porté comme surface de production.

`src/release` contient uniquement une source indisponible : liste vide, aucune
sélection, aucun scénario et aucun état prêt. Les commandes préexistantes du
Companion restent accessibles dans Réglages, ainsi que les diagnostics techniques
et leur reset exceptionnel. Les tests n'exécutent aucune connexion Cloud réelle.

Une affirmation READY non démonstrative nécessite un binding identique pour la
confirmation Account, l'affectation, l'installation et l'ACK. L'admission MEDIA est
une preuve indépendante. Cette projection d'affichage n'est pas le nouveau gate
du runtime, lequel appartient au futur lot de raccordement.

## Ressources et adaptations

Les 23 tokens OKLCH sont convertis une fois en sRGB dans `scenevibe_colors.xml` ;
`color-tokens.json` conserve les deux valeurs. Space Grotesk et Inter sont les
fichiers variables originaux, avec leurs licences OFL. Les six illustrations SVG
du handoff sont conservées dans les assets de provenance et rasterisées en PNG
1280×720 pour `ImageView.CENTER_CROP`. Les icônes Lucide sont des vectors Android
accompagnés de leur licence ISC et de leurs empreintes de provenance.

Les adaptations sont bornées : cibles de bouton 48dp, bague native 3dp avec écart
3dp, ombre de carte native, actions qui reviennent en colonne si la police ne tient
plus, défilement minimal pour une action initiale hors écran, paramètres et mesures
réels, texte de démonstration renforcé et texte clair sur les boutons verts. Les
contrôles historiques conservent leurs libellés et leurs listeners.

Le rapport de livraison contient la comparaison aux vingt références, la matrice
R1–R12 et le plan du raccordement réel séparé.
