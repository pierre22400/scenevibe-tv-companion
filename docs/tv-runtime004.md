# SCENEVIBE — TV-RUNTIME 004 — v24

Date : 28 octobre 2026. Même Companion, branche `work/scenevibe-ad-sync-001`, PR #31 en brouillon. Référence v23 : `643efdd3d5491ac0bc9194a2d0083e90de45296e`.

## Acquis et portée des preuves

Pierre déclare sur sa Sony v23 : cache Columbo, TEXT, relecture TEXT, commandes téléphone, affichage/masquage du moniteur et interrupteurs opérationnels. Il constate l'absence de relecture AUDIO après recul, une disparition de TEXT lors des bascules AD-SYNC et AUDIO3 non entendu alors qu'une publicité Prime est affichée. Ce sont des observations physiques du propriétaire. Aucun nouveau journal Sony v23 couvrant ces trois incidents n'est fourni avec TV-RUNTIME 004 ; leurs causes physiques exactes ne sont pas reconstituées.

Le cahier des charges source est `Texte collé(20261028-102827).txt`. Les preuves Sony précédentes restent distinctes et ne deviennent pas des traces des nouveaux incidents. La qualification v24 emploie les 19 événements existants, sans génération d'AUDIO ni modification de leur calendrier. Les essais Android utilisent MainActivity, OverlayService, le cache et MediaPlayer réels, avec une MediaSession publique appartenant au banc et des réponses fournisseur de qualification. Ils ne reproduisent ni Prime, ni une publicité Prime, ni un téléviseur Sony.

**Correction de la table du cahier des charges :** le paquet réel contient 11 TEXT jusqu'à 15:05, puis 5 autres jusqu'à 28:26. La lecture jusqu'à 15:05 doit donc donner 11 TEXT + 3 AUDIO, et la timeline complète 16 TEXT + 3 AUDIO. Les timestamps éditoriaux ne sont pas déplacés pour obtenir artificiellement 16 TEXT avant 15:05.

## Causes logicielles reproduites

### AUDIO : protection trop générale après interruption

La v23 conserve volontairement les MP3 consommés après perte MEDIA, pour empêcher les redémarrages intempestifs. Sa récupération réarme uniquement les TEXT futurs. Un recul légitime, précédé d'une interruption, ne réarme donc pas les MP3 ; une reprise normale et un revisionnage partagent la même protection. Supprimer toute consommation à chaque retour MEDIA recréerait des doublons.

La v24 ajoute une preuve de recul au calendrier existant. Elle n'introduit ni deuxième lecteur ni deuxième horloge. L'identité d'épisode, l'admission, les permissions et la fraîcheur MEDIA sont toujours vérifiées en amont.

### AD-SYNC : la politique de mode ne doit pas détruire un effet valide

Dans la v23, `setEnabled()` efface les observations de continuité même si MEDIA reste valide. Lors du passage OFF vers ON, la reconstitution de cette preuve peut fermer les effets. `onInterruption()` retire la fenêtre, mais l'événement TEXT reste consommé ; le retour dans sa fenêtre ne le redessine pas. Ce chemin explique une disparition reproductible du TEXT courant, sans démontrer que tous les événements futurs étaient perdus sur Sony.

La v24 conserve les preuves compatibles et l'intention de suspension lors d'une bascule. La bascule ne vide ni le calendrier ni ses consommations. Une véritable perte d'identité/horloge ferme toujours les effets ; au retour fiable, le TEXT déjà consommé peut être restauré uniquement à l'intérieur de sa fenêtre éditoriale originale. Cette restauration a son propre compteur et ne produit pas un deuxième DUE. Une fenêtre expirée reste expirée.

## Politique de relecture

