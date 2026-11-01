# TIMEBASE RESYNC 002 — Matrice de sécurité et niveau de preuve

1er novembre 2026 — Alex pour Pierre. Référence code/APK `33e59cf26e5f2b3c077e978c797b0b3b2834706e`, workflow final41272817005. PASS G1 borné aux contrôles ci-dessous ; ni Sony, ni Netflix.

## Contrôles exécutés

| Cas | Preuve | Résultat / limite |
|---|---|---|
| Fenêtre publique | ImageReader réel, test publicPixels, ligne6 de chaque banc | PASS : 4 repères connus et magenta non protégé présents |
| Composition mixte | SurfaceView setSecure(true) distincte du compteur ordinaire ; ligne5 | PASS : surface réellement dessinée, repères ordinaires présents, magenta de surface protégée absent |
| Fenêtre FLAG_SECURE | Test secureWindow, ligne7 | PASS : aucun repère protégé obtenu ; pas de seconde méthode |
| Refus / annulation consentement | Dialogue Android réel refusé, deniedConsent, ligne3 | PASS : aucune projection/service créé ; trace scalaire de refus |
| Révocation | API publique stop() sur notre projection après une première Image ; ligne1 | PASS : SESSION_REVOKED, Image fermée, ressources fermées |
| Arrêt avant réception | stopBeforeImage, ligne2 | PASS : zéro image, aucune reprise après attente |
| Départ de l'écran de contrôle | leaveFixture, ligne4 | PASS : arrêt ; aucun suivi d'une autre application |
| Ressources sur cas ci-dessus | received=imagesClosed, releaseErrors=0 pour les captures finales | PASS : appels de fermeture retournés ; pas un audit de tous les drivers OEM |
| ROI hors limites / non finie | Invariants Java sur coordonnées et dimensions | PASS portable ; pas une capture Android de format invalide |
| Buffer nul, tronqué, row/pixel stride invalide | ByteBuffer fabriqués et traitement partagé PixelProbe | PASS portable ; aucun payload Android arbitraire collecté |
| Génération ancienne / expiration / fermeture terminale / redémarrage de policy | CapturePolicy, temps synthétiques explicites | PASS portable ; absence de restauration vérifiée structurellement, pas test physique de redémarrage Sony |
| Mauvais motif | Images de test fabriquées, 0/3/4 repères | PASS portable ; pas un détecteur de contenu tiers |
| Confidentialité des captures | ROI écrasée, Image fermée, aucune image dans files/, aucune permission réseau/audio | PASS sur les chemins natifs exercés et contrats statiques |
| Isolation | Comparaison exacte537 fichiers de base + diff distant | PASS : aucun fichier qualifié modifié |

La limite de dimension est validée **avant** allocation de la copie. Les images hors génération/temps ne peuvent pas rouvrir une tentative. Seul le worker de session lit et ferme ImageReader ; aucun polling permanent. La suppression du journal signale les erreurs et refuse de courir contre une fermeture encore active.

## Cas non couverts par une injection Android exhaustive

| Cas | Ce qui est établi | Ce qui reste NOT EXECUTED |
|---|---|---|
| Erreur de création VirtualDisplay | Retour sûr d'initialisation indisponible observé lors de révocation précoce sur run41272427383 ; resourcesClosed=true | Injection déterministe d'une panne propre à createVirtualDisplay séparée de toute autre étape |
| Image tardive après arrêt | État fermé/génération ancienne refusés en Java ; listener retiré et reader fermé dans le service | Livraison forcée d'une vraie Image Android après fermeture du reader |
| Image de format/dimensions incompatible | Validation et buffers/layouts négatifs portables ; garde du format Image en code | Substitution d'une vraie ImageReader Image d'un autre format pendant la session |
| Rotation/redimensionnement système | Garde de dimensions refuse une discordance, sans redémarrer la projection | Rotation réelle pendant capture sur les deux bancs |
| Erreur de fermeture native / épuisement système | Fermeture de chaque ressource tentée séparément ; toute erreur est exposée | Injection d'une panne de driver, OOM ou blocage système |

Ces lignes ne valent pas PASS natif et limitent la portée du verdict. Elles ne sont pas dissimulées derrière un mock. Le PASS G1 porte sur les critères A–H observables du mandat dans les deux bancs exercés ; il ne certifie pas toutes les défaillances Android possibles ni un produit grand public.

## Interdits et état final

Deux permissions FGS ordinaires seulement. Pas de MediaSession tiers, AccessibilityService, geste/touche de lecteur, ADB/root comme acquisition, transport réseau, OCR, image persistante ou secret de production. L'ADB de banc est borné à emulator-5554 et contrôlé par ro.kernel.qemu ; l'instrumentation refuse le matériel physique avant toute action. Aucun contournement secure. G2 NOT EXECUTED.
