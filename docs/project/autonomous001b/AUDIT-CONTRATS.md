# PARCOURS AUTONOME 001-B — raccords et contrats

Décision propriétaire du 8 novembre 2026, amendement 001 appliqué : la saisie du code TV est la seule opération d'association du spectateur. Aucun compte humain, seconde validation TV, écran « Autoriser cet appareil » ou confirmation par Track publique.

## Réutilisation et séparation

Base Android : `1bdac37c2a655041828e4b03b97e6de08a71699a`, fonctionnel v31 `1d642e70d1559e6d753476945dcb749036dc4266`. Base Cloud/Web : PR #54, logiciel `99752817bc0779fea58a6adb58874201ada364b4`, documentation `0a0aeb235b8445f07776cc07a2b78cb9bb59f272`.

| Raccord | Composants réutilisés | Ajout isolé |
| --- | --- | --- |
| Catalogue, Studio, Channel/Spaces, révision publique | PUBLIC CATALOGUE 003 et Web 001 | Aucun second catalogue ni nouvel éditeur |
| Code et possession | `TvFirst008State`, challenge TV-FIRST, `SecretStore.AndroidKeyStoreSecretStore` | `PublicCloudClient`, namespace `public001b_identity` |
| Validation des paquets/MP3 | `MultimediaDeliveryProfile`, `StudioMultimediaPackage`, `TrackParser`, `AndroidStudioAudioInspector` | Adaptation publique `PublicCatalogueCandidate`, octets publics originaux conservés |
| Engagement du cache | `AndroidInstallationBackend`, `InstallationStore` | Fichiers `public001b/<generation>`, codec `scenevibe.public-multimedia-cache.v1`, namespace strict public distinct |
| Restitution | `OverlayService`, `TvFirst009TextServiceOwner`, `CloudMultimediaSession`, moteurs TEXT/AUDIO, MEDIA, publicités et calendrier existants | Admission publique attachée à la possession et au cache, sans référence privée fabriquée |
| Préférences | Réglages TEXT/AUDIO existants | Application automatique des commandes Web, remontée des réglages TV effectivement appliqués |

Le label interne `accountId` exigé par le parseur pur historique reçoit le Channel dans une copie détachée après validation de l'enveloppe publique. Ce label n'est ni un compte, ni un grant, ni une autorité d'accès. L'identité publique originale, son digest et sa révision restent liés au cache. Seuls les labels de ressources publics vérifiés `svasset:public-<sha>` deviennent les labels internes `svasset:<sha>` attendus par le parseur Studio. Aucun parseur Studio ou MP3 parallèle.

Le chemin privé conserve son propre consentement historique et ses gardes. Le chemin public ne les simule pas : il emploie une autorité publique distincte. Le `Pending` interne à usage unique du service reste un transfert technique automatique, sans dialogue utilisateur.

## Séquence Cloud/TV

Toutes les actions sont `POST /api/autonomous/<action>`, origine exacte, corps fermé, réponses `no-store`. Le client signé exige HTTPS natif sur 443, refuse les redirections et borne tailles/délais/MIME. Les ressources sont demandées par identifiant du manifeste, jamais par URL fournie par le navigateur.

| Action | Autorité | Résultat utile |
| --- | --- | --- |
| `challenge` | Identité d'installation et secret générés sur TV | Code à six chiffres, expiration 120 s |
| `pair` Web | Unique saisie du code non consommé | Cookie opaque HttpOnly/SameSite ; aucun bearer TV côté Web |
| `challenge-status` | Secret TV du challenge | Credential chiffré localement, device/génération et échéance du grant |
| `association` | Bearer de possession TV + génération | Revalidation automatique du grant, échéance originale |
| `request` Web | Cookie de la TV associée | Commande épinglée à Track/révision/SHA ; pas de cache annoncé |
| `poll` TV | Possession courante | Commandes de cette génération seulement |
| `package` / `resource` | Possession + commande + disponibilité publique | Octets bornés et vérifiés ; contrôle automatique entre chunks |
| `validate-cache` | Possession + Track/révision/SHA | Reprise du cache indépendante du délai initial de téléchargement |
| `receipt:cached` | Possession et binding exact, après commit/relecture Android | Cache confirmé ; contrôles conservés jusqu'à l'expiration originale du grant |
| `activate` Web | Cookie associé, cache confirmé | Préférences demandées, état `cached` tant qu'elles ne sont pas appliquées |
| `receipt:armed` | Possession et état Android réellement appliqué | TEXT/AUDIO appliqués, y compris OFF/OFF ; aucune preuve de visionnage |
| `device-preferences` | Possession, commande déjà `armed` | Remontée d'un réglage TV appliqué, sans approbation humaine |

