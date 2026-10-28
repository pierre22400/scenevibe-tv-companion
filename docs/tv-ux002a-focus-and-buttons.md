# TV-UX 002-A — focus Sony et états des boutons

## Constat et diagnostic borné

Pierre rapporte sur Sony BRAVIA Android TV 12 avec le v20 : badge et panneau visibles, TEXT/AUDIO opérationnels, mais retour de Prime à la fiche de l'épisode lors du passage interactif. C'est une observation physique du propriétaire, pas un test automatique.

La pièce `Texte collé(20261027-195438).txt` contient la commande PowerShell et les 100 dernières lignes filtrées de logcat (101 lignes au total). SHA-256 des octets reçus : `6a0aee1f5d85e98181f8e22a2b26db4b18ef95bafe0db05314ed5740f47df2a7`. Elle ne contient aucun événement Prime, aucune ligne d'exécution SceneVibe TEXT/AD-SYNC, aucun événement de touche ni transition de focus du panneau. Le nom des tags dans la commande n'est pas un événement. Les START du launcher/SceneVibe/BootHelper ne prouvent pas une transition Prime au moment du défaut. Ce prélèvement ne permet donc pas d'identifier la cause physique exacte.

Le code v20, lui, établit un mécanisme précis : `showDeveloperWhenMediaReady()` appelle `activatePrepared()` ; celui-ci retire `FLAG_NOT_FOCUSABLE` et `FLAG_NOT_TOUCHABLE`, agrandit la fenêtre, puis demande le focus d'un bouton. Cette transition ne lance pas d'Activity et ne contient aucune commande PLAY/PAUSE/SEEK ou injection de Back. Elle retire cependant le focus de fenêtre au lecteur : hypothèse de perturbation prioritaire, sans attribuer une réaction interne non observée à Prime. Une transition au milieu d'une pression de télécommande et un événement extérieur restent des hypothèses non départagées par ce journal.

Android distingue le focus de fenêtre du cycle de vie Activity. `FLAG_NOT_TOUCH_MODAL` concerne les pointeurs, pas la conservation du D-pad par le lecteur. `FLAG_NOT_FOCUSABLE` conserve les touches à la fenêtre située derrière, mais interdit alors leur réception par le panneau. Il n'existe ici aucune API publique qui garantisse simultanément les deux focus clavier. Références : [WindowManager.LayoutParams](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_FOCUSABLE), [Activity.onWindowFocusChanged](https://developer.android.com/reference/android/app/Activity#onWindowFocusChanged(boolean)). Aucun contournement par accessibilité, touches globales, injection ou modification de Prime n'est introduit.

## Correctif et interaction effectivement proposée

- **Chemin normal sans prise de focus automatique** : mode Développeur → préparer le badge → Passer à Prime Video. La disponibilité MEDIA peut agrandir la même fenêtre en **affichage passif**, mais ses deux drapeaux no-input restent présents. Aucun `requestFocus()` n'est exécuté sur ce chemin. Les commandes visibles sont marquées indisponibles dans cet aperçu ; le D-pad et Back restent au lecteur. Leur présence n'est pas présentée comme une interaction distante fonctionnelle.
- **Commandes réelles dans l'onglet** : TEXT/AUDIO/AD-SYNC, suspension, reprise et recalage gardent l'unique propriétaire métier. La fermeture du panneau passif s'effectue depuis l'application par « Fermer le panneau overlay ». Revenir dans SceneVibe gare sa fenêtre ; cela peut naturellement modifier le cycle de vie du lecteur tiers.
- **Essai interactif séparé** : « Tester le focus overlay (peut interrompre Prime) » autorise une seule transition focusable pour ce lancement. Ce choix n'est ni une préférence durable, ni hérité par le lancement normal suivant. Ce mode expérimental peut encore provoquer une interruption chez un lecteur sensible au focus. Il ne constitue pas la correction prouvée du comportement Prime.
- Pendant l'interaction, les deux moitiés de Back sont consommées ; premier Back/× ferme la saisie puis second ferme le panneau. Une relâche de Back/Entrée sans pression reçue dans cette fenêtre est consommée sans fermer/clique automatique. Les touches de transport reçues par cette fenêtre sont consommées pour éviter le fallback média. Aucune commande n'est renvoyée au lecteur. Après fermeture, la télécommande retrouve la fenêtre derrière.

Le même OverlayService, cache privé, calendrier et MP3 restent en place. La fermeture ne désactive ni le service ni les interrupteurs. Les garde-fous AD-SYNC de reprise/recalage, les événements futurs et la non-relecture automatique des MP3 interrompus ne sont pas modifiés. Le PASS physique CLOUD → TV 002 reste une preuve antérieure distincte.

## Représentation commune et accessible

`DeveloperButton` fournit le même rendu aux deux surfaces :

