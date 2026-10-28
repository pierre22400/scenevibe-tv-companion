# SCENEVIBE — TV-RUNTIME 003 — v23

Date : 28 octobre 2026. Branche `work/scenevibe-ad-sync-001`, PR #31 en brouillon.

## Constat et causes démontrables

Le propriétaire décrit une absence de commentaires sur **Sony v22**. Il confirme le cache 16 TEXT + 3 AUDIO et la notification Sony introuvable. Ce sont des observations physiques, jamais des résultats automatisés.

Source physique examinée : `Texte collé(20261028-082023).txt`, Library `libfile_42583693b97c819199e3de0cc5f05331`. Ce fichier contient **100 dernières lignes logcat**, précédées d’une commande PowerShell et suivies du prompt, soit 103 lignes de transcription. Il ne contient pas le journal complet du 28 octobre. Les octets récupérés font 12922 octets, SHA-256 `b9218fbd084174136c3a3f45f9dd86b39aa2731cb33dccde581effb61b1ff7aa`. Le journal complet v21 (995192 octets, UTF-16LE BOM, SHA-256 `6b89d011a18c31a9180837e51986e3a04543cccda0a67ae7c9fc41b3be233c5d`) reste distinct ; son analyse antérieure est dans `tv-ux002b-ad-sync001c.md`. Aucune ligne manquante n’est reconstituée.

Le premier TEXT du paquet inchangé est à **32988 ms**, durée **8000 ms** ; le suivant à **52988 ms**, durée **10000 ms**. Les MP3 restent à 90000, 300000 et 900000 ms. À 09:12:01, démarrer à 43788 ms ne rend donc normalement aucun TEXT immédiatement : la première fenêtre est expirée et la prochaine n’a pas commencé.

**Défaut logiciel reproduit** : v22 marque le premier TEXT consommé lors de cette arrivée tardive. Une absence d’identité ou d’horloge appelle `onInterruption()`, qui efface la dernière position mais conserve tous les événements consommés. Revenir ensuite à 20614 ou 30009 ms ne réarme pas le premier TEXT, même lors du franchissement ultérieur de 32988 ms. Cette conservation uniforme, ajoutée pour empêcher le redémarrage des MP3, supprime aussi une relecture TEXT légitime. La suite compare le comportement v22 et v23 avec les coordonnées physiques.

Cette reproduction explique un chemin concret d’absence de TEXT. Elle ne prouve pas que c’est l’unique cause de chaque incident Sony : le journal ne donne ni les préférences TEXT/AUDIO, ni l’ensemble des positions à 250 ms, ni les compteurs de calendrier. Il ne démontre pas un défaut du renderer ou du décodage MP3. Les plages où aucune fenêtre n’est programmée restent une attente normale.

`MEDIA_IDENTITY_INVALID` signifie refus du matcher historique (paquet + identifiant précis ou titre d’épisode spécifique + durée), jamais publicité détectée. Les échantillons PAUSED/brut0 sans position sélectionnée ne sont pas convertis en horloge artificielle. `MEDIA_CLOCK_INVALID` pouvait recouvrir état, position, vitesse, ancre ou durée invalide : les traces v22 ne permettent pas de distinguer rétrospectivement chacun. v23 ajoute ces catégories et les faits temporels bornés.

`OVERLAY_PERMISSION_REQUIRED` à 09:11:39 provient d’un `Settings.canDrawOverlays(context)==false`, puis ferme la session ; le propriétaire redémarre à 09:11:48. Le code ne dépend pas d’un état UI pour cette vérification. Il n’existe pas de preuve d’une fausse révocation Android ou de la cause système du refus. v23 respecte ce refus mais suspend les effets, conservant l’admission en mémoire tant que son véritable consentement/reçu reste valide. Si Android tue le processus, cette admission ne survit pas artificiellement.

## Correctifs ciblés

