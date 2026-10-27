# TV-UX 002 — onglet Développeur et fenêtre de service

Cette décision remplace le raccourci de notification comme condition du parcours TV-UX 001. Le PASS physique Cloud → Android TV 002 reste acquis. Les corrections v19 de reprise/recalage sont conservées ; leur validation Sony n'est pas acquise.

## Architecture et parcours

`MainActivity` reste une application TV normale : accueil, tracks, association, téléchargement/cache et réglages. Son nouvel onglet **Développeur** embarque les widgets de `CompanionControlPanel`. Une autre instance du même composant fournit uniquement les commandes dans la fenêtre du **même OverlayService**. Les deux utilisent `TvFirst009TextServiceOwner.panelCommands()` : aucune seconde préférence TEXT/AUDIO/AD-SYNC, aucun second calendrier ni lecteur vidéo.

1. Activer explicitement une track depuis le cache comme auparavant, puis ouvrir Développeur.
2. Activer **Mode Développeur**. Cela ne prépare aucun panneau et n'accorde aucun permis de lecture.
3. Choisir **Afficher les commandes en overlay**. Le service attache un petit badge `TYPE_APPLICATION_OVERLAY`, non focusable et non touchable. L'application conserve la télécommande.
4. Choisir **Passer à Prime Video**. C'est exclusivement le lancement public Leanback/launcher du package installé, pas une commande de lecture ou un lien interne Prime. Le bouton est indisponible si Prime n'est pas installé.
5. Le `onStop` de l'Activity consomme une intention de transfert à usage unique, valable 15 secondes. Aucun compte à rebours ne donne le focus. Avec une session multimédia admise, le badge reste passif pendant la navigation Prime : il attend une nouvelle capture MEDIA compatible **PLAYING**, postérieure au transfert et fraîche de 2,5 s au plus. Le contrôle emploie la sonde existante, même si AD-SYNC suspend les commentaires. Sans track active, le panneau interactif peut s'ouvrir dès ce transfert, avec commandes de restitution désactivées.
6. La même fenêtre et le même service deviennent interactifs. Ils survivent à la destruction de l'Activity source. Le D-pad pilote les commandes et le pavé numérique local.
7. Back ou × ferme d'abord la saisie, puis le panneau. Le mode reste activé ; service, cache, interrupteurs et calendrier restent inchangés. Le lecteur retrouve ses touches.

Revenir dans SceneVibe gare le panneau en état passif. Une perte de focus le gare également ; une perte d'autorisation de superposition le retire. La désactivation du mode ferme sa fenêtre, sans arrêter les commentaires. Le mode et l'intention d'ouverture ne sont pas restaurés après mort du processus. Aucun démarrage automatique de restitution n'est ajouté.

## Commandes partagées et garanties

Les deux surfaces exposent TEXT, AUDIO, AD-SYNC, Suspendre, Reprendre, Recaler, session/synchronisation et diagnostics numériques : position MEDIA brute, position suivie, temps local, âge de capture et âge de l'ancre. Aucun identifiant, titre ou jeton MEDIA n'est affiché dans ces diagnostics. INTERLUDES reste réservé, sans fonctionnalité nouvelle.

L'identité compatible, les deux captures réellement distinctes pour la reprise, les observations récentes, les refus explicites et la borne de recalage restent inchangés. Suspendre ferme les effets, pas le calendrier. Un MP3 interrompu n'est pas réarmé automatiquement ; la relecture exige le déplacement confirmé déjà prévu. SceneRenderer garde ses overlays éditoriaux passifs. Aucun contrôle du transport Prime, signal publicitaire supposé, permission sensible ou service d'accessibilité n'est ajouté.

## Qualification et portée

La CI `tv-ux002.yml` qualifie les transitions pures, les widgets Robolectric, le build/lint v20 et les builds conservés. Son banc natif utilise un émulateur Android TV API 31 `tv_1080p` : vraie MainActivity, vrai OverlayService, vraie fenêtre et Activity de visionnage **possédée par le banc**. Seule la destination du lancement est substituée. Les tests de formulaire actif déclarent un port enregistreur ; ils ne fabriquent pas une admission Cloud. Les tests AD-SYNC natifs conservent leurs propres sessions MEDIA et le décodage MP3 existant. Les workflows historiques et les contrôles Cloud/cache restent exécutés dans leur périmètre.

L'APK expérimental v20 garde `com.scenevibe.tvcompanionpoc`, versionName `0.8.2-tv-release-hardening-tvux002-developer-overlay`, l'origine de staging existante et le certificat M5. Les classes Activity de banc sont ajoutées seulement par une propriété de qualification, jamais au candidat signé. Résultats et empreinte du candidat : voir la PR #31 et les artefacts de sa dernière CI ; aucun résultat Sony nouveau n'est revendiqué.

## Limites réelles

- Le chemin principal exige le bouton de lancement dans Développeur. HOME puis ouverture manuelle d'un lecteur laisse volontairement le badge passif ; aucune touche globale de réouverture n'est promise. Après fermeture complète, revenir à SceneVibe pour préparer à nouveau.
- Avec une track active, MEDIA absent/incompatible/figé laisse le badge passif. Après cinq minutes sans lecture compatible, le transfert expire vers une préparation passive. Il faut revenir à l'application et recommencer le lancement explicite.
- Une capture compatible PLAYING n'est ni une preuve d'image du film au premier plan, ni une détection publicitaire. Les réactions de Prime à la prise de focus doivent être observées sur Sony. Les publicités et les limites d'horloge restent celles du prototype AD-SYNC ; aucune publicité SceneVibe n'est insérée.
- Le nouveau transfert sous track admise est qualifié par ses conditions logicielles ; le banc de navigation n'appelle pas Neon/R2 et ne prétend pas être une nouvelle restitution Cloud → Prime. Le PASS physique v17 reste une preuve distincte. La reprise et le recalage v19/v20 n'ont pas encore de PASS physique Sony.

## Procédure Sony, après autorisation d'installation uniquement

1. Installer le candidat M5 v20 autorisé ; garder association/cache, vérifier 16 TEXT + 3 AUDIO et activer la track.
2. Développeur → mode ON → Afficher les commandes en overlay → Passer à Prime Video. Naviguer au D-pad jusqu'au film, lancer la lecture ; vérifier badge passif puis panneau interactif, sans notification.
3. Devant Prime, suspendre : TEXT/AUDIO s'arrêtent. Reprendre : attendre les deux captures compatibles ; vérifier un événement futur sans relecture du MP3 interrompu. Recaler avec le pavé numérique, vérifier l'offset et les refus éventuels.
4. Premier Back/× ferme la saisie ; second ferme le panneau. Vérifier touches rendues au lecteur, service actif et interrupteurs conservés. Revenir à SceneVibe pour rouvrir ; relever tout refus ou réaction du lecteur sans modifier Prime.

STOP avant installation Sony, fusion et déploiement. PR #31 conservée en brouillon.