- Lecture normale : un MP3 par passage éligible dans sa fenêtre.
- Pause/reprise : politique native FREEZE_AND_RESUME inchangée pour un MP3 en cours ; aucun MP3 terminé ne redémarre.
- Session absente puis retrouvée sans recul : aucun MP3 consommé n'est réarmé.
- Même session, recul de plus de 5 secondes : au moins trois captures, sur au moins 750 ms, avec au moins 500 ms de progression MEDIA, sans oscillation négative et avec progression cohérente avec la vitesse publiée. Les captures ne fabriquent pas de temps de film ; seules les positions MEDIA sélectionnées avancent le calendrier.
- Après confirmation : réarmement des TEXT/AUDIO futurs à partir du premier temps inférieur réellement observé, ou d'un AUDIO encore dans sa fenêtre ; aucun rattrapage des fenêtres expirées.
- Nouvelle session retrouvée plus bas : preuve de SEEK insuffisante, aucun réarmement AUDIO automatique. Les options téléphone offrent une confirmation facultative de relecture au temps MEDIA actuel, sans déplacement du lecteur ni nouvelle admission. Elle n'est pas requise pour les reculs habituels confirmés dans la même session.
- Bascule de politique/recalage : nouvelle base de comparaison, jamais preuve implicite de SEEK. L'autorité et les fichiers du cache restent identiques.

Les seuils bornent la preuve logicielle ; les positions incohérentes publiées par une plateforme peuvent empêcher sa confirmation. Le banc vérifie ce refus ainsi que le chemin normal. Il ne prouve pas les événements numériques effectivement publiés par Prime sur Sony.

## AUDIO3 à 15:00 : faits et hypothèses

Seul fait physique nouveau : Pierre n'a pas entendu AUDIO3 pendant une publicité affichée. Aucun signal publicitaire fiable n'est disponible. Continuité MEDIA ne signifie pas absence de publicité ; absence MEDIA ne signifie pas publicité détectée. Le temps d'une publicité ne devient pas automatiquement le temps éditorial Columbo.

| Hypothèse | Ce que permet le diagnostic v24 |
|---|---|
| A. Traversée sans lecture | `traversed` et `consumedWithoutStart` séparés des compteurs DUE et STARTED |
| B. Identité/horloge incompatibles | Motif MEDIA de fermeture, admission/cache conservés |
| C. Horloge publique différente du film | Valeurs publiques et positions sélectionnées ; aucune interprétation interne Prime inventée |
| D. Fenêtre de trois secondes dépassée | `AUDIO_SKIPPED` avec motif, aucun rattrapage tardif |
| E. Ressource/démarrage refusé | PREPARE_REQUESTED, PREPARED, START_REFUSED ou ERROR distincts de STARTED |
| F. Événement déjà consommé | Compteurs par ordinal et réarmement confirmé, sans identifiant privé |

Le banc rend l'absence MEDIA autour de 15:00 reproductible : retour après la fenêtre sans lecture, diagnostic explicite, puis recul réel à 14:50 et nouveau passage autorisé. Ce scénario n'est pas présenté comme une publicité simulée détectable. Sans journal de l'incident physique, aucune des six hypothèses n'est attribuée définitivement à Sony.

## Téléphone et observabilité

Les trois interrupteurs restent indépendants et affichent leur préférence réelle. Une seule commande « Commentaires : actifs / en pause » remplace les deux boutons Suspendre/Reprendre. Elle représente la suspension globale véritable, utile pour une interruption volontaire sans perdre les préférences TEXT/AUDIO ; MEDIA en attente, film en pause, canaux OFF, service arrêté et refus ont des libellés distincts. La réponse du service fait foi, sans changement optimiste ni écrasement par une réponse de polling antérieure.

Les options avancées conservent le recalage et proposent la relecture facultative d'une session ambiguë. Afficher/masquer le moniteur ne modifie pas le focus Prime. Aucun transport PLAY/PAUSE/BACK/SEEK n'est envoyé à une autre application. Le problème d'accès à la télécommande Sony seule reste hors de cette correction ; le téléphone est l'alternative expérimentale acceptée.

L'échéance absolue reste 120 minutes dès activation TV, sans renouvellement par appairage, commandes ou polling. Les protections HTTP existantes sont conservées. HTTP LAN reste en clair sur un réseau privé de confiance ; le bearer n'est pas persisté dans le navigateur. L'endurance physique de deux heures reste à observer.