- Même Companion, OverlayService, cache, matcher d’identité, calendrier et lecteurs natifs. Aucun nouveau lecteur ni transport Prime.
- Après interruption et retour de plus de deux secondes en arrière, **deux positions compatibles consécutives** dans une progression bornée réarment seulement les **TEXT futurs à partir du retour**. Les MP3 consommés restent consommés. Une observation isolée ne suffit pas. Les profils historiques gardent leur politique M5 par défaut ; la v23 choisit explicitement cette récupération TEXT.
- Aucune relecture hors fenêtre, aucun rattrapage en rafale, aucune modification des timestamps éditoriaux. Le recalage avec confirmation explicite de seek garde sa politique préexistante.
- Deux MediaSessions également compatibles et PLAYING conservent la session précédemment choisie au lieu de suivre un simple changement d’ordre. Une identité incompatible ou une session PAUSED ne supplante pas une autre compatible PLAYING. Un objet MediaController disparu pendant la lecture est écarté avec motif, sans accepter ses métadonnées ni arrêter la boucle entière.
- Permissions manquantes : tous les effets restent fermés. L’admission et les fichiers restent conservés ; le retour effectif des permissions et de MEDIA compatible permet la récupération. Les expirations/révocations réelles d’autorité arrêtent toujours la session.
- AD-SYNC reste consultatif : pas de signal publicitaire Prime disponible. ON confirme la récupération sur deux captures fraîches, OFF reste utilisable avec MEDIA compatible. La suspension manuelle survit à ON/OFF. L’absence MEDIA ne supprime pas le cache et ne réarme pas les MP3.

## Diagnostic et parcours de la track

Le cache est restauré en lecture seule sans catalogue ni téléchargement. L’écran indique le titre réellement présent dans le paquet, les 16 TEXT + 3 AUDIO, puis l’état de restitution et le prochain événement. L’affirmation isolée « service actif · activation conservée » est remplacée par une disponibilité contextualisée.

`SceneVibeRuntime` trace les transitions du calendrier (total, consommés, futurs, fenêtres, DUE TEXT/AUDIO, TEXT réarmés), `TEXT_EVENT_ELIGIBLE`, `TEXT_RENDER_REQUESTED`, `TEXT_WINDOW_ATTACHED` ou refus, préparation et STARTED AUDIO. Aucun identifiant d’événement, contenu éditorial, compte, token ou session opaque n’est journalisé. Une simple variation de position n’émet pas un log à chaque tick. Une fenêtre attachée est une preuve logicielle WindowManager, pas une perception visuelle/acoustique sur Sony.

Une nouvelle admission n’est requise qu’après arrêt volontaire, perte du processus ou invalidation réelle du consentement/reçu/association. Le cache seul n’autorise pas une restitution après ces limites. Une seule confirmation directement accessible depuis le cache restauré valide le démarrage et l’édition de référence ; aucune nouvelle formalité n’est ajoutée.

## Particularité du contrôleur Android observée pendant la qualification

Le test natif a établi que publier `PLAYING` avec une position `-1` n’assure pas que le contrôleur tiers reçoive `-1` : sur le banc API31, `getPlaybackState()` retourne ensuite une position positive extrapolée par Android. Le code AOSP `MediaSessionRecord.getStateWithUpdatedPosition()` confirme cette transformation côté système. Les traces `rawMs` désignent donc la valeur retournée par le **contrôleur public Android**, pas une ancre interne de Prime. Aucune inspection de Prime n’est effectuée.

Le cas instrumenté d’horloge invalide utilise désormais `BUFFERING/-1`, réellement livré au Companion puis refusé. Les règles de position, état, identité et fraîcheur du Companion ne sont pas affaiblies pour accommoder le test. Source : https://android.googlesource.com/platform/frameworks/base/+/master/services/core/java/com/android/server/media/MediaSessionRecord.java . Cette propriété Android ne permet pas d’attribuer rétrospectivement les alternances physiques à un mécanisme interne précis de Prime.

## Téléphone — 120 minutes dès activation TV

Le serveur, code d’association et bearer partagent une échéance monotone absolue **7200000 ms**. Association tardive, polling, commandes et reconnexion HTTP ne la renouvellent pas. Aucun autre délai court de session n’est ajouté. Les timeouts de requête (2 s) protègent une connexion lente, sans expirer le téléphone. L’état retourné inclut le temps restant réel de cette échéance, affiché sur TV et page.

TEXT/AUDIO/AD-SYNC, Suspendre/Reprendre/Recaler, afficher/fermer le moniteur restent les mêmes commandes SceneVibe. Code TV à usage unique, authentification du téléphone, refus Host/Origin, commandes bornées et numérotées, arrêt volontaire, révocation à l’arrêt service/mode conservés. Aucun port Internet, Cloud, commande Prime, permission sensible ou renouvellement automatique. HTTP local **en clair**, accepté seulement sur réseau privé de confiance. Un rechargement complet de page perd le bearer non persistant ; garder la page ouverte pendant la séance.

