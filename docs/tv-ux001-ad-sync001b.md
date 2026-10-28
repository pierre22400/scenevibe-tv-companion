# TV-UX 001 + AD-SYNC 001-B — candidat v19

Le PASS physique Cloud → TV 002 reste acquis. Pierre a ensuite observé sur Sony
Android TV 12 la suspension v18, mais pas la reprise ni le recalage. Les correctifs
ci-dessous ne deviennent pas une nouvelle preuve physique par leur compilation.

## Commande d’ouverture : frontière produit encore ouverte

Une application ordinaire sans focus ne reçoit pas les touches globales de la
Sony. Le renderer éditorial garde FLAG_NOT_FOCUSABLE et FLAG_NOT_TOUCHABLE.
Aucun service d’accessibilité, remappage, accès système, MediaSession SceneVibe
prioritaire ou interception de touches Prime n’est ajouté.

La meilleure alternative retenue est la **notification SceneVibe** : son action
« Ouvrir le panneau » et son contentIntent envoient un PendingIntent immuable au
même OverlayService non exporté. Ils ne lancent pas MainActivity. L’extension TV
publique AndroidX Core 1.13.1 porte cette action sur Android 12 (l’extension du SDK
framework n’est publique que depuis API 35). Cette dépendance ne donne aucun droit
système ; sa permission de receiver dynamique est interne à l’application et
limitée à la variante v19. Les profils historiques gardent leurs permissions.

