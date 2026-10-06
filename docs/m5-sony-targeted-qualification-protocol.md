# M5 — Qualification ciblée Sony

PHYSICAL SONY PENDING. Exécuter avec Pierre, une action puis son observation à la fois.
APK : `scenevibe-tv-companion-cloud-qualification-stable.apk`.
SHA-256 : `43a9bb12ecc7712afdcf7ed5486d4eff1c1be1767308c8832d919ac0dca817d2`.
HEAD et identité finale de l'artefact : manifeste Phase D de la PR #15, cité dans le rapport.

1. Avant upgrade, ouvrir Diagnostics et relever version, Cloud deviceId, installationId,
   permissions overlay/MediaSession, autostart, installed/acknowledged revision, codec/handler,
   installation read failure et last startup restore. Conserver ces valeurs et le package existant.
2. Vérifier le SHA-256 de l'APK et `apksigner verify --print-certs` : signer attendu
   `f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.
   Installer uniquement par upgrade : `adb install -r scenevibe-tv-companion-cloud-qualification-stable.apk`.
   Ne pas désinstaller, pm clear, reset Cloud ou refaire le pairing.
3. Ouvrir Diagnostics après upgrade : mêmes identités, permissions, autostart, révisions et
   codec/handler; aucun CACHE_FAILED/CORRUPT nouveau; label
   `Temporal engine: scenevibe.media-calendar.v1`. L'installation existante se restaure ARMED
   sans Send et reste invisible tant qu'aucun commentaire n'est dû.
4. Dans Prime, rejouer le Video existant aux positions connues. Vérifier DUE puis disparition,
   une seule scène, et le texte exact si le scénario installé contient déjà
   `COMPOSÉ=é | DÉCOMPOSÉ=é | LIGATURE=œ | APOSTROPHE=’ | EMOJI=🙂`.
   Ne pas inventer un nouveau scénario Unicode ou une route legacy forcée.
5. Au commentaire dû, pause puis reprise : pas de consommation visuelle prématurée pendant
   la pause, reprise cohérente. Faire un saut avant au-delà du commentaire puis un retour
   arrière avant son début : pas de commentaire sauté affiché; replay au passage dû suivant.
6. Quitter Prime pendant la scène : masquage immédiat. Revenir : pas de scène stale ressuscitée;
   seul un nouveau passage dû selon le scénario peut montrer. Ne pas isoler le réseau si cela
   empêche Prime de lire le Video.
7. Par le flux Cloud normalement supporté, envoyer une nouvelle révision valide : ancien
   visuel masqué, nouvelle installed revision puis ACK correspondant, aucune double scène.
   Ne pas fabriquer de mécanisme de redelivery ni tester un chemin legacy absent du produit.
8. Faire un reboot complet Sony, sans Send. Ouvrir d'abord Diagnostics : identités et révisions
   conservées, durable lisible, read failure NONE, startup restore ARMED, même temporal engine.
   Ouvrir ensuite Prime : DUE, Unicode existant, expiry, puis backward/replay cohérents.
9. Pour un cycle réussi, conserver les logs SceneVibe et rechercher CORRUPT, CACHE_FAILED,
   GENERIC_INVALID, FATAL EXCEPTION, `Process: com.scenevibe` et une double ownership. Distinguer
   les erreurs système Sony étrangères à SceneVibe. Rapporter les observations et toute action
   non exécutée; aucun Sony PASS ne peut être déduit des tests automatiques.

STOP avant cette phase physique dans le WORK logiciel. Ne pas merge la PR.