Chaque AUDIO possède des compteurs séparés : futur, DUE, SKIPPED, REARMED, préparation demandée/réussie, STARTED natif, STOPPED, COMPLETED, refus et erreur. Les acquittements viennent des transitions réelles de MediaPlayer, et non d'un simple appel de renderer ni d'un sondage de son état. Une garde prétendant avoir exécuté START sans l'avoir fait est un refus. Les reprises du même lecteur ne comptent pas comme un nouveau STARTED. Les traces utilisent l'ordinal et le temps programmé, sans jetons, URL privées, identifiants d'événements ni contenu éditorial.

Les motifs de fermeture accompagnent le retrait d'une vraie fenêtre TEXT. Les transitions AD-SYNC et les compteurs du calendrier sont tracés ; un changement de position seul ne provoque pas de log toutes les 250 ms.

## Qualification logicielle

Les résultats effectifs, références des runs, empreintes et signatures sont fournis dans le rapport de livraison autonome. Les nouvelles suites vérifient :

- Le paquet inchangé : 11 TEXT/3 AUDIO avant 15:05, 19 événements sur la timeline complète, répétition d'un épisode sans déplacement d'horaire.
- Trois MP3 relus une fois par passage confirmé, protections pause/session/oscillations/nouvelle session, avance rapide sans rafale.
- Bascules avant, pendant et entre TEXT, horloge invalide puis récupération, suspension manuelle, AUDIO actif ou terminé.
- Sur pile Android TV réelle de banc : service admis, commandes TCP, fenêtres OverlayService, MP3 natifs, absence autour de 15:00 et récupération à 14:50, acquittements de refus et fin naturelle. Zéro téléchargement supplémentaire lors des relectures.
- Page exacte dans Chromium et vrai serveur local : commande globale unique, état confirmé, options, canaux et échéance 120 minutes via horloge de qualification injectée. Ce test ne simule pas une endurance physique de deux heures.
- Profils historiques retenus, intégrité/cache/redémarrage, sécurité de l'admission et absence de commandes tierces.

## Procédure Sony courte, après autorisation d'installation

1. Mettre à jour vers v24 signé M5 **sans désinstaller ni effacer les données**. Retrouver Columbo dans le cache ; aucun catalogue, appairage Cloud ou téléchargement supplémentaire n'est nécessaire si l'autorité est toujours valide. Si le processus/service a réellement cessé, activer une fois depuis le cache selon le parcours existant.
2. Associer le téléphone, garder la page ouverte ; vérifier les 120 min et les trois préférences. Ouvrir Prime par le parcours habituel passif. SceneVibe ne doit pas changer l'écran de film.
3. AUDIO1 : écouter à 01:30, laisser finir, revenir **avec les contrôles Prime habituels** à 01:20, reprendre ; attendre une seule nouvelle écoute à 01:30. Refaire à 04:50/05:00 et 14:50/15:00. Une pause/reprise après fin et une récupération MEDIA sans recul ne doivent rien rejouer.
4. Premier TEXT : 00:32,988–00:40,988 ; basculer AD-SYNC dans les deux sens au milieu de cette fenêtre. Il doit rester visible ou revenir tant qu'elle n'est pas expirée. Vérifier aussi le TEXT suivant à 00:52,988, puis la pause globale/réactivation sans perte de préférence.
5. Si une publicité survient : noter l'heure, la position éditoriale connue et le contexte, sans appeler cela détection. Vérifier les traces DUE/préparation/STARTED/refus/skip. Un commentaire manqué ne part pas tardivement ; un retour ultérieur à 14:50 permet le passage AUDIO3. Si Prime recrée la session et que la relecture reste ambiguë, relever le motif puis essayer une fois l'option facultative de relecture.

Capture continue avant et après : `Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass`, puis `./scripts/Collect-SceneVibeDiagnostic.ps1` avec le numéro Sony réel ; arrêter avec Ctrl+C après le scénario. Aucune purge logcat, injection ou commande de transport. Ne pas considérer une fenêtre attachée ou STARTED logiciel comme une perception acoustique Sony.

STOP avant installation/intervention Sony, fusion, mutation Cloud/Neon/R2, déploiement Vercel et modification de secrets ou données utilisateur.
