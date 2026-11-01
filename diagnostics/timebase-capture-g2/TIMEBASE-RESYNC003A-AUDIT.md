# TIMEBASE RESYNC 003-A — Audit du premier buffer

Alex — 1er novembre 2026. Cet audit porte sur le code qualifié `4213cb060bb77cd0cd3f986001861acd1ea3b987`, inchangé pour les deux classes dans le HEAD documentaire `6100de73c02794e9eb55ebf8b472b74dc09ef85b` de la PR Draft #39.

## Sources et limite du journal physique

**OWNER_REPORTED** : l'extrait fourni par Pierre indique IMAGE_TIMEOUT, consentement GRANTED, display 1920×1080, received=1, rejected=1, usefulFrames=0, delayMs=45000, triggerElapsedMs=123349134, closedElapsedMs=123359159 et resourcesClosed=true. Aucun journal brut de cette tentative n'a été joint ou retrouvé dans les références consultées. Aucun SHA-256 du journal original, timestamp du buffer ou instant de réception ne peut être inventé. L'extrait est conservé séparément des journaux d'émulateurs.

**CALCULATED** : la fermeture intervient 10025 ms après le déclenchement. Cela est compatible avec la fenêtre logicielle de 10 s et son nettoyage ; ce délai ne prouve ni une protection Netflix ni l'absence de pixels du compteur.

