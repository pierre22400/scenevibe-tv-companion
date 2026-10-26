# CLOUD → ANDROID TV 002

Le candidat v17 conserve le package `com.scenevibe.tvcompanionpoc` et ajoute une activation explicite du cache multimédia au propriétaire d'OverlayService existant. Les profils v14/v15/v16 conservent leurs flags antérieurs. La compilation et la qualification native du lot 002 sont en cours ; ce document ne déclare pas de PASS physique.

Le mapping technique compare les SHA-256 exacts du snapshot Studio, du runtime TEXT et du manifeste déjà qualifiés. Il ne réécrit pas les champs éditoriaux UNKNOWN. Chaque départ demande une confirmation locale de l'édition de référence sans publicité. Cette confirmation reste en mémoire et ne constitue pas une preuve juridique. Une restauration du cache ne démarre aucun commentaire.

Le service utilise SceneRenderer, AndroidLocalAudioPort et le calendrier M5 existants, avec une seule sonde MediaSession passive. Les contrôles TEXT/AUDIO sont indépendants. Les starts MP3 différés revalident le permis avant de devenir audibles. Un changement de cache, génération ou consentement invalide le permis. Aucune commande n'est envoyée à Prime.

Les tests instrumentés 002 cliquent les véritables boutons du Companion. Deux bancs sont distingués : staging HTTPS Neon/R2, et repli HTTPS loopback PostgreSQL 16 + ObjectStore HTTP sans secrets. Leur horloge est une MediaSession appartenant à SceneVibe : un garde debug exclusif adapte le package attendu à cette fixture. Le candidat non debuggable refuse ce garde. Le banc local ne prouve pas le staging. Aucun de ces tests n'est une preuve de lecture Prime ou de rendu Sony.

Staging prévu : `https://scenevibe-cloud-tv002-staging-archc-ode.vercel.app`, projet Vercel dédié, target preview, branche Neon dédiée et nouveau préfixe R2. Son état opérationnel doit être prouvé par les résultats de qualification ; une URL configurée ne suffit pas.

Le workflow peut signer avec les quatre secrets de signature stable déjà employés par android-release.yml, sans les modifier. Il ne crée aucune nouvelle clé. Ce certificat CI doit encore être comparé à la référence M5 installée sur Sony ; une signature valide ne suffit pas à prouver cette égalité. Si la référence diffère, employer la clé M5 existante sur le PC propriétaire, puis recalculer le SHA du fichier signé.

STOP avant installation Sony. La préparation et la comparaison des certificats sont autorisées ; aucune commande d'installation physique n'est exécutée par cette mission.