Les tests de frontière temporelle injectent une horloge monotone et envoient de vraies requêtes HTTP jusqu’à 119:59,999 puis à 120:00,000. Ils ne constituent pas une endurance physique de deux heures. Chromium utilise les assets exacts à viewport téléphone avec ce même serveur, propriétaire de commandes explicitement simulé ; l’intégration Android teste séparément le propriétaire réel. Aucune équivalence avec une télécommande Sony n’est revendiquée.

## Qualification

Les résultats effectifs, commits, signatures et liens des runs sont dans le livrable final. La campagne utilise les 19 événements et les trois identités MP3 déjà disponibles, sans génération d’AUDIO ni requête fournisseur. Le banc Android TV API31 emploie MainActivity, OverlayService, WindowManager, le décodeur existant et une **MediaSession publique appartenant au banc**. Il ne contient ni Prime ni Sony. La révocation de permission du nouveau test concerne uniquement l’émulateur jetable, contrôlé avant toute commande par son numéro emulator-* et ro.kernel.qemu.

## Limites physiques connues

Sony v22 : affichage passif observé, 000 non reçu, notification introuvable, D-pad inaccessible. La mission conserve ce moniteur passif ; elle ne recommence pas un chantier de focus. Le téléphone est l’alternative désormais acceptée. Aucun PASS physique v23 ni garantie d’accès Sony seul.

La cause interne des alternances Prime PLAYING/PAUSED, des positions publiées et du refus d’overlay à 09:11:39 reste non démontrée. La v23 offre les diagnostics nécessaires sans affaiblir les identités. Aucun signal publicitaire n’est inventé.

## Essai Sony après autorisation d’installation — parcours court

1. Mettre à jour vers l’APK v23 M5 sans désinstaller ni effacer de données. Vérifier les autorisations Android overlay et MEDIA.
2. Retrouver Columbo automatiquement dans le cache ; ne pas recharger le catalogue ni télécharger. Si le service était réellement arrêté, confirmer une fois « Activer TEXT + AUDIO depuis le cache ». TEXT/AUDIO ON, AD-SYNC OFF pour le premier passage.
3. Dans Développeur, activer les commandes locales. Sur téléphone, ouvrir l’adresse HTTP complète affichée, saisir le code, garder la page ouverte. Vérifier les 120 min restantes.
4. Ouvrir Prime par le parcours passif. Lire depuis 00:28 à travers 00:32,988 puis 00:52,988. Le diagnostic doit passer de MEDIA compatible/calendrier à demande TEXT puis fenêtre attachée. Contrôler TEXT/AUDIO et Suspendre/Reprendre au téléphone, sans sortie de l’écran de film.
5. Revenir volontairement avant 00:32,988 après une interruption, observer le TEXT à sa fenêtre d’origine ; les MP3 déjà consommés ne doivent pas repartir du seul fait d’une récupération. Vérifier les trois fenêtres MP3 01:30,05:00,15:00 par la lecture ou les contrôles Prime manuels. SceneVibe n’envoie aucun transport.
6. AD-SYNC ON : attendre deux captures cohérentes après une interruption ; vérifier reprise et suspension manuelle conservée. Faire un aller-retour SceneVibe/Prime sans redemander de téléchargement. Vérifier la commande au téléphone en fin de séance et l’expiration à deux heures.

Diagnostic sur DENIS : `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`, puis `./scripts/Collect-SceneVibeDiagnostic.ps1` avec le numéro de série propre à la Sony. Démarrer la capture avant le scénario, l’arrêter après avec Ctrl+C. Aucune commande logcat -c, réinitialisation, injection de touche ou commande lecteur. Les tags incluent désormais SceneVibeRuntime.

## Références Android publiques

- https://developer.android.com/reference/android/provider/Settings#canDrawOverlays(android.content.Context)
- https://developer.android.com/reference/android/media/session/PlaybackState#getLastPositionUpdateTime()
- https://developer.android.com/reference/android/media/session/PlaybackState#getPosition()

STOP avant toute installation Sony, fusion, mutation Cloud, déploiement ou modification des données utilisateur.