La durée courte de 15 minutes concerne les commandes de téléchargement non engagées. Un cache confirmé reste contrôlable pendant la durée du grant existant (7 jours au Cloud), sans nouvelle saisie durant le film. Les expirations, révocations et générations sont revérifiées automatiquement. Le cookie anonyme est haché en DB locale isolée pour survivre au redémarrage du coordinateur ; les commandes de ce prototype restent en mémoire. Après redémarrage du coordinateur, le cache TV est conservé et revalidé ; une nouvelle demande Web peut être nécessaire pour reconstruire le suivi, avec la même association tant qu'elle est valide.

## Cache, lecture et mises à jour

Le cache a un seul pointeur public courant par génération. Chaque installation écrit un ensemble complet temporaire, fsync les fichiers, redécode les trois MP3, renomme atomiquement l'ensemble puis engage et relit le pointeur `InstallationStore`. Une erreur ne remplace pas le précédent ensemble complet. Aucun ACK historique n'est produit. Les anciens ensembles restent conservés ; leur collecte et les quotas disque sont une réserve de passage à l'échelle.

Une requête de même Track pendant une session active attend. Pause, perte réseau ou simple disparition de métadonnées ne valent pas fin de film. Seul un état MEDIA `STOPPED` observé permet de terminer automatiquement cette session publique et de traiter la demande au cycle suivant. Une autre Track explicitement sélectionnée peut remplacer la session publique ; une session privée est préservée. Le contrôle d'activité est rejoué aux frontières du téléchargement et du commit.

Le départ public exige une Track mixte TEXT+AUDIO et l'identité MEDIA exacte, dont un `mediaId` non vide égal à celui de la Track, la plateforme et le package attendus. Un titre similaire ne contourne pas `MEDIA_IDENTITY_BLOCKED`. Seul Prime est admis dans ce candidat ; aucune compatibilité publique Sony/Prime, Netflix ou autre fournisseur n'est acquise par les tests de notre MediaSession. Les permissions Android de superposition et d'accès MEDIA existantes restent requises ; aucune permission applicative redondante n'est ajoutée.

## Isolation et réserves de raccordement réel

`scenevibePublicAutonomous001b` vaut `false` par défaut ; seule la variante expérimentale `tvux002` du workflow dédié l'active. Les anciens profils gardent leur version et leurs contrôles. L'injection réseau de qualification exige DEBUG + origine Cloud vide ; elle est refusée dans l'APK signé.

Le Cloud de démonstration demeure limité aux origines loopback, données synthétiques, base PostgreSQL native jetable et gates opt-in. Production et tout marqueur Vercel sont refusés. Aucune route créateur n'est ouverte anonymement. Le SQL de session anonyme est un opt-in du banc local, pas une migration fournisseur.

L'APK signé conserve l'origine Preview privée déjà qualifiée de la v31. Les nouvelles routes publiques n'y sont pas déployées. Les essais Web et Android sont exécutés séparément avec une frontière réseau synthétique côté émulateur ; ils ne prouvent pas un trajet HTTPS Web→Cloud réel→Sony. Une composition Cloud isolée autorisée, ses origines et sa persistance des commandes devront être préparées avant campagne réelle, puis l'APK recompilé si l'origine change. Aucun contournement du verrou local n'est prévu pour activer la démonstration en Production.

Les profils synthétiques sont préconfigurés sur le serveur local. Le navigateur n'apporte aucun `accountId` faisant autorité. Aucun chantier Better Auth, compte humain, mot de passe ou secret fournisseur.