**Cela ne prouve pas qu’une touche de la télécommande Sony donne accès à cette
notification pendant Prime.** L’émulateur vérifie la capacité depuis une autre
Activity possédée par le test, sélectionnée au D-pad, sans détourner une touche
globale. Cette Activity remplace explicitement le point d’entrée du menu système
pour le banc ; elle n’est pas livrée dans l’APK signé. Aucun raccourci Sony n’est
inventé. Le [guide Sony des réglages rapides](https://helpguide.sony.net/tv/jga1/v1/en-142/01-03_12.html)
mentionne des notifications BRAVIA et des variations selon le modèle, sans garantir
l’exposition d’une action SceneVibe. Le bouton de prévisualisation dans Réglages ne satisfait pas à lui seul
le critère d’invocation depuis Prime.

Tant que cet accès système n’est pas démontré sur la TV, le critère principal est
**BLOCKED — TV OVERLAY INVOCATION**, même si les tests de la fenêtre sont verts.
L’alternative conditionnelle à essayer est le centre de notifications/les réglages
rapides de la Sony, si ce modèle y expose l’action SceneVibe. Aucun retour à
l’accueil n’est présenté comme une solution conforme.

Sources primaires : [focus des fenêtres Android](https://developer.android.com/reference/android/view/WindowManager.LayoutParams#FLAG_NOT_FOCUSABLE),
[extension TV AndroidX](https://developer.android.com/reference/androidx/core/app/NotificationCompat.TvExtender),
[extension framework](https://developer.android.com/reference/android/app/Notification.TvExtender),
[temps de mise à jour PlaybackState](https://developer.android.com/reference/android/media/session/PlaybackState#getLastPositionUpdateTime()).

## Deux fenêtres, un service et un état

SceneRenderer reste passif. CompanionControlPanel est une fenêtre temporaire
TYPE_APPLICATION_OVERLAY, focusable seulement ouverte, sur le côté droit. Ses
boutons à fort contraste, contours/inversion au focus et clavier numérique
permettent TEXT, AUDIO, suspension, reprise et saisie du temps au D-pad. Le port de
commandes lit et modifie TvFirst009TextServiceOwner ; il ne mémorise pas de second
état métier. Les Réglages relisent ces mêmes interrupteurs. Le type Channel réserve
l’extension INTERLUDES, sans moteur ni interrupteur actif fictif.

Back ferme d’abord la saisie, puis le panneau. Chaque × effectue la même fermeture
non destructive à son niveau. Aucune de ces actions ne change les interrupteurs,
le cache, la protection AD-SYNC, le calendrier ni la session du service. Les modales
de l’application ont également un × qui annule, avec leurs callbacks de fermeture
et le Back Android natif conservés. Perte de permission, de focus ou échec d’ajout
retirent la fenêtre ; l’entrée est libérée avant tout retrait, y compris en erreur.

## Reprise et recalage corrigés

Le verrou certain du code v18 était la limite de 2,5 secondes sur **l’âge de
l’ancre PlaybackState**. Ce temps décrit la dernière modification d’état publiée,
pas la date à laquelle SceneVibe lit la session : il peut vieillir pendant une
lecture normale. Sans log de cette tentative v18, on ne lui attribue pas avec
certitude toute la panne physique observée.

Le correctif exige toujours deux captures compatibles distinctes et récentes,
contrôle progression, identité, session, durée et validité, mais n’exige plus que
le lecteur fabrique une nouvelle ancre à chaque capture. Reprendre provoque une
lecture passive immédiate puis attend la confirmation suivante. Le panneau dit
« confirmation MEDIA attendue » et n’annonce pas une synchronisation acquise.
La sonde du seul profil v19 préfère une session compatible avec la track admise,
même lorsqu’une autre session non pertinente est PLAYING.

Recaler ferme d’abord les effets SceneVibe, relit la session actuelle puis vérifie
la capture : session absente, identité incompatible, observation invalide ou trop
ancienne, écart supérieur à une heure, format ou Companion inactif sont distingués.
L’utilisateur peut corriger et réessayer sans nouvelle activation. Une ancienne
capture n’est jamais recyclée comme preuve du film actif. La correction reste
locale et ne change ni Cloud ni timestamps éditoriaux.

La suspension conserve les identités consommées et tous les événements futurs.
Le retour ignore les fenêtres expirées et ne rejoue pas un MP3 interrompu. Seule
la case explicite de SEEK autorise la relecture historique. Suspendre depuis le
panneau active explicitement la protection si nécessaire ; les profils v17 et
antérieurs gardent leur chemin de référence. La détection des publicités n’est
pas démontrée et aucun contrôle du lecteur tiers n’est ajouté.

## Portée des essais

Les tests purs reproduisent l’ancre ancienne avec captures actuelles, la capture
périmée, l’absence, les refus précis et la récupération. Le runtime TEXT/AUDIO
partagé conserve ses tests de suspension, consommation et événements futurs.
Robolectric teste les vrais widgets, Back/×, erreurs de fenêtre et perte de droit.
Le banc natif TV exige un profil paysage tv_1080p et UI_MODE_TYPE_TELEVISION,
puis utilise la vraie WindowManager, l’OverlayService réel pour
l’invocation et sa conservation, une Activity de premier plan de test et un port
d’actions explicite pour les commandes actives. Ce dernier n’est pas une admission
Cloud fabriquée ; le test natif ne prétend pas faire jouer une track Cloud admise
via le panneau. Un essai natif lit aussi la vraie sonde publique sur une session SceneVibe : perte
explicitement simulée pendant une navigation, refus sans session, retour sans
reprise automatique, puis reprise et recalage avec le panneau ouvert. Cela ne
prouve pas le comportement MEDIA de Prime. Les quatre essais natifs AD-SYNC du
MP3/session propre sont gardés.
Les campagnes fournisseurs et la qualification Sony 002 ne sont pas relancées.

## Sony — essai ultérieur, après nouveau GO

1. Installer le v19 signé M5, conserver le cache, puis activer la track depuis
   l’application comme auparavant. Ouvrir Prime normalement.
2. Sans quitter Prime, vérifier si les notifications/réglages rapides Sony offrent
   l’action SceneVibe. Si elle est inaccessible, arrêter le test d’invocation :
   le critère reste BLOCKED, sans remappage ni nouvelle permission.
3. Si accessible, sélectionner l’action, vérifier Prime toujours visible et les
   états TEXT/AUDIO. Tester chaque interrupteur puis Back et × ; vérifier que la
   télécommande commande de nouveau Prime après fermeture.
4. Pendant une interruption, ouvrir/suspendre/fermer. Au retour du film, rouvrir et
   Reprendre ; si requis, Recaler avec le pavé numérique, sans cocher SEEK pour un
   simple retour de publicité. Vérifier un futur commentaire et la non-relecture.
5. Répéter ouverture/fermeture, conserver cache/service, et ne relever que les
   diagnostics SceneVibe. Aucune capture du flux Prime, détection publicitaire ou
   manipulation de ses transports. Aucun de ces essais n’est exécuté par Work.