**OBSERVED_CODE** : [CaptureService.java au commit qualifié](https://github.com/pierre22400/scenevibe-tv-companion/blob/4213cb060bb77cd0cd3f986001861acd1ea3b987/diagnostics/timebase-capture-g2/app/src/main/java/com/scenevibe/timebasecaptureg2/CaptureService.java#L132) et [CapturePolicy.java](https://github.com/pierre22400/scenevibe-tv-companion/blob/4213cb060bb77cd0cd3f986001861acd1ea3b987/diagnostics/timebase-capture-g2/app/src/main/java/com/scenevibe/timebasecaptureg2/CapturePolicy.java#L43), lus intégralement. Les SHA Git des blobs sont respectivement `e392463c5b1eb8a7c99e319f61a736af20a775d4` et `ccf8dd25d49fa0e2efea22738d92a5743462f49b`.

## Tous les refus et sorties de l'ancien chemin

| Condition ancienne | Conséquence | Compatible avec l'extrait seul ? |
|---|---|---|
| Réception avant triggeredAt+250 ms | rejet puis fermeture de l'Image ; attente d'une autre | Oui, cause candidate principale |
| Premier timestamp ≤0, lastTimestamp initialisé à 0 | considéré comme doublon à tort, même sans prédécesseur | Oui, timestamp non enregistré donc UNKNOWN |
| Génération différente, état autre que CAPTURING, usefulFrame déjà vrai, réception avant triggeredAt ou à ≥10 s | accepts=false, rejet agrégé sans motif | Théoriquement oui pour le rejet ; l'extrait ne permet pas d'attribuer l'un de ces états |
| acquireLatestImage retourne null | rejected++ sans received++ | Ce callback n'est pas une image ; ne suffit pas seul à expliquer received=1 |
| received>240 | IMAGE_TIMEOUT terminal | Exclu par received=1 |
| Retour à la préparation | PREPARATION_RETURNED terminal | Ne produit pas IMAGE_TIMEOUT |
| Dimensions, format RGBA8888 ou nombre de planes invalides | IMAGE_INVALID terminal | Ne produit pas IMAGE_TIMEOUT |
| Erreur de lecture, stride/ROI/crop ou autre RuntimeException | IMAGE_INVALID terminal | Ne produit pas IMAGE_TIMEOUT |
| Refus de frame après recadrage, y compris course avec expiration/arrêt | STALE_GENERATION terminal, libellé trop global | Ne produit pas IMAGE_TIMEOUT |
| Callback après closed=true | ignoré, aucune acquisition | Ne peut expliquer le premier received=1 à lui seul |

Le calcul du doublon et la mise à jour de lastTimestamp précédaient le contrôle de politique. Si une seule image arrivait avant 250 ms, elle était fermée et perdue ; sans nouvelle composition, aucun prochain callback n'était garanti. Ce défaut est démontrable dans la politique et ne nécessite aucune hypothèse sur Netflix.

**INFERRED** : le défaut est une explication crédible du timeout Sony. **UNKNOWN** : laquelle des causes agrégées s'est réellement produite. L'ancien journal ne permet pas une attribution rétrospective certaine.

## Correctif minimal et garanties conservées

La barrière de 250 ms reste dans CapturePolicy. Le déclenchement ouvre toujours une fenêtre totale de 10 s. La création du **nouvel ImageReader et du seul VirtualDisplay** est différée après la barrière. Aucun buffer n'est produit dans cette file avant sa création ; sa première image peut donc passer la barrière sans dépendre d'une deuxième image. Aucun buffer complet n'est retenu pendant l'attente et aucun second consentement n'est demandé.

Le callback porte la génération immuable de sa file. Avant toute lecture, le service vérifie l'identité de l'ImageReader, la génération, le VirtualDisplay actif et son identifiant. La Surface n'est réutilisée par aucune ancienne tentative. Format, dimensions, strides et ROI restent validés ; un seul frame est engagé atomiquement, puis les ressources sont fermées. Le délai de 250 ms n'est pas ajouté à l'échéance de 10 s.

Le premier timestamp n'a pas de prédécesseur : zéro, ou une origine négative arbitraire, ne constitue pas un doublon. Les comparaisons de timestamps n'ont lieu qu'après une valeur réellement observée sur la même source. Aucun timestamp d'image n'est soustrait à elapsedRealtime/uptime.

Cette fraîcheur est celle du **pipeline de cette tentative et de la composition du display**. Elle ne prouve pas l'identité d'une application ni l'instant où une interface statique a été dessinée. Elle ne garantit pas qu'un premier buffer contient des chiffres plutôt qu'un noir ou une interface inadéquate ; l'inspection humaine demeure indispensable, sans A/B automatique.

## Nouvelle journalisation

`rejectionCounts` conserve les causes fermées ; `bufferDecisions` conserve au plus 16 décisions techniques, puis `decisionsTruncated`. Sont distingués : INITIALIZATION_REJECTED, DUPLICATE_TIMESTAMP, NON_MONOTONIC_TIMESTAMP, STALE_GENERATION, CAPTURE_INACTIVE, USEFUL_FRAME_ALREADY_ACQUIRED, RECEIPT_CLOCK_INVALID, ACQUISITION_EXPIRED, SOURCE_READER_MISMATCH, DISPLAY_BINDING_MISMATCH, NO_IMAGE_AVAILABLE, BUFFER_LIMIT, PREPARATION_RETURNED, IMAGE_LAYOUT_INVALID, FRAME_COMMIT_REFUSED et IMAGE_READ_OR_CROP_FAILURE. Le dernier couvre explicitement une exception de lecture ou recadrage, sans journaliser son message arbitraire.

`imageTimestampNs=null` indique qu’aucun timestamp d’Image n’a été observé ; un zéro numérique signifie une valeur effectivement lue. Les décisions indiquent index, horodatage de réception elapsedRealtime, écart au déclenchement, timestamp source en ns, générations et identifiant technique du display. Les instants de création du lecteur et du display sont ajoutés. Aucun pixel, titre, compte, URL ou hash stable de contenu n'est journalisé. Les callbacks ignorés après clôture ne rouvrent ni ne réécrivent la tentative terminale.

## Sources Android primaires

- [Image.getTimestamp](https://developer.android.com/reference/android/media/Image#getTimestamp()) : sens et base temporelle dépendent du producteur ; aucune positivité universelle n'est supposée.
- [ImageReader / getSurface](https://developer.android.com/reference/android/media/ImageReader#getSurface()) : les images sont fournies par le producteur connecté à cette Surface ; aucune réutilisation de Surface dans le correctif.
- [MediaProjection](https://developer.android.com/media/grow/media-projection) : consentement public par session et gestion explicite des ressources.

Aucune intervention Sony, capture tierce, modification Companion/Cloud, OCR ou contournement n'a été effectué pour cet audit ou le correctif.