| État | Représentation | Sémantique |
|---|---|---|
| Interrupteur ON disponible | Fond clair, bordure rouge intérieure, texte ON | Switch checkable/checked, état accessible ON |
| Interrupteur OFF disponible | Fond blanc, bordure neutre, texte OFF | Switch checkable/non checked, état accessible OFF |
| Focus D-pad | Anneau noir extérieur additionnel | Ne modifie jamais ON/OFF ; reste distinct du rouge |
| Indisponible | Fond gris, bordure neutre, mention indisponible | Disabled ; valeur demandée conservée, non activable au D-pad |
| Suspendre/Reprendre/Recaler/Fermer | Boutons d'action avec anneau de focus | Non checkable, aucun faux ON/OFF |

L'état n'est donc jamais donné par la couleur seule. Le mode Développeur utilise aussi ce composant ; INTERLUDES n'est pas développé.

## Qualification et limites

Le banc natif utilise Android TV API 31, profil paysage `tv_1080p`, la vraie MainActivity, le vrai service et WindowManager, devant une Activity possédée par le banc. Un modèle de lecture volontairement sensible au focus permet de comparer les deux politiques : il est une caractérisation d'hypothèse, pas une reproduction certifiée de Prime. La transition MEDIA sous admission Cloud n'est pas fabriquée dans ce banc : les conditions de disponibilité sont testées séparément ; le passage de fenêtre est exercé avec la route UI sans admission. Le calendrier partagé et le décodeur MP3 gardent leurs tests de non-régression.

Les tests vérifient les drapeaux, le focus réel, les événements reçus, la fenêtre conservée, le retour des touches, les boutons rendus et accessibles, les relâches orphelines, les commandes de transport et Back/×. Un test de caractérisation attend qu'un modèle sensible quitte sa lecture lorsque l'essai explicite prend le focus, tout en comptant zéro Back/transport/pause Activity : c'est une **limite démontrée du modèle**, pas un PASS de compatibilité de cet essai. Les résultats exacts et les artefacts sont référencés dans la PR #31 après exécution.

Les nouvelles traces `SceneVibeControls` contiennent uniquement politique de fenêtre, gain/perte de focus, cycle de vie SceneVibe et codes de touches reçues/consommées. Elles ne capturent ni contenu Prime, ni arbre d'interface tiers, ni identifiant de compte/session.

**Verdict produit : PARTIAL tant qu'une interaction D-pad au-dessus de Prime sans interruption n'est pas physiquement démontrée.** Le candidat v21 est destiné à qualifier la politique passive et les nouveaux boutons ; il ne doit pas être présenté comme une résolution déjà prouvée de l'interaction continue. Ni un drapeau tactile ni un délai ne permettraient de promettre cette résolution. Aucun nouveau PASS Sony n'est revendiqué.

## Candidat et courte procédure Sony après GO installation

Candidat `scenevibe-tv-ux002a-stable.apk`, package `com.scenevibe.tvcompanionpoc`, versionCode 21, versionName `0.8.2-tv-release-hardening-tvux002a-focus-safe`, origine de staging existante, signature M5 appliquée en CI. Empreinte des octets et vérification v2/v3 : PR #31 et artefact signé.

1. Après autorisation seulement, installer le v21 M5 et activer le cache Columbo existant (16 TEXT + 3 AUDIO). Lancer une capture continue **avant** les actions, avec `SceneVibeControls`, `SceneVibeText009`, `SceneVibeAdSync`, ActivityTaskManager et WindowManager ; ne pas limiter ensuite aux 100 dernières lignes.
2. Développeur ON → préparer → **Passer à Prime Video** (normal) → lancer le film. Vérifier badge/panneau passif, film conservé et touches au lecteur. **Back est alors celui de Prime** ; ne pas le tester comme fermeture SceneVibe. Fermer l'aperçu depuis l'onglet SceneVibe.
3. Si l'essai interactif est souhaité, le sélectionner explicitement dans l'onglet. S'il renvoie à la fiche, arrêter cet essai et conserver les traces : compatibilité interactive toujours FAIL. Ne pas envoyer PLAY/PAUSE/SEEK pour compenser.
4. Seulement si le lecteur conserve son écran : vérifier ON/OFF rouge/neutre, anneau noir sans bascule, suspension/reprise et recalage ; puis Back/× imbriqués, service/interrupteurs conservés et touches rendues au lecteur. Ne pas confondre ce test futur avec les résultats du banc.

STOP avant installation Sony, fusion, déploiement et mutation Cloud. PR #31 maintenue en brouillon.

> Suite v22 : [TV-UX 002-B / AD-SYNC 001-C](tv-ux002b-ad-sync001c.md). Les constats v21 restent historiques ; l’accès D-pad depuis Prime n’était pas qualifié.
