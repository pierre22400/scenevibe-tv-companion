# SceneVibe — référence expérimentale Sony v19

**Statut : CLOSED / PASS PHYSIQUE — 5 novembre 2026.**

Le propriétaire a qualifié sur une **Sony BRAVIA VH1 (Android TV 12)** le package expérimental `com.scenevibe.tvcompanionpoc.netflixoverlay001`, **versionCode 19**, sur **Netflix, Prime Video, Disney+ et CANAL+**.

Parcours validé à la télécommande, **sans ADB pendant le visionnage** : accueil TV → préparer SceneVibe → READY → lecteur tiers → MUTE prolongé → **Recaler et reprendre** → OK → TEXT et AUDIO → RETOUR au lecteur. La synchronisation est **manuelle** ; aucune position automatique du lecteur tiers n'est connue. La piste de démonstration joue TEXT à 00:10 et AUDIO à 00:13 après recalage à 00:08. « Recaler sans reprise » et l'annulation par RETOUR ont également passé leurs essais sur Netflix.

## Identité et non-régression

- APK v19 signé, SHA-256 : `bbb290750796ae3e860f8419af548836cdf9115487d15e8c0ce0a9889e9c8a6f`.
- Certificat expérimental SHA-256 : `e49c665500106afc7fc3638c71a30f5e9352dd1f05bbba871fbaf6f2570df02a`.
- Qualification logicielle Work : **192/192 tests PASS** ; signature v2/v3 selon les preuves livrées.
- Companion principal `com.scenevibe.tvcompanionpoc` **v28 inchangé**.
- Archive source de référence : `SCENEVIBE-MULTIPLATFORM-UX-001B-LIVRAISON.zip`, SHA-256 : `93924b4b757848bae8a3931ed8c66ba31288a30977c01946b429fe5f6ae1d671`.
- Patch final inclus dans l'archive : `DIFF-FINAL.patch`, SHA-256 : `9bbdc54fd71df4e62c89a68256feb5b97c5b061bf8dbcc4ba2b8e821a04a668d`.

## Suite pour Kiro

**Figer v19 : aucun changement fonctionnel sur le candidat qualifié.** Les sources expérimentales ont été assemblées dans Work à partir du commit `7d984261048156dd91a79e148fd2f419d8f5cafb` puis des correctifs historiques décrits dans l'archive ci-dessus. **Cette branche documente la qualification ; elle ne prétend pas contenir les sources v19 reconstruites.** Pour intégrer les sources dans GitHub, Kiro devra repartir de l'archive contrôlée et créer une branche de développement dédiée, sans écraser `main` ni fusionner les autres prototypes.

Ne pas modifier le raccourci MUTE, le service d'accessibilité ou le moteur TEXT/AUDIO sans nécessité démontrée. Pas d'installation Sony, déploiement, commit sur les branches existantes ou fusion implicite.
