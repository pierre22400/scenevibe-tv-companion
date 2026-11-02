# REAL TRACK TV 001 — candidat isolé

Base : PR #35 `90b55c1c9dcb6894a8285c6a1dbe830a584bba8c`.
Branche : `work/scenevibe-real-track-tv-001`, PR Draft empilée sur la branche #35.
Le Companion principal et `main` ne sont ni déplacés ni installés par cette mission.

L'option `-PscenevibeRealTrack001=true` active uniquement le build `tvux002` :
versionCode 29, suffixe `-real-track-e2e001`, même package et origine Cloud.
Sans option, les versions/profils historiques et l'admission du corpus 19 événements
restent inchangés. La nouvelle admission accepte la référence manuelle explicite
Columbo / Eaux troubles / Prime après vérification du cache, de la génération et
du consentement local. Aucun booléen du document n'accorde une admission MEDIA.

Le calendrier, les ports TEXT/AUDIO, SEEK, association chiffrée, cache, garde Prime,
commande de suspension manuelle et contrôle téléphone ne sont pas réécrits.
Le message d'activation indique les vrais comptes du cache manuel.

Le workflow dédié rejoue la suite JVM commune, les vues, la compilation/lint,
les 51 cas natifs historiques et deux redémarrages sans modifier leurs assertions,
puis deux cas Android supplémentaires pour les six événements manuels. Les trois
MP3 de test sont synthétiques et distincts ; aucune voix de Pierre n'est incluse.
Le réseau Android est une fixture déclarée ; le PostgreSQL/HTTP réel est testé
dans la PR Cloud. Aucun succès émulé ne vaut preuve Prime/Sony.

Si la clé M5 existante est disponible dans le workflow, le candidat est signé
et le certificat attendu est contrôlé. Sinon l'APK reste unsigned et l'installation
est bloquée. Aucune nouvelle clé ni réinstallation propre n'est autorisée.

Certificat M5 attendu :
`f908bf564ed97ba67e02b1ebc89eb0239cf980752587f55eb9ec0419791a2e9c`.
La version actuellement installée n'a pas été extraite de la Sony : le dernier
état déclaré est v27.1/versionCode28. Toute mise à jour future doit conserver
le package, l'association et les données ; elle exige un GO distinct.
